/* ============================================
   HavenCraft — Player Profile Tracker
   ============================================ */

(function () {
    'use strict';

    let activeRequestId = 0;
    let lastRenderedProfile = null;

    const escapeHtml = (text) => (typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || ''));
    const escapeSql = (text) => (typeof window.escapeSqlWildcards === 'function'
        ? window.escapeSqlWildcards(text)
        : String(text || '').replace(/[%_\\]/g, '\\$&'));

    function fmtCompact(val, maximumFractionDigits = 1) {
        return Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits }).format(Number(val) || 0);
    }

    const DOM = {
        guest: document.getElementById('profileGuest'),
        loading: document.getElementById('profileLoading'),
        dashboard: document.getElementById('profileDashboard'),
        skinContainer: document.getElementById('profileSkinContainer'),
        name: document.getElementById('profileName'),
        playtime: document.getElementById('statPlaytime'),
        kills: document.getElementById('statKills'),
        deaths: document.getElementById('statDeaths'),
        money: document.getElementById('statMoney'),
        shards: document.getElementById('statShards'),
        statBlockPlace: document.getElementById('statBlockPlace'),
        statBlockBreak: document.getElementById('statBlockBreak'),
        statTotalBlocks: document.getElementById('statTotalBlocks'),
        statMobsKilled: document.getElementById('statMobsKilled'),
        statVotes: document.getElementById('statVotes'),
        badges: document.getElementById('profileBadges'),
        statKillRatio: document.getElementById('statKillRatio'),
        statDeathRatio: document.getElementById('statDeathRatio'),
        shareBar: document.getElementById('profileShareBar'),
        shareLink: document.getElementById('profileShareLink'),
        shareCopy: document.getElementById('profileShareCopy'),
        downloadBtn: document.getElementById('profileDownloadBtn'),
        loginInput: document.getElementById('profileLoginInput'),
        loginBtn: document.getElementById('profileLoginBtn'),
        rankTag: document.getElementById('profileRankTag'),
        statsGrid: document.getElementById('statsGrid'),
        statsNotFound: document.getElementById('statsNotFoundMessage')
    };

    const STAFF_ROLE_STYLES = {
        'owner': { cls: 'rank-owner', icon: 'fa-crown' },
        'role-owner': { cls: 'rank-owner', icon: 'fa-crown' },
        'sr. admin': { cls: 'rank-sr-admin', icon: 'fa-star' },
        'senior admin': { cls: 'rank-sr-admin', icon: 'fa-star' },
        'sr admin': { cls: 'rank-sr-admin', icon: 'fa-star' },
        'role-sr-admin': { cls: 'rank-sr-admin', icon: 'fa-star' },
        'admin': { cls: 'rank-admin', icon: 'fa-star' },
        'role-admin': { cls: 'rank-admin', icon: 'fa-star' },
        'moderator': { cls: 'rank-mod', icon: 'fa-shield-halved' },
        'role-mod': { cls: 'rank-mod', icon: 'fa-shield-halved' },
        'officer': { cls: 'rank-officer', icon: 'fa-star-half-stroke' },
        'role-officer': { cls: 'rank-officer', icon: 'fa-star-half-stroke' }
    };

    const KILL_BADGES = [
        { min: 1500, icon: 'fa-dragon', label: 'War Legend' },
        { min: 500, icon: 'fa-skull', label: 'PvP Slayer' },
        { min: 100, icon: 'fa-crosshairs', label: 'Skilled Fighter' },
        { min: 50, icon: 'fa-khanda', label: 'Rookie Fighter' }
    ];

    const PLAYTIME_BADGES = [
        { min: 500, icon: 'fa-bed', label: 'No Lifer' },
        { min: 200, icon: 'fa-fire', label: 'Hardcore' },
        { min: 100, icon: 'fa-star', label: 'Dedicated' },
        { min: 50, icon: 'fa-clock', label: 'Regular Player' }
    ];

    const DEATH_BADGES = [
        { min: 500, icon: 'fa-ghost', label: 'Immortal Spirit' },
        { min: 300, icon: 'fa-shield', label: 'Unbreakable' },
        { min: 100, icon: 'fa-band-aid', label: 'Battle Scarred' },
        { min: 50, icon: 'fa-dice', label: 'Risk Taker' }
    ];

    const VOTE_BADGES = [
        { min: 1000, icon: 'fa-crown', label: 'Vote King' },
        { min: 500, icon: 'fa-trophy', label: 'Super Voter' },
        { min: 200, icon: 'fa-rocket', label: 'Server Booster' },
        { min: 50, icon: 'fa-thumbs-up', label: 'Supporter' }
    ];

    function renderTierBadge(value, cls, tiers) {
        const match = tiers.find(t => value >= t.min);
        return match ? `<span class="profile-badge ${cls}"><i class="fa-solid ${match.icon}"></i> ${match.label}</span>` : '';
    }

    function getStaffMember(username) {
        if (!username) return null;
        if (window.HavenStaff && typeof window.HavenStaff.findMember === 'function') {
            return window.HavenStaff.findMember(username);
        }
        if (!Array.isArray(window.STAFF_MEMBERS)) return null;
        const lower = username.toLowerCase();
        const cleanLower = lower.replace(/^\.+/, '');
        return window.STAFF_MEMBERS.find((member) => {
            const mLower = (member.name || '').toLowerCase();
            return mLower === lower || mLower === cleanLower || mLower === '.' + cleanLower;
        }) || null;
    }

    function resolveSupabasePair() {
        if (!window.supabaseLeaderboard && window.HavenCraftCore?.getLeaderboard) {
            window.supabaseLeaderboard = window.HavenCraftCore.getLeaderboard();
        }
        if (!window.supabaseVote && window.HavenCraftCore?.getVote) {
            window.supabaseVote = window.HavenCraftCore.getVote();
        }
        return !!(window.supabaseLeaderboard && window.supabaseVote);
    }

    function waitForSupabaseClients() {
        if (resolveSupabasePair()) return Promise.resolve(true);

        return new Promise((resolve) => {
            let settled = false;
            const finish = () => {
                if (settled) return;
                settled = true;
                resolve(resolveSupabasePair());
            };

            const sdkScript = document.querySelector('script[src*="supabase-js"]');
            if (sdkScript) {
                sdkScript.addEventListener('load', finish, { once: true });
                sdkScript.addEventListener('error', finish, { once: true });
            }
            if (document.readyState !== 'complete') {
                window.addEventListener('load', finish, { once: true });
            } else {
                queueMicrotask(finish);
            }
        });
    }

    function setupProfileLogin() {
        if (!DOM.loginBtn || !DOM.loginInput) return;

        DOM.loginInput.setAttribute('maxlength', '24');
        const doLogin = () => {
            const val = DOM.loginInput.value.trim();
            if (!val || val.length < 3 || val.length > 24) {
                if (typeof showToast === 'function') showToast('Please enter a valid username (3-24 characters)');
                return;
            }
            if (window.HavenCraftCore) {
                const result = window.HavenCraftCore.login(val);
                if (!result.success && typeof showToast === 'function') {
                    showToast(result.error);
                }
            } else {
                const root = window.getRootPath ? window.getRootPath('assets/js/profile.js') : '../';
                window.location.href = `${root}profile/?player=${encodeURIComponent(val)}`;
            }
        };

        DOM.loginBtn.addEventListener('click', doLogin);
        DOM.loginInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') doLogin();
        });
    }

    async function renderSkin(username) {
        if (!DOM.skinContainer) return;
        DOM.skinContainer.innerHTML = '<div class="profile-skin-skeleton"><i class="fa-solid fa-gamepad"></i></div>';

        const staffMember = getStaffMember(username);
        const skinEntry = staffMember ? { ...staffMember } : { name: username };

        if (skinEntry.skin && skinEntry.skin.includes('mineskin.org/skins/') && window.HavenStaff?.resolveMineskin) {
            skinEntry.skin = await window.HavenStaff.resolveMineskin(skinEntry.skin);
        }

        const cleanSkinTarget = username.replace(/^\.+/, '');
        const skinTextureUrl = window.HavenStaff?.getSkinTextureUrl
            ? window.HavenStaff.getSkinTextureUrl(skinEntry)
            : `https://minotar.net/skin/${encodeURIComponent(cleanSkinTarget)}`;
        const fallbackBodyUrl = window.HavenStaff?.getBodyRenderUrl
            ? window.HavenStaff.getBodyRenderUrl(skinEntry)
            : `https://minotar.net/armor/body/${encodeURIComponent(cleanSkinTarget)}/180.png`;

        const safeAlt = escapeHtml(username) || 'Player avatar';
        const showStaticFallback = () => {
            DOM.skinContainer.innerHTML = `<img src="${fallbackBodyUrl}" style="height:220px; width:auto;" alt="${safeAlt}" />`;
        };

        if (typeof skinview3d === 'undefined') {
            showStaticFallback();
            return;
        }

        try {
            const skinViewer = new skinview3d.SkinViewer({
                canvas: document.createElement('canvas'),
                width: 140,
                height: 220
            });
            skinViewer.canvas.style.animation = 'fadeIn 0.35s ease';
            DOM.skinContainer.innerHTML = '';
            DOM.skinContainer.appendChild(skinViewer.canvas);

            skinViewer.animation = new skinview3d.WalkingAnimation();
            skinViewer.animation.speed = 0.5;
            skinViewer.autoRotate = false;
            skinViewer.zoom = 0.9;

            if (typeof skinViewer.loadSkin === 'function') {
                Promise.resolve(skinViewer.loadSkin(skinTextureUrl))
                    .catch(() => Promise.resolve(skinViewer.loadSkin('https://minotar.net/skin/MHF_Steve')))
                    .catch(showStaticFallback);
            }
        } catch (e) {
            console.warn('Skinview3d error:', e);
            showStaticFallback();
        }
    }

    function bindShareAndExport(username) {
        const shareUrl = `${window.location.origin}/profile/?player=${encodeURIComponent(username)}`;
        if (DOM.shareLink) {
            DOM.shareLink.textContent = shareUrl;
        }
        if (DOM.shareCopy) {
            DOM.shareCopy.onclick = async () => {
                const ok = typeof window.copyText === 'function'
                    ? await window.copyText(shareUrl)
                    : await navigator.clipboard.writeText(shareUrl).then(() => true).catch(() => false);
                if (!ok) {
                    if (typeof showToast === 'function') showToast('Could not copy profile link');
                    return;
                }
                if (typeof showToast === 'function') showToast('✓ Profile link copied!');
                DOM.shareCopy.innerHTML = '<i class="fa-solid fa-check"></i> Copied!';
                setTimeout(() => {
                    DOM.shareCopy.innerHTML = '<i class="fa-solid fa-link"></i> Copy Link';
                }, 2000);
            };
        }

        if (!DOM.downloadBtn) return;
        DOM.downloadBtn.onclick = async () => {
            if (typeof html2canvas === 'undefined') {
                if (typeof showToast === 'function') showToast('Error: Capture library not loaded');
                return;
            }

            const originalContent = DOM.downloadBtn.innerHTML;
            DOM.downloadBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Generating Card...';
            DOM.downloadBtn.disabled = true;

            const target = DOM.dashboard;
            const shareWrapper = document.querySelector('.profile-link-wrapper');
            if (shareWrapper) shareWrapper.style.display = 'none';

            // Fix for html2canvas rendering webkit-background-clip as a solid background block
            const profileNameEl = DOM.name;
            let prevBg = '', prevClip = '', prevFill = '', prevColor = '', prevShadow = '';
            if (profileNameEl) {
                prevBg = profileNameEl.style.background;
                prevClip = profileNameEl.style.webkitBackgroundClip;
                prevFill = profileNameEl.style.webkitTextFillColor;
                prevColor = profileNameEl.style.color;
                prevShadow = profileNameEl.style.textShadow;

                profileNameEl.style.background = 'none';
                profileNameEl.style.webkitBackgroundClip = 'initial';
                profileNameEl.style.webkitTextFillColor = '#F1F5F9';
                profileNameEl.style.color = '#F1F5F9';
                profileNameEl.style.textShadow = 'none';
            }

            const watermarkHeader = document.createElement('div');
            watermarkHeader.style.cssText = 'margin-bottom: 24px; padding-bottom: 18px; border-bottom: 1px solid #223447; animation: none; width: 100%;';
            watermarkHeader.innerHTML = `
                <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <img src="../logo/logo.png" style="height: 42px; width: auto; border-radius: 8px; object-fit: contain;" onerror="console.warn('Logo failed to load');" />
                        <span style="font-family: 'Chakra Petch', sans-serif; font-size: 24px; font-weight: 700; color: #F1F5F9; letter-spacing: 0.02em;">HavenCraft</span>
                    </div>
                    <div style="text-align: right; background: #111D29; padding: 8px 16px; border-radius: 8px; border: 1px solid #324A63;">
                        <div style="color: #F59E0B; margin: 0; font-family: 'Fira Code', monospace; font-size: 14px; font-weight: 600; letter-spacing: 0.5px;">${window.SERVER_CONFIG?.ip || 'mc.havencraft.pro'}</div>
                        <div style="color: #B8C7D6; margin-top: 4px; font-size: 12px; font-weight: 600;">Port: <span style="color: #F1F5F9;" class="server-port">${window.SERVER_CONFIG?.port || 2566}</span></div>
                    </div>
                </div>
            `;
            target.insertBefore(watermarkHeader, target.firstChild);

            const watermarkFooter = document.createElement('div');
            watermarkFooter.style.cssText = 'text-align: center; margin-top: 16px; padding-top: 14px; border-top: 1px solid #223447; color: #8FA3B8; font-size: 13px; font-weight: 500;';
            const d = new Date();
            watermarkFooter.innerHTML = `<i class="fa-solid fa-chart-simple"></i> Player Statistics captured on ${d.toLocaleDateString()} at ${d.toLocaleTimeString()}`;
            target.appendChild(watermarkFooter);

            const skinCanvas = DOM.skinContainer?.querySelector('canvas');
            let imgTemp = null;
            if (skinCanvas) {
                imgTemp = document.createElement('img');
                imgTemp.crossOrigin = 'anonymous';
                const cleanUserScreenshot = (username || '').replace(/^\.+/, '');
                imgTemp.src = `https://minotar.net/armor/body/${encodeURIComponent(cleanUserScreenshot)}/240.png`;
                imgTemp.style.height = '240px';
                skinCanvas.style.display = 'none';
                DOM.skinContainer.appendChild(imgTemp);

                await new Promise((r) => {
                    imgTemp.onload = r;
                    imgTemp.onerror = () => {
                        console.warn('Fallback skin image failed to load, continuing export');
                        r();
                    };
                    setTimeout(r, 2000);
                });
            }

            await new Promise(r => setTimeout(r, 200));

            try {
                const canvas = await html2canvas(target, {
                    backgroundColor: '#0B131B',
                    scale: 2,
                    useCORS: true,
                    allowTaint: false,
                    logging: false,
                    onclone: (clonedDoc) => {
                        // Strip animations/transitions to prevent cloned elements from restarting at opacity: 0
                        const allElements = clonedDoc.querySelectorAll('*');
                        for (let i = 0; i < allElements.length; i++) {
                            allElements[i].style.animation = 'none';
                            allElements[i].style.transition = 'none';
                            allElements[i].style.opacity = '1';
                            allElements[i].style.transform = 'none';
                        }

                        const clonedTarget = clonedDoc.getElementById('profileDashboard');
                        if (clonedTarget) {
                            clonedTarget.style.padding = '30px';
                            clonedTarget.style.borderRadius = '12px';
                            clonedTarget.style.background = '#0B131B';
                        }

                        const banner = clonedDoc.querySelector('.profile-banner-new');
                        if (banner) {
                            banner.style.background = '#111D29';
                            banner.style.border = '1px solid #223447';
                        }
                    }
                });

                const link = document.createElement('a');
                link.download = `HavenCraft-${username}-Profile.png`;
                link.href = canvas.toDataURL('image/png');
                link.click();
                DOM.downloadBtn.innerHTML = '<i class="fa-solid fa-check"></i> Image Exported!';
            } catch (err) {
                console.error('Capture error:', err);
                const errMsg = err?.message || 'Canvas tainted or CORS issue';
                if (typeof showToast === 'function') {
                    showToast('Failed to save image: ' + errMsg);
                } else {
                    alert('Failed to save image: ' + errMsg);
                }
            } finally {
                if (shareWrapper) shareWrapper.style.display = '';
                watermarkHeader.remove();
                watermarkFooter.remove();

                if (profileNameEl) {
                    profileNameEl.style.background = prevBg;
                    profileNameEl.style.webkitBackgroundClip = prevClip;
                    profileNameEl.style.webkitTextFillColor = prevFill;
                    profileNameEl.style.color = prevColor;
                    profileNameEl.style.textShadow = prevShadow;
                }

                if (skinCanvas) {
                    skinCanvas.style.display = '';
                    imgTemp?.remove();
                }

                setTimeout(() => {
                    DOM.downloadBtn.innerHTML = originalContent;
                    DOM.downloadBtn.disabled = false;
                }, 3000);
            }
        };
    }

    function renderAuth() {
        const urlParams = new URLSearchParams(window.location.search);
        const urlUser = urlParams.get('player') || urlParams.get('user');
        const currentUser = window.HavenCraftCore?.getCurrentUser();
        const username = urlUser || currentUser;

        if (!username) {
            if (DOM.dashboard) DOM.dashboard.style.display = 'none';
            if (DOM.loading) DOM.loading.style.display = 'none';
            if (DOM.guest) DOM.guest.style.display = 'block';
            return;
        }

        if (DOM.guest) DOM.guest.style.display = 'none';
        if (DOM.dashboard) DOM.dashboard.style.display = 'none';
        if (DOM.loading) {
            DOM.loading.style.opacity = '1';
            DOM.loading.style.display = 'block';
        }
        if (DOM.name) DOM.name.textContent = username;

        document.title = `${username} / HavenCraft`;
        const breadcrumbEl = document.getElementById('breadcrumbProfileText');
        if (breadcrumbEl) breadcrumbEl.textContent = username;

        renderSkin(username);
        bindShareAndExport(username);
        fetchData(username);
    }

    async function fetchData(username) {
        const requestId = ++activeRequestId;
        const clientsReady = await waitForSupabaseClients();
        if (requestId !== activeRequestId) return;

        if (!clientsReady) {
            console.warn('[Profile] Supabase clients unavailable.');
            if (DOM.loading) DOM.loading.style.display = 'none';
            if (DOM.dashboard) DOM.dashboard.style.display = 'block';
            if (DOM.statsGrid) DOM.statsGrid.style.display = 'none';
            if (DOM.statsNotFound) DOM.statsNotFound.style.display = 'block';
            return;
        }

        const cleanUser = escapeSql((username || '').trim());
        if (!cleanUser) return;

        let { data: statsData } = await window.supabaseLeaderboard
            .from('player_stats')
            .select('*')
            .ilike('username', cleanUser)
            .limit(1)
            .maybeSingle();

        if (!statsData) {
            const altUser = cleanUser.startsWith('.') ? cleanUser.substring(1) : ('.' + cleanUser);
            const { data: altData } = await window.supabaseLeaderboard
                .from('player_stats')
                .select('*')
                .ilike('username', altUser)
                .limit(1)
                .maybeSingle();
            if (altData) statsData = altData;
        }

        if (requestId !== activeRequestId) return;

        if (statsData) {
            DOM.playtime.textContent = `${(statsData.playtime_sec / 3600).toFixed(1)} Hrs`;
            DOM.kills.textContent = fmtCompact(statsData.kills, 1);
            DOM.deaths.textContent = fmtCompact(statsData.deaths, 1);
            DOM.money.textContent = `$${fmtCompact(statsData.money, 2)}`;
            if (DOM.shards) DOM.shards.textContent = fmtCompact(statsData.shards, 2);

            if (DOM.statBlockPlace) DOM.statBlockPlace.textContent = fmtCompact(statsData.block_place, 1);
            if (DOM.statBlockBreak) DOM.statBlockBreak.textContent = fmtCompact(statsData.block_break, 1);
            const totalBlocks = (Number(statsData.block_place) || 0) + (Number(statsData.block_break) || 0);
            if (DOM.statTotalBlocks) DOM.statTotalBlocks.textContent = fmtCompact(totalBlocks, 1);
            if (DOM.statMobsKilled) DOM.statMobsKilled.textContent = fmtCompact(statsData.mobs_killed, 1);

            if (DOM.statKillRatio) {
                const kills = Number(statsData.kills) || 0;
                const rawDeaths = Number(statsData.deaths) || 0;
                const kd = rawDeaths === 0 ? (kills > 0 ? kills.toFixed(2) : '0.00') : (kills / rawDeaths).toFixed(2);
                const dk = rawDeaths === 0 ? '0.00' : (rawDeaths / (kills || 1)).toFixed(2);
                DOM.statKillRatio.textContent = kd;
                if (DOM.statDeathRatio) DOM.statDeathRatio.textContent = dk;
            }

            const statCols = ['playtime_sec', 'kills', 'deaths', 'money', 'shards', 'block_place', 'block_break', 'mobs_killed'];
            await Promise.all(statCols.map(async (col) => {
                const val = Number(statsData[col]) || 0;
                const rankEl = document.getElementById(`rank-${col}`);
                if (val <= 0) {
                    if (rankEl) rankEl.style.display = 'none';
                    return;
                }

                try {
                    const { count, error } = await window.supabaseLeaderboard
                        .from('player_stats')
                        .select('*', { count: 'exact', head: true })
                        .gt(col, statsData[col]);

                    if (error) throw error;
                    const rankNum = (count || 0) + 1;
                    if (rankEl) {
                        rankEl.textContent = `#${rankNum}`;
                        rankEl.title = `Ranked #${rankNum} on the server`;
                        rankEl.style.display = 'block';
                    }
                } catch (err) {
                    console.warn(`Failed to calculate rank for ${col}:`, err);
                }
            }));

            if (DOM.statsGrid) DOM.statsGrid.style.display = 'flex';
            if (DOM.statsNotFound) DOM.statsNotFound.style.display = 'none';
        } else {
            DOM.playtime.textContent = '0 Hrs';
            DOM.kills.textContent = '0';
            DOM.deaths.textContent = '0';
            DOM.money.textContent = '$0';
            if (DOM.shards) DOM.shards.textContent = '0';
            if (DOM.statBlockPlace) DOM.statBlockPlace.textContent = '0';
            if (DOM.statBlockBreak) DOM.statBlockBreak.textContent = '0';
            if (DOM.statTotalBlocks) DOM.statTotalBlocks.textContent = '0';
            if (DOM.statMobsKilled) DOM.statMobsKilled.textContent = '0';

            if (DOM.statsGrid) DOM.statsGrid.style.display = 'none';
            if (DOM.statsNotFound) DOM.statsNotFound.style.display = 'block';
        }

        let totalVotes = 0;
        try {
            const { count: voteCount, error: voteErr } = await window.supabaseVote
                .from('votes')
                .select('*', { count: 'exact', head: true })
                .ilike('username', cleanUser);

            if (!voteErr && typeof voteCount === 'number') {
                totalVotes = voteCount;
            }
        } catch (vErr) {
            console.warn('[Profile] Failed to fetch vote count:', vErr);
        }

        if (requestId !== activeRequestId) return;

        if (DOM.statVotes) {
            DOM.statVotes.textContent = fmtCompact(totalVotes, 1);
        }

        lastRenderedProfile = { username, statsData: statsData || {}, totalVotes };
        renderAllBadges(username, lastRenderedProfile.statsData, totalVotes);

        if (DOM.loading && DOM.loading.style.display !== 'none') {
            DOM.loading.style.transition = 'opacity 0.2s ease';
            DOM.loading.style.opacity = '0';
            setTimeout(() => {
                DOM.loading.style.display = 'none';
                if (DOM.dashboard) {
                    DOM.dashboard.style.display = 'block';
                    DOM.dashboard.style.opacity = '0';
                    DOM.dashboard.style.transition = 'opacity 0.3s cubic-bezier(0.16, 1, 0.3, 1)';
                    requestAnimationFrame(() => {
                        DOM.dashboard.style.opacity = '1';
                    });
                }
            }, 200);
        } else if (DOM.dashboard) {
            DOM.dashboard.style.display = 'block';
        }
    }

    function getPaidRank(username) {
        if (!username || typeof window.PAID_RANKS !== 'object' || window.PAID_RANKS === null) return null;
        const lower = username.toLowerCase();
        const cleanLower = lower.replace(/^\.+/, '');
        return window.PAID_RANKS[lower] || window.PAID_RANKS['.' + cleanLower] || window.PAID_RANKS[cleanLower] || null;
    }

    function renderAllBadges(username, statsData, totalVotes) {
        if (!DOM.badges) return;
        let tags = '';

        const staffMember = getStaffMember(username);
        if (staffMember?.role) {
            const style = STAFF_ROLE_STYLES[staffMember.roleClass]
                || STAFF_ROLE_STYLES[staffMember.role.toLowerCase().trim()]
                || { cls: 'rank-mod', icon: 'fa-shield-halved' };
            tags += `<span class="profile-rank-tag ${style.cls}"><i class="fa-solid ${style.icon}"></i> ${escapeHtml(staffMember.role)}</span>`;
        }

        const paidRank = getPaidRank(username);
        if (paidRank) {
            tags += `<span class="profile-rank-tag rank-paid"><i class="fa-solid fa-gem"></i> ${escapeHtml(paidRank)}</span>`;
        }

        if (DOM.rankTag) {
            DOM.rankTag.innerHTML = tags;
        }

        const badges = [
            renderTierBadge(statsData.kills || 0, 'badge-pvp', KILL_BADGES),
            renderTierBadge((statsData.playtime_sec || 0) / 3600, 'badge-veteran', PLAYTIME_BADGES),
            renderTierBadge(statsData.deaths || 0, 'badge-death', DEATH_BADGES),
            renderTierBadge(totalVotes, 'badge-voter', VOTE_BADGES)
        ].join('');

        DOM.badges.innerHTML = badges;
    }

    window.addEventListener('hc_login', renderAuth);
    window.addEventListener('hc_logout', renderAuth);
    window.addEventListener('hc_staff_loaded', () => {
        if (lastRenderedProfile) {
            renderAllBadges(lastRenderedProfile.username, lastRenderedProfile.statsData, lastRenderedProfile.totalVotes);
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            setupProfileLogin();
            renderAuth();
        });
    } else {
        setupProfileLogin();
        renderAuth();
    }
})();
