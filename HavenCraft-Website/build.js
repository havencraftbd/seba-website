/**
 * HavenCraft — Production CSS Minifier & Core JS Bundler
 * Usage: npm run build / npm run bundle / npm run watch
 */

const fs = require('fs');
const path = require('path');

let terser = null;
try {
    terser = require('terser');
} catch (_) {
    terser = null;
}

const ROOT = __dirname;
const DIST = path.join(ROOT, 'dist');

const CSS_FILES = [
    'assets/css/style.css',
    'assets/css/pages.css',
    'assets/css/profile.css',
    'assets/css/vote.css',
    'assets/css/store.css',
    'assets/css/leaderboard.css',
    'assets/css/commands.css',
    'assets/css/discord.css',
    'assets/css/support.css',
    'assets/css/contact.css',
];

const JS_BUNDLE_FILES = [
    'assets/js/core.js',
    'assets/js/utils.js',
    'assets/js/navbar.js',
    'assets/js/ticket-chat.js',
    'assets/js/components.js',
    'assets/js/leaderboard-core.js',
    'assets/js/leaderboard.js',
    'assets/js/home.js',
];

function minifyCSS(css) {
    return css
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\s+/g, ' ')
        .replace(/\s*{\s*/g, '{')
        .replace(/\s*}\s*/g, '}')
        .replace(/\s*;\s*/g, ';')
        .replace(/\s*:\s*/g, ':')
        .replace(/\s*,\s*/g, ',')
        .replace(/;}/g, '}')
        .trim();
}

async function minifyJS(js) {
    if (terser) {
        try {
            const minified = await terser.minify(js, {
                compress: {
                    drop_debugger: true,
                    dead_code: true
                },
                // Preserve global function and variable names for cross-file inline handlers
                mangle: false,
                format: {
                    comments: false
                }
            });
            return minified.code;
        } catch (err) {
            console.warn('   ⚠️  Terser minification encountered an error, falling back to safe packaging:', err.message);
        }
    }

    // Safe zero-dependency fallback avoids regex stripping that could corrupt URLs or regex literals
    return js
        .split('\n')
        .map(line => line.trimEnd())
        .filter(line => {
            const trimmed = line.trim();
            return trimmed.length > 0 && !trimmed.startsWith('//');
        })
        .join('\n')
        .trim();
}

async function build() {
    const startTime = Date.now();
    console.log('\n⚒️  HavenCraft Build Starting...\n');

    fs.mkdirSync(path.join(DIST, 'assets', 'css'), { recursive: true });
    fs.mkdirSync(path.join(DIST, 'assets', 'js'), { recursive: true });

    let totalSaved = 0;

    console.log('📦 CSS Minification:');
    CSS_FILES.forEach((file) => {
        const fullPath = path.join(ROOT, file);
        if (!fs.existsSync(fullPath)) {
            console.log(`   ⚠️  Skipped (not found): ${file}`);
            return;
        }

        const original = fs.readFileSync(fullPath, 'utf8');
        const minified = minifyCSS(original);
        const outputPath = path.join(DIST, file);

        fs.mkdirSync(path.dirname(outputPath), { recursive: true });
        fs.writeFileSync(outputPath, minified);

        const saved = original.length - minified.length;
        totalSaved += saved;
        const percent = ((saved / original.length) * 100).toFixed(1);
        console.log(`   ✅ ${file} — ${(original.length / 1024).toFixed(1)}KB → ${(minified.length / 1024).toFixed(1)}KB (${percent}% saved)`);
    });

    console.log('\n📦 JS Bundling:');
    let bundled = '';
    let totalOriginalJS = 0;
    JS_BUNDLE_FILES.forEach((file) => {
        const fullPath = path.join(ROOT, file);
        if (!fs.existsSync(fullPath)) {
            console.log(`   ⚠️  Skipped (not found): ${file}`);
            return;
        }
        const content = fs.readFileSync(fullPath, 'utf8');
        totalOriginalJS += content.length;
        bundled += `\n/* === ${file} === */\n${content}\n`;
        console.log(`   📄 Added: ${file} (${(content.length / 1024).toFixed(1)}KB)`);
    });

    const minifiedBundle = await minifyJS(bundled);
    const bundlePath = path.join(DIST, 'assets', 'js', 'bundle.min.js');
    fs.writeFileSync(bundlePath, minifiedBundle);

    try {
        new Function(minifiedBundle);
        console.log('   🔍 Bundle syntax validation: PASSED');
    } catch (syntaxErr) {
        console.error('   ❌ Bundle syntax validation FAILED:', syntaxErr.message);
        process.exit(1);
    }

    const jsSaved = totalOriginalJS - minifiedBundle.length;
    totalSaved += jsSaved;
    const jsPercent = ((jsSaved / totalOriginalJS) * 100).toFixed(1);
    console.log(`   ✅ Bundle: ${(totalOriginalJS / 1024).toFixed(1)}KB → ${(minifiedBundle.length / 1024).toFixed(1)}KB (${jsPercent}% saved)`);

    const elapsed = Date.now() - startTime;
    console.log(`\n🎉 Build complete in ${elapsed}ms`);
    console.log(`   Total savings: ${(totalSaved / 1024).toFixed(1)}KB`);
    console.log(`   Output: ${DIST}\n`);
}

if (process.argv.includes('--watch')) {
    console.log('👀 Watch mode enabled. Watching for changes...');
    build().then(() => {
        ['assets/css', 'assets/js'].forEach((dir) => {
            const fullDir = path.join(ROOT, dir);
            if (fs.existsSync(fullDir)) {
                fs.watch(fullDir, { recursive: true }, (_, filename) => {
                    if (filename && (filename.endsWith('.css') || filename.endsWith('.js'))) {
                        console.log(`\n🔄 Change detected: ${dir}/${filename}`);
                        build();
                    }
                });
            }
        });
    });
} else {
    build();
}
