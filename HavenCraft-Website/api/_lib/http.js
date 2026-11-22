/**
 * Shared HTTP, Turnstile, rate-limiting, and Discord text sanitization helpers
 * for Vercel serverless endpoints under api/.
 */

export function enforceMethod(req, res, allowed = ['POST']) {
    if (allowed.includes(req?.method)) return true;
    res.setHeader?.('Allow', allowed);
    res.status(405).json({ error: 'Method not allowed' });
    return false;
}

export function getClientIp(req) {
    const forwarded = req?.headers?.['x-forwarded-for'];
    const raw = Array.isArray(forwarded) ? forwarded[0] : (forwarded || '');
    const first = String(raw).split(',')[0].trim();
    return first || req?.socket?.remoteAddress || 'unknown';
}

export function createRateLimiter({ maxRequests = 5, windowMs = 60 * 1000, cleanupThreshold = 500 } = {}) {
    const store = new Map();

    return function checkRateLimit(key, limit = maxRequests, customWindowMs = windowMs) {
        const now = Date.now();
        if (store.size > cleanupThreshold) {
            for (const [k, val] of store.entries()) {
                if (now > val.resetTime) store.delete(k);
            }
        }

        const record = store.get(key);
        if (!record || now > record.resetTime) {
            store.set(key, { count: 1, resetTime: now + customWindowMs });
            return { limited: false };
        }

        if (record.count >= limit) {
            return {
                limited: true,
                retryAfter: Math.ceil((record.resetTime - now) / 1000)
            };
        }

        record.count += 1;
        return { limited: false };
    };
}

export function sanitizeDiscordText(str, { stripBackticks = false } = {}) {
    if (!str || typeof str !== 'string') return '';
    let sanitized = str
        .replace(/@everyone/gi, '@\u200Beveryone')
        .replace(/@here/gi, '@\u200Bhere')
        .replace(/<@&/g, '<@\u200B&');
    if (stripBackticks) {
        sanitized = sanitized.replace(/`/g, '\u200B');
    }
    return sanitized;
}

export async function verifyTurnstile(token, clientIp, context = 'security') {
    if (!token) {
        return { success: false, error: 'Please complete the security check (Captcha).' };
    }

    let secret = (process.env.TURNSTILE_SECRET_KEY || '').trim();
    if (!secret) {
        console.error('[Security Alert]: TURNSTILE_SECRET_KEY is missing in environment variables.');
        return { success: false, error: 'Server security configuration missing. Please contact staff.' };
    }

    // Some dashboards copy the site/secret prefix with an extra 'A' (`0x4AAAAAAAF...`, length 36).
    if (secret.startsWith('0x4AAAAAAAF') && secret.length === 36) {
        secret = secret.replace(/^0x4AAAAAAAF/, '0x4AAAAAAF');
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 8000);

    try {
        const formData = new URLSearchParams();
        formData.append('secret', secret);
        formData.append('response', token);
        if (clientIp && clientIp !== 'unknown') {
            formData.append('remoteip', clientIp);
        }

        const verifyRes = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData,
            signal: controller.signal
        });

        const outcome = await verifyRes.json();
        if (outcome && outcome.success) {
            return { success: true };
        }

        console.warn(`Turnstile ${context} verification rejected:`, outcome);
        return { success: false, error: 'Captcha validation failed. Please refresh and try again.' };
    } catch (err) {
        console.error('Turnstile verification network error:', err);
        return { success: false, error: 'Security verification error. Please try again.' };
    } finally {
        clearTimeout(timeoutId);
    }
}
