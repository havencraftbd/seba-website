/* ============================================
   HavenCraft — Vote Page Controller
   Tab Switching, User Vote Verification, Cooldown Alerts
   ============================================ */

(function () {
    'use strict';

    let voteReminderTimer = null;

    function getVoteState() {
        return window.HavenVote || (window.HavenVote = {
            currentUsername: null,
            currentSiteCooldowns: [],
            countdownInterval: null
        });
    }

    function armReminderTimer(delayMs, siteName) {
        if (voteReminderTimer) clearTimeout(voteReminderTimer);
        const timerDelay = Math.min(Math.max(0, delayMs), 2147483647);
        voteReminderTimer = setTimeout(() => {
            sendVoteNotification(siteName);
            localStorage.removeItem('hc_vote_next_expiry');
            const username = getVoteState().currentUsername;
            if (username && typeof loadUserVoteStatus === 'function') {
                loadUserVoteStatus(username);
            }
        }, timerDelay);
    }

    function resetGuestView(showPrompt) {
        getVoteState().currentUsername = null;
        getVoteState().currentSiteCooldowns = [];

        const panel = document.getElementById('statusNotification');
        if (panel) {
            if (showPrompt) {
                panel.style.display = 'flex';
                panel.innerHTML = `
                    <div style="display:flex; width:100%; align-items:center;">
                        <div class="status-icon" style="background: rgba(255,200,0,0.1); color: #ffcc00;"><i class="fa-solid fa-circle-info"></i></div>
                        <div class="status-content">
                            <h3 style="color: var(--text-secondary); margin: 0;">Enter your username</h3>
                            <p style="font-size: 0.85rem; color: var(--text-muted); margin: 4px 0 0;">Type your Minecraft username above to check your vote cooldown status.</p>
                        </div>
                    </div>`;
            } else {
                panel.style.display = 'none';
            }
        }

        if (typeof renderVoteSites === 'function') {
            renderVoteSites([]);
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        if (typeof window.syncServerClock === 'function') {
            window.syncServerClock();
        }
        if (typeof renderVoteSites === 'function') {
            renderVoteSites([]);
        }

        const mainTabBtns = document.querySelectorAll('.tab-btn[data-tab]');
        const mainTabContents = document.querySelectorAll('.tab-content');

        mainTabBtns.forEach((btn) => {
            btn.addEventListener('click', () => {
                const target = btn.dataset.tab;
                mainTabBtns.forEach((b) => {
                    b.classList.remove('active');
                    b.setAttribute('aria-selected', 'false');
                });
                mainTabContents.forEach((c) => c.classList.remove('active'));
                btn.classList.add('active');
                btn.setAttribute('aria-selected', 'true');

                const panel = document.getElementById(`tab-${target}`);
                if (panel) panel.classList.add('active');

                if (target === 'leaderboards' && typeof loadLeaderboard === 'function') {
                    const active = document.querySelector('.leaderboard-tab[data-lb-tab].active');
                    loadLeaderboard(active?.dataset.lbTab || 'monthly');
                }
                if (target === 'wall-of-fame' && typeof loadWallOfFame === 'function') {
                    const active = document.querySelector('.wof-tab[data-wof-tab].active');
                    loadWallOfFame(active?.dataset.wofTab || 'alltime');
                }
            });
        });

        const lbTabs = document.querySelectorAll('.leaderboard-tab[data-lb-tab]');
        lbTabs.forEach((btn) => {
            btn.addEventListener('click', () => {
                lbTabs.forEach((b) => b.classList.remove('active'));
                btn.classList.add('active');
                if (typeof loadLeaderboard === 'function') {
                    loadLeaderboard(btn.dataset.lbTab);
                }
            });
        });

        const wofTabs = document.querySelectorAll('.wof-tab[data-wof-tab]');
        wofTabs.forEach((btn) => {
            btn.addEventListener('click', () => {
                wofTabs.forEach((b) => b.classList.remove('active'));
                btn.classList.add('active');
                if (typeof loadWallOfFame === 'function') {
                    loadWallOfFame(btn.dataset.wofTab);
                }
            });
        });

        const usernameInput = document.getElementById('usernameInput');
        const checkBtn = document.getElementById('checkVotesBtn');

        if (window.HavenCraftCore) {
            const currentUser = window.HavenCraftCore.getCurrentUser();
            if (currentUser && usernameInput) {
                usernameInput.value = currentUser;
                if (typeof loadUserVoteStatus === 'function') {
                    loadUserVoteStatus(currentUser);
                }
            }
        }

        window.addEventListener('hc_login', (e) => {
            const user = e.detail?.gametag;
            if (user && usernameInput) {
                usernameInput.value = user;
                if (typeof loadUserVoteStatus === 'function') {
                    loadUserVoteStatus(user);
                }
            }
        });

        window.addEventListener('hc_logout', () => {
            if (usernameInput) usernameInput.value = '';
            resetGuestView(false);
        });

        checkBtn?.addEventListener('click', () => {
            const username = usernameInput?.value.trim();
            if (!username) {
                resetGuestView(true);
                return;
            }
            if (typeof loadUserVoteStatus === 'function') {
                loadUserVoteStatus(username);
            }
        });

        usernameInput?.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const username = usernameInput.value.trim();
                if (username && typeof loadUserVoteStatus === 'function') {
                    loadUserVoteStatus(username);
                } else {
                    resetGuestView(false);
                }
            }
        });

        const reminderBtn = document.getElementById('btnVoteReminder');
        if (reminderBtn) {
            reminderBtn.addEventListener('click', toggleVoteReminders);
            updateReminderBtnUI();
        }

        checkPendingVoteReminders();

        if (typeof setupRealtime === 'function') {
            setupRealtime();
        }
    });

    function updateReminderBtnUI() {
        const btn = document.getElementById('btnVoteReminder');
        if (!btn) return;

        const isEnabled = localStorage.getItem('hc_vote_reminders_enabled') === 'true' &&
            ('Notification' in window) && Notification.permission === 'granted';

        if (isEnabled) {
            btn.classList.add('active');
            btn.innerHTML = '<i class="fa-solid fa-bell" style="color:var(--primary)" aria-hidden="true"></i> <span id="reminderBtnText">Alerts Active</span>';
        } else {
            btn.classList.remove('active');
            btn.innerHTML = '<i class="fa-regular fa-bell" aria-hidden="true"></i> <span id="reminderBtnText">Enable Alerts</span>';
        }
    }

    async function toggleVoteReminders() {
        if (!('Notification' in window)) {
            if (typeof showToast === 'function') {
                showToast('⚠️ Notifications are not supported in this browser.');
            }
            return;
        }

        const currentlyEnabled = localStorage.getItem('hc_vote_reminders_enabled') === 'true' && Notification.permission === 'granted';

        if (currentlyEnabled) {
            localStorage.removeItem('hc_vote_reminders_enabled');
            if (voteReminderTimer) clearTimeout(voteReminderTimer);
            updateReminderBtnUI();
            if (typeof showToast === 'function') showToast('Vote reminders turned off.');
            return;
        }

        try {
            const permission = await Notification.requestPermission();
            if (permission === 'granted') {
                localStorage.setItem('hc_vote_reminders_enabled', 'true');
                updateReminderBtnUI();
                if (typeof showToast === 'function') {
                    showToast('✓ Vote reminders enabled! You will be notified when cooldowns expire.');
                }
                const cooldowns = getVoteState().currentSiteCooldowns;
                if (cooldowns.length > 0) {
                    scheduleVoteReminder(cooldowns);
                }
            } else if (permission === 'denied') {
                if (typeof showToast === 'function') {
                    showToast('⚠️ Notification permission blocked. Please enable it in browser settings.');
                }
            }
        } catch (e) {
            console.warn('Notification error:', e);
        }
    }

    function sendVoteNotification(siteName) {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;

        const lastNotified = localStorage.getItem('hc_vote_last_notified');
        const now = Date.now();
        if (lastNotified && (now - parseInt(lastNotified, 10)) < 12 * 3600 * 1000) {
            return;
        }

        localStorage.setItem('hc_vote_last_notified', now.toString());

        const isDailyFollowup = siteName === 'daily_reminder';
        const title = isDailyFollowup ? 'HavenCraft SMP: Daily Vote Reminder' : 'HavenCraft SMP: Vote Ready';
        const message = isDailyFollowup
            ? 'You have votes available today on HavenCraft.'
            : (siteName
                ? `Your cooldown for ${siteName} has ended. Vote now for your in-game rewards.`
                : 'Your vote cooldown has ended. Vote now for your in-game rewards.');

        const options = {
            body: message,
            icon: '/logo/logo.webp',
            badge: '/logo/icon.webp',
            data: { url: '/vote/' },
            tag: 'havencraft-vote-reminder',
            renotify: false
        };

        if (navigator.serviceWorker && navigator.serviceWorker.controller) {
            navigator.serviceWorker.ready.then(reg => {
                reg.showNotification(title, options);
            }).catch(() => {
                new Notification(title, options);
            });
        } else {
            new Notification(title, options);
        }

        const nextDailyExpiry = Date.now() + 24 * 3600 * 1000;
        localStorage.setItem('hc_vote_next_expiry', nextDailyExpiry.toString());
        localStorage.setItem('hc_vote_next_site', 'daily_reminder');
    }

    function scheduleVoteReminder(siteCooldowns) {
        if (!siteCooldowns || siteCooldowns.length === 0) return;
        if (localStorage.getItem('hc_vote_reminders_enabled') !== 'true') return;
        if (!('Notification' in window) || Notification.permission !== 'granted') return;

        if (voteReminderTimer) clearTimeout(voteReminderTimer);

        const activeCooldowns = siteCooldowns.filter(s => s.state === 'cooldown' && s.remainingMs > 0);
        if (activeCooldowns.length === 0) return;

        activeCooldowns.sort((a, b) => a.remainingMs - b.remainingMs);
        const earliest = activeCooldowns[0];
        const expiryTimestamp = Date.now() + earliest.remainingMs;

        localStorage.setItem('hc_vote_next_expiry', expiryTimestamp.toString());
        localStorage.setItem('hc_vote_next_site', earliest.site?.name || '');

        armReminderTimer(earliest.remainingMs, earliest.site?.name);
    }

    function checkPendingVoteReminders() {
        if (localStorage.getItem('hc_vote_reminders_enabled') !== 'true') return;
        if (!('Notification' in window) || Notification.permission !== 'granted') return;

        const savedExpiry = localStorage.getItem('hc_vote_next_expiry');
        if (!savedExpiry) return;

        const expiryTime = parseInt(savedExpiry, 10);
        const now = Date.now();
        const siteName = localStorage.getItem('hc_vote_next_site') || '';

        if (now >= expiryTime) {
            sendVoteNotification(siteName);
            localStorage.removeItem('hc_vote_next_expiry');
        } else {
            armReminderTimer(expiryTime - now, siteName);
        }
    }

    window.scheduleVoteReminder = scheduleVoteReminder;

    window.handleVoteClick = function (siteId, siteName, cooldownHours = 24) {
        if (!getVoteState().currentUsername) return;

        if (localStorage.getItem('hc_vote_reminders_enabled') === 'true' &&
            ('Notification' in window) && Notification.permission === 'granted') {
            const cooldownMs = (cooldownHours || 24) * 3600 * 1000;
            const expiryTimestamp = Date.now() + cooldownMs;

            const currentSaved = localStorage.getItem('hc_vote_next_expiry');
            if (!currentSaved || expiryTimestamp < parseInt(currentSaved, 10)) {
                localStorage.setItem('hc_vote_next_expiry', expiryTimestamp.toString());
                localStorage.setItem('hc_vote_next_site', siteName || '');
                armReminderTimer(cooldownMs, siteName);
            }
        }
    };

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            checkPendingVoteReminders();
            const usernameInput = document.getElementById('usernameInput');
            const username = usernameInput ? usernameInput.value.trim() : getVoteState().currentUsername;
            if (username && typeof loadUserVoteStatus === 'function') {
                loadUserVoteStatus(username);
            }
        }
    });
})();
