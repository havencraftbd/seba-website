/**
 * Shared Supabase admin REST helper for Vercel serverless endpoints.
 */

export function getSupabaseAdminConfig() {
    const url = (process.env.SUPABASE_URL || process.env.TICKET_SUPABASE_URL || '').trim().replace(/\/+$/, '');
    const serviceKey = (
        process.env.SUPABASE_SERVICE_KEY ||
        process.env.TICKET_SUPABASE_SERVICE_KEY ||
        process.env.SUPABASE_SERVICE_ROLE_KEY ||
        ''
    ).trim();

    if (!url || !serviceKey) return null;
    return { url, serviceKey };
}

export function buildSupabaseHeaders(serviceKey, prefer = 'return=representation') {
    const headers = {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        'Content-Type': 'application/json'
    };
    if (prefer) {
        headers.Prefer = prefer;
    }
    return headers;
}

export async function supabaseRest(config, path, options = {}) {
    const {
        method = 'GET',
        body,
        headers = {},
        prefer = 'return=representation',
        timeoutMs = 8000,
        parseJson = true
    } = options;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
        const mergedHeaders = {
            ...buildSupabaseHeaders(config.serviceKey, method === 'GET' ? '' : prefer),
            ...headers
        };

        const res = await fetch(`${config.url}${path}`, {
            method,
            headers: mergedHeaders,
            body: body !== undefined && typeof body !== 'string' ? JSON.stringify(body) : body,
            signal: controller.signal
        });

        if (!parseJson) {
            const text = await res.text().catch(() => '');
            return { ok: res.ok, status: res.status, data: null, text };
        }

        const text = await res.text().catch(() => '');
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch {
                data = null;
            }
        }
        return { ok: res.ok, status: res.status, data, text };
    } finally {
        clearTimeout(timeoutId);
    }
}
