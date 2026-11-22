/* ============================================
   HavenCraft — Dynamic Supabase Gallery
   ============================================ */

const fallbackData = [
    ['../images/community.webp', 'Active HavenCraft Community'],
    ['../images/survival.webp', 'Balanced Survival World'],
    ['../images/gameplay.webp', 'Fair Gameplay & PvP'],
    ['../images/community.webp', 'Community Infrastructure'],
    ['../images/survival.webp', 'Custom Terrain & Builds'],
    ['../images/gameplay.webp', 'Player Events & Gatherings']
];

let supabaseClient = null;
let allGalleryItems = [];
let hasMore = true;
const ITEMS_PER_PAGE = 6;
const cacheKey = 'hvn_gallery_supabase_cache_v1';

const escapeGalleryText = (text) => (typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || ''));

function initSupabase() {
    if (window.HavenCraftCore) {
        supabaseClient = window.HavenCraftCore.getGallery();
        return !!supabaseClient;
    }
    return false;
}

function showSkeletons(container) {
    container.innerHTML = '';
    for (let i = 0; i < ITEMS_PER_PAGE; i++) {
        const skeleton = document.createElement('div');
        skeleton.className = 'gallery-skeleton-card loading-skeleton';
        container.appendChild(skeleton);
    }
}

function createGalleryItemNode(url, caption) {
    const item = document.createElement('button');
    item.type = 'button';
    item.className = 'gallery-item reveal';
    item.style.cursor = 'zoom-in';
    item.setAttribute('aria-label', caption ? `View screenshot: ${caption}` : 'View community screenshot');

    const safeCaption = escapeGalleryText(caption);
    const captionHtml = caption
        ? `<div class="gallery-caption"><span>${safeCaption}</span></div>`
        : '';

    item.innerHTML = `
        <img src="${escapeGalleryText(url)}" alt="${safeCaption || 'Screenshot'}"
             onload="this.classList.add('loaded'); if(this.parentElement) this.parentElement.classList.add('img-loaded');"
             onerror="this.parentNode.style.display='none'">
        ${captionHtml}
    `;
    return item;
}

function renderGalleryItems({ showLoadMore = false, emptyFallback = false } = {}) {
    const galleryGrid = document.getElementById('dynamicGalleryGrid');
    const loadMoreContainer = document.getElementById('loadMoreContainer');
    if (!galleryGrid) return;

    galleryGrid.innerHTML = '';

    if (emptyFallback && allGalleryItems.length === 0) {
        galleryGrid.innerHTML = `
            <div style="text-align: center; grid-column: 1 / -1; padding: 40px; color: var(--text-secondary);">
                <i class="fa-regular fa-image" style="font-size: 2.5rem; margin-bottom: 15px; display: block;"></i>
                <p>No screenshots are available in the gallery yet.</p>
            </div>
        `;
    } else {
        allGalleryItems.forEach((item) => {
            galleryGrid.appendChild(createGalleryItemNode(item.imageURL, item.caption));
        });
    }

    if (loadMoreContainer) {
        loadMoreContainer.style.display = showLoadMore ? 'block' : 'none';
    }

    if (typeof window.initEnhancedReveal === 'function') {
        window.initEnhancedReveal();
    }
    setupGalleryLightbox();
}

async function loadGallery() {
    const galleryGrid = document.getElementById('dynamicGalleryGrid');
    if (!galleryGrid) return;

    if (!initSupabase()) {
        allGalleryItems = fallbackData.map(([imageURL, caption]) => ({ imageURL, caption }));
        renderGalleryItems({ showLoadMore: false });
        return;
    }

    let cachedData = null;
    try {
        cachedData = localStorage.getItem(cacheKey);
    } catch (_) {}

    if (cachedData) {
        try {
            allGalleryItems = JSON.parse(cachedData);
            renderGalleryItems({ showLoadMore: allGalleryItems.length >= ITEMS_PER_PAGE });
        } catch (e) {
            console.warn('Failed to parse cached items, showing skeletons', e);
            showSkeletons(galleryGrid);
        }
    } else {
        showSkeletons(galleryGrid);
    }

    try {
        const { data, error } = await supabaseClient
            .from('gallery')
            .select('id, image_url, caption')
            .eq('status', 'approved')
            .order('created_at', { ascending: false })
            .range(0, ITEMS_PER_PAGE - 1);

        if (error) throw error;

        const freshItems = (data || []).map((item) => ({
            id: item.id,
            imageURL: item.image_url,
            caption: item.caption || ''
        }));

        hasMore = freshItems.length === ITEMS_PER_PAGE;
        try {
            localStorage.setItem(cacheKey, JSON.stringify(freshItems));
        } catch (_) {}

        if (JSON.stringify(freshItems) !== cachedData) {
            allGalleryItems = freshItems;
            renderGalleryItems({ showLoadMore: hasMore, emptyFallback: true });
        }
    } catch (error) {
        console.warn('Supabase Fetch Error (falling back to default gallery):', error);
        if (!cachedData) {
            allGalleryItems = fallbackData.map(([imageURL, caption]) => ({ imageURL, caption }));
            renderGalleryItems({ showLoadMore: false });
        }
    }
}

