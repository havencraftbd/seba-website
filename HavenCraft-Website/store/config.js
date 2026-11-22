/**
 * HavenCraft — Canonical Store Catalog & Configuration
 * Loaded both in the browser (`window.STORE_CONFIG`) and on Vercel serverless (`api/_lib/store-catalog.js`).
 */
(function (root) {
    const DEFAULT_RANKS = [
        {
            id: 'prime',
            name: 'Prime',
            fullName: 'Prime Rank',
            badge: 'Starter',
            icon: '💎',
            popular: false,
            popularBadgeText: '',
            priceBDT: '৳220',
            priceUSD: '$1.99',
            bdtAmount: 220,
            usdAmount: 1.99,
            adminCommand: '/lp user {username} parent set prime',
            spawnerQuotas: {
                basic: 2,
                combat: 2
            },
            perks: [
                { icon: '💸', text: 'In-game Money: <strong>200K</strong>' },
                { icon: '✨', text: 'In-game Shard: <strong>400</strong>' },
                { icon: '🏠', text: '<strong>8</strong> SetHomes' },
                { icon: '🛡️', text: 'Max Armor & Tools <em>(Vanilla Max)</em>' },
                { icon: '📦', text: '<strong>8</strong> Shulker Boxes' },
                { icon: '🍏', text: '90 Golden Apple + 12 Enchanted Golden Apple' },
                { icon: '🐄', text: '4 Spawners <em>(Basic×2 & Combat×2)</em>' },
                { icon: '🧱', text: '<strong>20K</strong> Claim Blocks' }
            ],
            commandsInherit: '',
            commands: ['/back', '/ptime', '/disposal', '/near', '/hat']
        },
        {
            id: 'legend',
            name: 'Legend',
            fullName: 'Legend Rank',
            badge: 'Mid-Tier',
            icon: '⚔️',
            popular: false,
            popularBadgeText: '',
            priceBDT: '৳340',
            priceUSD: '$2.99',
            bdtAmount: 340,
            usdAmount: 2.99,
            adminCommand: '/lp user {username} parent set legend',
            spawnerQuotas: {
                basic: 2,
                combat: 4
            },
            perks: [
                { icon: '💸', text: 'In-game Money: <strong>350K</strong>' },
                { icon: '✨', text: 'In-game Shard: <strong>600</strong>' },
                { icon: '🏠', text: '<strong>10</strong> SetHomes' },
                { icon: '⚔️', text: 'Full Max + Custom Gear <em>(Limited)</em>' },
                { icon: '⚔️', text: 'Full Max Custom Sword' },
                { icon: '📦', text: '<strong>12</strong> Shulker Boxes' },
                { icon: '🍏', text: '160 Golden Apple + 32 Enchanted Golden Apple' },
                { icon: '🐓', text: '6 Spawners <em>(Basic×2 & Combat×4)</em>' },
                { icon: '🧱', text: '<strong>30K</strong> Claim Blocks' }
            ],
            commandsInherit: 'All Prime Commands +',
            commands: ['/workbench', '/echest', '/feed', '/nick', '/smithingtable', '/cartographytable', '/loom']
        },
        {
            id: 'elite',
            name: 'Elite',
            fullName: 'Elite Rank',
            badge: 'Best Value',
            icon: '👑',
            popular: true,
            popularBadgeText: 'Most Popular',
            priceBDT: '৳420',
            priceUSD: '$3.69',
            bdtAmount: 420,
            usdAmount: 3.69,
            adminCommand: '/lp user {username} parent set elite',
            spawnerQuotas: {
                basic: 2,
                combat: 4,
                advanced: 2
            },
            perks: [
                { icon: '💸', text: 'In-game Money: <strong>500K</strong>' },
                { icon: '✨', text: 'In-game Shard: <strong>600</strong>' },
                { icon: '🏠', text: '<strong>12</strong> SetHomes' },
                { icon: '🐦', text: '<strong>1</strong> Elytra' },
                { icon: '🔨', text: 'Max Mace ×1' },
                { icon: '⛏', text: '3x3 Custom Pickaxe' },
                { icon: '⚔️', text: 'Full Max-Custom Gear' },
                { icon: '📦', text: '<strong>15</strong> Shulker Boxes' },
                { icon: '🍏', text: '256 Golden Apple + 48 Enchanted Golden Apple' },
                { icon: '🐄', text: '8 Spawners <em>(Basic×2, Advance×2 & Combat×4)</em>' },
                { icon: '🧱', text: '<strong>40K</strong> Claim Blocks' }
            ],
            commandsInherit: 'All Legend Commands +',
            commands: ['/heal', '/ext', '/ptime night', '/anvil', '/grindstone']
        }
    ];

    const DEFAULT_SPAWNER_CATEGORIES = [
        {
            id: 'basic',
            name: 'Basic Spawners',
            icon: '🔥',
            priceBDT: '৳25',
            priceUSD: '$0.25',
            bdtAmount: 25,
            usdAmount: 0.25,
            items: [
                { id: 'pig', name: 'Pig', emoji: '🐷' },
                { id: 'cow', name: 'Cow', emoji: '🐮' },
                { id: 'chicken', name: 'Chicken', emoji: '🐔' },
                { id: 'sheep', name: 'Sheep', emoji: '🐑' }
            ]
        },
        {
            id: 'combat',
            name: 'Combat Spawners',
            icon: '⚔️',
            priceBDT: '৳35',
            priceUSD: '$0.32',
            bdtAmount: 35,
            usdAmount: 0.32,
            items: [
                { id: 'zombie', name: 'Zombie', emoji: '🧟' },
                { id: 'spider', name: 'Spider', emoji: '🕷️' },
                { id: 'cave_spider', name: 'Cave Spider', emoji: '🕸️' },
                { id: 'skeleton', name: 'Skeleton', emoji: '🏹' },
                { id: 'guardian', name: 'Guardian', emoji: '🐟' }
            ]
        },
        {
            id: 'advanced',
            name: 'Advanced Spawners',
            icon: '⚡',
            priceBDT: '৳45',
            priceUSD: '$0.40',
            bdtAmount: 45,
            usdAmount: 0.40,
            items: [
                { id: 'enderman', name: 'Enderman', emoji: '🌑' },
                { id: 'slime', name: 'Slime', emoji: '🟢' },
                { id: 'magma_cube', name: 'Magma Cube', emoji: '🌋' },
                { id: 'creeper', name: 'Creeper', emoji: '💥' },
                { id: 'blaze', name: 'Blaze', emoji: '🔥' },
                { id: 'piglin', name: 'Zombified Piglin', emoji: '🐖', note: '(Gold Farming)' }
            ]
        },
        {
            id: 'special',
            name: 'Special Spawners',
            icon: '👑',
            priceBDT: '৳55',
            priceUSD: '$0.50',
            bdtAmount: 55,
            usdAmount: 0.50,
            items: [
                { id: 'iron_golem', name: 'Iron Golem', emoji: '🤖', note: '(Iron Farming)' },
                { id: 'ghast', name: 'Ghast', emoji: '👻', note: '(Ghast Tears & Gunpowder)' },
                { id: 'breeze', name: 'Breeze', emoji: '🌪️', note: '(Breeze Rod)' },
                { id: 'wither_skeleton', name: 'Wither Skeleton', emoji: '💀' }
            ]
        }
    ];

    const DEFAULT_STORE_CATALOG = {
        season: {
            name: 'Season 6',
            badgeText: 'Season 6',
            description: 'Order Season 6 ranks and mob spawners to support server hosting.'
        },
        payment: {
            number: '01516523070',
            accountType: 'Personal',
            methodAction: 'Send Money',
            notice: 'Personal Account • Send Money Only',
            bkashNumber: '01516523070',
            nagadNumber: '01516523070'
        },
        discord: {
            ticketUrl: 'https://dc.havencraft.pro'
        },
        ranks: DEFAULT_RANKS,
        spawnerCategories: DEFAULT_SPAWNER_CATEGORIES
    };

    function cloneCatalog(source) {
        return JSON.parse(JSON.stringify(source));
    }

    function escapePerkHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatPerkHtml(rawText) {
        if (!rawText) return '';
        // Preserve only inline <strong>/<em> tags while escaping any arbitrary HTML from remote rows.
        const protectedText = String(rawText)
            .replace(/<strong>/gi, '\u0001STRONG_OPEN\u0002')
            .replace(/<\/strong>/gi, '\u0001STRONG_CLOSE\u0002')
            .replace(/<em>/gi, '\u0001EM_OPEN\u0002')
            .replace(/<\/em>/gi, '\u0001EM_CLOSE\u0002');

        return escapePerkHtml(protectedText)
            .replace(/\u0001STRONG_OPEN\u0002/g, '<strong>')
            .replace(/\u0001STRONG_CLOSE\u0002/g, '</strong>')
            .replace(/\u0001EM_OPEN\u0002/g, '<em>')
            .replace(/\u0001EM_CLOSE\u0002/g, '</em>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\*([^*]+)\*/g, '<em>$1</em>');
    }

    const KNOWN_SPAWNER_META = [
        { match: ['wither_skeleton', 'witherskeleton', 'wither skeleton'], id: 'wither_skeleton', emoji: '💀' },
        { match: ['iron_golem', 'irongolem', 'iron golem'], id: 'iron_golem', emoji: '🤖', note: '(Iron Farming)' },
        { match: ['zombified_piglin', 'zombified piglin', 'piglin'], id: 'piglin', emoji: '🐖', note: '(Gold Farming)' },
        { match: ['cave_spider', 'cavespider', 'cave spider'], id: 'cave_spider', emoji: '🕸️' },
        { match: ['magma_cube', 'magmacube', 'magma cube'], id: 'magma_cube', emoji: '🌋' },
        { match: ['pig'], id: 'pig', emoji: '🐷' },
        { match: ['cow'], id: 'cow', emoji: '🐮' },
        { match: ['chicken'], id: 'chicken', emoji: '🐔' },
        { match: ['sheep'], id: 'sheep', emoji: '🐑' },
        { match: ['zombie'], id: 'zombie', emoji: '🧟' },
        { match: ['spider'], id: 'spider', emoji: '🕷️' },
        { match: ['skeleton'], id: 'skeleton', emoji: '🏹' },
        { match: ['guardian'], id: 'guardian', emoji: '🐟' },
        { match: ['enderman'], id: 'enderman', emoji: '🌑' },
        { match: ['slime'], id: 'slime', emoji: '🟢' },
        { match: ['creeper'], id: 'creeper', emoji: '💥' },
        { match: ['blaze'], id: 'blaze', emoji: '🔥' },
        { match: ['ghast'], id: 'ghast', emoji: '👻', note: '(Ghast Tears & Gunpowder)' },
        { match: ['breeze'], id: 'breeze', emoji: '🌪️', note: '(Breeze Rod)' }
    ];

    function resolveSpawnerMeta(row) {
        const rawId = String(row.id || '').toLowerCase().replace(/^spawner[-_]?/, '');
        const rawName = String(row.name || '').replace(/\s*spawner\s*$/i, '').trim();
        const nameLower = rawName.toLowerCase();

        for (const entry of KNOWN_SPAWNER_META) {
            if (entry.match.includes(rawId) || entry.match.includes(nameLower)) {
                const customIcon = (row.icon && !String(row.icon).startsWith('bi-')) ? row.icon : entry.emoji;
                return {
                    id: entry.id,
                    name: rawName || entry.id,
                    emoji: customIcon,
                    note: entry.note || ''
                };
            }
        }

        const fallbackId = rawId || nameLower.replace(/[^a-z0-9]+/g, '_') || ('sp_' + Date.now());
        const customEmoji = (row.icon && !String(row.icon).startsWith('bi-')) ? row.icon : '🥚';
        return {
            id: fallbackId,
            name: rawName || 'Spawner',
            emoji: customEmoji,
            note: ''
        };
    }

    function resolveRankAdminCommand(template, username, fallbackRankId = '') {
        const cleanTemplate = String(template || '').trim();
        const baseCmd = cleanTemplate || (fallbackRankId ? `lp user {username} parent set ${fallbackRankId}` : '');
        if (!baseCmd) return '';
        const prefixed = baseCmd.startsWith('/') ? baseCmd : `/${baseCmd}`;
        return username ? prefixed.replace(/\{username\}/gi, username) : prefixed;
    }

    function applyShopItemsToCatalog(targetCatalog, rows) {
        if (!targetCatalog || !Array.isArray(rows) || rows.length === 0) return false;

        const rankRows = rows
            .filter(r => r && r.type === 'rank' && r.active !== false)
            .sort((a, b) => (Number(a.bdt) || 0) - (Number(b.bdt) || 0));

        if (rankRows.length > 0) {
            targetCatalog.ranks = rankRows.map(row => {
                const shortId = String(row.id || '').toLowerCase().replace(/^rank[-_]?/, '') ||
                    String(row.name || '').toLowerCase().replace(/\s*rank\s*$/i, '').trim().replace(/[^a-z0-9]+/g, '-');
                const shortName = String(row.name || 'Rank').replace(/\s+Rank$/i, '').trim() || 'Rank';
                const fullName = /rank$/i.test(String(row.name || '').trim()) ? String(row.name).trim() : `${shortName} Rank`;

                const tpl = DEFAULT_RANKS.find(t =>
                    t.id === shortId ||
                    shortId.includes(t.id) ||
                    shortName.toLowerCase().includes(t.id)
                ) || DEFAULT_RANKS[0];

                const bdtVal = Number(row.bdt) || tpl.bdtAmount || 0;
                const usdVal = parseFloat(String(row.usd || tpl.usdAmount || '0').replace(/[^0-9.]/g, '')) || 0;
                const adminCommand = resolveRankAdminCommand(row.command || tpl.adminCommand, '', shortId);

                let icon = tpl.icon || '💎';
                let badge = row.category || tpl.badge || '';
                let popular = !!tpl.popular;
                let popularBadgeText = tpl.popularBadgeText || '';
                let spawnerQuotas = cloneCatalog(tpl.spawnerQuotas || {});
                let perks = cloneCatalog(tpl.perks || []);
                let commandsInherit = tpl.commandsInherit || '';
                let commands = (tpl.commands || []).slice();

                const rawPerks = typeof row.perks === 'string' ? row.perks.trim() : '';
                if (rawPerks.startsWith('{')) {
                    try {
                        const parsed = JSON.parse(rawPerks);
                        if (parsed.emoji) icon = parsed.emoji;
                        if (row.category || parsed.badge) badge = row.category || parsed.badge;
                        if (typeof parsed.popular === 'boolean') popular = parsed.popular;
                        if (parsed.popularBadgeText !== undefined) popularBadgeText = parsed.popularBadgeText || (popular ? 'Most Popular' : '');
                        if (parsed.spawnerQuotas && typeof parsed.spawnerQuotas === 'object') {
                            const cleanQuotas = {};
                            ['basic', 'combat', 'advanced', 'special'].forEach(catKey => {
                                const q = parseInt(parsed.spawnerQuotas[catKey], 10) || 0;
                                if (q > 0) cleanQuotas[catKey] = q;
                            });
                            spawnerQuotas = cleanQuotas;
                        }
                        if (Array.isArray(parsed.perksList)) {
                            perks = parsed.perksList
                                .filter(p => p && String(p.text || '').trim().length > 0)
                                .map(p => ({
                                    icon: p.icon || '🎁',
                                    text: formatPerkHtml(p.text)
                                }));
                        }
                        if (typeof parsed.commandsInherit === 'string') {
                            commandsInherit = parsed.commandsInherit;
                        }
                        if (Array.isArray(parsed.commandsList)) {
                            commands = parsed.commandsList.map(c => String(c || '').trim()).filter(Boolean);
                        }
                    } catch (err) {
                        console.warn('[StoreConfig] Failed to parse rank perks JSON for', shortId, err?.message || err);
                    }
                }

                return {
                    id: shortId,
                    name: shortName,
                    fullName: fullName,
                    badge: badge,
                    icon: icon,
                    popular: popular,
                    popularBadgeText: popularBadgeText,
                    priceBDT: `৳${bdtVal}`,
                    priceUSD: `$${usdVal.toFixed(2)}`,
                    bdtAmount: bdtVal,
                    usdAmount: usdVal,
                    adminCommand: adminCommand,
                    spawnerQuotas: spawnerQuotas,
                    perks: perks,
                    commandsInherit: commandsInherit,
                    commands: commands
                };
            });
        }

        const spawnerRows = rows.filter(r => r && r.type === 'spawner' && r.active !== false);
        if (spawnerRows.length > 0) {
            const catDefs = [
                { id: 'basic', name: 'Basic Spawners', icon: '🔥', defaultBdt: 25, defaultUsd: 0.25 },
                { id: 'combat', name: 'Combat Spawners', icon: '⚔️', defaultBdt: 35, defaultUsd: 0.32 },
                { id: 'advanced', name: 'Advanced Spawners', icon: '⚡', defaultBdt: 45, defaultUsd: 0.40 },
                { id: 'special', name: 'Special Spawners', icon: '👑', defaultBdt: 55, defaultUsd: 0.50 }
            ];

            const grouped = {};
            catDefs.forEach(c => { grouped[c.id] = []; });

            spawnerRows.forEach(row => {
                const catRaw = String(row.category || 'Basic').trim().toLowerCase();
                let catKey = 'basic';
                if (catRaw.includes('combat')) catKey = 'combat';
                else if (catRaw.includes('advanced')) catKey = 'advanced';
                else if (catRaw.includes('special')) catKey = 'special';
                else if (catRaw.includes('basic')) catKey = 'basic';
                else {
                    catKey = catRaw.replace(/[^a-z0-9]+/g, '_') || 'basic';
                    if (!grouped[catKey]) {
                        grouped[catKey] = [];
                        catDefs.push({
                            id: catKey,
                            name: `${row.category} Spawners`,
                            icon: '🥚',
                            defaultBdt: Number(row.bdt) || 35,
                            defaultUsd: parseFloat(String(row.usd || '0.35').replace(/[^0-9.]/g, '')) || 0.35
                        });
                    }
                }

                const meta = resolveSpawnerMeta(row);
                const bdtVal = Number(row.bdt) || 25;
                const usdVal = parseFloat(String(row.usd || '0.25').replace(/[^0-9.]/g, '')) || 0.25;

                grouped[catKey].push({
                    id: meta.id,
                    name: meta.name,
                    emoji: meta.emoji,
                    note: meta.note,
                    bdtAmount: bdtVal,
                    usdAmount: usdVal,
                    adminCommand: row.command || ''
                });
            });

            targetCatalog.spawnerCategories = catDefs
                .filter(c => grouped[c.id] && grouped[c.id].length > 0)
                .map(c => {
                    const items = grouped[c.id];
                    const catBdt = items[0]?.bdtAmount ?? c.defaultBdt;
                    const catUsd = items[0]?.usdAmount ?? c.defaultUsd;
                    return {
                        id: c.id,
                        name: c.name,
                        icon: c.icon,
                        priceBDT: `৳${catBdt}`,
                        priceUSD: `$${Number(catUsd).toFixed(2)}`,
                        bdtAmount: catBdt,
                        usdAmount: catUsd,
                        items: items
                    };
                });
        }

        return true;
    }

    function buildCatalogLookup(catalog) {
        const ranksById = {};
        const spawnersById = {};
        const categoriesById = {};

        if (Array.isArray(catalog?.ranks)) {
            catalog.ranks.forEach(r => {
                if (r && r.id) {
                    ranksById[String(r.id).toLowerCase()] = r;
                }
            });
        }

        if (Array.isArray(catalog?.spawnerCategories)) {
            catalog.spawnerCategories.forEach(cat => {
                if (!cat || !cat.id) return;
                const catId = String(cat.id).toLowerCase();
                categoriesById[catId] = cat;
                if (Array.isArray(cat.items)) {
                    cat.items.forEach(item => {
                        if (!item || !item.id) return;
                        const itemId = String(item.id).toLowerCase();
                        spawnersById[itemId] = {
                            id: itemId,
                            name: item.name,
                            emoji: item.emoji || '🥚',
                            catId: catId,
                            catName: cat.name,
                            bdtAmount: item.bdtAmount !== undefined ? Number(item.bdtAmount) : Number(cat.bdtAmount || 0),
                            usdAmount: item.usdAmount !== undefined ? Number(item.usdAmount) : Number(cat.usdAmount || 0),
                            adminCommand: item.adminCommand || ''
                        };
                    });
                }
            });
        }

        return { ranksById, spawnersById, categoriesById };
    }

    if (typeof module === 'object' && module.exports) {
        module.exports = {
            DEFAULT_STORE_CATALOG,
            cloneCatalog,
            applyShopItemsToCatalog,
            buildCatalogLookup,
            resolveRankAdminCommand,
            resolveSpawnerMeta
        };
    }

    if (root && typeof root.document !== 'undefined') {
        root.STORE_CONFIG = cloneCatalog(DEFAULT_STORE_CATALOG);

        root.applyShopItemsToStoreConfig = function (rows) {
            return applyShopItemsToCatalog(root.STORE_CONFIG, rows);
        };

        try {
            if (typeof root.localStorage !== 'undefined') {
                const cachedSettings = root.localStorage.getItem('hvn_site_settings');
                if (cachedSettings) {
                    const parsed = JSON.parse(cachedSettings);
                    if (parsed.season) {
                        if (parsed.season.name) root.STORE_CONFIG.season.name = parsed.season.name;
                        if (parsed.season.badge) root.STORE_CONFIG.season.badgeText = parsed.season.badge;
                        if (parsed.season.description) root.STORE_CONFIG.season.description = parsed.season.description;
                    }
                    if (parsed.payment_numbers) {
                        if (parsed.payment_numbers.bkash) root.STORE_CONFIG.payment.bkashNumber = parsed.payment_numbers.bkash;
                        if (parsed.payment_numbers.nagad) root.STORE_CONFIG.payment.nagadNumber = parsed.payment_numbers.nagad;
                        if (parsed.payment_numbers.bkash || parsed.payment_numbers.nagad) {
                            root.STORE_CONFIG.payment.number = parsed.payment_numbers.bkash || parsed.payment_numbers.nagad;
                        }
                        if (parsed.payment_numbers.type) root.STORE_CONFIG.payment.notice = parsed.payment_numbers.type;
                    }
                    const conn = parsed.server_connectivity || parsed.server_info;
                    if (conn && conn.discordUrl) {
                        if (!root.STORE_CONFIG.discord) root.STORE_CONFIG.discord = {};
                        root.STORE_CONFIG.discord.ticketUrl = conn.discordUrl;
                    }
                }

                const cachedShopItems = root.localStorage.getItem('hvn_shop_items_cache');
                if (cachedShopItems) {
                    const parsedItems = JSON.parse(cachedShopItems);
                    if (Array.isArray(parsedItems) && parsedItems.length > 0) {
                        root.applyShopItemsToStoreConfig(parsedItems);
                    }
                }
            }
        } catch (err) {
            console.warn('[StoreConfig] Failed to hydrate local cache:', err?.message || err);
        }
    }
})(typeof window !== 'undefined' ? window : globalThis);
