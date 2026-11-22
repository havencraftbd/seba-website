# ==============================================================================
# PROGUARD OBFUSCATION CONFIGURATION FOR HAVENSELL
# ==============================================================================

# Do not shrink or remove classes; keep all resources and classes intact
-dontshrink

# Do not apply bytecode optimization that can conflict with Paper/Folia
-dontoptimize

# Ignore warnings and notes from external shaded libraries
-dontwarn **
-dontnote **
-ignorewarnings

# Adapt class strings for reflection
-adaptclassstrings

# Keep vital attributes and annotations for Paper / Bukkit
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# ------------------------------------------------------------------------------
# 1. Main Plugin Entrypoint
# ------------------------------------------------------------------------------
-keep public class com.haven.sell.HavenSell {
    public *;
    protected *;
}

# ------------------------------------------------------------------------------
# 2. Event Listeners and Handlers
# ------------------------------------------------------------------------------
-keep class * implements org.bukkit.event.Listener { *; }
-keepclassmembers class * {
    @org.bukkit.event.EventHandler *;
}

# ------------------------------------------------------------------------------
# 3. Command Executors & Tab Completers
# ------------------------------------------------------------------------------
-keep class * implements org.bukkit.command.CommandExecutor { *; }
-keep class * implements org.bukkit.command.TabCompleter { *; }

# ------------------------------------------------------------------------------
# 4. GUI & Inventory Holders
# ------------------------------------------------------------------------------
-keep class * implements org.bukkit.inventory.InventoryHolder { *; }
-keep class com.haven.sell.gui.** {
    public <methods>;
}

# ------------------------------------------------------------------------------
# 5. Data Models & Enums
# ------------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public <fields>;
}

# ------------------------------------------------------------------------------
# 6. Shaded Libraries (HikariCP, etc.)
# ------------------------------------------------------------------------------
-keep class com.haven.sell.libs.** { *; }

# ------------------------------------------------------------------------------
# 7. PlaceholderAPI Expansion
# ------------------------------------------------------------------------------
-keep class * extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
    public *;
}
