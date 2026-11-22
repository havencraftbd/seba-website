/**
 * Haven-Admin — shared front-end utilities.
 * Loaded by pages that need them (profile, lockscreen, ...).
 * Keep generic helpers here; page-specific logic stays in the page.
 */
(function () {
  'use strict';

  /**
   * SHA-256 hex digest (async).
   * Used for the local lockscreen password — never store a plaintext
   * password, not even in localStorage.
   */
  window.hvnSha256 = async function (text) {
    const data = new TextEncoder().encode(String(text == null ? '' : text));
    const buf = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(buf))
      .map(function (b) { return b.toString(16).padStart(2, '0'); })
      .join('');
  };

  /**
   * Styled confirmation dialog. Returns Promise<boolean>.
   * Replaces native confirm() across the panel.
   *
   *   const ok = await hvnConfirm({
   *     title: 'Remove staff member?',
   *     message: 'Sakib_MC will lose access immediately.',
   *     confirmText: 'Remove',
   *     tone: 'danger' // 'primary' | 'warning' | 'danger' (danger:true = 'danger')
   *   });
   *   if (!ok) return;
   */
  window.hvnConfirm = function (opts) {
    opts = opts || {};
    const title = opts.title || 'Are you sure?';
    const message = opts.message || '';
    const confirmText = opts.confirmText || 'Confirm';
    const cancelText = opts.cancelText || 'Cancel';
    const danger = !!opts.danger;
    const toneName = opts.tone || (danger ? 'danger' : 'primary');
    const tones = {
      danger:  { icon: 'bi-exclamation-triangle-fill', color: 'var(--danger)',  btn: 'btn-danger' },
      warning: { icon: 'bi-exclamation-triangle-fill', color: 'var(--warning)', btn: 'btn-warning' },
      primary: { icon: 'bi-question-circle-fill',      color: 'var(--primary)', btn: 'btn-primary' }
    };
    const tone = tones[toneName] || tones.primary;

    return new Promise(function (resolve) {
      const backdrop = document.createElement('div');
      backdrop.className = 'hvn-modal-backdrop';
      backdrop.innerHTML =
        '<div class="hvn-modal" role="dialog" aria-modal="true" aria-labelledby="hvnConfirmTitle">' +
          '<div class="hvn-modal-title">' +
            '<i class="bi ' + tone.icon + '"' +
            ' style="color: ' + tone.color + ';"></i>' +
            '<span id="hvnConfirmTitle"></span>' +
          '</div>' +
          '<div class="hvn-modal-msg"></div>' +
          '<div class="hvn-modal-actions">' +
            '<button type="button" class="btn btn-secondary btn-sm" data-act="cancel"></button>' +
            '<button type="button" class="btn btn-sm ' + tone.btn + '" data-act="ok"></button>' +
          '</div>' +
        '</div>';

      backdrop.querySelector('#hvnConfirmTitle').textContent = title;
      backdrop.querySelector('.hvn-modal-msg').textContent = message;
      backdrop.querySelector('[data-act="cancel"]').textContent = cancelText;
      backdrop.querySelector('[data-act="ok"]').textContent = confirmText;

      let settled = false;
      function done(value) {
        if (settled) return;
        settled = true;
        backdrop.classList.remove('show');
        document.removeEventListener('keydown', onKey);
        setTimeout(function () { backdrop.remove(); }, 180);
        resolve(value);
      }
      function onKey(e) {
        if (e.key === 'Escape') done(false);
      }

      backdrop.querySelector('[data-act="cancel"]').addEventListener('click', function () { done(false); });
      backdrop.querySelector('[data-act="ok"]').addEventListener('click', function () { done(true); });
      backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) done(false);
      });
      document.addEventListener('keydown', onKey);

      document.body.appendChild(backdrop);
      requestAnimationFrame(function () { backdrop.classList.add('show'); });
      const okBtn = backdrop.querySelector('[data-act="ok"]');
      if (okBtn) okBtn.focus();
    });
  };

  /**
   * Styled prompt dialog. Returns Promise<string|null>.
   * Replaces native window.prompt() across the panel.
   */
  window.hvnPrompt = function (opts) {
    opts = opts || {};
    const title = opts.title || 'Input Required';
    const message = opts.message || '';
    const defaultValue = opts.defaultValue || '';
    const placeholder = opts.placeholder || '';
    const confirmText = opts.confirmText || 'Confirm';
    const cancelText = opts.cancelText || 'Cancel';
    const toneName = opts.tone || 'primary';
    const tones = {
      danger:  { icon: 'bi-exclamation-triangle-fill', color: 'var(--danger)',  btn: 'btn-danger' },
      warning: { icon: 'bi-exclamation-triangle-fill', color: 'var(--warning)', btn: 'btn-warning' },
      primary: { icon: 'bi-pencil-square',             color: 'var(--primary)', btn: 'btn-primary' }
    };
    const tone = tones[toneName] || tones.primary;

    return new Promise(function (resolve) {
      const backdrop = document.createElement('div');
      backdrop.className = 'hvn-modal-backdrop';
      backdrop.innerHTML =
        '<div class="hvn-modal" role="dialog" aria-modal="true" aria-labelledby="hvnPromptTitle">' +
          '<div class="hvn-modal-title">' +
            '<i class="bi ' + tone.icon + '" style="color: ' + tone.color + ';"></i>' +
            '<span id="hvnPromptTitle"></span>' +
          '</div>' +
          (message ? '<div class="hvn-modal-msg"></div>' : '') +
          '<div style="margin-bottom: 18px;">' +
            '<input type="text" class="form-control hvn-prompt-input" style="width: 100%; box-sizing: border-box;" />' +
          '</div>' +
          '<div class="hvn-modal-actions">' +
            '<button type="button" class="btn btn-secondary btn-sm" data-act="cancel"></button>' +
            '<button type="button" class="btn btn-sm ' + tone.btn + '" data-act="ok"></button>' +
          '</div>' +
        '</div>';

      backdrop.querySelector('#hvnPromptTitle').textContent = title;
      if (message) backdrop.querySelector('.hvn-modal-msg').textContent = message;
      const input = backdrop.querySelector('.hvn-prompt-input');
      input.value = defaultValue;
      if (placeholder) input.placeholder = placeholder;
      backdrop.querySelector('[data-act="cancel"]').textContent = cancelText;
      backdrop.querySelector('[data-act="ok"]').textContent = confirmText;

      let settled = false;
      function done(val) {
        if (settled) return;
        settled = true;
        backdrop.classList.remove('show');
        document.removeEventListener('keydown', onKey);
        setTimeout(function () { backdrop.remove(); }, 180);
        resolve(val);
      }
      function onKey(e) {
        if (e.key === 'Escape') done(null);
        if (e.key === 'Enter') done(input.value);
      }

      backdrop.querySelector('[data-act="cancel"]').addEventListener('click', function () { done(null); });
      backdrop.querySelector('[data-act="ok"]').addEventListener('click', function () { done(input.value); });
      backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) done(null);
      });
      document.addEventListener('keydown', onKey);

      document.body.appendChild(backdrop);
      requestAnimationFrame(function () {
        backdrop.classList.add('show');
        input.focus();
        input.select();
      });
    });
  };

  /**
   * HTML Escaping utility to prevent XSS across the dashboard.
   */
  window.hvnEscapeHtml = function (str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  /**
   * Glassmorphic toast notification helper.
   * Usage: window.hvnToast('Order delivered successfully!', 'success')
   * Tone: 'success' | 'danger' | 'warning' | 'info'
   */
  window.hvnToast = function (message, type = 'info', duration = 3500) {
    let container = document.getElementById('hvnToastContainer');
    if (!container) {
      container = document.createElement('div');
      container.id = 'hvnToastContainer';
      container.style.cssText = `
        position: fixed;
        bottom: 24px;
        right: 24px;
        z-index: 99999;
        display: flex;
        flex-direction: column;
        gap: 10px;
        pointer-events: none;
        max-width: 380px;
        width: calc(100% - 48px);
      `;
      document.body.appendChild(container);
    }

    const tones = {
      success: { icon: 'bi-check-circle-fill', color: '#10b981', bg: 'rgba(16, 185, 129, 0.12)', border: 'rgba(16, 185, 129, 0.35)' },
      danger:  { icon: 'bi-exclamation-octagon-fill', color: '#ef4444', bg: 'rgba(239, 68, 68, 0.12)', border: 'rgba(239, 68, 68, 0.35)' },
      warning: { icon: 'bi-exclamation-triangle-fill', color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.12)', border: 'rgba(245, 158, 11, 0.35)' },
      info:    { icon: 'bi-info-circle-fill', color: '#38bdf8', bg: 'rgba(56, 189, 248, 0.12)', border: 'rgba(56, 189, 248, 0.35)' }
    };
    const tone = tones[type] || tones.info;

    const toast = document.createElement('div');
    toast.style.cssText = `
      pointer-events: auto;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 18px;
      background: rgba(15, 20, 32, 0.88);
      backdrop-filter: blur(14px);
      -webkit-backdrop-filter: blur(14px);
      border: 1px solid ${tone.border};
      border-radius: 12px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), 0 0 15px ${tone.bg};
      color: #f1f5f9;
      font-size: 0.86rem;
      font-weight: 500;
      opacity: 0;
      transform: translateY(12px) scale(0.96);
      transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
      max-height: 160px;
      overflow: hidden;
      word-break: break-word;
    `;

    toast.innerHTML = `
      <i class="bi ${tone.icon}" style="color: ${tone.color}; font-size: 1.15rem; flex-shrink: 0;"></i>
      <span style="flex: 1; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; word-break: break-word;">${window.hvnEscapeHtml(message)}</span>
      <button type="button" style="background: transparent; border: none; color: #94a3b8; cursor: pointer; padding: 2px; display: flex; align-items: center; font-size: 0.9rem; flex-shrink: 0;" title="Close">
        <i class="bi bi-x-lg"></i>
      </button>
    `;

    const closeBtn = toast.querySelector('button');
    function dismiss() {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px) scale(0.95)';
      setTimeout(() => { toast.remove(); }, 250);
    }
    closeBtn.addEventListener('click', dismiss);

    container.appendChild(toast);
    requestAnimationFrame(() => {
      toast.style.opacity = '1';
      toast.style.transform = 'translateY(0) scale(1)';
    });

    if (duration > 0) {
      setTimeout(dismiss, duration);
    }
  };

  /**
   * Client-side WebP image auto-compression & resize utility (Option 1).
   * Automatically scales images down to max 1920x1080 and converts to high-fidelity WebP (quality 0.82).
   * Cuts typical 3-5 MB Minecraft screenshots down to 80-150 KB (90-95% bandwidth and storage savings).
   *
   * @param {File|Blob} file - The original uploaded file object
   * @param {Object} [options] - Optional custom dimensions and quality
   * @param {number} [options.maxWidth=1920] - Max width constraint
   * @param {number} [options.maxHeight=1080] - Max height constraint
   * @param {number} [options.quality=0.82] - WebP export quality (0.0 to 1.0)
   * @returns {Promise<{file: File, blob: Blob, originalSize: number, compressedSize: number, savedPercent: number, width: number, height: number, originalName: string, compressedName: string}>}
   */
  window.hvnCompressToWebP = function (file, options = {}) {
    return new Promise(function (resolve, reject) {
      if (!file || !file.type || !file.type.startsWith('image/')) {
        return reject(new Error('Invalid image file provided.'));
      }

      // Preserve vector SVGs and animated GIFs
      if (file.type === 'image/svg+xml' || file.type === 'image/gif') {
        return resolve({
          file: file,
          blob: file,
          originalSize: file.size,
          compressedSize: file.size,
          savedPercent: 0,
          width: 0,
          height: 0,
          originalName: file.name || 'image',
          compressedName: file.name || 'image'
        });
      }

      const maxWidth = options.maxWidth || 1920;
      const maxHeight = options.maxHeight || 1080;
      const quality = typeof options.quality === 'number' ? options.quality : 0.82;

      const reader = new FileReader();
      reader.onerror = function (e) {
        reject(e || new Error('Failed to read image file.'));
      };

      reader.onload = function (e) {
        const img = new Image();
        img.onerror = function (err) {
          reject(err || new Error('Failed to load image in canvas.'));
        };

        img.onload = function () {
          try {
            let width = img.naturalWidth || img.width;
            let height = img.naturalHeight || img.height;

            // Constrain within bounding box preserving aspect ratio
            if (width > maxWidth || height > maxHeight) {
              const ratio = Math.min(maxWidth / width, maxHeight / height);
              width = Math.round(width * ratio);
              height = Math.round(height * ratio);
            }

            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;

            const ctx = canvas.getContext('2d');
            ctx.imageSmoothingEnabled = true;
            ctx.imageSmoothingQuality = 'high';
            ctx.drawImage(img, 0, 0, width, height);

            canvas.toBlob(
              function (blob) {
                if (!blob) {
                  return reject(new Error('Canvas WebP encoding failed.'));
                }

                const rawName = file.name || 'screenshot';
                const baseName = rawName.substring(0, rawName.lastIndexOf('.')) || rawName;
                const cleanBase = baseName.replace(/[^a-zA-Z0-9_-]/g, '_').toLowerCase();
                const newFileName = cleanBase + '.webp';

                const compressedFile = new File([blob], newFileName, {
                  type: 'image/webp',
                  lastModified: Date.now()
                });

                const origSize = file.size;
                const compSize = compressedFile.size;
                const savedPercent = origSize > compSize
                  ? Math.round(((origSize - compSize) / origSize) * 100)
                  : 0;

                resolve({
                  file: compressedFile,
                  blob: blob,
                  originalSize: origSize,
                  compressedSize: compSize,
                  savedPercent: savedPercent,
                  width: width,
                  height: height,
                  originalName: file.name || 'image',
                  compressedName: newFileName
                });
              },
              'image/webp',
              quality
            );
          } catch (canvasErr) {
            reject(canvasErr);
          }
        };

        img.src = e.target.result;
      };

      reader.readAsDataURL(file);
    });
  };

  /**
   * Global Audit Log writer using cryptographically verified staff profile.
   */
  window.hvnLogAudit = async function (category, action, target, result = 'Success', details = {}) {
    const client = window.initHavenSupabase ? window.initHavenSupabase() : window.supabaseClient;
    if (!client) return;

    if (window.hvnAuth && typeof window.hvnAuth.waitForProfile === 'function') {
      await window.hvnAuth.waitForProfile();
    }

    const profile = (window.havenStaffProfile && window.havenStaffProfile._verified === true)
      ? window.havenStaffProfile
      : {};
    const staffName = profile.ign || profile.minecraftIgn || profile.name || 'Staff';
    const staffRole = profile.role || 'Staff';

    try {
      await client.from('audit_logs').insert([{
        staff_name: staffName,
        staff_role: staffRole,
        category: category,
        action: action,
        target: target,
        result: result,
        details: details
      }]);
    } catch (e) {
      console.warn('Audit log write error:', e);
    }
  };

  /**
   * Lightweight Debounce utility to prevent rapid Realtime events or search inputs
   * from spamming Supabase queries or DOM re-renders.
   */
  window.hvnDebounce = function (fn, delay = 350) {
    let timer = null;
    return function (...args) {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        timer = null;
        fn.apply(this, args);
      }, delay);
    };
  };

  /**
   * 1-Click Copy helper with automatic toast notification.
   * Ensures toasts remain clean, compact, and never dump giant text blocks or chat messages.
   */
  window.hvnCopyText = function (text, label = 'Copied to clipboard') {
    const val = String(text == null ? '' : text).trim();
    if (!val || val === '-' || val === '--') return;

    // Build a compact, clean toast label. Never dump long or multiline text into the toast notification.
    let toastMsg = 'Copied to clipboard';
    const isExplicitAction = /copied/i.test(label || '');
    const isLongOrMultiline = val.length > 36 || val.includes('\n');

    if (isExplicitAction) {
      toastMsg = label;
    } else if (isLongOrMultiline || !label || label === 'Copied to clipboard') {
      toastMsg = (label && label !== 'Copied to clipboard') ? `${label} copied` : 'Copied to clipboard';
    } else {
      toastMsg = `${label}: ${val}`;
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(val).then(() => {
        if (window.hvnToast) window.hvnToast(toastMsg, 'success', 2200);
      }).catch(() => {});
    } else {
      const ta = document.createElement('textarea');
      ta.value = val;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        if (window.hvnToast) window.hvnToast(toastMsg, 'success', 2200);
      } catch (_) {}
      document.body.removeChild(ta);
    }
  };

  /**
   * Safe event handler for copy pill elements: reads data-copy-val and data-copy-label.
   * Avoids inline script string interpolation and prevents attribute breakouts.
   */
  window.hvnHandleCopy = function (el) {
    if (!el) return;
    const val = el.getAttribute('data-copy-val') || '';
    const label = el.getAttribute('data-copy-label') || 'Copied to clipboard';
    if (window.hvnCopyText) {
      window.hvnCopyText(val, label);
    }
  };

  /**
   * Smart Shared Server Telemetry Fetcher with 10-second session cache & in-flight deduplication.
   * Avoids redundant /api/server-status calls when switching pages or when multiple widgets query status.
   */
  let _telemetryMemoryCache = null;
  let _telemetryMemoryTime = 0;
  let _telemetryInFlight = null;
  const TELEMETRY_TTL_MS = 9500;

  window.hvnFetchServerStatus = async function (opts = {}) {
    const force = Boolean(opts.force);
    const now = Date.now();

    if (!force && _telemetryMemoryCache && (now - _telemetryMemoryTime < TELEMETRY_TTL_MS)) {
      return _telemetryMemoryCache;
    }

    if (!force) {
      try {
        const raw = sessionStorage.getItem('hvn_telemetry_cache');
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && parsed.ts && (now - parsed.ts < TELEMETRY_TTL_MS) && parsed.data) {
            _telemetryMemoryCache = parsed.data;
            _telemetryMemoryTime = parsed.ts;
            return parsed.data;
          }
        }
      } catch (_) {}
    }

    if (_telemetryInFlight) {
      return _telemetryInFlight;
    }

    _telemetryInFlight = (async () => {
      try {
        const session = window.hvnAuth ? await window.hvnAuth.getSession() : null;
        const token = session?.access_token || '';
        const res = await fetch('/api/server-status', {
          headers: token ? { 'Authorization': `Bearer ${token}` } : {}
        });
        if (!res.ok) return _telemetryMemoryCache || null;
        const data = await res.json();
        if (data && data.success) {
          _telemetryMemoryCache = data;
          _telemetryMemoryTime = Date.now();
          try {
            sessionStorage.setItem('hvn_telemetry_cache', JSON.stringify({
              ts: _telemetryMemoryTime,
              data: data
            }));
          } catch (_) {}
        }
        return data;
      } catch (err) {
        return _telemetryMemoryCache || null;
      } finally {
        _telemetryInFlight = null;
      }
    })();

    return _telemetryInFlight;
  };
})();


