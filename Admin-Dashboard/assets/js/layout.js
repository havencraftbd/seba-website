/**
 * HavenCraft SMP — Unified Admin Layout Manager
 * Centrally manages Top Navbar (Header), Main Sidebar, and Footer.
 * Fully compatible with BOTH web servers (Vercel clean URLs) AND local file:// opening.
 * Strict RBAC: Filters sidebar menus & notifications based on staff permissions.
 * Zero Emojis — Clean Bootstrap Icons
 */

(function () {
  'use strict';

  const isFile = window.location.protocol === 'file:';
  const pathname = (window.location.pathname || '').replace(/\\/g, '/').toLowerCase();

  // Detect if current page is in a subfolder
  const isSubfolder = pathname.includes('/server') || pathname.includes('/tickets') ||
    pathname.includes('/orders') || pathname.includes('/settings') ||
    pathname.includes('/shop') || pathname.includes('/gallery') ||
    pathname.includes('/logs') || pathname.includes('/profile') ||
    pathname.includes('/users') || pathname.includes('/login') ||
    pathname.includes('/finance');

  const basePrefix = isSubfolder ? '../' : './';

  // Ensure HVN_CONFIG fallback dynamically
  if (!window.HVN_CONFIG) {
    window.HVN_CONFIG = {
      serverIp: 'mc.havencraft.pro',
      port: '2566',
      javaPort: '2566',
      bedrockPort: '2025',
      discordUrl: 'https://dc.havencraft.pro',
      seasonName: 'Season 6',
      websiteUrl: 'https://havencraft.pro',
      getServerTarget: function () {
        const p = this.port || this.javaPort || '2566';
        return (p && String(p) !== '25565') ? `${this.serverIp}:${p}` : this.serverIp;
      }
    };
  }

  // Navigation Links Configuration
  const NAV_ITEMS = [
    { title: 'Dashboard', serverPath: '/', filePath: `${basePrefix}index.html`, icon: 'bi-speedometer2', key: 'dashboard', perm: null },
    { title: 'Server Control', serverPath: '/server', filePath: `${basePrefix}server/index.html`, icon: 'bi-hdd-network', key: 'server', perm: 'console' },
    { title: 'Users', serverPath: '/users', filePath: `${basePrefix}users/index.html`, icon: 'bi-people', key: 'users', perm: 'users' },
    { title: 'Tickets', serverPath: '/tickets', filePath: `${basePrefix}tickets/index.html`, icon: 'bi-ticket-perforated', key: 'tickets', perm: 'tickets' },
    { title: 'Finance Ledger', serverPath: '/finance', filePath: `${basePrefix}finance/index.html`, icon: 'bi-wallet2', key: 'finance', perm: 'finance' },
    { title: 'Orders', serverPath: '/orders', filePath: `${basePrefix}orders/index.html`, icon: 'bi-bag-check', key: 'orders', perm: 'orders' },
    { title: 'Site Settings', serverPath: '/settings', filePath: `${basePrefix}settings/index.html`, icon: 'bi-sliders', key: 'settings', perm: 'settings' },
    { title: 'Shop Items', serverPath: '/shop', filePath: `${basePrefix}shop/index.html`, icon: 'bi-shop', key: 'shop', perm: 'shop' },
    { title: 'Gallery', serverPath: '/gallery', filePath: `${basePrefix}gallery/index.html`, icon: 'bi-images', key: 'gallery', perm: 'gallery' },
    { title: 'Audit Logs', serverPath: '/logs', filePath: `${basePrefix}logs/index.html`, icon: 'bi-journal-text', key: 'logs', perm: 'logs' },
    { title: 'My Profile', serverPath: '/profile', filePath: `${basePrefix}profile/index.html`, icon: 'bi-person-circle', key: 'profile', perm: null }
  ];

  // SECURITY: Only return the Supabase-verified in-memory profile for RBAC decisions
  function getActiveStaffProfile() {
    if (window.havenStaffProfile && window.havenStaffProfile._verified === true) {
      return window.havenStaffProfile;
    }
    return null;
  }

  function isItemActive(item) {
    if (item.key === 'dashboard') {
      return (pathname.endsWith('/index.html') && !isSubfolder) ||
        pathname === '/' ||
        pathname.endsWith('admin-website/') ||
        pathname.endsWith('admin-website');
    }
    return pathname.includes(`/${item.key}`) || pathname.includes(item.key);
  }

  function renderSidebarNavItems() {
    const staff = getActiveStaffProfile();
    const isOwner = staff ? (staff.isOwner || (staff.email && (typeof window.hvnIsOwnerEmail === 'function' ? window.hvnIsOwnerEmail(staff.email) : staff.email.toLowerCase() === 'rajuel55@gmail.com'))) : false;
    const perms = (staff && staff.permissions) || {};

    let navHtml = '';
    NAV_ITEMS.forEach(item => {
      // RBAC Permission Check for Sidebar Menu
      if (item.perm && !isOwner) {
        if (item.key === 'finance' || item.perm === 'finance') {
          if (!perms.finance && !perms.orders) {
            return;
          }
        } else if (item.key === 'orders' || item.perm === 'orders') {
          if (!perms.orders && !perms.finance) {
            return;
          }
        } else if (!perms[item.perm]) {
          return; // Skip rendering menu item if user lacks required permission
        }
      }

      const href = isFile ? item.filePath : item.serverPath;
      const activeClass = isItemActive(item) ? 'active' : '';

      navHtml += `
        <li class="nav-item">
          <a href="${href}" class="nav-link ${activeClass}" title="${item.title}">
            <i class="bi ${item.icon}"></i>
            <span class="nav-text">${item.title}</span>
          </a>
        </li>
      `;
    });
    return navHtml;
  }

  function renderSidebar() {
    const homeHref = isFile ? `${basePrefix}index.html` : '/';
    const profileHref = isFile ? `${basePrefix}profile/index.html` : '/profile';

    let initAvatar = 'https://mc-heads.net/avatar/MHF_Steve/64';
    let initName = 'Admin';
    let initRole = 'Administrator';
    try {
      const cached = JSON.parse(localStorage.getItem('hvn_staff_profile') || '{}');
      if (cached.avatarUrl) {
        initAvatar = cached.avatarUrl;
      } else if (cached.ign || cached.minecraftIgn) {
        const clean = (cached.ign || cached.minecraftIgn).replace(/^\.+/, '');
        initAvatar = `https://mc-heads.net/avatar/${encodeURIComponent(clean)}/64`;
      }
      if (cached.name || cached.fullName) initName = cached.name || cached.fullName;
      if (cached.role) initRole = cached.role;
    } catch (_) {}

    return `
      <aside class="main-sidebar" id="mainSidebar">
        <div class="sidebar-brand-wrapper">
          <a href="${homeHref}" class="brand-link" title="HavenCraft Admin">
            <div class="brand-logo-icon">
              <img src="${basePrefix}logo/logo.svg" alt="HavenCraft" width="26" height="26" style="display: block; object-fit: contain;">
            </div>
            <div class="brand-text">Haven<span>Craft</span></div>
          </a>
        </div>

        <div class="sidebar-content">
          <div class="sidebar-heading">Navigation</div>
          <ul class="sidebar-nav" id="sidebarNavList">
            ${renderSidebarNavItems()}
          </ul>
        </div>

        <div class="sidebar-footer">
          <a href="${profileHref}" class="staff-info-card" title="View & Edit Profile" style="text-decoration: none;">
            <img id="staffUserAvatar" src="${initAvatar}" alt="Staff Avatar" class="staff-avatar" onerror="if(!this.dataset.fb){this.dataset.fb='1';this.src='https://minotar.net/helm/Steve/128.png';}">
            <div class="staff-meta">
              <div id="staffUserName" class="staff-name">${initName}</div>
              <div id="staffUserRole" class="staff-role">
                <i class="bi bi-patch-check-fill"></i> <span id="staffRoleText">${initRole}</span>
              </div>
            </div>
            <i class="bi bi-chevron-right chevron-icon" style="color: var(--text-muted); font-size: 0.8rem; margin-left: auto;"></i>
          </a>
        </div>
      </aside>
      <div class="sidebar-backdrop" id="sidebarBackdrop"></div>
    `;
  }

  function renderHeader() {
    const ordersHref = isFile ? `${basePrefix}orders/index.html` : '/orders';
    const ticketsHref = isFile ? `${basePrefix}tickets/index.html` : '/tickets';
    const usersHref = isFile ? `${basePrefix}users/index.html` : '/users';

    return `
      <header class="main-header">
        <div class="header-left">
          <button class="btn-sidebar-toggle" id="sidebarToggle" type="button" aria-label="Toggle Navigation" title="Toggle Sidebar">
            <svg class="hvn-toggle-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <rect width="18" height="18" x="3" y="3" rx="3" ry="3"></rect>
              <line x1="9" x2="9" y1="3" y2="21"></line>
              <path class="hvn-toggle-chevron" d="m14 9 3 3-3 3"></path>
            </svg>
          </button>
          <div class="server-quick-status" title="${window.HVN_CONFIG?.getServerTarget ? window.HVN_CONFIG.getServerTarget() : (window.HVN_CONFIG?.serverIp || 'mc.havencraft.pro')}">
            <div class="pulse-dot"></div>
            <span id="serverPingText">Server online</span>
          </div>
        </div>

        <div class="header-right">
          <div class="header-actions">
            <!-- Command Palette Trigger -->
            <button class="header-btn" id="hvnPaletteBtn" title="Search & Commands (Ctrl+K)" aria-label="Open command palette">
              <i class="bi bi-search"></i>
            </button>

            <!-- Notifications Bell (opens drawer) -->
            <button class="notif-bell-btn" id="notifBellBtn" title="Staff Notifications" aria-label="Notifications">
              <i class="bi bi-bell"></i>
              <span class="notif-badge-pill" id="notifBadge" style="display: none;">0</span>
            </button>

            <!-- Theme Studio -->
            <div style="position: relative;">
              <button class="header-btn" id="hvnThemeBtn" title="Theme Studio" aria-label="Theme settings">
                <i class="bi bi-palette"></i>
              </button>
              <div class="hvn-theme-menu" id="hvnThemeMenu">
                <div class="hvn-theme-menu-label">Appearance</div>
                <div class="hvn-theme-seg" id="hvnThemeSeg">
                  <button type="button" data-mode="dark">Dark</button>
                  <button type="button" data-mode="light">Light</button>
                </div>
                <div class="hvn-theme-menu-label">Accent color</div>
                <div class="hvn-accent-row" id="hvnAccentRow">
                  <button type="button" class="hvn-accent-swatch" data-accent="emerald" style="background:#00c896;" title="Emerald"><i class="bi bi-check2"></i></button>
                  <button type="button" class="hvn-accent-swatch" data-accent="cyan" style="background:#38bdf8;" title="Cyan"><i class="bi bi-check2"></i></button>
                  <button type="button" class="hvn-accent-swatch" data-accent="violet" style="background:#a855f7;" title="Violet"><i class="bi bi-check2"></i></button>
                  <button type="button" class="hvn-accent-swatch" data-accent="amber" style="background:#f59e0b;" title="Amber"><i class="bi bi-check2"></i></button>
                </div>
                <div class="hvn-theme-menu-label">Tables</div>
                <div class="hvn-theme-switch-row">
                  <span>Compact density</span>
                  <input type="checkbox" id="hvnDensityToggle" style="accent-color: var(--primary); cursor: pointer;">
                </div>
              </div>
            </div>

            <!-- Live Website Link -->
            <a href="https://havencraft.pro" target="_blank" rel="noopener" class="header-btn" title="View Public Website" aria-label="View Public Website">
              <i class="bi bi-box-arrow-up-right"></i>
            </a>

            <!-- Logout Button -->
            <button id="headerBtnLogout" class="header-btn btn-logout" title="Sign Out" aria-label="Sign Out">
              <i class="bi bi-box-arrow-right"></i>
            </button>
          </div>
        </div>
      </header>
    `;
  }

  function renderFooter() {
    return `
      <footer class="main-footer">
        <div>&copy; 2026 HavenCraft Network</div>
        <div class="footer-meta"><span class="footer-dot"></span>v2.0.0</div>
      </footer>
    `;
  }

  function initLayout() {
    const layoutContainer = document.getElementById('app-layout');
    if (!layoutContainer) return;

    const pageContent = layoutContainer.innerHTML;

    layoutContainer.innerHTML = `
      <div class="app-wrapper">
        ${renderSidebar()}
        <div class="main-wrapper">
          ${renderHeader()}
          <main class="content-wrapper">
            ${pageContent}
          </main>
          ${renderFooter()}
        </div>
      </div>
    `;

    // Sidebar Toggle
    const toggleBtn = document.getElementById('sidebarToggle');
    const backdrop = document.getElementById('sidebarBackdrop');

    function toggleSidebar(e) {
      if (e && e.preventDefault) e.preventDefault();
      if (window.innerWidth <= 992) {
        document.body.classList.toggle('sidebar-open');
      } else {
        document.body.classList.toggle('sidebar-pinned');
      }
    }

    function closeMobileSidebar() {
      document.body.classList.remove('sidebar-open');
    }

    window.toggleSidebar = toggleSidebar;
    window.closeMobileSidebar = closeMobileSidebar;

    if (toggleBtn) toggleBtn.addEventListener('click', toggleSidebar);
    if (backdrop) backdrop.addEventListener('click', closeMobileSidebar);

    const closeBtn = document.getElementById('sidebarCloseBtn');
    if (closeBtn) closeBtn.addEventListener('click', closeMobileSidebar);

    document.querySelectorAll('.main-sidebar .nav-link, .main-sidebar .staff-info-card').forEach(link => {
      link.addEventListener('click', function () {
        if (window.innerWidth <= 992) closeMobileSidebar();
      });
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') closeMobileSidebar();
    });



    // Logout
    const logoutBtn = document.getElementById('headerBtnLogout');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', async function () {
        if (typeof window.hvnAuth !== 'undefined' && window.hvnAuth.logout) {
          await window.hvnAuth.logout();
        } else {
          const loginTarget = isFile ? `${basePrefix}login/index.html` : '/login';
          window.location.replace(loginTarget);
        }
      });
    }

    // ===== Notification Drawer, Theme Studio, Command Palette =====
    // (all optional enhancements — wrapped so a failure here can never break
    //  the core layout: sidebar RBAC re-render, logout, server status)

    // IMPORTANT: shared notification state must live in initLayout scope, NOT
    // inside the try block below — `let` is block-scoped, and the loader
    // (defined outside this block) must read/write the SAME array.
    let notifItemsCache = [];

    try {

    // Drawer footer links reuse the same hrefs renderHeader computes
    const ordersHref = isFile ? `${basePrefix}orders/index.html` : '/orders';
    const usersHref = isFile ? `${basePrefix}users/index.html` : '/users';
    const ticketsHref = isFile ? `${basePrefix}tickets/index.html` : '/tickets';

    const notifBtn = document.getElementById('notifBellBtn');
    const notifDrawer = document.createElement('div');
    notifDrawer.className = 'hvn-notif-drawer';
    notifDrawer.id = 'hvnNotifDrawer';
    notifDrawer.innerHTML = `
      <div class="hvn-notif-drawer-head">
        <h3><i class="bi bi-bell" style="color: var(--primary);"></i> Notifications</h3>
        <div style="display: flex; gap: 6px; align-items: center;">
          <button type="button" class="btn-notif-mark-read" onclick="window.markAllNotifsRead()" title="Mark all as read"
            style="background: var(--primary-dim); border: 1px solid var(--border-primary); color: var(--primary); font-size: 0.72rem; font-weight: 600; padding: 5px 10px; border-radius: 8px; cursor: pointer;">
            <i class="bi bi-check2-all"></i> Mark all
          </button>
          <button type="button" id="hvnNotifDrawerClose" title="Close"
            style="background: transparent; border: none; color: var(--text-muted); cursor: pointer; padding: 4px; display: flex; align-items: center;">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>
      <div class="hvn-notif-tabs" id="hvnNotifTabs">
        <button type="button" class="hvn-notif-tab active" data-tab="all">All</button>
        <button type="button" class="hvn-notif-tab" data-tab="order">Orders</button>
        <button type="button" class="hvn-notif-tab" data-tab="ticket">Tickets</button>
        <button type="button" class="hvn-notif-tab" data-tab="staff">Staff</button>
      </div>
      <ul class="hvn-notif-drawer-list" id="hvnNotifDrawerList">
        <li style="padding: 24px 16px; text-align: center; color: var(--text-muted); font-size: 0.82rem;">
          <i class="bi bi-arrow-repeat spin" style="font-size: 1.2rem; display: block; margin-bottom: 6px;"></i>
          Checking notifications...
        </li>
      </ul>
      <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 16px; border-top: 1px solid var(--border);">
        <a href="${ordersHref}" id="notifFooterOrders" style="color: var(--primary); text-decoration: none; font-weight: 600; font-size: 0.78rem; display: none;">
          <i class="bi bi-bag-check"></i> Orders
        </a>
        <a href="${usersHref}" id="notifFooterUsers" style="color: var(--primary); text-decoration: none; font-weight: 600; font-size: 0.78rem; display: none;">
          <i class="bi bi-people"></i> Users
        </a>
        <a href="${ticketsHref}" id="notifFooterTickets" style="color: var(--primary); text-decoration: none; font-weight: 600; font-size: 0.78rem; display: none;">
          <i class="bi bi-ticket-perforated"></i> Tickets &rarr;
        </a>
      </div>
    `;
    const notifDrawerBackdrop = document.createElement('div');
    notifDrawerBackdrop.className = 'hvn-drawer-backdrop';
    notifDrawerBackdrop.id = 'hvnNotifDrawerBackdrop';
    document.body.appendChild(notifDrawerBackdrop);
    document.body.appendChild(notifDrawer);

    function closeNotifDrawer() {
      notifDrawer.classList.remove('show');
      notifDrawerBackdrop.classList.remove('show');
    }

    if (notifBtn) {
      notifBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        notifDrawer.classList.add('show');
        notifDrawerBackdrop.classList.add('show');
        if (typeof window.hvnRenderNotifDrawer === 'function') {
          window.hvnRenderNotifDrawer();
        }
        loadRealLiveNotifications();
      });
    }
    notifDrawerBackdrop.addEventListener('click', closeNotifDrawer);
    document.getElementById('hvnNotifDrawerClose').addEventListener('click', closeNotifDrawer);

    document.getElementById('hvnNotifTabs').addEventListener('click', function (e) {
      const btn = e.target.closest('.hvn-notif-tab');
      if (!btn) return;
      document.querySelectorAll('#hvnNotifTabs .hvn-notif-tab').forEach(t => t.classList.remove('active'));
      btn.classList.add('active');
      if (window.hvnRenderNotifDrawer) window.hvnRenderNotifDrawer();
    });

    window.hvnRenderNotifDrawer = function () {
      const listEl = document.getElementById('hvnNotifDrawerList');
      if (!listEl) return;
      const activeTabEl = document.querySelector('#hvnNotifTabs .hvn-notif-tab.active');
      const activeTab = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'all';

      let readKeys = [];
      try {
        readKeys = JSON.parse(localStorage.getItem('hvn_notifs_read_keys') || '[]');
        if (!Array.isArray(readKeys)) readKeys = [];
      } catch (e) { readKeys = []; }

      let dismissedKeys = [];
      try {
        dismissedKeys = JSON.parse(localStorage.getItem('hvn_notifs_dismissed_keys') || '[]');
        if (!Array.isArray(dismissedKeys)) dismissedKeys = [];
      } catch (e) { dismissedKeys = []; }

      // Unread count controls ONLY the navbar bell badge
      const unreadItems = notifItemsCache.filter(it => !readKeys.includes(it.key) && !dismissedKeys.includes(it.key));
      const unreadAll = unreadItems.length;
      const badge = document.getElementById('notifBadge');
      if (badge) {
        if (unreadAll > 0) {
          badge.textContent = unreadAll > 9 ? '9+' : unreadAll;
          badge.style.display = 'flex';
        } else {
          badge.style.display = 'none';
        }
      }

      // Visible items: Active notifications matching the selected tab, excluding manually dismissed items
      const visible = notifItemsCache.filter(it =>
        (activeTab === 'all' || it.type === activeTab) && !dismissedKeys.includes(it.key)
      );

      if (visible.length === 0) {
        listEl.innerHTML = `
          <li style="padding: 36px 20px; text-align: center; color: var(--text-muted); font-size: 0.85rem; background: var(--bg-dark); border-radius: 12px; margin: 8px;">
            <div style="width: 52px; height: 52px; border-radius: 50%; background: var(--primary-dim); color: var(--primary); display: inline-flex; align-items: center; justify-content: center; font-size: 1.8rem; margin-bottom: 12px; border: 1px solid var(--border-primary);">
              <i class="bi bi-check2"></i>
            </div>
            <div style="font-weight: 700; color: var(--text-main); font-size: 1.05rem; margin-bottom: 4px;">All Caught Up!</div>
            <div style="font-size: 0.78rem; color: var(--text-muted); max-width: 250px; margin: 0 auto; line-height: 1.45;">No pending notifications matching your permissions.</div>
          </li>
        `;
        return;
      }

      listEl.innerHTML = visible.map(it => {
        const isRead = readKeys.includes(it.key);
        const unreadDot = !isRead
          ? `<span style="width: 7px; height: 7px; border-radius: 50%; background: var(--primary); display: inline-block; margin-right: 6px; box-shadow: 0 0 6px var(--primary); flex-shrink: 0;"></span>`
          : '';
        const itemOpacity = isRead ? '0.78' : '1';

        return `
          <li style="opacity: ${itemOpacity}; transition: opacity 0.2s;">
            <a href="${it.href}" class="notif-item">
              ${it.iconHtml}
              <div style="flex: 1; min-width: 0;">
                <div class="notif-item-title" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: ${isRead ? '500' : '700'}; display: flex; align-items: center;">
                  ${unreadDot}${it.title}
                </div>
                <div class="notif-item-sub" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${it.sub}</div>
              </div>
              ${it.badgeHtml}
              <button type="button" class="hvn-notif-item-close" data-notif-key="${it.key}" title="Dismiss from list"><i class="bi bi-x-lg" style="font-size: 0.72rem;"></i></button>
            </a>
          </li>
        `;
      }).join('');

      listEl.querySelectorAll('.hvn-notif-item-close').forEach(btn => {
        btn.addEventListener('click', function (e) {
          e.preventDefault();
          e.stopPropagation();
          const key = btn.getAttribute('data-notif-key');
          try {
            const existingDismissed = JSON.parse(localStorage.getItem('hvn_notifs_dismissed_keys') || '[]');
            const mergedDismissed = Array.from(new Set([...(Array.isArray(existingDismissed) ? existingDismissed : []), key])).slice(-200);
            localStorage.setItem('hvn_notifs_dismissed_keys', JSON.stringify(mergedDismissed));

            const existingRead = JSON.parse(localStorage.getItem('hvn_notifs_read_keys') || '[]');
            const mergedRead = Array.from(new Set([...(Array.isArray(existingRead) ? existingRead : []), key])).slice(-200);
            localStorage.setItem('hvn_notifs_read_keys', JSON.stringify(mergedRead));
          } catch (err) { }
          window.hvnRenderNotifDrawer();
        });
      });
    };

    // ===== Theme Studio dropdown =====
    const themeBtn = document.getElementById('hvnThemeBtn');
    const themeMenu = document.getElementById('hvnThemeMenu');

    function closeThemeMenu() {
      if (themeMenu) themeMenu.classList.remove('show');
    }

    if (themeBtn && themeMenu) {
      themeBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        themeMenu.classList.toggle('show');
      });
      document.addEventListener('click', function (e) {
        if (!themeMenu.contains(e.target) && e.target !== themeBtn) closeThemeMenu();
      });

      function applyTheme(mode, accent, density) {
        if (typeof window.hvnApplyTheme === 'function') {
          window.hvnApplyTheme(mode, accent, density, true);
        } else {
          if (mode === 'light') document.documentElement.setAttribute('data-theme', 'light');
          else document.documentElement.removeAttribute('data-theme');
          if (accent && accent !== 'emerald') document.documentElement.setAttribute('data-accent', accent);
          else document.documentElement.removeAttribute('data-accent');
          if (density === 'compact') document.documentElement.setAttribute('data-density', 'compact');
          else document.documentElement.removeAttribute('data-density');
          try {
            localStorage.setItem('hvn_theme_mode', mode || 'dark');
            localStorage.setItem('hvn_theme_accent', accent || 'emerald');
            localStorage.setItem('hvn_theme_density', density || 'comfortable');
          } catch (e) { }
          window.dispatchEvent(new CustomEvent('hvnThemeChanged'));
        }
        syncThemeMenuUI(mode || 'dark', accent || 'emerald', density || 'comfortable');
      }

      function syncThemeMenuUI(mode, accent, density) {
        document.querySelectorAll('#hvnThemeSeg button').forEach(b =>
          b.classList.toggle('active', b.getAttribute('data-mode') === mode));
        document.querySelectorAll('#hvnAccentRow .hvn-accent-swatch').forEach(b =>
          b.classList.toggle('active', b.getAttribute('data-accent') === accent));
        const dens = document.getElementById('hvnDensityToggle');
        if (dens) dens.checked = (density === 'compact');
      }

      window.addEventListener('hvnThemeChanged', function (e) {
        const t = e.detail || (typeof window.hvnGetTheme === 'function' ? window.hvnGetTheme() : {
          mode: localStorage.getItem('hvn_theme_mode') || 'dark',
          accent: localStorage.getItem('hvn_theme_accent') || 'emerald',
          density: localStorage.getItem('hvn_theme_density') || 'comfortable'
        });
        syncThemeMenuUI(t.mode || 'dark', t.accent || 'emerald', t.density || 'comfortable');
      });

      document.getElementById('hvnThemeSeg').addEventListener('click', function (e) {
        const btn = e.target.closest('button[data-mode]');
        if (!btn) return;
        applyTheme(btn.getAttribute('data-mode'), localStorage.getItem('hvn_theme_accent') || 'emerald', localStorage.getItem('hvn_theme_density') || 'comfortable');
      });

      document.getElementById('hvnAccentRow').addEventListener('click', function (e) {
        const btn = e.target.closest('.hvn-accent-swatch');
        if (!btn) return;
        applyTheme(localStorage.getItem('hvn_theme_mode') || 'dark', btn.getAttribute('data-accent'), localStorage.getItem('hvn_theme_density') || 'comfortable');
      });

      const densToggle = document.getElementById('hvnDensityToggle');
      if (densToggle) {
        densToggle.addEventListener('change', function () {
          applyTheme(localStorage.getItem('hvn_theme_mode') || 'dark', localStorage.getItem('hvn_theme_accent') || 'emerald', densToggle.checked ? 'compact' : 'comfortable');
        });
      }

      syncThemeMenuUI(
        localStorage.getItem('hvn_theme_mode') || 'dark',
        localStorage.getItem('hvn_theme_accent') || 'emerald',
        localStorage.getItem('hvn_theme_density') || 'comfortable'
      );
    }

    // ===== Command Palette (Ctrl+K) =====
    const cmdkBackdrop = document.createElement('div');
    cmdkBackdrop.className = 'hvn-cmdk-backdrop';
    cmdkBackdrop.id = 'hvnCmdkBackdrop';
    cmdkBackdrop.innerHTML = `
      <div class="hvn-cmdk" role="dialog" aria-modal="true" aria-label="Command palette">
        <input type="text" class="hvn-cmdk-input" id="hvnCmdkInput" placeholder="Type a page or command..." autocomplete="off" spellcheck="false">
        <ul class="hvn-cmdk-list" id="hvnCmdkList"></ul>
        <div class="hvn-cmdk-footer">
          <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
          <span><kbd>Enter</kbd> select</span>
          <span><kbd>Esc</kbd> close</span>
        </div>
      </div>
    `;
    document.body.appendChild(cmdkBackdrop);
    const cmdkInput = document.getElementById('hvnCmdkInput');
    const cmdkList = document.getElementById('hvnCmdkList');
    let cmdkSelected = 0;
    let cmdkResults = [];

    function closeCmdk() {
      cmdkBackdrop.classList.remove('show');
      cmdkInput.value = '';
    }

    function openCmdk() {
      cmdkBackdrop.classList.add('show');
      cmdkInput.value = '';
      renderCmdk('');
      setTimeout(function () { cmdkInput.focus(); }, 30);
    }

    function getVisibleNavItems() {
      const staff = getActiveStaffProfile();
      const isOwner = staff ? (staff.isOwner || (staff.email && (typeof window.hvnIsOwnerEmail === 'function' ? window.hvnIsOwnerEmail(staff.email) : staff.email.toLowerCase() === 'rajuel55@gmail.com'))) : false;
      const perms = (staff && staff.permissions) || {};
      return NAV_ITEMS.filter(item => {
        if (!item.perm || isOwner) return true;
        if (item.key === 'finance' || item.key === 'orders') return Boolean(perms.finance || perms.orders);
        return Boolean(perms[item.perm]);
      }).map(item => ({
        title: item.title,
        icon: item.icon,
        href: isFile ? item.filePath : item.serverPath,
        keywords: item.key
      }));
    }

    function renderCmdk(query) {
      const q = (query || '').toLowerCase().trim();
      const navItems = getVisibleNavItems().map(n => ({
        title: n.title,
        icon: n.icon,
        keywords: n.keywords,
        hint: 'Page',
        run: function () { window.location.href = n.href; }
      }));

      const actions = [
        {
          title: 'Toggle Light / Dark Mode', icon: 'bi-circle-half', keywords: 'theme light dark mode', hint: 'Action', run: function () {
            const currentMode = document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
            const nextMode = currentMode === 'light' ? 'dark' : 'light';
            const currentAccent = localStorage.getItem('hvn_theme_accent') || 'emerald';
            const currentDensity = localStorage.getItem('hvn_theme_density') || 'comfortable';
            if (typeof window.hvnApplyTheme === 'function') {
              window.hvnApplyTheme(nextMode, currentAccent, currentDensity, true);
            } else {
              applyTheme(nextMode, currentAccent, currentDensity);
            }
          }
        },
        { title: 'Open Notifications', icon: 'bi-bell', keywords: 'notifs alerts drawer', hint: 'Action', run: function () { if (notifBtn) notifBtn.click(); } },
        { title: 'Copy Server IP', icon: 'bi-clipboard', keywords: 'copy ip server address', hint: 'Action', run: function () { const target = window.HVN_CONFIG && window.HVN_CONFIG.getServerTarget ? window.HVN_CONFIG.getServerTarget() : 'mc.havencraft.pro'; if (window.hvnCopyText) window.hvnCopyText(target, 'Server IP copied'); } },
        { title: 'Refresh Dashboard Data', icon: 'bi-arrow-clockwise', keywords: 'reload refresh data', hint: 'Action', run: function () { if (typeof window.loadDashboardData === 'function') window.loadDashboardData(); else window.location.reload(); } },
        { title: 'View Public Website', icon: 'bi-box-arrow-up-right', keywords: 'site havencraft pro', hint: 'Link', run: function () { window.open('https://havencraft.pro', '_blank'); } },
        { title: 'Sign Out', icon: 'bi-box-arrow-right', keywords: 'logout signout exit', hint: 'Action', run: function () { const lb = document.getElementById('headerBtnLogout'); if (lb) lb.click(); } }
      ];

      const all = navItems.concat(actions).filter(x =>
        !q || x.title.toLowerCase().includes(q) || (x.keywords || '').toLowerCase().includes(q));

      cmdkResults = all;
      cmdkSelected = 0;
      if (all.length === 0) {
        cmdkList.innerHTML = '<li class="hvn-cmdk-empty">No matching pages or commands</li>';
        return;
      }
      cmdkList.innerHTML = all.map((x, i) => `
        <li class="hvn-cmdk-item ${i === 0 ? 'selected' : ''}" data-cmdk-index="${i}">
          <i class="bi ${x.icon}"></i>
          <span>${x.title}</span>
          <kbd>${x.hint}</kbd>
        </li>
      `).join('');
      cmdkList.querySelectorAll('.hvn-cmdk-item').forEach(li => {
        li.addEventListener('click', function () {
          const idx = parseInt(li.getAttribute('data-cmdk-index'), 10);
          closeCmdk();
          if (cmdkResults[idx]) cmdkResults[idx].run();
        });
      });
    }

    const paletteBtn = document.getElementById('hvnPaletteBtn');
    if (paletteBtn) paletteBtn.addEventListener('click', openCmdk);
    cmdkBackdrop.addEventListener('click', function (e) {
      if (e.target === cmdkBackdrop) closeCmdk();
    });
    cmdkInput.addEventListener('input', function () { renderCmdk(cmdkInput.value); });
    document.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        if (cmdkBackdrop.classList.contains('show')) closeCmdk();
        else openCmdk();
        return;
      }
      if (!cmdkBackdrop.classList.contains('show')) return;
      if (e.key === 'Escape') { closeCmdk(); return; }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const items = cmdkList.querySelectorAll('.hvn-cmdk-item');
        if (!items.length) return;
        items[cmdkSelected].classList.remove('selected');
        cmdkSelected = e.key === 'ArrowDown'
          ? Math.min(items.length - 1, cmdkSelected + 1)
          : Math.max(0, cmdkSelected - 1);
        items[cmdkSelected].classList.add('selected');
        items[cmdkSelected].scrollIntoView({ block: 'nearest' });
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        const chosen = cmdkResults[cmdkSelected];
        if (chosen) { closeCmdk(); chosen.run(); }
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        closeNotifDrawer();
        closeThemeMenu();
      }
    });

    } catch (enhanceErr) {
      // Drawer/theme/palette are enhancements only — core layout must keep working
      console.warn('Layout enhancements failed to initialize:', enhanceErr);
    }

    function escapeHtmlNotif(str) {
      if (window.hvnEscapeHtml) return window.hvnEscapeHtml(str);
      return String(str == null ? '' : str)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    let currentActiveNotifKeys = [];

    // Strict Permission-Filtered Notifications Loader
    async function loadRealLiveNotifications() {
      const badge = document.getElementById('notifBadge');
      if (!document.getElementById('hvnNotifDrawerList')) return;

      const client = (window.initHavenSupabase && window.initHavenSupabase()) || window.supabaseClient;
      if (!client) {
        setTimeout(loadRealLiveNotifications, 600);
        return;
      }

      let staff = getActiveStaffProfile();
      if (!staff && window.hvnAuth && typeof window.hvnAuth.waitForProfile === 'function') {
        try {
          staff = await window.hvnAuth.waitForProfile();
        } catch (e) {
          staff = null;
        }
      }
      if (!staff) {
        staff = getActiveStaffProfile();
      }
      if (!staff) {
        // If unauthenticated or still loading, clear spinner by rendering empty state
        if (typeof window.hvnRenderNotifDrawer === 'function') {
          window.hvnRenderNotifDrawer();
        }
        return;
      }

      const isOwner = Boolean(
        staff.isOwner ||
        (staff.email && (typeof window.hvnIsOwnerEmail === 'function'
          ? window.hvnIsOwnerEmail(staff.email)
          : String(staff.email).toLowerCase() === 'rajuel55@gmail.com'))
      );
      const perms = staff.permissions || {};

      // Toggle notification footer links strictly by verified permissions
      const fOrders = document.getElementById('notifFooterOrders');
      const fUsers = document.getElementById('notifFooterUsers');
      const fTickets = document.getElementById('notifFooterTickets');
      if (fOrders) fOrders.style.display = (isOwner || perms.orders || perms.finance) ? 'inline-flex' : 'none';
      if (fUsers) fUsers.style.display = (isOwner || perms.users) ? 'inline-flex' : 'none';
      if (fTickets) fTickets.style.display = (isOwner || perms.tickets) ? 'inline-flex' : 'none';

      const ordersTarget = isFile ? `${basePrefix}orders/index.html` : '/orders';
      const ticketsTarget = isFile ? `${basePrefix}tickets/index.html` : '/tickets';
      const usersTarget = isFile ? `${basePrefix}users/index.html` : '/users';

      const queries = [];

      // One-time cleanup for v3.7 to clear any stuck read or dismissal caches
      try {
        if (localStorage.getItem('hvn_notifs_reset_v37') !== '1') {
          localStorage.removeItem('hvn_notifs_read_keys');
          localStorage.removeItem('hvn_notifs_dismissed_keys');
          localStorage.setItem('hvn_notifs_reset_v37', '1');
        }
      } catch (_) { }

      // Query store orders ONLY if staff has orders or finance permission
      if (isOwner || perms.orders || perms.finance) {
        queries.push(
          client.from('store_orders')
            .select('*')
            .order('created_at', { ascending: false })
            .limit(15)
        );
      } else {
        queries.push(Promise.resolve({ data: [] }));
      }

      // Query tickets ONLY if staff has tickets permission
      if (isOwner || perms.tickets) {
        queries.push(
          client.from('tickets')
            .select('*')
            .order('created_at', { ascending: false })
            .limit(15)
        );
      } else {
        queries.push(Promise.resolve({ data: [] }));
      }

      // Query staff applications ONLY if Owner or users permission
      if (isOwner || perms.users) {
        queries.push(
          client.from('staff_profiles')
            .select('*')
            .order('created_at', { ascending: false })
            .limit(15)
        );
      } else {
        queries.push(Promise.resolve({ data: [] }));
      }

      try {
        const results = await Promise.allSettled(queries);
        const ordersRes = results[0] && results[0].status === 'fulfilled' ? results[0].value : null;
        const ticketsRes = results[1] && results[1].status === 'fulfilled' ? results[1].value : null;
        const staffRes = results[2] && results[2].status === 'fulfilled' ? results[2].value : null;

        if (ordersRes && ordersRes.error) {
          console.warn('[HavenNotifs] store_orders query notice:', ordersRes.error);
        }
        if (ticketsRes && ticketsRes.error) {
          console.warn('[HavenNotifs] tickets query notice:', ticketsRes.error);
        }
        if (staffRes && staffRes.error) {
          console.warn('[HavenNotifs] staff_profiles query notice:', staffRes.error);
        }

        const rawOrders = (ordersRes && Array.isArray(ordersRes.data)) ? ordersRes.data : [];
        const rawTickets = (ticketsRes && Array.isArray(ticketsRes.data)) ? ticketsRes.data : [];
        const rawStaff = (staffRes && Array.isArray(staffRes.data)) ? staffRes.data : [];

        // Bulletproof in-memory filter matching orders/index.html logic
        const pendingOrders = rawOrders.filter(o => String(o.status || '').trim().toLowerCase() === 'pending').slice(0, 8);
        const openTickets = rawTickets.filter(t => String(t.status || '').trim().toLowerCase() === 'open').slice(0, 8);
        const pendingStaff = rawStaff.filter(s => {
          const st = String(s.status || '').trim().toLowerCase();
          const r = String(s.role || '').trim().toLowerCase();
          if (st === 'rejected' || st === 'suspended') return false;
          return st === 'pending' || r.includes('pending');
        }).slice(0, 8);

        // Build unique ID keys for each active notification item so new notifications are never suppressed
        currentActiveNotifKeys = [
          ...pendingStaff.map(s => `staff:${s.id}`),
          ...pendingOrders.map(o => `order:${o.id}`),
          ...openTickets.map(t => `ticket:${t.id}`)
        ];

        // Clean up dismissed keys that no longer exist among active notifications
        try {
          const prevDismissed = JSON.parse(localStorage.getItem('hvn_notifs_dismissed_keys') || '[]');
          if (Array.isArray(prevDismissed)) {
            const cleanedDismissed = prevDismissed.filter(k => currentActiveNotifKeys.includes(k));
            localStorage.setItem('hvn_notifs_dismissed_keys', JSON.stringify(cleanedDismissed));
          }
        } catch (_) { }

        notifItemsCache = [];

        pendingStaff.forEach(s => {
          notifItemsCache.push({
            type: 'staff',
            key: `staff:${s.id}`,
            href: usersTarget,
            iconHtml: `<div class="notif-item-icon" style="background: rgba(88, 101, 242, 0.15); color: #5865F2;"><i class="bi bi-discord"></i></div>`,
            title: `Staff Application: ${escapeHtmlNotif(s.full_name || s.discord || 'Applicant')}`,
            sub: `${escapeHtmlNotif(s.discord || 'Discord')} • Awaiting Review`,
            badgeHtml: `<span class="badge" style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; font-size: 0.68rem; padding: 2px 6px; flex-shrink: 0;">Review</span>`
          });
        });

        pendingOrders.forEach(o => {
          const rawPrice = String(o.price || '').trim();
          const cleanPrice = rawPrice.replace(/^[৳$Tk\s]+/i, '');
          const displayPrice = cleanPrice ? `৳${cleanPrice}` : (rawPrice ? `৳${rawPrice}` : '');
          notifItemsCache.push({
            type: 'order',
            key: `order:${o.id}`,
            href: ordersTarget,
            iconHtml: `<div class="notif-item-icon" style="background: rgba(245, 158, 11, 0.15); color: #f59e0b;"><i class="bi bi-bag-check-fill"></i></div>`,
            title: `Pending Order: ${escapeHtmlNotif(o.item_name || 'Store Item')}`,
            sub: `${escapeHtmlNotif(o.username || 'Player')} • ${escapeHtmlNotif(o.payment_method || 'bKash')} ${escapeHtmlNotif(displayPrice)}`,
            badgeHtml: `<span class="badge" style="background: rgba(245, 158, 11, 0.12); color: #f59e0b; font-size: 0.68rem; padding: 2px 6px; flex-shrink: 0;">Deliver</span>`
          });
        });

        openTickets.forEach(t => {
          notifItemsCache.push({
            type: 'ticket',
            key: `ticket:${t.id}`,
            href: ticketsTarget,
            iconHtml: `<div class="notif-item-icon" style="background: rgba(14, 165, 233, 0.15); color: #0ea5e9;"><i class="bi bi-ticket-perforated-fill"></i></div>`,
            title: `Support Ticket ${escapeHtmlNotif(t.ticket_code || '#' + t.id)}`,
            sub: `${escapeHtmlNotif(t.user_name || 'Player')}: ${escapeHtmlNotif(t.subject || 'Support Request')}`,
            badgeHtml: `<span class="badge" style="background: rgba(14, 165, 233, 0.12); color: #0ea5e9; font-size: 0.68rem; padding: 2px 6px; flex-shrink: 0;">Reply</span>`
          });
        });
      } catch (err) {
        console.warn('Failed to load notifications from database:', err);
      } finally {
        if (typeof window.hvnRenderNotifDrawer === 'function') {
          window.hvnRenderNotifDrawer();
        }
      }
    }

    window.markAllNotifsRead = function () {
      try {
        const existing = JSON.parse(localStorage.getItem('hvn_notifs_read_keys') || '[]');
        const merged = Array.from(new Set([...(Array.isArray(existing) ? existing : []), ...currentActiveNotifKeys])).slice(-200);
        localStorage.setItem('hvn_notifs_read_keys', JSON.stringify(merged));
      } catch (e) { }
      if (window.hvnRenderNotifDrawer) window.hvnRenderNotifDrawer();
    };

    loadRealLiveNotifications();

    const debouncedLoadNotifs = (typeof window.hvnDebounce === 'function')
      ? window.hvnDebounce(loadRealLiveNotifications, 450)
      : loadRealLiveNotifications;

    // Subscribe to realtime updates for store_orders, tickets, and staff_profiles with authenticated session
    let notifRealtimeSub = null;
    async function initNotificationsRealtime() {
      const client = (window.initHavenSupabase && window.initHavenSupabase()) || window.supabaseClient;
      if (!client) return;

      try {
        if (client.auth) {
          const { data: { session } } = await client.auth.getSession();
          if (session && session.access_token && client.realtime && typeof client.realtime.setAuth === 'function') {
            await client.realtime.setAuth(session.access_token);
          }
        }
      } catch (authErr) { }

      if (notifRealtimeSub) {
        try {
          client.removeChannel(notifRealtimeSub);
        } catch (e) { }
        notifRealtimeSub = null;
      }

      try {
        notifRealtimeSub = client.channel('admin-layout-navbar-notifs')
          .on('postgres_changes', { event: '*', schema: 'public', table: 'store_orders' }, () => {
            debouncedLoadNotifs();
          })
          .on('postgres_changes', { event: '*', schema: 'public', table: 'tickets' }, () => {
            debouncedLoadNotifs();
          })
          .on('postgres_changes', { event: '*', schema: 'public', table: 'staff_profiles' }, () => {
            debouncedLoadNotifs();
          })
          .subscribe();
      } catch (subErr) {
        console.warn('[HavenNotifs] Realtime subscription notice:', subErr);
      }
    }
    initNotificationsRealtime();

    setInterval(() => {
      if (!document.hidden) loadRealLiveNotifications();
    }, 15000);

    // Live server status pill (with player count & 1-click IP copy)
    const statusPillWrap = document.querySelector('.server-quick-status');
    if (statusPillWrap) {
      statusPillWrap.style.cursor = 'pointer';
      statusPillWrap.addEventListener('click', () => {
        const target = window.HVN_CONFIG?.getServerTarget ? window.HVN_CONFIG.getServerTarget() : (window.HVN_CONFIG?.serverIp || 'mc.havencraft.pro');
        if (window.hvnCopyText) window.hvnCopyText(target, 'Server IP copied');
      });
    }

    async function refreshHeaderServerStatus() {
      if (document.hidden) return;
      const wrap = document.querySelector('.server-quick-status');
      const pingEl = document.getElementById('serverPingText');
      if (!wrap || !pingEl) return;
      const target = window.HVN_CONFIG?.getServerTarget ? window.HVN_CONFIG.getServerTarget() : (window.HVN_CONFIG?.serverIp || 'mc.havencraft.pro');
      try {
        const res = await fetch('https://api.mcsrvstat.us/3/' + encodeURIComponent(target));
        const data = await res.json();
        const online = !!(data && data.online);
        const playersOnline = (data && data.players && typeof data.players.online === 'number') ? data.players.online : null;
        const playersMax = (data && data.players && typeof data.players.max === 'number') ? data.players.max : null;
        if (online && playersOnline !== null) {
          pingEl.textContent = `Server Online • ${playersOnline}${playersMax ? '/' + playersMax : ''}`;
        } else {
          pingEl.textContent = online ? 'Server Online' : 'Server Offline';
        }
        wrap.classList.toggle('is-offline', !online);
        wrap.title = `${target} — ${online ? 'Online' : 'Offline'} (Click to copy IP)`;
      } catch (err) { }
    }
    refreshHeaderServerStatus();
    setInterval(refreshHeaderServerStatus, 60000);

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        debouncedLoadNotifs();
        refreshHeaderServerStatus();
      }
    });

    // Re-render sidebar and refresh notifications & realtime when staff profile finishes loading
    window.addEventListener('hvnStaffProfileLoaded', function () {
      const navList = document.getElementById('sidebarNavList');
      if (navList) {
        navList.innerHTML = renderSidebarNavItems();
      }
      loadRealLiveNotifications();
      initNotificationsRealtime();
    });

    // Handle case where profile was already loaded before this listener bound
    if (window.havenStaffProfile && window.havenStaffProfile._verified) {
      const navList = document.getElementById('sidebarNavList');
      if (navList) {
        navList.innerHTML = renderSidebarNavItems();
      }
      loadRealLiveNotifications();
      initNotificationsRealtime();
    }

    if (typeof window.applyConfigToDom === 'function') {
      window.applyConfigToDom();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initLayout);
  } else {
    initLayout();
  }
})();
