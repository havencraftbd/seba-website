/**
 * HavenCraft — Vote Validation Utility
 * 
 * Supports:
 * - Java Edition: 3-16 characters (letters, numbers, underscores)
 * - Bedrock / Geyser / Floodgate Edition: 3-24 characters (supports prefixes '.', '*', spaces, and underscores)
 * - Blocks symbol-only junk like '+++', '***', or '   '
 */

function isValidUsername(username) {
    if (typeof username !== 'string') return false;
    const trimmed = username.trim();

    // Length check: 3 to 24 characters
    if (trimmed.length < 3 || trimmed.length > 24) return false;

    // Must contain at least one alphanumeric character (prevents pure symbol spam)
    if (!/[a-zA-Z0-9]/.test(trimmed)) return false;

    // Allowed: letters, numbers, underscores, single spaces, optional Floodgate prefixes (. * + -)
    const MC_USERNAME_REGEX = /^[a-zA-Z0-9_.+*-](?:[a-zA-Z0-9_.+*-]| (?! )){1,22}[a-zA-Z0-9_.+*-]?$/;
    return MC_USERNAME_REGEX.test(trimmed);
}

/**
 * HTML Sanitization / Escape
 * Uses global escapeHtml if available, otherwise safely escapes special characters.
 */
function sanitizeText(str) {
    if (typeof window !== 'undefined' && typeof window.escapeHtml === 'function') {
        return window.escapeHtml(str);
    }
    if (!str && str !== 0) return '';
    return str.toString()
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Expose globally for vote modules
if (typeof window !== 'undefined') {
    window.isValidUsername = isValidUsername;
    window.sanitizeText = sanitizeText;
}
