/**
 * HavenCraft SMP — Minecraft Server Power Signal API (Vercel Serverless Function)
 * Strict Zero-Leak Security Architecture:
 * 1. Caller Authentication: Requires valid Supabase JWT Bearer Token.
 * 2. RBAC Verification: Caller must have is_active === true and permissions.console === true.
 * 3. Dangerous Signal Restriction: 'stop' and 'kill' power signals are strictly locked to Owner.
 * 4. Audit Logging: Logs power actions directly to Supabase audit_logs table.
 */

const rateLimitMap = new Map();
function checkRateLimit(ip, limit = 10, windowMs = 60 * 1000) {
  const now = Date.now();
  if (rateLimitMap.size > 500) {
    for (const [k, v] of rateLimitMap.entries()) {
      if (now > v.resetTime) rateLimitMap.delete(k);
    }
  }
  const rec = rateLimitMap.get(ip);
  if (!rec || now > rec.resetTime) {
    rateLimitMap.set(ip, { count: 1, resetTime: now + windowMs });
    return { limited: false };
  }
  if (rec.count >= limit) {
    return { limited: true, retryAfter: Math.ceil((rec.resetTime - now) / 1000) };
  }
  rec.count++;
  return { limited: false };
}

export default async function handler(req, res) {
  const allowedOrigins = [
    'https://admin.havencraft.pro',
    'https://havencraft.pro',
    'https://www.havencraft.pro'
  ];
  if (process.env.ALLOWED_ORIGIN) {
    allowedOrigins.push(process.env.ALLOWED_ORIGIN.trim());
  }
  const origin = req.headers.origin || '';
  const isProd = process.env.VERCEL_ENV === 'production';
  const isAllowed = !origin ||
    allowedOrigins.includes(origin) ||
    (!isProd && /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin));

  if (isAllowed && origin) {
    res.setHeader('Access-Control-Allow-Origin', origin);
  } else if (!origin) {
    res.setHeader('Access-Control-Allow-Origin', 'https://admin.havencraft.pro');
  }

  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Vary', 'Origin');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  if (req.method !== 'POST') {
    res.setHeader('Allow', ['POST']);
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const clientIp = (req.headers['x-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim();
  const rl = checkRateLimit(clientIp, 10, 60 * 1000);
  if (rl.limited) {
    res.setHeader('Retry-After', String(rl.retryAfter));
    return res.status(429).json({ error: `Rate limit exceeded. Try again in ${rl.retryAfter}s.` });
  }

  try {
    const { action } = req.body || {};
    const validActions = ['start', 'stop', 'restart', 'kill'];

    if (!validActions.includes(action)) {
      return res.status(400).json({ error: 'Invalid power signal' });
    }

    // =========================================================================
    // 1. CALLER AUTHENTICATION & PERMISSION CHECK
    // =========================================================================
    const supabaseUrl = (process.env.SUPABASE_URL || 'https://nvcnowvasqcsygtbwktw.supabase.co').replace(/\/$/, '');
    const serviceKey = process.env.SUPABASE_SERVICE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;

    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace(/^Bearer\s+/i, '').trim();

    if (!token) {
      return res.status(401).json({ error: 'Unauthorized: Missing Authorization Bearer token.' });
    }

    // Verify token with Supabase Auth
    const authVerifyResp = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        'apikey': serviceKey || process.env.SUPABASE_ANON_KEY || '',
        'Authorization': `Bearer ${token}`
      }
    });

    if (!authVerifyResp.ok) {
      return res.status(401).json({ error: 'Unauthorized: Invalid or expired staff session token.' });
    }

    const callerUser = await authVerifyResp.json();
    const callerEmail = (callerUser.email || '').trim().toLowerCase();
    const ownerEmail = (process.env.OWNER_EMAIL || 'rajuel55@gmail.com').trim().toLowerCase();
    const isOwner = (callerEmail === ownerEmail);

    let callerName = callerEmail.split('@')[0];
    let callerRole = isOwner ? 'Owner' : 'Staff';

    // If not Owner, verify console permission & signal restrictions
    if (!isOwner) {
      if (!serviceKey) {
        return res.status(500).json({ error: 'Server configuration error: SUPABASE_SERVICE_KEY missing.' });
      }

      const profResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${callerUser.id}&select=*`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });
      const profiles = await profResp.json();
      const profile = profiles && profiles[0];

      if (!profile || !profile.is_active || profile.status !== 'active') {
        return res.status(403).json({ error: 'Forbidden: Inactive or suspended staff account.' });
      }

      callerName = profile.minecraft_ign || profile.full_name || callerName;
      callerRole = profile.role || 'Staff';

      const perms = profile.permissions || {};
      if (!perms.console || perms.console_edit === false || perms.read_only === true) {
        return res.status(403).json({ error: 'Forbidden: You have View-Only access or lack server power control permission.' });
      }

      // Restrict stop & kill power actions strictly to Owner
      if (action === 'stop' || action === 'kill') {
        return res.status(403).json({
          error: `Forbidden: The power action "${action}" can only be initiated by the Primary Server Owner.`
        });
      }
    }

    // Helper: Write accurate power signal outcome to audit_logs
    async function writePowerAuditLog(resultStatus, extraDetails = {}) {
      if (!serviceKey) return;
      try {
        await fetch(`${supabaseUrl}/rest/v1/audit_logs`, {
          method: 'POST',
          headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            staff_name: callerName,
            staff_role: callerRole,
            category: 'Server Power',
            action: `Power ${action.toUpperCase()}`,
            target: 'Minecraft Server Daemon',
            result: resultStatus,
            details: Object.assign({ signal: action }, extraDetails)
          })
        });
      } catch (logErr) {
        console.warn('Power audit log write failed:', logErr.message);
      }
    }

    // =========================================================================
    // 2. DISPATCH TO PTERODACTYL DAEMON
    // =========================================================================
    // Automatically route through Cloudflare Worker proxy to bypass Cloudflare Bot Fight Mode WAF on QbitNode
    const DEFAULT_PROXY_URL = 'https://qbit-proxy.rajuel55.workers.dev';
    let panelUrl = (process.env.PTERODACTYL_PANEL_URL || '').trim();
    if (!panelUrl || panelUrl.includes('panel.qbitnode.com')) {
      panelUrl = (process.env.PTERODACTYL_PROXY_URL || DEFAULT_PROXY_URL).trim();
    }
    if (panelUrl && !panelUrl.startsWith('http://') && !panelUrl.startsWith('https://')) {
      panelUrl = 'https://' + panelUrl;
    }
    panelUrl = panelUrl.replace(/^http:\/\//i, 'https://').split('/server/')[0].replace(/\/+$/, '');

    let apiKey = (process.env.PTERODACTYL_API_KEY || '').trim();
    apiKey = apiKey.replace(/^Bearer\s+/i, '').trim();

    let serverUuid = (process.env.PTERODACTYL_SERVER_UUID || '').trim();
    if (serverUuid.includes('/server/')) {
      serverUuid = serverUuid.split('/server/')[1];
    }
    serverUuid = serverUuid.replace(/\/+$/, '').trim();

    if (panelUrl && apiKey && serverUuid) {
      try {
        const response = await fetch(`${panelUrl}/api/client/servers/${serverUuid}/power`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          },
          body: JSON.stringify({ signal: action })
        });

        if (!response.ok) {
          const errorText = await response.text();
          let parsedError = errorText;
          try {
            const jsonErr = JSON.parse(errorText);
            if (jsonErr && jsonErr.errors && jsonErr.errors[0]) {
              parsedError = jsonErr.errors[0].detail || jsonErr.errors[0].code || errorText;
            }
          } catch (e) {
            if (errorText.includes('<!DOCTYPE') || errorText.includes('<html')) {
              if (errorText.includes('Cloudflare') || errorText.includes('cf-ray') || response.status === 403) {
                parsedError = `Cloudflare WAF blocked request (HTTP ${response.status}).`;
              } else {
                parsedError = `${response.status} ${response.statusText}`;
              }
            } else if (errorText.length > 180) {
              parsedError = errorText.substring(0, 180);
            }
          }
          await writePowerAuditLog(`Failed (${response.status})`, { error: parsedError });
          return res.status(response.status).json({
            error: 'Pterodactyl daemon rejected signal',
            details: `${parsedError} [Via: ${panelUrl}]`,
            status: response.status
          });
        }

        await writePowerAuditLog('Success');
        return res.status(200).json({
          success: true,
          message: `Power action "${action}" sent to Minecraft server by ${callerName}.`
        });
      } catch (err) {
        await writePowerAuditLog('Failed (Network)', { error: err.message });
        return res.status(500).json({ error: 'Failed to contact Pterodactyl daemon', details: `${err.message} [Via: ${panelUrl}]` });
      }
    }

    const missing = [];
    if (!panelUrl) missing.push('PTERODACTYL_PANEL_URL');
    if (!apiKey) missing.push('PTERODACTYL_API_KEY');
    if (!serverUuid) missing.push('PTERODACTYL_SERVER_UUID');

    await writePowerAuditLog('Simulated', { missing });
    return res.status(200).json({
      success: true,
      simulated: true,
      message: `[SIMULATED] Power action "${action}" authorized for ${callerName} (${callerRole}). Missing in Vercel: ${missing.join(', ')}. Please add them in Vercel and Redeploy.`
    });

  } catch (err) {
    return res.status(500).json({ error: 'Server power action failed', details: err.message });
  }
}
