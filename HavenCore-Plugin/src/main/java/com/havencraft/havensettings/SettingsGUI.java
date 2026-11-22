package com.havencraft.havensettings;

import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.havencraft.havencore.util.TextUtil;

public class SettingsGUI implements Listener {

    private final JavaPlugin plugin;
    private final SettingsManager settingsManager;
    private final FileConfiguration guiConfig;
    private final FileConfiguration messagesConfig;
    private final FileConfiguration customCmdConfig;
    private final String title;
    private final int size;
    private final String statusOn;
    private final String statusOff;

    public SettingsGUI(JavaPlugin plugin, SettingsManager sm,
                       FileConfiguration guiCfg, FileConfiguration msgCfg, FileConfiguration cmdCfg) {
        this.plugin = plugin;
        this.settingsManager = sm;
        this.guiConfig = guiCfg;
        this.messagesConfig = msgCfg;
        this.customCmdConfig = cmdCfg;
        this.title = TextUtil.colorize(guiCfg.getString("title", "&8&lPlayer Settings"));
        this.size = Math.max(1, Math.min(6, guiCfg.getInt("rows", 3))) * 9;
        this.statusOn = TextUtil.colorize(guiCfg.getString("status-on", "&a&lON"));
        this.statusOff = TextUtil.colorize(guiCfg.getString("status-off", "&c&lOFF"));
    }

    /**
     * Opens the settings menu for a player viewing their own settings.
     *
     * @param player the player opening and viewing the menu
     */
    public void openSettings(Player player) {
        openSettings(player, player);
    }

    /**
     * Opens the settings menu for a viewer player targeting another player's settings.
     *
     * @param viewer the player viewing the menu
     * @param target the target player whose settings are being inspected/modified
     */
    public void openSettings(Player viewer, org.bukkit.OfflinePlayer target) {
        PlayerSettings ps = settingsManager.getOrLoad(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String customTitle = viewer.getUniqueId().equals(target.getUniqueId()) ? title : title + ": " + targetName;
        SettingsGUIHolder holder = new SettingsGUIHolder(target.getUniqueId());
        Inventory gui = Bukkit.createInventory(holder, size, customTitle);
        holder.setInventory(gui);

        if (guiConfig.getBoolean("filler.enabled", true)) {
            Material fm = mat(guiConfig.getString("filler.material", "BLACK_STAINED_GLASS_PANE"));
            ItemStack filler = item(fm, TextUtil.colorize(guiConfig.getString("filler.name", " ")), null);
            for (int i = 0; i < size; i++) gui.setItem(i, filler);
        }

        renderButtons(gui, holder, ps, target);
        viewer.openInventory(gui);
        playSound(viewer, "open");
    }

    private void renderButtons(Inventory gui, SettingsGUIHolder holder, PlayerSettings ps, org.bukkit.OfflinePlayer target) {
        holder.getSlotMap().clear();
        addBtn(gui, holder.getSlotMap(), "private-message", ps.isMsgEnabled());
        addBtn(gui, holder.getSlotMap(), "tpa", ps.isTpaEnabled());
        addBtn(gui, holder.getSlotMap(), "tpahere", ps.isTpaHereEnabled());
        addBtn(gui, holder.getSlotMap(), "scoreboard", ps.isScoreboardEnabled());
        addBtn(gui, holder.getSlotMap(), "tpa-confirmation", ps.isTpaConfirmationEnabled());
        addBtn(gui, holder.getSlotMap(), "night-vision", ps.isNightVisionEnabled());
        addBtn(gui, holder.getSlotMap(), "public-chat", ps.isPublicChatEnabled());
        addCustomBtns(gui, holder.getSlotMap(), ps, target);

        int cs = guiConfig.getInt("close.slot", 22);
        gui.setItem(cs, item(mat(guiConfig.getString("close.material", "BARRIER")),
                TextUtil.colorize(guiConfig.getString("close.name", "&c&lClose")),
                TextUtil.colorizeList(guiConfig.getStringList("close.lore"))));
        holder.getSlotMap().put(cs, "close");
    }

    private void addBtn(Inventory gui, Map<Integer, String> slotMap, String id, boolean on) {
        ConfigurationSection s = guiConfig.getConfigurationSection("buttons." + id);
        if (s == null) return;
        int slot = s.getInt("slot", 0);
        Material m = on ? mat(s.getString("material-on", "OAK_SIGN")) : mat(s.getString("material-off", "OAK_SIGN"));
        String st = on ? statusOn : statusOff;
        List<String> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) lore.add(TextUtil.colorize(l.replace("{status}", st)));
        gui.setItem(slot, item(m, TextUtil.colorize(s.getString("name", id)), lore));
        slotMap.put(slot, id);
    }

    private void addCustomBtns(Inventory gui, Map<Integer, String> slotMap, PlayerSettings ps, org.bukkit.OfflinePlayer player) {
        if (!customCmdConfig.getBoolean("enabled", false)) return;
        ConfigurationSection sec = customCmdConfig.getConfigurationSection("custom-commands");
        if (sec == null) return;
        int slot = guiConfig.getInt("custom-commands-start-slot", 16);
        for (String id : sec.getKeys(false)) {
            ConfigurationSection cs = sec.getConfigurationSection(id);
            if (cs == null) continue;
            String perm = cs.getString("permission", null);
            if (perm != null && !perm.isEmpty()) {
                if (player.isOnline() && player.getPlayer() != null && !player.getPlayer().hasPermission(perm)) {
                    continue; // Skip if online and missing permission
                }
            }
            boolean on = ps.isCustomCommandEnabled(id);
            Material m = on ? mat(cs.getString("material-on", "OAK_SIGN")) : mat(cs.getString("material-off", "OAK_SIGN"));
            String st = on ? statusOn : statusOff;
            List<String> lore = new ArrayList<>();
            for (String l : cs.getStringList("lore")) lore.add(TextUtil.colorize(l.replace("{status}", st)));
            if (slot < size) {
                gui.setItem(slot, item(m, TextUtil.colorize(cs.getString("name", id)), lore));
                slotMap.put(slot, "custom:" + id);
                slot++;
            }
        }
    }

