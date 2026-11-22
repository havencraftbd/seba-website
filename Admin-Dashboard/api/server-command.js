/**
 * HavenCraft SMP — Minecraft Server Command Execution API (Vercel Serverless Function)
 * Strict Zero-Leak Security Architecture:
 * 1. Caller Authentication: Requires valid Supabase JWT Bearer Token.
 * 2. RBAC Verification:
 *    - Console staff (permissions.console === true) can run non-blacklisted commands.
 *    - Order/Finance staff (permissions.orders === true) can ONLY run verified store delivery commands.
 * 3. Command Injection & Namespace Bypass Shield: Blocks newlines, control chars, namespace prefixes (minecraft:op), and LP privilege escalation.
 * 4. Audit Logging: Logs command executions directly to Supabase audit_logs table.
 */

const rateLimitMap = new Map();
function checkRateLimit(ip, limit = 30, windowMs = 60 * 1000) {
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
  const rl = checkRateLimit(clientIp, 30, 60 * 1000);
  if (rl.limited) {
    res.setHeader('Retry-After', String(rl.retryAfter));
    return res.status(429).json({ error: `Rate limit exceeded. Try again in ${rl.retryAfter}s.` });
  }

  try {
    const { command } = req.body || {};
    if (!command || typeof command !== 'string') {
      return res.status(400).json({ error: 'Missing or invalid command string' });
    }

    // Security Check: Reject newline or control character command injection
    if (/[\r\n\0]/.test(command)) {
      return res.status(400).json({ error: 'Invalid command format: Multi-line commands are prohibited.' });
    }

    const cleanCmd = command.trim().replace(/^\//, '');
    if (!cleanCmd) {
      return res.status(400).json({ error: 'Empty command string' });
    }

    // Strict regex for safe store delivery commands (Ranks and SmartSpawners ONLY)
    const SAFE_DELIVERY_PATTERNS = [
      /^lp\s+user\s+\.?[a-zA-Z0-9_]{1,32}\s+parent\s+set\s+(prime|legend|elite)$/i,
      /^ss\s+give\s+\.?[a-zA-Z0-9_]{1,32}\s+smart_spawner\s+[a-z0-9_]+_spawner\s+\d{1,3}$/i
    ];
    const isSafeStoreDeliveryCmd = SAFE_DELIVERY_PATTERNS.some(rx => rx.test(cleanCmd));

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

    // If not Owner, verify staff profile and permissions in database
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
      if (perms.read_only === true) {
        return res.status(403).json({
          error: 'Forbidden: Your account is in View-Only mode and cannot execute server commands.'
        });
      }

      const hasConsoleEdit = perms.console === true && perms.console_edit !== false;
      const hasOrdersEdit = (perms.orders === true && perms.orders_edit !== false) ||
        (perms.finance === true && perms.finance_edit !== false);

      if (!hasConsoleEdit) {
        // Allow Order/Moderator staff with Edit permission to execute ONLY verified store delivery commands
        if (!(hasOrdersEdit && isSafeStoreDeliveryCmd)) {
          return res.status(403).json({
            error: 'Forbidden: You have View-Only access or lack console execution permission for commands.'
          });
        }
      }

      // Enforce Command Blacklist with Namespace Stripping (e.g., blocks "minecraft:op", "bukkit:stop")
      const firstToken = cleanCmd.split(/\s+/)[0].toLowerCase();
      const baseCommand = firstToken.includes(':') ? firstToken.split(':').pop() : firstToken;

      const OWNER_ONLY_COMMANDS = [
        'op', 'deop', 'stop', 'restart', 'kill', 'end', 'ban', 'ban-ip', 'pardon', 'pardon-ip',
        'whitelist', 'execute', 'sudo', 'reload', 'rl', 'plugman', 'luckperms', 'pex',
        'gamerule', 'fill', 'setblock', 'clone', 'forceload', 'cmi', 'spark',
        'save-all', 'save-off', 'save-on', 'timings'
      ];

      if (OWNER_ONLY_COMMANDS.includes(baseCommand) || OWNER_ONLY_COMMANDS.includes(firstToken)) {
        return res.status(403).json({
          error: 'Forbidden: Dangerous server command locked strictly to Owner.',
          details: `Command "/${firstToken}" requires Primary Owner authority.`
        });
      }

      // Block arbitrary "/lp" permission tampering by non-owners (only allow safe store rank sets)
      if ((baseCommand === 'lp' || baseCommand === 'perm' || baseCommand === 'permissions') && !isSafeStoreDeliveryCmd) {
        return res.status(403).json({
          error: 'Forbidden: Direct LuckPerms modification is locked strictly to Owner.',
          details: 'Non-owner staff may only assign standard store ranks (prime, legend, elite).'
        });
      }
    }

    // Helper: Write accurate execution outcome to audit_logs
    async function writeCommandAuditLog(resultStatus, extraDetails = {}) {
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
            category: 'Server Console',
            action: 'Command Execution',
            target: cleanCmd.length > 80 ? cleanCmd.substring(0, 80) + '...' : cleanCmd,
            result: resultStatus,
            details: Object.assign({ command: cleanCmd }, extraDetails)
          })
        });
      } catch (logErr) {
        console.warn('Console audit log write failed:', logErr.message);
      }
    }

    // =========================================================================
    // 2. DISPATCH TO PTERODACTYL DAEMON
    // =========================================================================
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
        const response = await fetch(`${panelUrl}/api/client/servers/${serverUuid}/command`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          },
          body: JSON.stringify({ command: cleanCmd })
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
          await writeCommandAuditLog(`Failed (${response.status})`, { error: parsedError });
          return res.status(response.status).json({
            error: 'Pterodactyl rejected command',
            details: `${parsedError} [Via: ${panelUrl}]`,
            status: response.status
          });
        }

        await writeCommandAuditLog('Success');
        return res.status(200).json({
          success: true,
          output: `Command executed on daemon: /${cleanCmd}`
        });
      } catch (err) {
        await writeCommandAuditLog('Failed (Network)', { error: err.message });
        return res.status(500).json({ error: 'Pterodactyl communication failed', details: `${err.message} [Via: ${panelUrl}]` });
      }
    }

    const missing = [];
    if (!panelUrl) missing.push('PTERODACTYL_PANEL_URL');
    if (!apiKey) missing.push('PTERODACTYL_API_KEY');
    if (!serverUuid) missing.push('PTERODACTYL_SERVER_UUID');

    await writeCommandAuditLog('Simulated', { missing });
    return res.status(200).json({
      success: true,
      simulated: true,
      output: `[SIMULATED] Command "/${cleanCmd}" authorized for ${callerName} (${callerRole}). Missing in Vercel: ${missing.join(', ')}. Please add them in Vercel and Redeploy.`
    });

  } catch (err) {
    return res.status(500).json({ error: 'Server command execution failed', details: err.message });
  }
}
