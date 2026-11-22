package com.haven.shards.util;

public class TimeUtil {

    /**
     * Parses duration strings such as "30m", "1h", "2h", "1d", "7d", "30s", "45" into minutes.
     */
    public static int parseDurationToMinutes(String input) {
        if (input == null || input.isEmpty()) return 0;
        input = input.toLowerCase().trim();

        try {
            if (input.endsWith("d")) {
                double days = Double.parseDouble(input.substring(0, input.length() - 1));
                return (int) Math.round(days * 1440);
            } else if (input.endsWith("h")) {
                double hours = Double.parseDouble(input.substring(0, input.length() - 1));
                return (int) Math.round(hours * 60);
            } else if (input.endsWith("m")) {
                double mins = Double.parseDouble(input.substring(0, input.length() - 1));
                return (int) Math.round(mins);
            } else if (input.endsWith("s")) {
                double secs = Double.parseDouble(input.substring(0, input.length() - 1));
                if (secs <= 0) return 0;
                return Math.max(1, (int) Math.round(secs / 60.0));
            } else {
                return Integer.parseInt(input);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Formats duration into a human-readable String (e.g. 30m, 1h, 1d 2h).
     */
    public static String formatMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "0m";
        int days = totalMinutes / 1440;
        int hours = (totalMinutes % 1440) / 60;
        int mins = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0 || sb.length() == 0) sb.append(mins).append("m");

        return sb.toString().trim();
    }

    /**
     * Formats seconds into a human-readable String (e.g. 1d 2h 30m 15s).
     */
    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }
}
