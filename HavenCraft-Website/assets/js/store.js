/**
 * HavenCraft Store — Interactive Catalog, Spawner Cart, Checkout & PNG Invoice
 * Configuration source: store/config.js (hydrated live from Supabase shop_items)
 */

let currentCurrency = 'BDT';
let currentPaymentMethod = 'bKash';

const spawnerCart = {};

let activeOrder = {
    type: 'rank',
    tier: 'prime',
    itemName: 'Prime Rank',
    priceBDT: '৳220',
    priceUSD: '$1.99',
    selectedRankSpawners: {},
    lastGeneratedSlip: '',
    lastInvoiceData: null
};

const RANK_CONFIG = {};
const SPAWNER_CATALOG = {};
const SPAWNERS_BY_CAT = {};

function getStoreConfig() {
    return window.STORE_CONFIG || {};
}

function escHtml(str) {
    if (typeof window.escapeHtml === 'function') {
        return window.escapeHtml(str);
    }
    return String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function getPaymentNumber(method) {
    const p = getStoreConfig().payment || {};
    if (method === 'Nagad' && p.nagadNumber) return p.nagadNumber;
    if (method === 'bKash' && p.bkashNumber) return p.bkashNumber;
    return p.number || '01516523070';
}

function applyAvatarWithFallback(imgEl, username, size = 64) {
    if (!imgEl) return;
    if (typeof window.getPlayerAvatarUrls === 'function') {
        const urls = window.getPlayerAvatarUrls(username || 'MHF_Steve', size);
        imgEl.onerror = () => {
            if (imgEl.dataset.fallbackStage !== '1' && urls.fallback && imgEl.src !== urls.fallback) {
                imgEl.dataset.fallbackStage = '1';
                imgEl.src = urls.fallback;
            } else if (imgEl.dataset.fallbackStage !== '2' && urls.steve && imgEl.src !== urls.steve) {
                imgEl.dataset.fallbackStage = '2';
                imgEl.src = urls.steve;
            } else {
                imgEl.onerror = null;
            }
        };
        imgEl.dataset.fallbackStage = '0';
        imgEl.src = urls.primary;
        return;
    }

    const cleanName = String(username || '').trim().replace(/^\./, '') || 'MHF_Steve';
    imgEl.onerror = () => {
        imgEl.onerror = null;
        imgEl.src = `https://mc-heads.net/avatar/MHF_Steve/${size}`;
    };
    imgEl.src = `https://mc-heads.net/avatar/${encodeURIComponent(cleanName)}/${size}`;
}

function notifyStore(msg) {
    if (typeof window.showToast === 'function') {
        window.showToast(msg);
    } else {
        alert(msg);
    }
}

function resetObjectMap(obj) {
    Object.keys(obj).forEach(k => delete obj[k]);
}

function initConfigMaps() {
    const config = getStoreConfig();
    resetObjectMap(RANK_CONFIG);
    resetObjectMap(SPAWNER_CATALOG);
    resetObjectMap(SPAWNERS_BY_CAT);

    if (Array.isArray(config.ranks)) {
        config.ranks.forEach(r => {
            RANK_CONFIG[r.id] = {
                id: r.id,
                name: r.fullName || `${r.name} Rank`,
                shortName: r.name,
                badge: r.badge || '',
                icon: r.icon || '💎',
                bdt: r.priceBDT,
                usd: r.priceUSD,
                quotas: r.spawnerQuotas || {},
                perks: r.perks || [],
                commandsInherit: r.commandsInherit || '',
                commands: r.commands || []
            };
        });
    }

    if (Array.isArray(config.spawnerCategories)) {
        config.spawnerCategories.forEach(cat => {
            SPAWNERS_BY_CAT[cat.id] = [];
            if (Array.isArray(cat.items)) {
                cat.items.forEach(item => {
                    const bdt = item.bdtAmount !== undefined ? item.bdtAmount : (cat.bdtAmount !== undefined ? cat.bdtAmount : 25);
                    const usd = item.usdAmount !== undefined ? item.usdAmount : (cat.usdAmount !== undefined ? cat.usdAmount : 0.25);
                    SPAWNER_CATALOG[item.id] = {
                        id: item.id,
                        name: item.name,
                        emoji: item.emoji || '🥚',
                        cat: cat.id,
                        bdt,
                        usd,
                        note: item.note || ''
                    };
                    SPAWNERS_BY_CAT[cat.id].push(item.id);
                });
            }
        });
    }
}

function getRankIconHtml(rankId) {
    const map = { prime: 'fa-gem', legend: 'fa-khanda', elite: 'fa-crown' };
    const cls = map[String(rankId || '').toLowerCase()] || 'fa-gem';
    return `<i class="fa-solid ${cls}" aria-hidden="true"></i>`;
}

function getCategoryIconHtml(catId) {
    const map = {
        basic: 'fa-wheat-awn',
        combat: 'fa-khanda',
        advanced: 'fa-gem',
        special: 'fa-crown'
    };
    const cls = map[String(catId || '').toLowerCase()] || 'fa-cubes';
    return `<i class="fa-solid ${cls}" aria-hidden="true"></i>`;
}

function getPerkIconClass(text) {
    const t = String(text || '').toLowerCase();
    if (t.includes('money') || t.includes('coin')) return 'fa-coins';
    if (t.includes('shard')) return 'fa-gem';
    if (t.includes('home')) return 'fa-house';
    if (t.includes('armor') || t.includes('tool') || t.includes('mace')) return 'fa-shield-halved';
    if (t.includes('shulker') || t.includes('box')) return 'fa-box-open';
    if (t.includes('apple')) return 'fa-apple-whole';
    if (t.includes('spawner')) return 'fa-cubes';
    if (t.includes('claim')) return 'fa-map-location-dot';
    if (t.includes('fly')) return 'fa-feather';
    if (t.includes('suffix') || t.includes('tag')) return 'fa-tag';
    return 'fa-check';
}

function getSpawnerIconHtml(itemId, catId) {
    const id = String(itemId || '').toLowerCase();
    if (id.includes('cow') || id.includes('pig') || id.includes('sheep') || id.includes('chicken') || id.includes('rabbit')) return '<i class="fa-solid fa-paw" aria-hidden="true"></i>';
    if (id.includes('zombie') || id.includes('skeleton') || id.includes('wither')) return '<i class="fa-solid fa-skull" aria-hidden="true"></i>';
    if (id.includes('spider')) return '<i class="fa-solid fa-spider" aria-hidden="true"></i>';
    if (id.includes('blaze') || id.includes('magma')) return '<i class="fa-solid fa-fire-flame-curved" aria-hidden="true"></i>';
    if (id.includes('creeper')) return '<i class="fa-solid fa-burst" aria-hidden="true"></i>';
    if (id.includes('golem') || id.includes('enderman') || id.includes('shulker')) return '<i class="fa-solid fa-cube" aria-hidden="true"></i>';
    if (id.includes('slime') || id.includes('guardian')) return '<i class="fa-solid fa-droplet" aria-hidden="true"></i>';
    if (catId === 'combat') return '<i class="fa-solid fa-khanda" aria-hidden="true"></i>';
    if (catId === 'advanced' || catId === 'special') return '<i class="fa-solid fa-gem" aria-hidden="true"></i>';
    return '<i class="fa-solid fa-cube" aria-hidden="true"></i>';
}

function renderRanksGrid() {
    const container = document.getElementById('ranksGrid');
    const config = getStoreConfig();
    if (!container || !Array.isArray(config.ranks)) return;

    container.innerHTML = config.ranks.map(rank => {
        const safeId = escHtml(rank.id);
        const safeName = escHtml(rank.name);
        const safeBadge = escHtml(rank.badge || '');
        const safePopularText = escHtml(rank.popularBadgeText || 'Most Popular');
        const safeBdt = escHtml(rank.priceBDT);
        const safeUsd = escHtml(rank.priceUSD);

        return `
        <div class="rank-card ${rank.popular ? 'popular' : ''}" data-tier="${safeId}">
            <div class="rank-header">
                <div class="rank-title-row">
                    <h3>${getRankIconHtml(rank.id)} ${safeName}</h3>
                    <div class="rank-badges-inline">
                        ${rank.popular ? `<span class="popular-badge"><i class="fa-solid fa-fire" aria-hidden="true"></i> ${safePopularText}</span>` : ''}
                        ${rank.badge ? `<span class="rank-tier-tag">${safeBadge}</span>` : ''}
                    </div>
                </div>
                <div class="rank-price" data-bdt="${safeBdt}" data-usd="${safeUsd}">
                    <span class="price-val">${currentCurrency === 'USD' ? safeUsd : safeBdt}</span>
                    <span>/ season</span>
                </div>
            </div>
            <div class="rank-body">
                <div class="perk-group">
                    <div class="perk-group-title"><i class="fa-solid fa-gift" aria-hidden="true"></i> Perks</div>
                    <ul class="rank-perks">
                        ${(rank.perks || []).map(p => `<li><span class="perk-icon"><i class="fa-solid ${getPerkIconClass(p.text)}" aria-hidden="true"></i></span> ${p.text}</li>`).join('')}
                    </ul>
                </div>
                <div class="perk-group">
                    <div class="perk-group-title"><i class="fa-solid fa-terminal" aria-hidden="true"></i> Commands</div>
                    ${rank.commandsInherit ? `<p class="cmd-inherit"><i class="fa-solid fa-check-double" aria-hidden="true"></i> ${escHtml(rank.commandsInherit)}</p>` : ''}
                    <div class="command-tags">
                        ${(rank.commands || []).map(cmd => `<span class="cmd-tag">${escHtml(cmd)}</span>`).join('')}
                    </div>
                </div>
            </div>
            <button type="button" class="btn btn-primary rank-buy-btn" onclick="openRankCheckout('${safeId}')">
                <i class="fa-solid fa-cart-shopping" aria-hidden="true"></i> Purchase Rank
            </button>
        </div>
        `;
    }).join('');
}

function renderSpawnerGrid() {
    const container = document.getElementById('spawnerGrid');
    const config = getStoreConfig();
    if (!container || !Array.isArray(config.spawnerCategories)) return;

    container.innerHTML = config.spawnerCategories.map(cat => {
        const safeCatId = escHtml(cat.id);
        const safeCatName = escHtml(cat.name);
        const safeCatBdt = escHtml(cat.priceBDT);
        const safeCatUsd = escHtml(cat.priceUSD);

        return `
        <div class="spawner-category" data-category="${safeCatId}">
            <div class="spawner-cat-header">
                <span class="spawner-cat-icon">${getCategoryIconHtml(cat.id)}</span>
                <div>
                    <h3>${safeCatName}</h3>
                    <div class="spawner-cat-price" data-bdt="${safeCatBdt}" data-usd="${safeCatUsd}">
                        <span class="price-val">${currentCurrency === 'USD' ? safeCatUsd : safeCatBdt}</span> <span class="price-unit">/ per piece</span>
                    </div>
                </div>
            </div>
            <div class="spawner-list">
                ${(cat.items || []).map(item => {
            const bdt = item.bdtAmount !== undefined ? item.bdtAmount : cat.bdtAmount;
            const usd = item.usdAmount !== undefined ? item.usdAmount : cat.usdAmount;
            const qty = spawnerCart[item.id] || 0;
            const hasQty = qty > 0;
            const safeItemId = escHtml(item.id);
            const safeItemName = escHtml(item.name);
            const safeNote = item.note ? `<em class="sp-note">${escHtml(item.note)}</em>` : '';
            return `
                        <div class="spawner-item ${hasQty ? 'has-qty' : ''}" data-id="${safeItemId}" data-name="${safeItemName}" data-cat="${safeCatId}" data-bdt="${escHtml(bdt)}" data-usd="${escHtml(usd)}">
                            <span class="sp-emoji">${getSpawnerIconHtml(item.id, cat.id)}</span>
                            <span class="sp-name">${safeItemName} ${safeNote}</span>
                            <div class="sp-stepper ${hasQty ? 'active' : ''}">
                                <button type="button" class="sp-btn sp-minus" ${!hasQty ? 'disabled' : ''} onclick="changeSpawnerQty('${safeItemId}', -1)" aria-label="Decrease ${safeItemName}">−</button>
                                <span class="sp-qty ${hasQty ? 'has-qty' : ''}" id="qty-${safeItemId}">${qty}</span>
                                <button type="button" class="sp-btn sp-plus" onclick="changeSpawnerQty('${safeItemId}', 1)" aria-label="Increase ${safeItemName}">+</button>
                            </div>
                        </div>
                    `;
        }).join('')}
            </div>
        </div>
        `;
    }).join('');
}

function setCurrency(curr) {
    currentCurrency = curr === 'USD' ? 'USD' : 'BDT';
    try {
        localStorage.setItem('havencraft-currency', currentCurrency);
    } catch (err) {
        console.warn('[Store] Failed to persist currency preference:', err?.message || err);
    }

    const btnBDT = document.getElementById('btnBDT');
    const btnUSD = document.getElementById('btnUSD');
    if (btnBDT) btnBDT.classList.toggle('active', currentCurrency === 'BDT');
    if (btnUSD) btnUSD.classList.toggle('active', currentCurrency === 'USD');

    document.querySelectorAll('[data-bdt][data-usd]').forEach(el => {
        const val = currentCurrency === 'USD' ? el.getAttribute('data-usd') : el.getAttribute('data-bdt');
        const priceVal = el.querySelector('.price-val');
        if (priceVal) {
            priceVal.style.opacity = '0';
            setTimeout(() => {
                priceVal.textContent = val;
                priceVal.style.opacity = '1';
            }, 120);
        }
    });

    updateSpawnerCartBar();
    updateModalPriceDisplay();
}

function switchStoreTab(tab) {
    const ranksSec = document.getElementById('ranks-section');
    const spawnersSec = document.getElementById('spawners-section');
    document.querySelectorAll('.store-tab').forEach(t => {
        const isActive = t.getAttribute('data-tab') === tab;
        t.classList.toggle('active', isActive);
        t.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    if (tab === 'ranks') {
        if (ranksSec) ranksSec.style.display = '';
        if (spawnersSec) spawnersSec.style.display = 'none';
    } else {
        if (ranksSec) ranksSec.style.display = 'none';
        if (spawnersSec) spawnersSec.style.display = '';
    }

    updateSpawnerCartBar();
}

function changeSpawnerQty(spawnerId, delta) {
    const current = spawnerCart[spawnerId] || 0;
    const next = Math.max(0, current + delta);

    if (next === 0) {
        delete spawnerCart[spawnerId];
    } else {
        spawnerCart[spawnerId] = next;
    }

    const qtyEl = document.getElementById(`qty-${spawnerId}`);
    if (qtyEl) {
        qtyEl.textContent = next;
        qtyEl.classList.toggle('has-qty', next > 0);

        const stepper = qtyEl.closest('.sp-stepper');
        if (stepper) {
            stepper.classList.toggle('active', next > 0);
            const minusBtn = stepper.querySelector('.sp-minus');
            if (minusBtn) minusBtn.disabled = next === 0;
        }

        const spawnerItem = qtyEl.closest('.spawner-item') || document.querySelector(`.spawner-item[data-id="${spawnerId}"]`);
        if (spawnerItem) {
            spawnerItem.classList.toggle('has-qty', next > 0);
        }
    }

    updateSpawnerCartBar();
}

function getSpawnerCartTotals() {
    let totalItems = 0;
    let totalBDT = 0;
    let totalUSD = 0;

    Object.entries(spawnerCart).forEach(([id, qty]) => {
        const item = SPAWNER_CATALOG[id];
        if (item && qty > 0) {
            totalItems += qty;
            totalBDT += item.bdt * qty;
            totalUSD += item.usd * qty;
        }
    });

    return { totalItems, totalBDT, totalUSD };
}

function updateSpawnerCartBar() {
    const bar = document.getElementById('spawnerCartBar');
    if (!bar) return;

    const spawnersSection = document.getElementById('spawners-section');
    const isSpawnersTabVisible = spawnersSection && spawnersSection.style.display !== 'none';
    const { totalItems, totalBDT, totalUSD } = getSpawnerCartTotals();

    bar.classList.toggle('visible', totalItems > 0 && Boolean(isSpawnersTabVisible));

    const countEl = document.getElementById('cartTotalCount');
    const priceEl = document.getElementById('cartTotalPrice');
    if (countEl) countEl.textContent = `${totalItems} ${totalItems === 1 ? 'item' : 'items'}`;
    if (priceEl) {
        priceEl.textContent = currentCurrency === 'USD' ? `$${totalUSD.toFixed(2)}` : `৳${totalBDT}`;
    }
}

function clearSpawnerCart() {
    Object.keys(spawnerCart).forEach(id => {
        delete spawnerCart[id];
        const el = document.getElementById(`qty-${id}`);
        if (el) {
            el.textContent = '0';
            el.classList.remove('has-qty');
            const stepper = el.closest('.sp-stepper');
            if (stepper) {
                stepper.classList.remove('active');
                const minusBtn = stepper.querySelector('.sp-minus');
                if (minusBtn) minusBtn.disabled = true;
            }
        }
        const itemEl = document.querySelector(`.spawner-item[data-id="${id}"]`);
        if (itemEl) itemEl.classList.remove('has-qty');
    });
    updateSpawnerCartBar();
}

function openRankCheckout(tier) {
    const config = RANK_CONFIG[tier];
    if (!config) return;

    activeOrder.type = 'rank';
    activeOrder.tier = tier;
    activeOrder.itemName = config.name;
    activeOrder.priceBDT = config.bdt;
    activeOrder.priceUSD = config.usd;
    activeOrder.selectedRankSpawners = {};

    Object.keys(config.quotas).forEach(cat => {
        activeOrder.selectedRankSpawners[cat] = {};
    });

    renderRankSpawnerSelectors(config.quotas);

    const rankStep = document.getElementById('rankSpawnersStep');
    const spawnerStep = document.getElementById('spawnersCartStep');
    const totalQuota = Object.values(config.quotas || {}).reduce((acc, q) => acc + (Number(q) || 0), 0);
    if (rankStep) rankStep.style.display = totalQuota > 0 ? '' : 'none';
    if (spawnerStep) spawnerStep.style.display = 'none';

    setupModalForCheckout();
}

function buildSpawnerOrderItemTitle(fallbackTotal) {
    const entries = Object.keys(spawnerCart)
        .filter(id => (spawnerCart[id] || 0) > 0)
        .map(id => {
            const qty = spawnerCart[id];
            const sp = SPAWNER_CATALOG[id];
            const rawName = sp ? sp.name : id;
            const fullSpawnerName = /spawner/i.test(rawName) ? rawName : `${rawName} Spawner`;
            return qty > 1 ? `${fullSpawnerName} (x${qty})` : fullSpawnerName;
        });

    return entries.length > 0 ? entries.join(', ') : `Custom Spawners (${fallbackTotal || 1} Pcs)`;
}

function openSpawnerCartCheckout() {
    const { totalItems, totalBDT, totalUSD } = getSpawnerCartTotals();
    if (totalItems === 0) {
        notifyStore('Please select at least 1 spawner to checkout!');
        return;
    }

    activeOrder.type = 'spawners';
    activeOrder.tier = 'custom';
    activeOrder.itemName = buildSpawnerOrderItemTitle(totalItems);
    activeOrder.priceBDT = `৳${totalBDT}`;
    activeOrder.priceUSD = `$${totalUSD.toFixed(2)}`;

    renderSpawnerCartReview();

    const rankStep = document.getElementById('rankSpawnersStep');
    const spawnerStep = document.getElementById('spawnersCartStep');
    if (rankStep) rankStep.style.display = 'none';
    if (spawnerStep) spawnerStep.style.display = '';

    setupModalForCheckout();
}

function setupModalForCheckout() {
    updateModalPriceDisplay();

    const modalTitle = document.getElementById('checkoutModalTitle');
    const summaryBar = document.querySelector('#checkoutModal .checkout-summary-bar');
    const form = document.getElementById('checkoutForm');
    const successView = document.getElementById('orderSuccessView');
    if (modalTitle) modalTitle.textContent = 'Store Checkout';
    if (summaryBar) summaryBar.style.display = '';
    if (form) form.style.display = '';
    if (successView) successView.style.display = 'none';

    setPaymentMethod(currentPaymentMethod || 'bKash');

    const overlay = document.getElementById('checkoutModalOverlay');
    if (overlay) overlay.style.display = 'flex';
    document.body.style.overflow = 'hidden';
    document.documentElement.style.overflowX = 'hidden';

    const usernameInput = document.getElementById('playerUsername');
    if (usernameInput && usernameInput.value) {
        handleUsernameInput(usernameInput.value);
    }

    renderStoreTurnstile();
}

let storeTurnstileWidgetId = null;

function renderStoreTurnstile() {
    if (typeof window.mountTurnstileWidget !== 'function') return;
    window.mountTurnstileWidget('storeTurnstile', {
        widgetId: storeTurnstileWidgetId,
        onReady: (id) => { storeTurnstileWidgetId = id; }
    });
}

function closeCheckoutModal() {
    const overlay = document.getElementById('checkoutModalOverlay');
    if (overlay) overlay.style.display = 'none';
    const termsOverlay = document.getElementById('storeTermsModalOverlay');
    if (!termsOverlay || termsOverlay.style.display === 'none') {
        document.body.style.overflow = '';
        document.documentElement.style.overflowX = '';
    }
}

let currentTermsLang = 'bn';

function openStoreTermsModal(e) {
    if (e && e.preventDefault) e.preventDefault();
    if (e && e.stopPropagation) e.stopPropagation();

    const overlay = document.getElementById('storeTermsModalOverlay');
    if (overlay) {
        overlay.style.display = 'flex';
        document.body.style.overflow = 'hidden';
        document.documentElement.style.overflowX = 'hidden';
        const modalBody = overlay.querySelector('.store-terms-modal-body');
        if (modalBody) modalBody.scrollTop = 0;
    }
}

function closeStoreTermsModal() {
    const overlay = document.getElementById('storeTermsModalOverlay');
    if (overlay) overlay.style.display = 'none';

    const checkoutOverlay = document.getElementById('checkoutModalOverlay');
    if (!checkoutOverlay || checkoutOverlay.style.display === 'none') {
        document.body.style.overflow = '';
        document.documentElement.style.overflowX = '';
    }
}

function handleTermsOverlayClick(e) {
    if (e.target && e.target.id === 'storeTermsModalOverlay') {
        closeStoreTermsModal();
    }
}

function switchTermsLang(lang) {
    currentTermsLang = lang === 'en' ? 'en' : 'bn';
    const isBn = currentTermsLang === 'bn';
    const btnBn = document.getElementById('btnTermsLangBn');
    const btnEn = document.getElementById('btnTermsLangEn');
    const paneBn = document.getElementById('termsPaneBn');
    const paneEn = document.getElementById('termsPaneEn');
    const btnAcceptText = document.getElementById('termsBtnAcceptText');
    const btnCloseText = document.getElementById('termsBtnCloseText');
    const linkFullText = document.getElementById('termsFullPageLinkText');
    const subtitle = document.getElementById('storeTermsModalSubtitle');

    if (btnBn) {
        btnBn.classList.toggle('active', isBn);
        btnBn.setAttribute('aria-selected', isBn ? 'true' : 'false');
    }
    if (btnEn) {
        btnEn.classList.toggle('active', !isBn);
        btnEn.setAttribute('aria-selected', !isBn ? 'true' : 'false');
    }
    if (paneBn) paneBn.classList.toggle('active', isBn);
    if (paneEn) paneEn.classList.toggle('active', !isBn);

    if (btnAcceptText) btnAcceptText.textContent = isBn ? 'আমি বুঝেছি ও সম্মত (I Agree)' : 'I Understand & Agree';
    if (btnCloseText) btnCloseText.textContent = isBn ? 'বন্ধ করুন' : 'Close';
    if (linkFullText) linkFullText.textContent = isBn ? 'সম্পূর্ণ ওয়েবসাইট নীতিমালা দেখুন ↗' : 'View Full Website Terms ↗';
    if (subtitle) {
        subtitle.textContent = isBn
            ? 'স্টোর নীতিমালা ও নিয়মাবলী • Official Store Rules'
            : 'Official Store Terms & Purchasing Agreement';
    }
}

function acceptStoreTermsFromModal() {
    const termsCheckbox = document.getElementById('agreeStoreTerms');
    if (termsCheckbox) {
        termsCheckbox.checked = true;
        const wrapper = document.querySelector('.terms-checkbox-wrapper');
        if (wrapper) wrapper.classList.remove('input-error');
    }
    closeStoreTermsModal();
    if (window.showToast) {
        window.showToast(currentTermsLang === 'bn'
            ? '✓ স্টোর শর্তাবলীতে সম্মতি গ্রহণ করা হয়েছে'
            : '✓ Store terms accepted successfully');
    }
}

function updateModalPriceDisplay() {
    const priceText = currentCurrency === 'USD' ? activeOrder.priceUSD : activeOrder.priceBDT;
    const summaryItem = document.getElementById('modalSummaryItem');
    const summaryPrice = document.getElementById('modalSummaryPrice');
    const guideAmount = document.getElementById('guideAmountText');

    if (summaryItem) summaryItem.textContent = activeOrder.itemName;
    if (summaryPrice) summaryPrice.textContent = priceText;
    if (guideAmount) guideAmount.textContent = priceText;
}

function renderRankSpawnerSelectors(quotas) {
    const container = document.getElementById('rankSpawnerSelectors');
    if (!container) return;

    container.innerHTML = '';

    Object.entries(quotas).forEach(([cat, targetCount]) => {
        const safeCat = escHtml(cat);
        const catGroup = document.createElement('div');
        catGroup.className = 'spawner-choice-group';
        catGroup.id = `sp-group-${safeCat}`;

        const catNameFormatted = cat.charAt(0).toUpperCase() + cat.slice(1);
        catGroup.innerHTML = `
            <div class="spawner-choice-header">
                <span><i class="fa-solid fa-egg" aria-hidden="true"></i> ${escHtml(catNameFormatted)} Spawners</span>
                <span class="quota-badge" id="catQuota-${safeCat}">0/${targetCount} Selected</span>
            </div>
            <div class="spawner-choice-chips" id="chips-${safeCat}"></div>
        `;
        container.appendChild(catGroup);

        const chipsContainer = catGroup.querySelector(`#chips-${safeCat}`);
        const availableSpawners = SPAWNERS_BY_CAT[cat] || [];

        availableSpawners.forEach(spawnerId => {
            const spData = SPAWNER_CATALOG[spawnerId];
            if (!spData) return;

            const safeSpId = escHtml(spawnerId);
            const chip = document.createElement('div');
            chip.className = 'spawner-chip';
            chip.id = `rankChip-${safeSpId}`;
            chip.innerHTML = `
                <span>${getSpawnerIconHtml(spawnerId, cat)} ${escHtml(spData.name)}</span>
                <div class="chip-stepper">
                    <button type="button" class="chip-btn chip-minus" onclick="changeRankSpawnerChoice('${safeCat}', '${safeSpId}', -1)">−</button>
                    <span class="chip-count" id="chipQty-${safeSpId}">0</span>
                    <button type="button" class="chip-btn chip-plus" onclick="changeRankSpawnerChoice('${safeCat}', '${safeSpId}', 1)">+</button>
                </div>
            `;
            chipsContainer.appendChild(chip);
        });
    });

    updateRankSpawnersSummary();
}

function changeRankSpawnerChoice(cat, spawnerId, delta) {
    if (!activeOrder.selectedRankSpawners[cat]) {
        activeOrder.selectedRankSpawners[cat] = {};
    }

    const currentQty = activeOrder.selectedRankSpawners[cat][spawnerId] || 0;
    const catQuotas = RANK_CONFIG[activeOrder.tier]?.quotas || {};
    const maxAllowed = catQuotas[cat] || 0;

    let currentCatTotal = 0;
    Object.values(activeOrder.selectedRankSpawners[cat]).forEach(q => { currentCatTotal += q; });

    if (delta > 0 && currentCatTotal >= maxAllowed) {
        notifyStore(`You have already selected ${maxAllowed} ${cat} spawners!`);
        return;
    }

    const nextQty = Math.max(0, currentQty + delta);
    if (nextQty === 0) {
        delete activeOrder.selectedRankSpawners[cat][spawnerId];
    } else {
        activeOrder.selectedRankSpawners[cat][spawnerId] = nextQty;
    }

    const chipQtyEl = document.getElementById(`chipQty-${spawnerId}`);
    const chipEl = document.getElementById(`rankChip-${spawnerId}`);
    if (chipQtyEl) {
        chipQtyEl.textContent = nextQty;
        chipQtyEl.classList.toggle('has-qty', nextQty > 0);
    }
    if (chipEl) chipEl.classList.toggle('selected', nextQty > 0);

    let updatedCatTotal = 0;
    Object.values(activeOrder.selectedRankSpawners[cat]).forEach(q => { updatedCatTotal += q; });
    const catQuotaEl = document.getElementById(`catQuota-${cat}`);
    if (catQuotaEl) {
        catQuotaEl.textContent = `${updatedCatTotal}/${maxAllowed} Selected`;
        catQuotaEl.classList.toggle('complete', updatedCatTotal === maxAllowed);
    }

    updateRankSpawnersSummary();
}

function updateRankSpawnersSummary() {
    const quotaBadge = document.getElementById('spawnerQuotaBadge');
    if (!quotaBadge) return;

    const quotas = RANK_CONFIG[activeOrder.tier]?.quotas || {};
    let totalNeeded = 0;
    let totalChosen = 0;

    Object.entries(quotas).forEach(([cat, target]) => {
        totalNeeded += target;
        if (activeOrder.selectedRankSpawners[cat]) {
            Object.values(activeOrder.selectedRankSpawners[cat]).forEach(q => { totalChosen += q; });
        }
    });

    quotaBadge.textContent = `${totalChosen}/${totalNeeded} Selected`;
    quotaBadge.classList.toggle('complete', totalChosen === totalNeeded && totalNeeded > 0);
}

function getCategoryIcon(catId) {
    const icons = { basic: '🐷', combat: '⚔️', advanced: '💎', special: '👑' };
    return icons[catId] || '📦';
}

function getCategoryDisplayName(catId, configName) {
    if (configName) {
        return configName.replace(/\s*spawners\s*/gi, '').trim();
    }
    return catId.charAt(0).toUpperCase() + catId.slice(1);
}

function formatGroupedSpawnersMarkdown(groupedMap, categories) {
    const lines = [];
    categories.forEach(cat => {
        const group = groupedMap[cat.id];
        if (group && group.items.length > 0) {
            const icon = getCategoryIcon(cat.id);
            const displayName = getCategoryDisplayName(cat.id, cat.name);
            lines.push(`> • ${icon} **${displayName} (${group.total}):** ${group.items.join(', ')}`);
        }
    });

    Object.entries(groupedMap).forEach(([catId, group]) => {
        if (!categories.some(c => c.id === catId) && group.items.length > 0) {
            const icon = getCategoryIcon(catId);
            const displayName = getCategoryDisplayName(catId);
            lines.push(`> • ${icon} **${displayName} (${group.total}):** ${group.items.join(', ')}`);
        }
    });

    return lines.join('\n');
}

function formatSelectedRankSpawnersString() {
    const categories = getStoreConfig().spawnerCategories || [];
    const grouped = {};

    categories.forEach(cat => {
        const list = activeOrder.selectedRankSpawners[cat.id];
        if (!list) return;
        let catTotal = 0;
        const items = [];
        Object.entries(list).forEach(([spawnerId, qty]) => {
            const spData = SPAWNER_CATALOG[spawnerId];
            if (spData && qty > 0) {
                catTotal += qty;
                items.push(`\`${qty}x ${spData.name}\``);
            }
        });
        if (items.length > 0) grouped[cat.id] = { total: catTotal, items };
    });

    return formatGroupedSpawnersMarkdown(grouped, categories);
}

function renderSpawnerCartReview() {
    const container = document.getElementById('cartItemsReview');
    if (!container) return;

    container.innerHTML = '';
    const isUSD = currentCurrency === 'USD';

    Object.entries(spawnerCart).forEach(([id, qty]) => {
        const item = SPAWNER_CATALOG[id];
        if (!item || qty <= 0) return;

        const safeId = escHtml(id);
        const row = document.createElement('div');
        row.className = 'cart-item-row';
        const itemTotal = isUSD ? `$${(item.usd * qty).toFixed(2)}` : `৳${item.bdt * qty}`;

        row.innerHTML = `
            <div class="cart-item-info">
                <span class="cart-item-emoji">${getSpawnerIconHtml(id, item.cat)}</span>
                <span class="cart-item-name">${escHtml(item.name)}</span>
                <span class="cart-item-unit">${isUSD ? `$${item.usd.toFixed(2)}` : `৳${item.bdt}`} each</span>
            </div>
            <div class="cart-item-right">
                <div class="sp-stepper ${qty > 0 ? 'active' : ''}">
                    <button type="button" class="sp-btn sp-minus" ${qty <= 0 ? 'disabled' : ''} onclick="changeSpawnerCartReviewQty('${safeId}', -1)">−</button>
                    <span class="sp-qty ${qty > 0 ? 'has-qty' : ''}">${qty}</span>
                    <button type="button" class="sp-btn sp-plus" onclick="changeSpawnerCartReviewQty('${safeId}', 1)">+</button>
                </div>
                <span class="cart-item-total">${itemTotal}</span>
            </div>
        `;
        container.appendChild(row);
    });
}

function changeSpawnerCartReviewQty(spawnerId, delta) {
    changeSpawnerQty(spawnerId, delta);
    const { totalItems, totalBDT, totalUSD } = getSpawnerCartTotals();

    if (totalItems === 0) {
        closeCheckoutModal();
        return;
    }

    activeOrder.itemName = buildSpawnerOrderItemTitle(totalItems);
    activeOrder.priceBDT = `৳${totalBDT}`;
    activeOrder.priceUSD = `$${totalUSD.toFixed(2)}`;

    updateModalPriceDisplay();
    renderSpawnerCartReview();
}

function formatSpawnerCartItemsString() {
    const categories = getStoreConfig().spawnerCategories || [];
    const grouped = {};

    Object.entries(spawnerCart).forEach(([id, qty]) => {
        if (qty <= 0) return;
        const item = SPAWNER_CATALOG[id];
        if (!item) return;
        const catId = item.cat || 'other';
        if (!grouped[catId]) grouped[catId] = { total: 0, items: [] };
        grouped[catId].total += qty;
        grouped[catId].items.push(`\`${qty}x ${item.name}\``);
    });

    return formatGroupedSpawnersMarkdown(grouped, categories);
}

let avatarDebounceTimer = null;
function handleUsernameInput(rawName) {
    clearTimeout(avatarDebounceTimer);
    avatarDebounceTimer = setTimeout(() => {
        const name = (rawName || '').trim();
        const avatarImg = document.getElementById('playerAvatarPreview');
        if (!avatarImg) return;
        applyAvatarWithFallback(avatarImg, name || 'MHF_Steve', 64);
    }, 250);
}

function setPaymentMethod(method) {
    currentPaymentMethod = method === 'Nagad' ? 'Nagad' : 'bKash';
    const tabBkash = document.getElementById('tabBkash');
    const tabNagad = document.getElementById('tabNagad');
    const paymentBox = document.getElementById('paymentBox');
    const methodLabel = document.getElementById('paymentMethodLabel');
    const guideMethodName = document.getElementById('guideMethodName');
    const paymentNumDisplay = document.getElementById('paymentNumberDisplay');
    const guidePaymentNumber = document.getElementById('guidePaymentNumber');

    if (tabBkash) tabBkash.classList.toggle('active', currentPaymentMethod === 'bKash');
    if (tabNagad) tabNagad.classList.toggle('active', currentPaymentMethod === 'Nagad');

    if (paymentBox) {
        paymentBox.classList.toggle('method-bkash', currentPaymentMethod === 'bKash');
        paymentBox.classList.toggle('method-nagad', currentPaymentMethod === 'Nagad');
    }

    const paymentNum = getPaymentNumber(currentPaymentMethod);
    const accountType = getStoreConfig().payment?.accountType || 'Personal';

    if (methodLabel) methodLabel.textContent = `${currentPaymentMethod} ${accountType}`;
    if (guideMethodName) guideMethodName.textContent = currentPaymentMethod;
    if (paymentNumDisplay) paymentNumDisplay.textContent = paymentNum;
    if (guidePaymentNumber) guidePaymentNumber.textContent = paymentNum;

    const payActionNote = document.querySelector('.pay-action-note');
    if (payActionNote && getStoreConfig().payment?.notice) {
        payActionNote.innerHTML = `<i class="fa-solid fa-circle-info" aria-hidden="true"></i> ${escHtml(getStoreConfig().payment.notice)}`;
    }
}

async function copyPaymentNumber() {
    const paymentNum = getPaymentNumber(currentPaymentMethod);
    const copyPill = document.getElementById('paymentCopyPill');

    const ok = typeof window.copyText === 'function'
        ? await window.copyText(paymentNum)
        : false;

    if (!ok) {
        notifyStore(`Copy number manually: ${paymentNum}`);
        return;
    }

    if (window.showToast) {
        window.showToast(`✓ Number copied: ${paymentNum}`);
    }

    if (copyPill) {
        copyPill.innerHTML = '<span style="color:var(--primary); font-weight:700;"><i class="fa-solid fa-check" aria-hidden="true"></i> Copied!</span>';
        setTimeout(() => {
            copyPill.innerHTML = '<i class="fa-regular fa-copy" aria-hidden="true"></i> <span>Tap to copy</span>';
        }, 2200);
    }
}

function triggerFieldError(element, message) {
    if (!element) return;
    element.classList.add('input-error');
    if (typeof element.focus === 'function') element.focus();
    if (window.showToast) window.showToast(message);
}

async function handleOrderSubmit(e) {
    e.preventDefault();

    const usernameInput = document.getElementById('playerUsername');
    const senderNumberInput = document.getElementById('senderNumber');
    const trxIdInput = document.getElementById('trxId');
    const discordUserInput = document.getElementById('discordUser');

    const username = (usernameInput?.value || '').trim();
    const senderNumber = (senderNumberInput?.value || '').trim();
    const trxId = (trxIdInput?.value || '').trim();
    const discordUser = (discordUserInput?.value || '').trim();

    const isBedrock = username.startsWith('.');
    const detectedPlatform = isBedrock ? 'Bedrock' : 'Java';

    if (!/^[a-zA-Z0-9_.]{3,24}$/.test(username)) {
        triggerFieldError(usernameInput, '⚠️ Please enter a valid Minecraft username (3-24 characters)');
        return;
    }

    if (activeOrder.type === 'rank') {
        const quotas = RANK_CONFIG[activeOrder.tier]?.quotas || {};
        for (const [cat, targetCount] of Object.entries(quotas)) {
            let catCount = 0;
            if (activeOrder.selectedRankSpawners[cat]) {
                Object.values(activeOrder.selectedRankSpawners[cat]).forEach(q => { catCount += q; });
            }
            if (catCount < targetCount) {
                const spawnerStep = document.getElementById('rankSpawnersStep');
                if (spawnerStep) spawnerStep.scrollIntoView({ behavior: 'smooth', block: 'center' });
                if (window.showToast) {
                    window.showToast(`⚠️ Please select all ${targetCount} ${cat} spawners before submitting.`);
                }
                return;
            }
        }
    }

    if (!senderNumber || senderNumber.length < 4) {
        triggerFieldError(senderNumberInput, '⚠️ Please enter the payment sender mobile number');
        return;
    }
    if (!trxId || trxId.length < 4) {
        triggerFieldError(trxIdInput, '⚠️ Please enter the Transaction ID (TrxID)');
        return;
    }
    if (!discordUser || discordUser.length < 2) {
        triggerFieldError(discordUserInput, '⚠️ Please enter your Discord username');
        return;
    }

    const agreeTerms = document.getElementById('agreeStoreTerms');
    const termsWrapper = document.querySelector('.terms-checkbox-wrapper');
    if (agreeTerms && !agreeTerms.checked) {
        triggerFieldError(termsWrapper || agreeTerms, '⚠️ Please check the box to accept the Store Terms');
        return;
    }

    let captchaToken = '';
    const turnstileInput = document.querySelector('#checkoutForm [name="cf-turnstile-response"]') || document.querySelector('[name="cf-turnstile-response"]');
    if (turnstileInput && turnstileInput.value) {
        captchaToken = turnstileInput.value;
    } else if (window.turnstile && typeof window.turnstile.getResponse === 'function') {
        try {
            captchaToken = (storeTurnstileWidgetId !== null ? window.turnstile.getResponse(storeTurnstileWidgetId) : '')
                || window.turnstile.getResponse('#storeTurnstile')
                || window.turnstile.getResponse()
                || '';
        } catch (err) {
            console.warn('[Store] Turnstile getResponse failed:', err?.message || err);
        }
    }

    if (!captchaToken) {
        const captchaBox = document.getElementById('storeTurnstile');
        triggerFieldError(captchaBox || agreeTerms, '⚠️ Please complete the security check (Captcha)');
        return;
    }

    const submitBtn = document.getElementById('btnSubmitOrder');
    const btnText = document.getElementById('btnSubmitText');
    const btnSpinner = document.getElementById('btnSubmitSpinner');

    if (submitBtn) submitBtn.disabled = true;
    if (btnText) btnText.style.display = 'none';
    if (btnSpinner) btnSpinner.style.display = 'inline-flex';

    const selectedSpawnersStr = activeOrder.type === 'rank' ? formatSelectedRankSpawnersString() : '';
    const spawnerCartStr = activeOrder.type === 'spawners' ? formatSpawnerCartItemsString() : '';
    const cartItems = activeOrder.type === 'spawners'
        ? Object.entries(spawnerCart)
            .filter(([, qty]) => qty > 0)
            .map(([id, qty]) => ({ id, qty }))
        : [];
    const activePrice = currentCurrency === 'USD' ? activeOrder.priceUSD : activeOrder.priceBDT;

    const payload = {
        username,
        platform: detectedPlatform,
        itemType: activeOrder.type,
        itemId: activeOrder.tier,
        itemName: activeOrder.itemName,
        price: activePrice,
        currency: currentCurrency,
        paymentMethod: currentPaymentMethod,
        senderNumber,
        trxId,
        discordUser,
        selectedRankSpawners: activeOrder.type === 'rank' ? activeOrder.selectedRankSpawners : undefined,
        selectedSpawners: selectedSpawnersStr,
        cartItems,
        itemsList: spawnerCartStr,
        agreedToTerms: true,
        termsTimestamp: new Date().toISOString(),
        captchaToken
    };

    const resetTurnstile = () => {
        if (window.turnstile && typeof window.turnstile.reset === 'function') {
            try {
                if (storeTurnstileWidgetId !== null) window.turnstile.reset(storeTurnstileWidgetId);
                else window.turnstile.reset('#storeTurnstile');
            } catch (err) {
                console.warn('[Store] Turnstile reset warning:', err?.message || err);
            }
        }
    };

    try {
        const res = await fetch('/api/order', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json().catch(() => ({}));

        if (res.ok && data.success && data.orderId) {
            const verifiedPayload = {
                ...payload,
                itemName: data.itemName || payload.itemName,
                price: data.price || payload.price
            };
            showOrderSuccess(verifiedPayload, data.orderId);
        } else {
            notifyStore(data.error || 'Failed to submit order. Please check your inputs and try again.');
            resetTurnstile();
        }
    } catch (err) {
        console.error('Store order error:', err);
        notifyStore('Unable to connect to order service. Please check your network or contact Discord support.');
        resetTurnstile();
    } finally {
        if (submitBtn) submitBtn.disabled = false;
        if (btnText) btnText.style.display = '';
        if (btnSpinner) btnSpinner.style.display = 'none';
    }
}

function getStructuredOrderSpawners() {
    const categories = getStoreConfig().spawnerCategories || [];
    const groups = [];

    if (activeOrder.type === 'rank') {
        categories.forEach(cat => {
            const list = activeOrder.selectedRankSpawners[cat.id];
            if (!list) return;
            let catTotal = 0;
            const items = [];
            Object.entries(list).forEach(([spawnerId, qty]) => {
                const spData = SPAWNER_CATALOG[spawnerId];
                if (spData && qty > 0) {
                    catTotal += qty;
                    items.push({ id: spawnerId, name: spData.name, emoji: spData.emoji || '🥚', qty });
                }
            });
            if (items.length > 0) {
                groups.push({
                    catId: cat.id,
                    icon: getCategoryIcon(cat.id),
                    displayName: getCategoryDisplayName(cat.id, cat.name),
                    total: catTotal,
                    items
                });
            }
        });
    } else if (activeOrder.type === 'spawners') {
        const grouped = {};
        Object.entries(spawnerCart).forEach(([id, qty]) => {
            if (qty <= 0) return;
            const item = SPAWNER_CATALOG[id];
            if (!item) return;
            const catId = item.cat || 'other';
            if (!grouped[catId]) grouped[catId] = { total: 0, items: [] };
            grouped[catId].total += qty;
            grouped[catId].items.push({ id, name: item.name, emoji: item.emoji || '🥚', qty });
        });

        categories.forEach(cat => {
            const g = grouped[cat.id];
            if (g && g.items.length > 0) {
                groups.push({
                    catId: cat.id,
                    icon: getCategoryIcon(cat.id),
                    displayName: getCategoryDisplayName(cat.id, cat.name),
                    total: g.total,
                    items: g.items
                });
            }
        });

        Object.entries(grouped).forEach(([catId, g]) => {
            if (!categories.some(c => c.id === catId) && g.items.length > 0) {
                groups.push({
                    catId,
                    icon: getCategoryIcon(catId),
                    displayName: getCategoryDisplayName(catId),
                    total: g.total,
                    items: g.items
                });
            }
        });
    }

    return groups;
}

function renderInvoiceSpawnersUI(containerEl, spawnerGroups, fallbackMarkdown) {
    if (!containerEl) return;
    containerEl.innerHTML = '';

    if (Array.isArray(spawnerGroups) && spawnerGroups.length > 0) {
        spawnerGroups.forEach(group => {
            const groupDiv = document.createElement('div');
            groupDiv.className = 'inv-spawner-group';

            const catSpan = document.createElement('span');
            catSpan.className = 'inv-spawner-cat';
            catSpan.innerHTML = `${getCategoryIconHtml(group.catId)} `;
            catSpan.appendChild(document.createTextNode(`${group.displayName} `));

            const countSpan = document.createElement('span');
            countSpan.className = 'inv-spawner-cat-count';
            countSpan.textContent = `(${group.total})`;
            catSpan.appendChild(countSpan);

            const tagsDiv = document.createElement('div');
            tagsDiv.className = 'inv-spawner-tags';

            group.items.forEach(item => {
                const tag = document.createElement('span');
                tag.className = 'inv-spawner-tag';
                tag.innerHTML = `${getSpawnerIconHtml(item.id, group.catId)} `;
                tag.appendChild(document.createTextNode(`${item.name} `));

                const qtyStrong = document.createElement('strong');
                qtyStrong.textContent = `×${item.qty}`;
                tag.appendChild(qtyStrong);

                tagsDiv.appendChild(tag);
            });

            groupDiv.appendChild(catSpan);
            groupDiv.appendChild(tagsDiv);
            containerEl.appendChild(groupDiv);
        });
        return;
    }

    if (fallbackMarkdown) {
        const cleanLines = String(fallbackMarkdown)
            .split('\n')
            .map(l => l.replace(/^[>\s•*-]+/, '').replace(/\*\*/g, '').replace(/`/g, '').trim())
            .filter(Boolean);

        cleanLines.forEach(line => {
            const groupDiv = document.createElement('div');
            groupDiv.className = 'inv-spawner-group';
            const textSpan = document.createElement('span');
            textSpan.className = 'inv-spawner-cat';
            textSpan.textContent = line;
            groupDiv.appendChild(textSpan);
            containerEl.appendChild(groupDiv);
        });
    }
}

function formatInvoiceDate(dateObj) {
    try {
        return dateObj.toLocaleString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true
        });
    } catch {
        return dateObj.toISOString().slice(0, 16).replace('T', ' ');
    }
}

function showOrderSuccess(payload, orderId) {
    const modalTitle = document.getElementById('checkoutModalTitle');
    const summaryBar = document.querySelector('#checkoutModal .checkout-summary-bar');
    const checkoutModal = document.getElementById('checkoutModal');
    const form = document.getElementById('checkoutForm');
    const successView = document.getElementById('orderSuccessView');

    if (modalTitle) modalTitle.textContent = 'Order Receipt';
    if (summaryBar) summaryBar.style.display = 'none';
    if (form) form.style.display = 'none';
    if (successView) successView.style.display = 'block';
    if (checkoutModal) checkoutModal.scrollTop = 0;

    const isBedrock = payload.username.startsWith('.') || (typeof payload.platform === 'string' && payload.platform.toLowerCase() === 'bedrock');
    const displayPlatform = isBedrock ? 'Bedrock' : 'Java';
    const issuedAt = formatInvoiceDate(new Date());
    const spawnerGroups = getStructuredOrderSpawners();
    const spawnersTitle = activeOrder.type === 'spawners' ? 'Selected Spawners' : 'Included Spawners';
    const itemTypeLabel = activeOrder.type === 'spawners' ? 'In-Game Spawner Package' : 'Seasonal Rank Package';

    const idEl = document.getElementById('receiptOrderId');
    const dateEl = document.getElementById('receiptDate');
    const avatarEl = document.getElementById('receiptPlayerAvatar');
    const playerEl = document.getElementById('receiptPlayer');
    const editionEl = document.getElementById('receiptEditionBadge');
    const discordEl = document.getElementById('receiptDiscord');
    const methodEl = document.getElementById('receiptMethod');
    const senderEl = document.getElementById('receiptSender');
    const trxEl = document.getElementById('receiptTrx');
    const itemEl = document.getElementById('receiptItem');
    const itemTypeEl = document.getElementById('receiptItemType');
    const amountEl = document.getElementById('receiptAmount');
    const totalAmountEl = document.getElementById('receiptTotalAmount');
    const spawnersRow = document.getElementById('receiptSpawnersRow');
    const spawnersLabelEl = document.getElementById('receiptSpawnersLabel');
    const spawnersText = document.getElementById('receiptSpawnersText');

    if (idEl) idEl.textContent = `#${orderId}`;
    if (dateEl) dateEl.textContent = issuedAt;
    if (avatarEl) applyAvatarWithFallback(avatarEl, payload.username, 64);
    if (playerEl) playerEl.textContent = payload.username;
    if (editionEl) editionEl.textContent = `${displayPlatform} Edition`;
    if (discordEl) discordEl.textContent = payload.discordUser || '-';
    if (methodEl) methodEl.textContent = `${payload.paymentMethod} Personal`;
    if (senderEl) senderEl.textContent = payload.senderNumber || '-';
    if (trxEl) trxEl.textContent = payload.trxId;
    if (itemEl) itemEl.textContent = payload.itemName;
    if (itemTypeEl) itemTypeEl.textContent = itemTypeLabel;
    if (amountEl) amountEl.textContent = payload.price;
    if (totalAmountEl) totalAmountEl.textContent = payload.price;

    if (spawnersLabelEl) {
        const span = spawnersLabelEl.querySelector('span');
        if (span) span.textContent = spawnersTitle;
        else spawnersLabelEl.textContent = spawnersTitle;
    }

    const extraSpawners = payload.selectedSpawners || payload.itemsList;
    if (spawnersRow && spawnersText) {
        if (spawnerGroups.length > 0 || extraSpawners) {
            spawnersRow.style.display = 'flex';
            renderInvoiceSpawnersUI(spawnersText, spawnerGroups, extraSpawners);
        } else {
            spawnersRow.style.display = 'none';
        }
    }

    const cleanSpawnerLines = spawnerGroups.map(g => {
        const itemsText = g.items.map(i => `${i.qty}x ${i.name}`).join(', ');
        return `  • ${g.displayName} (${g.total}): ${itemsText}`;
    });

    activeOrder.lastInvoiceData = {
        orderId: `#${orderId}`,
        rawOrderId: orderId,
        issuedAt,
        username: payload.username,
        platform: `${displayPlatform} Edition`,
        discordUser: payload.discordUser || '-',
        itemName: payload.itemName,
        itemTypeLabel,
        price: payload.price,
        paymentMethod: `${payload.paymentMethod} Personal`,
        senderNumber: payload.senderNumber || '-',
        trxId: payload.trxId,
        spawnersTitle,
        spawnerGroups
    };

    activeOrder.lastGeneratedSlip = [
        `HAVENCRAFT STORE | OFFICIAL INVOICE (#${orderId})`,
        `Status  : Pending Payment Verification (Manual Check)`,
        `Date    : ${issuedAt}`,
        `--------------------------------------------`,
        `Player  : ${payload.username} (${displayPlatform} Edition)`,
        `Discord : ${payload.discordUser}`,
        `Package : ${payload.itemName}`,
        `Amount  : ${payload.price}`,
        `Payment : ${payload.paymentMethod} Personal (Sender: ${payload.senderNumber})`,
        `TrxID   : ${payload.trxId}`,
        `Terms   : Accepted (Strict No-Refund Policy)`,
        ...(cleanSpawnerLines.length > 0 ? [`${spawnersTitle}:`, ...cleanSpawnerLines] : []),
        `--------------------------------------------`,
        `Note: Staff manually verifies payment & TrxID before in-game delivery.`
    ].join('\n');

    if (typeof window.copyText === 'function') {
        window.copyText(activeOrder.lastGeneratedSlip);
    }

    setTimeout(() => {
        downloadInvoiceImage({ isAuto: true });
    }, 450);

    if (activeOrder.type === 'spawners') {
        clearSpawnerCart();
    }
}

async function copyReceiptSlip() {
    if (!activeOrder.lastGeneratedSlip) return;
    const copyBtn = document.getElementById('btnCopyInvoice');

    const ok = typeof window.copyText === 'function'
        ? await window.copyText(activeOrder.lastGeneratedSlip)
        : false;

    if (!ok) {
        notifyStore('Could not access the clipboard automatically. Copy the details from the receipt above.');
        return;
    }

    if (window.showToast) {
        window.showToast('✓ Invoice copied to clipboard!');
    }

    if (copyBtn) {
        copyBtn.classList.add('copied-state');
        const span = copyBtn.querySelector('span');
        const icon = copyBtn.querySelector('i');
        const prevText = span ? span.textContent : 'Copy Receipt Slip';
        if (span) span.textContent = 'Copied!';
        if (icon) icon.className = 'fa-solid fa-check';
        setTimeout(() => {
            copyBtn.classList.remove('copied-state');
            if (span) span.textContent = prevText;
            if (icon) icon.className = 'fa-regular fa-copy';
        }, 2000);
    }
}

function drawRoundedRect(ctx, x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + w, y, x + w, y + h, radius);
    ctx.arcTo(x + w, y + h, x, y + h, radius);
    ctx.arcTo(x, y + h, x, y, radius);
    ctx.arcTo(x, y, x + w, y, radius);
    ctx.closePath();
}

function loadCanvasImage(src) {
    return new Promise((resolve) => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.onload = () => resolve(img);
        img.onerror = () => resolve(null);
        img.src = src;
    });
}

function readStoreThemeColors() {
    const styles = getComputedStyle(document.documentElement);
    const pick = (token, fallback) => styles.getPropertyValue(token).trim() || fallback;
    return {
        bg: pick('--bg', '#0B131B'),
        surface: pick('--surface', '#111D29'),
        surfaceElevated: pick('--surface-elevated', '#172636'),
        surfaceRecessed: pick('--surface-recessed', '#080E14'),
        border: pick('--border', '#223447'),
        borderStrong: pick('--border-strong', '#324A63'),
        primary: pick('--primary', '#14B8A6'),
        primaryDim: pick('--primary-dim', 'rgba(20, 184, 166, 0.14)'),
        accent: pick('--accent', '#F59E0B'),
        accentHover: pick('--accent-hover', '#FBBF24'),
        accentDim: pick('--accent-dim', 'rgba(245, 158, 11, 0.14)'),
        text: pick('--text', '#F1F5F9'),
        textSecondary: pick('--text-secondary', '#B8C7D6'),
        textMuted: pick('--text-muted', '#8FA3B8'),
        fontSans: pick('--font-body', "'Source Sans 3', 'Hind Siliguri', sans-serif"),
        fontHeading: pick('--font-heading', "'Chakra Petch', 'Hind Siliguri', sans-serif"),
        fontMono: pick('--font-mono', "'Fira Code', monospace")
    };
}

async function downloadInvoiceImage(options = {}) {
    const isAuto = Boolean(options && options.isAuto);
    const inv = activeOrder.lastInvoiceData;
    if (!inv) return;

    const downloadBtn = document.getElementById('btnDownloadInvoice');
    const downloadSpan = downloadBtn ? downloadBtn.querySelector('span') : null;
    const prevDownloadText = downloadSpan ? downloadSpan.textContent : 'Save PNG Invoice';
    if (downloadBtn) {
        downloadBtn.disabled = true;
        if (downloadSpan) downloadSpan.textContent = 'Generating...';
    }

    try {
        const theme = readStoreThemeColors();
        const spawnerGroups = inv.spawnerGroups || [];
        const hasSpawners = spawnerGroups.length > 0;
        const spawnerBlockHeight = hasSpawners ? (44 + spawnerGroups.length * 36) : 0;

        const width = 720;
        const height = 560 + spawnerBlockHeight;
        const scale = 2;

        const canvas = document.createElement('canvas');
        canvas.width = width * scale;
        canvas.height = height * scale;
        const ctx = canvas.getContext('2d');
        ctx.scale(scale, scale);

        ctx.fillStyle = theme.bg;
        ctx.fillRect(0, 0, width, height);

        drawRoundedRect(ctx, 16, 16, width - 32, height - 32, 12);
        ctx.fillStyle = theme.surface;
        ctx.fill();
        ctx.lineWidth = 1;
        ctx.strokeStyle = theme.borderStrong;
        ctx.stroke();

        const logoImg = await loadCanvasImage('../logo/logo.png');
        drawRoundedRect(ctx, 40, 38, 42, 42, 8);
        if (logoImg) {
            ctx.fillStyle = theme.surfaceRecessed;
            ctx.fill();
            ctx.drawImage(logoImg, 43, 41, 36, 36);
        } else {
            ctx.fillStyle = theme.primaryDim;
            ctx.fill();
            ctx.fillStyle = theme.primary;
            ctx.font = `700 18px ${theme.fontHeading}`;
            ctx.fillText('HC', 48, 65);
        }

        ctx.fillStyle = theme.text;
        ctx.font = `700 20px ${theme.fontHeading}`;
        ctx.fillText('HavenCraft Store', 94, 57);

        ctx.fillStyle = theme.textMuted;
        ctx.font = `600 12px ${theme.fontSans}`;
        ctx.fillText('OFFICIAL PAYMENT INVOICE', 94, 75);

        const statusText = 'VERIFICATION PENDING';
        ctx.font = `700 11px ${theme.fontMono}`;
        const statusW = ctx.measureText(statusText).width + 32;
        const statusX = width - 40 - statusW;
        drawRoundedRect(ctx, statusX, 44, statusW, 28, 6);
        ctx.fillStyle = theme.accentDim;
        ctx.fill();
        ctx.strokeStyle = theme.accent;
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(statusX + 14, 58, 4, 0, Math.PI * 2);
        ctx.fillStyle = theme.accent;
        ctx.fill();

        ctx.fillStyle = theme.accentHover;
        ctx.fillText(statusText, statusX + 24, 62);

        ctx.beginPath();
        ctx.moveTo(40, 94);
        ctx.lineTo(width - 40, 94);
        ctx.strokeStyle = theme.border;
        ctx.stroke();

        drawRoundedRect(ctx, 40, 108, width - 80, 52, 8);
        ctx.fillStyle = theme.surfaceRecessed;
        ctx.fill();
        ctx.strokeStyle = theme.border;
        ctx.stroke();

        ctx.fillStyle = theme.textMuted;
        ctx.font = `600 11px ${theme.fontMono}`;
        ctx.fillText('INVOICE ID', 56, 126);
        ctx.fillText('ISSUE DATE', 242, 126);
        ctx.textAlign = 'right';
        ctx.fillText('DELIVERY MODE', width - 56, 126);
        ctx.textAlign = 'left';

        ctx.fillStyle = theme.accent;
        ctx.font = `600 14px ${theme.fontMono}`;
        ctx.fillText(inv.orderId, 56, 146);

        ctx.fillStyle = theme.text;
        ctx.font = `600 12.5px ${theme.fontSans}`;
        ctx.fillText(inv.issuedAt, 242, 146);

        ctx.textAlign = 'right';
        ctx.fillText('In-Game Delivery', width - 56, 146);
        ctx.textAlign = 'left';

        drawRoundedRect(ctx, 40, 174, width - 80, 64, 8);
        ctx.fillStyle = 'rgba(245, 158, 11, 0.08)';
        ctx.fill();
        ctx.strokeStyle = theme.accent;
        ctx.stroke();

        ctx.fillStyle = theme.accentHover;
        ctx.font = `700 13px ${theme.fontHeading}`;
        ctx.fillText('Manual Payment Verification in Progress', 56, 196);

        ctx.fillStyle = theme.textSecondary;
        ctx.font = `500 12px ${theme.fontSans}`;
        ctx.fillText('Staff verify your bKash/Nagad payment & TrxID before delivering in-game items.', 56, 214);
        ctx.fillStyle = theme.textMuted;
        ctx.font = `500 11.5px ${theme.fontSans}`;
        ctx.fillText('Keep this invoice and open a ticket on dc.havencraft.pro for quick assistance.', 56, 229);

        const cardY = 252;
        const cardW = (width - 92) / 2;
        const cardH = 104;

        drawRoundedRect(ctx, 40, cardY, cardW, cardH, 8);
        ctx.fillStyle = theme.surfaceRecessed;
        ctx.fill();
        ctx.strokeStyle = theme.border;
        ctx.stroke();

        ctx.fillStyle = theme.textMuted;
        ctx.font = `600 11px ${theme.fontMono}`;
        ctx.fillText('PLAYER DETAILS', 56, cardY + 22);

        ctx.fillStyle = theme.textSecondary;
        ctx.font = `500 12px ${theme.fontSans}`;
        ctx.fillText('Username:', 56, cardY + 46);
        ctx.fillText('Platform:', 56, cardY + 68);
        ctx.fillText('Discord:', 56, cardY + 90);

        ctx.textAlign = 'right';
        ctx.font = `700 12.5px ${theme.fontSans}`;
        ctx.fillStyle = theme.text;
        ctx.fillText(inv.username, 40 + cardW - 16, cardY + 46);
        ctx.fillStyle = theme.primary;
        ctx.fillText(inv.platform, 40 + cardW - 16, cardY + 68);
        ctx.fillStyle = theme.text;
        ctx.fillText(inv.discordUser, 40 + cardW - 16, cardY + 90);
        ctx.textAlign = 'left';

        const rightCardX = 40 + cardW + 12;
        drawRoundedRect(ctx, rightCardX, cardY, cardW, cardH, 8);
        ctx.fillStyle = theme.surfaceRecessed;
        ctx.fill();
        ctx.strokeStyle = theme.border;
        ctx.stroke();

        ctx.fillStyle = theme.textMuted;
        ctx.font = `600 11px ${theme.fontMono}`;
        ctx.fillText('PAYMENT DETAILS', rightCardX + 16, cardY + 22);

        ctx.fillStyle = theme.textSecondary;
        ctx.font = `500 12px ${theme.fontSans}`;
        ctx.fillText('Method:', rightCardX + 16, cardY + 46);
        ctx.fillText('Sender No:', rightCardX + 16, cardY + 68);
        ctx.fillText('TrxID:', rightCardX + 16, cardY + 90);

        ctx.textAlign = 'right';
        ctx.fillStyle = theme.text;
        ctx.font = `700 12.5px ${theme.fontSans}`;
        ctx.fillText(inv.paymentMethod, rightCardX + cardW - 16, cardY + 46);
        ctx.fillStyle = theme.textSecondary;
        ctx.fillText(inv.senderNumber, rightCardX + cardW - 16, cardY + 68);
        ctx.fillStyle = theme.accent;
        ctx.font = `600 12.5px ${theme.fontMono}`;
        ctx.fillText(inv.trxId, rightCardX + cardW - 16, cardY + 90);
        ctx.textAlign = 'left';

        const tableY = 370;
        const tableH = 128 + spawnerBlockHeight;
        drawRoundedRect(ctx, 40, tableY, width - 80, tableH, 8);
        ctx.fillStyle = theme.surfaceRecessed;
        ctx.fill();
        ctx.strokeStyle = theme.borderStrong;
        ctx.stroke();

        ctx.fillStyle = theme.surfaceElevated;
        ctx.fillRect(40, tableY, width - 80, 32);
        ctx.beginPath();
        ctx.moveTo(40, tableY + 32);
        ctx.lineTo(width - 40, tableY + 32);
        ctx.strokeStyle = theme.border;
        ctx.stroke();

        ctx.fillStyle = theme.textMuted;
        ctx.font = `600 11px ${theme.fontMono}`;
        ctx.fillText('ORDER SUMMARY', 56, tableY + 20);
        ctx.textAlign = 'right';
        ctx.fillText('AMOUNT', width - 56, tableY + 20);
        ctx.textAlign = 'left';

        ctx.fillStyle = theme.text;
        ctx.font = `700 14.5px ${theme.fontHeading}`;
        const displayItemTitle = inv.itemName.length > 52 ? inv.itemName.slice(0, 49) + '...' : inv.itemName;
        ctx.fillText(displayItemTitle, 56, tableY + 56);

        ctx.fillStyle = theme.textMuted;
        ctx.font = `500 12px ${theme.fontSans}`;
        ctx.fillText(inv.itemTypeLabel, 56, tableY + 74);

        ctx.textAlign = 'right';
        ctx.fillStyle = theme.accent;
        ctx.font = `700 16px ${theme.fontHeading}`;
        ctx.fillText(inv.price, width - 56, tableY + 62);
        ctx.textAlign = 'left';

        let currentY = tableY + 88;
        if (hasSpawners) {
            ctx.beginPath();
            ctx.moveTo(56, currentY);
            ctx.lineTo(width - 56, currentY);
            ctx.strokeStyle = theme.border;
            ctx.stroke();

            currentY += 22;
            ctx.fillStyle = theme.textMuted;
            ctx.font = `600 11px ${theme.fontMono}`;
            ctx.fillText(inv.spawnersTitle.toUpperCase(), 56, currentY);
            currentY += 12;

            spawnerGroups.forEach(group => {
                drawRoundedRect(ctx, 56, currentY, width - 112, 28, 6);
                ctx.fillStyle = theme.surface;
                ctx.fill();

                ctx.fillStyle = theme.text;
                ctx.font = `700 12px ${theme.fontSans}`;
                ctx.fillText(`${group.displayName} (${group.total})`, 68, currentY + 18);

                const itemsSummary = group.items.map(i => `${i.name} ×${i.qty}`).join('  ·  ');
                ctx.textAlign = 'right';
                ctx.fillStyle = theme.primary;
                ctx.font = `600 12px ${theme.fontSans}`;
                ctx.fillText(itemsSummary, width - 68, currentY + 18);
                ctx.textAlign = 'left';

                currentY += 34;
            });
        }

        const totalBarY = tableY + tableH - 40;
        ctx.fillStyle = 'rgba(20, 184, 166, 0.1)';
        ctx.fillRect(40, totalBarY, width - 80, 40);
        ctx.beginPath();
        ctx.moveTo(40, totalBarY);
        ctx.lineTo(width - 40, totalBarY);
        ctx.strokeStyle = theme.primary;
        ctx.stroke();

        ctx.fillStyle = theme.textSecondary;
        ctx.font = `600 12px ${theme.fontSans}`;
        ctx.fillText('Store Terms Accepted (Strict No-Refund Policy)', 56, totalBarY + 24);

        ctx.textAlign = 'right';
        ctx.fillStyle = theme.primary;
        ctx.font = `700 17px ${theme.fontHeading}`;
        ctx.fillText(`Total Paid: ${inv.price}`, width - 56, totalBarY + 25);
        ctx.textAlign = 'left';

        const footerY = height - 30;
        ctx.fillStyle = theme.textMuted;
        ctx.font = `500 12px ${theme.fontSans}`;
        ctx.fillText('HavenCraft SMP Bangladesh · havencraft.pro', 40, footerY);
        ctx.textAlign = 'right';
        ctx.fillText('Support & Tickets: dc.havencraft.pro', width - 40, footerY);
        ctx.textAlign = 'left';

        const dataUrl = canvas.toDataURL('image/png');
        const link = document.createElement('a');
        link.download = `HavenCraft Invoice ${inv.rawOrderId || 'Order'}.png`;
        link.href = dataUrl;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        if (window.showToast) {
            window.showToast(isAuto ? '✓ Order placed! Invoice downloaded automatically.' : '✓ Invoice image downloaded!');
        }
    } catch (err) {
        console.error('Failed to generate invoice image:', err);
        if (window.showToast) {
            window.showToast('Could not generate image. Using Copy Invoice instead.');
        }
        copyReceiptSlip();
    } finally {
        if (downloadBtn) {
            downloadBtn.disabled = false;
            if (downloadSpan) downloadSpan.textContent = prevDownloadText;
        }
    }
}

function initStore() {
    initConfigMaps();

    const heroBadge = document.getElementById('storeHeroBadge');
    const seasonConfig = getStoreConfig().season;
    if (heroBadge && (seasonConfig?.badgeText || seasonConfig?.name)) {
        heroBadge.innerHTML = `<i class="fa-solid fa-gem" aria-hidden="true"></i> <span class="season-name" data-season-name>${escHtml(seasonConfig.badgeText || seasonConfig.name)}</span> Live Catalog`;
    }

    renderRanksGrid();
    renderSpawnerGrid();

    const ticketUrl = getStoreConfig().discord?.ticketUrl || window.SERVER_CONFIG?.discordUrl;
    const ticketBtn = document.getElementById('btnOpenDiscordTicket');
    if (ticketBtn && ticketUrl) {
        ticketBtn.href = ticketUrl;
    }

    const initialNum = getPaymentNumber('bKash');
    const paymentNumDisplay = document.getElementById('paymentNumberDisplay');
    const guidePaymentNumber = document.getElementById('guidePaymentNumber');
    if (paymentNumDisplay) paymentNumDisplay.textContent = initialNum;
    if (guidePaymentNumber) guidePaymentNumber.textContent = initialNum;
}

window.addEventListener('hvn_settings_updated', (e) => {
    const detail = e.detail || {};

    const season = detail.season || getStoreConfig().season;
    if (season) {
        const heroBadge = document.getElementById('storeHeroBadge');
        if (heroBadge && (season.badge || season.badgeText || season.name)) {
            heroBadge.innerHTML = `<i class="fa-solid fa-gem" aria-hidden="true"></i> <span class="season-name" data-season-name>${escHtml(season.badge || season.badgeText || season.name)}</span> Live Catalog`;
        }
    }

    if (typeof setPaymentMethod === 'function') {
        setPaymentMethod(currentPaymentMethod || 'bKash');
    }

    const conn = detail.server_connectivity || detail.server_info;
    const ticketUrl = conn?.discordUrl || getStoreConfig().discord?.ticketUrl || window.SERVER_CONFIG?.discordUrl;
    const ticketBtn = document.getElementById('btnOpenDiscordTicket');
    if (ticketBtn && ticketUrl) {
        ticketBtn.href = ticketUrl;
    }
});

async function syncShopCatalogFromSupabase() {
    try {
        let rows = null;
        if (window.HavenCraftCore && typeof window.HavenCraftCore.getGallery === 'function') {
            const client = window.HavenCraftCore.getGallery();
            if (client && client.from) {
                const { data, error } = await client.from('shop_items').select('*');
                if (!error && Array.isArray(data) && data.length > 0) {
                    rows = data;
                }
            }
        }

        if (!rows) {
            const cfg = window.HavenCraftCore?.getSupabaseConfig?.('core');
            if (cfg && cfg.url && cfg.key) {
                const res = await fetch(`${cfg.url}/rest/v1/shop_items?select=*`, {
                    headers: {
                        apikey: cfg.key,
                        Authorization: `Bearer ${cfg.key}`
                    }
                });
                if (res.ok) {
                    const data = await res.json();
                    if (Array.isArray(data) && data.length > 0) {
                        rows = data;
                    }
                }
            }
        }

        if (rows && rows.length > 0) {
            try {
                localStorage.setItem('hvn_shop_items_cache', JSON.stringify(rows));
            } catch (err) {
                console.warn('[Store] Failed to cache shop_items:', err?.message || err);
            }

            if (typeof window.applyShopItemsToStoreConfig === 'function') {
                window.applyShopItemsToStoreConfig(rows);
                initConfigMaps();
                renderRanksGrid();
                renderSpawnerGrid();
                updateSpawnerCartBar();
                updateModalPriceDisplay();
            }
        }
    } catch (err) {
        console.warn('[HavenCraft Store] Live shop_items sync fallback to static config:', err);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initStore();
    syncShopCatalogFromSupabase();

    try {
        const savedCurrency = localStorage.getItem('havencraft-currency');
        if (savedCurrency === 'USD') {
            setCurrency('USD');
        }
    } catch (err) {
        console.warn('[Store] Failed to read currency preference:', err?.message || err);
    }

    document.querySelectorAll('.store-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            switchStoreTab(tab.getAttribute('data-tab'));
        });
    });

    const usernameInput = document.getElementById('playerUsername');
    if (usernameInput) {
        usernameInput.addEventListener('input', () => handleUsernameInput(usernameInput.value));
    }

    ['playerUsername', 'senderNumber', 'trxId', 'discordUser'].forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            input.addEventListener('input', () => input.classList.remove('input-error'));
        }
    });

    const termsCheckbox = document.getElementById('agreeStoreTerms');
    if (termsCheckbox) {
        termsCheckbox.addEventListener('change', () => {
            const wrapper = document.querySelector('.terms-checkbox-wrapper');
            if (wrapper) wrapper.classList.remove('input-error');
        });
    }

    const checkoutOverlay = document.getElementById('checkoutModalOverlay');
    if (checkoutOverlay) {
        checkoutOverlay.addEventListener('click', (e) => {
            if (e.target === checkoutOverlay) {
                closeCheckoutModal();
            }
        });
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const termsOverlay = document.getElementById('storeTermsModalOverlay');
            if (termsOverlay && termsOverlay.style.display !== 'none') {
                closeStoreTermsModal();
                return;
            }
            if (checkoutOverlay && checkoutOverlay.style.display !== 'none') {
                closeCheckoutModal();
            }
        }
    });
});
