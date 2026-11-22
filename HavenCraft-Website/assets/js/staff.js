/* =============================================
   STAFF DATA — Single Source of Truth for Staff & Custom Skins

   HOW SKINS WORK:
   - Mojang Premium Accounts: Leave 'skin' empty/omitted. It automatically syncs
     the latest skin via Minotar in real-time.
   - Cracked / Custom Skins: Add `skin: "URL_HERE"`
     Examples:
       * Mojang Texture URL: skin: "https://textures.minecraft.net/texture/654eb90c..."
       * Image URL:          skin: "https://i.imgur.com/example.png"
       * Copy another user:  skin: "Notch"
       * Optional 2D render: render: "https://...png" (overrides card body render)
   ============================================= */
let staffMembers = [
    {
        name: "JU3L_",
        category: "Management",
        role: "Owner",
        roleClass: "role-owner",
        description: "Founder and lead developer of HavenCraft. Runs server development and website updates."
    },
    {
        name: "Zipp_404",
        category: "Management",
        role: "Admin",
        roleClass: "role-admin",
        description: "Manages server infrastructure, tuning, and backend stability."
    },
    {
        name: ".Rafi_PRX",
        skin: "https://textures.minecraft.net/texture/3a5b39537e96566e8df214cd2b5c94fafbc37733bc98d9eb916e4ed814ad06e4",
        category: "Management",
        role: "Admin",
        roleClass: "role-admin",
        description: "Oversees community management, player support, and in-game events."
    },
    {
        name: "SaimongamerZ",
        category: "Management",
        role: "Admin",
        roleClass: "role-admin",
        description: "Oversees community management, player support, and in-game events."
    },
    {
        name: "Mrdevil1x",
        skin: "https://textures.minecraft.net/texture/2f38c0df84ca184fb127ff183ac720ac5447dfeb9dbb791b235dcba506bd56a2",
        category: "Moderation",
        role: "Moderator",
        roleClass: "role-mod",
        description: "Moderates chat, helps settle player disputes, and runs community build events."
    },
    {
        name: "IM_SAM_JI",
        skin: "",
        category: "Support",
        role: "Officer",
        roleClass: "role-officer",
        description: "Helps new players get started and handles support tickets."
    },
    {
        name: "NOT_SALMAN",
        skin: "https://textures.minecraft.net/texture/a09e60ab4d321482408fbcd19aeb54284402847998114eddff9c3037a389d7fa",
        category: "Support",
        role: "Officer",
        roleClass: "role-officer",
        description: "Helps new players get started and handles support tickets."
    }
];

window.STAFF_MEMBERS = staffMembers;

const _mineskinCache = {};

async function resolveMineskin(url) {
    if (!url || typeof url !== 'string') return url;
    const match = url.match(/mineskin\.org\/skins\/([a-f0-9]{32})/i);
    if (!match) return url;
    const uuid = match[1];
    if (_mineskinCache[uuid]) return _mineskinCache[uuid];
    try {
        const res = await fetch(`https://api.mineskin.org/get/uuid/${uuid}`);
        if (res.ok) {
            const data = await res.json();
            const texUrl = data.data?.texture?.url;
            if (texUrl) {
                _mineskinCache[uuid] = texUrl;
                return texUrl;
            }
        }
    } catch (_) {}
    return url;
}

function extractTextureHash(urlOrHash) {
    if (!urlOrHash || typeof urlOrHash !== 'string') return null;
    const match = urlOrHash.match(/([a-f0-9]{64})/i);
    return match ? match[1] : null;
}

function isDirectUrl(value) {
    return typeof value === 'string' && (
        value.startsWith('http://') ||
        value.startsWith('https://') ||
        value.startsWith('/') ||
        value.startsWith('./') ||
        value.startsWith('../')
    );
}

function normalizeMemberInput(memberOrName) {
    if (typeof memberOrName === 'string') {
        return { name: memberOrName };
    }
    return memberOrName && typeof memberOrName === 'object' ? memberOrName : { name: 'MHF_Steve' };
}

