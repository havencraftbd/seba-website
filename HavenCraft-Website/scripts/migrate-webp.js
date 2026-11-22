/**
 * HavenCraft — Gallery Image Migration to WebP
 * 
 * This script will:
 * 1. Connect to your Supabase project.
 * 2. Find all images in the 'gallery' table that are NOT .webp.
 * 3. Download them, convert them to optimized WebP using 'sharp'.
 * 4. Upload the new WebP images to the 'gallery' storage bucket.
 * 5. Update the database 'gallery' table with the new WebP URLs.
 * 6. (Optional) Delete the old heavy PNG/JPG files to free up space.
 * 
 * Prerequisites:
 * npm install @supabase/supabase-js sharp dotenv
 */

require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');
const sharp = require('sharp');

// Supabase Credentials (Loaded securely from environment variables / .env file)
const SUPABASE_URL = process.env.GALLERY_SUPABASE_URL;
const SUPABASE_SERVICE_KEY = process.env.GALLERY_SUPABASE_SERVICE_KEY;

if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
    console.error('❌ Error: Supabase URL or Service Key is missing!');
    console.error('👉 Please configure GALLERY_SUPABASE_URL and GALLERY_SUPABASE_SERVICE_KEY in your .env file or environment.');
    process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false }
});

async function migrateImages() {
    console.log('🚀 Starting WebP Migration...\n');

    // 1. Fetch all gallery records
    const { data: records, error: dbError } = await supabase
        .from('gallery')
        .select('*');

    if (dbError) {
        console.error('❌ Error fetching from database:', dbError.message);
        return;
    }

    // Filter only records that don't have .webp in their URL
    const imagesToConvert = records.filter(r => r.image_url && !r.image_url.toLowerCase().endsWith('.webp'));

    if (imagesToConvert.length === 0) {
        console.log('✅ No images need conversion! Everything is already WebP.');
        return;
    }

    const CONCURRENCY_LIMIT = parseInt(process.env.MIGRATION_CONCURRENCY, 10) || 3;
    console.log(`📦 Found ${imagesToConvert.length} images to convert (Processing ${CONCURRENCY_LIMIT} concurrently)...\n`);

    // Helper: Zero-dependency concurrency worker pool
    async function runConcurrent(items, limit, taskFn) {
        const results = [];
        let currentIndex = 0;

        const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
            while (currentIndex < items.length) {
                const index = currentIndex++;
                results[index] = await taskFn(items[index], index, items.length);
            }
        });

        await Promise.all(workers);
        return results;
    }

    async function processSingleImage(record, index, total) {
        const itemPrefix = `[${index + 1}/${total}]`;
        console.log(`⏳ ${itemPrefix} Processing: ${record.id} (Caption: "${record.caption || 'No caption'}")`);
        try {
            // 2. Download the original image with Timeout & Safety Guards
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 15000); // 15 seconds timeout
            let response;
            try {
                response = await fetch(record.image_url, { signal: controller.signal });
            } catch (fetchErr) {
                if (fetchErr.name === 'AbortError') {
                    throw new Error('Image download timed out (15s limit exceeded)');
                }
                throw fetchErr;
            } finally {
                clearTimeout(timeoutId);
            }

            if (!response.ok) throw new Error(`Failed to fetch image: ${response.statusText || response.status}`);

            // Safety Guard: Check Content-Length to prevent memory exhaustion (>15MB)
            const contentLength = response.headers.get('content-length');
            if (contentLength && parseInt(contentLength, 10) > 15 * 1024 * 1024) {
                throw new Error(`File too large (${(parseInt(contentLength, 10) / 1024 / 1024).toFixed(1)}MB > 15MB limit)`);
            }

            const arrayBuffer = await response.arrayBuffer();
            const originalBuffer = Buffer.from(arrayBuffer);

            // 3. Convert to WebP using sharp
            const webpBuffer = await sharp(originalBuffer)
                .webp({ quality: 82 })
                .toBuffer();

            const oldSizeKB = (originalBuffer.length / 1024).toFixed(1);
            const newSizeKB = (webpBuffer.length / 1024).toFixed(1);
            const savings = ((1 - webpBuffer.length / originalBuffer.length) * 100).toFixed(1);

            // 4. Generate new filename
            // Extract old filename safely from URL pathname (ignoring query parameters)
            let oldFilename = 'image.png';
            try {
                const pathname = new URL(record.image_url).pathname;
                const pathParts = pathname.split('/');
                oldFilename = pathParts[pathParts.length - 1] || 'image.png';
            } catch {
                const urlParts = record.image_url.split('/');
                oldFilename = urlParts[urlParts.length - 1] || 'image.png';
            }
            const lastDotIndex = oldFilename.lastIndexOf('.');
            const baseName = lastDotIndex > 0 ? oldFilename.substring(0, lastDotIndex) : oldFilename;
            const newFilename = `${baseName}.webp`;

            // 5. Upload WebP to Storage
            const { data: uploadData, error: uploadError } = await supabase.storage
                .from('gallery')
                .upload(newFilename, webpBuffer, {
                    contentType: 'image/webp',
                    upsert: true,
                    cacheControl: '3600'
                });

            if (uploadError) throw new Error(`Storage upload failed: ${uploadError.message}`);

            // 6. Get new Public URL
            const { data: urlData } = supabase.storage
                .from('gallery')
                .getPublicUrl(newFilename);
            const newUrl = urlData.publicUrl;

            // 7. Update Database Record
            const { error: updateError } = await supabase
                .from('gallery')
                .update({ image_url: newUrl })
                .eq('id', record.id);

            if (updateError) throw new Error(`Database update failed: ${updateError.message}`);

            console.log(`   ✨ ${itemPrefix} Success: ${oldSizeKB}KB → ${newSizeKB}KB (${savings}% saved)`);
            return { success: true, id: record.id };

        } catch (err) {
            console.error(`   ❌ ${itemPrefix} Failed to process ${record.id}:`, err.message);
            return { success: false, id: record.id, error: err.message };
        }
    }

    const results = await runConcurrent(imagesToConvert, CONCURRENCY_LIMIT, processSingleImage);

    const successCount = results.filter(r => r && r.success).length;
    const failedCount = results.filter(r => r && !r.success).length;

    console.log('\n🎉 Migration Complete!');
    console.log(`📊 Successfully converted: ${successCount}`);
    console.log(`⚠️ Failed: ${failedCount}`);
}

migrateImages();
