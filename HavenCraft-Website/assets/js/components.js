/* ============================================================================
 * HavenCraft — Shared Site Shell (Footer, Server DOM Hydration, GA & Effects)
 * ============================================================================ */
(function () {
    'use strict';

    const root = window.getRootPath ? window.getRootPath('assets/js/components.js') : './';

    if ('serviceWorker' in navigator && window.location.protocol !== 'file:') {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register(`${root}sw.js`).catch((err) => {
                console.warn('[PWA] Service Worker registration failed:', err);
            });
        });
    }

    if (!document.querySelector('script[src*="assets/js/animations.js"]')) {
        const animJs = document.createElement('script');
        animJs.src = `${root}assets/js/animations.js`;
        animJs.defer = true;
        document.body.appendChild(animJs);
    }

    const gaId = window.HavenCraftCore?.gaMeasurementId || 'G-3ES6YSB8Y4';
    if (!document.querySelector(`script[src*="www.googletagmanager.com/gtag/js?id=${gaId}"]`)) {
        const gaScript = document.createElement('script');
        gaScript.async = true;
        gaScript.src = `https://www.googletagmanager.com/gtag/js?id=${gaId}`;
        document.head.appendChild(gaScript);
    }

    if (!window.gtag) {
        window.dataLayer = window.dataLayer || [];
        window.gtag = function () { window.dataLayer.push(arguments); };
        window.gtag('js', new Date());
        window.gtag('config', gaId);
    }

    const cfg = window.SERVER_CONFIG || {
        ip: 'mc.havencraft.pro',
        port: 2566,
        version: '1.21+'
    };

    const FOOTER_HTML = `
    <footer class="site-footer" id="site-footer">
        <div class="container">
            <div class="footer-grid">
                <div class="footer-brand">
                    <a href="${root}" class="footer-logo">
                        <img src="${root}logo/icon.svg" alt="" aria-hidden="true" width="28" height="28" />
                        <span>HavenCraft</span>
                    </a>
                    <p class="footer-tagline">24/7 Bangladesh survival server for Java &amp; Bedrock.</p>
                    <div class="footer-brand-bar">
                        <button type="button" class="footer-ip-chip" onclick="if(typeof copyIP==='function') copyIP(this)" title="Copy server IP" aria-label="Copy server IP">
                            <span class="server-ip">${cfg.ip}</span>
                            <i class="fa-regular fa-copy" aria-hidden="true"></i>
                        </button>
                        <div class="footer-socials">
                            <a href="https://dc.havencraft.pro" target="_blank" rel="noopener noreferrer" class="social-icon" aria-label="Discord">
                                <i class="fa-brands fa-discord" aria-hidden="true"></i>
                            </a>
                            <a href="https://www.facebook.com/hvnbd" target="_blank" rel="noopener noreferrer" class="social-icon" aria-label="Facebook">
                                <i class="fa-brands fa-facebook" aria-hidden="true"></i>
                            </a>
                            <a href="https://www.youtube.com/@hvn-bd" target="_blank" rel="noopener noreferrer" class="social-icon" aria-label="YouTube">
                                <i class="fa-brands fa-youtube" aria-hidden="true"></i>
                            </a>
                            <a href="https://www.instagram.com/havencraft.bd" target="_blank" rel="noopener noreferrer" class="social-icon" aria-label="Instagram">
                                <i class="fa-brands fa-instagram" aria-hidden="true"></i>
                            </a>
                            <a href="mailto:support@havencraft.pro" class="social-icon" aria-label="Email Support" title="support@havencraft.pro">
                                <i class="fa-solid fa-envelope" aria-hidden="true"></i>
                            </a>
                        </div>
                    </div>
                </div>
                <div class="footer-col">
                    <div class="footer-col-heading">Explore</div>
                    <ul class="footer-links">
                        <li><a href="${root}features/">Features</a></li>
                        <li><a href="${root}leaderboard/">Leaderboard</a></li>
                        <li><a href="${root}vote/">Vote</a></li>
                        <li><a href="${root}store/">Store</a></li>
                    </ul>
                </div>
                <div class="footer-col">
                    <div class="footer-col-heading">Community</div>
                    <ul class="footer-links">
                        <li><a href="${root}support/">Support Desk</a></li>
                        <li><a href="${root}rules/">Server Rules</a></li>
                        <li><a href="${root}staff/">Staff Team</a></li>
                        <li><a href="${root}gallery/">Gallery</a></li>
                    </ul>
                </div>
            </div>

            <div class="footer-bottom">
                <div class="footer-bottom-left">
                    <p class="footer-copyright">&copy; ${new Date().getFullYear()} HavenCraft</p>
                    <span class="footer-sep" aria-hidden="true">·</span>
                    <nav class="footer-legal" aria-label="Legal and info">
                        <a href="${root}about/">About</a>
                        <span class="footer-sep" aria-hidden="true">·</span>
                        <a href="${root}contact/">Contact</a>
                        <span class="footer-sep" aria-hidden="true">·</span>
                        <a href="${root}privacy/">Privacy</a>
                        <span class="footer-sep" aria-hidden="true">·</span>
                        <a href="${root}terms/">Terms</a>
                    </nav>
                </div>
                <p class="footer-dev">
                    Built by <a href="https://www.facebook.com/juel18371/" target="_blank" rel="noopener noreferrer">JU3L</a>
                </p>
            </div>
        </div>
    </footer>`;

    function applyServerConfigToDOM() {
        const currentCfg = window.SERVER_CONFIG || cfg;

        document.querySelectorAll('.server-port, [data-server-port]').forEach(el => {
            el.textContent = currentCfg.port;
        });
        document.querySelectorAll('.server-ip, [data-server-ip]').forEach(el => {
            el.textContent = currentCfg.ip;
        });
        document.querySelectorAll('.server-address-full, [data-server-full]').forEach(el => {
            el.textContent = `${currentCfg.ip}:${currentCfg.port}`;
        });
        document.querySelectorAll('.bedrock-connect-link, [data-bedrock-link]').forEach(el => {
            el.href = `minecraft://?addExternalServer=HavenCraft%20SMP|${currentCfg.ip}:${currentCfg.bedrockPort || 2025}`;
        });

        const bPort = currentCfg.bedrockPort || 2025;
        document.querySelectorAll('.bedrock-port, [data-bedrock-port]').forEach(el => {
            el.textContent = bPort;
        });

        if (currentCfg.discordUrl) {
            document.querySelectorAll('a[href*="dc.havencraft.pro"], .discord-invite-btn, [data-discord-link]').forEach(el => {
                el.href = currentCfg.discordUrl;
            });
            document.querySelectorAll('#invite-url-text, .discord-url-text').forEach(el => {
                el.textContent = currentCfg.discordUrl;
            });
        }

        if (currentCfg.seasonName) {
            document.querySelectorAll('.season-name, [data-season-name]').forEach(el => {
                el.textContent = currentCfg.seasonName;
            });
        }
        if (currentCfg.seasonBadge) {
            document.querySelectorAll('.season-badge, [data-season-badge]').forEach(el => {
                el.textContent = currentCfg.seasonBadge;
            });
        }
    }

    window.applyServerConfigToDOM = applyServerConfigToDOM;

    const placeholder = document.getElementById('footer-placeholder');
    if (placeholder) {
        placeholder.outerHTML = FOOTER_HTML;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applyServerConfigToDOM);
    } else {
        applyServerConfigToDOM();
    }

    document.addEventListener('keydown', (e) => {
        if ((e.key === 'Enter' || e.key === ' ') && e.target?.classList?.contains('server-ip-box')) {
            e.preventDefault();
            if (typeof window.copyIP === 'function') window.copyIP(e.target);
        }
    });
})();