function getBodyRenderUrl(memberOrName) {
    const member = normalizeMemberInput(memberOrName);
    if (isDirectUrl(member.render)) {
        return member.render;
    }

    const name = member.name || 'MHF_Steve';
    const target = (member.skin && member.skin.trim()) || name;
    const hash = extractTextureHash(target);
    if (hash) {
        return `https://mc-heads.net/player/${hash}/180.png`;
    }
    if (target.includes('mineskin.org/skins/')) {
        const cleanFallback = name.replace(/^\.+/, '');
        return `https://minotar.net/armor/body/${encodeURIComponent(cleanFallback)}/180.png`;
    }
    if (isDirectUrl(target)) {
        return target;
    }
    const cleanTarget = target.replace(/^\.+/, '');
    return `https://minotar.net/armor/body/${encodeURIComponent(cleanTarget)}/180.png`;
}

function getSkinTextureUrl(memberOrName) {
    const member = normalizeMemberInput(memberOrName);
    const name = member.name || 'MHF_Steve';
    const target = (member.skin && member.skin.trim()) || name;
    const hash = extractTextureHash(target);
    if (hash) {
        return `https://textures.minecraft.net/texture/${hash}`;
    }
    if (isDirectUrl(target)) {
        return target;
    }
    const cleanTarget = target.replace(/^\.+/, '');
    return `https://minotar.net/skin/${encodeURIComponent(cleanTarget)}`;
}

function findStaffMember(username) {
    if (!username || !Array.isArray(staffMembers)) return null;
    const lower = String(username).trim().toLowerCase();
    const cleanLower = lower.replace(/^\.+/, '');
    return staffMembers.find((member) => {
        const mLower = (member.name || '').toLowerCase();
        return mLower === lower || mLower === cleanLower || mLower === '.' + cleanLower;
    }) || null;
}

window.HavenStaff = {
    findMember: findStaffMember,
    extractTextureHash,
    getBodyRenderUrl,
    getSkinTextureUrl,
    resolveMineskin
};

function escapeStaffHtml(str) {
    return typeof window.escapeHtml === 'function' ? window.escapeHtml(str) : String(str ?? '');
}

function resolveAllMineskins(list) {
    list.forEach((member) => {
        if (member.skin && member.skin.includes('mineskin.org/skins/')) {
            resolveMineskin(member.skin).then((resolvedUrl) => {
                if (resolvedUrl && resolvedUrl !== member.skin) {
                    member.skin = resolvedUrl;
                    const cardImg = document.querySelector(`#skin-${member.name.replace(/[^a-zA-Z0-9_-]/g, '')} img`);
                    if (cardImg) {
                        cardImg.src = getBodyRenderUrl(member);
                    }
                }
            });
        }
    });
}

function updateStaffJsonLd(members) {
    try {
        const schemaEl = document.getElementById('schemaStaffData');
        if (!schemaEl) return;
        const currentData = JSON.parse(schemaEl.textContent || '{}');
        if (!currentData.department) {
            currentData.department = { '@type': 'Organization', name: 'Staff Team' };
        }
        currentData.department.member = members.map((m) => ({
            '@type': 'Person',
            name: m.name,
            jobTitle: m.role
        }));
        schemaEl.textContent = JSON.stringify(currentData, null, 2);
    } catch (e) {
        console.warn('[Staff] Error updating JSON-LD schema:', e);
    }
}