    /**
     * Handles clicks within settings inventories, toggling options and canceling default interactions.
     *
     * @param event the click event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SettingsGUIHolder holder)) return;
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        String id = holder.getSlotMap().get(raw);
        if (id == null) return;

        playSound(player, "click");

        UUID targetUUID = holder.getTargetUuid();
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);

        String pfx = TextUtil.colorize(messagesConfig.getString("prefix", ""));

        switch (id) {
            case "close" -> player.closeInventory();
            case "private-message" -> {
                boolean now = settingsManager.toggleMsg(targetUUID);
                msg(player, pfx, "Private Messages", now);
                refresh(target);
            }
            case "tpa" -> {
                boolean now = settingsManager.toggleTpa(targetUUID);
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "tpa-enabled" : "tpa-disabled",
                        now ? "&aTPA requests enabled." : "&cTPA requests disabled.")));
                refresh(target);
            }
            case "tpahere" -> {
                boolean now = settingsManager.toggleTpaHere(targetUUID);
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "tpahere-enabled" : "tpahere-disabled",
                        now ? "&aTPAHere requests enabled." : "&cTPAHere requests disabled.")));
                refresh(target);
            }
            case "scoreboard" -> {
                boolean now = settingsManager.toggleScoreboard(targetUUID);
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "scoreboard-shown" : "scoreboard-hidden", "")));
                refresh(target);
            }
            case "tpa-confirmation" -> {
                boolean now = settingsManager.toggleTpaConfirmation(targetUUID);
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "tpa-confirmation-enabled" : "tpa-confirmation-disabled",
                        now ? "&aTPA confirmation popup enabled." : "&cTPA confirmation popup disabled.")));
                refresh(target);
            }
            case "night-vision" -> {
                boolean now = settingsManager.toggleNightVision(targetUUID);
                if (target.isOnline() && target.getPlayer() != null) {
                    if (now) applyNV(target.getPlayer()); else removeNV(target.getPlayer());
                }
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "night-vision-applied" : "night-vision-removed", "")));
                refresh(target);
            }
            case "public-chat" -> {
                boolean now = settingsManager.togglePublicChat(targetUUID);
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(now ? "public-chat-enabled" : "public-chat-disabled",
                        now ? "&aPublic chat enabled." : "&cPublic chat disabled.")));
                refresh(target);
            }
            default -> {
                if (id.startsWith("custom:")) {
                    String cid = id.substring(7);
                    msg(player, pfx, cid, settingsManager.toggleCustomCommand(targetUUID, cid));
                    refresh(target);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof SettingsGUIHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * Handles settings inventory closing sound.
     *
     * @param event the inventory close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getView().getTopInventory();
        if (inv != null && inv.getHolder() instanceof SettingsGUIHolder) {
            if (event.getPlayer() instanceof Player player) {
                playSound(player, "close");
            }
        }
    }

    /**
     * Applies infinite Night Vision potion effect to a player.
     *
     * @param p the player to apply night vision to
     */
    public void applyNV(Player p) { p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true)); }

    /**
     * Removes Night Vision potion effect from a player.
     *
     * @param p the player to remove night vision from
     */
    public void removeNV(Player p) { p.removePotionEffect(PotionEffectType.NIGHT_VISION); }

    /**
     * Retrieves the owning plugin instance.
     *
     * @return the JavaPlugin instance
     */
    public JavaPlugin getPlugin() { return plugin; }

    @SuppressWarnings("deprecation")
    private void refresh(org.bukkit.OfflinePlayer target) {
        if (target == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.InventoryView view = online.getOpenInventory();
            if (view != null && view.getTopInventory() != null && view.getTopInventory().getHolder() instanceof SettingsGUIHolder holder) {
                if (holder.getTargetUuid().equals(target.getUniqueId())) {
                    PlayerSettings ps = settingsManager.getOrLoad(target.getUniqueId());
                    renderButtons(view.getTopInventory(), holder, ps, target);
                    online.updateInventory();
                }
            }
        }
    }

    private void msg(Player p, String pfx, String name, boolean on) {
        String k = on ? "toggled-on" : "toggled-off";
        p.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString(k, "")).replace("{setting}", name));
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); if (lore != null && !lore.isEmpty()) meta.setLore(lore); i.setItemMeta(meta); }
        return i;
    }
    private Material mat(String n) { if (n == null) return Material.OAK_SIGN; Material m = Material.matchMaterial(n); return m != null ? m : Material.OAK_SIGN; }

    private void playSound(Player player, String soundKey) {
        String soundName = guiConfig.getString("sounds." + soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                try {
                    player.playSound(player.getLocation(), soundName.toLowerCase(), 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Closes all active settings inventories, cleans up tracking maps, and cancels the periodic task.
     */
    public void closeAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.InventoryView view = online.getOpenInventory();
            if (view != null && view.getTopInventory() != null && view.getTopInventory().getHolder() instanceof SettingsGUIHolder) {
                online.closeInventory();
            }
        }
    }
}
