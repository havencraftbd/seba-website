package com.havencraft.havencore.combat;

import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles events for PvP combat tagging, command blocking, teleport restrictions, and combat logging penalties.
 */
public class CombatListener implements Listener {

    private final CombatManager combatManager;

    public CombatListener(CombatManager combatManager) {
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!combatManager.isEnabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Skip if this damage was part of an internal combat-log quit execution
        if (combatManager.isPendingQuitKill(victim.getUniqueId())) return;

        // Ignore attacks with zero base damage (e.g. snowballs, eggs, fishing hooks) to prevent trolling
        if (event.getDamage() <= 0.0D) return;

        Player attacker = getAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        // Apply combat tag to both victim and attacker if in an enabled world
        combatManager.tagPlayer(victim, attacker);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!combatManager.isInCombat(player)) return;

        if (combatManager.isCommandBlocked(event.getMessage())) {
            event.setCancelled(true);
            TextUtil.sendMessage(player, combatManager.getPrefix() + combatManager.getMsgCommandBlocked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!combatManager.isBlockTeleportEvents()) return;

        Player player = event.getPlayer();

        if (!combatManager.isInCombat(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        // Always block command escapes, portals, and chorus fruits
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
            cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
            cause == PlayerTeleportEvent.TeleportCause.END_PORTAL ||
            cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {

            event.setCancelled(true);
            TextUtil.sendMessage(player, combatManager.getPrefix() + combatManager.getMsgTeleportBlocked());
            return;
        }

        // Smart hybrid handling for PLUGIN and UNKNOWN:
        // Anti-cheats (GrimAC, Vulcan, etc.) setback / rubberband hackers using short PLUGIN/UNKNOWN teleports.
        // We allow short-distance setbacks (<= 8 blocks, 64 distanceSquared) and block long-distance/cross-world escape teleports.
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN ||
            cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {

            if (event.getTo() != null && event.getFrom() != null) {
                if (event.getFrom().getWorld() != null && event.getTo().getWorld() != null &&
                    event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                    if (event.getFrom().distanceSquared(event.getTo()) <= 64.0D) {
                        return; // Allow anti-cheat setbacks
                    }
                }
            }

            event.setCancelled(true);
            TextUtil.sendMessage(player, combatManager.getPrefix() + combatManager.getMsgTeleportBlocked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Fix "Player is already dead" black-screen/disconnect glitch on reconnect
        if (combatManager.isCombatLogged(uuid) || player.isDead() || player.getHealth() <= 0.0) {
            combatManager.removeCombatLogged(uuid);
            Bukkit.getScheduler().runTask(combatManager.getPlugin(), () -> {
                if (player.isOnline() && (player.isDead() || player.getHealth() <= 0.0)) {
                    try {
                        player.spigot().respawn();
                    } catch (Throwable ignored) {
                        // Handled if player already respawned
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            if (combatManager.isKillOnLogout()) {
                handleCombatLogDeath(player);
            }
            combatManager.removeTag(player);
        }
    }

    private void handleCombatLogDeath(Player player) {
        combatManager.markCombatLogged(player.getUniqueId());

        Player attacker = combatManager.getLastAttacker(player);

        // 1. Broadcast combat log notification
        String logMsg = combatManager.getMsgCombatLogged();
        if (logMsg != null && !logMsg.isBlank()) {
            String formatted = logMsg.replace("{player}", player.getName());
            if (attacker != null) {
                formatted = formatted.replace("{attacker}", attacker.getName());
            } else {
                formatted = formatted.replace("{attacker}", "Unknown");
            }
            Bukkit.broadcast(TextUtil.parseLegacy(combatManager.getPrefix() + formatted));
        }

        // 2. Deal lethal damage attributed to the attacker so Paper fires PlayerDeathEvent & killer stats
        combatManager.addPendingQuitKill(player.getUniqueId());
        try {
            double lethalDamage = Math.max(player.getHealth() + 1000.0, 1000.0);
            if (attacker != null && attacker.isOnline() && !attacker.equals(player)) {
                player.damage(lethalDamage, attacker);
            } else {
                player.damage(lethalDamage);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[HavenCore] Error attributing combat log damage: " + t.getMessage());
        } finally {
            combatManager.removePendingQuitKill(player.getUniqueId());
        }

        // 3. Fail-safe: if totem or external plugin event prevented death, force-drop and set health to 0
        if (!player.isDead()) {
            World world = player.getWorld();
            boolean keepInv = Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY));
            if (!keepInv) {
                Location dropLoc = player.getLocation();
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        world.dropItemNaturally(dropLoc, item);
                    }
                }
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
            }
            player.setHealth(0.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        combatManager.removeTag(event.getEntity());
        combatManager.removeCombatLogged(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!combatManager.isDisableElytra()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.isGliding() && combatManager.isInCombat(player)) {
            event.setCancelled(true);
        }
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        // 1. Direct melee hit (Sword, Axe, Fist, Mace)
        if (damager instanceof Player p) {
            return p;
        }

        // 2. Projectiles (Arrows, Tridents, ThrownPotions, WindCharges)
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        // 3. TNT Primed
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player shooter) {
            return shooter;
        }

        // 4. Area Effect Cloud (Lingering Potions)
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player shooter) {
            return shooter;
        }

        // 5. Firework Rockets (Crossbows)
        if (damager instanceof Firework firework && firework.getShooter() instanceof Player shooter) {
            return shooter;
        }

        // 6. Pets / Tamed animals (Wolves, etc.)
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }

        // 7. Paper 1.21 DamageSource API (End Crystals, Wind Charges, indirect damage)
        try {
            DamageSource damageSource = event.getDamageSource();
            if (damageSource != null) {
                Entity causingEntity = damageSource.getCausingEntity();
                if (causingEntity instanceof Player p) {
                    return p;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
