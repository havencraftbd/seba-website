/**
 * HavenCraft SMP — AI Ticket Support & Sales Assistant Gateway (Vercel Serverless Function)
 *
 * Architecture & Zero-Leak Security:
 * 1. Gateway Isolation: Groq API NEVER connects to PostgreSQL directly. Only pre-sanitized strings are passed.
 * 2. Strict Whitelist Context: Only public shop_items (perks/pricing) and site_settings (IP/port) are read.
 *    No phone numbers, financial ledgers, staff profiles, or app secrets are ever read or exposed.
 * 3. Smart Handover: If human staff has replied, auto-reply exits immediately to avoid interrupting staff.
 * 4. Anti-AI-Slop Persona: Speaks naturally, warmly, and sharply like an expert Minecraft server team member.
 *    No robotic fillers, no corporate fluff, perfect Banglish/Bengali/English adaptation.
 * 5. Resilient Fallback: Primary llama-3.3-70b-versatile, auto-fallback to llama-3.1-8b-instant on rate limit.
 */

const rateLimitMap = new Map();
function checkRateLimit(key, limit = 25, windowMs = 60 * 1000) {
  const now = Date.now();
  if (rateLimitMap.size > 500) {
    for (const [k, v] of rateLimitMap.entries()) {
      if (now > v.resetTime) rateLimitMap.delete(k);
    }
  }
  const rec = rateLimitMap.get(key);
  if (!rec || now > rec.resetTime) {
    rateLimitMap.set(key, { count: 1, resetTime: now + windowMs });
    return { limited: false };
  }
  if (rec.count >= limit) {
    return { limited: true, retryAfter: Math.ceil((rec.resetTime - now) / 1000) };
  }
  rec.count++;
  return { limited: false };
}

// In-memory cache for available models from Groq
let cachedGroqModels = null;
let lastModelFetch = 0;

async function getAvailableGroqModels(apiKey) {
  const now = Date.now();
  if (cachedGroqModels && Array.isArray(cachedGroqModels) && cachedGroqModels.length > 0 && (now - lastModelFetch < 20 * 60 * 1000)) {
    return cachedGroqModels;
  }
  try {
    const resp = await fetch('https://api.groq.com/openai/v1/models', {
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      }
    });
    if (!resp.ok) {
      console.warn('Groq models lookup returned:', resp.status);
      return [];
    }
    const data = await resp.json();
    const list = Array.isArray(data?.data) ? data.data.map(m => m.id) : [];
    const chatModels = list.filter(id =>
      typeof id === 'string' &&
      !id.includes('whisper') &&
      !id.includes('prompt-guard') &&
      !id.includes('orpheus') &&
      !id.includes('safeguard') &&
      !id.includes('tts')
    );
    if (chatModels.length > 0) {
      cachedGroqModels = chatModels;
      lastModelFetch = now;
    }
    return chatModels;
  } catch (err) {
    console.warn('Groq models auto-discovery error:', err.message);
    return [];
  }
}

const GEMINI_CANDIDATE_MODELS = [
  'gemini-1.5-flash',
  'gemini-2.0-flash',
  'gemini-2.5-flash'
];

const PREFERRED_CHAT_MODELS = [
  'openai/gpt-oss-120b',
  'llama-3.3-70b-versatile',
  'openai/gpt-oss-20b',
  'qwen/qwen3.8-27b',
  'llama-3.1-8b-instant'
];

const PREFERRED_VISION_MODELS = [
  'llama-3.2-11b-vision-preview',
  'llama-3.2-90b-vision-preview'
];

/**
 * Extract image URL or base64 data URI from message or description
 */
