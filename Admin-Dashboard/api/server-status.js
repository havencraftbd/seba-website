/**
 * HavenCraft SMP — Minecraft Server Live Telemetry & Status API (Vercel Serverless Function)
 * Fetches real-time CPU, RAM, Disk, and Power state from Pterodactyl daemon.
 * Fallback to public Minecraft SRV status when daemon is unreachable.
 */

const rateLimitMap = new Map();
function checkRateLimit(ip, limit = 60, windowMs = 60 * 1000) {
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

  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Vary', 'Origin');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const clientIp = (req.headers['x-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim();
  const rl = checkRateLimit(clientIp, 60, 60 * 1000);
  if (rl.limited) {
    res.setHeader('Retry-After', String(rl.retryAfter));
    return res.status(429).json({ error: `Rate limit exceeded. Try again in ${rl.retryAfter}s.` });
  }

  try {
    // 1. Verify caller session
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
      return res.status(401).json({ error: 'Unauthorized: Invalid or expired staff token.' });
    }

    const callerUser = await authVerifyResp.json();
    const callerEmail = (callerUser.email || '').trim().toLowerCase();
    const ownerEmail = (process.env.OWNER_EMAIL || 'rajuel55@gmail.com').trim().toLowerCase();
    const isOwner = (callerEmail === ownerEmail);

    // Enforce active staff RBAC verification (Fail-Closed: prevent pending/suspended users from accessing server telemetry)
    if (!isOwner) {
      if (!serviceKey) {
        return res.status(500).json({ error: 'Server configuration error: SUPABASE_SERVICE_KEY missing.' });
      }
      const profResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${encodeURIComponent(callerUser.id)}&select=is_active,status,role`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });
      const profiles = await profResp.json();
      const profile = Array.isArray(profiles) ? profiles[0] : null;
      if (!profile || !profile.is_active || profile.status !== 'active' || String(profile.role || '').toLowerCase() === 'pending') {
        return res.status(403).json({ error: 'Forbidden: Active staff account required.' });
      }
    }

    // 2. Resolve Pterodactyl Configuration
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

    // 3. Query Pterodactyl Resources Endpoint
    if (panelUrl && apiKey && serverUuid) {
      try {
        const pteroResp = await fetch(`${panelUrl}/api/client/servers/${serverUuid}/resources`, {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Accept': 'application/json'
          }
        });

        if (pteroResp.ok) {
          const pteroData = await pteroResp.json();
          const attr = pteroData.attributes || {};
          const resrc = attr.resources || {};

          return res.status(200).json({
            success: true,
            source: 'pterodactyl',
            telemetry: 'full',
            state: attr.current_state || 'running',
            isSuspended: !!attr.is_suspended,
            cpu: Math.round((resrc.cpu_absolute || 0) * 10) / 10,
            memoryBytes: resrc.memory_bytes || 0,
            memoryLimitBytes: resrc.memory_limit_bytes || (8 * 1024 * 1024 * 1024),
            diskBytes: resrc.disk_bytes || 0,
            diskLimitBytes: resrc.disk_limit_bytes || (40 * 1024 * 1024 * 1024),
            uptime: resrc.uptime || 0
          });
        }
      } catch (pteroErr) {
        console.warn('Pterodactyl resources fetch failed, attempting public ping fallback:', pteroErr.message);
      }
    }

    // 4. Fallback: Public Minecraft Query (mcsrvstat)
    try {
      const pingResp = await fetch('https://api.mcsrvstat.us/3/mc.havencraft.pro:2566');
      if (pingResp.ok) {
        const pingData = await pingResp.json();
        const isOnline = !!pingData.online;

        // Public ping only proves reachability — CPU/RAM/Disk are NOT available here,
        // so they stay null (UI shows "--") instead of fabricated numbers.
        return res.status(200).json({
          success: true,
          source: 'minecraft_ping',
          telemetry: 'basic',
          state: isOnline ? 'running' : 'offline',
          isSuspended: false,
          cpu: null,
          memoryBytes: null,
          memoryLimitBytes: null,
          diskBytes: null,
          diskLimitBytes: null,
          playersOnline: pingData.players ? pingData.players.online : 0,
          playersMax: pingData.players ? pingData.players.max : 100,
          version: pingData.version || 'Paper 1.21'
        });
      }
    } catch (pingErr) {}

    // Final Fallback when both Pterodactyl and Public Ping are unreachable
    return res.status(200).json({
      success: true,
      source: 'fallback',
      telemetry: 'basic',
      state: 'offline',
      cpu: null,
      memoryBytes: null,
      memoryLimitBytes: null,
      diskBytes: null,
      diskLimitBytes: null,
      playersOnline: 0,
      uptime: 0
    });

  } catch (err) {
    return res.status(500).json({ error: 'Failed to fetch server telemetry', details: err.message });
  }
}
