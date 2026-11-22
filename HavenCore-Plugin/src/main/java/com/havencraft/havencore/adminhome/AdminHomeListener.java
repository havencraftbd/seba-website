package com.havencraft.havencore.adminhome;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.security.BetterTeamsHook;
import com.havencraft.havencore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminHomeListener implements Listener {

    private final HavenCorePlugin plugin;
    private final Map<UUID, AdminSession> sessions = new ConcurrentHashMap<>();

    public AdminHomeListener(HavenCorePlugin plugin) {
        this.plugin = plugin;
    }

    private String getPrefix() {
        return plugin.getConfig().getString("admin-home.prefix", plugin.getGlobalPrefix());
    }

    private Material getMaterial(String path, Material fallback) {
        String name = plugin.getConfig().getString("admin-home.items." + path);
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + name + "' configured at admin-home.items." + path + ". Using fallback " + fallback.name());
            return fallback;
        }
    }

    private List<Component> getLore(String path, Map<String, String> placeholders) {
        List<String> lines = plugin.getConfig().getStringList("admin-home.lore." + path);
        List<Component> componentLines = new ArrayList<>();
        for (String line : lines) {
            String processed = line;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    processed = processed.replace(entry.getKey(), entry.getValue());
                }
            }
            componentLines.add(TextUtil.parseLegacy(processed));
        }
        return componentLines;
    }

    /**
     * Opens the Main Home List GUI for the administrator (showing personal Essentials homes + Team Home).
     */
    public void openHomeListGui(Player admin, OfflinePlayer target) {
        Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        List<String> homes = new ArrayList<>();

        if (ess != null) {
            User essUser = ess.getUser(target.getUniqueId());
            if (essUser != null && essUser.getHomes() != null) {
                homes.addAll(essUser.getHomes());
            }
        }

        // Check if player belongs to a BetterTeams team with a home set
        BetterTeamsHook btHook = plugin.getBetterTeamsHook();
        Object team = null;
        String teamName = null;
        Location teamHomeLoc = null;

        if (btHook.isAvailable()) {
            team = btHook.getTeam(target);
            if (team != null) {
                teamName = btHook.getTeamName(team);
                teamHomeLoc = btHook.getTeamHome(team);
            }
        }

        boolean hasTeamHome = (teamHomeLoc != null);

        if (homes.isEmpty() && !hasTeamHome) {
            TextUtil.sendMessage(admin, getPrefix() + "&cNo homes found for player &e" + (target.getName() != null ? target.getName() : "Unknown"));
            return;
        }

        // Store session
        AdminSession session = new AdminSession(admin.getUniqueId(), target.getUniqueId(), target.getName());
        if (team != null) {
            session.setTeamObject(team);
            session.setTeamName(teamName);
        }
        sessions.put(admin.getUniqueId(), session);

        // Calculate size: multiple of 9, max 54
        int totalItems = homes.size() + (hasTeamHome ? 1 : 0);
        int size = (int) Math.ceil(totalItems / 9.0) * 9;
        size = Math.max(9, Math.min(54, size));

        String titleFormat = plugin.getConfig().getString("admin-home.titles.list", "&8Homes: &0{player}");
        AdminHomeListHolder holder = new AdminHomeListHolder();
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parseLegacy(titleFormat.replace("{player}", target.getName() != null ? target.getName() : "Player")));
        holder.setInventory(inv);

        // Create premium glass filler
        Material fillerMat = getMaterial("filler-glass", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        Material compassMat = getMaterial("home-compass", Material.COMPASS);

        // Populate Essentials homes
        int slotIndex = 0;
        if (ess != null) {
            User essUser = ess.getUser(target.getUniqueId());
            for (String homeName : homes) {
                if (slotIndex >= size) break;

                Location loc = null;
                if (essUser != null) {
                    try {
                        loc = essUser.getHome(homeName);
                    } catch (Exception ignored) {}
                }

                ItemStack item = new ItemStack(compassMat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parseLegacy("&e" + homeName));

                    Map<String, String> placeholders = new HashMap<>();
                    if (loc != null && loc.getWorld() != null) {
                        placeholders.put("{world}", loc.getWorld().getName());
                        placeholders.put("{x}", String.format("%.1f", loc.getX()));
                        placeholders.put("{y}", String.format("%.1f", loc.getY()));
                        placeholders.put("{z}", String.format("%.1f", loc.getZ()));
                        placeholders.put("{yaw}", String.valueOf(Math.round(loc.getYaw())));
                        placeholders.put("{pitch}", String.valueOf(Math.round(loc.getPitch())));
                    } else {
                        placeholders.put("{world}", "N/A");
                        placeholders.put("{x}", "N/A");
                        placeholders.put("{y}", "N/A");
                        placeholders.put("{z}", "N/A");
                        placeholders.put("{yaw}", "N/A");
                        placeholders.put("{pitch}", "N/A");
                    }

                    meta.lore(getLore("home-compass", placeholders));
                    item.setItemMeta(meta);
                }
                inv.setItem(slotIndex++, item);
            }
        }

        // Populate BetterTeams Team Home if present
        if (hasTeamHome && slotIndex < size) {
            Material teamHomeMat = getMaterial("team-home", Material.BEACON);
            ItemStack teamItem = new ItemStack(teamHomeMat);
            ItemMeta teamMeta = teamItem.getItemMeta();
            if (teamMeta != null) {
                String title = plugin.getConfig().getString("admin-home.team-home.title", "&6&lTeam Home &7({team})");
                teamMeta.displayName(TextUtil.parseLegacy(title.replace("{team}", teamName != null ? teamName : "Team")));

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("{team}", teamName != null ? teamName : "Unknown");
                if (teamHomeLoc.getWorld() != null) {
                    placeholders.put("{world}", teamHomeLoc.getWorld().getName());
                    placeholders.put("{x}", String.format("%.1f", teamHomeLoc.getX()));
                    placeholders.put("{y}", String.format("%.1f", teamHomeLoc.getY()));
                    placeholders.put("{z}", String.format("%.1f", teamHomeLoc.getZ()));
                    placeholders.put("{yaw}", String.valueOf(Math.round(teamHomeLoc.getYaw())));
                    placeholders.put("{pitch}", String.valueOf(Math.round(teamHomeLoc.getPitch())));
                } else {
                    placeholders.put("{world}", "N/A");
                    placeholders.put("{x}", "N/A");
                    placeholders.put("{y}", "N/A");
                    placeholders.put("{z}", "N/A");
                    placeholders.put("{yaw}", "N/A");
                    placeholders.put("{pitch}", "N/A");
                }

                teamMeta.lore(getLore("team-home", placeholders));
                teamItem.setItemMeta(teamMeta);
            }
            inv.setItem(slotIndex, teamItem);
        }

        admin.openInventory(inv);
    }

    /**
     * Opens the Sub-GUI for Actions directly on a specific team.
     */
    public void openTeamActionGui(Player admin, String teamName) {
        BetterTeamsHook btHook = plugin.getBetterTeamsHook();
        if (!btHook.isAvailable()) {
            TextUtil.sendMessage(admin, getPrefix() + "&cBetterTeams plugin is not enabled on this server.");
            return;
        }

        Object team = btHook.getTeam(teamName);
        if (team == null) {
            TextUtil.sendMessage(admin, getPrefix() + "&cTeam not found: &e" + teamName);
            return;
        }

        String realTeamName = btHook.getTeamName(team);
        if (realTeamName == null) realTeamName = teamName;

        Location homeLoc = btHook.getTeamHome(team);
        if (homeLoc == null) {
            TextUtil.sendMessage(admin, getPrefix() + "&cTeam &e" + realTeamName + " &cdoes not have a team home set.");
            return;
        }

        AdminSession session = new AdminSession(admin.getUniqueId(), realTeamName, team);
        sessions.put(admin.getUniqueId(), session);
        openActionGui(admin, session, realTeamName, true);
    }

    /**
     * Opens the Sub-GUI for Actions on a specific home or team home.
     */
    public void openActionGui(Player admin, AdminSession session, String homeName, boolean isTeamHome) {
        session.setTeamHome(isTeamHome);
        session.setCurrentHome(homeName);

        String titleFormat;
        if (isTeamHome) {
            titleFormat = plugin.getConfig().getString("admin-home.titles.team-action", "&8Team Home: &0{team}");
            titleFormat = titleFormat.replace("{team}", session.getTeamName() != null ? session.getTeamName() : homeName);
        } else {
            titleFormat = plugin.getConfig().getString("admin-home.titles.action", "&8Home Action: &0{home}");
            titleFormat = titleFormat.replace("{home}", homeName);
        }

        AdminHomeActionHolder holder = new AdminHomeActionHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parseLegacy(titleFormat));
        holder.setInventory(inv);

        // Fill with border glass
        Material fillerMat = getMaterial("filler-glass", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 10: Teleport Admin
        Material tpAdminMat = getMaterial("tp-admin", Material.ENDER_PEARL);
        ItemStack tpAdmin = new ItemStack(tpAdminMat);
        ItemMeta tpAdminMeta = tpAdmin.getItemMeta();
        if (tpAdminMeta != null) {
            tpAdminMeta.displayName(TextUtil.parseLegacy(isTeamHome ? "&a&lTeleport to Team Home" : "&a&lTeleport to Home"));
            tpAdminMeta.lore(getLore("tp-admin", null));
            tpAdmin.setItemMeta(tpAdminMeta);
        }
        inv.setItem(10, tpAdmin);

        // Slot 12: Send Player / Send Team
        if (isTeamHome) {
            Material sendTeamMat = getMaterial("send-team", Material.MINECART);
            ItemStack sendTeam = new ItemStack(sendTeamMat);
            ItemMeta sendTeamMeta = sendTeam.getItemMeta();
            if (sendTeamMeta != null) {
                sendTeamMeta.displayName(TextUtil.parseLegacy("&b&lSend Team to Home"));
                sendTeamMeta.lore(getLore("send-team", Map.of("{team}", session.getTeamName() != null ? session.getTeamName() : "Team")));
                sendTeam.setItemMeta(sendTeamMeta);
            }
            inv.setItem(12, sendTeam);
        } else {
            Material sendPlayerMat = getMaterial("send-player", Material.MINECART);
            ItemStack sendPlayer = new ItemStack(sendPlayerMat);
            ItemMeta sendPlayerMeta = sendPlayer.getItemMeta();
            if (sendPlayerMeta != null) {
                sendPlayerMeta.displayName(TextUtil.parseLegacy("&b&lSend Player to Home"));
                sendPlayerMeta.lore(getLore("send-player", Map.of("{player}", session.getTargetName() != null ? session.getTargetName() : "Player")));
                sendPlayer.setItemMeta(sendPlayerMeta);
            }
            inv.setItem(12, sendPlayer);
        }

        // Slot 14: Update Location
        Material updateLocMat = getMaterial("update-loc", Material.ANVIL);
        ItemStack updateLoc = new ItemStack(updateLocMat);
        ItemMeta updateLocMeta = updateLoc.getItemMeta();
        if (updateLocMeta != null) {
            updateLocMeta.displayName(TextUtil.parseLegacy(isTeamHome ? "&d&lUpdate Team Home Location" : "&d&lUpdate Home Location"));
            updateLocMeta.lore(getLore(isTeamHome ? "update-team-loc" : "update-loc", isTeamHome ? Map.of("{team}", session.getTeamName() != null ? session.getTeamName() : "Team") : null));
            updateLoc.setItemMeta(updateLocMeta);
        }
        inv.setItem(14, updateLoc);

        // Slot 16: Delete Home
        Material deleteHomeMat = getMaterial("delete-home", Material.TNT);
        ItemStack deleteHome = new ItemStack(deleteHomeMat);
        ItemMeta deleteHomeMeta = deleteHome.getItemMeta();
        if (deleteHomeMeta != null) {
            deleteHomeMeta.displayName(TextUtil.parseLegacy(isTeamHome ? "&c&lDelete Team Home" : "&c&lDelete Home"));
            deleteHomeMeta.lore(getLore(isTeamHome ? "delete-team-home" : "delete-home", isTeamHome ? Map.of("{team}", session.getTeamName() != null ? session.getTeamName() : "Team") : null));
            deleteHome.setItemMeta(deleteHomeMeta);
        }
        inv.setItem(16, deleteHome);

        // Slot 18: Go Back
        Material goBackMat = getMaterial("go-back", Material.ARROW);
        ItemStack goBack = new ItemStack(goBackMat);
        ItemMeta goBackMeta = goBack.getItemMeta();
        if (goBackMeta != null) {
            goBackMeta.displayName(TextUtil.parseLegacy("&e&lGo Back"));
            goBackMeta.lore(getLore("go-back", null));
            goBack.setItemMeta(goBackMeta);
        }
        inv.setItem(18, goBack);

        admin.openInventory(inv);
    }

    /**
     * Opens the Confirmation GUI before deletion.
     */
    public void openConfirmationGui(Player admin, AdminSession session) {
        String titleFormat = plugin.getConfig().getString("admin-home.titles.confirm", "&8Confirm Deletion?");
        AdminHomeConfirmHolder holder = new AdminHomeConfirmHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parseLegacy(titleFormat));
        holder.setInventory(inv);

        // Fill with border glass
        Material fillerMat = getMaterial("filler-glass", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 11: Confirm
        Material confirmMat = getMaterial("confirm-delete", Material.LIME_CONCRETE);
        ItemStack confirm = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(TextUtil.parseLegacy("&a&lCONFIRM DELETION"));
            confirmMeta.lore(getLore("confirm-delete", Map.of("{home}", session.isTeamHome() ? (session.getTeamName() + " (Team Home)") : session.getCurrentHome())));
            confirm.setItemMeta(confirmMeta);
        }
        inv.setItem(11, confirm);

        // Slot 15: Cancel
        Material cancelMat = getMaterial("cancel-delete", Material.RED_CONCRETE);
        ItemStack cancel = new ItemStack(cancelMat);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(TextUtil.parseLegacy("&c&lCANCEL"));
            cancelMeta.lore(getLore("cancel-delete", null));
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(15, cancel);

        admin.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Object holder = topInventory.getHolder();

        if (holder instanceof AdminHomeListHolder) {
            event.setCancelled(true);
            AdminSession session = sessions.get(admin.getUniqueId());
            if (session == null) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= topInventory.getSize()) return;

            ItemStack clickedItem = topInventory.getItem(slot);
            if (clickedItem == null) return;

            Material teamHomeMat = getMaterial("team-home", Material.BEACON);
            Material compassMat = getMaterial("home-compass", Material.COMPASS);

            if (clickedItem.getType() == teamHomeMat) {
                // Clicked on Team Home
                openActionGui(admin, session, session.getTeamName() != null ? session.getTeamName() : "Team", true);
                return;
            }

            if (clickedItem.getType() == compassMat) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta == null || !meta.hasDisplayName()) return;

                String homeName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                openActionGui(admin, session, homeName, false);
            }

        } else if (holder instanceof AdminHomeActionHolder) {
            event.setCancelled(true);
            AdminSession session = sessions.get(admin.getUniqueId());
            if (session == null) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= topInventory.getSize()) return;

            if (session.isTeamHome()) {
                handleTeamHomeAction(admin, session, slot);
            } else {
                handlePersonalHomeAction(admin, session, slot);
            }

        } else if (holder instanceof AdminHomeConfirmHolder) {
            event.setCancelled(true);
            AdminSession session = sessions.get(admin.getUniqueId());
            if (session == null) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= topInventory.getSize()) return;

            if (slot == 11) { // Confirm Deletion
                if (session.isTeamHome()) {
                    BetterTeamsHook btHook = plugin.getBetterTeamsHook();
                    boolean deleted = btHook.delTeamHome(session.getTeamObject());
                    if (deleted) {
                        TextUtil.sendMessage(admin, getPrefix() + "&aSuccessfully deleted team &e" + session.getTeamName() + "'s &fhome.");
                    } else {
                        TextUtil.sendMessage(admin, getPrefix() + "&cFailed to delete team home. BetterTeams may require manual command.");
                    }
                } else {
                    Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
                    if (ess != null && session.getTargetUuid() != null) {
                        User essUser = ess.getUser(session.getTargetUuid());
                        if (essUser != null) {
                            try {
                                essUser.delHome(session.getCurrentHome());
                                TextUtil.sendMessage(admin, getPrefix() + "&aSuccessfully deleted &e" + session.getTargetName() + "'s &fhome: &c" + session.getCurrentHome());
                            } catch (Exception e) {
                                TextUtil.sendMessage(admin, getPrefix() + "&cFailed to delete home: " + e.getMessage());
                            }
                        }
                    }
                }
                admin.closeInventory();
            } else if (slot == 15) { // Cancel
                openActionGui(admin, session, session.getCurrentHome(), session.isTeamHome());
            }
        }
    }

    private void handleTeamHomeAction(Player admin, AdminSession session, int slot) {
        BetterTeamsHook btHook = plugin.getBetterTeamsHook();
        Object team = session.getTeamObject() != null ? session.getTeamObject() : btHook.getTeam(session.getTeamName());
        if (team == null) {
            TextUtil.sendMessage(admin, getPrefix() + "&cCould not retrieve team data.");
            return;
        }

        switch (slot) {
            case 10 -> { // Teleport Admin to Team Home
                Location loc = btHook.getTeamHome(team);
                if (loc != null) {
                    admin.teleport(loc);
                    TextUtil.sendMessage(admin, getPrefix() + "&aTeleported to team &e" + session.getTeamName() + "'s &fhome.");
                } else {
                    TextUtil.sendMessage(admin, getPrefix() + "&cCould not teleport: Team home location is invalid or not set.");
                }
                admin.closeInventory();
            }
            case 12 -> { // Send Team Members to Team Home
                Location loc = btHook.getTeamHome(team);
                if (loc == null) {
                    TextUtil.sendMessage(admin, getPrefix() + "&cCould not teleport: Team home location is not set.");
                    return;
                }
                List<Player> onlineMembers = btHook.getOnlineMembers(team);
                if (onlineMembers.isEmpty()) {
                    TextUtil.sendMessage(admin, getPrefix() + "&cNo members of team &e" + session.getTeamName() + " &care currently online.");
                    return;
                }
                for (Player member : onlineMembers) {
                    member.teleport(loc);
                    TextUtil.sendMessage(member, "&bHavenCraft &8» &7An administrator teleported you to your team home.");
                }
                TextUtil.sendMessage(admin, getPrefix() + "&aTeleported &e" + onlineMembers.size() + " &fonline member(s) to team &e" + session.getTeamName() + "'s &fhome.");
                admin.closeInventory();
            }
            case 14 -> { // Update Team Home Location
                Location newLoc = admin.getLocation();
                boolean updated = btHook.setTeamHome(team, newLoc);
                if (updated) {
                    TextUtil.sendMessage(admin, getPrefix() + "&aUpdated team &e" + session.getTeamName() + "'s &fhome to your current location.");
                } else {
                    TextUtil.sendMessage(admin, getPrefix() + "&cFailed to update team home via BetterTeams API.");
                }
                admin.closeInventory();
            }
            case 16 -> { // Delete Team Home
                openConfirmationGui(admin, session);
            }
            case 18 -> { // Go Back
                if (session.isDirectTeamMode() || session.getTargetUuid() == null) {
                    admin.closeInventory();
                } else {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(session.getTargetUuid());
                    openHomeListGui(admin, target);
                }
            }
        }
    }

    private void handlePersonalHomeAction(Player admin, AdminSession session, int slot) {
        Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || session.getTargetUuid() == null) return;

        User essUser = ess.getUser(session.getTargetUuid());
        if (essUser == null) return;

        switch (slot) {
            case 10 -> { // Teleport Admin
                Location loc = null;
                try {
                    loc = essUser.getHome(session.getCurrentHome());
                } catch (Exception ignored) {}
                if (loc != null) {
                    admin.teleport(loc);
                    TextUtil.sendMessage(admin, getPrefix() + "&aTeleported to &e" + session.getTargetName() + "'s &fhome: &e" + session.getCurrentHome());
                } else {
                    TextUtil.sendMessage(admin, getPrefix() + "&cCould not teleport: Home location is invalid.");
                }
                admin.closeInventory();
            }
            case 12 -> { // Send Player to Home
                Player targetPlayer = Bukkit.getPlayer(session.getTargetUuid());
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    TextUtil.sendMessage(admin, getPrefix() + "&cCannot teleport: Player is currently offline!");
                    return;
                }
                Location loc = null;
                try {
                    loc = essUser.getHome(session.getCurrentHome());
                } catch (Exception ignored) {}
                if (loc != null) {
                    targetPlayer.teleport(loc);
                    TextUtil.sendMessage(admin, getPrefix() + "&aTeleported &e" + targetPlayer.getName() + " &fto their home: &e" + session.getCurrentHome());
                    TextUtil.sendMessage(targetPlayer, "&bHavenCraft &8» &7An administrator teleported you to your home: &e" + session.getCurrentHome());
                } else {
                    TextUtil.sendMessage(admin, getPrefix() + "&cCould not teleport: Home location is invalid.");
                }
                admin.closeInventory();
            }
            case 14 -> { // Update Location
                Location newLoc = admin.getLocation();
                essUser.setHome(session.getCurrentHome(), newLoc);
                TextUtil.sendMessage(admin, getPrefix() + "&aUpdated &e" + session.getTargetName() + "'s &fhome &e" + session.getCurrentHome() + " &fto your current location.");
                admin.closeInventory();
            }
            case 16 -> { // Delete Home (Confirmation GUI)
                openConfirmationGui(admin, session);
            }
            case 18 -> { // Go Back
                OfflinePlayer target = Bukkit.getOfflinePlayer(session.getTargetUuid());
                openHomeListGui(admin, target);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof AdminHomeListHolder ||
                holder instanceof AdminHomeActionHolder ||
                holder instanceof AdminHomeConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player admin)) return;

        // Use a 1-tick delay to check if the player opened another admin home inventory
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!admin.isOnline()) {
                sessions.remove(admin.getUniqueId());
                return;
            }

            try {
                org.bukkit.inventory.InventoryView openView = admin.getOpenInventory();
                if (openView == null) {
                    sessions.remove(admin.getUniqueId());
                    return;
                }

                Inventory top = openView.getTopInventory();
                if (top == null) {
                    sessions.remove(admin.getUniqueId());
                    return;
                }

                Object holder = top.getHolder();
                if (!(holder instanceof AdminHomeListHolder) &&
                        !(holder instanceof AdminHomeActionHolder) &&
                        !(holder instanceof AdminHomeConfirmHolder)) {
                    sessions.remove(admin.getUniqueId());
                }
            } catch (Throwable ignored) {
                sessions.remove(admin.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void clearSessions() {
        sessions.clear();
    }
}
