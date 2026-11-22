/**
 * HavenCraft SMP — Supabase Client Wrapper
 * Uses Public Anon Key with Row Level Security (RLS).
 * Zero Secrets Stored in Client-Side Code.
 */

(function () {
  'use strict';

  // Unified Core Supabase Project (Auth, Orders, Tickets, Site Settings, Gallery, Audit Logs)
  const CORE_SUPABASE_URL = 'https://nvcnowvasqcsygtbwktw.supabase.co';
  const CORE_SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im52Y25vd3Zhc3Fjc3lndGJ3a3R3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4OTc0MDYsImV4cCI6MjEwNzQ3MzQwNn0.-5z7uOIcr3DBAuBLm7xtiirPqFuVGl_rExTG1rdU61k';

  function initClients() {
    if (typeof window.supabase !== 'undefined' && typeof window.supabase.createClient === 'function') {
      if (!window.supabaseClient) {
        window.supabaseClient = window.supabase.createClient(CORE_SUPABASE_URL, CORE_SUPABASE_KEY, {
          auth: {
            flowType: 'pkce',
            autoRefreshToken: true,
            persistSession: true,
            detectSessionInUrl: true
          }
        });
        window.hvnDb = window.supabaseClient;
        window.hvnGalleryDb = window.supabaseClient; // Alias to single authenticated project
      }
      return true;
    }
    return false;
  }

  // Auto-init or fallback helper
  initClients();

  window.initHavenSupabase = function () {
    initClients();
    return window.supabaseClient;
  };

  // Safe fallback pointing to primary authenticated client
  window.initHavenGallerySupabase = function () {
    initClients();
    return window.supabaseClient;
  };
})();

