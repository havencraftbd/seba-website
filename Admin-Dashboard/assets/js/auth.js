/**
 * HavenCraft SMP — Staff Authentication & Route Guard
 * Strict 3-Layer RBAC Enforcement:
 * 1. Owner locked ONLY to verified email 'rajuel55@gmail.com' (Zero IGN metadata escalation)
 * 2. Route Guard checks required module permissions before rendering pages
 * 3. Enforces persistent lockscreen state (Back-button & direct URL bypass blocked)
 * 4. Exposes window.havenStaffProfile & window.hvnAuth.getCurrentProfile() with full permissions
 */

(function () {
  'use strict';

  const isFile = window.location.protocol === 'file:';
  const rawPath = (window.location.pathname || '').replace(/\\/g, '/').toLowerCase();

  // Bulletproof detection for pending approval gate page
  function isCurrentPendingPage() {
    if (document.title.toLowerCase().includes('awaiting') || document.title.toLowerCase().includes('pending')) return true;
    if (rawPath.includes('pending-approval') || rawPath.endsWith('pending-approval.html')) return true;
    return false;
  }

  // Bulletproof detection for login page
  function isCurrentLoginPage() {
    if (isCurrentPendingPage()) return false;
    if (rawPath.includes('setup-password')) return false;
    if (document.getElementById('staffLoginForm')) return true;
    if (document.title.toLowerCase().includes('login')) return true;
    if (rawPath.endsWith('/login') || rawPath.includes('/login/') || rawPath.endsWith('login.html') || rawPath.endsWith('/login/index.html')) {
      return true;
    }
    return false;
  }

  // Auto-clean any legacy lockscreen storage flags
  try {
    localStorage.removeItem('hvn_session_locked');
    sessionStorage.removeItem('hvn_lock_return_url');
  } catch (e) { }

  // Detect subfolder for relative paths (including /users, /profile, /login, /finance)
  const isSubfolder = rawPath.includes('/server') || rawPath.includes('/tickets') ||
    rawPath.includes('/orders') || rawPath.includes('/settings') ||
    rawPath.includes('/shop') || rawPath.includes('/gallery') ||
    rawPath.includes('/logs') || rawPath.includes('/profile') ||
    rawPath.includes('/users') || rawPath.includes('/login') ||
    rawPath.includes('/finance');
  const basePrefix = isSubfolder ? '../' : './';

  // Permission Map for Page Route Protection
  function getRequiredPermissionForCurrentRoute() {
    if (rawPath.includes('/orders')) return 'orders';
    if (rawPath.includes('/finance')) return 'finance';
    if (rawPath.includes('/tickets')) return 'tickets';
    if (rawPath.includes('/server')) return 'console';
    if (rawPath.includes('/settings')) return 'settings';
    if (rawPath.includes('/shop')) return 'shop';
    if (rawPath.includes('/gallery')) return 'gallery';
    if (rawPath.includes('/logs')) return 'logs';
    if (rawPath.includes('/users')) return 'users';
    return null; // Home dashboard ('/') and profile ('/profile') require active status only
  }

  const ALL_MODULES = ['users', 'orders', 'finance', 'console', 'settings', 'tickets', 'shop', 'gallery', 'logs'];
  let allowInternalAuthWrite = false;

  // Normalize permissions object to include both <module> (View) and <module>_edit (Edit) flags
  function normalizePermissions(rawInput, isOwner) {
    const raw = Object.assign({}, rawInput || {});
    const normalized = {};

    if (isOwner) {
      ALL_MODULES.forEach(function (mod) {
        normalized[mod] = true;
        normalized[mod + '_edit'] = true;
      });
      normalized.read_only = false;
      return Object.freeze(normalized);
    }

    const isGlobalReadOnly = Boolean(raw.read_only === true);

    // Mutual view fallback between orders and finance
    if (raw.orders === true && raw.finance === undefined) raw.finance = true;
    if (raw.finance === true && raw.orders === undefined) raw.orders = true;

    ALL_MODULES.forEach(function (mod) {
      const canView = Boolean(raw[mod] === true);
      normalized[mod] = canView;

      if (!canView || isGlobalReadOnly || mod === 'logs') {
        normalized[mod + '_edit'] = false;
      } else if (raw[mod + '_edit'] !== undefined) {
        normalized[mod + '_edit'] = Boolean(raw[mod + '_edit'] === true);
      } else {
        // Backwards compatibility for legacy profiles that only had boolean true
        normalized[mod + '_edit'] = true;
      }
    });

    // Mutual edit fallback between orders_edit and finance_edit when one wasn't explicitly set
    if (raw.orders_edit !== undefined && raw.finance_edit === undefined && normalized.finance) {
      normalized.finance_edit = Boolean(raw.orders_edit === true && !isGlobalReadOnly);
    }
    if (raw.finance_edit !== undefined && raw.orders_edit === undefined && normalized.orders) {
      normalized.orders_edit = Boolean(raw.finance_edit === true && !isGlobalReadOnly);
    }

    normalized.read_only = isGlobalReadOnly;
    return Object.freeze(normalized);
  }

  // Create a chainable rejected Supabase query builder stub when a write operation is blocked
  function createBlockedWriteBuilder(reason) {
    const response = Promise.resolve({
      data: null,
      error: { message: reason, code: '42501', details: 'Blocked by HavenCraft Zero-Bypass Write Guard' }
    });
    const proxy = new Proxy(function () { }, {
      get: function (_, prop) {
        if (prop === 'then') return response.then.bind(response);
        if (prop === 'catch') return response.catch.bind(response);
        if (prop === 'finally') return response.finally.bind(response);
        return function () { return proxy; };
      }
    });
    return proxy;
  }

  // Install Zero-Bypass Client Write Guard on Supabase client (.from & .storage.from)
  function installSupabaseWriteGuard(client) {
    if (!client || client._hvnWriteGuardInstalled) return;
    try {
      const tableModuleMap = {
        store_orders: 'orders',
        server_sales: 'finance',
        server_expenses: 'finance',
        tickets: 'tickets',
        ticket_messages: 'tickets',
        shop_items: 'shop',
        gallery: 'gallery',
        gallery_items: 'gallery',
        site_settings: 'settings',
        staff_members: 'users',
        staff_profiles: 'users'
      };

      const origFrom = client.from.bind(client);
      client.from = function (table) {
        const queryBuilder = origFrom(table);
        const cleanTable = String(table || '').trim().toLowerCase();

        // Guard audit_logs against tampering (insert allowed for logging, update/delete blocked for non-Owner)
        if (cleanTable === 'audit_logs') {
          ['update', 'delete', 'upsert'].forEach(function (method) {
            if (typeof queryBuilder[method] === 'function') {
              queryBuilder[method] = function () {
                const prof = window.hvnAuth ? window.hvnAuth.getCurrentProfile() : null;
                if (!prof || !prof.isOwner) {
                  console.error('[HavenSecurity] Blocked unauthorized "' + method + '" on audit_logs.');
                  return createBlockedWriteBuilder('Access Denied: Audit logs are immutable.');
                }
                return origFrom(table)[method].apply(origFrom(table), arguments);
              };
            }
          });
          return queryBuilder;
        }

        const requiredMod = tableModuleMap[cleanTable];
        if (!requiredMod) return queryBuilder;

        ['insert', 'update', 'upsert', 'delete'].forEach(function (method) {
          if (typeof queryBuilder[method] !== 'function') return;
          const origMethod = queryBuilder[method].bind(queryBuilder);

          queryBuilder[method] = function (payload) {
            if (allowInternalAuthWrite) {
              return origMethod.apply(queryBuilder, arguments);
            }

            const prof = window.hvnAuth ? window.hvnAuth.getCurrentProfile() : null;
            if (!prof) {
              return createBlockedWriteBuilder('Access Denied: Unverified staff session.');
            }
            if (prof.isOwner) {
              return origMethod.apply(queryBuilder, arguments);
            }

            // Special case for staff_profiles: a staff member may update ONLY their own safe profile metadata
            if (cleanTable === 'staff_profiles' && method === 'update' && payload && typeof payload === 'object' && !Array.isArray(payload)) {
              const canEditUsers = window.hvnAuth.canEdit('users');
              if (!canEditUsers) {
                // Strip any privilege-escalation fields if user lacks users_edit permission
                const forbiddenKeys = ['role', 'permissions', 'is_active', 'status', 'email', 'id'];
                const attemptedEscalation = forbiddenKeys.some(function (k) { return Object.prototype.hasOwnProperty.call(payload, k); });
                if (attemptedEscalation) {
                  console.error('[HavenSecurity] Blocked privilege escalation attempt on staff_profiles.');
                  return createBlockedWriteBuilder('Access Denied: You have View-Only access and cannot modify staff roles or permissions.');
                }
                // Allow self-profile metadata update (e.g., full_name, discord, minecraft_ign) unless global read_only is set
                if (prof.permissions && prof.permissions.read_only === true) {
                  return createBlockedWriteBuilder('Access Denied: Your account is in global View-Only mode.');
                }
                return origMethod.apply(queryBuilder, arguments);
              }
            }

            if (!window.hvnAuth.canEdit(requiredMod)) {
              console.warn('[HavenSecurity] Blocked "' + method + '" on "' + cleanTable + '" (View-Only access for module: ' + requiredMod + ').');
              return createBlockedWriteBuilder('View-Only Mode: You only have permission to view "' + requiredMod + '", not make changes.');
            }

            return origMethod.apply(queryBuilder, arguments);
          };
        });

        return queryBuilder;
      };

      // Guard Supabase Storage buckets against unauthorized uploads/deletions
      if (client.storage && typeof client.storage.from === 'function') {
        const origStorageFrom = client.storage.from.bind(client.storage);
        const bucketModuleMap = {
          gallery: 'gallery',
          tickets: 'tickets',
          shop: 'shop'
        };

        client.storage.from = function (bucketId) {
          const storageApi = origStorageFrom(bucketId);
          const cleanBucket = String(bucketId || '').trim().toLowerCase();
          const requiredMod = bucketModuleMap[cleanBucket];
          if (!requiredMod) return storageApi;

          ['upload', 'update', 'remove', 'move', 'copy'].forEach(function (method) {
            if (typeof storageApi[method] !== 'function') return;
            const origStorageMethod = storageApi[method].bind(storageApi);
            storageApi[method] = function () {
              const prof = window.hvnAuth ? window.hvnAuth.getCurrentProfile() : null;
              if (!prof || (!prof.isOwner && !window.hvnAuth.canEdit(requiredMod))) {
                console.warn('[HavenSecurity] Blocked storage "' + method + '" on bucket "' + cleanBucket + '" (View-Only access).');
                return Promise.resolve({
                  data: null,
                  error: { message: 'View-Only Mode: You do not have permission to upload or modify files in ' + cleanBucket + '.' }
                });
              }
              return origStorageMethod.apply(storageApi, arguments);
            };
          });

          return storageApi;
        };
      }

      Object.defineProperty(client, '_hvnWriteGuardInstalled', {
        value: true,
        writable: false,
        configurable: false
      });
    } catch (e) {
      console.warn('[HavenAuth] Could not install write guard:', e);
    }
  }

  let authProfileResolve = null;
  const authProfilePromise = new Promise(function (resolve) {
    authProfileResolve = resolve;
  });
  let activeRequireAuthPromise = null;

  window.hvnAuth = {
    normalizePermissions: normalizePermissions,

    async getSession() {
      const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
      if (client) installSupabaseWriteGuard(client);
      if (!client || !client.auth) return null;
      try {
        const { data: { session }, error } = await client.auth.getSession();
        if (error || !session) return null;
        return session;
      } catch (err) {
        return null;
      }
    },

    // SECURITY: Only return the cryptographically verified in-memory profile (never trust raw localStorage for RBAC)
    getCurrentProfile() {
      if (window.havenStaffProfile && window.havenStaffProfile._verified === true) {
        return window.havenStaffProfile;
      }
      return null;
    },

    // Check if the current user can VIEW a module
    canView(mod) {
      const p = this.getCurrentProfile();
      if (!p) return false;
      if (p.isOwner) return true;
      if (!mod) return true;
      const perms = p.permissions || {};
      if (mod === 'orders' || mod === 'finance') {
        return Boolean(perms[mod] === true || perms.orders === true || perms.finance === true);
      }
      return Boolean(perms[mod] === true);
    },

    // Check if the current user can EDIT / PERFORM ACTIONS in a module
    canEdit(mod) {
      const p = this.getCurrentProfile();
      if (!p) return false;
      if (p.isOwner) return true;
      const perms = p.permissions || {};
      if (perms.read_only === true || mod === 'logs') return false;
      if (!mod) return !perms.read_only;
      if (!this.canView(mod)) return false;

      if (mod === 'orders' || mod === 'finance') {
        if (perms[mod + '_edit'] !== undefined) {
          return Boolean(perms[mod + '_edit'] === true);
        }
        return Boolean(perms.orders_edit === true || perms.finance_edit === true);
      }
      return Boolean(perms[mod + '_edit'] === true);
    },

    // Check if the current user is in View-Only (Read-Only) mode for a module (defaults to current route module)
    isViewOnly(mod) {
      const targetMod = mod || getRequiredPermissionForCurrentRoute();
      const p = this.getCurrentProfile();
      if (!p || p.isOwner) return false;
      if (!targetMod) return Boolean(p.permissions && p.permissions.read_only === true);
      return Boolean(this.canView(targetMod) && !this.canEdit(targetMod));
    },

    applyViewOnlyPageUI() {
      const currentMod = getRequiredPermissionForCurrentRoute();
      const viewOnly = this.isViewOnly(currentMod);
      if (document.body) {
        document.body.classList.toggle('hvn-view-only-page', viewOnly);
      }
      const existingBadge = document.getElementById('hvnViewOnlyBanner');
      if (existingBadge) {
        existingBadge.remove();
      }
    },

    waitForProfile() {
      if (window.havenStaffProfile && window.havenStaffProfile._verified === true) {
        return Promise.resolve(window.havenStaffProfile);
      }
      return authProfilePromise;
    },

    async requireAuth(preResolvedSession) {
      if (activeRequireAuthPromise) {
        return activeRequireAuthPromise;
      }
      activeRequireAuthPromise = this._runRequireAuth(preResolvedSession).finally(function () {
        activeRequireAuthPromise = null;
      });
      return activeRequireAuthPromise;
    },

    async _runRequireAuth(preResolvedSession) {
      // If user accesses old lockscreen URL, redirect to dashboard or login
      if (rawPath.includes('/lockscreen') || rawPath.endsWith('lockscreen.html')) {
        const redir = isFile ? `${basePrefix}index.html` : '/';
        window.location.replace(redir);
        return;
      }

      const onLogin = isCurrentLoginPage();
      const onPending = isCurrentPendingPage();

      // Enforce ephemeral session ("Keep me signed in" unchecked)
      try {
        if (!onLogin && localStorage.getItem('hvn_ephemeral_session') === '1' && !sessionStorage.getItem('hvn_tab_session_active')) {
          await this.logout();
          return;
        }
      } catch (e) { }

      // Check live session (prefer preResolvedSession only if valid, otherwise wait on getSession)
      const session = (preResolvedSession && preResolvedSession.access_token)
        ? preResolvedSession
        : await this.getSession();

      // CASE 1: NOT LOGGED IN
      if (!session || !session.access_token) {
        localStorage.removeItem('hvn_staff_profile');
        if (onLogin) {
          document.documentElement.classList.add('hvn-auth-ready');
          if (authProfileResolve) authProfileResolve(null);
          try { window.dispatchEvent(new CustomEvent('hvnAuthUnauthenticated')); } catch (e) { }
          return;
        }
        const target = isFile ? `${basePrefix}login/index.html` : '/login';
        window.location.replace(target);
        return;
      }

      // CASE 2: LOGGED IN — CRYPTOGRAPHIC SUPABASE SERVER VERIFICATION (Anti-LocalStorage Tampering)
      const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
      if (!client || !client.auth) {
        const target = isFile ? `${basePrefix}login/index.html` : '/login';
        window.location.replace(target);
        return;
      }

      let verifiedUser = null;
      try {
        const { data: userData, error: userErr } = await client.auth.getUser(session.access_token);
        if (userErr || !userData || !userData.user) {
          console.warn('[HavenAuth] Session token rejected by Supabase Auth server.');
          await this.logout();
          return;
        }
        verifiedUser = userData.user;

        // Detect if someone tampered with user email or id inside localStorage sb-*-auth-token
        const localEmail = (session.user?.email || '').trim().toLowerCase();
        const serverEmail = (verifiedUser.email || '').trim().toLowerCase();
        if ((localEmail && localEmail !== serverEmail) || (session.user?.id && session.user.id !== verifiedUser.id)) {
          console.error('[HavenAuth] Security Violation: LocalStorage session tampering detected.');
          await this.logout();
          return;
        }
      } catch (verifyErr) {
        console.warn('[HavenAuth] Could not verify user with Supabase:', verifyErr);
        await this.logout();
        return;
      }

      let profile = null;
      let dbFetchSucceeded = false;

      // CRITICAL SECURITY: Owner is verified ONLY against server-verified email from Supabase Auth
      const userEmail = (verifiedUser.email || '').trim().toLowerCase();
      const isServerOwner = (userEmail === 'rajuel55@gmail.com');

      try {
        const { data, error: profErr } = await client
          .from('staff_profiles')
          .select('*')
          .eq('id', verifiedUser.id)
          .maybeSingle();

        if (!profErr) dbFetchSucceeded = true;

        if (data) {
          profile = data;
        } else {
          // Check if profile exists by email (pre-invited staff)
          const { data: emailData, error: emailErr } = await client
            .from('staff_profiles')
            .select('*')
            .eq('email', userEmail)
            .maybeSingle();

          if (!emailErr) dbFetchSucceeded = true;

          if (emailData) {
            profile = emailData;
          } else if (!isServerOwner && dbFetchSucceeded) {
            // Brand new user: Auto-register as Pending (cannot give themselves roles/permissions)
            const meta = verifiedUser.user_metadata || {};
            const provider = (verifiedUser.app_metadata && verifiedUser.app_metadata.provider) || '';
            const dName = meta.custom_claims?.global_name || meta.full_name || meta.preferred_username || userEmail.split('@')[0];
            const dTag = provider === 'discord'
              ? (meta.custom_claims?.preferred_username || meta.user_name || dName)
              : 'Not linked';

            const newPending = {
              id: verifiedUser.id,
              email: userEmail,
              full_name: dName,
              discord: dTag,
              minecraft_ign: 'Pending',
              role: 'Pending Verification',
              is_active: false,
              status: 'pending',
              permissions: { users: false, orders: false, finance: false, console: false, settings: false, tickets: false, shop: false, gallery: false, logs: false }
            };
            allowInternalAuthWrite = true;
            try {
              await client.from('staff_profiles').upsert(newPending);
            } finally {
              allowInternalAuthWrite = false;
            }
            profile = newPending;
          }
        }
      } catch (e) {
        console.warn('[HavenAuth] Profile fetch error:', e);
      }

      // Enforce Owner Profile invariants
      if (isServerOwner) {
        if (!profile) {
          profile = {
            id: verifiedUser.id,
            email: userEmail,
            full_name: 'Juel',
            minecraft_ign: 'JU3L_',
            role: 'Owner',
            is_active: true,
            status: 'active',
            permissions: { users: true, orders: true, finance: true, console: true, settings: true, tickets: true, shop: true, gallery: true, logs: true }
          };
        } else {
          profile.role = 'Owner';
          profile.is_active = true;
          profile.status = 'active';
          profile.permissions = { users: true, orders: true, finance: true, console: true, settings: true, tickets: true, shop: true, gallery: true, logs: true };
        }
      } else if (profile) {
        // Anti-escalation safety: Non-owner can NEVER hold Owner role
        if (profile.role === 'Owner') {
          profile.role = 'Pending Verification';
          profile.is_active = false;
          profile.status = 'pending';
          profile.permissions = { users: false, orders: false, finance: false, console: false, settings: false, tickets: false, shop: false, gallery: false, logs: false };
        }
      }

      // Check if user is pending approval
      const isPending = !isServerOwner && (!profile || !profile.is_active || profile.status !== 'active' || profile.role === 'Pending' || profile.role === 'Pending Verification');

      if (isPending) {
        localStorage.removeItem('hvn_staff_profile');
        if (!onPending) {
          const pendingTarget = isFile ? `${basePrefix}login/pending-approval.html` : '/login/pending-approval';
          window.location.replace(pendingTarget);
        } else {
          document.documentElement.classList.add('hvn-auth-ready');
          if (authProfileResolve) authProfileResolve(null);
        }
        return;
      }

      // Verified staff on login or pending page -> redirect to dashboard
      if (onLogin || onPending) {
        const target = isFile ? `${basePrefix}index.html` : '/';
        window.location.replace(target);
        return;
      }

      // STRICT ROUTE RBAC CHECK: Validate page access by required permission
      const requiredPerm = getRequiredPermissionForCurrentRoute();
      if (requiredPerm && !isServerOwner) {
        const staffPerms = profile.permissions || {};
        let hasAccess = staffPerms[requiredPerm] === true;

        // Mutual permission compatibility between finance and orders
        if (requiredPerm === 'finance' || requiredPerm === 'orders') {
          hasAccess = (staffPerms.finance === true || staffPerms.orders === true);
        }

        if (!hasAccess) {
          console.warn(`[HavenAuth] Access Denied: User lacks '${requiredPerm}' permission.`);
          alert(`Access Denied: You do not have permission to access this module (${requiredPerm}).`);
          const homeTarget = isFile ? `${basePrefix}index.html` : '/';
          window.location.replace(homeTarget);
          return;
        }
      }

      this.updateStaffUI(profile, verifiedUser);
      document.documentElement.classList.add('hvn-auth-ready');
    },

    updateStaffUI(staff, authUser) {
      if (!staff && !authUser) return;

      const profile = staff || {};
      const email = (authUser?.email || profile.email || '').trim().toLowerCase();
      const isOwner = (email === 'rajuel55@gmail.com');
      const name = profile.full_name || authUser?.user_metadata?.full_name || (isOwner ? 'Juel' : email.split('@')[0]);
      const role = isOwner ? 'Owner' : (profile.role || 'Moderator');
      const ign = profile.minecraft_ign || (isOwner ? 'JU3L_' : 'HavenCraft');
      const cleanAvatarIgn = window.hvnCleanMcIgn ? window.hvnCleanMcIgn(ign) : ign.replace(/^\.+/, '');
      const perms = normalizePermissions(profile.permissions, isOwner);

      const nameEl = document.getElementById('staffUserName');
      const roleEl = document.getElementById('staffRoleText');
      const avatarEl = document.getElementById('staffUserAvatar');
      const greetingEl = document.getElementById('dashGreetingUser');

      if (nameEl) nameEl.textContent = name;
      if (roleEl) roleEl.textContent = role;
      const finalAvatar = profile.avatar_url || `https://mc-heads.net/avatar/${encodeURIComponent(cleanAvatarIgn)}/64`;
      if (avatarEl) {
        avatarEl.src = finalAvatar;
        avatarEl.onerror = function () {
          if (!this.dataset.fb) {
            this.dataset.fb = '1';
            this.src = `https://minotar.net/helm/${encodeURIComponent(cleanAvatarIgn)}/128.png`;
          }
        };
      }
      const profAvatarEl = document.getElementById('profileUserAvatar');
      if (profAvatarEl) {
        profAvatarEl.src = finalAvatar;
        profAvatarEl.onerror = function () {
          if (!this.dataset.fb) {
            this.dataset.fb = '1';
            this.src = `https://minotar.net/helm/${encodeURIComponent(cleanAvatarIgn)}/128.png`;
          }
        };
      }
      if (greetingEl) greetingEl.textContent = ign;

      const staffObj = Object.freeze({
        _verified: true,
        id: profile.id || authUser?.id,
        email,
        name,
        fullName: name,
        ign,
        minecraftIgn: ign,
        role,
        isOwner,
        avatarUrl: profile.avatar_url || '',
        avatarType: profile.avatar_type || 'ign',
        minecraftIgn: ign,
        role,
        isOwner,
        is_active: isOwner ? true : (profile.is_active !== undefined ? Boolean(profile.is_active) : (profile.status === 'active')),
        isActive: isOwner ? true : (profile.is_active !== undefined ? Boolean(profile.is_active) : (profile.status === 'active')),
        status: isOwner ? 'active' : (profile.status || (profile.is_active ? 'active' : 'pending')),
        permissions: perms,
        discord: profile.discord || 'Not linked'
      });

      window.havenStaffProfile = staffObj;
      if (authProfileResolve) authProfileResolve(staffObj);

      this.applyViewOnlyPageUI();

      try {
        // Store display-only fields in localStorage (RBAC always verified live from Supabase)
        localStorage.setItem('hvn_staff_profile', JSON.stringify({
          id: staffObj.id,
          email: staffObj.email,
          name: staffObj.name,
          fullName: staffObj.fullName,
          ign: staffObj.ign,
          minecraftIgn: staffObj.minecraftIgn,
          role: staffObj.role,
          discord: staffObj.discord
        }));
      } catch (e) { }

      // Dispatch event for UI components (e.g., layout.js sidebar filter)
      window.dispatchEvent(new CustomEvent('hvnStaffProfileLoaded', { detail: staffObj }));
    },


    async login(email, password) {
      const cleanEmail = (email || '').trim().toLowerCase();
      const cleanPass = (password || '').trim();

      const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
      if (!client || !client.auth) throw new Error('Supabase client is not initialized');

      const { data, error } = await client.auth.signInWithPassword({
        email: cleanEmail,
        password: cleanPass
      });

      if (error) throw error;

      if (data && data.user) {
        try {
          const { data: profile } = await client
            .from('staff_profiles')
            .select('*')
            .eq('id', data.user.id)
            .maybeSingle();

          if (profile) {
            const isOwner = (typeof window.hvnIsOwnerEmail === 'function')
              ? window.hvnIsOwnerEmail(cleanEmail)
              : (cleanEmail === 'rajuel55@gmail.com');
            if (!profile.is_active && !isOwner) {
              await client.auth.signOut();
              throw new Error('This staff account is currently awaiting verification or deactivated.');
            }
            this.updateStaffUI(profile, data.user);
          } else {
            this.updateStaffUI(null, data.user);
          }
        } catch (e) {
          this.updateStaffUI(null, data.user);
        }
      }

      return data;
    },

    async logout() {
      localStorage.removeItem('hvn_staff_profile');
      localStorage.removeItem('hvn_demo_session');
      localStorage.removeItem('hvn_admin_token');
      localStorage.removeItem('hvn_ephemeral_session');
      try { sessionStorage.removeItem('hvn_tab_session_active'); } catch (e) { }
      const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
      if (client && client.auth) {
        try { await client.auth.signOut(); } catch (e) { }
      }
      const target = isFile ? `${basePrefix}login/index.html` : '/login';
      window.location.replace(target);
    },

    async getToken() {
      const session = await this.getSession();
      return session?.access_token || '';
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    // Only unveil login page immediately; protected pages stay hidden until requireAuth() verifies session & RBAC
    if (isCurrentLoginPage()) {
      document.documentElement.classList.add('hvn-auth-ready');
    }

    window.hvnAuth.requireAuth().catch(function (err) {
      console.error('[HavenAuth] requireAuth error:', err);
      if (isCurrentLoginPage() || isCurrentPendingPage()) {
        document.documentElement.classList.add('hvn-auth-ready');
      } else {
        const target = isFile ? `${basePrefix}login/index.html` : '/login';
        window.location.replace(target);
      }
    });

    const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
    if (client && client.auth) {
      client.auth.onAuthStateChange(function (event, session) {
        if ((event === 'SIGNED_IN' || event === 'INITIAL_SESSION' || event === 'USER_UPDATED') && session) {
          // Defer outside Supabase's _notifyAllSubscribers / navigator lock stack to prevent PKCE deadlocks
          setTimeout(function () {
            window.hvnAuth.requireAuth(session).catch(function () {
              if (isCurrentLoginPage() || isCurrentPendingPage()) {
                document.documentElement.classList.add('hvn-auth-ready');
              }
            });
          }, 0);
        }
      });
    }
  });
})();
