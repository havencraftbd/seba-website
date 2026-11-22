/* ============================================================================
 * HavenCraft — Support Desk Page Controller (/support/)
 * Relies on window.HavenTicketChat (assets/js/ticket-chat.js).
 * ============================================================================ */
(function () {
    'use strict';

    const chat = window.HavenTicketChat;
    if (!chat) return;

    const {
        playChime,
        classifyMessageKind,
        deduplicateMessages,
        getActiveTicket,
        saveActiveTicket,
        clearActiveTicket,
        readChatCache,
        writeChatCache,
        appendChatCache,
        getSavedRating,
        fetchTicketViaApi,
        sendTicketReply,
        closeTicketOnServer,
        fileToBase64,
        stageImageAttachment,
        clearAttachmentPreview,
        uploadTicketAttachment,
        mountCsatWidget,
        setupChatInputBehaviors,
        subscribeTicketRealtime
    } = chat;

    let currentTicket = null;
    let activeSubscription = null;
    let stagedAttachment = null;
    const renderedMsgIds = new Set();

    function esc(str) {
        if (typeof window.escapeHtml === 'function') return window.escapeHtml(str);
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function notify(msg) {
        if (typeof window.showToast === 'function') window.showToast(msg);
    }

    // ------------------------------------------------------------------------
    // 1. Support Desk Localization & Lookup View Switcher
    // ------------------------------------------------------------------------
    window.hvnSwitchSupportLang = function (lang) {
        const nextLang = lang || 'bn';
        if (typeof window.deskSetLanguage === 'function' && document.querySelector('#newTicketView .hvn-wiz-shell')) {
            window.deskSetLanguage(nextLang);
            return;
        }
        try { localStorage.setItem('hvn_support_lang', nextLang); } catch { }
        window.hvnUpdateSupportPageLanguage(nextLang);
    };

    window.hvnUpdateSupportPageLanguage = function (lang) {
        if (!lang) return;
        const isBn = lang === 'bn';

        document.getElementById('deskTopLangBar')?.setAttribute('data-active', isBn ? 'bn' : 'en');
        const bnBtn = document.getElementById('deskTopLangBn');
        const enBtn = document.getElementById('deskTopLangEn');
        bnBtn?.classList.toggle('active', isBn);
        enBtn?.classList.toggle('active', !isBn);
        bnBtn?.setAttribute('aria-pressed', isBn ? 'true' : 'false');
        enBtn?.setAttribute('aria-pressed', !isBn ? 'true' : 'false');

        const isLookup = !!window.__isLookupMode;
        const topbarIcon = document.getElementById('deskTopbarIcon');
        if (topbarIcon) topbarIcon.className = isLookup ? 'fa-solid fa-ticket' : 'fa-solid fa-headset';

        const topbarTitle = document.getElementById('deskTopbarTitle');
        if (topbarTitle) {
            topbarTitle.textContent = isLookup
                ? (isBn ? 'টিকেট রিস্টোর' : 'Restore Ticket')
                : (isBn ? 'নতুন টিকেট' : 'New Ticket');
        }

        const switchIcon = document.getElementById('iconSwitchSupportMode');
        if (switchIcon) switchIcon.className = isLookup ? 'fa-solid fa-arrow-left' : 'fa-solid fa-ticket';

        const switchTxt = document.getElementById('txtSwitchSupportMode');
        if (switchTxt) {
            switchTxt.textContent = isLookup
                ? (isBn ? 'নতুন টিকেট' : 'New Ticket')
                : (isBn ? 'আগের টিকেট' : 'Restore Ticket');
        }

        const livePillTxt = document.getElementById('supportLivePillTxt');
        if (livePillTxt) {
            livePillTxt.textContent = isBn ? 'অনলাইন · ২–৫ মিনিটে রিপ্লাই' : 'Online · ~2–5 min reply';
        }

        const sideDiscordBtnText = document.getElementById('sideDiscordBtnText');
        if (sideDiscordBtnText) sideDiscordBtnText.textContent = isBn ? 'ডিসকর্ড সাপোর্ট' : 'Discord Support';

        const faqUpdates = [
            ['faqCardPayment', isBn ? 'পেমেন্ট ও ডেলিভারি' : 'Payment & Delivery', isBn
                ? 'bKash/Nagad পেমেন্ট কনফার্ম হতে সাধারণত ৫–১৫ মিনিট সময় লাগে। TrxID সাথে রাখুন।'
                : 'bKash/Nagad payments usually verify in 5–15 mins. Keep your TrxID handy.'],
            ['faqCardPassword', isBn ? 'পাসওয়ার্ড রিসেট' : 'Forgot Password', isBn
                ? 'সার্ভারে <code>/changepassword</code> ব্যবহার করুন অথবা টিকিটে আপনার IGN জানান।'
                : 'Use <code>/changepassword</code> in-game or share your IGN in a ticket.'],
            ['faqCardClaim', isBn ? 'ল্যান্ড ক্লেইম' : 'Claim Land', isBn
                ? 'ইন-গেমে <code>/claim</code> বা Golden Shovel দিয়ে জায়গা ক্লেইম করুন।'
                : 'Use <code>/claim</code> or a Golden Shovel in-game to claim land.']
        ];

        faqUpdates.forEach(([id, q, aHtml]) => {
            const card = document.getElementById(id);
            if (!card) return;
            const qEl = card.querySelector('.faq-q');
            const aEl = card.querySelector('.faq-a');
            if (qEl) qEl.textContent = q;
            if (aEl) aEl.innerHTML = aHtml;
        });

        const lDesc = document.getElementById('lookupHeaderDesc');
        if (lDesc) {
            lDesc.textContent = isBn
                ? 'আপনার টিকেট কোড এবং নিরাপত্তার জন্য রেজিস্ট্রেশন করা ইমেইল প্রদান করুন।'
                : 'Enter your ticket code and registered email to restore your chat.';
        }
        const lEmailLabel = document.getElementById('lookupEmailLabel');
        if (lEmailLabel) lEmailLabel.textContent = isBn ? 'রেজিস্ট্রেশন করা ইমেইল (Security Check) *' : 'Registered Email *';

        const lBtnText = document.getElementById('lookupBtnText');
        if (lBtnText) lBtnText.textContent = isBn ? 'চ্যাট লোড করুন' : 'Load Chat';
    };

    window.__isLookupMode = false;
    window.toggleLookupView = function (showLookup) {
        window.__isLookupMode = !!showLookup;
        document.getElementById('contactFormSection')?.classList.remove('chat-mode');
        document.body.classList.remove('support-chat-active');

        const newView = document.getElementById('newTicketView');
        const lookupView = document.getElementById('lookupTicketView');
        const chatRoom = document.getElementById('ticketChatRoom');
        const topBar = document.getElementById('supportDeskTopbar');
        const switchBtn = document.getElementById('btnSwitchSupportMode');
        const lookupAlert = document.getElementById('lookupInlineAlert');

        if (chatRoom) chatRoom.style.display = 'none';
        if (topBar) topBar.style.display = 'flex';
        if (lookupAlert) {
            lookupAlert.style.display = 'none';
            lookupAlert.innerHTML = '';
        }

        if (window.__isLookupMode) {
            if (newView) newView.style.display = 'none';
            if (lookupView) lookupView.style.display = 'block';
            switchBtn?.classList.add('is-lookup-active');
        } else {
            if (newView) newView.style.display = 'block';
            if (lookupView) lookupView.style.display = 'none';
            switchBtn?.classList.remove('is-lookup-active');
        }

        window.hvnUpdateSupportPageLanguage(localStorage.getItem('hvn_support_lang') || 'bn');
    };

    function showLookupAlert(msg) {
        const alertEl = document.getElementById('lookupInlineAlert');
        if (!alertEl) {
            if (msg) notify(msg);
            return;
        }
        if (!msg) {
            alertEl.style.display = 'none';
            alertEl.innerHTML = '';
            return;
        }
        alertEl.innerHTML = `<i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i> <span>${esc(msg)}</span>`;
        alertEl.style.display = 'flex';
        alertEl.focus();
    }

    window.handleLookupTicket = async function () {
        const codeInput = document.getElementById('lookupCode');
        const emailInput = document.getElementById('lookupEmail');
        const submitBtn = document.getElementById('lookupSubmitBtn');
        const code = codeInput ? codeInput.value.trim().toUpperCase() : '';
        const email = emailInput ? emailInput.value.trim().toLowerCase() : '';
        const isBn = (localStorage.getItem('hvn_support_lang') || 'bn') === 'bn';

        showLookupAlert('');
        codeInput?.classList.remove('is-invalid');
        emailInput?.classList.remove('is-invalid');

        if (!code || code.length < 4) {
            codeInput?.classList.add('is-invalid');
            codeInput?.focus();
            showLookupAlert(isBn ? 'অনুগ্রহ করে সঠিক টিকেট কোড লিখুন (যেমন: HC-xxxx)।' : 'Please enter a valid ticket code (e.g. HC-xxxx).');
            return;
        }

        const saved = getActiveTicket({ allowClosed: true });
        const sessionToken = saved && saved.ticket_code === code && saved.sessionToken ? saved.sessionToken : '';

        if (!sessionToken && (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
            emailInput?.classList.add('is-invalid');
            emailInput?.focus();
            showLookupAlert(isBn
                ? 'নিরাপত্তা যাচাইয়ের জন্য টিকেট খোলার সময় ব্যবহৃত ইমেইলটি লিখুন।'
                : 'Please enter the registered email used when creating this ticket.');
            return;
        }

        const origBtnHtml = submitBtn ? submitBtn.innerHTML : '';
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin" aria-hidden="true"></i> <span>${isBn ? 'লোড হচ্ছে...' : 'Loading...'}</span>`;
        }

        try {
            const { ok, data } = await fetchTicketViaApi({ ticketCode: code, email, sessionToken });
            if (ok && data.success && data.ticket) {
                saveActiveTicket(data.ticket);
                writeChatCache(data.ticket.id, data.messages || []);
                renderTicketRoom(data.ticket, data.messages || []);
            } else {
                throw new Error(data.error || (isBn ? 'টিকেট খুঁজে পাওয়া যায়নি বা ইমেইল মেলেনি।' : 'Ticket not found or email mismatch.'));
            }
        } catch (err) {
            showLookupAlert(err.message || (isBn ? 'টিকেট লোড করা যায়নি।' : 'Failed to load ticket.'));
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = origBtnHtml;
            }
        }
    };

    // ------------------------------------------------------------------------
    // 2. Transcript, Message Copy & Closed State
    // ------------------------------------------------------------------------
    window.downloadTicketTranscript = function () {
        if (!currentTicket) return;
        const box = document.getElementById('ticketMessagesBox');
        if (!box) return;

        let transcriptText = [
            '=====================================================',
            'HavenCraft Support Ticket Transcript',
            `Ticket Code: ${currentTicket.ticket_code}`,
            `Subject: ${currentTicket.subject}`,
            `User: ${currentTicket.user_name}`,
            `Created: ${new Date().toLocaleString()}`,
            `Status: ${currentTicket.status}`,
            '=====================================================\n'
        ].join('\n');

        box.querySelectorAll('.msg-row, .chat-msg').forEach(m => {
            const sender = m.querySelector('.msg-author-tag span, .chat-sender')?.innerText.trim()
                || (m.classList.contains('system') ? 'System' : 'Unknown');
            const time = (m.querySelector('.msg-time, .chat-time')?.innerText || '').replace(/[•\s]/g, ' ').trim();
            const bubble = m.querySelector('.msg-bubble, .chat-msg-text') || m;
            const text = (bubble.innerText || '').replace(/Initial Issue Inquiry/g, '').trim();
            if (text) transcriptText += `[${time || 'N/A'}] ${sender}: ${text}\n`;
        });

        const blob = new Blob([transcriptText], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `HavenCraft-Ticket-${currentTicket.ticket_code}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        notify('✓ Transcript downloaded!');
    };

    window.copyTicketMessage = async function (btn) {
        const row = btn && btn.closest('[data-msg-text]');
        if (!row) return;
        const raw = decodeURIComponent(row.getAttribute('data-msg-text') || '');
        const cleaned = raw
            .replace(/\[image:[\s\S]+?\]/g, '')
            .replace(/\[voice:[\s\S]+?\]/g, '')
            .replace(/\[(?:ESCALATE|HANDOVER)[^\]]*\]/gi, '')
            .replace(/@(Admin|Owner|Mod)\b/gi, '')
            .trim() || raw;

        const ok = typeof window.copyText === 'function' ? await window.copyText(cleaned) : false;
        if (ok) notify('✓ Message copied to clipboard!');
    };

    function applyTicketClosedState(existingRating = null) {
        window.cancelVoiceRecording();
        if (currentTicket) {
            currentTicket.status = 'closed';
            saveActiveTicket(currentTicket);
        }

        const closeBtnWrap = document.getElementById('ticketCloseBtnWrap');
        if (closeBtnWrap) closeBtnWrap.innerHTML = '';

        document.querySelector('.ticket-badge')?.classList.add('closed');

        const inputContainer = document.getElementById('ticketInputContainer');
        if (inputContainer && currentTicket) {
            inputContainer.style.display = 'flex';
            mountCsatWidget(inputContainer, { ticket: currentTicket, existingRating, compact: false });
        }
    }

    // ------------------------------------------------------------------------
    // 3. Live Ticket Chat Room & Message Renderer
    // ------------------------------------------------------------------------
    function renderTicketRoom(ticket, messages = []) {
        currentTicket = ticket;

        const newView = document.getElementById('newTicketView');
        const lookupView = document.getElementById('lookupTicketView');
        const chatRoom = document.getElementById('ticketChatRoom');
        const topBar = document.getElementById('supportDeskTopbar');

        if (newView) newView.style.display = 'none';
        if (lookupView) lookupView.style.display = 'none';
        if (topBar) topBar.style.display = 'none';
        if (!chatRoom) return;

        const contactSection = document.getElementById('contactFormSection');
        if (contactSection) {
            contactSection.classList.add('chat-mode');
            contactSection.style.display = 'block';
        }
        document.body.classList.add('support-chat-active');
        chatRoom.style.display = 'block';

        const dedupedMessages = deduplicateMessages(messages);
        const isClosed = ticket.status === 'closed' || dedupedMessages.some(m => classifyMessageKind(m) === 'close');
        if (isClosed) ticket.status = 'closed';
        const existingRating = isClosed ? getSavedRating(ticket.id, dedupedMessages) : null;

        chatRoom.innerHTML = `
            <div class="ticket-chat-card">
                <div class="ticket-chat-header">
                    <div class="ticket-agent-profile">
                        <div class="ticket-agent-avatar-wrap">
                            <img src="../logo/icon.svg" alt="HavenCraft Support" class="ticket-agent-avatar" onerror="this.src='/logo/icon.svg'" />
                            <span class="ticket-agent-status-dot"></span>
                        </div>
                        <div class="ticket-title-area">
                            <div class="ticket-title-top">
                                <h3>HavenCraft Support</h3>
                                <span class="ticket-badge ${isClosed ? 'closed' : ''}">${esc(ticket.ticket_code)}</span>
                            </div>
                            <div class="ticket-meta-subtitle">
                                <span class="ticket-meta-subject" title="${esc(ticket.subject || '')}">${esc(ticket.subject || 'Support Ticket')}</span>
                                <span class="ticket-meta-user">• ${esc(ticket.user_name || '')}</span>
                            </div>
                        </div>
                    </div>
                    <div class="ticket-chat-actions">
                        <button type="button" class="chat-header-btn" onclick="window.downloadTicketTranscript()" title="Download Transcript">
                            <i class="fa-solid fa-download" aria-hidden="true"></i> <span class="btn-text">Transcript</span>
                        </button>
                        <button type="button" class="chat-header-btn" onclick="window.resetTicketChat()" title="New Ticket">
                            <i class="fa-solid fa-plus" aria-hidden="true"></i> <span class="btn-text">New Ticket</span>
                        </button>
                        <span id="ticketCloseBtnWrap">
                            ${!isClosed ? `
                                <button type="button" class="chat-header-btn btn-danger" onclick="window.closeActiveTicket()" title="Close Ticket">
                                    <i class="fa-solid fa-lock" aria-hidden="true"></i> <span class="btn-text">Close</span>
                                </button>
                            ` : ''}
                        </span>
                    </div>
                </div>

                <div class="ticket-messages-box" id="ticketMessagesBox"></div>

                <div class="ticket-input-area" id="ticketInputContainer" style="flex-direction: column; align-items: stretch;">
                    ${isClosed ? '' : `
                        <div class="ticket-attachment-preview" id="ticketAttachPreview" style="display: none;">
                            <div class="attach-thumb-wrap">
                                <img id="ticketAttachThumb" src="" alt="Screenshot" />
                                <button type="button" class="attach-remove-btn" onclick="window.removeTicketAttachment()" title="Remove file">
                                    <i class="fa-solid fa-xmark"></i>
                                </button>
                            </div>
                            <div class="attach-meta">
                                <span class="attach-filename" id="ticketAttachName">screenshot.webp</span>
                                <span class="attach-filesize" id="ticketAttachSize">0 KB</span>
                            </div>
                        </div>
                        <div id="ticketVoiceBar" class="composer-voice-bar" style="display: none;">
                            <div class="voice-bar-left">
                                <span class="voice-record-pulse"></span>
                                <span class="voice-timer" id="voiceRecordingTimer">00:00 / 02:00</span>
                                <div class="voice-bar-visualizer">
                                    <span class="v-wave"></span><span class="v-wave"></span><span class="v-wave"></span><span class="v-wave"></span><span class="v-wave"></span>
                                </div>
                            </div>
                            <div class="voice-bar-actions">
                                <button type="button" class="btn-voice-cancel" onclick="window.cancelVoiceRecording()" title="Cancel recording">
                                    <i class="fa-solid fa-trash"></i> Cancel
                                </button>
                                <button type="button" class="btn-voice-send" onclick="window.stopAndSendVoiceRecording()" title="Send voice message">
                                    <i class="fa-solid fa-paper-plane"></i> Send
                                </button>
                            </div>
                        </div>
                        <div class="ticket-input-row" id="ticketInputRow">
                            <label class="ticket-attach-btn" id="ticketAttachBtn" title="Attach screenshot or image" aria-label="Attach screenshot">
                                <i class="fa-solid fa-paperclip"></i>
                                <input type="file" id="ticketFileInput" accept="image/png,image/jpeg,image/webp,image/gif" style="display: none;" onchange="window.handleTicketFileSelect(event)" />
                            </label>
                            <textarea id="ticketReplyInput" placeholder="Write a message..." rows="1" maxlength="2000"></textarea>
                            <button type="button" class="ticket-mic-btn" id="ticketMicBtn" onclick="window.toggleVoiceRecording()" title="Record voice message (up to 2 mins)" aria-label="Record voice message">
                                <i class="fa-solid fa-microphone"></i>
                            </button>
                            <button type="button" class="ticket-send-btn" id="ticketSendBtn" onclick="window.sendTicketReply()" title="Send" aria-label="Send message">
                                <i class="fa-solid fa-paper-plane" aria-hidden="true"></i>
                            </button>
                        </div>
                    `}
                </div>
            </div>
        `;

        if (isClosed) {
            const inputContainer = document.getElementById('ticketInputContainer');
            mountCsatWidget(inputContainer, { ticket, existingRating, compact: false });
        }

        renderedMsgIds.clear();
        const box = document.getElementById('ticketMessagesBox');
        if (box) {
            box.innerHTML = '';
            let prevMsg = null;
            dedupedMessages.forEach((msg, idx) => {
                const isPlayer = classifyMessageKind(msg) === 'player';
                const isInitial = idx === 0 && isPlayer;
                const isGrouped = Boolean(
                    prevMsg &&
                    prevMsg.sender === msg.sender &&
                    prevMsg.sender_name === msg.sender_name &&
                    msg.created_at && prevMsg.created_at &&
                    (new Date(msg.created_at) - new Date(prevMsg.created_at) < 5 * 60 * 1000)
                );
                appendMessageToBox(box, msg, isGrouped, isInitial);
                prevMsg = msg;
            });
            box.scrollTop = box.scrollHeight;
        }

        const replyInput = document.getElementById('ticketReplyInput');
        if (replyInput) {
            setupChatInputBehaviors(replyInput, {
                maxHeight: 180,
                onSubmit: window.sendTicketReply,
                onImagePaste: stageTicketAttachment,
                onResize: (val) => {
                    const micBtn = document.getElementById('ticketMicBtn');
                    if (micBtn) micBtn.style.display = val && val.trim().length > 0 ? 'none' : 'inline-flex';
                }
            });
        }

        if (activeSubscription) activeSubscription.unsubscribe();
        subscribeTicketRealtime(ticket.id, 'ticket-room', {
            onRemoteMessage: (newMsg) => {
                playChime();
                notify(`💬 Reply from ${newMsg.sender_name || 'Staff'}`);
                const msgBox = document.getElementById('ticketMessagesBox');
                if (msgBox) appendMessageToBox(msgBox, newMsg);
                appendChatCache(ticket.id, newMsg);
            },
            onTicketClosed: () => {
                applyTicketClosedState();
                notify('Ticket closed.');
            }
        }).then(sub => {
            activeSubscription = sub;
        });
    }

    function appendMessageToBox(box, msg, isGroupedOverride = null, isInitial = false) {
        if (!box || !msg) return;

        if (msg.id) {
            const idKey = String(msg.id);
            if (renderedMsgIds.has(idKey)) return;
            renderedMsgIds.add(idKey);
        }

        const kind = classifyMessageKind(msg);
        if (kind === 'close' || kind === 'rating') {
            if (box.querySelector(`.msg-system-pill[data-sys-kind="${kind}"]`)) return;
        }

        const formattedMsg = window.hvnFormatChatMessage(msg.message);
        const div = document.createElement('div');

        if (kind === 'system' || kind === 'close' || kind === 'rating') {
            div.className = 'msg-system-pill chat-msg system';
            div.setAttribute('data-msg-sender', 'system');
            div.setAttribute('data-sys-kind', kind);
            div.innerHTML = `<i class="fa-solid fa-shield-halved" aria-hidden="true"></i> <span>${formattedMsg}</span>`;
            box.appendChild(div);
            box.scrollTop = box.scrollHeight;
            return;
        }

        const isPlayer = kind === 'player';
        let isGrouped = false;
        if (isGroupedOverride !== null) {
            isGrouped = Boolean(isGroupedOverride);
        } else {
            const lastMsgEl = box.querySelector('.msg-row:last-child');
            if (lastMsgEl) {
                const lastSender = lastMsgEl.getAttribute('data-msg-sender');
                const lastAuthor = lastMsgEl.getAttribute('data-msg-author');
                const lastTime = parseInt(lastMsgEl.getAttribute('data-msg-time') || '0', 10);
                const thisTime = msg.created_at ? new Date(msg.created_at).getTime() : Date.now();
                if (lastSender === (isPlayer ? 'player' : 'staff') && lastAuthor === (msg.sender_name || '') && (thisTime - lastTime < 5 * 60 * 1000)) {
                    isGrouped = true;
                }
            }
        }

        const isAi = msg.sender_name === 'HavenCraft AI';
        const rawMsg = String(msg.message || '');
        const isVoice = rawMsg.trim().startsWith('[voice:');
        const isMedia = rawMsg.includes('[image:') || /\.(?:png|jpe?g|gif|webp)/i.test(rawMsg);
        const authorName = isAi
            ? 'HavenCraft Support'
            : (msg.sender_name || (isPlayer ? (currentTicket?.user_name || 'Player') : 'HavenCraft Support'));
        const authorEsc = esc(authorName);
        const timeStr = msg.created_at ? new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
        const timeEpoch = msg.created_at ? new Date(msg.created_at).getTime() : Date.now();

        const avatarInfo = window.getPlayerAvatarUrls ? window.getPlayerAvatarUrls(authorName, 32) : null;
        const playerAvatarUrl = avatarInfo ? avatarInfo.fallback : `https://mc-heads.net/avatar/${encodeURIComponent(String(authorName).replace(/^\.+/, '') || 'Steve')}/32`;
        const staffAvatarUrl = (authorName && !['Staff', 'Admin', 'HavenCraft Support'].includes(authorName) && !isAi)
            ? playerAvatarUrl
            : '../logo/icon.svg';

        const actionsHtml = !isVoice
            ? `<div class="msg-hover-actions"><button type="button" class="msg-action-icon-btn" onclick="window.copyTicketMessage(this)" title="Copy message text" aria-label="Copy message"><i class="fa-regular fa-copy"></i></button></div>`
            : '';

        div.setAttribute('data-msg-sender', isPlayer ? 'player' : 'staff');
        div.setAttribute('data-msg-author', authorName);
        div.setAttribute('data-msg-time', String(timeEpoch));
        div.setAttribute('data-msg-text', encodeURIComponent(rawMsg));
        if (msg.id) div.setAttribute('data-msg-id', esc(String(msg.id)));

        const groupedClass = isGrouped ? 'msg-row-grouped' : '';
        const voiceClass = isVoice ? 'has-voice' : '';
        const mediaClass = isMedia ? 'msg-bubble-media' : '';

        if (isPlayer) {
            div.className = `msg-row msg-row-player ${groupedClass} ${voiceClass}`;
            div.innerHTML = `
                <div class="msg-content-wrapper">
                    ${!isGrouped ? `<div class="msg-author-tag"><span class="msg-time">${timeStr} • </span><span>${authorEsc}</span><i class="fa-solid fa-user" aria-hidden="true"></i></div>` : ''}
                    <div class="msg-bubble-wrap">
                        ${actionsHtml}
                        <div class="msg-bubble msg-bubble-player ${isInitial ? 'msg-bubble-initial' : ''} ${mediaClass}">
                            ${isInitial ? '<div class="initial-badge-pill"><i class="fa-solid fa-ticket" aria-hidden="true"></i> Initial Issue Inquiry</div>' : ''}
                            ${formattedMsg}
                        </div>
                    </div>
                </div>
                ${!isGrouped ? `<img src="${playerAvatarUrl}" alt="${authorEsc}" class="msg-avatar" title="${authorEsc}" loading="lazy" onerror="this.src='https://mc-heads.net/avatar/Steve/32'" />` : '<div class="msg-avatar-spacer"></div>'}
                <span class="chat-sender" style="display:none;">${authorEsc}</span>
                <span class="chat-time" style="display:none;">${timeStr}</span>
            `;
        } else {
            div.className = `msg-row msg-row-staff ${groupedClass} ${voiceClass}`;
            div.innerHTML = `
                ${!isGrouped ? `<img src="${staffAvatarUrl}" alt="${authorEsc}" class="msg-avatar" title="${authorEsc}" loading="lazy" onerror="this.src='../logo/icon.svg'" />` : '<div class="msg-avatar-spacer"></div>'}
                <div class="msg-content-wrapper">
                    ${!isGrouped ? `<div class="msg-author-tag"><i class="fa-solid fa-shield-halved" style="color: var(--primary);" aria-hidden="true"></i><span>${authorEsc}</span><span class="msg-time">• ${timeStr}</span></div>` : ''}
                    <div class="msg-bubble-wrap">
                        <div class="msg-bubble msg-bubble-staff ${mediaClass}">${formattedMsg}</div>
                        ${actionsHtml}
                    </div>
                </div>
                <span class="chat-sender" style="display:none;">${authorEsc}</span>
                <span class="chat-time" style="display:none;">${timeStr}</span>
            `;
        }

        box.appendChild(div);
        box.scrollTop = box.scrollHeight;
    }

    // ------------------------------------------------------------------------
    // 4. Attachments, Voice Recording, Replies & Ticket Closure
    // ------------------------------------------------------------------------
    function getDeskPreviewEls() {
        return {
            previewEl: document.getElementById('ticketAttachPreview'),
            thumbEl: document.getElementById('ticketAttachThumb'),
            nameEl: document.getElementById('ticketAttachName'),
            sizeEl: document.getElementById('ticketAttachSize'),
            fileInput: document.getElementById('ticketFileInput')
        };
    }

    async function stageTicketAttachment(file) {
        stagedAttachment = await stageImageAttachment(file, getDeskPreviewEls());
    }

    window.removeTicketAttachment = function () {
        stagedAttachment = null;
        clearAttachmentPreview(getDeskPreviewEls());
    };

    window.handleTicketFileSelect = function (e) {
        const file = e.target.files?.[0];
        if (file) stageTicketAttachment(file);
    };

    window.sendTicketReply = async function () {
        if (!currentTicket || currentTicket.status === 'closed') return;

        const input = document.getElementById('ticketReplyInput');
        const btn = document.getElementById('ticketSendBtn');
        const text = input ? input.value.trim() : '';

        if (!text && !stagedAttachment) return;
        if (text.length > 2000) {
            notify('Message is too long (max 2000 characters)');
            return;
        }

        let finalMessage = text;
        if (stagedAttachment) {
            const pendingFile = stagedAttachment;
            window.removeTicketAttachment();

            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i>';
            }
            try {
                const url = await uploadTicketAttachment(pendingFile, currentTicket);
                finalMessage = finalMessage ? `${finalMessage}\n${url}` : url;
            } catch (err) {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="fa-solid fa-paper-plane" aria-hidden="true"></i>';
                }
                notify(`Failed to upload screenshot: ${err.message || 'Error'}`);
                return;
            }
        }

        if (!finalMessage) return;

        if (input) {
            input.value = '';
            input.style.height = '42px';
            input.style.overflowY = 'hidden';
        }
        const micBtn = document.getElementById('ticketMicBtn');
        if (micBtn) micBtn.style.display = 'inline-flex';
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-paper-plane" aria-hidden="true"></i>';
        }

        const box = document.getElementById('ticketMessagesBox');
        const optimisticMsg = {
            id: `opt-${Date.now()}`,
            ticket_id: currentTicket.id,
            sender: 'player',
            sender_name: currentTicket.user_name || 'Player',
            message: finalMessage,
            created_at: new Date().toISOString()
        };
        if (box) appendMessageToBox(box, optimisticMsg);
        appendChatCache(currentTicket.id, optimisticMsg);

        try {
            const repRes = await sendTicketReply({ ticket: currentTicket, message: finalMessage });
            if (!repRes.ok && repRes.data?.error?.toLowerCase().includes('closed')) {
                applyTicketClosedState();
                notify('This ticket has been closed.');
            }
        } catch (err) {
            console.error('[Send Reply Error]', err);
        }

        input?.focus();
    };

    let mediaRecorder = null;
    let audioChunks = [];
    let voiceTimerInterval = null;
    let voiceDurationSeconds = 0;
    const MAX_VOICE_SECONDS = 120;

    function updateVoiceTimerDisplay() {
        const timerEl = document.getElementById('voiceRecordingTimer');
        if (timerEl) {
            timerEl.textContent = `${String(Math.floor(voiceDurationSeconds / 60)).padStart(2, '0')}:${String(voiceDurationSeconds % 60).padStart(2, '0')} / 02:00`;
        }
    }

    window.toggleVoiceRecording = function () {
        if (mediaRecorder && mediaRecorder.state === 'recording') {
            window.stopAndSendVoiceRecording();
        } else {
            window.startVoiceRecording();
        }
    };

    window.startVoiceRecording = async function () {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            notify('Voice recording is not supported in this browser.');
            return;
        }
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            audioChunks = [];
            let options = {};
            if (window.MediaRecorder?.isTypeSupported('audio/webm;codecs=opus')) options = { mimeType: 'audio/webm;codecs=opus' };
            else if (window.MediaRecorder?.isTypeSupported('audio/ogg;codecs=opus')) options = { mimeType: 'audio/ogg;codecs=opus' };
            else if (window.MediaRecorder?.isTypeSupported('audio/mp4')) options = { mimeType: 'audio/mp4' };

            mediaRecorder = new MediaRecorder(stream, options);
            mediaRecorder.ondataavailable = (e) => {
                if (e.data && e.data.size > 0) audioChunks.push(e.data);
            };
            mediaRecorder.start(200);
            voiceDurationSeconds = 0;

            document.getElementById('ticketVoiceBar')?.style && (document.getElementById('ticketVoiceBar').style.display = 'flex');
            document.getElementById('ticketInputRow')?.style && (document.getElementById('ticketInputRow').style.display = 'none');
            updateVoiceTimerDisplay();

            clearInterval(voiceTimerInterval);
            voiceTimerInterval = setInterval(() => {
                voiceDurationSeconds += 1;
                updateVoiceTimerDisplay();
                if (voiceDurationSeconds >= MAX_VOICE_SECONDS) window.stopAndSendVoiceRecording();
            }, 1000);
        } catch (err) {
            console.error('Microphone access denied:', err);
            notify(`Microphone error: ${err.message || 'Permission denied.'}`);
        }
    };

    window.cancelVoiceRecording = function () {
        clearInterval(voiceTimerInterval);
        if (mediaRecorder && mediaRecorder.state !== 'inactive') {
            mediaRecorder.onstop = null;
            mediaRecorder.stop();
            mediaRecorder.stream?.getTracks().forEach(t => t.stop());
        }
        mediaRecorder = null;
        audioChunks = [];

        const voiceBar = document.getElementById('ticketVoiceBar');
        const inputRow = document.getElementById('ticketInputRow');
        if (voiceBar) voiceBar.style.display = 'none';
        if (inputRow) inputRow.style.display = 'flex';
    };

    window.stopAndSendVoiceRecording = function () {
        clearInterval(voiceTimerInterval);
        if (!mediaRecorder || mediaRecorder.state === 'inactive') return;

        const durationToSend = voiceDurationSeconds || 1;
        mediaRecorder.onstop = () => {
            mediaRecorder?.stream?.getTracks().forEach(t => t.stop());
            const mimeType = mediaRecorder?.mimeType || 'audio/webm';
            const audioBlob = new Blob(audioChunks, { type: mimeType });

            const voiceBar = document.getElementById('ticketVoiceBar');
            const inputRow = document.getElementById('ticketInputRow');
            if (voiceBar) voiceBar.style.display = 'none';
            if (inputRow) inputRow.style.display = 'flex';

            mediaRecorder = null;
            audioChunks = [];

            if (audioBlob.size > 2 * 1024 * 1024) {
                notify('Voice message exceeds 2MB limit. Please record a shorter message.');
                return;
            }
            sendVoiceMessage(audioBlob, durationToSend);
        };
        mediaRecorder.stop();
    };

    async function sendVoiceMessage(audioBlob, duration) {
        if (!currentTicket || currentTicket.status === 'closed') return;

        const tempBlobUrl = URL.createObjectURL(audioBlob);
        const box = document.getElementById('ticketMessagesBox');
        const optimisticMsg = {
            id: `opt-voice-${Date.now()}`,
            ticket_id: currentTicket.id,
            sender: 'player',
            sender_name: currentTicket.user_name || 'Player',
            message: `[voice:${tempBlobUrl}:${duration}]`,
            created_at: new Date().toISOString()
        };
        if (box) appendMessageToBox(box, optimisticMsg);

        let uploadedVoiceUrl = null;
        try {
            uploadedVoiceUrl = await uploadTicketAttachment(audioBlob, currentTicket);
        } catch (err) {
            console.warn('Voice upload fallback:', err?.message || err);
        }

        const finalVoicePayload = uploadedVoiceUrl
            ? `[voice:${uploadedVoiceUrl}:${duration}]`
            : `[voice:${await fileToBase64(audioBlob)}:${duration}]`;

        appendChatCache(currentTicket.id, { ...optimisticMsg, message: finalVoicePayload });
        await sendTicketReply({ ticket: currentTicket, message: finalVoicePayload }).catch(err => {
            console.error('Send voice reply error:', err);
        });
    }

    window.closeActiveTicket = function () {
        if (!currentTicket) return;

        const performClose = async () => {
            try {
                const { ok, data } = await closeTicketOnServer({ ticket: currentTicket });
                if (ok && data.success) {
                    applyTicketClosedState();
                    const closeMsg = {
                        id: `close-sys-${Date.now()}`,
                        sender: 'system',
                        sender_name: 'HavenCraft System',
                        message: 'Ticket resolved and closed by user.',
                        created_at: new Date().toISOString()
                    };
                    const box = document.getElementById('ticketMessagesBox');
                    if (box) appendMessageToBox(box, closeMsg);
                    appendChatCache(currentTicket.id, closeMsg);
                    notify('Ticket closed successfully.');
                }
            } catch {
                notify('Failed to close ticket.');
            }
        };

        if (typeof window.hvnShowConfirmModal === 'function') {
            window.hvnShowConfirmModal({
                title: 'টিকিট বন্ধ করতে চান?',
                message: 'এই টিকিটটি বন্ধ করলে সাপোর্ট সেশন সমাপ্ত হবে। আপনি কি নিশ্চিত?',
                confirmText: 'হ্যাঁ, টিকিট বন্ধ করুন',
                cancelText: 'না, চালু রাখুন',
                onConfirm: performClose
            });
        } else if (confirm('Are you sure you want to close this ticket?')) {
            performClose();
        }
    };

    function renderNewTicketWizard() {
        const newView = document.getElementById('newTicketView');
        if (!newView || typeof window.hvnMountSupportWizard !== 'function') return;
        window.hvnMountSupportWizard(newView, {
            isWidget: false,
            prefix: '../',
            onTicketCreated: (savedTicket, initialMsg) => {
                saveActiveTicket(savedTicket);
                renderTicketRoom(savedTicket, [initialMsg]);
            }
        });
    }

    window.resetTicketChat = function () {
        window.cancelVoiceRecording();
        clearActiveTicket(currentTicket?.id);
        currentTicket = null;
        stagedAttachment = null;
        if (activeSubscription) {
            activeSubscription.unsubscribe();
            activeSubscription = null;
        }
        document.body.classList.remove('support-chat-active');
        window.toggleLookupView(false);
        renderNewTicketWizard();
    };

    // ------------------------------------------------------------------------
    // 5. Support Desk Boot & Ticket Restoration
    // ------------------------------------------------------------------------
    document.addEventListener('DOMContentLoaded', async () => {
        window.hvnUpdateSupportPageLanguage(localStorage.getItem('hvn_support_lang') || 'bn');

        ['lookupCode', 'lookupEmail'].forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        window.handleLookupTicket();
                    }
                });
                el.addEventListener('input', () => el.classList.remove('is-invalid'));
            }
        });

        const urlParams = new URLSearchParams(window.location.search);
        const queryTicket = urlParams.get('ticket');
        if (queryTicket) {
            renderNewTicketWizard();
            window.toggleLookupView(true);
            const codeInput = document.getElementById('lookupCode');
            if (codeInput) codeInput.value = queryTicket;
            window.handleLookupTicket();
            return;
        }

        const active = getActiveTicket();
        if (active) {
            const cachedMsgs = readChatCache(active.id);
            if (cachedMsgs.length > 0) {
                renderTicketRoom(active, cachedMsgs);
            }

            try {
                const { ok, data } = await fetchTicketViaApi({
                    ticketCode: active.ticket_code,
                    sessionToken: active.sessionToken || '',
                    email: active.user_email || ''
                });
                if (ok && data.success && data.ticket) {
                    if (data.ticket.status === 'closed') {
                        clearActiveTicket(data.ticket.id);
                        renderNewTicketWizard();
                    } else {
                        saveActiveTicket(data.ticket);
                        const fullMsgs = writeChatCache(data.ticket.id, data.messages || []);
                        renderTicketRoom(data.ticket, fullMsgs);
                    }
                    return;
                }
            } catch (err) {
                console.warn('[Support] Active ticket API sync failed:', err?.message || err);
            }

            if (cachedMsgs.length === 0) renderNewTicketWizard();
            return;
        }

        const contactSection = document.getElementById('contactFormSection');
        if (contactSection && window.getComputedStyle(contactSection).display !== 'none') {
            renderNewTicketWizard();
        }
    });
})();
