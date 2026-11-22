function extractMotd(motd) {
    if (!motd) return '';
    if (Array.isArray(motd.clean)) return motd.clean.join(' ').trim();
    if (typeof motd.clean === 'string') return motd.clean.trim();
    if (Array.isArray(motd.raw)) return motd.raw.join(' ').trim();
    if (typeof motd.raw === 'string') return motd.raw.trim();
    return '';
}

function normalizePlayers(playersData) {
    const online = Number(playersData?.online) || 0;
    const max = Number(playersData?.max) || 100;
    let list = [];

    if (Array.isArray(playersData?.list)) {
        list = playersData.list
            .map(p => (typeof p === 'string' ? p : (p?.name_clean || p?.name || '')))
            .filter(Boolean);
    }

    return { online, max, list };
}

export default async function handler(req, res) {
    const queryIp = typeof req?.query?.ip === 'string' && /^[a-zA-Z0-9.-]{3,100}$/.test(req.query.ip.trim())
        ? req.query.ip.trim()
        : null;
    const queryPort = typeof req?.query?.port === 'string' && /^\d{2,5}$/.test(req.query.port.trim())
        ? req.query.port.trim()
        : null;

    const port = queryPort || process.env.BEDROCK_PORT || '2025';
    const ip = queryIp || process.env.SERVER_IP || 'mc.havencraft.pro';

    // Prioritize native Java ping (returns player list) and fall back to Bedrock if Java fails
    const JAVA_ENDPOINTS = [
        `https://api.mcstatus.io/v2/status/java/${ip}`,
        `https://api.mcsrvstat.us/3/${ip}`
    ];
    const BEDROCK_ENDPOINTS = [
        `https://api.mcstatus.io/v2/status/bedrock/${ip}:${port}`,
        `https://api.mcsrvstat.us/bedrock/3/${ip}:${port}`
    ];

    const fetchEndpoint = async (endpoint) => {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 4500);

        try {
            const response = await fetch(endpoint, { signal: controller.signal });
            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();

            if (!data || !data.online) {
                throw new Error('Server reported offline or invalid data');
            }

            return {
                online: true,
                players: normalizePlayers(data.players),
                motd: extractMotd(data.motd),
                version: typeof data.version?.name_clean === 'string'
                    ? data.version.name_clean
                    : (typeof data.version === 'string' ? data.version : (data.version?.name || '1.21+'))
            };
        } catch (err) {
            clearTimeout(timeoutId);
            throw err;
        }
    };

    try {
        let result;
        try {
            result = await Promise.any(JAVA_ENDPOINTS.map(fetchEndpoint));
        } catch (_) {
            result = await Promise.any(BEDROCK_ENDPOINTS.map(fetchEndpoint));
        }

        res.setHeader?.('Cache-Control', 's-maxage=15, stale-while-revalidate=45');
        result.server_time = Date.now();
        return res.status(200).json(result);

    } catch (aggregateError) {
        res.setHeader?.('Cache-Control', 's-maxage=10, stale-while-revalidate=30');
        return res.status(200).json({
            online: false,
            players: { online: 0, max: 0, list: [] },
            motd: '',
            server_time: Date.now()
        });
    }
}
