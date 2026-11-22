import storeConfigModule from '../../store/config.js';
import { supabaseRest } from './supabase.js';

const {
    DEFAULT_STORE_CATALOG,
    cloneCatalog,
    applyShopItemsToCatalog,
    buildCatalogLookup,
    resolveRankAdminCommand
} = storeConfigModule;

const CATALOG_CACHE_TTL_MS = 60 * 1000;
let catalogCache = {
    fetchedAt: 0,
    catalog: null
};

const CATEGORY_ICONS = {
    basic: '🐷',
    combat: '⚔️',
    advanced: '💎',
    special: '👑'
};

function getCategoryDisplayName(catId, catalog) {
    const cat = (catalog?.spawnerCategories || []).find(c => c.id === catId);
    if (cat?.name) return cat.name.replace(/\s*spawners\s*/gi, '').trim();
    return catId.charAt(0).toUpperCase() + catId.slice(1);
}

function parseNumericPrice(rawPrice) {
    if (typeof rawPrice === 'number' && Number.isFinite(rawPrice)) return rawPrice;
    if (typeof rawPrice !== 'string') return NaN;
    const cleaned = rawPrice.replace(/[^0-9.]/g, '');
    return cleaned ? parseFloat(cleaned) : NaN;
}

function detectCurrency(rawCurrency, rawPrice) {
    const c = typeof rawCurrency === 'string' ? rawCurrency.trim().toUpperCase() : '';
    const p = typeof rawPrice === 'string' ? rawPrice.trim() : '';

    if (c === 'USD') {
        if (p.includes('৳')) return { ok: false, error: 'Currency mismatch between BDT symbol and USD selection.' };
        return { ok: true, currency: 'USD' };
    }
    if (c === 'BDT') {
        if (p.includes('$')) return { ok: false, error: 'Currency mismatch between USD symbol and BDT selection.' };
        return { ok: true, currency: 'BDT' };
    }
    if (p.includes('$')) return { ok: true, currency: 'USD' };
    return { ok: true, currency: 'BDT' };
}

export function resetCatalogCache() {
    catalogCache = { fetchedAt: 0, catalog: null };
}

export async function getAuthoritativeCatalog(supabaseConfig, { forceRefresh = false } = {}) {
    const now = Date.now();
    if (!forceRefresh && catalogCache.catalog && (now - catalogCache.fetchedAt) < CATALOG_CACHE_TTL_MS) {
        return { catalog: catalogCache.catalog, fromCache: true };
    }

    const catalog = cloneCatalog(DEFAULT_STORE_CATALOG);

    if (supabaseConfig) {
        try {
            const res = await supabaseRest(supabaseConfig, '/rest/v1/shop_items?select=*', {
                method: 'GET',
                timeoutMs: 4500
            });
            if (res.ok && Array.isArray(res.data) && res.data.length > 0) {
                applyShopItemsToCatalog(catalog, res.data);
            }
        } catch (err) {
            console.warn('[StoreCatalog] Failed to fetch live shop_items, using canonical fallback:', err?.message || err);
        }
    }

    catalogCache = { fetchedAt: now, catalog };
    return { catalog, fromCache: false };
}

function findRankInCatalog(catalog, lookup, itemId, itemName) {
    const idKey = String(itemId || '').trim().toLowerCase().replace(/^rank[-_]?/, '');
    if (idKey && lookup.ranksById[idKey]) {
        return lookup.ranksById[idKey];
    }

    const normalizedName = String(itemName || '').trim().toLowerCase();
    if (!normalizedName) return null;

    return (catalog.ranks || []).find(r =>
        String(r.id || '').toLowerCase() === normalizedName ||
        String(r.name || '').toLowerCase() === normalizedName ||
        String(r.fullName || '').toLowerCase() === normalizedName
    ) || null;
}

function formatGroupedSpawnerMarkdown(groupedByCat, catalog) {
    const categories = catalog.spawnerCategories || [];
    const lines = [];

    const appendGroup = (catId, group) => {
        if (!group || group.items.length === 0) return;
        const icon = CATEGORY_ICONS[catId] || '📦';
        const displayName = getCategoryDisplayName(catId, catalog);
        const itemParts = group.items.map(i => `\`${i.qty}x ${i.name}\``);
        lines.push(`> • ${icon} **${displayName} (${group.total}):** ${itemParts.join(', ')}`);
    };

    categories.forEach(cat => {
        appendGroup(cat.id, groupedByCat[cat.id]);
    });

    Object.entries(groupedByCat).forEach(([catId, group]) => {
        if (!categories.some(c => c.id === catId)) {
            appendGroup(catId, group);
        }
    });

    return lines.join('\n');
}

