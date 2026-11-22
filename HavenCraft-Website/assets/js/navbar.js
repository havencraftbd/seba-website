/* ============================================
   HavenCraft — Shared Navbar Component
   ============================================ */

(function () {
    'use strict';

    const root = window.getRootPath ? window.getRootPath('assets/js/navbar.js') : './';

    const NAVBAR_HTML = `
    <a href="#main-content" class="skip-link">Skip to main content</a>
    <div class="nav-overlay" id="navOverlay"></div>
    <nav class="navbar" id="navbar">
        <div class="container">
            <div class="nav-brand-wrapper">
                <a href="${root}" class="nav-brand nav-brand-dynamic" aria-label="HavenCraft SMP Home">
                    <img src="${root}logo/icon.svg" alt="" aria-hidden="true" class="brand-logo" width="26" height="26" />
                    <span class="brand-text">Haven<span class="text-gradient">Craft</span></span>
                </a>
            </div>

            <div class="nav-links" id="navLinks">
                <a href="${root}" data-nav="home"><i class="fa-solid fa-house"></i> <span>Home</span></a>
                <a href="${root}vote/" data-nav="vote"><i class="fa-solid fa-check-to-slot"></i> <span>Vote</span></a>
                <a href="${root}leaderboard/" data-nav="leaderboard"><i class="fa-solid fa-trophy"></i> <span>Leaderboard</span></a>
                <a href="${root}store/" data-nav="store"><i class="fa-solid fa-bag-shopping"></i> <span>Store</span></a>
                
                <div class="nav-more-wrapper">
                    <button class="nav-more-btn" aria-haspopup="true" aria-expanded="false"><i class="fa-solid fa-compass"></i> <span>More</span> <i class="fa-solid fa-chevron-down more-chevron"></i></button>
                    <div class="nav-more-dropdown">
                        <a href="${root}features/"><i class="fa-solid fa-star"></i> <span>Features</span></a>
                        <a href="${root}commands/"><i class="fa-solid fa-terminal"></i> <span>Commands</span></a>
                        <a href="${root}gallery/"><i class="fa-solid fa-image"></i> <span>Gallery</span></a>
                        <a href="${root}staff/"><i class="fa-solid fa-users"></i> <span>Staff</span></a>
                        <a href="${root}rules/"><i class="fa-solid fa-book"></i> <span>Rules</span></a>
                        <a href="${root}about/"><i class="fa-solid fa-circle-info"></i> <span>About</span></a>
                        <a href="${root}contact/"><i class="fa-solid fa-envelope"></i> <span>Contact</span></a>
                    </div>
                </div>

                <!-- Custom Mobile Flat Navigation (Visible only on mobile/tablet) -->
                <div class="nav-mobile-explore">
                    <div class="nav-mobile-divider"></div>
                    <div class="nav-mobile-title">Explore</div>
                    <div class="nav-mobile-grid">
                        <a href="${root}features/"><i class="fa-solid fa-star"></i> <span>Features</span></a>
                        <a href="${root}commands/"><i class="fa-solid fa-terminal"></i> <span>Commands</span></a>
                        <a href="${root}gallery/"><i class="fa-solid fa-image"></i> <span>Gallery</span></a>
                        <a href="${root}staff/"><i class="fa-solid fa-users"></i> <span>Staff</span></a>
                        <a href="${root}rules/"><i class="fa-solid fa-book"></i> <span>Rules</span></a>
                        <a href="${root}about/"><i class="fa-solid fa-circle-info"></i> <span>About</span></a>
                        <a href="${root}contact/"><i class="fa-solid fa-envelope"></i> <span>Contact</span></a>
                    </div>
                </div>
            </div>

            <div class="nav-actions" id="navActions">
                <div class="nav-search-wrapper" id="navSearchWrapper">
                    <button type="button" class="nav-action-btn" id="searchToggle" aria-label="Search players" aria-haspopup="true" aria-expanded="false" aria-controls="navSearchBox">
                        <i class="fa-solid fa-magnifying-glass"></i>
                    </button>

                    <div class="nav-search-box" id="navSearchBox" role="search" aria-label="Player Search">
                        <div class="nav-search-input-bar">
                            <i class="fa-solid fa-magnifying-glass nav-search-icon" aria-hidden="true"></i>
                            <input type="text" id="playerSearchInput" class="nav-search-input" placeholder="Enter Minecraft username..." autocomplete="off" />
                            <button type="button" class="nav-search-clear" id="searchClear" aria-label="Clear search" title="Clear" style="display:none;"><i class="fa-solid fa-delete-left"></i></button>
                            <button type="button" class="nav-search-close" id="searchClose" aria-label="Close search" title="Close"><i class="fa-solid fa-xmark"></i></button>
                        </div>
                        <div class="nav-search-dropdown" id="navSearchDropdown">
                            <div class="search-results" id="searchResults">
                                <div class="search-empty">
                                    <i class="fa-solid fa-user-group"></i>
                                    <p>Type a username to search across the server</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- User / Gametag Tracking -->
                <div class="user-menu-wrapper" id="userMenuWrapper">
                    <button class="nav-action-btn user-btn" id="userBtn" aria-label="Select player" aria-haspopup="true" aria-expanded="false" aria-controls="userDropdown">
                        <img src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=" alt="" class="user-head" id="userHead" style="display:none;" width="24" height="24" />
                        <svg class="user-icon-svg" id="userIconDefault" width="20" height="20" viewBox="0 0 24 24"
                            fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                            stroke-linejoin="round">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                            <circle cx="12" cy="7" r="4"></circle>
                        </svg>
                    </button>
                    <div class="user-dropdown" id="userDropdown">
                        <div class="user-dropdown-header">Active Gametag</div>
                        <div class="user-dropdown-body">
                            <p class="user-dropdown-hint">Enter your username to track your stats &amp; badges across the site.</p>
                            <input type="text" id="gametagInput" class="gametag-input" placeholder="Minecraft username..." maxlength="24" />
                            <button class="btn btn-primary btn-sm gametag-submit" id="gametagSubmit">Set Gametag</button>
                        </div>
                        <div class="user-dropdown-logged" id="userLoggedIn" style="display:none;">
                            <div class="user-dropdown-profile">
                                <img src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=" alt="" class="user-dropdown-avatar" id="userDropdownAvatar" width="40" height="40" />
                                <div class="user-dropdown-meta">
                                    <span class="user-dropdown-name" id="userDropdownName"></span>
                                    <span class="user-dropdown-sub">Synced Gametag</span>
                                </div>
                            </div>
                            <div class="user-dropdown-actions-row">
                                <a href="${root}profile/" class="btn btn-primary btn-sm"><i class="fa-solid fa-user"></i> My Stats</a>
                                <button type="button" class="btn btn-outline btn-sm" id="userLogout" title="Switch player">Switch</button>
                            </div>
                        </div>
                    </div>
                </div>

                <button class="hamburger" id="hamburger" aria-label="Toggle menu" aria-expanded="false" aria-controls="navLinks">
                    <span></span>
                    <span></span>
                    <span></span>
                </button>
            </div>
        </div>
    </nav>
    `;

    function injectNavbar() {
        const currentPath = window.location.pathname;
        const normalizedCurrent = currentPath.replace(/\/index\.html$/, '/');
        const homeHref = new URL(root, window.location.href).pathname.replace(/\/index\.html$/, '/');
        let isCurrentPageHome = (
            normalizedCurrent === homeHref ||
            normalizedCurrent === '/' ||
            normalizedCurrent === ''
        );

        const placeholder = document.getElementById('navbar-placeholder');
        if (placeholder) {
            const initialClasses = `navbar navbar-init${isCurrentPageHome ? ' is-home-nav' : ''}`;
            placeholder.outerHTML = NAVBAR_HTML.replace('class="navbar" id="navbar"', `class="${initialClasses}" id="navbar"`);
        }

        const navbar = document.getElementById('navbar');

        document.querySelectorAll('.nav-links > a').forEach(link => {
            const linkPath = new URL(link.href, window.location.origin).pathname;
            const navKey = link.getAttribute('data-nav') || '';

            if (navKey === 'home') {
                const normalizedLink = linkPath.replace(/\/index\.html$/, '/');
                if (normalizedCurrent === normalizedLink || currentPath === '/' || currentPath === '') {
                    link.classList.add('active');
                    isCurrentPageHome = true;
                }
            } else if (linkPath !== '/' && (currentPath === linkPath || currentPath === linkPath + 'index.html' || (linkPath.length > 1 && currentPath.startsWith(linkPath)))) {
                link.classList.add('active');
            }
        });

        if (navbar && isCurrentPageHome) {
            navbar.classList.add('is-home-nav');
        }

        document.querySelectorAll('.nav-more-dropdown a').forEach(dropLink => {
            const dropPath = new URL(dropLink.href, window.location.origin).pathname;
            if (dropPath !== '/' && (currentPath === dropPath || currentPath === dropPath + 'index.html' || (dropPath.length > 1 && currentPath.startsWith(dropPath)))) {
                dropLink.classList.add('active');
                const navMoreBtn = document.querySelector('.nav-more-btn');
                if (navMoreBtn) navMoreBtn.classList.add('active');
            }
        });

        bindEvents();

        if (window.HavenCraftCore && window.HavenCraftCore.getCurrentUser()) {
            applyLoginUI(window.HavenCraftCore.getCurrentUser());
        }
    }

    function bindEvents() {
        // Inline Top Search Setup
        const navActions = document.getElementById('navActions');
        const navSearchWrapper = document.getElementById('navSearchWrapper');
        const searchToggle = document.getElementById('searchToggle');
        const navSearchBox = document.getElementById('navSearchBox');
        const searchClose = document.getElementById('searchClose');
        const searchInput = document.getElementById('playerSearchInput');
        const searchClear = document.getElementById('searchClear');
        const searchResults = document.getElementById('searchResults');
        const defaultSearchEmptyHtml = `<div class="search-empty"><i class="fa-solid fa-user-group"></i><p>Type a username to search across the server</p></div>`;
        let searchTimeout = null;

        function isSearchOpen() {
            return !!(navActions && navActions.classList.contains('search-open'));
        }

        function openSearch(e) {
            if (e) e.stopPropagation();
            if (!navActions || !navSearchBox) return;

            const userDropdownEl = document.getElementById('userDropdown');
            const userBtnEl = document.getElementById('userBtn');
            if (userDropdownEl) userDropdownEl.classList.remove('open');
            if (userBtnEl) userBtnEl.setAttribute('aria-expanded', 'false');

            const navMoreWrapperEl = document.querySelector('.nav-more-wrapper');
            const navMoreBtnEl = document.querySelector('.nav-more-btn');
            if (navMoreWrapperEl) navMoreWrapperEl.classList.remove('open');
            if (navMoreBtnEl) navMoreBtnEl.setAttribute('aria-expanded', 'false');

            navActions.classList.add('search-open');
            if (searchToggle) searchToggle.setAttribute('aria-expanded', 'true');
            window.requestAnimationFrame(() => searchInput && searchInput.focus());
        }

        function closeSearch(restoreFocus = true) {
            if (!isSearchOpen()) return;
            navActions.classList.remove('search-open');
            if (searchToggle) searchToggle.setAttribute('aria-expanded', 'false');
            clearTimeout(searchTimeout);
            if (searchInput) searchInput.value = '';
            if (searchClear) searchClear.style.display = 'none';
            if (searchResults) searchResults.innerHTML = defaultSearchEmptyHtml;
            if (restoreFocus && searchToggle) searchToggle.focus();
        }

        if (searchToggle) searchToggle.addEventListener('click', openSearch);
        if (searchClose) {
            searchClose.addEventListener('click', (e) => {
                e.stopPropagation();
                closeSearch(true);
            });
        }
        if (navSearchWrapper) {
            navSearchWrapper.addEventListener('click', (e) => e.stopPropagation());
        }

        document.addEventListener('click', (e) => {
            if (isSearchOpen() && navSearchWrapper && !navSearchWrapper.contains(e.target)) {
                closeSearch(false);
            }
        });

        if (searchInput) {
            searchInput.addEventListener('input', () => {
                const val = searchInput.value.trim();
                if (searchClear) searchClear.style.display = val ? 'inline-flex' : 'none';
                clearTimeout(searchTimeout);

                if (!val) {
                    if (searchResults) searchResults.innerHTML = defaultSearchEmptyHtml;
                    return;
                }

                if (searchResults) {
                    searchResults.innerHTML = `<div class="search-loading"><i class="fa-solid fa-spinner fa-spin"></i><p>Searching...</p></div>`;
                }

                searchTimeout = setTimeout(() => searchPlayers(val), 400);
            });

            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeSearch(true);
                }
            });
        }

        if (navSearchBox) {
            navSearchBox.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeSearch(true);
                }
            });
        }

        if (searchClear) {
            searchClear.addEventListener('click', () => {
                if (searchInput) searchInput.value = '';
                searchClear.style.display = 'none';
                clearTimeout(searchTimeout);
                if (searchResults) searchResults.innerHTML = defaultSearchEmptyHtml;
                if (searchInput) searchInput.focus();
            });
        }

        const hamburger = document.getElementById('hamburger');
        const navLinks = document.getElementById('navLinks');
        const navOverlay = document.getElementById('navOverlay');
        const navMoreBtn = document.querySelector('.nav-more-btn');
        const navMoreWrapper = document.querySelector('.nav-more-wrapper');

        function openMenu() {
            closeSearch(false);
            if (hamburger) {
                hamburger.classList.add('active');
                hamburger.setAttribute('aria-expanded', 'true');
            }
            if (navLinks) navLinks.classList.add('open');
            if (navOverlay) navOverlay.classList.add('show');
            document.body.style.overflow = 'hidden';
        }

        function closeMenu() {
            if (hamburger) {
                hamburger.classList.remove('active');
                hamburger.setAttribute('aria-expanded', 'false');
            }
            if (navLinks) navLinks.classList.remove('open');
            if (navOverlay) navOverlay.classList.remove('show');
            document.body.style.overflow = '';
        }

        if (hamburger) {
            hamburger.addEventListener('click', (e) => {
                e.stopPropagation();
                if (navLinks && navLinks.classList.contains('open')) {
                    closeMenu();
                } else {
                    openMenu();
                }
            });
        }

        // Auto-close mobile menu on desktop resize to prevent scroll lock
        window.addEventListener('resize', () => {
            if (window.innerWidth > 1024 && navLinks && navLinks.classList.contains('open')) {
                closeMenu();
            }
        }, { passive: true });

        // Close menu when clicking on the overlay
        if (navOverlay) {
            navOverlay.addEventListener('click', closeMenu);
        }

        // Click outside fallback
        document.addEventListener('click', (e) => {
            if (navLinks && navLinks.classList.contains('open')) {
                if (!navLinks.contains(e.target) && (!hamburger || !hamburger.contains(e.target))) {
                    closeMenu();
                }
            }
        });

        if (navLinks) {
            navLinks.querySelectorAll('a').forEach(link => {
                link.addEventListener('click', () => {
                    closeMenu();
                    if (navMoreWrapper) navMoreWrapper.classList.remove('open');
                });
            });
        }

        // More Dropdown Setup
        if (navMoreBtn && navMoreWrapper) {
            navMoreBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const isOpen = navMoreWrapper.classList.toggle('open');
                navMoreBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
            });

            document.addEventListener('click', (e) => {
                if (!navMoreWrapper.contains(e.target)) {
                    navMoreWrapper.classList.remove('open');
                    navMoreBtn.setAttribute('aria-expanded', 'false');
                }
            });
        }

        const navbar = document.getElementById('navbar');
        const navBrandDynamic = navbar ? navbar.querySelector('.nav-brand-dynamic') : null;
        const heroTitle = document.querySelector('.hero-title');
        const isHomeNav = !!(navbar && navbar.classList.contains('is-home-nav'));
        const reducedMotionQuery = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;

        const syncBrandInteractivity = (revealed) => {
            if (!navBrandDynamic || !isHomeNav) return;
            navBrandDynamic.setAttribute('aria-hidden', revealed ? 'false' : 'true');
            if (revealed) {
                navBrandDynamic.removeAttribute('tabindex');
            } else {
                navBrandDynamic.setAttribute('tabindex', '-1');
            }
        };

        if (navBrandDynamic && isHomeNav) {
            syncBrandInteractivity(false);
            navBrandDynamic.addEventListener('click', (e) => {
                if (!isBrandRevealed) {
                    e.preventDefault();
                    e.stopPropagation();
                }
            });
        }

        // Consensys / Lenis 1.3.27 exact constants
        const SCROLL_DELTA = 20;
        const LENIS_LERP = 0.1;
        const LENIS_DAMPING = LENIS_LERP * 60; // 6.0
        const LINE_DELTA_SCALE = 100 / 6;

        // Cached layout metrics (recalculated only on init / resize / font load — never inside scroll)
        let navHeight = 68;
        let topLimit = 0;
        let topThreshold = 100;
        let brandRevealY = 160;
        let maxScrollY = 0;

        let anchorY = Math.max(0, window.scrollY || window.pageYOffset || 0);
        let isScrolled = false;
        let isBrandRevealed = false;
        let isHidden = false;
        let isBooting = true;

        // Inertial wheel scroll state (Lenis 1.3.27 model)
        let targetScroll = anchorY;
        let animatedScroll = anchorY;
        let lastAppliedY = anchorY;
        let isWheelAnimating = false;
        let wheelRafId = null;
        let lastFrameTs = 0;

        const isNavbarPinnedOpen = () => {
            if (!navbar) return true;
            const userDropdownEl = document.getElementById('userDropdown');
            return (
                isSearchOpen() ||
                (navLinks && navLinks.classList.contains('open')) ||
                (navMoreWrapper && navMoreWrapper.classList.contains('open')) ||
                (userDropdownEl && userDropdownEl.classList.contains('open')) ||
                navbar.contains(document.activeElement)
            );
        };

        const measureLayout = () => {
            if (!navbar) return;
            navHeight = navbar.offsetHeight || 68;
            const bannerEl = document.querySelector('.hvn-announcement-banner');
            topLimit = bannerEl ? bannerEl.offsetHeight : 0;
            maxScrollY = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);

            if (heroTitle) {
                const rect = heroTitle.getBoundingClientRect();
                const currentY = Math.max(0, window.scrollY || window.pageYOffset || 0);
                const titleDocTop = rect.top + currentY;
                const titleDocBottom = rect.bottom + currentY;
                topThreshold = Math.max(topLimit + 100, Math.round(titleDocTop - navHeight - 8));
                brandRevealY = Math.max(topThreshold + SCROLL_DELTA, Math.round(titleDocBottom - navHeight + 4));
            } else {
                topThreshold = topLimit + 100;
                brandRevealY = 110;
            }
        };

        const setHiddenState = (hidden) => {
            if (!navbar || isHidden === hidden) return;
            isHidden = hidden;
            navbar.classList.toggle('nav-hidden', hidden);
        };

        // Consensys exact navbar scroll handler (s_.initScrollHandler)
        const handleScrollPosition = (scrollY) => {
            if (!navbar) return;
            const currentY = Math.max(0, scrollY);

            const nextScrolled = currentY > 25;
            if (nextScrolled !== isScrolled) {
                isScrolled = nextScrolled;
                navbar.classList.toggle('scrolled', nextScrolled);
            }

            if (isHomeNav) {
                const nextBrand = currentY >= brandRevealY;
                if (nextBrand !== isBrandRevealed) {
                    isBrandRevealed = nextBrand;
                    navbar.classList.toggle('brand-revealed', nextBrand);
                    syncBrandInteractivity(nextBrand);
                }
            }

            if (isBooting || isNavbarPinnedOpen()) {
                setHiddenState(false);
                anchorY = currentY;
                return;
            }

            if (currentY < topThreshold) {
                setHiddenState(false);
                anchorY = currentY;
            } else if (Math.abs(currentY - anchorY) > SCROLL_DELTA) {
                const scrollingDown = currentY > anchorY;
                setHiddenState(scrollingDown);
                anchorY = currentY;
            }
        };

        const shouldPreventSmoothWheel = (target, deltaY) => {
            if (document.body.style.overflow === 'hidden' || document.documentElement.style.overflow === 'hidden') {
                return true;
            }
            let el = target && target.nodeType === 1 ? target : target && target.parentElement;
            while (el && el !== document.body && el !== document.documentElement) {
                if (el.hasAttribute('data-lenis-prevent') || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') {
                    return true;
                }
                if (el.scrollHeight > el.clientHeight + 1) {
                    const style = window.getComputedStyle(el);
                    const oy = style.overflowY;
                    if (oy === 'auto' || oy === 'scroll' || oy === 'overlay') {
                        const canDown = deltaY > 0 && el.scrollTop + el.clientHeight < el.scrollHeight - 1;
                        const canUp = deltaY < 0 && el.scrollTop > 1;
                        if (canDown || canUp || style.position === 'fixed') return true;
                    }
                }
                el = el.parentElement;
            }
            return false;
        };

        const stepSmoothWheel = (ts) => {
            if (!isWheelAnimating) {
                wheelRafId = null;
                lastFrameTs = 0;
                return;
            }
            const dt = lastFrameTs ? Math.min(0.05, Math.max(0.001, (ts - lastFrameTs) / 1000)) : (1 / 60);
            lastFrameTs = ts;

            // Consensys Lenis 1.3.27 exact exponential lerp: bn(value, to, lerp * 60, dt)
            const alpha = 1 - Math.exp(-LENIS_DAMPING * dt);
            animatedScroll += (targetScroll - animatedScroll) * alpha;

            const done = Math.round(animatedScroll) === Math.round(targetScroll) && Math.abs(targetScroll - animatedScroll) < 0.5;
            if (done) {
                animatedScroll = targetScroll;
                isWheelAnimating = false;
            }

            lastAppliedY = animatedScroll;
            window.scrollTo(0, animatedScroll);
            handleScrollPosition(animatedScroll);

            if (isWheelAnimating) {
                wheelRafId = window.requestAnimationFrame(stepSmoothWheel);
            } else {
                wheelRafId = null;
                lastFrameTs = 0;
            }
        };

        const startSmoothWheel = () => {
            if (!isWheelAnimating) {
                isWheelAnimating = true;
            }
            if (!wheelRafId) {
                lastFrameTs = 0;
                wheelRafId = window.requestAnimationFrame(stepSmoothWheel);
            }
        };

        const syncSmoothScrollMode = () => {
            const reduced = !!(reducedMotionQuery && reducedMotionQuery.matches);
            document.documentElement.classList.toggle('hc-smooth-scroll', !reduced);
        };
        syncSmoothScrollMode();
        if (reducedMotionQuery && reducedMotionQuery.addEventListener) {
            reducedMotionQuery.addEventListener('change', syncSmoothScrollMode);
        }

        window.addEventListener('wheel', (e) => {
            if (reducedMotionQuery && reducedMotionQuery.matches) return;
            if (e.ctrlKey || e.metaKey || e.defaultPrevented) return;
            if (Math.abs(e.deltaY) < Math.abs(e.deltaX) || e.deltaY === 0) return;
            if (shouldPreventSmoothWheel(e.target, e.deltaY)) return;

            let deltaY = e.deltaY;
            if (e.deltaMode === 1) {
                deltaY *= LINE_DELTA_SCALE;
            } else if (e.deltaMode === 2) {
                deltaY *= window.innerHeight;
            }

            e.preventDefault();

            if (!isWheelAnimating) {
                const currentY = Math.max(0, window.scrollY || window.pageYOffset || 0);
                animatedScroll = currentY;
                targetScroll = currentY;
                maxScrollY = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
            }

            targetScroll = Math.max(0, Math.min(maxScrollY, targetScroll + deltaY));
            startSmoothWheel();
        }, { passive: false });

        let nativeScrollTicking = false;
        window.addEventListener('scroll', () => {
            const actualY = Math.max(0, window.scrollY || window.pageYOffset || 0);
            if (isWheelAnimating) {
                if (Math.abs(actualY - lastAppliedY) > 3) {
                    // User dragged scrollbar or used keyboard while wheel inertia was running
                    isWheelAnimating = false;
                    animatedScroll = actualY;
                    targetScroll = actualY;
                } else {
                    return;
                }
            } else {
                animatedScroll = actualY;
                targetScroll = actualY;
            }

            if (!nativeScrollTicking) {
                nativeScrollTicking = true;
                window.requestAnimationFrame(() => {
                    nativeScrollTicking = false;
                    if (!isWheelAnimating) {
                        handleScrollPosition(Math.max(0, window.scrollY || window.pageYOffset || 0));
                    }
                });
            }
        }, { passive: true });

        // Smoothly glide in-page anchor links (#leaderboard, #faq, etc.) via the same Lenis curve
        document.addEventListener('click', (e) => {
            if (reducedMotionQuery && reducedMotionQuery.matches) return;
            const link = e.target && e.target.closest ? e.target.closest('a[href^="#"]') : null;
            if (!link) return;
            const hash = link.getAttribute('href');
            if (!hash || hash === '#') return;
            const targetEl = document.querySelector(hash);
            if (!targetEl) return;

            e.preventDefault();
            measureLayout();
            const currentY = Math.max(0, window.scrollY || window.pageYOffset || 0);
            if (!isWheelAnimating) {
                animatedScroll = currentY;
            }
            const destY = Math.max(0, Math.min(maxScrollY, targetEl.getBoundingClientRect().top + currentY - navHeight));
            targetScroll = destY;
            startSmoothWheel();
            if (history.pushState) {
                history.pushState(null, '', hash);
            }
        });

        let resizeTimer = null;
        window.addEventListener('resize', () => {
            if (resizeTimer) clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                measureLayout();
                handleScrollPosition(Math.max(0, window.scrollY || window.pageYOffset || 0));
            }, 150);
        }, { passive: true });

        if ('ResizeObserver' in window) {
            let roTimer = null;
            const ro = new ResizeObserver(() => {
                if (roTimer) clearTimeout(roTimer);
                roTimer = setTimeout(() => {
                    maxScrollY = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
                }, 250);
            });
            ro.observe(document.documentElement);
        }

        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(() => {
                measureLayout();
                handleScrollPosition(Math.max(0, window.scrollY || window.pageYOffset || 0));
            });
        }

        window.addEventListener('load', () => {
            measureLayout();
            handleScrollPosition(Math.max(0, window.scrollY || window.pageYOffset || 0));
        }, { once: true });

        if (navbar) {
            navbar.addEventListener('focusin', () => {
                setHiddenState(false);
                anchorY = Math.max(0, window.scrollY || window.pageYOffset || 0);
            });
        }

        measureLayout();
        handleScrollPosition(anchorY);

        window.requestAnimationFrame(() => {
            window.requestAnimationFrame(() => {
                if (navbar) navbar.classList.remove('navbar-init');
            });
        });
        setTimeout(() => {
            isBooting = false;
            anchorY = Math.max(0, window.scrollY || window.pageYOffset || 0);
        }, 180);

        // Gametag User Setup
        const userBtn = document.getElementById('userBtn');
        const userDropdown = document.getElementById('userDropdown');
        const userMenuWrapper = document.getElementById('userMenuWrapper');
        const gametagInput = document.getElementById('gametagInput');
        const gametagSubmit = document.getElementById('gametagSubmit');
        const userLogout = document.getElementById('userLogout');

        if (userBtn && userDropdown && userMenuWrapper) {
            userBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                closeSearch(false);
                const isOpen = userDropdown.classList.toggle('open');
                userBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
            });

            document.addEventListener('click', (e) => {
                if (!userMenuWrapper.contains(e.target)) {
                    userDropdown.classList.remove('open');
                    userBtn.setAttribute('aria-expanded', 'false');
                }
            });
        }

        if (gametagSubmit && gametagInput) {
            const doLogin = () => {
                if (window.HavenCraftCore) {
                    const result = window.HavenCraftCore.login(gametagInput.value.trim());
                    if (result.success) {
                        showToast(`Player set to ${gametagInput.value.trim()}`);
                    } else {
                        showToast(result.error);
                    }
                }
            };
            gametagSubmit.addEventListener('click', doLogin);
            gametagInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') doLogin();
            });
        }

        if (userLogout) {
            userLogout.addEventListener('click', () => {
                if (window.HavenCraftCore) {
                    window.HavenCraftCore.logout();
                    showToast('Player selection cleared');
                    if (userDropdown) userDropdown.classList.remove('open');
                }
            });
        }

        // Listen for core state changes
        window.addEventListener('hc_login', (e) => applyLoginUI(e.detail.gametag));
        window.addEventListener('hc_logout', () => applyLogoutUI());
    }

    function applyAvatarFallback(imgEl, username, size) {
        const urls = window.getPlayerAvatarUrls ? window.getPlayerAvatarUrls(username, size) : {
            primary: `https://minotar.net/avatar/${encodeURIComponent(username.replace(/^\.+/, ''))}/${size}.png`,
            fallback: `https://mc-heads.net/avatar/${encodeURIComponent(username.replace(/^\.+/, ''))}/${size}`,
            steve: `https://mc-heads.net/avatar/MHF_Steve/${size}`
        };
        imgEl.src = urls.primary;
        imgEl.onerror = () => {
            if (!imgEl.dataset.retried) {
                imgEl.dataset.retried = '1';
                imgEl.src = urls.fallback;
            } else {
                imgEl.onerror = null;
                imgEl.src = urls.steve;
            }
        };
    }

    function applyLoginUI(gametag) {
        const userHead = document.getElementById('userHead');
        const userIconDefault = document.getElementById('userIconDefault');
        const userDropdownAvatar = document.getElementById('userDropdownAvatar');
        const userDropdownName = document.getElementById('userDropdownName');
        const userLoggedIn = document.getElementById('userLoggedIn');
        const gametagInput = document.getElementById('gametagInput');
        const userDropdown = document.getElementById('userDropdown');

        if (userHead) {
            userHead.alt = gametag;
            applyAvatarFallback(userHead, gametag, 40);
            userHead.style.display = 'block';
        }
        if (userIconDefault) userIconDefault.style.display = 'none';

        if (userDropdownAvatar) {
            applyAvatarFallback(userDropdownAvatar, gametag, 80);
        }
        if (userDropdownName) userDropdownName.textContent = gametag;
        if (userLoggedIn) userLoggedIn.style.display = 'flex';

        if (gametagInput && gametagInput.parentElement) {
            gametagInput.parentElement.style.display = 'none';
        }

        if (userDropdown) {
            const header = userDropdown.querySelector('.user-dropdown-header');
            if (header) header.textContent = 'Active Gametag';
        }
    }

    function applyLogoutUI() {
        const userHead = document.getElementById('userHead');
        const userIconDefault = document.getElementById('userIconDefault');
        const userLoggedIn = document.getElementById('userLoggedIn');
        const gametagInput = document.getElementById('gametagInput');
        const userDropdown = document.getElementById('userDropdown');

        if (userHead) userHead.style.display = 'none';
        if (userIconDefault) userIconDefault.style.display = 'block';

        if (userLoggedIn) userLoggedIn.style.display = 'none';
        if (gametagInput && gametagInput.parentElement) {
            gametagInput.parentElement.style.display = 'block';
            gametagInput.value = '';
        }

        if (userDropdown) {
            const header = userDropdown.querySelector('.user-dropdown-header');
            if (header) header.textContent = 'Select Gametag';
        }
    }

    // showToast is now provided by utils.js



    // Lazy load Supabase SDK on-demand for search if not preloaded on page
    let _supabaseSearchPromise = null;
    async function ensureSupabaseForSearch() {
        if (window.supabaseLeaderboard) return true;
        if (typeof window.supabase === 'undefined') {
            if (!_supabaseSearchPromise) {
                _supabaseSearchPromise = new Promise((resolve) => {
                    const script = document.createElement('script');
                    script.src = 'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2';
                    script.onload = () => resolve(true);
                    script.onerror = () => {
                        _supabaseSearchPromise = null;
                        resolve(false);
                    };
                    document.head.appendChild(script);
                });
            }
            await _supabaseSearchPromise;
        }
        if (window.HavenCraftCore && window.HavenCraftCore.initSupabase) {
            window.HavenCraftCore.initSupabase();
        }
        return !!window.supabaseLeaderboard;
    }

    async function searchPlayers(query) {
        const searchResults = document.getElementById('searchResults');
        if (!searchResults) return;

        const rawQuery = String(query || '').trim();
        if (!rawQuery) {
            searchResults.innerHTML = `<div class="search-empty"><i class="fa-solid fa-user-group"></i><p>Type a username to search across the server</p></div>`;
            return;
        }

        const cleanQuery = window.escapeSqlWildcards ? window.escapeSqlWildcards(rawQuery) : rawQuery.replace(/[%_\\]/g, '\\$&');

        if (!window.supabaseLeaderboard) {
            const ready = await ensureSupabaseForSearch();
            if (!ready) {
                searchResults.innerHTML = `<div class="search-empty"><i class="fa-solid fa-circle-exclamation"></i><p>Search unavailable. Please try again later.</p></div>`;
                return;
            }
        }

        try {
            const { data, error } = await window.supabaseLeaderboard
                .from('player_stats')
                .select('username, playtime_sec, kills, deaths, money')
                .ilike('username', `%${cleanQuery}%`)
                .order('playtime_sec', { ascending: false })
                .limit(10);

            if (error || !data || data.length === 0) {
                searchResults.innerHTML = `<div class="search-empty"><i class="fa-solid fa-ghost"></i><p>No players found for "<strong>${escapeHtml(query)}</strong>"</p></div>`;
                return;
            }

            let html = `<div class="search-results-header">${data.length} player${data.length > 1 ? 's' : ''} found</div>`;
            html += '<div class="search-results-list">';

            data.forEach(player => {
                const safeName = escapeHtml(player.username);
                const imgHtml = window.renderAvatarImgHtml
                    ? window.renderAvatarImgHtml(player.username, { size: 40, lazy: true })
                    : `<img src="https://minotar.net/avatar/${encodeURIComponent(player.username.replace(/^\.+/, ''))}/40.png" alt="${safeName}" loading="lazy" />`;
                const hours = (player.playtime_sec / 3600).toFixed(1);
                html += `
                <a href="${root}profile/index.html?player=${encodeURIComponent(player.username)}" class="search-result-item">
                    ${imgHtml}
                    <div class="search-result-info">
                        <span class="search-result-name">${safeName}</span>
                        <span class="search-result-stats">${hours}h playtime · ${Number(player.kills).toLocaleString()} kills</span>
                    </div>
                    <i class="fa-solid fa-chevron-right search-result-arrow"></i>
                </a>`;
            });

            html += '</div>';
            searchResults.innerHTML = html;
        } catch (err) {
            console.error('[Search] Error:', err);
            searchResults.innerHTML = `<div class="search-empty"><i class="fa-solid fa-circle-exclamation"></i><p>Something went wrong. Try again.</p></div>`;
        }
    }

    const escapeHtml = window.escapeHtml || function (text) {
        if (!text) return '';
        const d = document.createElement('div');
        d.textContent = text;
        return d.innerHTML;
    };

    injectNavbar();

})();
