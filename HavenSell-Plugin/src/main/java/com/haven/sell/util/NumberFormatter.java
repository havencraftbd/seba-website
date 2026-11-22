package com.haven.sell.util;

import java.util.List;

/**
 * Formats numbers into abbreviated form (K, M, B, T).
 * Example: 1500 → 1.5K, 1000000 → 1M
 */
public final class NumberFormatter {

    private NumberFormatter() {}

    private static final double[] THRESHOLDS = {
            1_000_000_000_000.0,
            1_000_000_000.0,
            1_000_000.0,
            1_000.0
    };

    /**
     * Formats a number using abbreviations if enabled.
     *
     * @param amount      The number to format
     * @param enabled     Whether abbreviations are enabled
     * @param suffixes    The suffix list [K, M, B, T] from config
     * @return Formatted string e.g. "1.5K" or "1500.00"
     */
    public static String format(double amount, boolean enabled, List<String> suffixes) {
        if (!enabled || suffixes == null || suffixes.isEmpty()) {
            return String.format("%.2f", amount);
        }

        // suffixes expected order from config: K, M, B, T
        // We match in descending order: T → B → M → K
        String[] orderedSuffixes;
        if (suffixes.size() >= 4) {
            orderedSuffixes = new String[]{
                    suffixes.get(3), // T
                    suffixes.get(2), // B
                    suffixes.get(1), // M
                    suffixes.get(0)  // K
            };
        } else {
            // Fallback defaults
            orderedSuffixes = new String[]{"T", "B", "M", "K"};
        }

        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (amount >= THRESHOLDS[i]) {
                double result = amount / THRESHOLDS[i];
                // Use 2 decimals for more precision, trim trailing zeros
                String formatted = trimDecimals(result);
                return formatted + orderedSuffixes[i];
            }
        }

        // Below 1K - show up to 2 decimal places, trim trailing zeros
        return trimDecimals(amount);
    }

    /**
     * Formats without abbreviation (plain decimal).
     */
    public static String formatPlain(double amount, int decimals) {
        return String.format("%." + decimals + "f", amount);
    }

    /**
     * Trims unnecessary trailing zeros.
     * E.g. 1.50 → 1.5, 1.00 → 1, 1.23 → 1.23
     */
    private static String trimDecimals(double value) {
        // Use up to 2 decimal places, remove trailing zeros
        String formatted = String.format("%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }
}
