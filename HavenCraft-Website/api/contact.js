import crypto from 'crypto';
import {
    enforceMethod,
    getClientIp,
    createRateLimiter,
    verifyTurnstile,
    sanitizeDiscordText
} from './_lib/http.js';
import { getSupabaseAdminConfig, supabaseRest } from './_lib/supabase.js';

const checkRateLimit = createRateLimiter({ maxRequests: 5, windowMs: 60 * 1000 });

function computeSessionToken(ticketId, email, serviceKey) {
    return crypto.createHmac('sha256', serviceKey)
        .update(`${ticketId}:${(email || '').toLowerCase().trim()}`)
        .digest('hex');
}

function safeEqualStrings(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    return crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b));
}

async function generateSequentialTicketCode(supabaseConfig) {
    try {
        const res = await supabaseRest(supabaseConfig, '/rest/v1/tickets?select=ticket_code&order=id.desc&limit=50');
        let maxNum = 999;
        if (res.ok && Array.isArray(res.data)) {
            for (const row of res.data) {
                const match = row?.ticket_code?.match(/^HC-(\d+)$/i);
                if (match) {
                    const val = parseInt(match[1], 10);
                    if (!isNaN(val) && val > maxNum && val < 50000) {
                        maxNum = val;
                    }
                }
            }
        }
        return `HC-${maxNum + 1}`;
    } catch (err) {
        console.error('[Generate Ticket Code Error]:', err);
        return 'HC-1000';
    }
}

async function authorizeTicketAccess(supabaseConfig, {
    ticketId,
    ticketCode,
    sessionToken,
    email,
    select = 'status,user_email',
    notFoundMessage = 'Ticket does not exist',
    requireEmailPrompt = false
}) {
    let queryPath = '';
    if (ticketCode) {
        queryPath = `/rest/v1/tickets?ticket_code=eq.${encodeURIComponent(ticketCode)}&select=${select}`;
    } else {
        const cleanId = String(ticketId || '').trim();
        if (!/^\d+$/.test(cleanId)) {
            return { ok: false, status: 400, error: 'Valid Ticket ID is required' };
        }
        queryPath = `/rest/v1/tickets?id=eq.${cleanId}&select=${select}`;
    }

    const tktRes = await supabaseRest(supabaseConfig, queryPath);
    if (!tktRes.ok) {
        console.error('[Ticket Auth Fetch Error]:', tktRes.status, tktRes.text);
        return { ok: false, status: 500, error: 'Database error while verifying ticket.' };
    }

    const tickets = Array.isArray(tktRes.data) ? tktRes.data : [];
    if (tickets.length === 0) {
        return { ok: false, status: 404, error: notFoundMessage };
    }

    const ticket = tickets[0];
    const targetId = ticketId || ticket.id;
    const expectedToken = computeSessionToken(targetId, ticket.user_email, supabaseConfig.serviceKey);
    const cleanToken = typeof sessionToken === 'string' ? sessionToken.trim() : '';
    const cleanEmail = typeof email === 'string' ? email.trim().toLowerCase() : '';
    const storedEmail = (ticket.user_email || '').trim().toLowerCase();

    const isTokenValid = Boolean(cleanToken && safeEqualStrings(cleanToken, expectedToken));
    const isEmailValid = Boolean(cleanEmail && storedEmail && safeEqualStrings(cleanEmail, storedEmail));

    if (!isTokenValid && !isEmailValid) {
        if (requireEmailPrompt) {
            return {
                ok: false,
                status: 403,
                error: 'Security Verification: Please provide the email address used to create this ticket.',
                requiresEmail: true
            };
        }
        return { ok: false, status: 403, error: 'Unauthorized: Invalid ticket session token.' };
    }

    return { ok: true, ticket, expectedToken };
}

