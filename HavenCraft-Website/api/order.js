import {
    enforceMethod,
    getClientIp,
    createRateLimiter,
    verifyTurnstile,
    sanitizeDiscordText
} from './_lib/http.js';
import { getSupabaseAdminConfig, supabaseRest } from './_lib/supabase.js';
import { validateAndBuildOrder } from './_lib/store-catalog.js';

const checkRateLimit = createRateLimiter({ maxRequests: 5, windowMs: 60 * 1000 });

function generateOrderId() {
    const randomChars = Math.random().toString(36).substring(2, 6).toUpperCase();
    const timestamp = Date.now().toString().slice(-4);
    return `HC-${timestamp}${randomChars}`;
}

function normalizePaymentMethod(rawMethod) {
    const val = typeof rawMethod === 'string' ? rawMethod.trim().toLowerCase() : '';
    if (!val || val === 'bkash') return 'bKash';
    if (val === 'nagad') return 'Nagad';
    return null;
}

async function dispatchDiscordWebhook(webhookUrl, payload) {
    if (!webhookUrl) return { ok: false, skipped: true };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);

    try {
        const res = await fetch(webhookUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: controller.signal
        });
        if (!res.ok) {
            const errText = await res.text().catch(() => '');
            console.error('Discord Webhook error:', res.status, errText);
            return { ok: false, status: res.status };
        }
        return { ok: true };
    } catch (err) {
        console.error('Discord Webhook dispatch failed:', err?.message || err);
        return { ok: false, error: err?.message || 'Network error' };
    } finally {
        clearTimeout(timeoutId);
    }
}

async function persistStoreOrder(supabaseConfig, orderRecord) {
    if (!supabaseConfig) return { ok: false, skipped: true };

    try {
        const res = await supabaseRest(supabaseConfig, '/rest/v1/store_orders', {
            method: 'POST',
            prefer: 'return=minimal',
            body: orderRecord,
            parseJson: false
        });
        if (!res.ok) {
            console.error('[Supabase Store Order Insert Failed]:', res.status, res.text);
            return { ok: false, status: res.status };
        }
        return { ok: true };
    } catch (err) {
        console.error('[Supabase Store Order Exception]:', err?.message || err);
        return { ok: false, error: err?.message || 'Database exception' };
    }
}