function validateOrderAgainstCatalog(body, catalog) {
    const lookup = buildCatalogLookup(catalog);
    const orderType = body.itemType === 'spawners' ? 'spawners' : 'rank';

    const currencyCheck = detectCurrency(body.currency, body.price);
    if (!currencyCheck.ok) {
        return { ok: false, status: 400, error: currencyCheck.error };
    }
    const currency = currencyCheck.currency;

    const clientPrice = parseNumericPrice(body.price);
    const hasClientPrice = body.price !== undefined && body.price !== null && String(body.price).trim() !== '';
    if (hasClientPrice && Number.isNaN(clientPrice)) {
        return { ok: false, status: 400, error: 'Invalid order price format.' };
    }

    if (orderType === 'rank') {
        const rank = findRankInCatalog(catalog, lookup, body.itemId || body.tier, body.itemName);
        if (!rank) {
            return { ok: false, status: 400, error: 'Selected rank package was not found in the store catalog.' };
        }

        const expectedAmount = currency === 'USD' ? Number(rank.usdAmount) : Number(rank.bdtAmount);
        if (!Number.isFinite(expectedAmount) || expectedAmount <= 0) {
            return { ok: false, status: 500, error: 'Rank pricing is not configured properly.' };
        }

        if (hasClientPrice && Math.abs(clientPrice - expectedAmount) > 0.01) {
            return {
                ok: false,
                status: 409,
                priceMismatch: true,
                error: 'Catalog pricing has changed or is invalid. Please refresh the store page and try again.'
            };
        }

        const quotas = rank.spawnerQuotas || {};
        const requiredCats = Object.entries(quotas).filter(([, q]) => Number(q) > 0);
        let selectedSpawnersText = '';

        if (requiredCats.length > 0) {
            const rawSelections = body.selectedRankSpawners;
            if (!rawSelections || typeof rawSelections !== 'object' || Array.isArray(rawSelections)) {
                return { ok: false, status: 400, error: 'Please select your included rank spawners before submitting.' };
            }

            const grouped = {};
            for (const [catId, quotaCount] of requiredCats) {
                const targetCount = Number(quotaCount);
                const chosenMap = rawSelections[catId];
                if (!chosenMap || typeof chosenMap !== 'object' || Array.isArray(chosenMap)) {
                    return { ok: false, status: 400, error: `Please select ${targetCount} ${catId} spawners.` };
                }

                let sum = 0;
                grouped[catId] = { total: 0, items: [] };
                for (const [spawnerId, rawQty] of Object.entries(chosenMap)) {
                    const qty = Number(rawQty);
                    if (!Number.isInteger(qty) || qty < 0) {
                        return { ok: false, status: 400, error: 'Invalid included spawner quantity.' };
                    }
                    if (qty === 0) continue;

                    const spMeta = lookup.spawnersById[String(spawnerId).toLowerCase()];
                    if (!spMeta || spMeta.catId !== catId) {
                        return { ok: false, status: 400, error: `Invalid spawner "${spawnerId}" for category "${catId}".` };
                    }
                    sum += qty;
                    grouped[catId].total += qty;
                    grouped[catId].items.push({ id: spMeta.id, name: spMeta.name, qty });
                }

                if (sum !== targetCount) {
                    return {
                        ok: false,
                        status: 400,
                        error: `Please select exactly ${targetCount} ${catId} spawner(s) for ${rank.fullName}.`
                    };
                }
            }

            // Reject extra categories not included in the rank's quota.
            for (const extraCat of Object.keys(rawSelections)) {
                if (!quotas[extraCat]) {
                    const hasPositive = Object.values(rawSelections[extraCat] || {}).some(v => Number(v) > 0);
                    if (hasPositive) {
                        return { ok: false, status: 400, error: `Category "${extraCat}" is not included with ${rank.fullName}.` };
                    }
                }
            }

            selectedSpawnersText = formatGroupedSpawnerMarkdown(grouped, catalog);
        }

        const formattedPrice = currency === 'USD' ? `$${expectedAmount.toFixed(2)}` : `৳${expectedAmount}`;
        return {
            ok: true,
            orderType: 'rank',
            itemId: rank.id,
            itemName: rank.fullName || `${rank.name} Rank`,
            dbItemName: rank.fullName || `${rank.name} Rank`,
            currency,
            price: formattedPrice,
            amountNumeric: expectedAmount,
            adminCmdTemplate: rank.adminCommand || `lp user {username} parent set ${rank.id}`,
            selectedSpawners: selectedSpawnersText,
            itemsList: ''
        };
    }

    // Standalone spawner bundle order validation
    const rawCart = body.cartItems;
    if (!Array.isArray(rawCart) || rawCart.length === 0) {
        return { ok: false, status: 400, error: 'Please select at least 1 spawner in your cart before checking out.' };
    }
    if (rawCart.length > 50) {
        return { ok: false, status: 400, error: 'Cart exceeds maximum allowed line items.' };
    }

    const mergedById = new Map();
    for (const entry of rawCart) {
        const spId = typeof entry?.id === 'string' ? entry.id.trim().toLowerCase() : '';
        const qty = Number(entry?.qty);
        if (!spId || !Number.isInteger(qty) || qty < 1 || qty > 999) {
            return { ok: false, status: 400, error: 'Invalid spawner item or quantity in cart.' };
        }
        const spMeta = lookup.spawnersById[spId];
        if (!spMeta) {
            return { ok: false, status: 400, error: `Unknown or unavailable spawner item: ${spId}` };
        }
        const prev = mergedById.get(spId) || 0;
        const combined = prev + qty;
        if (combined > 999) {
            return { ok: false, status: 400, error: 'Quantity per spawner item cannot exceed 999.' };
        }
        mergedById.set(spId, combined);
    }

    let totalBDT = 0;
    let totalUSD = 0;
    const titleLabels = [];
    const grouped = {};

    for (const [spId, qty] of mergedById.entries()) {
        const spMeta = lookup.spawnersById[spId];
        totalBDT += spMeta.bdtAmount * qty;
        totalUSD += spMeta.usdAmount * qty;

        const baseName = spMeta.name || spId;
        const fullLabel = /spawner/i.test(baseName) ? baseName : `${baseName} Spawner`;
        titleLabels.push(qty > 1 ? `${fullLabel} (x${qty})` : fullLabel);

        const catId = spMeta.catId || 'other';
        if (!grouped[catId]) grouped[catId] = { total: 0, items: [] };
        grouped[catId].total += qty;
        grouped[catId].items.push({ id: spId, name: spMeta.name, qty });
    }

    const expectedAmount = currency === 'USD' ? Number(totalUSD.toFixed(2)) : totalBDT;
    if (!Number.isFinite(expectedAmount) || expectedAmount <= 0) {
        return { ok: false, status: 500, error: 'Spawner pricing is not configured properly.' };
    }

    if (hasClientPrice && Math.abs(clientPrice - expectedAmount) > 0.01) {
        return {
            ok: false,
            status: 409,
            priceMismatch: true,
            error: 'Spawner prices have changed or are invalid. Please refresh the store page and try again.'
        };
    }

    const formattedPrice = currency === 'USD' ? `$${expectedAmount.toFixed(2)}` : `৳${expectedAmount}`;
    const summaryTitle = titleLabels.join(', ');

    return {
        ok: true,
        orderType: 'spawners',
        itemId: 'custom',
        itemName: summaryTitle,
        dbItemName: summaryTitle,
        currency,
        price: formattedPrice,
        amountNumeric: expectedAmount,
        adminCmdTemplate: '',
        selectedSpawners: '',
        itemsList: formatGroupedSpawnerMarkdown(grouped, catalog)
    };
}

export async function validateAndBuildOrder(body, supabaseConfig) {
    let { catalog, fromCache } = await getAuthoritativeCatalog(supabaseConfig);
    let result = validateOrderAgainstCatalog(body, catalog);

    // If a cached catalog rejected the order (price changed or new item added in Supabase),
    // bypass cache once and re-validate against fresh live rows before failing.
    if (!result.ok && fromCache && supabaseConfig) {
        ({ catalog } = await getAuthoritativeCatalog(supabaseConfig, { forceRefresh: true }));
        result = validateOrderAgainstCatalog(body, catalog);
    }

    if (!result.ok) return result;

    return {
        ...result,
        resolveAdminCommand(username) {
            return result.adminCmdTemplate
                ? resolveRankAdminCommand(result.adminCmdTemplate, username, result.itemId)
                : '';
        }
    };
}