function renderStaffGrid(list) {
    const staffGrid = document.getElementById('staffGrid');
    if (!staffGrid) return;
    staffGrid.className = '';
    staffGrid.innerHTML = '';

    const categoryOrder = [];
    const categories = {};
    list.forEach((m) => {
        const cat = m.category || 'Support';
        if (!categories[cat]) {
            categories[cat] = [];
            categoryOrder.push(cat);
        }
        categories[cat].push(m);
    });

    categoryOrder.forEach((cat) => {
        const group = categories[cat];
        if (!group || group.length === 0) return;

        const section = document.createElement('div');
        section.className = 'staff-category-section';
        section.innerHTML = `
            <div class="staff-category-header">
                <h2 class="staff-category-title">${escapeStaffHtml(cat)}</h2>
                <span class="category-count">${group.length} ${group.length === 1 ? 'Member' : 'Members'}</span>
            </div>
            <div class="staff-grid"></div>
        `;

        const grid = section.querySelector('.staff-grid');
        if (group.length === 4) {
            grid.classList.add('staff-grid-4');
        }

        group.forEach((member) => {
            const originalIndex = staffMembers.findIndex(m => m.name === member.name);
            const safeRoleClass = String(member.roleClass || 'role-officer').replace(/[^a-zA-Z0-9_-]/g, '');
            const safeName = escapeStaffHtml(member.name);
            const safeRole = escapeStaffHtml(member.role);
            const card = document.createElement('button');
            card.type = 'button';
            card.className = `staff-card glow-${safeRoleClass}`;
            card.setAttribute('data-index', originalIndex);
            card.setAttribute('aria-label', `View details for ${member.name}, ${member.role}`);

            const isBedrock = member.platform === 'bedrock' || member.name.startsWith('.');
            const fallbackName = (member.uuid || member.name).replace(/^\.+/, '');

            card.innerHTML = `
                <div class="staff-skin-container" id="skin-${member.name.replace(/[^a-zA-Z0-9_-]/g, '')}">
                    <img src="${getBodyRenderUrl(member)}"
                         alt="${safeName}"
                         class="staff-skin-img"
                         onerror="this.onerror=null;this.src='https://minotar.net/armor/body/${encodeURIComponent(fallbackName)}/180.png';" />
                </div>
                <div class="staff-name">${safeName}</div>
                <div class="staff-card-meta">
                    <span class="staff-role ${safeRoleClass}">${safeRole}</span>
                    <span class="platform-badge ${isBedrock ? 'bedrock' : 'java'}">
                        <i class="${isBedrock ? 'fa-solid fa-mobile-screen' : 'fa-brands fa-java'}"></i> ${isBedrock ? 'Bedrock' : 'Java'}
                    </span>
                </div>
            `;
            card.addEventListener('click', () => openStaffModal(originalIndex));
            grid.appendChild(card);
        });

        staffGrid.appendChild(section);
    });

    const statPillSpan = document.querySelector('.staff-header-stats .staff-stat-pill span');
    if (statPillSpan) {
        statPillSpan.textContent = `${list.length} Dedicated Staff`;
    }

    if (typeof window.initEnhancedReveal === 'function') {
        window.initEnhancedReveal();
    }
}