export default async function handler(req, res) {
    if (!enforceMethod(req, res, ['POST'])) return;

    try {
        const clientIp = getClientIp(req);
        const rateLimit = checkRateLimit(clientIp);
        if (rateLimit.limited) {
            return res.status(429).json({
                error: `Too many submissions. Please wait ${rateLimit.retryAfter} seconds before trying again.`
            });
        }

        const body = req.body || {};
        if (body.botcheck) {
            return res.status(200).json({ success: true, orderId: generateOrderId() });
        }

        const captchaToken = body.captchaToken || body['cf-turnstile-response'] || '';
        const captchaResult = await verifyTurnstile(captchaToken, clientIp, 'order');
        if (!captchaResult.success) {
            return res.status(400).json({ error: captchaResult.error });
        }

        const username = typeof body.username === 'string' ? body.username.trim() : '';
        if (!/^[a-zA-Z0-9_.]{3,24}$/.test(username)) {
            return res.status(400).json({ error: 'Please enter a valid Minecraft username (3-24 characters).' });
        }

        const rawPlatform = typeof body.platform === 'string' ? body.platform.trim().toLowerCase() : '';
        const isBedrock = rawPlatform === 'bedrock' || username.startsWith('.');
        const platform = isBedrock ? 'Bedrock' : 'Java';

        const paymentMethod = normalizePaymentMethod(body.paymentMethod);
        if (!paymentMethod) {
            return res.status(400).json({ error: 'Unsupported payment method selected.' });
        }

        const senderNumber = typeof body.senderNumber === 'string' ? body.senderNumber.trim() : '';
        if (!senderNumber || senderNumber.length < 4 || senderNumber.length > 20) {
            return res.status(400).json({ error: 'Please enter a valid payment sender number or last 4 digits.' });
        }

        const trxId = typeof body.trxId === 'string' ? body.trxId.trim() : '';
        if (!trxId || trxId.length < 4 || trxId.length > 32) {
            return res.status(400).json({ error: 'Please enter a valid Transaction ID (TrxID).' });
        }

        const discordUser = typeof body.discordUser === 'string' ? body.discordUser.trim() : '';
        if (!discordUser || discordUser === 'Not provided' || discordUser.length < 2 || discordUser.length > 50) {
            return res.status(400).json({ error: 'Please enter a valid Discord username.' });
        }

        const webhookUrl = (process.env.DISCORD_STORE_WEBHOOK_URL || '').trim();
        const supabaseConfig = getSupabaseAdminConfig();
        if (!webhookUrl && !supabaseConfig) {
            console.error('[Configuration Error]: Both DISCORD_STORE_WEBHOOK_URL and Supabase admin credentials are missing.');
            return res.status(500).json({
                error: 'Order service is temporarily unavailable. Please contact staff directly on Discord.'
            });
        }

        // Authoritative price, rank command, and spawner validation against live Supabase shop_items / canonical catalog.
        const validated = await validateAndBuildOrder(body, supabaseConfig);
        if (!validated.ok) {
            return res.status(validated.status || 400).json({ error: validated.error });
        }

        const orderId = generateOrderId();
        const cleanPlayerName = username.startsWith('.') ? username.substring(1) : username;
        const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(cleanPlayerName || 'MHF_Steve')}/100`;
        const adminCmd = validated.resolveAdminCommand(username);

        const safeUsername = sanitizeDiscordText(username, { stripBackticks: true });
        const safeDiscordUser = sanitizeDiscordText(discordUser, { stripBackticks: true });
        const safeSender = sanitizeDiscordText(senderNumber, { stripBackticks: true });
        const safeTrxId = sanitizeDiscordText(trxId, { stripBackticks: true });
        const safeItemName = sanitizeDiscordText(validated.itemName, { stripBackticks: true });

        const embedFields = [
            {
                name: '👤 Minecraft Player',
                value: `> • **Username:** \`${safeUsername}\`\n> • **Edition:** \`${platform} Edition\`\n> • **Discord:** \`${safeDiscordUser}\``,
                inline: false
            },
            {
                name: '📦 Purchase Package',
                value: `> • **Item:** **${safeItemName}**\n> • **Amount:** \`${validated.price}\`\n> • **Payment:** \`${paymentMethod} (Send Money)\``,
                inline: false
            },
            {
                name: '💳 Payment & Verification',
                value: `> • **Sender Number:** \`${safeSender}\`\n> • **Transaction ID:** \`${safeTrxId}\`\n> • **Status:** ⏳ *Pending Staff Verification*`,
                inline: false
            }
        ];

        if (validated.selectedSpawners) {
            embedFields.push({
                name: '🎁 Included Rank Spawners',
                value: validated.selectedSpawners,
                inline: false
            });
        }

        if (validated.itemsList) {
            embedFields.push({
                name: '🛒 Purchased Spawners',
                value: validated.itemsList,
                inline: false
            });
        }

        if (adminCmd) {
            embedFields.push({
                name: '⚡ Quick Admin Console Command',
                value: `\`\`\`bash\n${adminCmd}\n\`\`\``,
                inline: false
            });
        }

        embedFields.push({
            name: '⚖️ Store Terms Agreement',
            value: '> • **Accepted:** ✅ `Yes` (Strict No-Refund & In-game Policy)',
            inline: false
        });

        const discordPayload = {
            username: 'HavenCraft Store',
            avatar_url: 'https://havencraft.pro/logo/logo.png',
            embeds: [
                {
                    title: `🛒 New Store Order • \`${orderId}\``,
                    color: 0x00c896,
                    fields: embedFields,
                    thumbnail: { url: avatarUrl },
                    footer: {
                        text: `HavenCraft Store • Order ID: ${orderId}`,
                        icon_url: 'https://havencraft.pro/logo/logo.png'
                    },
                    timestamp: new Date().toISOString()
                }
            ]
        };

        const dbRecord = {
            order_code: orderId,
            username,
            platform,
            item_name: validated.dbItemName,
            price: validated.price,
            payment_method: paymentMethod,
            sender_number: senderNumber,
            trx_id: trxId,
            discord_user: discordUser,
            status: 'pending'
        };

        const [discordOutcome, dbOutcome] = await Promise.all([
            dispatchDiscordWebhook(webhookUrl, discordPayload),
            persistStoreOrder(supabaseConfig, dbRecord)
        ]);

        // Never return 200 OK if every configured delivery target failed.
        if (!discordOutcome.ok && !dbOutcome.ok) {
            return res.status(502).json({
                error: 'Could not save or dispatch your order right now. Please try again or contact staff on Discord.'
            });
        }

        return res.status(200).json({
            success: true,
            orderId,
            username,
            itemName: validated.itemName,
            price: validated.price,
            trxId
        });
    } catch (error) {
        console.error('Store Order API Error:', error);
        return res.status(500).json({ error: 'Failed to process order. Please contact staff on Discord.' });
    }
}
