/* ============================================
   HavenCraft — Discord Community Widget
   ============================================ */

(function () {
    'use strict';

    const SERVER_ID = '1447293915165233263';
    const FALLBACK_INVITE = window.SERVER_CONFIG?.discordUrl || 'https://dc.havencraft.pro';
    const API_URL = `https://discord.com/api/guilds/${SERVER_ID}/widget.json`;
    const DISCORD_CACHE_KEY = 'hvn_discord_widget_cache_v2';
    const DISCORD_CACHE_TTL = 90000;

    const escapeHtml = (text) => (typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || ''));
    const notify = (msg) => {
        if (typeof window.showToast === 'function') window.showToast(msg);
    };

    document.addEventListener('DOMContentLoaded', () => {
        const guildNameEl = document.getElementById('guild-name');
        const footerGuildNameEl = document.getElementById('footer-guild-name');
        const onlineCountEl = document.getElementById('online-count');
        const channelsListEl = document.getElementById('channels-list');
        const membersGridEl = document.getElementById('members-grid');
        const membersListHeaderEl = document.getElementById('members-list-header');
        const memberSearchInput = document.getElementById('member-search');

        const mainInviteBtn = document.getElementById('guild-invite-btn-main');
        const footerInviteBtn = document.getElementById('guild-invite-btn-footer');
        const inviteUrlText = document.getElementById('invite-url-text');

        let allMembers = [];

        async function fetchDiscordWidget() {
            let cachedData = null;
            try {
                const cached = sessionStorage.getItem(DISCORD_CACHE_KEY);
                if (cached) {
                    const parsed = JSON.parse(cached);
                    if (parsed && parsed.data) {
                        cachedData = parsed.data;
                        if (Date.now() - parsed.timestamp < DISCORD_CACHE_TTL) {
                            renderWidgetData(cachedData);
                            return;
                        }
                    }
                }
            } catch (_) {}

            try {
                const response = await fetch(API_URL);
                if (response.status === 429 && cachedData) {
                    renderWidgetData(cachedData);
                    return;
                }
                if (!response.ok) throw new Error(`HTTP error ${response.status}`);

                const data = await response.json();
                try {
                    sessionStorage.setItem(DISCORD_CACHE_KEY, JSON.stringify({ timestamp: Date.now(), data }));
                } catch (_) {}
                renderWidgetData(data);
            } catch (_) {
                if (cachedData) {
                    renderWidgetData(cachedData);
                } else {
                    renderErrorState();
                }
            }
        }

        function renderWidgetData(data) {
            const serverName = data.name || 'HavenCraft SMP';
            if (guildNameEl) guildNameEl.textContent = serverName;
            if (footerGuildNameEl) footerGuildNameEl.textContent = serverName;
            if (onlineCountEl) onlineCountEl.textContent = `${data.presence_count || 0} Online`;

            const inviteLink = data.instant_invite || FALLBACK_INVITE;
            if (mainInviteBtn) mainInviteBtn.href = inviteLink;
            if (footerInviteBtn) footerInviteBtn.href = inviteLink;
            if (inviteUrlText) inviteUrlText.textContent = inviteLink;

            if (data.channels && data.channels.length > 0) {
                renderChannels(data.channels);
            }

            allMembers = data.members || [];
            renderMembers(allMembers);
            memberSearchInput?.removeAttribute('disabled');
        }

        function renderChannels(channels) {
            if (!channelsListEl) return;

            const textChannels = [];
            const voiceChannels = [];
            channels.forEach((ch) => {
                const nameLower = (ch.name || '').toLowerCase();
                if (nameLower.includes('lounge') || nameLower.includes('voice') || nameLower.includes('gaming') || nameLower.includes('music') || nameLower.includes('staff') || nameLower.includes('room')) {
                    voiceChannels.push(ch);
                } else {
                    textChannels.push(ch);
                }
            });

            let channelsHTML = '';
            if (textChannels.length > 0) {
                channelsHTML += '<div class="discord-channel-category"><div class="discord-category-header"><span>TEXT CHANNELS</span></div>';
                textChannels.slice(0, 7).forEach((ch, idx) => {
                    channelsHTML += `<button type="button" class="discord-channel-item ${idx === 0 ? 'active' : ''}" onclick="setActiveChannel(this)"><i class="fa-solid fa-hashtag discord-channel-icon" aria-hidden="true"></i><span>${escapeHtml(ch.name)}</span></button>`;
                });
                channelsHTML += '</div>';
            }

            const finalVoice = voiceChannels.length > 0 ? voiceChannels : [
                { name: 'General Lounge' },
                { name: 'Gaming Zone' },
                { name: 'Duo / Trio' },
                { name: 'Staff Meeting' }
            ];
            channelsHTML += '<div class="discord-channel-category"><div class="discord-category-header"><span>VOICE CHANNELS</span></div>';
            finalVoice.forEach((ch) => {
                channelsHTML += `<button type="button" class="discord-channel-item" onclick="setActiveChannel(this)"><i class="fa-solid fa-volume-high discord-channel-icon" aria-hidden="true"></i><span>${escapeHtml(ch.name)}</span></button>`;
            });
            channelsHTML += '</div>';
            channelsListEl.innerHTML = channelsHTML;
        }

        function renderMembers(members) {
            if (!membersGridEl || !membersListHeaderEl) return;

            if (!members || members.length === 0) {
                membersGridEl.innerHTML = '<div class="discord-loading-container"><i class="fa-solid fa-users-slash" aria-hidden="true"></i><p>No members matching filter.</p></div>';
                membersListHeaderEl.textContent = 'Online Members (0)';
                return;
            }

            membersListHeaderEl.textContent = `Online Members (${members.length})`;
            membersGridEl.innerHTML = members.map((member) => {
                const statusClass = member.status === 'idle' ? 'idle' : (member.status === 'dnd' ? 'dnd' : 'online');
                const rawAvatar = member.avatar_url || 'https://cdn.discordapp.com/embed/avatars/0.png';
                const avatarUrl = /^https:\/\/[a-z0-9_.-]+\.discord(app)?\.(com|net)\//i.test(rawAvatar) ? rawAvatar : 'https://cdn.discordapp.com/embed/avatars/0.png';
                const cleanUsername = escapeHtml(member.username || 'Member');
                const activityHTML = member.game && member.game.name
                    ? `<div class="discord-member-activity" title="${escapeHtml(member.game.name)}"><i class="fa-solid fa-gamepad" aria-hidden="true"></i><span>Playing ${escapeHtml(member.game.name)}</span></div>`
                    : '<div class="discord-member-activity"><span>Online</span></div>';

                return `
                    <div class="discord-member-card">
                        <div class="discord-avatar-container">
                            <img src="${avatarUrl}" class="discord-member-avatar" alt="${cleanUsername}" onerror="this.src='https://cdn.discordapp.com/embed/avatars/0.png'" />
                            <div class="discord-status-dot ${statusClass}"></div>
                        </div>
                        <div class="discord-member-info">
                            <div class="discord-member-name">${cleanUsername}</div>
                            ${activityHTML}
                        </div>
                    </div>
                `;
            }).join('');
        }

        function renderErrorState() {
            if (guildNameEl) guildNameEl.textContent = 'HavenCraft SMP';
            if (footerGuildNameEl) footerGuildNameEl.textContent = 'HavenCraft';
            if (onlineCountEl) onlineCountEl.textContent = 'Widget Offline';
            if (membersGridEl) {
                membersGridEl.innerHTML = `
                    <div class="discord-error-container">
                        <i class="fa-solid fa-triangle-exclamation discord-error-icon" aria-hidden="true"></i>
                        <h3>Widget Unavailable</h3>
                        <p>Unable to load the live Discord member widget right now. You can still join directly using our invite button.</p>
                        <button type="button" class="discord-retry-btn" onclick="window.location.reload()">
                            <i class="fa-solid fa-arrow-rotate-right" aria-hidden="true"></i> Retry
                        </button>
                    </div>
                `;
            }
        }

        memberSearchInput?.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            if (!query) {
                renderMembers(allMembers);
                return;
            }
            renderMembers(allMembers.filter((m) =>
                (m.username || '').toLowerCase().includes(query) ||
                ((m.game && m.game.name) || '').toLowerCase().includes(query)
            ));
        });

        fetchDiscordWidget();
    });

    window.copyInviteLink = async function () {
        const el = document.getElementById('invite-url-text');
        const urlText = el ? el.textContent.trim() : FALLBACK_INVITE;
        const ok = typeof window.copyText === 'function'
            ? await window.copyText(urlText)
            : await navigator.clipboard.writeText(urlText).then(() => true).catch(() => false);
        notify(ok ? 'Discord Invite Link copied!' : 'Could not copy invite link.');
    };

    window.setActiveChannel = function (element) {
        document.querySelectorAll('.discord-channel-item').forEach(item => item.classList.remove('active'));
        element.classList.add('active');
        const span = element.querySelector('span');
        if (span) notify(`Previewing #${span.textContent}`);
    };
})();
