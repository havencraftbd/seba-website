/**
 * ============================================================================
 * HAVENCRAFT SMP — CENTRAL SERVER & SYSTEM CONFIGURATION
 * ============================================================================
 * Single Source of Truth for the entire HavenCraft Admin Dashboard.
 * To change server IP or ports, simply update the values below.
 * All dashboard pages (Dashboard, Console, Settings, Layout) automatically
 * load and propagate these values globally.
 * ============================================================================
 */

(function () {
  'use strict';

  // ==========================================================================
  // Active Server Hostname & Port Defaults:
  // ==========================================================================
  const DEFAULT_SERVER_IP   = 'mc.havencraft.pro';
  const DEFAULT_SERVER_PORT = '2566';
  const DEFAULT_BEDROCK_PORT = '2025';
  const DEFAULT_OWNER_EMAIL = 'rajuel55@gmail.com';

  // Load from persistent local storage or defaults
  const saved = localStorage.getItem('hvn_site_config');
  let config = {};
  if (saved) {
    try { config = JSON.parse(saved); } catch (e) { config = {}; }
  }

  const serverIp = config.serverIp || DEFAULT_SERVER_IP;
  const port = config.port || config.javaPort || DEFAULT_SERVER_PORT;
  const javaPort = config.javaPort || port || DEFAULT_SERVER_PORT;
  const bedrockPort = config.bedrockPort || DEFAULT_BEDROCK_PORT;
  // SECURITY: Never trust localStorage for ownerEmail to prevent client-side privilege escalation
  const ownerEmail = DEFAULT_OWNER_EMAIL;

  window.HVN_CONFIG = {
    serverIp: serverIp,
    port: port,
    javaPort: javaPort,
    bedrockPort: bedrockPort,
    discordUrl: config.discordUrl || 'https://dc.havencraft.pro',
    seasonName: config.seasonName || 'Season 6',
    websiteUrl: 'https://havencraft.pro',

    // Helper: Returns full target string e.g. "mc.havencraft.pro:2566"
    getServerTarget: function () {
      const ip = this.serverIp || DEFAULT_SERVER_IP;
      const p = this.port || this.javaPort || DEFAULT_SERVER_PORT;
      return (p && String(p) !== '25565') ? `${ip}:${p}` : ip;
    },

    // Helper: Verify if an email belongs to the primary server owner (Immutable check)
    isOwnerEmail: function (email) {
      if (!email) return false;
      return String(email).trim().toLowerCase() === DEFAULT_OWNER_EMAIL;
    }
  };

  Object.defineProperty(window.HVN_CONFIG, 'ownerEmail', {
    value: DEFAULT_OWNER_EMAIL,
    writable: false,
    configurable: false,
    enumerable: true
  });

  // Global helper accessible across all scripts (Immutable reference)
  Object.defineProperty(window, 'hvnIsOwnerEmail', {
    value: function (email) {
      if (!email) return false;
      return String(email).trim().toLowerCase() === DEFAULT_OWNER_EMAIL;
    },
    writable: false,
    configurable: false
  });

  // Global helper: Clean Bedrock leading dot ('.') for mc-heads.net avatar URLs
  window.hvnCleanMcIgn = function (ign, fallback = 'Steve') {
    const raw = String(ign || '').trim().replace(/^\.+/, '');
    return raw || fallback;
  };

  // Global helper: Local timezone YYYY-MM-DD key (Prevents UTC midnight offset bug in BD UTC+6)
  window.hvnFormatLocalDateKey = function (dateInput) {
    if (!dateInput) return '';
    const str = String(dateInput).trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(str)) return str;
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return str.split('T')[0] || '';
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  };

  // Global helper: Local timezone YYYY-MM key
  window.hvnFormatLocalMonthKey = function (dateInput) {
    const key = window.hvnFormatLocalDateKey(dateInput);
    return key ? key.slice(0, 7) : '';
  };

  function applyConfigToDom() {
    if (!document.body) return;
    document.querySelectorAll('[data-config]').forEach(el => {
      try {
        const key = el.getAttribute('data-config');
        if (!key) return;

        let val;
        if (key === 'serverTarget') {
          val = window.HVN_CONFIG ? window.HVN_CONFIG.getServerTarget() : DEFAULT_SERVER_IP;
        } else if (window.HVN_CONFIG && window.HVN_CONFIG[key] !== undefined) {
          val = window.HVN_CONFIG[key];
        } else {
          val = el.getAttribute('data-config-fallback') || null;
          if (val === null && !el.textContent.trim()) {
            console.warn(`[HVN_CONFIG] Missing config value for [data-config="${key}"]`);
          }
        }

        if (val !== null && val !== undefined) {
          if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
            el.value = val;
          } else {
            el.textContent = val;
          }
        }
      } catch (err) {
        console.warn('[HVN_CONFIG] Error applying config to element:', err);
      }
    });
  }

  // Central updater: call this anywhere (e.g. Settings page) to sync globally
  window.updateGlobalConfig = function (newValues) {
    if (!newValues || typeof newValues !== 'object') return;
    const safeValues = Object.assign({}, newValues);
    delete safeValues.ownerEmail;
    delete safeValues.supabaseUrl;
    delete safeValues.supabaseAnonKey;

    if (safeValues.port && !safeValues.javaPort) safeValues.javaPort = safeValues.port;
    if (safeValues.javaPort && !safeValues.port) safeValues.port = safeValues.javaPort;
    Object.assign(window.HVN_CONFIG, safeValues);
    localStorage.setItem('hvn_site_config', JSON.stringify({
      serverIp: window.HVN_CONFIG.serverIp,
      port: window.HVN_CONFIG.port,
      javaPort: window.HVN_CONFIG.javaPort,
      bedrockPort: window.HVN_CONFIG.bedrockPort,
      discordUrl: window.HVN_CONFIG.discordUrl,
      seasonName: window.HVN_CONFIG.seasonName
    }));

    // Broadcast change to all active components
    window.dispatchEvent(new CustomEvent('hvnConfigUpdated', { detail: window.HVN_CONFIG }));
    applyConfigToDom();
  };

  window.applyConfigToDom = applyConfigToDom;

  // Auto-sync live site_settings from Supabase on Admin startup
  async function syncAdminConfigFromSupabase() {
    try {
      const REST_URL = 'https://nvcnowvasqcsygtbwktw.supabase.co/rest/v1/site_settings?select=key,value';
      const REST_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im52Y25vd3Zhc3Fjc3lndGJ3a3R3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4OTc0MDYsImV4cCI6MjEwNzQ3MzQwNn0.-5z7uOIcr3DBAuBLm7xtiirPqFuVGl_rExTG1rdU61k';
      const res = await fetch(REST_URL, {
        headers: {
          'apikey': REST_KEY,
          'Authorization': `Bearer ${REST_KEY}`
        }
      });
      if (!res.ok) return;
      const rows = await res.json();
      if (!Array.isArray(rows) || rows.length === 0) return;

      const map = {};
      rows.forEach(r => { if (r && r.key) map[r.key] = r.value; });

      const conn = map.server_connectivity || map.server_info;
      const season = map.season;
      const updates = {};

      if (conn && typeof conn === 'object') {
        if (conn.serverIp) updates.serverIp = String(conn.serverIp).trim();
        if (conn.port || conn.javaPort) {
          updates.port = String(conn.port || conn.javaPort).trim();
          updates.javaPort = updates.port;
        }
        if (conn.bedrockPort) updates.bedrockPort = String(conn.bedrockPort).trim();
        if (conn.discordUrl) updates.discordUrl = String(conn.discordUrl).trim();
      }
      if (season && typeof season === 'object' && season.name) {
        updates.seasonName = String(season.name).trim();
      }

      if (Object.keys(updates).length > 0) {
        window.updateGlobalConfig(updates);
      }
    } catch (e) { }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      applyConfigToDom();
      syncAdminConfigFromSupabase();
    });
  } else {
    applyConfigToDom();
    syncAdminConfigFromSupabase();
  }
})();