async function initPagination() {
    const loadMoreBtn = document.getElementById('loadMoreBtn');
    const galleryGrid = document.getElementById('dynamicGalleryGrid');
    const loadMoreContainer = document.getElementById('loadMoreContainer');

    if (!loadMoreBtn || !galleryGrid) return;

    loadMoreBtn.addEventListener('click', async () => {
        if (!supabaseClient || !hasMore) return;

        const originalText = loadMoreBtn.innerHTML;
        loadMoreBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate fa-spin"></i> Loading...';
        loadMoreBtn.disabled = true;

        try {
            const nextOffset = allGalleryItems.length;
            const { data, error } = await supabaseClient
                .from('gallery')
                .select('id, image_url, caption')
                .eq('status', 'approved')
                .order('created_at', { ascending: false })
                .range(nextOffset, nextOffset + ITEMS_PER_PAGE - 1);

            if (error) throw error;

            if (!data || data.length === 0) {
                hasMore = false;
                if (loadMoreContainer) loadMoreContainer.style.display = 'none';
                return;
            }

            data.forEach((item) => {
                const galleryItem = {
                    id: item.id,
                    imageURL: item.image_url,
                    caption: item.caption || ''
                };
                allGalleryItems.push(galleryItem);
                galleryGrid.appendChild(createGalleryItemNode(galleryItem.imageURL, galleryItem.caption));
            });

            hasMore = data.length === ITEMS_PER_PAGE;
            if (loadMoreContainer) {
                loadMoreContainer.style.display = hasMore ? 'block' : 'none';
            }

            if (typeof window.initEnhancedReveal === 'function') {
                window.initEnhancedReveal();
            }
            setupGalleryLightbox();
        } catch (error) {
            console.error('Pagination Error:', error);
        } finally {
            loadMoreBtn.innerHTML = originalText;
            loadMoreBtn.disabled = false;
        }
    });
}

function setupGalleryLightbox() {
    const galleryGrid = document.getElementById('dynamicGalleryGrid');
    const lightbox = document.getElementById('lightboxOverlay');
    const lightboxImg = document.getElementById('lightboxImg');
    const lightboxCaption = document.getElementById('lightboxCaption');

    if (!galleryGrid || !lightbox || !lightboxImg || galleryGrid._lightboxInitialized) return;
    galleryGrid._lightboxInitialized = true;

    galleryGrid.addEventListener('click', (e) => {
        const item = e.target.closest('.gallery-item');
        if (!item) return;

        const img = item.querySelector('img');
        const caption = item.querySelector('.gallery-caption');
        if (img && img.style.display !== 'none' && img.src) {
            lightboxImg.src = img.src;
            if (lightboxCaption) {
                lightboxCaption.textContent = caption ? caption.textContent.trim() : '';
            }
            lightbox.style.display = 'flex';
            lightbox.setAttribute('aria-hidden', 'false');
        }
    });

    lightbox.addEventListener('click', (e) => {
        if (e.target !== lightboxImg) {
            lightbox.style.display = 'none';
            lightbox.setAttribute('aria-hidden', 'true');
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && lightbox.style.display === 'flex') {
            lightbox.style.display = 'none';
            lightbox.setAttribute('aria-hidden', 'true');
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    loadGallery();
    initPagination();
    setupGalleryLightbox();
});