export default async function handler(req, res) {
    if (!enforceMethod(req, res, ['POST'])) return;

    try {
        const clientIp = getClientIp(req);
        const body = req.body || {};
        const action = body.action || 'create';

        const supabaseConfig = getSupabaseAdminConfig();
        if (!supabaseConfig) {
            console.error('[Configuration Error]: SUPABASE_URL or SUPABASE_SERVICE_KEY is missing in environment variables.');
            return res.status(500).json({ error: 'Server database configuration missing. Please contact staff.' });
        }

        if (action === 'get') {
            const ticketCode = typeof body.ticketCode === 'string' ? body.ticketCode.trim().toUpperCase() : '';
            if (!ticketCode) {
                return res.status(400).json({ error: 'Ticket code is required' });
            }

            const auth = await authorizeTicketAccess(supabaseConfig, {
                ticketCode,
                sessionToken: body.sessionToken,
                email: body.email,
                select: '*',
                notFoundMessage: 'Ticket not found. Please check your ticket code.',
                requireEmailPrompt: true
            });
            if (!auth.ok) {
                const errPayload = { error: auth.error };
                if (auth.requiresEmail) errPayload.requiresEmail = true;
                return res.status(auth.status).json(errPayload);
            }

            const msgRes = await supabaseRest(
                supabaseConfig,
                `/rest/v1/ticket_messages?ticket_id=eq.${auth.ticket.id}&order=created_at.asc&select=*`
            );

            return res.status(200).json({
                success: true,
                ticket: auth.ticket,
                sessionToken: auth.expectedToken,
                messages: Array.isArray(msgRes.data) ? msgRes.data : []
            });
        }

        if (action === 'reply') {
            const rateLimit = checkRateLimit(`reply_${clientIp}`, 15, 60 * 1000);
            if (rateLimit.limited) {
                return res.status(429).json({ error: `Slow down! Please wait ${rateLimit.retryAfter}s before typing again.` });
            }

            const rawMessage = typeof body.message === 'string' ? body.message.trim() : '';
            const message = sanitizeDiscordText(rawMessage);
            const senderName = typeof body.senderName === 'string' && body.senderName.trim() ? body.senderName.trim() : 'Player';

            if (!body.ticketId || !message) {
                return res.status(400).json({ error: 'Ticket ID and message are required' });
            }
            if (message.length > 2000) {
                return res.status(400).json({ error: 'Message cannot exceed 2000 characters' });
            }

            const auth = await authorizeTicketAccess(supabaseConfig, {
                ticketId: body.ticketId,
                sessionToken: body.sessionToken,
                email: body.email
            });
            if (!auth.ok) {
                return res.status(auth.status).json({ error: auth.error });
            }
            if (auth.ticket.status === 'closed') {
                return res.status(400).json({ error: 'This ticket has been closed by staff.' });
            }

            const insertRes = await supabaseRest(supabaseConfig, '/rest/v1/ticket_messages', {
                method: 'POST',
                body: {
                    ticket_id: body.ticketId,
                    sender: 'player',
                    sender_name: senderName,
                    message
                }
            });

            if (!insertRes.ok) {
                console.error('[Supabase Message Insert Error]:', insertRes.text);
                return res.status(500).json({ error: 'Failed to send message. Please try again.' });
            }

            return res.status(200).json({
                success: true,
                message: Array.isArray(insertRes.data) ? insertRes.data[0] : insertRes.data
            });
        }

        if (action === 'upload') {
            const rateLimit = checkRateLimit(`upload_${clientIp}`, 10, 60 * 1000);
            if (rateLimit.limited) {
                return res.status(429).json({ error: `Upload rate limit reached. Please wait ${rateLimit.retryAfter}s.` });
            }

            const imageBase64 = typeof body.fileBase64 === 'string' ? body.fileBase64 : '';
            if (!body.ticketId || !imageBase64) {
                return res.status(400).json({ error: 'Ticket ID and image data are required' });
            }

            const auth = await authorizeTicketAccess(supabaseConfig, {
                ticketId: body.ticketId,
                sessionToken: body.sessionToken,
                email: body.email
            });
            if (!auth.ok) {
                return res.status(auth.status).json({ error: auth.error });
            }
            if (auth.ticket.status === 'closed') {
                return res.status(400).json({ error: 'Cannot upload attachments to a closed ticket.' });
            }

            let buffer;
            let mimeType = 'image/jpeg';

            if (imageBase64.startsWith('data:')) {
                const matches = imageBase64.match(/^data:([A-Za-z0-9\/\-+.]+);base64,(.+)$/);
                if (!matches || matches.length !== 3) {
                    return res.status(400).json({ error: 'Invalid Base64 image data' });
                }
                mimeType = matches[1].toLowerCase();
                buffer = Buffer.from(matches[2], 'base64');
            } else {
                buffer = Buffer.from(imageBase64, 'base64');
            }

            const allowedMimes = {
                'image/png': 'png',
                'image/jpeg': 'jpg',
                'image/jpg': 'jpg',
                'image/webp': 'webp',
                'image/gif': 'gif',
                'audio/webm': 'webm',
                'audio/ogg': 'ogg',
                'audio/mp4': 'mp4',
                'audio/mpeg': 'mp3',
                'audio/wav': 'wav'
            };

            const extension = allowedMimes[mimeType];
            if (!extension) {
                return res.status(400).json({ error: 'Invalid file format. Allowed: Images (PNG, JPG, WebP, GIF) & Voice Audio (WebM, OGG, MP4, MP3).' });
            }
            if (buffer.length > 2 * 1024 * 1024) {
                return res.status(400).json({ error: 'File exceeds maximum 2MB size limit' });
            }

            const randomToken = crypto.randomBytes(12).toString('hex');
            const isAudio = mimeType.startsWith('audio/');
            const uniqueFilename = isAudio
                ? `tickets/voice_${body.ticketId}_${Date.now()}_${randomToken}.${extension}`
                : `tickets/ticket_${body.ticketId}_${Date.now()}_${randomToken}.${extension}`;

            // Isolate uploads into ticket-attachments rather than shared gallery storage.
            const bucketName = 'ticket-attachments';
            const normalizedMime = mimeType === 'image/jpg' ? 'image/jpeg' : mimeType;
            const uploadHeaders = {
                apikey: supabaseConfig.serviceKey,
                Authorization: `Bearer ${supabaseConfig.serviceKey}`,
                'Content-Type': normalizedMime,
                'x-upsert': 'false'
            };

            let uploadRes = await fetch(`${supabaseConfig.url}/storage/v1/object/${bucketName}/${uniqueFilename}`, {
                method: 'POST',
                headers: uploadHeaders,
                body: buffer
            });

            if (!uploadRes.ok) {
                await supabaseRest(supabaseConfig, '/storage/v1/bucket', {
                    method: 'POST',
                    prefer: '',
                    body: {
                        id: bucketName,
                        name: bucketName,
                        public: true,
                        file_size_limit: 2097152,
                        allowed_mime_types: ['image/webp', 'image/png', 'image/jpeg', 'image/gif', 'audio/webm', 'audio/ogg', 'audio/mp4', 'audio/mpeg']
                    }
                }).catch(err => console.warn('[Ticket Bucket Auto-Create Warning]:', err?.message || err));

                uploadRes = await fetch(`${supabaseConfig.url}/storage/v1/object/${bucketName}/${uniqueFilename}`, {
                    method: 'POST',
                    headers: uploadHeaders,
                    body: buffer
                });
            }

            if (!uploadRes.ok) {
                const errText = await uploadRes.text().catch(() => '');
                console.error('[Storage Upload Error]:', errText);
                return res.status(500).json({ error: 'Failed to upload screenshot to storage.' });
            }

            return res.status(200).json({
                success: true,
                url: `${supabaseConfig.url}/storage/v1/object/public/${bucketName}/${uniqueFilename}`
            });
        }

        if (action === 'close') {
            if (!body.ticketId) {
                return res.status(400).json({ error: 'Ticket ID is required' });
            }

            const auth = await authorizeTicketAccess(supabaseConfig, {
                ticketId: body.ticketId,
                sessionToken: body.sessionToken,
                email: body.email
            });
            if (!auth.ok) {
                return res.status(auth.status).json({ error: auth.error });
            }

            const patchRes = await supabaseRest(supabaseConfig, `/rest/v1/tickets?id=eq.${body.ticketId}`, {
                method: 'PATCH',
                prefer: 'return=minimal',
                body: { status: 'closed', updated_at: new Date().toISOString() },
                parseJson: false
            });
            if (!patchRes.ok) {
                console.error('[Ticket Close Error]:', patchRes.status, patchRes.text);
                return res.status(500).json({ error: 'Failed to close ticket. Please try again.' });
            }

            if (body.rating && Number(body.rating) >= 1 && Number(body.rating) <= 5) {
                const ratingCheck = await supabaseRest(
                    supabaseConfig,
                    `/rest/v1/ticket_messages?ticket_id=eq.${body.ticketId}&message=like.*Player%20Rating*&select=id&limit=1`
                );
                if (Array.isArray(ratingCheck.data) && ratingCheck.data.length > 0) {
                    return res.status(200).json({ success: true, alreadyRated: true });
                }

                const stars = '⭐'.repeat(Number(body.rating));
                const feedbackText = sanitizeDiscordText(typeof body.feedback === 'string' ? body.feedback.trim() : '');
                await supabaseRest(supabaseConfig, '/rest/v1/ticket_messages', {
                    method: 'POST',
                    prefer: 'return=minimal',
                    body: {
                        ticket_id: body.ticketId,
                        sender: 'system',
                        sender_name: 'HavenCraft Feedback',
                        message: `⭐ Player Rating: ${stars} (${body.rating}/5)${feedbackText ? `\n📝 Comment: ${feedbackText}` : ''}`
                    },
                    parseJson: false
                });
            } else {
                const closeCheck = await supabaseRest(
                    supabaseConfig,
                    `/rest/v1/ticket_messages?ticket_id=eq.${body.ticketId}&message=like.*resolved%20and%20closed*&select=id&limit=1`
                );
                if (!Array.isArray(closeCheck.data) || closeCheck.data.length === 0) {
                    await supabaseRest(supabaseConfig, '/rest/v1/ticket_messages', {
                        method: 'POST',
                        prefer: 'return=minimal',
                        body: {
                            ticket_id: body.ticketId,
                            sender: 'staff',
                            sender_name: 'HavenCraft System',
                            message: '🔒 Ticket resolved and closed by user.'
                        },
                        parseJson: false
                    });
                }
            }

            return res.status(200).json({ success: true });
        }

        // Default action: create ticket
        const rateLimit = checkRateLimit(clientIp, 3, 120 * 1000);
        if (rateLimit.limited) {
            return res.status(429).json({
                error: `Too many submissions. Please wait ${rateLimit.retryAfter}s before opening another ticket.`
            });
        }

        if (body.botcheck) {
            return res.status(200).json({ success: true, ticket: { ticket_code: 'HC-TEST' } });
        }

        const captchaToken = body.captchaToken || body['cf-turnstile-response'] || '';
        const captchaResult = await verifyTurnstile(captchaToken, clientIp, 'contact');
        if (!captchaResult.success) {
            return res.status(400).json({ error: captchaResult.error });
        }

        const name = typeof body.name === 'string' ? body.name.trim() : '';
        const email = typeof body.email === 'string' ? body.email.trim() : '';
        const subject = typeof body.subject === 'string' && body.subject.trim() ? body.subject.trim() : 'General Support';
        const rawMessage = typeof body.message === 'string' ? body.message.trim() : '';
        const message = sanitizeDiscordText(rawMessage);

        if (!name || !email || !message) {
            return res.status(400).json({ error: 'Name, email, and message are required' });
        }
        if (name.length < 2 || name.length > 100) {
            return res.status(400).json({ error: 'Name must be between 2 and 100 characters' });
        }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
            return res.status(400).json({ error: 'Please enter a valid email address' });
        }
        if (message.length < 5 || message.length > 3000) {
            return res.status(400).json({ error: 'Message must be between 5 and 3000 characters' });
        }

        const extraLines = [];
        if (typeof body.discordTag === 'string' && body.discordTag.trim() && !message.includes(body.discordTag.trim())) {
            extraLines.push(`💬 **Discord:** \`${sanitizeDiscordText(body.discordTag.trim(), { stripBackticks: true })}\``);
        }
        if (typeof body.trxId === 'string' && body.trxId.trim() && !message.includes(body.trxId.trim())) {
            extraLines.push(`💳 **TrxID:** \`${sanitizeDiscordText(body.trxId.trim(), { stripBackticks: true })}\``);
        }
        if (typeof body.paymentMethod === 'string' && body.paymentMethod.trim()) {
            extraLines.push(`💰 **Method:** ${sanitizeDiscordText(body.paymentMethod.trim())}`);
        }
        if (typeof body.platform === 'string' && body.platform.trim()) {
            extraLines.push(`🎮 **Platform:** ${sanitizeDiscordText(body.platform.trim())}`);
        }

        const initialFormattedMessage = extraLines.length > 0
            ? `${extraLines.join('\n')}\n\n📝 **Details:**\n${message}`
            : message;

        let ticketCode = await generateSequentialTicketCode(supabaseConfig);
        let createdTicket = null;
        let lastErrorText = '';

        // Sequential HC-XXXX code is derived from recent rows, so concurrent creates retry on 409 conflicts.
        for (let attempt = 0; attempt < 20; attempt++) {
            const ticketRes = await supabaseRest(supabaseConfig, '/rest/v1/tickets', {
                method: 'POST',
                body: {
                    ticket_code: ticketCode,
                    user_name: name,
                    user_email: email,
                    subject,
                    status: 'open'
                }
            });

            if (ticketRes.ok) {
                createdTicket = Array.isArray(ticketRes.data) ? ticketRes.data[0] : ticketRes.data;
                break;
            }

            lastErrorText = ticketRes.text;
            console.warn(`[Ticket Create Attempt ${attempt + 1}] Code ${ticketCode} rejected (HTTP ${ticketRes.status}):`, lastErrorText);

            if (ticketRes.status !== 409) {
                break;
            }
            const currentNum = parseInt(ticketCode.replace(/^HC-/i, ''), 10) || 1000;
            ticketCode = `HC-${currentNum + 1}`;
        }

        if (!createdTicket) {
            console.error('[Ticket Create Failed]:', lastErrorText);
            return res.status(500).json({ error: 'Failed to create support ticket in database. Please try again shortly.' });
        }

        const initialMsgRes = await supabaseRest(supabaseConfig, '/rest/v1/ticket_messages', {
            method: 'POST',
            prefer: 'return=minimal',
            body: {
                ticket_id: createdTicket.id,
                sender: 'player',
                sender_name: name,
                message: initialFormattedMessage
            },
            parseJson: false
        });

        if (!initialMsgRes.ok) {
            console.error('[Ticket Initial Message Error]:', initialMsgRes.status, initialMsgRes.text);
            // Roll back orphan ticket header if its initial message could not be saved.
            await supabaseRest(supabaseConfig, `/rest/v1/tickets?id=eq.${createdTicket.id}`, {
                method: 'DELETE',
                prefer: 'return=minimal',
                parseJson: false
            }).catch(delErr => console.error('[Orphan Ticket Rollback Error]:', delErr?.message || delErr));

            return res.status(500).json({ error: 'Failed to initialize support ticket message. Please try again shortly.' });
        }

        const sessionToken = computeSessionToken(createdTicket.id, createdTicket.user_email, supabaseConfig.serviceKey);

        return res.status(200).json({
            success: true,
            ticket: createdTicket,
            sessionToken
        });
    } catch (error) {
        console.error('[Contact API Handler Error]:', error);
        return res.status(500).json({ error: 'Server error processing request. Please try again.' });
    }
}
