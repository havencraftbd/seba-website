/**
 * HavenCraft — Universal Theme Engine & Pre-Paint Applier (v4.0)
 * Loaded in <head> BEFORE admin.css so saved theme choices apply
 * instantly without flash of unstyled content (FOUC).
 *
 * Manages:
 *  - Mode: 'dark' (default) | 'light'
 *  - Accent: 'emerald' (default) | 'cyan' | 'violet' | 'amber'
 *  - Density: 'comfortable' (default) | 'compact'
 *  - Realtime cross-tab synchronization via 'storage' event
 *  - Browser-level color-scheme for native form controls and scrollbars
 */
(function () {
  'use strict';

  function apply(mode, accent, density, persist) {
    var root = document.documentElement;
    if (!root) return;

    mode = mode === 'light' ? 'light' : 'dark';
    accent = accent || 'emerald';
    density = density || 'comfortable';

    // Auth/public pages (login, pending-approval, setup-password) carry the
    // hvn-public-page class and are designed dark-only — the dashboard's
    // light preference must never half-apply to them.
    if (mode === 'light' && root.classList.contains('hvn-public-page')) {
      mode = 'dark';
    }

    // Apply data-theme attribute
    if (mode === 'light') {
      root.setAttribute('data-theme', 'light');
    } else {
      root.removeAttribute('data-theme');
    }

    // Set native browser color scheme (syncs native scrollbars, inputs, date pickers)
    try {
      root.style.colorScheme = mode;
    } catch (e) { }

    // Apply data-accent attribute
    if (accent && accent !== 'emerald') {
      root.setAttribute('data-accent', accent);
    } else {
      root.removeAttribute('data-accent');
    }

    // Apply data-density attribute
    if (density === 'compact') {
      root.setAttribute('data-density', 'compact');
    } else {
      root.removeAttribute('data-density');
    }

    // Persist to localStorage if requested
    if (persist !== false) {
      try {
        localStorage.setItem('hvn_theme_mode', mode);
        localStorage.setItem('hvn_theme_accent', accent);
        localStorage.setItem('hvn_theme_density', density);
      } catch (e) { /* storage unavailable (e.g. strict private mode) */ }
    }

    // Dispatch custom event for dynamic components (Chart.js, layout, widgets)
    try {
      window.dispatchEvent(new CustomEvent('hvnThemeChanged', {
        detail: { mode: mode, accent: accent, density: density }
      }));
    } catch (e) { }
  }

  function get() {
    var mode = 'dark';
    var accent = 'emerald';
    var density = 'comfortable';
    try {
      mode = localStorage.getItem('hvn_theme_mode') || 'dark';
      accent = localStorage.getItem('hvn_theme_accent') || 'emerald';
      density = localStorage.getItem('hvn_theme_density') || 'comfortable';
    } catch (e) { }
    return { mode: mode, accent: accent, density: density };
  }

  // Pre-paint initial application
  try {
    var current = get();
    apply(current.mode, current.accent, current.density, false);
  } catch (e) { }

  // Expose global Theme API
  window.hvnApplyTheme = apply;
  window.hvnGetTheme = get;

  // Cross-tab realtime theme synchronization
  window.addEventListener('storage', function (e) {
    if (e.key === 'hvn_theme_mode' || e.key === 'hvn_theme_accent' || e.key === 'hvn_theme_density') {
      var updated = get();
      apply(updated.mode, updated.accent, updated.density, false);
    }
  });
})();
