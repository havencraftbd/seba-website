/* ============================================
   HavenCraft — Micro-Animation Engine
   ============================================ */

(function () {
    'use strict';

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn, .btn-vote');
        if (!btn || btn.classList.contains('copy-ip-btn')) return;

        if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }

        const circle = document.createElement('span');
        circle.classList.add('hc-ripple');

        const rect = btn.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        circle.style.width = circle.style.height = size + 'px';
        circle.style.left = (e.clientX - rect.left - size / 2) + 'px';
        circle.style.top = (e.clientY - rect.top - size / 2) + 'px';

        btn.appendChild(circle);

        // Fallback timer ensures removal if animationend is suppressed
        let removed = false;
        const removeCircle = () => {
            if (!removed) {
                removed = true;
                circle.remove();
            }
        };

        circle.addEventListener('animationend', removeCircle, { once: true });
        setTimeout(removeCircle, 650);
    });

    function animateCounter(el, target, suffix) {
        if (el.dataset.hcCounted) return;
        el.dataset.hcCounted = 'true';

        if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            el.textContent = target.toLocaleString() + (suffix || '');
            return;
        }

        const duration = 600;
        const start = performance.now();

        function update(now) {
            const elapsed = now - start;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            const current = Math.round(target * eased);

            el.textContent = current.toLocaleString() + (suffix || '');

            if (progress < 1) {
                requestAnimationFrame(update);
            } else {
                el.textContent = target.toLocaleString() + (suffix || '');
            }
        }

        requestAnimationFrame(update);
    }

    function initCounters() {
        const targets = document.querySelectorAll('.vote-count .number, .fame-votes strong');
        if (targets.length === 0) return;

        if (typeof IntersectionObserver === 'undefined') {
            targets.forEach((el) => {
                const match = el.textContent.trim().match(/^([\d,]+)\s*(.*)$/);
                if (match) {
                    const num = parseInt(match[1].replace(/,/g, ''), 10);
                    if (!isNaN(num)) el.textContent = num.toLocaleString() + (match[2] || '');
                }
            });
            return;
        }

        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) return;

                const el = entry.target;
                const match = el.textContent.trim().match(/^([\d,]+)\s*(.*)$/);
                if (match) {
                    const num = parseInt(match[1].replace(/,/g, ''), 10);
                    const suffix = match[2] || '';
                    if (!isNaN(num) && num > 0 && Number.isFinite(num)) {
                        animateCounter(el, num, suffix);
                    }
                }

                observer.unobserve(el);
            });
        }, { threshold: 0.3 });

        targets.forEach(el => observer.observe(el));
    }

    function initEnhancedReveal() {
        const items = document.querySelectorAll('.reveal, .reveal-stagger');
        if (items.length === 0) return;

        if (typeof IntersectionObserver === 'undefined') {
            items.forEach(el => el.classList.add('visible'));
            return;
        }

        const isMobile = typeof window !== 'undefined' && window.innerWidth <= 768;
        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('visible');
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: isMobile ? 0.02 : 0.08,
            rootMargin: isMobile ? '0px 0px -15px 0px' : '0px 0px -40px 0px'
        });

        items.forEach((el) => {
            if (!el.classList.contains('visible')) {
                observer.observe(el);
            }
        });
    }

    window.initEnhancedReveal = initEnhancedReveal;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initCounters();
            initEnhancedReveal();
        });
    } else {
        initCounters();
        initEnhancedReveal();
    }
})();