async function syncStaffFromSupabase() {
    try {
        let rows = null;
        let client = null;
        if (window.HavenCraftCore && typeof window.HavenCraftCore.getGallery === 'function') {
            client = window.HavenCraftCore.getGallery();
        } else if (window.supabaseGallery) {
            client = window.supabaseGallery;
        }

        if (client && client.from) {
            const { data, error } = await client
                .from('staff_members')
                .select('*')
                .eq('is_active', true)
                .order('order_index', { ascending: true });
            if (!error && Array.isArray(data) && data.length > 0) {
                rows = data;
            }
        }

        if (!rows) {
            const cfg = window.HavenCraftCore?.getSupabaseConfig?.('core');
            if (cfg && cfg.url && cfg.key) {
                const res = await fetch(`${cfg.url}/rest/v1/staff_members?select=*&is_active=eq.true&order=order_index.asc`, {
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
            staffMembers = rows.map((r) => ({
                id: r.id,
                name: r.name,
                category: r.category || 'Support',
                role: r.role || 'Officer',
                roleClass: r.role_class || `role-${(r.role || 'officer').toLowerCase().replace(/[^a-z0-9]/g, '-')}`,
                description: r.description || '',
                skin: r.skin || '',
                orderIndex: r.order_index || 0,
                platform: r.name.startsWith('.') ? 'bedrock' : 'java'
            }));
            window.STAFF_MEMBERS = staffMembers;
            renderStaffGrid(staffMembers);
            resolveAllMineskins(staffMembers);
            updateStaffJsonLd(staffMembers);
            window.dispatchEvent(new CustomEvent('hc_staff_loaded'));
        }
    } catch (err) {
        console.warn('[Staff] Supabase sync fallback to local cache:', err);
    }
}

renderStaffGrid(staffMembers);
resolveAllMineskins(staffMembers);
updateStaffJsonLd(staffMembers);

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', syncStaffFromSupabase);
} else {
    syncStaffFromSupabase();
}

let lastFocusedElement = null;
let modalSkinViewer = null;
const staffModal = document.getElementById('staffModal');
const closeStaffModal = document.getElementById('closeStaffModal');

function disposeSkinViewer() {
    if (modalSkinViewer) {
        try {
            if (modalSkinViewer.canvas) {
                const gl = modalSkinViewer.canvas.getContext('webgl2') || modalSkinViewer.canvas.getContext('webgl');
                if (gl && typeof gl.getExtension === 'function') {
                    const ext = gl.getExtension('WEBGL_lose_context');
                    if (ext) ext.loseContext();
                }
            }
            if (typeof modalSkinViewer.dispose === 'function') {
                modalSkinViewer.dispose();
            }
        } catch (e) {
            console.warn('[Staff] Error disposing 3D skin viewer:', e);
        }
        modalSkinViewer = null;
    }
    const modalSkinContainer = document.getElementById('staffModalSkinContainer');
    if (modalSkinContainer) modalSkinContainer.innerHTML = '';
}

async function openStaffModal(index) {
    const member = staffMembers[index];
    if (!member || !staffModal) return;

    lastFocusedElement = document.activeElement;
    disposeSkinViewer();

    const modalSkinContainer = document.getElementById('staffModalSkinContainer');
    const isBedrock = member.platform === 'bedrock' || member.name.startsWith('.');
    const safeAlt = escapeStaffHtml(member.name);

    modalSkinContainer.innerHTML = `
        <div class="staff-skin-spinner" id="modalSkinSpinner">
            <i class="fa-solid fa-spinner fa-spin"></i>
            <span>Loading 3D skin...</span>
        </div>
    `;

    if (member.skin && member.skin.includes('mineskin.org/skins/')) {
        member.skin = await resolveMineskin(member.skin);
    }

    try {
        modalSkinViewer = new skinview3d.SkinViewer({
            canvas: document.createElement('canvas'),
            width: 160,
            height: 240
        });

        const clearSpinner = () => {
            const spinner = document.getElementById('modalSkinSpinner');
            if (spinner) spinner.remove();
        };

        modalSkinContainer.appendChild(modalSkinViewer.canvas);
        modalSkinViewer.animation = new skinview3d.WalkingAnimation();
        modalSkinViewer.animation.speed = 0.5;
        modalSkinViewer.autoRotate = false;
        modalSkinViewer.controls.enableRotate = true;
        modalSkinViewer.controls.enableZoom = false;
        modalSkinViewer.controls.enablePan = false;
        modalSkinViewer.zoom = 0.95;

        const targetSkinUrl = getSkinTextureUrl(member);
        if (modalSkinViewer.loadSkin) {
            Promise.resolve(modalSkinViewer.loadSkin(targetSkinUrl))
                .then(clearSpinner)
                .catch(() => Promise.resolve(modalSkinViewer.loadSkin('https://minotar.net/skin/MHF_Steve')).then(clearSpinner))
                .catch(() => {
                    modalSkinContainer.innerHTML = `<img src="${getBodyRenderUrl(member)}" style="height:220px; width:auto;" alt="${safeAlt}" />`;
                });
            setTimeout(clearSpinner, 2500);
        } else {
            clearSpinner();
        }
    } catch (_) {
        modalSkinContainer.innerHTML = `<img src="${getBodyRenderUrl(member)}" style="height:220px; width:auto;" alt="${safeAlt}" />`;
    }

    const safeRoleClass = String(member.roleClass || 'role-officer').replace(/[^a-zA-Z0-9_-]/g, '');
    document.getElementById('staffModalName').textContent = member.name;
    document.getElementById('staffModalRole').innerHTML = `<span class="staff-role ${safeRoleClass}">${escapeStaffHtml(member.role)}</span>`;

    const platformEl = document.getElementById('staffModalPlatform');
    if (platformEl) {
        platformEl.innerHTML = `<span class="platform-badge ${isBedrock ? 'bedrock' : 'java'}"><i class="${isBedrock ? 'fa-solid fa-mobile-screen' : 'fa-brands fa-java'}"></i> ${isBedrock ? 'Bedrock Edition' : 'Java Edition'}</span>`;
    }

    const profileBtn = document.getElementById('staffModalProfileBtn');
    if (profileBtn) {
        profileBtn.href = `../profile/?player=${encodeURIComponent(member.name)}`;
    }

    document.getElementById('staffModalDesc').textContent = member.description;
    staffModal.classList.add('show');
    closeStaffModal?.focus();
}

function closeStaffModalFn() {
    staffModal?.classList.remove('show');
    disposeSkinViewer();
    if (lastFocusedElement) lastFocusedElement.focus();
}

function trapFocus(e) {
    const focusable = staffModal.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
    }
}

if (closeStaffModal) {
    closeStaffModal.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        closeStaffModalFn();
    });
}

if (staffModal) {
    staffModal.addEventListener('click', (e) => {
        if (e.target === staffModal || e.target.closest('#closeStaffModal') || e.target.closest('.close-modal')) {
            e.preventDefault();
            closeStaffModalFn();
        }
    });
    staffModal.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeStaffModalFn();
        if (e.key === 'Tab') trapFocus(e);
    });
}