/**
 * Animated number count-up for stat cards.
 * hvnCountUp(element, endValue, formatFn) — animates 0 → endValue over ~750ms,
 * calling formatFn(value) each frame. Respects prefers-reduced-motion.
 */
window.hvnCountUp = function (el, end, formatFn) {
  if (!el) return;
  const fmt = formatFn || function (n) { return Math.round(n).toLocaleString(); };
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.textContent = fmt(end);
    return;
  }
  const duration = 750;
  const start = performance.now();
  function frame(now) {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    el.textContent = fmt(end * eased);
    if (t < 1) requestAnimationFrame(frame);
    else el.textContent = fmt(end);
  }
  requestAnimationFrame(frame);
};

/**
 * Undo-able destructive action: replaces the confirm() flow.
 * Hide/remove the row optimistically, then either the user clicks Undo
 * (restore callback runs) or after 6s the real delete executes.
 *
 *   const cancel = window.hvnUndoAction('Sale #12 deleted', deleteFn, restoreFn);
 */
window.hvnUndoAction = function (label, performFn, restoreFn, duration = 6000) {
  let container = document.getElementById('hvnToastContainer');
  if (!container) {
    container = document.createElement('div');
    container.id = 'hvnToastContainer';
    container.style.cssText = 'position:fixed;bottom:24px;right:24px;z-index:99999;display:flex;flex-direction:column;gap:10px;pointer-events:none;max-width:380px;width:calc(100% - 48px);';
    document.body.appendChild(container);
  }

  let done = false;
  const toast = document.createElement('div');
  toast.style.cssText = 'pointer-events:auto;display:flex;align-items:center;gap:12px;padding:12px 16px;background:rgba(15,20,32,0.92);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);border:1px solid rgba(245,158,11,0.4);border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,0.5);color:#f1f5f9;font-size:0.86rem;font-weight:500;';
  toast.innerHTML = `
    <i class="bi bi-trash3" style="color: #f59e0b; font-size: 1.1rem; flex-shrink: 0;"></i>
    <span style="flex:1;line-height:1.4;">${window.hvnEscapeHtml(label)}</span>
    <button type="button" class="hvn-undo-btn" style="background:rgba(0,200,150,0.15);border:1px solid rgba(0,200,150,0.4);color:#00c896;font-weight:700;font-size:0.78rem;padding:6px 14px;border-radius:8px;cursor:pointer;">UNDO</button>
    <button type="button" class="hvn-undo-close" style="background:transparent;border:none;color:#94a3b8;cursor:pointer;padding:2px;display:flex;align-items:center;" title="Dismiss">
      <i class="bi bi-x-lg" style="font-size:0.85rem;"></i>
    </button>
  `;
  container.appendChild(toast);

  function finalize(confirmed) {
    if (done) return;
    done = true;
    clearTimeout(timer);
    toast.remove();
    if (confirmed && typeof performFn === 'function') performFn();
    if (!confirmed && typeof restoreFn === 'function') restoreFn();
  }

  const timer = setTimeout(function () { finalize(true); }, duration);
  toast.querySelector('.hvn-undo-btn').addEventListener('click', function () { finalize(false); });
  toast.querySelector('.hvn-undo-close').addEventListener('click', function () { finalize(true); });
  return function () { finalize(false); };
};

/**
 * Tiny inline SVG sparkline (no chart library).
 * hvnSparkline(values[], width, height) → svg markup string.
 */
window.hvnSparkline = function (values, w = 88, h = 26) {
  const data = (values || []).filter(v => typeof v === 'number' && isFinite(v));
  if (data.length < 2) return '';
  const max = Math.max(...data), min = Math.min(...data);
  const range = (max - min) || 1;
  const stepX = w / (data.length - 1);
  const pts = data.map((v, i) => `${(i * stepX).toFixed(1)},${(h - 3 - ((v - min) / range) * (h - 6)).toFixed(1)}`);
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" style="display:block;" aria-hidden="true">
    <polyline points="${pts.join(' ')}" fill="none" stroke="var(--primary)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" opacity="0.85"></polyline>
    <circle cx="${pts[pts.length - 1].split(',')[0]}" cy="${pts[pts.length - 1].split(',')[1]}" r="2.4" fill="var(--primary)"></circle>
  </svg>`;
};
