/**
 * HavenCraft SMP — Automatic Gallery Image Upload API (Vercel Serverless Function)
 * Strict Zero-Leak Security Architecture:
 * 1. Caller Authentication: Requires valid Supabase JWT Bearer Token.
 * 2. Permission Verification: Caller must have is_active === true and permissions.gallery === true (or Owner).
 * 3. Auto-Bucket Provisioning: Ensures 'gallery' bucket exists with public: true.
 * 4. Bypasses client-side RLS issues by securely uploading with SUPABASE_SERVICE_KEY.
 * 5. Returns permanent, public CDN URL (never temporary blob).
 */

const rateLimitMap = new Map();
function checkRateLimit(ip, limit = 15, windowMs = 60 * 1000) {
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
  const rl = checkRateLimit(clientIp, 15, 60 * 1000);
  if (rl.limited) {
    res.setHeader('Retry-After', String(rl.retryAfter));
    return res.status(429).json({ error: `Rate limit exceeded. Try again in ${rl.retryAfter}s.` });
  }

  try {
    const supabaseUrl = (process.env.SUPABASE_URL || 'https://nvcnowvasqcsygtbwktw.supabase.co').replace(/\/$/, '');
    const serviceKey = process.env.SUPABASE_SERVICE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;

    if (!serviceKey) {
      return res.status(500).json({
        error: 'Server configuration error: SUPABASE_SERVICE_KEY is missing in Vercel environment variables.'
      });
    }

    // =========================================================================
    // 1. CALLER AUTHENTICATION & PERMISSION CHECK
    // =========================================================================
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace(/^Bearer\s+/i, '').trim();

    if (!token) {
      return res.status(401).json({ error: 'Unauthorized: Missing Authorization Bearer token.' });
    }

    const authVerifyResp = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${token}`
      }
    });

    if (!authVerifyResp.ok) {
      return res.status(401).json({ error: 'Unauthorized: Invalid or expired staff session token.' });
    }

    const callerUser = await authVerifyResp.json();
    const callerEmail = (callerUser.email || '').trim().toLowerCase();
    const ownerEmail = (process.env.OWNER_EMAIL || 'rajuel55@gmail.com').trim().toLowerCase();
    const isCallerOwner = (callerEmail === ownerEmail);

    if (!isCallerOwner) {
      const profResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${callerUser.id}&select=*`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });
      const profiles = await profResp.json();
      const profile = profiles && profiles[0];

      if (!profile || !profile.is_active || profile.status !== 'active') {
        return res.status(403).json({ error: 'Forbidden: Staff account is deactivated or suspended.' });
      }

      const perms = profile.permissions || {};
      const hasGalleryEdit = perms.gallery === true && perms.gallery_edit !== false && perms.read_only !== true;
      if (!hasGalleryEdit) {
        return res.status(403).json({ error: 'Forbidden: You have View-Only access or lack permission to upload to the gallery.' });
      }
    }

    // =========================================================================
    // 2. PARSE BODY & VALIDATE FILE / URL
    // =========================================================================
    const {
      fileName = 'photo.png',
      fileType = 'image/png',
      fileBase64 = '',
      imageUrl = '',
      caption = 'Showcase Image',
      category = 'SMP Community',
      author = 'Staff Admin',
      featured = false
    } = req.body || {};

    const ALLOWED_IMAGE_TYPES = {
      'image/webp': 'webp',
      'image/png': 'png',
      'image/jpeg': 'jpg',
      'image/jpg': 'jpg',
      'image/gif': 'gif'
    };

    let permanentUrl = '';

    // If an external web image URL was provided directly (strictly require https:// and no script/HTML chars)
    if (imageUrl) {
      const cleanImgUrl = String(imageUrl).trim();
      if (!/^https:\/\/[^\s"'<>]+$/i.test(cleanImgUrl) || cleanImgUrl.startsWith('blob:')) {
        return res.status(400).json({ error: 'Invalid external image URL. Only secure https:// image URLs are allowed.' });
      }
      permanentUrl = cleanImgUrl;
    } else if (fileBase64) {
      const normalizedMime = String(fileType || '').trim().toLowerCase();
      const safeExt = ALLOWED_IMAGE_TYPES[normalizedMime];
      if (!safeExt) {
        return res.status(400).json({
          error: 'Unsupported file type. Only WebP, PNG, JPEG, and GIF images are allowed.'
        });
      }

      // Clean base64 payload
      const base64Clean = String(fileBase64).replace(/^data:image\/(webp|png|jpeg|jpg|gif);base64,/i, '');
      const fileBuffer = Buffer.from(base64Clean, 'base64');

      if (fileBuffer.length === 0) {
        return res.status(400).json({ error: 'Uploaded image file buffer is empty.' });
      }
      if (fileBuffer.length > 2 * 1024 * 1024) {
        return res.status(400).json({ error: 'Image exceeds maximum 2MB file size limit.' });
      }

      // Ensure 'gallery' storage bucket exists and is public with strict MIME whitelist
      try {
        await fetch(`${supabaseUrl}/storage/v1/bucket`, {
          method: 'POST',
          headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            id: 'gallery',
            name: 'gallery',
            public: true,
            file_size_limit: 2097152, // 2MB
            allowed_mime_types: ['image/webp', 'image/png', 'image/jpeg', 'image/gif']
          })
        });
      } catch (e) {
        // Ignore 409 Conflict if bucket already exists
      }

      // Generate unique, path-traversal-proof file path using validated extension
      const cleanBaseName = String(fileName || 'photo')
        .replace(/\.[^/.]+$/, '')
        .replace(/[^a-zA-Z0-9_-]/g, '_')
        .slice(0, 60)
        .toLowerCase() || 'photo';
      const filePath = `showcase/${Date.now()}_${cleanBaseName}.${safeExt}`;

      // Upload binary to Supabase Storage using serviceKey
      const uploadResp = await fetch(`${supabaseUrl}/storage/v1/object/gallery/${filePath}`, {
        method: 'POST',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': normalizedMime === 'image/jpg' ? 'image/jpeg' : normalizedMime,
          'cache-control': '31536000, immutable',
          'x-upsert': 'true'
        },
        body: fileBuffer
      });

      if (!uploadResp.ok) {
        const uploadErrData = await uploadResp.text();
        console.error('Supabase storage upload error:', uploadErrData);
        return res.status(500).json({
          error: `Storage upload failed: ${uploadErrData}`
        });
      }

      permanentUrl = `${supabaseUrl}/storage/v1/object/public/gallery/${filePath}`;
    } else {
      return res.status(400).json({ error: 'No image file or URL was provided.' });
    }

    if (!permanentUrl || permanentUrl.startsWith('blob:')) {
      return res.status(400).json({ error: 'Invalid permanent image URL generated.' });
    }

    // =========================================================================
    // 3. INSERT INTO public.gallery TABLE
    // =========================================================================
    const insertResp = await fetch(`${supabaseUrl}/rest/v1/gallery`, {
      method: 'POST',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=representation'
      },
      body: JSON.stringify([{
        image_url: permanentUrl,
        caption: caption.trim() || 'Showcase Image',
        category: category || 'SMP Community',
        uploaded_by: author || 'Staff Admin',
        featured: Boolean(featured),
        status: 'approved',
        created_at: new Date().toISOString()
      }])
    });

    if (!insertResp.ok) {
      const dbErr = await insertResp.text();
      console.error('Gallery database insert error:', dbErr);
      return res.status(500).json({ error: `Database insert failed: ${dbErr}` });
    }

    const insertedRows = await insertResp.json();
    return res.status(200).json({
      success: true,
      data: insertedRows && insertedRows[0],
      publicUrl: permanentUrl
    });

  } catch (err) {
    console.error('Gallery upload handler exception:', err);
    return res.status(500).json({ error: err.message || 'Internal server error' });
  }
}
