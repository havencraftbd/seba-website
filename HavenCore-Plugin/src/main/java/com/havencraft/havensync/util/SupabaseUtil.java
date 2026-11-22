package com.havencraft.havensync.util;

/**
 * Shared utility methods for Supabase integration.
 */
public final class SupabaseUtil {

    private SupabaseUtil() {}

    /**
     * Builds the full Supabase REST URL from a base project URL and table name.
     * Handles multiple formats for backwards compatibility:
     *   - "https://xxx.supabase.co"                  → appends /rest/v1/{table}
     *   - "https://xxx.supabase.co/rest/v1"          → appends /{table}
     *   - "https://xxx.supabase.co/rest/v1/old_table" → replaces with /{table} if table is specified
     */
    public static String buildSupabaseUrl(String baseUrl, String tableName) {
        if (baseUrl == null) baseUrl = "";
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // If no table configured, use URL as-is (legacy full-URL mode)
        if (tableName == null || tableName.isEmpty()) {
            return trimmed;
        }

        // If URL already contains /rest/v1, strip anything after it and append table
        int restIndex = trimmed.indexOf("/rest/v1");
        if (restIndex >= 0) {
            return trimmed.substring(0, restIndex) + "/rest/v1/" + tableName;
        }

        // Base project URL only — append /rest/v1/ + table
        return trimmed + "/rest/v1/" + tableName;
    }
}