function extractImageUrl(text) {
  if (!text || typeof text !== 'string') return null;
  const tagMatch = text.match(/\[image:\s*([\s\S]+?)\]/i);
  if (tagMatch && tagMatch[1]) return tagMatch[1].trim();
  const directMatch = text.match(/(https?:\/\/[^\s<>"'\)]+?\.(?:png|jpe?g|webp|gif)(?:\?[^\s<>"'\)]*)?)/i);
  if (directMatch && directMatch[1]) return directMatch[1].trim();
  const storageMatch = text.match(/(https?:\/\/[^\s<>"'\)]+?\/ticket-attachments\/[^\s<>"'\)]+)/i);
  if (storageMatch && storageMatch[1]) return storageMatch[1].trim();
  const dataMatch = text.match(/(data:image\/(?:png|jpe?g|webp|gif);base64,[a-zA-Z0-9+/=]+)/i);
  if (dataMatch && dataMatch[1]) return dataMatch[1].trim();
  return null;
}

/**
 * Clean image markup from text so we don't pollute conversational prompts with giant base64 or long links
 */
function stripImageTags(text) {
  if (!text || typeof text !== 'string') return '';
  return text
    .replace(/\[image:\s*[\s\S]+?\]/gi, '')
    .replace(/https?:\/\/[^\s<>"'\)]+?\/ticket-attachments\/[^\s<>"'\)]+/gi, '')
    .trim();
}

/**
 * Safe fetch & convert image to base64 object for Gemini inlineData and Groq Vision
 */
async function fetchImageAsBase64(imageUrl) {
  if (!imageUrl || typeof imageUrl !== 'string') return null;
  if (imageUrl.startsWith('data:image/')) {
    const match = imageUrl.match(/^data:(image\/[a-zA-Z0-9.+_-]+);base64,(.+)$/);
    if (match) {
      return { mimeType: match[1], base64: match[2], dataUrl: imageUrl };
    }
  }
  if (/^https?:\/\//i.test(imageUrl)) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 8000);
      const imgResp = await fetch(imageUrl, { signal: controller.signal });
      clearTimeout(timeoutId);
      if (imgResp.ok) {
        let mimeType = (imgResp.headers.get('content-type') || '').toLowerCase();
        if (mimeType.includes(';')) mimeType = mimeType.split(';')[0].trim();
        if (!mimeType || mimeType === 'application/octet-stream') {
          if (/\.png(\?.*)?$/i.test(imageUrl)) mimeType = 'image/png';
          else if (/\.webp(\?.*)?$/i.test(imageUrl)) mimeType = 'image/webp';
          else if (/\.jpe?g(\?.*)?$/i.test(imageUrl)) mimeType = 'image/jpeg';
          else if (/\.gif(\?.*)?$/i.test(imageUrl)) mimeType = 'image/gif';
          else mimeType = 'image/jpeg';
        }

        const arrayBuffer = await imgResp.arrayBuffer();
        if (arrayBuffer.byteLength > 0 && arrayBuffer.byteLength <= 8 * 1024 * 1024) {
          const base64 = Buffer.from(arrayBuffer).toString('base64');
          return { mimeType, base64, dataUrl: `data:${mimeType};base64,${base64}` };
        }
      }
    } catch (err) {
      console.warn('Image pre-fetch for vision failed:', err.message);
    }
  }
  return null;
}

/**
 * Call Google Gemini API (Flash models) with multimodal image support
 */
async function callGemini(modelName, apiKey, systemInstruction, conversationMessages) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;

  const contents = [];
  for (let i = 0; i < conversationMessages.length; i++) {
    const m = conversationMessages[i];
    if (m.role === 'system') continue;

    const geminiRole = (m.role === 'assistant') ? 'model' : 'user';
    const parts = [];

    // If this message has an attached image, inject inlineData for native Gemini vision
    if (m.attachedImage && m.attachedImage.base64) {
      parts.push({
        inlineData: {
          mimeType: m.attachedImage.mimeType || 'image/jpeg',
          data: m.attachedImage.base64
        }
      });
    }

    if (m.content) {
      parts.push({ text: m.content });
    }

    if (parts.length > 0) {
      contents.push({ role: geminiRole, parts });
    }
  }

  const payload = {
    system_instruction: {
      parts: [{ text: systemInstruction }]
    },
    contents: contents,
    generationConfig: {
      temperature: 0.35,
      maxOutputTokens: 1400,
      topP: 0.95
    }
  };

  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (!resp.ok) {
    const errText = await resp.text();
    const err = new Error(`Gemini ${modelName} error (${resp.status}): ${errText}`);
    err.status = resp.status;
    throw err;
  }

  const data = await resp.json();
  const textParts = data.candidates?.[0]?.content?.parts?.map(p => p.text).join('') || '';
  return textParts.trim();
}

/**
 * Inspects a player screenshot using Google Gemini Vision (Flash models)
 */
async function inspectImageWithGemini(playerImageObj, geminiApiKey, geminiModel = 'gemini-2.5-flash') {
  if (!playerImageObj?.base64 || !geminiApiKey) return null;
  try {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${geminiApiKey}`;

    const promptText = `You are a Minecraft server technical support OCR & diagnosis assistant for HavenCraft SMP.
Examine this player screenshot carefully:
1. Extract ALL visible text, error messages, dialog headings, buttons, and error codes verbatim.
2. Specifically identify Minecraft Bedrock error codes (e.g. "Terracotta", "NetherNet", "Glowstone", "Crossbow", "Multiplayer Connection Failed") or Java disconnect reasons (e.g. "Connection refused", "Internal exception", "Not allowed", "Outdated client", "io.netty").
3. State whether this is Minecraft Bedrock Edition (Pocket Edition / Windows Bedrock / Console), Minecraft Java Edition (PC), or a launcher.
4. Provide a 1-2 sentence technical summary of the exact problem shown.

Format your output like:
Platform: <Bedrock Edition | Java Edition | Launcher>
Exact On-Screen Text / Code: <quote exact text>
Technical Diagnosis: <what this error means>`;

    const payload = {
      contents: [
        {
          role: 'user',
          parts: [
            {
              inlineData: {
                mimeType: playerImageObj.mimeType || 'image/jpeg',
                data: playerImageObj.base64
              }
            },
            {
              text: promptText
            }
          ]
        }
      ],
      generationConfig: {
        temperature: 0.1,
        maxOutputTokens: 350,
        topP: 0.95
      }
    };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);
    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (!resp.ok) {
      const errText = await resp.text();
      console.warn(`Gemini Vision (${geminiModel}) error (${resp.status}):`, errText);
      return null;
    }

    const data = await resp.json();
    const textParts = data.candidates?.[0]?.content?.parts?.map(p => p.text).join('') || '';
    return textParts.trim() || null;
  } catch (err) {
    console.warn(`Gemini Vision (${geminiModel}) inspection exception:`, err.message);
    return null;
  }
}

/**
 * Inspects a player screenshot using Groq Vision model
 */
async function inspectImageWithGroq(imageUrl, groqApiKey, visionModel = 'llama-3.2-11b-vision-preview') {
  if (!imageUrl || !groqApiKey) return null;
  try {
    let finalImageUrl = imageUrl;

    // If it's a remote URL, fetch and convert to base64 Data URL so Groq receives the bytes directly
    if (/^https?:\/\//i.test(imageUrl)) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 6000);
        const imgResp = await fetch(imageUrl, { signal: controller.signal });
        clearTimeout(timeoutId);
        if (imgResp.ok) {
          const contentType = imgResp.headers.get('content-type') || 'image/jpeg';
          const arrayBuffer = await imgResp.arrayBuffer();
          if (arrayBuffer.byteLength > 0 && arrayBuffer.byteLength <= 5 * 1024 * 1024) {
            const base64 = Buffer.from(arrayBuffer).toString('base64');
            finalImageUrl = `data:${contentType};base64,${base64}`;
          }
        }
      } catch (fetchErr) {
        console.warn('Image pre-fetch for vision inspection failed, attempting direct URL:', fetchErr.message);
      }
    }

    const payload = {
      model: visionModel,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'text',
              text: 'You are a Minecraft server technical support expert inspecting a player screenshot for HavenCraft SMP.\n' +
                'Describe concisely (2-4 sentences max):\n' +
                '1. What exact error message, disconnect screen, launcher warning, or UI text is displayed? (Quote exact words like "Multiplayer Connection Failed", "NetherNet", "Connection Refused", "Whitelist", etc.)\n' +
                '2. Is this Minecraft Java Edition or Bedrock Edition?\n' +
                '3. What is the technical issue shown?\n' +
                'Return only the concise factual summary.'
            },
            {
              type: 'image_url',
              image_url: {
                url: finalImageUrl
              }
            }
          ]
        }
      ],
      temperature: 0.1,
      max_tokens: 300
    };

    const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${groqApiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (!resp.ok) {
      const errTxt = await resp.text();
      console.warn(`Groq Vision (${visionModel}) error (${resp.status}):`, errTxt);
      return null;
    }

    const data = await resp.json();
    return data?.choices?.[0]?.message?.content?.trim() || null;
  } catch (err) {
    console.warn('Groq Vision inspection exception:', err.message);
    return null;
  }
}

async function analyzePlayerImage(imageUrl, playerImageObj, geminiApiKey, groqApiKey, availableModels = []) {
  if (!imageUrl && !playerImageObj) return null;

  // 1. Primary: Use Gemini Vision (Gemini 2.5 Flash / 2.0 Flash / 1.5 Flash)
  if (geminiApiKey && playerImageObj?.base64) {
    for (const gm of GEMINI_CANDIDATE_MODELS) {
      try {
        const geminiVisionResult = await inspectImageWithGemini(playerImageObj, geminiApiKey, gm);
        if (geminiVisionResult) {
          console.log(`[Ticket AI] Vision analysis successful via Gemini (${gm}):`, geminiVisionResult.slice(0, 100));
          return geminiVisionResult;
        }
      } catch (gErr) {
        console.warn(`[Ticket AI] Gemini vision attempt failed for ${gm}:`, gErr.message);
      }
    }
  }

  // 2. Fallback: Use Groq Vision if available
  if (groqApiKey) {
    const visionCandidates = [];
    for (const p of PREFERRED_VISION_MODELS) {
      if (availableModels.includes(p)) visionCandidates.push(p);
    }
    for (const m of availableModels) {
      if (typeof m === 'string' && m.includes('vision') && !visionCandidates.includes(m)) {
        visionCandidates.push(m);
      }
    }

    const sourceUrl = playerImageObj?.dataUrl || imageUrl;
    for (const vm of visionCandidates) {
      const res = await inspectImageWithGroq(sourceUrl, groqApiKey, vm);
      if (res) return res;
    }
  }

  return null;
}

export default async function handler(req, res) {
  // CORS Configuration
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

  res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, x-ticket-ai-secret');
  res.setHeader('Vary', 'Origin');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // Health/Diagnostic check (GET)
  if (req.method === 'GET') {
    const groqKey = (process.env.GROQ_API_KEY || '').trim();
    const geminiKey = (process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || '').trim();
    let modelsList = [];
    if (groqKey) {
      try {
        modelsList = await getAvailableGroqModels(groqKey);
      } catch (_) { }
    }
    return res.status(200).json({
      status: 'active',
      service: 'HavenCraft Ticket AI Gateway',
      geminiConfigured: Boolean(geminiKey),
      groqConfigured: Boolean(groqKey),
      primaryEngine: geminiKey ? 'Google Gemini (Flash)' : (groqKey ? 'Groq LLaMA' : 'None'),
      geminiCandidateModels: GEMINI_CANDIDATE_MODELS,
      availableGroqModels: modelsList,
      preferredGroqOrder: PREFERRED_CHAT_MODELS
    });
  }

  if (req.method !== 'POST') {
    res.setHeader('Allow', ['POST', 'GET']);
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const clientIp = (req.headers['x-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim();
  const rl = checkRateLimit(`ai-ip-${clientIp}`, 25, 60 * 1000);
  if (rl.limited) {
    res.setHeader('Retry-After', String(rl.retryAfter));
    return res.status(429).json({ error: `Rate limit exceeded. Try again in ${rl.retryAfter}s.` });
  }

  const supabaseUrl = (process.env.SUPABASE_URL || 'https://nvcnowvasqcsygtbwktw.supabase.co').replace(/\/$/, '');
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_KEY;
  const groqApiKey = (process.env.GROQ_API_KEY || '').trim();
  const geminiApiKey = (process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || '').trim();

  if (!groqApiKey && !geminiApiKey) {
    return res.status(500).json({ error: 'AI Gateway Error: Neither GEMINI_API_KEY nor GROQ_API_KEY is configured on the server. Please configure at least one.' });
  }
  if (!serviceKey) {
    return res.status(500).json({ error: 'Supabase Service Key is not configured.' });
  }

  try {
    const { action = 'auto_reply', ticket_id, ai_enabled } = req.body || {};

    if (!ticket_id) {
      return res.status(400).json({ error: 'Missing ticket_id parameter.' });
    }

    // 1. Verify Caller Authentication
    const internalSecret = (process.env.TICKET_AI_SECRET || '').trim();
    const providedSecret = (req.headers['x-ticket-ai-secret'] || req.body?.secret || '').trim();
    const authHeader = req.headers['authorization'] || '';
    const userToken = authHeader.replace(/^Bearer\s+/i, '').trim();

    let isWebhookAuth = Boolean(internalSecret && providedSecret && internalSecret === providedSecret);
    let isStaffAuth = false;
    let staffName = 'Staff';

    if (!isWebhookAuth && userToken) {
      // Validate staff Supabase JWT
      const authVerifyResp = await fetch(`${supabaseUrl}/auth/v1/user`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${userToken}`
        }
      });
      if (authVerifyResp.ok) {
        const callerUser = await authVerifyResp.json();
        const profResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${encodeURIComponent(callerUser.id)}&select=is_active,status,role,minecraft_ign`, {
          headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`
          }
        });
        const profiles = await profResp.json();
        const profile = Array.isArray(profiles) ? profiles[0] : null;
        if (profile && profile.is_active && profile.status === 'active') {
          isStaffAuth = true;
          staffName = profile.minecraft_ign || 'Staff';
        }
      }
    }

    // Permit webhook calls, staff calls, or internal pg_net triggers
    if (!isWebhookAuth && !isStaffAuth) {
      // If no internal secret is configured yet, allow trigger requests with server service-role authorization
      const reqApiKey = req.headers['apikey'] || '';
      if (reqApiKey !== serviceKey) {
        return res.status(401).json({ error: 'Unauthorized: Valid staff token or ticket AI secret required.' });
      }
    }

    // -------------------------------------------------------------------------
    // ACTION: TOGGLE AI FOR TICKET (Staff only)
    // -------------------------------------------------------------------------
    if (action === 'toggle') {
      const targetState = Boolean(ai_enabled);
      await fetch(`${supabaseUrl}/rest/v1/tickets?id=eq.${ticket_id}`, {
        method: 'PATCH',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json',
          'Prefer': 'return=minimal'
        },
        body: JSON.stringify({ ai_enabled: targetState })
      });
      return res.status(200).json({ success: true, ticket_id, ai_enabled: targetState });
    }

    // -------------------------------------------------------------------------
    // 2. Load Ticket & Message History
    // -------------------------------------------------------------------------
    const ticketResp = await fetch(`${supabaseUrl}/rest/v1/tickets?id=eq.${ticket_id}&select=*`, {
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`
      }
    });
    const tickets = await ticketResp.json();
    const ticket = Array.isArray(tickets) ? tickets[0] : null;

    if (!ticket) {
      return res.status(404).json({ error: `Ticket #${ticket_id} not found.` });
    }

    // Skip if ticket is closed
    const status = (ticket.status || 'open').toLowerCase();
    if (status === 'closed' || status === 'resolved') {
      return res.status(200).json({ skipped: true, reason: 'Ticket is closed.' });
    }

    // Load message history
    const msgsResp = await fetch(`${supabaseUrl}/rest/v1/ticket_messages?ticket_id=eq.${ticket_id}&order=created_at.asc`, {
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`
      }
    });
    const messages = await msgsResp.json();
    const msgList = Array.isArray(messages) ? messages : [];

    // SMART HANDOVER RULE:
    // If ANY human staff member has sent a message, auto-reply exits immediately!
    const humanStaffReplied = msgList.some(m =>
      m.sender === 'staff' &&
      m.sender_name !== 'HavenCraft AI' &&
      m.sender_name !== 'HavenCraft System'
    );

    if (action === 'auto_reply' && humanStaffReplied) {
      return res.status(200).json({ skipped: true, reason: 'Human staff has taken over this ticket.' });
    }

    // Check if AI is explicitly disabled for this ticket (handed over to staff)
    if (ticket.ai_enabled === false && action !== 'suggest') {
      // If no human staff has replied yet, check if player has been waiting and sent follow-up messages
      const lastPlayerMsg = msgList.slice().reverse().find(m => m.sender === 'player');
      const lastAiMsg = msgList.slice().reverse().find(m => m.sender_name === 'HavenCraft AI');

      const nowMs = Date.now();
      const lastAiTime = lastAiMsg?.created_at ? new Date(lastAiMsg.created_at).getTime() : 0;
      const minutesSinceLastAi = (nowMs - lastAiTime) / (60 * 1000);

      const playerWaitingText = (lastPlayerMsg?.message || '').toLowerCase();
      const isImpatientQuestion = /\b(keu nai|keu ki nai|hello|kothay|reply|kobe|kotokkhon|shomoy lagbe|waiting|anyone|admin|staff)\b|\?{2,}/i.test(playerWaitingText);

      // Only reassure if player explicitly asks while waiting AND at least 5 minutes have passed since last AI message
      if (isImpatientQuestion && minutesSinceLastAi >= 5) {
        const isBanglish = /[a-zA-Z]/.test(playerWaitingText) && !/[\u0980-\u09FF]/.test(playerWaitingText);
        const reassuranceReply = isBanglish
          ? "Amader staff team-ke alert pathano hoyeche. Ekjon staff member online ashamatroi apnar sathe jogajog korbe, onugroho kore kichukhon opekkha korun."
          : "আমাদের স্টাফ টিমকে ইতিমধ্যেই জানানো হয়েছে। একজন টিম মেম্বার অনলাইনে আসামাত্রই আপনার সাথে এখানে কথা বলবেন, অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন।";

        await fetch(`${supabaseUrl}/rest/v1/ticket_messages`, {
          method: 'POST',
          headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            ticket_id: ticket.id,
            sender: 'staff',
            sender_name: 'HavenCraft AI',
            message: `${reassuranceReply} [STAFF_PENDING_REMINDER]`
          })
        });

        return res.status(200).json({ success: true, reason: 'Staff waiting reassurance sent' });
      }

      return res.status(200).json({ skipped: true, reason: 'AI auto-reply is disabled for this ticket.' });
    }

    // Debounce / Await Player Response Rule:
    // If the very latest message is already from HavenCraft AI, do not auto-reply again (awaiting player)
    const lastMessage = msgList[msgList.length - 1];
    if (action === 'auto_reply' && lastMessage && lastMessage.sender_name === 'HavenCraft AI') {
      return res.status(200).json({
        skipped: true,
        reason: 'AI is waiting for the player to respond.'
      });
    }

    // Concurrency / duplicate check: Count consecutive AI replies
    let consecutiveAiReplies = 0;
    for (let i = msgList.length - 1; i >= 0; i--) {
      if (msgList[i].sender_name === 'HavenCraft AI') {
        consecutiveAiReplies++;
      } else {
        break;
      }
    }

    // Limit consecutive AI replies to 3 to prevent spam loops or burning tokens
    if (action === 'auto_reply' && consecutiveAiReplies >= 3) {
      return res.status(200).json({
        skipped: true,
        reason: 'Maximum consecutive AI replies (3) reached. Awaiting player or staff response.'
      });
    }

    // -------------------------------------------------------------------------
    // 3. Collect Sanitized Knowledge Base Context & Connectivity
    // -------------------------------------------------------------------------
    const [shopResp, settingsResp] = await Promise.all([
      fetch(`${supabaseUrl}/rest/v1/shop_items?select=type,name,category,bdt,usd,perks&active=eq.true`, {
        headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${serviceKey}` }
      }),
      fetch(`${supabaseUrl}/rest/v1/site_settings?key=in.(server_connectivity,server_info)&select=key,value`, {
        headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${serviceKey}` }
      })
    ]);

    const shopItems = await shopResp.json();
    const settingsRows = await settingsResp.json();

    let dbConnectivity = {};
    if (Array.isArray(settingsRows)) {
      const connRow = settingsRows.find(r => r.key === 'server_connectivity') || settingsRows.find(r => r.key === 'server_info');
      if (connRow?.value && typeof connRow.value === 'object') {
        dbConnectivity = connRow.value;
      }
    }

    const serverInfo = {
      serverIp: dbConnectivity.serverIp || 'mc.havencraft.pro',
      port: String(dbConnectivity.port || dbConnectivity.javaPort || '2566'),
      bedrockPort: String(dbConnectivity.bedrockPort || '2025'),
      seasonName: dbConnectivity.seasonName || 'Season 6',
      discordUrl: dbConnectivity.discordUrl || 'https://dc.havencraft.pro'
    };

    // Format Ranks & Spawners for Knowledge
    const ranksList = (Array.isArray(shopItems) ? shopItems : [])
      .filter(i => i.type === 'rank')
      .map(r => {
        let perkSummary = r.perks || '';
        try {
          const parsed = typeof r.perks === 'string' ? JSON.parse(r.perks) : r.perks;
          if (parsed && parsed.summary) perkSummary = `${parsed.summary} (Key items: Mace, Elytra, 3x3 Pickaxe, Claim Blocks)`;
        } catch (_) { }
        return `• ${r.name} (${r.category}): ৳${r.bdt} BDT ($${r.usd} USD) — ${perkSummary}`;
      }).join('\n');

    const spawnersList = (Array.isArray(shopItems) ? shopItems : [])
      .filter(i => i.type === 'spawner')
      .map(s => `• ${s.name} (${s.category}): ৳${s.bdt} BDT ($${s.usd} USD)`)
      .join('\n');

    // Safe Order Status Lookup (IF player is inquiring about an order)
    let sanitizedOrderNote = '';
    const userTextContext = `${ticket.subject} ${ticket.description} ${msgList.map(m => m.message).join(' ')}`.toLowerCase();
    const mentionsOrder = userTextContext.includes('order') ||
      userTextContext.includes('ord-') ||
      userTextContext.includes('buy') ||
      userTextContext.includes('taka') ||
      userTextContext.includes('kinechi') ||
      userTextContext.includes('rank pailam na');

    if (mentionsOrder && ticket.user_name) {
      try {
        const orderResp = await fetch(`${supabaseUrl}/rest/v1/store_orders?username=ilike.${encodeURIComponent(ticket.user_name)}&select=order_code,item_name,price,status,created_at&order=created_at.desc&limit=2`, {
          headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${serviceKey}` }
        });
        const orders = await orderResp.json();
        if (Array.isArray(orders) && orders.length > 0) {
          sanitizedOrderNote = orders.map(o =>
            `[Order: ${o.order_code} | Item: ${o.item_name} | Price: ${o.price} | Status: ${o.status}]`
          ).join('\n');
        }
      } catch (err) {
        console.warn('Order context lookup failed:', err);
      }
    }

    // Auto-discover models available for this specific Groq key early for vision & chat
    const availableModels = await getAvailableGroqModels(groqApiKey);

    // -------------------------------------------------------------------------
    // 3.5. Inspect Attached Player Media (Vision OCR & Error Diagnosis)
    // -------------------------------------------------------------------------
    let latestPlayerImageUrl = null;
    let imageFoundLocation = ''; // 'ticket_description' or 'message_ID'

    for (let i = msgList.length - 1; i >= 0; i--) {
      if (msgList[i].sender === 'player') {
        const found = extractImageUrl(msgList[i].message);
        if (found) {
          latestPlayerImageUrl = found;
          imageFoundLocation = `message_${msgList[i].id}`;
          break;
        }
      }
    }

    if (!latestPlayerImageUrl) {
      const foundDesc = extractImageUrl(ticket.description || ticket.message || '');
      if (foundDesc) {
        latestPlayerImageUrl = foundDesc;
        imageFoundLocation = 'ticket_description';
      }
    }

    let playerImageObj = null;
    let playerImageAnalysis = null;

    if (latestPlayerImageUrl) {
      try {
        // Pre-fetch image buffer as base64 for Gemini native vision and Groq fallback
        playerImageObj = await fetchImageAsBase64(latestPlayerImageUrl);
        const visionSourceUrl = playerImageObj?.dataUrl || latestPlayerImageUrl;
        playerImageAnalysis = await analyzePlayerImage(visionSourceUrl, playerImageObj, geminiApiKey, groqApiKey, availableModels);
        if (playerImageAnalysis) {
          console.log(`[Ticket AI] Analyzed player screenshot for ticket #${ticket.id}:`, playerImageAnalysis.slice(0, 120));
        }
      } catch (imgErr) {
        console.warn('[Ticket AI] Image inspection error:', imgErr.message);
      }
    }

    // -------------------------------------------------------------------------
    // 4. Construct Natural, Anti-AI-Slop System Prompt
    // -------------------------------------------------------------------------
    const serverIp = serverInfo.serverIp || 'mc.havencraft.pro';
    const javaPort = String(serverInfo.port || '2566');
    const bedrockPort = String(serverInfo.bedrockPort || '2025');

    const systemPrompt = `You are "HavenCraft AI", a helpful, sharp, and natural human-like support moderator for HavenCraft SMP (Minecraft Server).
You are chatting with player "${ticket.user_name || 'Player'}" inside their support ticket.

### SECTION 0: TONE, STYLE & ZERO-EMOJI MANDATE (STRICT ENFORCEMENT)
1. **ABSOLUTE PROHIBITION ON EMOJIS:**
   - YOU MUST NEVER USE EMOJIS in any response under any circumstances (no smileys like 😊, 😄, 😃, no icons like 🎮, 🛡️, 🔒, 👍, 👋, etc.).
   - Every single word must be plain text. Keep every reply clean, mature, and 100% emoji-free.
2. **SUPPORT TONE & MANNERISMS:**
   - Speak like a calm, mature, concise human staff moderator.
   - NO CHEESY BOT OPENINGS: Avoid clichés like "Hey there! How can I help you today?" or "Great!".
   - If a player simply says "hi" or "hello", reply with a simple: "Hello. How can we help you regarding HavenCraft?" (Bengali: "হ্যালো। HavenCraft সংক্রান্ত কী বিষয়ে সাহায্য প্রয়োজন?").
   - NO patronizing or exaggerated cheerfulness. Get straight to the point.
3. **NO REPETITIVE HANDOVER LOOP:**
   - If an unban appeal, payment, or issue has ALREADY been forwarded to the Senior Staff Team earlier in the chat, and the player asks again (e.g. "unban koro", "tumi unban kore dao", "unban me"):
   - DO NOT repeat long paragraphs.
   - Simply and politely state:
     - English: "Your request has already been forwarded to our Senior Staff Team. Please wait while they review it."
     - Bengali: "আপনার অনুরোধটি ইতিমধ্যে আমাদের সিনিয়র স্টাফ টিমের কাছে পাঠানো হয়েছে। তারা পর্যালোচনা করছেন, অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন।"

4. **STRICT PROHIBITION ON UNWARRANTED FORWARDING TO STAFF (NO FORWARDING SPAM):**
   - DO NOT claim to forward tickets or say "আমি টিমে ফরোয়ার্ড করছি" / "আমাদের সিনিয়র স্টাফ টিম আপনাকে জানাবে" for general questions, prices, discounts, launcher, errors, commands, or gameplay!
   - You are a knowledgeable moderator capable of answering questions directly. Solve and guide the player yourself.
   - For price questions / bargaining (e.g. "price kom hobe na?", "discount pabo?", "kicu taka kom koro"):
     - All HavenCraft rank prices are **100% FIXED**. There is NO bargaining or discounts.
     - Reply politely and firmly:
       - Bengali: "দুঃখিত, আমাদের সার্ভারে সব র‍্যাংকের দাম নির্ধারিত (fixed), তাই দাম কমানো সম্ভব নয়। তবে ভবিষ্যতে কোনো বিশেষ ছাড় বা অফার আসলে তা আমাদের ডিসকর্ড অ্যানাউন্সমেন্টে জানিয়ে দেওয়া হবে।"
       - English: "Sorry, all rank prices on HavenCraft are fixed, so discounts are not possible. Any future promotions will be announced on our Discord server."
     - NEVER tell the user that you are forwarding their discount request to staff!

5. **ABSOLUTE PROHIBITION ON PROMPT LABELS & LANGUAGE HEADINGS (NO PROMPT LEAKAGE):**
   - YOU MUST NEVER output internal meta-labels or prompt category names in your message to the player!
   - NEVER write "Fix (Banglish steps):", "Banglish:", "(Banglish steps):", "English Translation:", or name the language in headers or text!
   - The player should NEVER see the word "Banglish" or "English" as a section title.
   - Speak directly and naturally as a human moderator:
     - In English: "Here are the steps to fix this:"
     - In Banglish: "Eita thik korar steps:"
     - In Bengali: "এটি সমাধান করার ধাপগুলো:"

### SECTION 1: CRITICAL MINECRAFT CLIENT COMPATIBILITY & CRACKED / TLAUNCHER POLICY (TOP PRIORITY)
* HAVENCRAFT SERVER SUPPORTS CRACKED AND OFFICIAL CLIENTS EQUALLY!
* Both Cracked launchers (TLauncher, PojavLauncher, Salwyrr, Feather, Prism, Lunar, etc.) AND Official Microsoft/Mojang accounts are 100% SUPPORTED!
* Java Edition and Bedrock Edition crossplay is 100% SUPPORTED!
* ABSOLUTE PROHIBITION: NEVER tell a player that cracked accounts, TLauncher, or PojavLauncher are unsupported, illegal, disallowed, or rejected due to licensing/security. NEVER force or tell a player to buy an official paid Microsoft account! Doing so is a severe hallucination.
* DIAGNOSING THE "NOT ALLOWED" ERROR ON CRACKED / TLAUNCHER / POJAV:
  When a player reports a "not allowed" error when trying to join with TLauncher/Cracked:
  1. **Invalid Username (IGN)**: The username contains spaces, special characters (like #, $, @, !, %, ^, &, *), non-English characters, or is outside the 3-16 character limit. Fix: Change the username in launcher settings to use only letters (a-z, A-Z), numbers (0-9), and underscore (_).
  2. **Username Already Taken / Registered**: The username is already registered by another player on the HavenCraft server. Fix: Change the launcher username to a unique name and reconnect.
  3. **In-Game Registration**: Once connected, cracked players must authenticate:
     - First join: Type \`/register <password> <password>\` (e.g. \`/register 123456 123456\`).
     - Returning join: Type \`/login <password>\`.

### SECTION 2: HAVENCRAFT SERVER CONNECTION INFO
• Java Edition (PC / Mac / Linux):
  - Server IP: **${serverIp}**
  - Port: **${javaPort}**
• Bedrock Edition (Mobile / PE / iOS / Android / Console / Windows Bedrock):
  - Server IP: **${serverIp}**
  - Port: **${bedrockPort}**
* CRITICAL: Bedrock port is currently **${bedrockPort}**. NEVER say Bedrock port is unlisted or missing! Always give port **${bedrockPort}** for Bedrock.

### SECTION 3: HAVENCRAFT SMP IN-GAME RULES & MECHANICS (AUTHORITATIVE)
1. **LAND CLAIMS & UNCLAIMED BASE RULES:**
   - Players have land claim blocks to protect their builds and chests (using a Golden Shovel or /claim commands).
   - Inside a claimed area: 100% safe. Other players cannot break blocks, place blocks, or open chests.
   - Outside claims (Unclaimed wilderness / Unclaimed builds): Taking items, looting chests, and breaking blocks is **ALLOWED**.
   - **NO ITEM REFUNDS / NO ROLLBACKS FOR UNCLAIMED AREAS:** If a player complains that their unclaimed house was looted or griefed, politely explain that unclaimed areas are unprotected by server design. Advise them to always claim their builds and chests using \`/claim\` or a Golden Shovel. Server staff does NOT refund items taken from unclaimed territory.

2. **KEEP INVENTORY RULES (OVERWORLD VS NETHER & END):**
   - **Overworld:** Keep Inventory is **ON** (Enabled). Players do NOT lose their items or XP when they die in the Overworld!
   - **Nether & The End:** Keep Inventory is **OFF** (Disabled). Items and armor DROP upon death in the Nether and the End!

3. **PVP, NEWBIE PROTECTION & NETHER DEATHS:**
   - **Newbie Protection:** New players on HavenCraft have newbie protection and cannot be killed by other players right away.
   - **Nether Deaths & Trapping:**
     - Because Keep Inventory is OFF in the Nether, dying or being killed in the Nether results in lost items.
     - **ITEMS LOST IN THE NETHER ARE NOT REFUNDED (ITEMS BACK DEWA HOY NA):** The server does NOT return items lost from Nether or End deaths.
     - While staff does not encourage trapping or tricking players into the Nether, it is an intended high-risk survival mechanic. Advise players to be cautious, avoid blindly following untrusted players into Nether portals, and store their valuable items in their claimed base before exploring dangerous dimensions.

4. **GENERAL ITEM REFUND & ROLLBACK POLICY:**
   - The server does NOT refund items lost due to natural deaths, Nether/End hazard deaths, void falls, lava, or leaving chests in unclaimed areas.
   - Staff review is strictly reserved for verifiable server-side bugs or glitches supported by clear screenshot/video proof.

### SECTION 4: COMMON MINECRAFT ERRORS & QUICK FIXES
1. **Bedrock "Terracotta" Error (Error Code: Terracotta - An error has occurred):**
   - **What it is:** Official Minecraft Bedrock Edition error code (displayed on screen with an Enderman holding a dirt block and text: 'Search help.minecraft.net for "Terracotta" for more information'). It occurs when the Bedrock client fails Microsoft/Xbox account authentication handshake, the account session token has expired, or the Bedrock UDP connection/port to the server timed out.
   - **How to resolve on HavenCraft (Give these exact steps):**
     1) **Sign Out & Re-Sign into Microsoft Account:** Go to Minecraft **Settings > Account / Profile** and tap **"Sign Out of your Microsoft Account"**. Then tap **"Sign in for free"** to re-authenticate with Microsoft.
     2) **Restart Game & Device:** Completely close the Minecraft app (swipe it away from recent apps on mobile or close from task manager on PC) and restart.
     3) **Verify Server IP & Bedrock Port:** Make sure the server IP is **${serverIp}** and the Bedrock Port is set to **${bedrockPort}** (NOT the default 19132, and NOT Java port ${javaPort}).
     4) **Reset Network Connection:** Switch Wi-Fi off and on, or temporarily switch between Wi-Fi and mobile data to reset the UDP routing.
   - Direct natural phrasing:
     "Screenshot-e jeita dekha jacche eita Minecraft Bedrock-er **Terracotta** error code. Eita sadharonoto Microsoft/Xbox account session expire hole ba Bedrock server port/UDP connection-e mismatch hole hoy. Eita thik korar steps:
     1. Minecraft Settings > Account / Profile-e giye Microsoft account **Sign Out** kore abar **Sign In** koro.
     2. Minecraft app ta completely close/restart koro (recent apps theke kete abar dhuko).
     3. HavenCraft server address **${serverIp}** and Bedrock Port **${bedrockPort}** dewa ache kina check koro (default 19132 ba Java port ${javaPort} dile connect hobe na).
     4. Wi-Fi router restart dao ba mobile hotspot/data diye connect korar chesta koro."

2. **Bedrock "Multiplayer Connection Failed" (NetherNet error):**
   - Common Bedrock Edition glitch. Solutions:
     1) Restart the Minecraft Bedrock application.
     2) Sign out and re-sign into Microsoft/Xbox account inside Minecraft Settings.
     3) Verify Server IP is **${serverIp}** and Bedrock Port is **${bedrockPort}** (NOT default 19132 unless configured).
     4) Check Microsoft account privacy settings to ensure multiplayer is allowed.
3. **"Connection Timed Out" / "Cannot Connect to Server":**
   - Check if IP (**${serverIp}**) and Port (Java: **${javaPort}**, Bedrock: **${bedrockPort}**) are entered correctly.
   - Try changing DNS to 1.1.1.1 (Cloudflare) or 8.8.8.8 (Google), or restart router/internet.
4. **"Outdated Client" / "Outdated Server":**
   - Game version mismatch. Advise updating or switching Minecraft version to match the server.

### SECTION 5: STRICT LANGUAGE RULES & BANGLISH TOKEN OPTIMIZATION (COMPLIANCE MANDATORY)
1. **MATCH PLAYER'S EXACT LANGUAGE STYLE (STRICT MIRRORING):**
   a) **WHEN PLAYER WRITES IN BANGLISH (Bengali in English/Latin letters):**
      - Examples: "kivabe join korbo", "amr ping onek beshi", "fixed koro", "rank kinbo", "koto taka lagbe", "tlauncher diye dhukte partesi na"
      - **YOU MUST REPLY IN NATURAL BANGLISH (Latin letters)!**
      - DO NOT switch to Bengali script when the player writes in Banglish! Replying in Banglish saves over 65% token bandwidth, generates much faster, and feels 100% authentic like a local gamer chatting.
      - Banglish Reply Example: "HavenCraft-e join korar jonno server IP **mc.havencraft.pro** and port **2566** (Bedrock port **2025**). Apnar launcher settings-e IP o port check kore dekhun."

   b) **WHEN PLAYER WRITES IN BENGALI SCRIPT (বাংলা হরফে):**
      - Examples: "আমি সার্ভারে কীভাবে ঢুকব?", "র‍্যাংক কিনতে চাই", "পিং অনেক বেশি"
      - **YOU MUST REPLY IN CLEAR BENGALI SCRIPT (বাংলা)!**
      - Bengali Reply Example: "HavenCraft-এ যুক্ত হতে সার্ভার আইপি **mc.havencraft.pro** এবং পোর্ট **2566** ব্যবহার করুন।"

   c) **WHEN PLAYER WRITES IN ENGLISH:**
      - Examples: "How do I join the server?", "My game is lagging", "What is the store link?"
      - **YOU MUST REPLY IN CLEAR, CONCISE ENGLISH.**

   d) **WHEN PLAYER WRITES IN HINDI (Devanagari script or Hinglish like "kaise join kare", "bhai help karo", "unban kardo"):**
      - Our staff understands Hindi, so you understand their intent completely.
      - **BUT AI IS STRICTLY FORBIDDEN FROM REPLYING IN HINDI.**
      - You MUST reply in **POLITE, COURTEOUS ENGLISH** addressing their problem directly!
      - Example: "Hello! To connect to HavenCraft, use IP **mc.havencraft.pro** with port **2566** (Bedrock port **2025**). Please let us know if you need any further help!"

   e) **WHEN PLAYER WRITES IN ANY OTHER FOREIGN LANGUAGE (Japanese, Russian, Arabic, Spanish, French, etc.):**
      - DO NOT reply in that language!
      - Politely inform them in English that HavenCraft support is only provided in English and Bengali/Banglish, and kindly request them to write in English or Bangla:
      - Example: "Hello! Our support team currently communicates in English and Bengali (বাংলা/Banglish). Could you please describe your issue in English or Bangla so we can help you right away?"

2. **EVALUATE LATEST MESSAGE ONLY (NO STICKY LANGUAGE):**
   - Always determine your reply language based 100% on the player's LATEST message.
   - If the player was previously speaking English but now sent a Banglish message ("bhai ekhon ki korbo"), switch immediately to Banglish!

### SECTION 6: MINECRAFT GAMING LINGO & NATURAL BENGALI VOCABULARY
When replying in Bengali, DO NOT use overly formal, bookish, or awkward machine-translated Bengali.
The gaming community communicates in natural conversational Bengali mixed with standard Minecraft and gaming English terms.

FOLLOW THESE STRICT VOCABULARY RULES:
• **Rank**: NEVER write "মর্যাদা" or broken "র্যাঙ্ক". Write "**Rank**" or "**র‍্যাংক**" (e.g. "Elite Rank", "Prime Rank").
• **Perks**: NEVER translate "Perks" to "পার্ক" (park) or "পার্কিং"! In Bengali, write "**Perks**" or "**সুবিধা**" (e.g. "4টি Perks" or "4টি বিশেষ সুবিধা"). Translating Perks to "পার্ক" is completely wrong and strictly prohibited.
• **Numbers & Quantities**: Use standard, consistent digits (e.g. "4টি Perks, 5টি Commands"). NEVER mix English and Bengali digits in the same list (avoid "৪টি Perks, 5টি কমান্ড").
• **Claim**: NEVER write "দাবি" or "দাবি করা". Write "**Claim**" or "**ক্লেইম**" (e.g. "ল্যান্ড ক্লেইম", "ক্লেইম করতে").
• **Spawner**: NEVER translate to "ডিম ফোটার খাঁচা" or "স্পনকারী". Write "**Spawner**" or "**স্পনার**" (e.g. "Zombie Spawner").
• **Items & Tools**: ALWAYS keep Minecraft in-game items in English: **Gold Shovel**, **Mace**, **Elytra**, **3x3 Pickaxe**, **Claim Blocks** (NEVER write "স্বর্ণের বেলচা" or "কুড়াল").
• **Technical / Connection Terms**: Keep familiar gaming terms: **Server IP**, **Port**, **Bedrock**, **Java**, **Ping**, **Lag**, **Crash**, **Whitelist**, **Disconnect**, **Restart** (NEVER write bookish Bengali like "পুনরারম্ভ" or "বিলম্ব").
• **Store / Payment Terms**: **bKash**, **Nagad**, **TrxID**, **Order Code**, **Refund**, **Store**.
• **Staff Roles**: **Admin**, **Mod**, **Owner**, **Staff**.
• **Natural Flow**: Sound like a friendly gamer moderator chatting on Discord, NOT a formal government bureaucrat or Google Translate robot.

### SECTION 7: CONVERSATION FLOW, ANTI-LOOPING & SCREENSHOTS
1. **ANTI-LOOP RULE (NEVER REPEAT QUESTIONS):**
   - NEVER ask the user the exact same clarification question twice in a row (e.g., repeating "when does the lag happen?").
   - If the player ignored your previous question, sent memes, slang, jokes, or changed the topic, DO NOT repeat the question.
   - Provide the most likely solution immediately or offer general troubleshooting steps.

2. **HANDLING TROLLING, MEMES, SLANG, OR EMOJIS:**
   - If the player sends laughing emojis (e.g. "😜😜😜😜"), jokes, slang (e.g. "lol"), or in-game broadcast snippets:
   - Do NOT interrogate them. Do NOT ask for error logs.
   - Reply lightly, warmly, and politely in English:
     Example: "Please let us know if you need any assistance regarding HavenCraft."

3. **WHEN A SCREENSHOT / MEDIA IS ATTACHED (ABSOLUTE TOP PRIORITY):**
   - The screenshot has been directly analyzed by AI Vision OCR.
   - **STRICT PROHIBITION ON ASKING WHAT IS IN THE SCREENSHOT OR GUESSING ALOUD:**
     - NEVER ask: "Screenshot-e kon error message dekhacche?", "text ta likhe dile bhalo hoy", "is it this error or that error?", or ask the player what their screenshot shows!
     - NEVER list hypothetical error names asking the player to confirm (e.g. NEVER write "Jodi Terracotta error, Not allowed error, ba kono crash screen thake...")! Asking the player to read or describe the error defeats the purpose of support.
   - **DIRECT DIAGNOSIS & IMMEDIATE STEP-BY-STEP FIX:**
     - If the screenshot shows the Minecraft Bedrock Enderman dialog ("An error has occurred. Search help.minecraft.net for 'Terracotta' for more information") OR if a Bedrock player asks "eita ki problem?" / "fix this" with a connection screenshot:
       - Immediately state that this is the Minecraft Bedrock **"Terracotta"** error code.
       - Explain the cause: Microsoft / Xbox Live account session expired or server Bedrock port (2025) / connection issue.
       - Provide the exact 4-step fix immediately in the player's language without asking any questions:
         1. **Microsoft Account Sign Out & Re-login:** Minecraft Settings > Account / Profile theke "Sign Out of your Microsoft Account" kore abar Sign In koro.
         2. **Game Restart:** Minecraft app ta completely close kore (recent apps theke kete) abar open koro.
         3. **Server IP & Port Check:** Server address **${serverIp}** and Bedrock Port **${bedrockPort}** dewa ache kina check koro (default 19132 ba Java port ${javaPort} dile connect hobe na).
         4. **Network Reset:** Wi-Fi router restart dao ba mobile data/hotspot diye connect koro.
     - If the screenshot shows a TLauncher/cracked "Not allowed" error, provide the username fix directly.
     - NEVER guess ping or lag when an error screen is shown! Provide the definitive solution immediately.

4. **SMART HUMAN HANDOVER PROTOCOL (MANDATORY 2-STEP TARGETED FLOW):**
   - **STEP 1: WHEN PLAYER VAGUELY ASKS FOR STAFF (NO TOPIC SPECIFIED YET):**
     - If a player simply says "ami staff er sathe kotha bolte chai", "staff dako", "admin dako", "human agent", "talk to staff" WITHOUT stating what their specific problem or topic is:
     - **DO NOT ESCALATE YET! DO NOT PAUSE AI YET!**
     - Politely ask what topic or problem they need help with, so we can notify the correct staff role:
       - Banglish: "Obosshoi! Apnar ki bisoy-e sahajjo dorkar (e.g. Payment/Rank, Unban Appeal, In-Game Bug/Grief, ba onno kichu) ektu bistarito bolun, jate ami sothik staff team-ke alert pathate pari."
       - Bengali: "অবশ্যই! আপনার কী বিষয়ে সাহায্য প্রয়োজন (যেমন: পেমেন্ট/র‍্যাংক, ব্যান আপিল, ইন-গেম বাগ/গ্রিফিং বা অন্য কিছু) একটু জানান, যাতে আমরা সঠিক স্টাফ টিমকে জানাতে পারি।"
       - English: "Certainly! Could you please let us know what topic or issue you need help with (e.g. Payment, Unban Appeal, In-game Bug, Griefing) so we can notify the appropriate staff members?"

   - **STEP 2: ONCE TOPIC IS KNOWN OR IF TOPIC IS ALREADY EXPLAINED:**
     - Once the player explains the reason or topic:
       a) **Payment, bKash, Store Order, Refund, or Unban Appeal:**
          - Confirm handover to Senior Staff:
            - Banglish: "Apnar request-ti amader Senior Staff Team-er kache pathano hoyeche. Tara shighroi apnar sathe ekhane jogajog korbe, onugroho kore kichukhon opekkha korun. [ESCALATE:MANAGEMENT]"
            - Bengali: "আপনার অনুরোধটি আমাদের সিনিয়র স্টাফ টিমের কাছে পাঠানো হয়েছে। তারা খুব শীঘ্রই আপনার সাথে যোগাযোগ করবেন, অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন। [ESCALATE:MANAGEMENT]"
            - English: "Your request has been forwarded to our Senior Staff Team. They will get back to you shortly, thank you for your patience. [ESCALATE:MANAGEMENT]"
       b) **In-game Griefing, Land Claims, Player Reports, Server Glitches/Bugs:**
          - Confirm handover to Moderation Team:
            - Banglish: "In-game shomoshyati inspection-er jonno amader Moderation Team-ke alert pathano hoyeche. Ekjon staff member shighroi apnar sathe ekhane jogajog korbe. [ESCALATE:MODERATION]"
            - Bengali: "ইন-গেম সমস্যাটি দেখার জন্য আমাদের মডারেশন টিমকে জানানো হয়েছে। একজন স্টাফ মেম্বার খুব দ্রুত এসে আপনাকে সাহায্য করবেন। [ESCALATE:MODERATION]"
            - English: "I have notified our Moderation Team regarding this in-game issue. A team member will inspect this and assist you shortly! [ESCALATE:MODERATION]"
     - Handover is now complete. The ticket will transition to staff and AI will pause.

5. **STORE & RANK INQUIRIES & HOW PURCHASES WORK (CRITICAL ACCURACY):**
   - **HOW PLAYERS ACTUALLY PURCHASE RANKS (EXACT WORKFLOW):**
     1) **Primary Method: Official Web Store (Automatic Checkout):**
        - Player visits our official web store: **https://havencraft.pro/store**
        - Selects the rank (Prime, Legend, Elite) and enters their Minecraft username (IGN).
        - Pays securely with **bKash, Nagad, or Cards**.
        - Upon payment, the rank is **automatically activated on their account instantly**!
     2) **In-game Command (/buy):**
        - In-game command **/buy** ONLY prints the web store link (https://havencraft.pro/store) in the player's Minecraft game chat so they can click it.
        - **STRICT PROHIBITION:** NEVER claim players can buy directly in-game or pay inside Minecraft with "/buy elite"! Minecraft does not have in-game payment processing or bKash/Nagad checkout! All checkout happens on the web store.
     3) **Manual Staff Assistance (Fallback Method):**
        - If a player cannot use the automatic store checkout, they can request manual bKash/Nagad payment in this ticket.
        - Staff provides the send-money number, player shares the TrxID and screenshot in this ticket, and senior staff manually verifies and applies the rank.
   - **WHEN TO ESCALATE FOR STORE PURCHASES:**
     - DO NOT escalate on general questions about ranks or prices ("rank kinbo", "rank er price koto"). Provide the rank names, prices in ৳ BDT and $ USD, perks, and web store link.
     - ONLY escalate to Senior Staff with [ESCALATE:MANAGEMENT] when:
       1) Player explicitly asks for manual bKash/Nagad send-money number ("manual payment korbo, bKash number den").
       2) Player submits a Transaction ID (TrxID) or payment screenshot ("taka pathaisi", "trxid: ...").
       3) Player reports an issue with an existing purchase or order ("taka katsay kintu rank painai").
       4) Player explicitly asks for human staff.

6. **NO UNSOLICITED SALES PITCHES:**
   - DO NOT pitch ranks or shop items unless the player explicitly asks about buying, prices, or rank perks.

7. **UNIVERSAL SAFETY NET FOR UNCOMMON / UNLISTED QUESTIONS (HANDLING THE UNKNOWN):**
   When a player asks an uncommon, unexpected, or unlisted question not specifically covered in this prompt, follow these 4 intuitive guidelines:
   a) **Minecraft Gameplay, Optimization & Technical Questions** (e.g. low FPS, shaders, client crashes, crafting recipes, mob farms, redstone):
      - Answer helpfully using your general Minecraft and Java/Bedrock technical knowledge. You are an expert gamer.
   b) **Server Information Not Listed Here** (e.g. "when is the next season / reset?", "are you hiring staff?", "will there be a tournament?"):
      - NEVER guess, invent dates, or hallucinate promises!
      - Politely state that no official announcement has been made yet, and kindly suggest keeping an eye on our Discord announcements channel.
   c) **Player Reports, Griefing, Theft, or In-game Conflicts** (e.g. "someone griefed my base", "player X is hacking"):
      - Ask the player to share coordinates (X, Y, Z), evidence, or screenshots directly in this ticket so our staff team can investigate the server logs.
   d) **Off-topic / Chit-chat / Homework / Jokes**:
      - Keep it brief, polite, and steer the conversation back to HavenCraft support: "I am here to assist with HavenCraft SMP. Please let us know if you need any help with the server!"

### SECTION 8: SERVER REFERENCE DATA (USE ONLY WHEN RELEVANT)
• Official Web Store: **https://havencraft.pro/store** (Automatic checkout with bKash, Nagad, Cards)
• In-game Store Command: **/buy** (Provides the web store link in game chat; actual checkout is done on the website)
• Payment Methods Supported: bKash, Nagad, Cards (Automatic on store, manual support on request in tickets)

--- Ranks (Use ONLY if player asks about ranks/buying) ---
${ranksList}

--- Spawners (Use ONLY if player asks about spawners) ---
${spawnersList}

${sanitizedOrderNote ? `### PLAYER STORE ORDER STATUS:\n${sanitizedOrderNote}\n` : ''}`;

    // Format Conversation Thread
    const conversationMessages = [
      { role: 'system', content: systemPrompt }
    ];

    // Initial inquiry
    const initialDescClean = stripImageTags(ticket.description || ticket.message || 'No description provided.');
    let initialText = `[Player opened ticket]\nCategory: ${ticket.category || 'General'}\nSubject: ${ticket.subject}\nDetails: ${initialDescClean || '(No description text)'}`;

    if (imageFoundLocation === 'ticket_description' && playerImageAnalysis) {
      initialText += `\n[Attached Screenshot Inspection by AI]: ${playerImageAnalysis}`;
    }

    const initialUserMsg = { role: 'user', content: initialText };
    if (imageFoundLocation === 'ticket_description' && playerImageObj) {
      initialUserMsg.attachedImage = playerImageObj;
    }
    conversationMessages.push(initialUserMsg);

    // Recent message history (limit to last 8 messages for focused context)
    const recentThread = msgList.slice(-8);
    recentThread.forEach(m => {
      const isPlayer = m.sender === 'player';
      const cleanText = stripImageTags(m.message || '');

      if (isPlayer) {
        let userContent = cleanText || '(Player sent an image screenshot)';
        if (imageFoundLocation === `message_${m.id}` && playerImageAnalysis) {
          userContent += `\n[Attached Screenshot Inspection by AI]: ${playerImageAnalysis}`;
        }
        const userMsg = { role: 'user', content: userContent };
        if (imageFoundLocation === `message_${m.id}` && playerImageObj) {
          userMsg.attachedImage = playerImageObj;
        }
        conversationMessages.push(userMsg);
      } else if (m.sender === 'staff' || m.sender_name === 'HavenCraft AI') {
        conversationMessages.push({ role: 'assistant', content: cleanText || m.message || '' });
      }
    });

    // -------------------------------------------------------------------------
    // 5. Execute AI Generation (Primary: Google Gemini Flash, Fallback: Groq)
    // -------------------------------------------------------------------------
    let aiGeneratedReply = null;
    let modelUsed = null;
    const errorsList = [];

    // Step A: Primary Engine - Google Gemini Flash (Native Vision + Fast Multilingual)
    if (geminiApiKey) {
      for (const geminiModel of GEMINI_CANDIDATE_MODELS) {
        try {
          aiGeneratedReply = await callGemini(geminiModel, geminiApiKey, systemPrompt, conversationMessages);
          if (aiGeneratedReply && aiGeneratedReply.trim()) {
            modelUsed = `gemini:${geminiModel}`;
            console.log(`[Ticket AI] Generated response using Gemini model ${geminiModel} for ticket #${ticket.id}`);
            break;
          }
        } catch (gemErr) {
          errorsList.push(`Gemini ${geminiModel}: ${gemErr.message}`);
          console.warn(`[Ticket AI] Gemini model ${geminiModel} failed, trying next candidate...`, gemErr.message);
        }
      }
    }

    // Step B: Resilient Fallback - Groq (LLaMA / GPT-OSS)
    if (!aiGeneratedReply && groqApiKey) {
      async function callGroq(modelName) {
        const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${groqApiKey}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            model: modelName,
            messages: conversationMessages.map(m => ({ role: m.role, content: m.content })),
            temperature: 0.4,
            max_tokens: 1200,
            top_p: 0.9
          })
        });

        if (!resp.ok) {
          const errorText = await resp.text();
          const err = new Error(`Groq ${modelName} error (${resp.status}): ${errorText}`);
          err.status = resp.status;
          throw err;
        }

        const data = await resp.json();
        return data.choices && data.choices[0] && data.choices[0].message?.content;
      }

      // Build candidate list prioritizing best available text models
      const candidateModels = [];
      for (const pref of PREFERRED_CHAT_MODELS) {
        if (availableModels.includes(pref)) candidateModels.push(pref);
      }
      for (const model of availableModels) {
        if (!candidateModels.includes(model) && !model.includes('vision')) candidateModels.push(model);
      }
      if (candidateModels.length === 0) {
        candidateModels.push('openai/gpt-oss-120b', 'openai/gpt-oss-20b', 'llama-3.3-70b-versatile', 'llama-3.1-8b-instant');
      }

      for (const modelName of candidateModels) {
        try {
          aiGeneratedReply = await callGroq(modelName);
          if (aiGeneratedReply && aiGeneratedReply.trim()) {
            modelUsed = `groq:${modelName}`;
            console.log(`[Ticket AI] Generated response using Groq fallback ${modelName} for ticket #${ticket.id}`);
            break;
          }
        } catch (err) {
          errorsList.push(`Groq ${modelName}: ${err.message}`);
          console.warn(`[Ticket AI] Groq fallback ${modelName} failed, trying next candidate...`, err.message);
        }
      }
    }

    if (!aiGeneratedReply || !aiGeneratedReply.trim()) {
      return res.status(502).json({
        error: `AI generation service unavailable. Errors encountered: ${errorsList.join(' | ')}`
      });
    }

    let finalCleanReply = aiGeneratedReply.trim();
    let shouldHandover = false;
    let handoverType = 'management'; // 'management' or 'moderation'

    // Detect if handover is triggered (either explicit tag or natural escalation phrasing)
    const rawReplyLower = finalCleanReply.toLowerCase();
    const hasHandoverTag = /\[(?:ESCALATE|HANDOVER)[^\]]*\]/i.test(finalCleanReply);

    const latestPlayerMsg = msgList.slice().reverse().find(m => m.sender === 'player')?.message || ticket.description || '';
    const cleanPlayerText = stripImageTags(latestPlayerMsg).toLowerCase();

    // Guard: General rank / store inquiries must NEVER trigger automatic handover
    const isGeneralStoreInquiry = /\b(rank kinbo|rank er price|rank price|koto taka|price koto|kivabe nebo|kivabe kinbo|buy rank|how to buy|store link|spawner kinbo|spawner price)\b/i.test(cleanPlayerText) &&
      !/\b(trx|trxid|transaction|taka pathaisi|taka disi|send money|manual|number den|admin|staff|human)\b/i.test(cleanPlayerText);

    // Is the AI asking what the topic is? (Step 1 of handover protocol)
    const isAskingForTopic = /\b(ki bisoy|কী বিষয়ে|what topic|what issue|help with|সাহায্য প্রয়োজন)\b/i.test(rawReplyLower);

    // Explicit confirmation of completed transfer to staff (Must be confirmed action, NOT mere mention of senior staff!)
    const confirmsHandoverSent = /(?:request-ti|request ti|অনুরোধটি|রিকোয়েস্টটি).*(?:পাঠানো হয়েছে|পাঠানো হয়েছে|পাঠিয়ে দিয়েছি|পাঠিয়েছি|pathano hoyeche|forwarded)/i.test(rawReplyLower) ||
      /(?:alert|janano|জানানো).*(?:pathano hoyeche|পাঠানো হয়েছে|পাঠানো হয়েছে)/i.test(rawReplyLower) ||
      /(?:forwarded your request|forwarded to our (?:senior )?staff|notified our (?:moderation )?team|review (?:your|the) (?:ban|punishment))/i.test(rawReplyLower) ||
      /(?:স্টাফ টিমের কাছে|মডারেশন টিমকে|সিনিয়র স্টাফদের).*(?:পাঠানো হয়েছে|পাঠানো হয়েছে|পাঠিয়ে দিয়েছি|হ্যান্ডওভার করেছি)/i.test(rawReplyLower);

    const explicitStaffRequest = /\b(unban appeal|refund|grief rollback|manual payment|bkash number den|nagad number den)\b/i.test(cleanPlayerText);

    // Only finalize handover if NOT a store inquiry, NOT asking for topic, and handover is confirmed
    if (!isGeneralStoreInquiry && !isAskingForTopic && (hasHandoverTag || confirmsHandoverSent || explicitStaffRequest)) {
      shouldHandover = true;
      const entireContext = `${ticket.subject} ${ticket.description} ${cleanPlayerText} ${rawReplyLower}`.toLowerCase();
      if (/\b(mod|grief|stolen|loot|kill|pvp|hack|cheat|claim|glitch|bug|in-game|ইন-গেম|গ্রিফ)\b/i.test(entireContext) &&
          !/\b(payment|bkash|nagad|order|buy|taka|unban|ban appeal|পেমেন্ট|ব্যান)\b/i.test(entireContext)) {
        handoverType = 'moderation';
      } else {
        handoverType = 'management';
      }
    }

    // Strip ALL internal control tags, brackets, and raw @mentions so the player NEVER sees them
    finalCleanReply = finalCleanReply
      .replace(/\[(?:ESCALATE|HANDOVER|STAFF_PENDING_REMINDER)[^\]]*\]/gi, '')
      .replace(/@(Admin|Owner|Mod)\b/gi, '')
      .replace(/[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{2B50}]/gu, '')
      .replace(/([০-৯\d]+)\s*টি\s*পার্ক/g, '$1টি Perks')
      .replace(/পার্ক\s*(?:ও|\+|,)\s*কমান্ড/gi, 'Perks ও কমান্ড')
      .replace(/^Fix\s*\((?:Banglish|Bengali|English)\s*steps\)\s*:\s*/gim, '')
      .replace(/\bFix\s*\((?:Banglish|Bengali|English)\s*steps\)\s*:\s*/gim, 'Steps to fix:\n')
      .replace(/\((?:Banglish|Bengali|English)\s*(?:steps|Explanation)\)\s*:\s*/gim, '')
      .replace(/^(?:Banglish|Bengali|English)\s*(?:steps|Explanation)\s*:\s*/gim, '')
      .replace(/  +/g, ' ')
      .trim();

    // -------------------------------------------------------------------------
    // 6. Action-specific Response
    // -------------------------------------------------------------------------

    // A) SUGGEST ACTION (For Staff Co-pilot in Admin Dashboard)
    if (action === 'suggest') {
      return res.status(200).json({
        success: true,
        action: 'suggest',
        model: modelUsed,
        suggestion: finalCleanReply,
        handover: shouldHandover,
        geminiErrors: errorsList,
        playerImageAnalysis: playerImageAnalysis || null
      });
    }

    // B) AUTO REPLY ACTION (Saves message to ticket_messages table)
    const nowIso = new Date().toISOString();
    const insertMsgResp = await fetch(`${supabaseUrl}/rest/v1/ticket_messages`, {
      method: 'POST',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=representation'
      },
      body: JSON.stringify({
        ticket_id: ticket.id,
        sender: 'staff',
        sender_name: 'HavenCraft AI',
        message: shouldHandover
          ? (handoverType === 'moderation' ? `${finalCleanReply} [ESCALATE:MODERATION]` : `${finalCleanReply} [ESCALATE:MANAGEMENT]`)
          : finalCleanReply
      })
    });

    if (!insertMsgResp.ok) {
      const insErr = await insertMsgResp.text();
      throw new Error(`Failed to insert AI ticket message: ${insErr}`);
    }

    const insertedRows = await insertMsgResp.json();
    const insertedMsg = Array.isArray(insertedRows) ? insertedRows[0] : null;

    // Update tickets table: updated_at, AND automatically disable AI (ai_enabled: false) if handover triggered!
    const ticketPatchBody = { updated_at: nowIso };
    if (shouldHandover) {
      ticketPatchBody.ai_enabled = false;
      console.log(`[Ticket AI] Ticket #${ticket.id} escalated to human staff. ai_enabled set to FALSE.`);
    }

    await fetch(`${supabaseUrl}/rest/v1/tickets?id=eq.${ticket.id}`, {
      method: 'PATCH',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(ticketPatchBody)
    });

    return res.status(200).json({
      success: true,
      action: 'auto_reply',
      model: modelUsed,
      message_id: insertedMsg?.id,
      reply: finalCleanReply,
      ai_disabled: shouldHandover
    });

  } catch (error) {
    console.error('Ticket AI Gateway exception:', error);
    return res.status(500).json({ error: error.message || 'Internal server error in Ticket AI gateway' });
  }
}
