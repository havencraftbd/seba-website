# ==============================================================================
# PROGUARD OBFUSCATION CONFIGURATION FOR HAVENSHARDS
# ==============================================================================

# Do not shrink or remove classes; keep all resources and classes intact
-dontshrink

# Do not apply bytecode optimization that can conflict with Paper/Folia
-dontoptimize

# Ignore warnings from external shaded libraries
-dontwarn **
-ignorewarnings

# Keep Bukkit/Java metadata and annotations intact
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ------------------------------------------------------------------------------
# 1. Main Plugin Entrypoint (Must remain public for Paper to load)
# ------------------------------------------------------------------------------
-keep public class com.haven.shards.HavenShards {
    public <methods>;
}

# ------------------------------------------------------------------------------
# 2. Public API (Used by AuctionHouse and external plugins)
# ------------------------------------------------------------------------------
-keep class com.haven.shards.api.** {
    public *;
}

# ------------------------------------------------------------------------------
# 3. Event Listeners & Handlers (Required for Paper/Spigot reflection)
# ------------------------------------------------------------------------------
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler *;
    public <methods>;
}

-keep class * extends org.bukkit.event.Event {
    public *;
}

-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}

-keep class com.haven.shards.listener.** {
    public <methods>;
}

# ------------------------------------------------------------------------------
# 4. Commands & GUI Holders
# ------------------------------------------------------------------------------
-keep class com.haven.shards.command.** {
    public <methods>;
}

-keep class com.haven.shards.gui.** {
    public <methods>;
}

# ------------------------------------------------------------------------------
# 5. Data Models & Enums
# ------------------------------------------------------------------------------
-keep class com.haven.shards.model.** {
    public <methods>;
    public <fields>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public <fields>;
}

# ------------------------------------------------------------------------------
# 6. Shaded Third-Party Libraries
# ------------------------------------------------------------------------------
-keep class com.haven.shards.lib.** { *; }
-keep class com.zaxxer.hikari.** { *; }
-keep class net.wesjd.anvilgui.** { *; }
-keep class org.slf4j.** { *; }

# ------------------------------------------------------------------------------
# 7. Managers & Core Services (Critical for external plugin reflection & hooks)
# ------------------------------------------------------------------------------
-keep class com.haven.shards.manager.** {
    public <methods>;
    public <fields>;
}

-keep class com.haven.shards.manager.DatabaseManager$TopEntry {
    public <methods>;
    public <fields>;
}

# ------------------------------------------------------------------------------
# 8. Hooks & External Integrations
# ------------------------------------------------------------------------------
-keep class com.haven.shards.hook.** {
    public <methods>;
}

# ------------------------------------------------------------------------------
# 9. Obfuscate internal utility implementations
# ------------------------------------------------------------------------------
-repackageclasses 'com.haven.shards.internal'
