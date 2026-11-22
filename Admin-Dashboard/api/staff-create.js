/**
 * HavenCraft SMP — Automatic Staff Provisioning API (Vercel Serverless Function)
 * Strict Zero-Leak Security Architecture:
 * 1. Strict Caller Authentication: Requires valid Supabase JWT Bearer Token.
 * 2. Strict RBAC: Only Primary Owner (......@gmail.com) or authorized Manager
 * 3. Zero Secrets Leak: SUPABASE_SERVICE_KEY strictly read from server environment.
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
    // 1. CALLER AUTHENTICATION (Bearer Token Verification)
    // =========================================================================
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace(/^Bearer\s+/i, '').trim();

    if (!token) {
      return res.status(401).json({
        error: 'Unauthorized: Missing Authorization Bearer token.'
      });
    }

    // Verify token with Supabase Auth
    const authVerifyResp = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${token}`
      }
    });

    if (!authVerifyResp.ok) {
      return res.status(401).json({
        error: 'Unauthorized: Invalid or expired staff session token.'
      });
    }

    const callerUser = await authVerifyResp.json();
    const callerEmail = (callerUser.email || '').trim().toLowerCase();
    const ownerEmail = (process.env.OWNER_EMAIL || 'rajuel55@gmail.com').trim().toLowerCase();
    const isCallerOwner = (callerEmail === ownerEmail);
    let callerProfile = null;

    // If not Owner, verify caller has 'users' permission in staff_profiles
    if (!isCallerOwner) {
      const callerProfResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${callerUser.id}&select=*`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });
      const callerProfiles = await callerProfResp.json();
      callerProfile = callerProfiles && callerProfiles[0];

      if (!callerProfile || !callerProfile.is_active || callerProfile.status !== 'active') {
        return res.status(403).json({
          error: 'Forbidden: Your staff account is deactivated or suspended.'
        });
      }

      const callerPerms = callerProfile.permissions || {};
      const hasUsersEditPerm = callerPerms.users === true && callerPerms.users_edit !== false && !callerPerms.read_only;
      if (!hasUsersEditPerm) {
        return res.status(403).json({
          error: 'Forbidden: You have View-Only access or lack permission to manage staff accounts.'
        });
      }
    }

    // =========================================================================
    // 2. PARSE AND VALIDATE INPUT & ROLE HIERARCHY
    // =========================================================================
    const {
      action = 'invite',
      targetUserId = '',
      name = '',
      ign = '',
      email = '',
      discord = '',
      role = 'Moderator',
      password = '',
      sendInvite = true,
      perms = {}
    } = req.body || {};

    // Role Hierarchy Scale (Higher number = Higher rank)
    const ROLE_HIERARCHY = {
      'Owner': 100,
      'Sr-Admin': 90,
      'Co-Owner': 90,
      'Manager': 80,
      'Developer': 75,
      'Admin': 70,
      'Administrator': 70,
      'Moderator': 60,
      'Mod': 60,
      'Officer': 50,
      'Helper': 40,
      'Builder': 30,
      'Staff': 20,
      'Pending': 10,
      'Pending Verification': 10
    };

    function getRoleRank(r) {
      if (!r) return 0;
      const key = Object.keys(ROLE_HIERARCHY).find(
        k => k.toLowerCase() === String(r).trim().toLowerCase()
      );
      return key ? ROLE_HIERARCHY[key] : 0;
    }

    const callerRole = isCallerOwner ? 'Owner' : (callerProfile?.role || 'Staff');
    const callerRank = getRoleRank(callerRole);

    // =========================================================================
    // 2A. CASE 0: DELETE STAFF ACCOUNT (Removes both staff_profiles & auth.users)
    // =========================================================================
    if (action === 'delete') {
      const cleanTargetId = String(targetUserId || '').trim();
      if (!cleanTargetId || !/^[0-9a-fA-F-]{36}$/.test(cleanTargetId)) {
        return res.status(400).json({ error: 'Valid targetUserId UUID is required for deletion.' });
      }
      if (cleanTargetId === callerUser.id) {
        return res.status(400).json({ error: 'You cannot delete your own active staff account.' });
      }

      const targetProfResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${encodeURIComponent(cleanTargetId)}&select=*`, {
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });
      const targetProfiles = await targetProfResp.json();
      const targetProfile = targetProfiles && targetProfiles[0];

      if (!targetProfile) {
        return res.status(404).json({
          error: 'Target staff profile not found. Cannot delete non-staff or unmapped accounts.'
        });
      }

      const targetEmail = (targetProfile.email || '').trim().toLowerCase();
      const existingTargetRank = getRoleRank(targetProfile.role);

      if (targetEmail === ownerEmail || targetProfile.role === 'Owner') {
        return res.status(403).json({ error: 'Forbidden: The Primary Server Owner account cannot be deleted.' });
      }
      if (!isCallerOwner && existingTargetRank >= callerRank) {
        return res.status(403).json({
          error: `Forbidden: You cannot delete a staff member with equal or higher rank ('${targetProfile.role}').`
        });
      }

      // Delete from staff_profiles
      await fetch(`${supabaseUrl}/rest/v1/staff_profiles?id=eq.${encodeURIComponent(cleanTargetId)}`, {
        method: 'DELETE',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      });

      // Delete from Supabase Auth (auth.users) so they cannot log back in
      await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(cleanTargetId)}`, {
        method: 'DELETE',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`
        }
      }).catch(() => { });

      return res.status(200).json({
        success: true,
        mode: 'delete',
        deletedUserId: cleanTargetId,
        message: 'Staff profile and Supabase Auth account permanently removed.'
      });
    }

    if (!email || typeof email !== 'string') {
      return res.status(400).json({ error: 'Valid staff email is required.' });
    }

    const cleanEmail = email.trim().toLowerCase();
    const cleanIgn = (ign || '').trim() || 'StaffMember';
    const cleanName = (name || '').trim() || cleanEmail.split('@')[0];
    const cleanRole = role || 'Moderator';

    // CRITICAL SECURITY: Non-owner can NEVER target the Primary Owner's email
    if (!isCallerOwner && cleanEmail === ownerEmail) {
      return res.status(403).json({
        error: 'Forbidden: You cannot invite, modify, or generate recovery links for the Primary Server Owner.'
      });
    }

    // Check if an account with this email already exists in staff_profiles and enforce rank hierarchy on existing role
    const existingProfResp = await fetch(`${supabaseUrl}/rest/v1/staff_profiles?email=ilike.${encodeURIComponent(cleanEmail)}&select=id,email,role`, {
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`
      }
    });
    const existingProfiles = await existingProfResp.json();
    const existingProfile = Array.isArray(existingProfiles) ? existingProfiles[0] : null;

    if (existingProfile) {
      const existingRank = getRoleRank(existingProfile.role);
      if (!isCallerOwner && existingRank >= callerRank) {
        return res.status(403).json({
          error: `Forbidden: An account for '${cleanEmail}' already holds rank '${existingProfile.role}', which is equal to or higher than your rank ('${callerRole}').`
        });
      }
    }

    function getRoleDefaultPerms(roleName, isOwnerCaller = false) {
      const clean = roleName || 'Moderator';
      const roleIsOwner = clean === 'Owner';
      const roleIsSrAdmin = clean === 'Sr-Admin' || clean === 'Co-Owner' || roleIsOwner;
      const roleIsManager = clean === 'Manager' || roleIsSrAdmin;
      const roleIsDev = clean === 'Developer';
      const roleIsAdmin = clean === 'Admin' || clean === 'Administrator' || roleIsManager || roleIsDev;
      const roleIsMod = clean === 'Mod' || clean === 'Moderator' || roleIsAdmin;
      const roleIsOfficer = clean === 'Officer' || clean === 'Helper' || roleIsMod;

      const base = {
        users: roleIsOwner || (clean === 'Sr-Admin' && isOwnerCaller) || (clean === 'Manager' && isOwnerCaller),
        orders: roleIsMod && !roleIsDev && clean !== 'Builder',
        finance: roleIsMod && !roleIsDev && clean !== 'Builder',
        console: roleIsAdmin || roleIsDev,
        settings: roleIsOwner || (clean === 'Sr-Admin' && isOwnerCaller) || roleIsDev,
        tickets: roleIsOfficer && clean !== 'Builder',
        shop: roleIsAdmin,
        gallery: roleIsOfficer || clean === 'Builder',
        logs: roleIsAdmin || roleIsDev
      };
      return {
        ...base,
        users_edit: base.users,
        orders_edit: base.orders,
        finance_edit: base.finance,
        console_edit: base.console,
        settings_edit: base.settings,
        tickets_edit: base.tickets,
        shop_edit: base.shop,
        gallery_edit: base.gallery,
        logs_edit: false,
        read_only: false
      };
    }

    const targetRank = getRoleRank(cleanRole);

    // Anti-Escalation Rule 1: A user CANNOT assign equal or higher role than their own!
    // Example: Admin (rank 70) can only assign roles strictly below Admin (< 70).
    // Admin cannot assign Admin (70) or Manager (80).
    if (!isCallerOwner && targetRank >= callerRank) {
      return res.status(403).json({
        error: `Forbidden: You cannot assign a role ('${cleanRole}') equal to or higher than your own role ('${callerRole}'). You can only assign roles strictly below your rank.`
      });
    }

    // Anti-Escalation Rule 2: Staff / users CANNOT give or take custom permissions!
    // Only the Primary Owner can supply custom perms (including View-Only vs. Edit).
    // For all other callers, perms is 100% locked to role defaults!
    let finalPerms;
    if (isCallerOwner && perms && typeof perms === 'object' && Object.keys(perms).length > 0) {
      const defaults = getRoleDefaultPerms(cleanRole, true);
      const isGlobalReadOnly = Boolean(perms.read_only === true);
      finalPerms = { read_only: isGlobalReadOnly };
      ['users', 'orders', 'finance', 'console', 'settings', 'tickets', 'shop', 'gallery', 'logs'].forEach((mod) => {
        const canView = perms[mod] !== undefined ? Boolean(perms[mod]) : defaults[mod];
        finalPerms[mod] = canView;
        if (!canView || isGlobalReadOnly || mod === 'logs') {
          finalPerms[mod + '_edit'] = false;
        } else if (perms[mod + '_edit'] !== undefined) {
          finalPerms[mod + '_edit'] = Boolean(perms[mod + '_edit']);
        } else {
          finalPerms[mod + '_edit'] = true;
        }
      });
    } else {
      // Non-owners CANNOT customize permissions: strictly use role defaults!
      finalPerms = getRoleDefaultPerms(cleanRole, false);
    }

    // =========================================================================
    // 3. CASE A: INVITE LINK GENERATION (Recommended / Pterodactyl Style)
    // =========================================================================
    if (sendInvite || action === 'invite' || (!password && action !== 'direct')) {
      const linkResp = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
        method: 'POST',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          type: 'invite',
          email: cleanEmail,
          data: {
            full_name: cleanName,
            minecraft_ign: cleanIgn,
            role: cleanRole
          }
        })
      });

      const linkResult = await linkResp.json();

      if (!linkResp.ok) {
        // If user already exists in auth, handle recovery link safely
        const linkErrMsg = String(linkResult.message || linkResult.msg || linkResult.error || linkResult.error_description || '').toLowerCase();
        if (linkErrMsg.includes('already')) {
          if (!isCallerOwner) {
            // SECURITY FIX (HAVEN-001): Non-owner staff cannot extract raw recovery links for existing accounts!
            // Dispatch standard password recovery email directly to the user's inbox
            await fetch(`${supabaseUrl}/auth/v1/recover`, {
              method: 'POST',
              headers: {
                'apikey': serviceKey,
                'Content-Type': 'application/json'
              },
              body: JSON.stringify({ email: cleanEmail })
            }).catch(() => { });

            return res.status(200).json({
              success: true,
              mode: 'invite',
              alreadyRegistered: true,
              message: 'Staff account already exists. A password recovery email has been dispatched directly to the staff member.',
              email: cleanEmail,
              ign: cleanIgn
            });
          }

          // Primary Server Owner flow: Owner is allowed to receive setup/recovery link
          const recResp = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
            method: 'POST',
            headers: {
              'apikey': serviceKey,
              'Authorization': `Bearer ${serviceKey}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              type: 'recovery',
              email: cleanEmail
            })
          });
          const recResult = await recResp.json();
          if (recResp.ok) {
            return res.status(200).json({
              success: true,
              mode: 'invite',
              alreadyRegistered: true,
              // HAVEN-001: raw recovery link is owner-only. Non-owner staff with
              // 'users' permission must never receive reset links for existing accounts.
              actionLink: isCallerOwner ? recResult.properties?.action_link : undefined,
              message: isCallerOwner
                ? 'Staff account already exists. Password setup/recovery link generated.'
                : 'Staff account already exists. Only the Primary Owner can regenerate access links.',
              email: cleanEmail,
              ign: cleanIgn
            });
          }
        }
        return res.status(linkResp.status).json({
          error: linkResult.message || 'Failed to generate invite link.',
          details: linkResult
        });
      }

      const userId = linkResult.id || linkResult.user?.id;
      const actionLink = linkResult.properties?.action_link;

      if (userId) {
        await fetch(`${supabaseUrl}/rest/v1/staff_profiles`, {
          method: 'POST',
          headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`,
            'Content-Type': 'application/json',
            'Prefer': 'resolution=merge-duplicates'
          },
          body: JSON.stringify({
            id: userId,
            email: cleanEmail,
            full_name: cleanName,
            minecraft_ign: cleanIgn,
            discord: discord || 'Not linked',
            role: cleanRole,
            status: 'active',
            is_active: true,
            permissions: finalPerms
          })
        }).catch(() => { });
      }

      return res.status(200).json({
        success: true,
        mode: 'invite',
        actionLink: actionLink,
        userId: userId,
        email: cleanEmail,
        ign: cleanIgn,
        name: cleanName,
        role: cleanRole,
        message: 'Staff invite link generated successfully!'
      });
    }

    // =========================================================================
    // 4. CASE B: DIRECT CREATION (With Initial Password)
    // =========================================================================
    if (!password || password.length < 8) {
      return res.status(400).json({ error: 'Password must be at least 8 characters long.' });
    }

    const userCreateResp = await fetch(`${supabaseUrl}/auth/v1/admin/users`, {
      method: 'POST',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        email: cleanEmail,
        password: password,
        email_confirm: true,
        user_metadata: {
          full_name: cleanName,
          minecraft_ign: cleanIgn,
          role: cleanRole
        }
      })
    });

    const userResult = await userCreateResp.json();

    if (!userCreateResp.ok) {
      // SECURITY FIX: Never silently overwrite an existing user's password!
      const userErrMsg = String(userResult.message || userResult.msg || userResult.error || userResult.error_description || '').toLowerCase();
      if (userErrMsg.includes('already')) {
        return res.status(409).json({
          error: 'An account with this email already exists. Use the invite link flow to send a password reset.'
        });
      }

      return res.status(userCreateResp.status).json({
        error: userResult.message || 'Failed to create user in Supabase Auth.',
        details: userResult
      });
    }

    const createdUserId = userResult.id;

    if (createdUserId) {
      await fetch(`${supabaseUrl}/rest/v1/staff_profiles`, {
        method: 'POST',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json',
          'Prefer': 'resolution=merge-duplicates'
        },
        body: JSON.stringify({
          id: createdUserId,
          email: cleanEmail,
          full_name: cleanName,
          minecraft_ign: cleanIgn,
          discord: discord || 'Not linked',
          role: cleanRole,
          status: 'active',
          is_active: true,
          permissions: finalPerms
        })
      }).catch(() => { });
    }

    return res.status(200).json({
      success: true,
      mode: 'direct',
      userId: createdUserId,
      email: cleanEmail,
      ign: cleanIgn,
      name: cleanName,
      role: cleanRole,
      message: 'Staff account created successfully!'
    });

  } catch (err) {
    return res.status(500).json({
      error: 'Staff provisioning server error',
      details: err.message
    });
  }
}
