package com.havencraft.havensettings;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.ITarget;
import com.earth2me.essentials.IUser;
import com.earth2me.essentials.User;
import com.havencraft.havencore.util.TextUtil;
import net.ess3.api.events.teleport.PreTeleportEvent;
import net.ess3.api.events.teleport.TeleportWarmupEvent;
import net.essentialsx.api.v2.events.TeleportRequestResponseEvent;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class TpaMessageListener implements Listener {

    private static final long REPROMPT_COOLDOWN_MS = 3000L;
    static final double DEFAULT_PORTAL_CHECK_RADIUS = 1.0;
    private static final double MAX_PORTAL_CHECK_RADIUS = 10.0;
    private static final double PORTAL_CHECK_BELOW_PADDING = 0.10;
    private static final double PORTAL_CHECK_ABOVE_HEIGHT = 1.85;

    private static final List<String> DEFAULT_TPA_POPUP_BODY = List.of(
            "&cWARNING! &7- &fYou are about to teleport to &a'{player}'&f.",
            "&fThis player is in the &c{world_display}&f. &eKeep Inventory &fis &e{keep_inventory} &fthere.",
            "&fYou can be &ctrapped &for &ckilled &fand lose all your items.",
            "",
            "&fAre you sure you want to teleport?"
    );

    private static final List<String> DEFAULT_TPAHERE_POPUP_BODY = List.of(
            "&cWARNING! &7- &a'{player}' &fwants to teleport you over.",
            "&fThis player is in the &c{world_display}&f. &eKeep Inventory &fis &e{keep_inventory} &fthere.",
            "&fYou can be &ctrapped &for &ckilled &fand lose all your items.",
            "",
            "&fAre you sure you want to teleport?"
    );

    private static final class PendingAccepterConfirm {
        private final UUID requesterUuid;
        private final long requestTime;
        private volatile boolean chatFallbackPending;
        private volatile boolean approved;

        private PendingAccepterConfirm(UUID requesterUuid, long requestTime) {
            this.requesterUuid = requesterUuid;
            this.requestTime = requestTime;
        }
    }

    private static final class PendingRequesterConfirm {
        private final UUID accepterUuid;
        private final String accepterName;
        private final long requestTime;
        private final String targetWorldName;
        private volatile long lastPromptSentAt;

        private PendingRequesterConfirm(UUID accepterUuid, String accepterName, long requestTime, String targetWorldName, long lastPromptSentAt) {
            this.accepterUuid = accepterUuid;
            this.accepterName = accepterName;
            this.requestTime = requestTime;
            this.targetWorldName = targetWorldName;
            this.lastPromptSentAt = lastPromptSentAt;
        }
    }

    private record ApprovedRequesterAccept(long requestTime, String targetWorldName) {}

    private record PendingWarmupArm(
            UUID teleporteeUuid,
            UUID counterpartUuid,
            String counterpartName,
            String initialWorldName,
            boolean isHere,
            boolean checkWorldWarning,
            boolean checkPortalProtection,
            long createdAt
    ) {}

    private record ActiveTpaTeleportGuard(
            UUID teleporteeUuid,
            UUID counterpartUuid,
            String counterpartName,
            String initialWorldName,
            boolean isHere,
            boolean checkWorldWarning,
            boolean checkPortalProtection,
            ITarget expectedTarget
    ) {}

    private final JavaPlugin plugin;
    private final SettingsManager settingsManager;
    private final FileConfiguration messagesConfig;
    private final Essentials essentials;
    private final TpaConfirmationDialogService dialogService;
    private final Map<UUID, PendingAccepterConfirm> pendingAccepterConfirms = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, PendingRequesterConfirm>> pendingRequesterConfirms = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, ApprovedRequesterAccept>> approvedRequesterAccepts = new ConcurrentHashMap<>();
    private final Map<UUID, PendingWarmupArm> pendingWarmupArms = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveTpaTeleportGuard> activeTpaGuards = new ConcurrentHashMap<>();

    public TpaMessageListener(JavaPlugin plugin, SettingsManager settingsManager, FileConfiguration messagesConfig) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        this.messagesConfig = messagesConfig;
        this.essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        this.dialogService = new TpaConfirmationDialogService(plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingAccepterConfirms.remove(uuid);
        pendingRequesterConfirms.remove(uuid);
        approvedRequesterAccepts.remove(uuid);
        pendingWarmupArms.remove(uuid);
        activeTpaGuards.remove(uuid);
        for (Map<UUID, PendingRequesterConfirm> map : pendingRequesterConfirms.values()) {
            map.remove(uuid);
        }
        for (Map<UUID, ApprovedRequesterAccept> map : approvedRequesterAccepts.values()) {
            map.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;

        Player sender = event.getPlayer();
        String message = event.getMessage().trim();

        // Split arguments by whitespace
        String[] args = message.split("\\s+");
        if (args.length == 0) return;

        String baseCommand = args[0].toLowerCase(Locale.ENGLISH);

        // Strip namespace prefix (e.g. /essentials:tpa -> /tpa)
        if (baseCommand.contains(":")) {
            int colonIndex = baseCommand.indexOf(":");
            baseCommand = "/" + baseCommand.substring(colonIndex + 1);
        }

        if (baseCommand.equals("/tpaconfirm")) {
            event.setCancelled(true);
            handleRequesterConfirm(sender, args);
            return;
        }

        if (baseCommand.equals("/tpacancel")) {
            event.setCancelled(true);
            handleRequesterCancel(sender, args);
            return;
        }

        if (isTpDenyCommand(baseCommand)) {
            clearAccepterPendingState(sender.getUniqueId());
            return;
        }

        if (isTpAcceptCommand(baseCommand)) {
            handleTpAccept(event, sender, args);
            return;
        }

        if (!settingsManager.isCustomTpaMessagesEnabled()) {
            return;
        }

        boolean isTpa = baseCommand.equals("/tpa") || baseCommand.equals("/tpask") || baseCommand.equals("/call");
        boolean isTpaHere = baseCommand.equals("/tpahere") || baseCommand.equals("/tpah");
        boolean isTpaAll = baseCommand.equals("/tpaall");

        if (!isTpa && !isTpaHere && !isTpaAll) {
            return;
        }

        if (essentials == null) return;

        User senderUser = essentials.getUser(sender);
        if (senderUser == null) return;

        // 1. Permission checks
        if (isTpa && !sender.hasPermission("essentials.tpa")) return;
        if (isTpaHere && !sender.hasPermission("essentials.tpahere")) return;
        if (isTpaAll && !sender.hasPermission("essentials.tpaall")) return;

        // 2. Jail check for sender
        if (senderUser.isJailed()) {
            return;
        }

        // 3. Check sender's TPA/TPAHere toggle in HavenSettings
        boolean senderAllowed = (isTpaHere || isTpaAll) ? settingsManager.isTpaHereEnabled(sender.getUniqueId()) : settingsManager.isTpaEnabled(sender.getUniqueId());
        if (!senderAllowed) {
            String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
            String msgKey = (isTpaHere || isTpaAll) ? "tpahere-blocked" : "tpa-blocked";
            String blockedMessage = messagesConfig.getString(msgKey, (isTpaHere || isTpaAll) ? "&cYou have TPAHere disabled." : "&cYou have TPA disabled.");
            sender.sendMessage(prefix + TextUtil.colorize(blockedMessage));
            event.setCancelled(true);
            return;
        }

        // Handle /tpaall
        if (isTpaAll) {
            Player targetPlayer = sender;
            if (args.length > 1) {
                targetPlayer = Bukkit.getPlayer(args[1]);
            }
            if (targetPlayer == null || !targetPlayer.isOnline() || !sender.canSee(targetPlayer)) {
                return;
            }
            User targetUser = essentials.getUser(targetPlayer);
            if (targetUser == null) return;

            event.setCancelled(true);

            String senderPrefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
            sender.sendMessage(senderPrefix + TextUtil.colorize(messagesConfig.getString("tpa-request-sent-all-success", "&aRequest sent to all players.")));

            for (Player recipientPlayer : Bukkit.getOnlinePlayers()) {
                if (recipientPlayer.getUniqueId().equals(targetPlayer.getUniqueId())) {
                    continue;
                }
                if (!recipientPlayer.isOnline() || !sender.canSee(recipientPlayer)) {
                    continue;
                }
                User recipientUser = essentials.getUser(recipientPlayer);
                if (recipientUser == null) continue;

                // Check TPA toggle and permissions
                if (!settingsManager.isTpaHereEnabled(recipientPlayer.getUniqueId())) {
                    continue;
                }
                if (!recipientUser.isTeleportEnabled()) {
                    continue;
                }
                if (targetPlayer.getWorld() != recipientPlayer.getWorld() && essentials.getSettings().isWorldTeleportPermissions() && !targetUser.isAuthorized("essentials.worlds." + targetPlayer.getWorld().getName())) {
                    continue;
                }

                try {
                    recipientUser.requestTeleport(targetUser, true);
                    sendCustomTpaMessage(targetPlayer, recipientPlayer, true);
                } catch (Exception ignored) {}
            }
            return;
        }

        // Handle /tpa and /tpahere
        if (args.length < 2) {
            return;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline() || !sender.canSee(targetPlayer)) {
            return;
        }

        User targetUser = essentials.getUser(targetPlayer);
        if (targetUser == null) return;

        // Check target's TPA/TPAHere toggle in HavenSettings
        boolean targetAllowed = isTpaHere ? settingsManager.isTpaHereEnabled(targetPlayer.getUniqueId()) : settingsManager.isTpaEnabled(targetPlayer.getUniqueId());
        if (!targetAllowed) {
            if (!sender.hasPermission("settings.bypass") && !sender.hasPermission("havensettings.bypass")) {
                String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
                String msgKey = isTpaHere ? "target-tpahere-blocked" : "target-blocked";
                String blockedMessage = messagesConfig.getString(msgKey, isTpaHere ? "&cThat player has TPAHere disabled." : "&cThat player has this disabled.");
                sender.sendMessage(prefix + TextUtil.colorize(blockedMessage));
                event.setCancelled(true);
                return;
            }
        }

        // Check target teleport enabled in EssentialsX
        if (!targetUser.isTeleportEnabled()) {
            String msg = targetUser.getDisplayName() + " has teleportation disabled.";
            sender.sendMessage(ChatColor.RED + msg);
            event.setCancelled(true);
            return;
        }

        // Check target tpaccept permission in EssentialsX
        if (!targetUser.isAuthorized("essentials.tpaccept")) {
            String msg = targetUser.getDisplayName() + " does not have permission to accept teleport requests.";
            sender.sendMessage(ChatColor.RED + msg);
            event.setCancelled(true);
            return;
        }

        // Check world teleport permissions
        if (sender.getWorld() != targetPlayer.getWorld() && essentials.getSettings().isWorldTeleportPermissions()) {
            String worldName = isTpaHere ? sender.getWorld().getName() : targetPlayer.getWorld().getName();
            if (!senderUser.isAuthorized("essentials.worlds." + worldName)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to teleport to world: " + worldName);
                event.setCancelled(true);
                return;
            }
        }

        // Check duplicate outstanding request
        if (targetUser.hasOutstandingTpaRequest(sender.getName(), isTpaHere)) {
            String msg = "You have already sent a teleport request to " + targetUser.getDisplayName() + ".";
            sender.sendMessage(ChatColor.RED + msg);
            event.setCancelled(true);
            return;
        }

        // Ignore check
        if (targetUser.isIgnoredPlayer(senderUser)) {
            String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
            String successMsg = messagesConfig.getString("tpa-request-sent-success", "&aRequest sent to {player}.");
            sender.sendMessage(prefix + TextUtil.colorize(successMsg.replace("{player}", targetPlayer.getDisplayName())));
            event.setCancelled(true);
            return;
        }

        // Process custom TPA
        event.setCancelled(true);
        targetUser.requestTeleport(senderUser, isTpaHere);
        sendCustomTpaMessage(sender, targetPlayer, isTpaHere);

        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        String successMsg = messagesConfig.getString("tpa-request-sent-success", "&aRequest sent to {player}.");
        sender.sendMessage(prefix + TextUtil.colorize(successMsg.replace("{player}", targetPlayer.getDisplayName())));
    }

    private boolean isTpAcceptCommand(String baseCommand) {
        return baseCommand.equals("/tpaccept")
                || baseCommand.equals("/etpaccept")
                || baseCommand.equals("/tpyes")
                || baseCommand.equals("/etpyes");
    }

    private boolean isTpDenyCommand(String baseCommand) {
        return baseCommand.equals("/tpdeny")
                || baseCommand.equals("/etpdeny")
                || baseCommand.equals("/tpno")
                || baseCommand.equals("/etpno");
    }

    private void clearAccepterPendingState(UUID accepterUuid) {
        pendingAccepterConfirms.remove(accepterUuid);
        approvedRequesterAccepts.remove(accepterUuid);
        for (Map<UUID, PendingRequesterConfirm> map : pendingRequesterConfirms.values()) {
            map.remove(accepterUuid);
        }
    }

    private void handleTpAccept(PlayerCommandPreprocessEvent event, Player accepter, String[] args) {
        boolean worldWarningEnabled = settingsManager.isTpaWorldWarningEnabled();
        boolean portalProtectionEnabled = settingsManager.isTpaPortalProtectionEnabled();
        if ((!worldWarningEnabled && !portalProtectionEnabled) || essentials == null) {
            return;
        }
        if (!accepter.hasPermission("essentials.tpaccept")) {
            return;
        }

        User accepterUser = essentials.getUser(accepter);
        if (accepterUser == null) {
            return;
        }

        boolean isWildcard = args.length >= 2 && (args[1].equals("*") || args[1].equalsIgnoreCase("all"));
        List<IUser.TpaRequest> matchedRequests = resolveMatchingRequests(accepterUser, args, isWildcard);
        if (matchedRequests.isEmpty()) {
            return;
        }

        if (portalProtectionEnabled && !isWildcard) {
            IUser.TpaRequest singleRequest = matchedRequests.get(0);
            Location destLoc = resolveDestinationLocation(accepter, singleRequest);
            Material portalMaterial = detectPortalMaterial(destLoc, settingsManager.getTpaPortalProtectionRadius());
            if (portalMaterial != null) {
                event.setCancelled(true);
                notifyPortalBlockedRequest(accepter, singleRequest, destLoc, portalMaterial);
                PendingAccepterConfirm pending = pendingAccepterConfirms.get(accepter.getUniqueId());
                if (pending != null
                        && Objects.equals(pending.requesterUuid, singleRequest.getRequesterUuid())
                        && pending.requestTime == singleRequest.getTime()) {
                    pendingAccepterConfirms.remove(accepter.getUniqueId(), pending);
                }
                if (singleRequest.isHere()) {
                    removeRequestIgnoreCase(accepterUser, singleRequest.getName());
                }
                return;
            }
        }

        if (!worldWarningEnabled) {
            return;
        }

        List<IUser.TpaRequest> requests = filterWarningRequests(accepter, matchedRequests, portalProtectionEnabled);
        if (requests.isEmpty()) {
            return;
        }

        String timeoutStr = String.valueOf(essentials.getSettings().getTpaAcceptCancellation());
        long timeoutSeconds = essentials.getSettings().getTpaAcceptCancellation();
        double teleportDelay = essentials.getSettings().getTeleportDelay();
        String delayStr = formatDelay(teleportDelay);
        String warningMode = settingsManager.getTpaWorldWarningMode();
        boolean isNotifyMode = "NOTIFY".equalsIgnoreCase(warningMode);
        boolean isConfirmChatMode = "CONFIRM".equalsIgnoreCase(warningMode);
        boolean promptedAccepter = false;

        for (IUser.TpaRequest request : requests) {
            Location checkLocation = resolveWarningLocation(accepter, request);
            if (checkLocation == null || checkLocation.getWorld() == null) {
                continue;
            }

            Player requester = resolveRequester(request);
            String requesterName = requester != null ? requester.getName() : request.getName();
            String worldName = checkLocation.getWorld().getName();
            String worldDisplay = settingsManager.getTpaWorldDisplayName(worldName);
            String blockName = getFormattedBlockUnder(checkLocation);
            String keepInventory = resolveKeepInventoryLabel(checkLocation.getWorld());

            if (!request.isHere()) {
                // /tpa: requester (Player1) is teleporting to accepter's (Player2's) world
                if (requester == null || !requester.isOnline()) {
                    continue;
                }

                if (isNotifyMode || !settingsManager.isTpaConfirmationEnabled(requester.getUniqueId())) {
                    sendNotifyWarning(
                            requester, accepter.getName(), worldName, worldDisplay,
                            blockName, timeoutStr, delayStr, keepInventory, teleportDelay > 0
                    );
                    continue;
                }

                Map<UUID, ApprovedRequesterAccept> approvedForAccepter = approvedRequesterAccepts.get(accepter.getUniqueId());
                ApprovedRequesterAccept approved = approvedForAccepter != null ? approvedForAccepter.get(requester.getUniqueId()) : null;
                if (approved != null
                        && approved.requestTime == request.getTime()
                        && worldName.equalsIgnoreCase(approved.targetWorldName)) {
                    approvedForAccepter.remove(requester.getUniqueId());
                    continue;
                }

                event.setCancelled(true);
                Map<UUID, PendingRequesterConfirm> reqMap = pendingRequesterConfirms.computeIfAbsent(
                        requester.getUniqueId(), k -> new ConcurrentHashMap<>()
                );
                PendingRequesterConfirm existing = reqMap.get(accepter.getUniqueId());
                long now = System.currentTimeMillis();
                if (existing == null
                        || existing.requestTime != request.getTime()
                        || !worldName.equalsIgnoreCase(existing.targetWorldName)
                        || (now - existing.lastPromptSentAt) >= REPROMPT_COOLDOWN_MS) {
                    long requestTime = request.getTime();
                    UUID accepterUuid = accepter.getUniqueId();
                    String accepterName = accepter.getName();
                    reqMap.put(accepterUuid, new PendingRequesterConfirm(accepterUuid, accepterName, requestTime, worldName, now));

                    TpaConfirmationDialogService.ConfirmationPrompt prompt = buildConfirmationPrompt(
                            "tpa-popup",
                            "&f&lTELEPORT CONFIRMATION",
                            DEFAULT_TPA_POPUP_BODY,
                            accepterName,
                            worldName,
                            worldDisplay,
                            blockName,
                            timeoutStr,
                            delayStr,
                            keepInventory,
                            "/tpaconfirm " + accepterName,
                            "/tpacancel " + accepterName,
                            timeoutSeconds,
                            () -> confirmRequesterByAccepter(requester, accepterUuid, requestTime),
                            () -> cancelRequesterByAccepter(requester, accepterUuid, requestTime),
                            () -> {}
                    );
                    dispatchConfirmationPrompt(requester, prompt, isConfirmChatMode);
                }

                String waitingMsg = messagesConfig.getString(
                        "tpa-world-waiting-message",
                        "&7Waiting for &e{player} &7to confirm teleporting to &c&l{world_display}&7..."
                );
                if (waitingMsg != null && !waitingMsg.isEmpty()) {
                    String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
                    accepter.sendMessage(prefix + TextUtil.colorize(
                            replaceTpaPlaceholders(
                                    waitingMsg, requesterName, worldName, worldDisplay,
                                    blockName, timeoutStr, delayStr, keepInventory
                            )
                    ));
                }
            } else {
                // /tpahere: accepter is teleporting to requester's world
                if (isNotifyMode) {
                    sendNotifyWarning(
                            accepter, requesterName, worldName, worldDisplay,
                            blockName, timeoutStr, delayStr, keepInventory, false
                    );
                    continue;
                }

                if (!settingsManager.isTpaConfirmationEnabled(accepter.getUniqueId())) {
                    continue;
                }

                PendingAccepterConfirm pending = pendingAccepterConfirms.get(accepter.getUniqueId());
                if (pending != null
                        && Objects.equals(pending.requesterUuid, request.getRequesterUuid())
                        && pending.requestTime == request.getTime()
                        && (pending.approved || pending.chatFallbackPending)) {
                    pendingAccepterConfirms.remove(accepter.getUniqueId());
                    continue;
                }

                event.setCancelled(true);
                if (promptedAccepter) {
                    continue;
                }
                promptedAccepter = true;

                PendingAccepterConfirm nextPending = new PendingAccepterConfirm(request.getRequesterUuid(), request.getTime());
                pendingAccepterConfirms.put(accepter.getUniqueId(), nextPending);

                TpaConfirmationDialogService.ConfirmationPrompt prompt = buildConfirmationPrompt(
                        "tpahere-popup",
                        "&f&lTELEPORT CONFIRMATION",
                        DEFAULT_TPAHERE_POPUP_BODY,
                        requesterName,
                        worldName,
                        worldDisplay,
                        blockName,
                        timeoutStr,
                        delayStr,
                        keepInventory,
                        "/tpaccept " + requesterName,
                        "/tpdeny " + requesterName,
                        timeoutSeconds,
                        () -> {
                            if (!accepter.isOnline()) {
                                return;
                            }
                            PendingAccepterConfirm current = pendingAccepterConfirms.get(accepter.getUniqueId());
                            if (current == null || current != nextPending) {
                                return;
                            }
                            current.approved = true;
                            try {
                                executeCommandWithFallback(accepter, "essentials:tpaccept", "tpaccept", requesterName);
                            } finally {
                                pendingAccepterConfirms.remove(accepter.getUniqueId(), current);
                            }
                        },
                        () -> {
                            if (!accepter.isOnline()) {
                                return;
                            }
                            PendingAccepterConfirm current = pendingAccepterConfirms.get(accepter.getUniqueId());
                            if (current != null && current == nextPending) {
                                pendingAccepterConfirms.remove(accepter.getUniqueId(), current);
                            }
                            executeCommandWithFallback(accepter, "essentials:tpdeny", "tpdeny", requesterName);
                        },
                        () -> {
                            PendingAccepterConfirm current = pendingAccepterConfirms.get(accepter.getUniqueId());
                            if (current != null && current == nextPending && !current.approved && !current.chatFallbackPending) {
                                pendingAccepterConfirms.remove(accepter.getUniqueId(), current);
                            }
                        }
                );

                TpaConfirmationDialogService.DisplayChannel channel =
                        dispatchConfirmationPrompt(accepter, prompt, isConfirmChatMode);
                if (channel == TpaConfirmationDialogService.DisplayChannel.CHAT_FALLBACK) {
                    nextPending.chatFallbackPending = true;
                }
            }
        }
    }

    private void handleRequesterConfirm(Player requester, String[] args) {
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        PendingRequesterConfirm pending = removePendingRequesterConfirm(requester.getUniqueId(), args);
        if (pending == null) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-no-pending", "&cYou do not have any pending teleport confirmation."
            )));
            return;
        }
        executeRequesterConfirm(requester, pending, prefix);
    }

    private void confirmRequesterByAccepter(Player requester, UUID accepterUuid, long expectedRequestTime) {
        if (!requester.isOnline()) {
            return;
        }
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        PendingRequesterConfirm pending = removePendingRequesterByAccepter(requester.getUniqueId(), accepterUuid, expectedRequestTime);
        if (pending == null) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-no-pending", "&cYou do not have any pending teleport confirmation."
            )));
            return;
        }
        executeRequesterConfirm(requester, pending, prefix);
    }

    private void executeRequesterConfirm(Player requester, PendingRequesterConfirm pending, String prefix) {
        Player accepter = Bukkit.getPlayer(pending.accepterUuid);
        if (accepter == null || !accepter.isOnline() || essentials == null) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-confirm-expired", "&cThat teleport request has expired or is no longer available."
            )));
            return;
        }

        User accepterUser = essentials.getUser(accepter);
        IUser.TpaRequest request = accepterUser != null ? findRequestByRequester(accepterUser, requester.getName()) : null;
        if (request == null || isRequestExpired(request) || request.getTime() != pending.requestTime) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-confirm-expired", "&cThat teleport request has expired or is no longer available."
            )));
            return;
        }

        Map<UUID, ApprovedRequesterAccept> approved = approvedRequesterAccepts.computeIfAbsent(accepter.getUniqueId(), k -> new ConcurrentHashMap<>());
        approved.put(requester.getUniqueId(), new ApprovedRequesterAccept(request.getTime(), pending.targetWorldName));
        try {
            executeCommandWithFallback(accepter, "essentials:tpaccept", "tpaccept", requester.getName());
        } finally {
            approved.remove(requester.getUniqueId());
        }
    }

    private void handleRequesterCancel(Player requester, String[] args) {
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        PendingRequesterConfirm pending = removePendingRequesterConfirm(requester.getUniqueId(), args);
        if (pending == null) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-no-pending", "&cYou do not have any pending teleport confirmation."
            )));
            return;
        }
        executeRequesterCancel(requester, pending, prefix);
    }

    private void cancelRequesterByAccepter(Player requester, UUID accepterUuid, long expectedRequestTime) {
        if (!requester.isOnline()) {
            return;
        }
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        PendingRequesterConfirm pending = removePendingRequesterByAccepter(requester.getUniqueId(), accepterUuid, expectedRequestTime);
        if (pending == null) {
            requester.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                    "tpa-world-no-pending", "&cYou do not have any pending teleport confirmation."
            )));
            return;
        }
        executeRequesterCancel(requester, pending, prefix);
    }

    private void executeRequesterCancel(Player requester, PendingRequesterConfirm pending, String prefix) {
        Player accepter = Bukkit.getPlayer(pending.accepterUuid);
        if (accepter != null && accepter.isOnline() && essentials != null) {
            User accepterUser = essentials.getUser(accepter);
            if (accepterUser != null) {
                removeRequestIgnoreCase(accepterUser, requester.getName());
            }
            String targetCancelMsg = messagesConfig.getString(
                    "tpa-world-cancelled-target", "&c&lCancelled! &e{player} &7cancelled the teleport request."
            );
            accepter.sendMessage(prefix + TextUtil.colorize(targetCancelMsg.replace("{player}", requester.getName())));
        }

        String requesterCancelMsg = messagesConfig.getString(
                "tpa-world-cancelled-requester", "&c&lCancelled! &7Request to &e{player} &7was cancelled."
        );
        requester.sendMessage(prefix + TextUtil.colorize(requesterCancelMsg.replace("{player}", pending.accepterName)));
    }

    private PendingRequesterConfirm removePendingRequesterByAccepter(UUID requesterUuid, UUID accepterUuid, long expectedRequestTime) {
        Map<UUID, PendingRequesterConfirm> map = pendingRequesterConfirms.get(requesterUuid);
        if (map == null || map.isEmpty()) {
            return null;
        }
        PendingRequesterConfirm candidate = map.get(accepterUuid);
        if (candidate == null || candidate.requestTime != expectedRequestTime) {
            return null;
        }
        if (!map.remove(accepterUuid, candidate)) {
            return null;
        }
        if (map.isEmpty()) {
            pendingRequesterConfirms.remove(requesterUuid);
        }
        return candidate;
    }

    private PendingRequesterConfirm removePendingRequesterConfirm(UUID requesterUuid, String[] args) {
        Map<UUID, PendingRequesterConfirm> map = pendingRequesterConfirms.get(requesterUuid);
        if (map == null || map.isEmpty()) {
            return null;
        }

        UUID matchedAccepterUuid = null;
        if (args.length >= 2) {
            String targetArg = args[1];
            Player target = Bukkit.getPlayer(targetArg);
            if (target != null && map.containsKey(target.getUniqueId())) {
                matchedAccepterUuid = target.getUniqueId();
            } else {
                for (Map.Entry<UUID, PendingRequesterConfirm> entry : map.entrySet()) {
                    if (entry.getValue().accepterName.equalsIgnoreCase(targetArg)) {
                        matchedAccepterUuid = entry.getKey();
                        break;
                    }
                }
            }
        } else {
            long latestTime = Long.MIN_VALUE;
            for (Map.Entry<UUID, PendingRequesterConfirm> entry : map.entrySet()) {
                if (entry.getValue().requestTime > latestTime) {
                    latestTime = entry.getValue().requestTime;
                    matchedAccepterUuid = entry.getKey();
                }
            }
        }

        if (matchedAccepterUuid == null) {
            return null;
        }
        PendingRequesterConfirm removed = map.remove(matchedAccepterUuid);
        if (map.isEmpty()) {
            pendingRequesterConfirms.remove(requesterUuid);
        }
        return removed;
    }

    private IUser.TpaRequest findRequestByRequester(User accepterUser, String requesterName) {
        IUser.TpaRequest req = accepterUser.getOutstandingTpaRequest(requesterName, false);
        if (req != null) {
            return req;
        }
        for (String key : accepterUser.getPendingTpaKeys()) {
            if (key.equalsIgnoreCase(requesterName)) {
                return accepterUser.getOutstandingTpaRequest(key, false);
            }
        }
        return null;
    }

    private void removeRequestIgnoreCase(User accepterUser, String requesterName) {
        if (accepterUser.removeTpaRequest(requesterName) != null) {
            return;
        }
        for (String key : new ArrayList<>(accepterUser.getPendingTpaKeys())) {
            if (key.equalsIgnoreCase(requesterName)) {
                accepterUser.removeTpaRequest(key);
            }
        }
    }

    private List<IUser.TpaRequest> resolveMatchingRequests(User accepterUser, String[] args, boolean isWildcard) {
        List<IUser.TpaRequest> matched = new ArrayList<>();
        if (args.length < 2) {
            IUser.TpaRequest next = accepterUser.getNextTpaRequest(false, false, false);
            if (next != null && !isRequestExpired(next)) {
                matched.add(next);
            }
            return matched;
        }

        if (isWildcard) {
            for (String key : new ArrayList<>(accepterUser.getPendingTpaKeys())) {
                IUser.TpaRequest candidate = accepterUser.getOutstandingTpaRequest(key, false);
                if (candidate != null && !isRequestExpired(candidate)) {
                    matched.add(candidate);
                }
            }
            return matched;
        }

        String targetArg = args[1];
        IUser.TpaRequest req = accepterUser.getOutstandingTpaRequest(targetArg, false);
        if (req == null) {
            Player matchedPlayer = Bukkit.getPlayer(targetArg);
            if (matchedPlayer != null) {
                req = accepterUser.getOutstandingTpaRequest(matchedPlayer.getName(), false);
            }
        }
        if (req == null) {
            String lowerArg = targetArg.toLowerCase(Locale.ENGLISH);
            for (String key : accepterUser.getPendingTpaKeys()) {
                if (key.equalsIgnoreCase(targetArg) || key.toLowerCase(Locale.ENGLISH).startsWith(lowerArg)) {
                    req = accepterUser.getOutstandingTpaRequest(key, false);
                    if (req != null) {
                        break;
                    }
                }
            }
        }

        if (req != null && !isRequestExpired(req)) {
            matched.add(req);
        }
        return matched;
    }

    private List<IUser.TpaRequest> filterWarningRequests(Player accepter, List<IUser.TpaRequest> candidates, boolean skipPortalBlocked) {
        List<IUser.TpaRequest> warned = new ArrayList<>();
        double portalRadius = settingsManager.getTpaPortalProtectionRadius();
        for (IUser.TpaRequest candidate : candidates) {
            if (skipPortalBlocked && detectPortalMaterial(resolveDestinationLocation(accepter, candidate), portalRadius) != null) {
                continue;
            }
            if (resolveWarningLocation(accepter, candidate) != null) {
                warned.add(candidate);
            }
        }
        return warned;
    }

    private void notifyPortalBlockedRequest(Player accepter, IUser.TpaRequest request, Location destLoc, Material portalMaterial) {
        Player requester = resolveRequester(request);
        boolean isHere = request.isHere();
        Player teleportee = isHere ? accepter : requester;
        Player destinationOwner = isHere ? requester : accepter;
        String destinationOwnerName = destinationOwner != null
                ? destinationOwner.getName()
                : (isHere ? request.getName() : accepter.getName());
        sendPortalCancelledMessages(teleportee, destinationOwner, destinationOwnerName, destLoc, portalMaterial);
    }

    private Player resolveRequester(IUser.TpaRequest request) {
        return request.getRequesterUuid() != null
                ? Bukkit.getPlayer(request.getRequesterUuid())
                : Bukkit.getPlayerExact(request.getName());
    }

    private void sendNotifyWarning(Player recipient, String subjectName, String worldName, String worldDisplay,
                                   String blockName, String timeoutStr, String delayStr,
                                   String keepInventory, boolean delayOneTick) {
        String notifyMsg = messagesConfig.getString(
                "tpa-world-notify-message",
                "&c&lWarning! &e{player} &7is in &c&l{world_display} &8(&7Block: &f{block}&8)&7! Move to cancel teleport."
        );
        if (notifyMsg == null || notifyMsg.isEmpty()) {
            return;
        }
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));
        String formatted = prefix + TextUtil.colorize(replaceTpaPlaceholders(
                notifyMsg, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory
        ));
        if (delayOneTick) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (recipient.isOnline()) {
                    recipient.sendMessage(formatted);
                }
            });
        } else {
            recipient.sendMessage(formatted);
        }
    }

    private boolean isRequestExpired(IUser.TpaRequest request) {
        long timeoutSec = essentials.getSettings().getTpaAcceptCancellation();
        return timeoutSec > 0 && (System.currentTimeMillis() - request.getTime()) > timeoutSec * 1000L;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleportRequestResponsePortalGuard(TeleportRequestResponseEvent event) {
        if (!event.isAccept() || !settingsManager.isTpaPortalProtectionEnabled()) {
            return;
        }
        IUser.TpaRequest req = event.getTpaRequest();
        if (req == null) {
            return;
        }
        net.ess3.api.IUser requesteeUser = event.getRequestee();
        net.ess3.api.IUser requesterUser = event.getRequester();
        Player accepter = requesteeUser != null ? requesteeUser.getBase() : null;
        Player requester = requesterUser != null ? requesterUser.getBase() : null;

        boolean isHere = req.isHere();
        Location liveRequesterLoc = (requester != null && requester.isOnline()) ? requester.getLocation() : null;
        Location accepterLoc = (accepter != null && accepter.isOnline()) ? accepter.getLocation() : null;
        Location destLoc = resolveRequestDestinationLocation(isHere, req.getLocation(), liveRequesterLoc, accepterLoc);

        Material portalMaterial = detectPortalMaterial(destLoc, settingsManager.getTpaPortalProtectionRadius());
        if (portalMaterial == null) {
            return;
        }

        event.setCancelled(true);
        Player teleportee = isHere ? accepter : requester;
        Player destinationOwner = isHere ? requester : accepter;
        String destinationOwnerName = destinationOwner != null
                ? destinationOwner.getName()
                : (isHere ? (requesterUser != null ? requesterUser.getName() : req.getName())
                          : (requesteeUser != null ? requesteeUser.getName() : "Player"));
        sendPortalCancelledMessages(teleportee, destinationOwner, destinationOwnerName, destLoc, portalMaterial);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportRequestResponse(TeleportRequestResponseEvent event) {
        if (!event.isAccept()) {
            return;
        }
        IUser.TpaRequest req = event.getTpaRequest();
        if (req == null) {
            return;
        }
        boolean isHere = req.isHere();
        boolean checkWorldWarning = settingsManager.isTpaWorldWarningEnabled()
                && (isHere ? settingsManager.isTpaWarningApplyToTpaHere() : settingsManager.isTpaWarningApplyToTpa());
        boolean checkPortalProtection = settingsManager.isTpaPortalProtectionEnabled();
        if (!checkWorldWarning && !checkPortalProtection) {
            return;
        }

        net.ess3.api.IUser teleporteeUser = isHere ? event.getRequestee() : event.getRequester();
        net.ess3.api.IUser counterpartUser = isHere ? event.getRequester() : event.getRequestee();
        if (teleporteeUser == null || teleporteeUser.getBase() == null) {
            return;
        }

        Player teleportee = teleporteeUser.getBase();
        Player counterpart = counterpartUser != null ? counterpartUser.getBase() : null;
        Location counterpartLoc = (counterpart != null && counterpart.isOnline()) ? counterpart.getLocation() : null;
        Location initialDest = isHere
                ? resolveRequestDestinationLocation(true, req.getLocation(), counterpartLoc, teleportee.getLocation())
                : counterpartLoc;
        String initialWorldName = (initialDest != null && initialDest.getWorld() != null)
                ? initialDest.getWorld().getName()
                : null;
        UUID counterpartUuid = counterpartUser != null ? counterpartUser.getUUID() : req.getRequesterUuid();
        String counterpartName = counterpart != null ? counterpart.getName()
                : (counterpartUser != null ? counterpartUser.getName() : req.getName());

        pendingWarmupArms.put(teleportee.getUniqueId(), new PendingWarmupArm(
                teleportee.getUniqueId(),
                counterpartUuid,
                counterpartName,
                initialWorldName,
                isHere,
                checkWorldWarning,
                checkPortalProtection,
                System.currentTimeMillis()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportWarmup(TeleportWarmupEvent event) {
        net.ess3.api.IUser teleporteeUser = event.getTeleportee() != null ? event.getTeleportee() : event.getTeleporter();
        if (teleporteeUser == null || teleporteeUser.getUUID() == null) {
            return;
        }
        UUID teleporteeUuid = teleporteeUser.getUUID();
        PendingWarmupArm arm = pendingWarmupArms.remove(teleporteeUuid);
        if (event.isCancelled() || arm == null || event.getTarget() == null || (System.currentTimeMillis() - arm.createdAt) > 2000L) {
            activeTpaGuards.remove(teleporteeUuid);
            return;
        }

        activeTpaGuards.put(teleporteeUuid, new ActiveTpaTeleportGuard(
                arm.teleporteeUuid,
                arm.counterpartUuid,
                arm.counterpartName,
                arm.initialWorldName,
                arm.isHere,
                arm.checkWorldWarning,
                arm.checkPortalProtection,
                event.getTarget()
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTeleport(PreTeleportEvent event) {
        net.ess3.api.IUser teleporteeUser = event.getTeleportee() != null ? event.getTeleportee() : event.getTeleporter();
        if (teleporteeUser == null || teleporteeUser.getUUID() == null) {
            return;
        }
        UUID teleporteeUuid = teleporteeUser.getUUID();
        ActiveTpaTeleportGuard guard = activeTpaGuards.remove(teleporteeUuid);
        if (guard == null || event.getTarget() != guard.expectedTarget) {
            return;
        }

        Location finalDest = event.getTarget() != null ? event.getTarget().getLocation() : null;
        if (finalDest == null || finalDest.getWorld() == null) {
            return;
        }

        Player teleportee = teleporteeUser.getBase();
        Player counterpart = guard.counterpartUuid != null ? Bukkit.getPlayer(guard.counterpartUuid) : null;

        if (guard.checkPortalProtection && settingsManager.isTpaPortalProtectionEnabled()) {
            Material portalMaterial = detectPortalMaterial(finalDest, settingsManager.getTpaPortalProtectionRadius());
            if (portalMaterial != null) {
                event.setCancelled(true);
                sendPortalCancelledMessages(teleportee, counterpart, guard.counterpartName, finalDest, portalMaterial);
                return;
            }
        }

        if (!guard.checkWorldWarning || !settingsManager.isTpaWorldWarningEnabled()) {
            return;
        }

        String finalWorldName = finalDest.getWorld().getName();
        boolean isFinalWarned = settingsManager.isTpaWarningWorld(finalWorldName);
        if (!shouldCancelWarmupTeleport(guard.initialWorldName, finalWorldName, isFinalWarned)) {
            return;
        }

        event.setCancelled(true);
        sendPairCancelMessages(
                teleportee,
                counterpart,
                guard.counterpartName,
                finalDest,
                "tpa-world-warmup-cancelled-teleportee",
                "&c&lCancelled! &e{player} &7moved to &c&l{world_display} &7before teleport.",
                "tpa-world-warmup-cancelled-target",
                "&c&lCancelled! &7Destination changed to &c&l{world_display} &7for &e{player}&7.",
                null
        );
    }

    private void sendPortalCancelledMessages(Player teleportee, Player destinationOwner,
                                             String destinationOwnerName, Location destLoc, Material portalMaterial) {
        sendPairCancelMessages(
                teleportee,
                destinationOwner,
                destinationOwnerName,
                destLoc,
                "tpa-portal-cancelled-teleportee",
                "&c&lCancelled! &e{player} &7is too close to the &c&l{portal}&7.",
                "tpa-portal-cancelled-target",
                "&c&lCancelled! &7Move away from the &c&l{portal} &7(&e{player}&7).",
                formatPortalName(portalMaterial)
        );
    }

    private void sendPairCancelMessages(Player teleportee, Player destinationOwner,
                                        String destinationOwnerName, Location destLoc,
                                        String teleporteeKey, String teleporteeDefault,
                                        String ownerKey, String ownerDefault,
                                        String portalName) {
        boolean hasWorld = destLoc != null && destLoc.getWorld() != null;
        String worldName = hasWorld ? destLoc.getWorld().getName() : "";
        String worldDisplay = worldName.isEmpty() ? "" : settingsManager.getTpaWorldDisplayName(worldName);
        String blockName = hasWorld ? getFormattedBlockUnder(destLoc) : "";
        String keepInventory = hasWorld ? resolveKeepInventoryLabel(destLoc.getWorld()) : "";
        String timeoutStr = essentials != null ? String.valueOf(essentials.getSettings().getTpaAcceptCancellation()) : "0";
        String delayStr = essentials != null ? formatDelay(essentials.getSettings().getTeleportDelay()) : "0";
        String prefix = TextUtil.colorize(messagesConfig.getString("tpa-prefix", messagesConfig.getString("prefix", "")));

        if (teleportee != null && teleportee.isOnline()) {
            String safeOwnerName = (destinationOwnerName != null && !destinationOwnerName.isBlank()) ? destinationOwnerName : "Player";
            String msg = messagesConfig.getString(teleporteeKey, teleporteeDefault);
            teleportee.sendMessage(prefix + TextUtil.colorize(replacePortalPlaceholder(
                    replaceTpaPlaceholders(msg, safeOwnerName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                    portalName
            )));
        }

        if (destinationOwner != null && destinationOwner.isOnline()
                && (teleportee == null || !destinationOwner.getUniqueId().equals(teleportee.getUniqueId()))) {
            String teleporteeName = teleportee != null ? teleportee.getName() : "Player";
            String msg = messagesConfig.getString(ownerKey, ownerDefault);
            destinationOwner.sendMessage(prefix + TextUtil.colorize(replacePortalPlaceholder(
                    replaceTpaPlaceholders(msg, teleporteeName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                    portalName
            )));
        }
    }

    @FunctionalInterface
    interface BlockMaterialLookup {
        Material getBlockType(int x, int y, int z);
    }

    private static final Map<Material, String> PORTAL_DISPLAY_NAMES = Map.of(
            Material.NETHER_PORTAL, "NETHER PORTAL",
            Material.END_PORTAL, "END PORTAL",
            Material.END_GATEWAY, "END GATEWAY"
    );

    static boolean isPortalMaterial(Material type) {
        return type != null && PORTAL_DISPLAY_NAMES.containsKey(type);
    }

    static String formatPortalName(Material portalMaterial) {
        if (portalMaterial == null) {
            return "PORTAL";
        }
        return PORTAL_DISPLAY_NAMES.getOrDefault(portalMaterial, portalMaterial.name().replace('_', ' '));
    }

    static double sanitizePortalRadius(double radius) {
        if (!Double.isFinite(radius)) {
            return DEFAULT_PORTAL_CHECK_RADIUS;
        }
        return Math.max(0.0, Math.min(MAX_PORTAL_CHECK_RADIUS, radius));
    }

    static Material detectPortalMaterial(Location location) {
        return detectPortalMaterial(location, DEFAULT_PORTAL_CHECK_RADIUS);
    }

    static Material detectPortalMaterial(Location location, double xzRadius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        return detectPortalMaterial(
                location.getX(),
                location.getY(),
                location.getZ(),
                world.getMinHeight(),
                world.getMaxHeight(),
                xzRadius,
                (bx, by, bz) -> {
                    if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                        return Material.AIR;
                    }
                    return world.getBlockAt(bx, by, bz).getType();
                }
        );
    }

    static Material detectPortalMaterial(double x, double y, double z,
                                           int worldMinY, int worldMaxExclusiveY,
                                           double xzRadius,
                                           BlockMaterialLookup lookup) {
        if (lookup == null || worldMaxExclusiveY <= worldMinY) {
            return null;
        }
        double safeRadius = sanitizePortalRadius(xzRadius);
        int minX = (int) Math.floor(x - safeRadius);
        int maxX = (int) Math.floor(x + safeRadius);
        int minY = Math.max(worldMinY, (int) Math.floor(y - PORTAL_CHECK_BELOW_PADDING));
        int maxY = Math.min(worldMaxExclusiveY - 1, (int) Math.floor(y + PORTAL_CHECK_ABOVE_HEIGHT));
        int minZ = (int) Math.floor(z - safeRadius);
        int maxZ = (int) Math.floor(z + safeRadius);

        int centerX = (int) Math.floor(x);
        int feetY = Math.max(worldMinY, Math.min(worldMaxExclusiveY - 1, (int) Math.floor(y)));
        int centerZ = (int) Math.floor(z);
        Material centerType = lookup.getBlockType(centerX, feetY, centerZ);
        if (isPortalMaterial(centerType)) {
            return centerType;
        }

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (bx == centerX && by == feetY && bz == centerZ) {
                        continue;
                    }
                    Material type = lookup.getBlockType(bx, by, bz);
                    if (isPortalMaterial(type)) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    private static String replacePortalPlaceholder(String template, String portalName) {
        return template == null ? "" : template.replace("{portal}", portalName == null ? "PORTAL" : portalName);
    }

    private Location resolveDestinationLocation(Player accepter, IUser.TpaRequest request) {
        boolean isHere = request.isHere();
        Player requester = isHere ? resolveRequester(request) : null;
        Location liveRequesterLoc = (requester != null && requester.isOnline()) ? requester.getLocation() : null;
        return resolveRequestDestinationLocation(
                isHere,
                request.getLocation(),
                liveRequesterLoc,
                accepter != null ? accepter.getLocation() : null
        );
    }

    static Location resolveRequestDestinationLocation(
            boolean isHere,
            Location requestSnapshotLoc,
            Location liveRequesterLoc,
            Location liveAccepterLoc
    ) {
        if (isHere) {
            if (requestSnapshotLoc != null && requestSnapshotLoc.getWorld() != null) {
                return requestSnapshotLoc;
            }
            return (liveRequesterLoc != null && liveRequesterLoc.getWorld() != null) ? liveRequesterLoc : null;
        }
        return liveAccepterLoc;
    }

    static boolean shouldCancelWarmupTeleport(
            String initialWorldName,
            String finalWorldName,
            boolean isFinalWorldWarned
    ) {
        if (!isFinalWorldWarned || finalWorldName == null) {
            return false;
        }
        return initialWorldName == null || !initialWorldName.equalsIgnoreCase(finalWorldName);
    }

    private Location resolveWarningLocation(Player accepter, IUser.TpaRequest request) {
        boolean isHere = request.isHere();
        if (isHere && !settingsManager.isTpaWarningApplyToTpaHere()) {
            return null;
        }
        if (!isHere && !settingsManager.isTpaWarningApplyToTpa()) {
            return null;
        }

        Location checkLocation = resolveDestinationLocation(accepter, request);

        if (checkLocation == null || checkLocation.getWorld() == null) {
            return null;
        }

        return settingsManager.isTpaWarningWorld(checkLocation.getWorld().getName()) ? checkLocation : null;
    }

    private TpaConfirmationDialogService.ConfirmationPrompt buildConfirmationPrompt(
            String sectionPath,
            String defaultTitle,
            List<String> defaultBody,
            String subjectName,
            String worldName,
            String worldDisplay,
            String blockName,
            String timeoutStr,
            String delayStr,
            String keepInventory,
            String fallbackAcceptCommand,
            String fallbackDenyCommand,
            long lifetimeSeconds,
            Runnable onAccept,
            Runnable onDeny,
            Runnable onDismiss) {
        ConfigurationSection section = messagesConfig.getConfigurationSection(sectionPath);
        String rawTitle = section != null ? section.getString("title", defaultTitle) : defaultTitle;

        List<String> rawLines;
        if (section != null && section.isList("body")) {
            rawLines = section.getStringList("body");
        } else if (section != null && section.isString("body")) {
            String single = section.getString("body", "");
            rawLines = single.isEmpty() ? defaultBody : List.of(single.split("\n", -1));
        } else {
            rawLines = defaultBody;
        }
        if (rawLines.isEmpty()) {
            rawLines = defaultBody;
        }

        List<String> formattedBody = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            formattedBody.add(replaceTpaPlaceholders(
                    line, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory
            ));
        }

        String acceptLabel = section != null ? section.getString("accept-button", "&a&lACCEPT") : "&a&lACCEPT";
        String acceptHover = section != null ? section.getString("accept-hover", "&7Click to accept teleport") : "&7Click to accept teleport";
        String denyLabel = section != null ? section.getString("deny-button", "&c&lDENY") : "&c&lDENY";
        String denyHover = section != null ? section.getString("deny-hover", "&7Click to deny teleport") : "&7Click to deny teleport";
        String buttonSpacing = messagesConfig.getString("tpa-button-spacing", "     ");
        String bedrockInstructions = messagesConfig.getString(
                "tpa-popup-bedrock-instructions",
                "&7Type: &e{accept_cmd} &7to accept or &e{deny_cmd} &7to deny"
        );

        return new TpaConfirmationDialogService.ConfirmationPrompt(
                replaceTpaPlaceholders(rawTitle, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                formattedBody,
                replaceTpaPlaceholders(acceptLabel, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                replaceTpaPlaceholders(acceptHover, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                replaceTpaPlaceholders(denyLabel, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                replaceTpaPlaceholders(denyHover, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                fallbackAcceptCommand,
                fallbackDenyCommand,
                buttonSpacing,
                replaceTpaPlaceholders(bedrockInstructions, subjectName, worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory),
                lifetimeSeconds,
                onAccept,
                onDeny,
                onDismiss
        );
    }

    private TpaConfirmationDialogService.DisplayChannel dispatchConfirmationPrompt(
            Player recipient,
            TpaConfirmationDialogService.ConfirmationPrompt prompt,
            boolean forceChatFallback) {
        if (forceChatFallback) {
            dialogService.sendChatFallback(recipient, prompt);
            return TpaConfirmationDialogService.DisplayChannel.CHAT_FALLBACK;
        }
        return dialogService.showConfirmation(recipient, prompt);
    }

    private void executeCommandWithFallback(Player player, String namespacedCmd, String shortCmd, String arg) {
        String base = Bukkit.getPluginCommand(namespacedCmd) != null ? namespacedCmd : shortCmd;
        String full = (arg != null && !arg.isBlank()) ? (base + " " + arg) : base;
        player.performCommand(full);
    }

    private String resolveKeepInventoryLabel(World world) {
        boolean enabled = world != null && Boolean.TRUE.equals(world.getGameRuleValue(GameRules.KEEP_INVENTORY));
        return enabled
                ? messagesConfig.getString("keep-inventory-on", "ON")
                : messagesConfig.getString("keep-inventory-off", "OFF");
    }

    private void sendCustomTpaMessage(Player sender, Player target, boolean isHere) {
        String worldName = sender.getWorld().getName();
        String worldDisplay = settingsManager.getTpaWorldDisplayName(worldName);
        String keepInventory = resolveKeepInventoryLabel(sender.getWorld());
        String blockName = getFormattedBlockUnder(sender.getLocation());
        String timeoutStr = String.valueOf(essentials.getSettings().getTpaAcceptCancellation());
        String delayStr = formatDelay(essentials.getSettings().getTeleportDelay());

        String requestMsgKey = isHere ? "tpa-request-here-message" : "tpa-request-message";
        String requestMsg = messagesConfig.getString(requestMsgKey, "{player} has requested to teleport.");
        String locMsg = messagesConfig.getString("tpa-request-location", "Location: {world} (Block: {block}) | Timeout: {timeout}s");
        String acceptBtnText = messagesConfig.getString("tpa-accept-button", "[ACCEPT]");
        String acceptHoverText = messagesConfig.getString("tpa-accept-hover", "Click to Accept");
        String denyBtnText = messagesConfig.getString("tpa-deny-button", "[DENY]");
        String denyHoverText = messagesConfig.getString("tpa-deny-hover", "Click to Deny");

        requestMsg = replaceTpaPlaceholders(requestMsg, sender.getName(), worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory);
        locMsg = replaceTpaPlaceholders(locMsg, sender.getName(), worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory);

        String fullMsgText = isHere ? (requestMsg + "\n" + locMsg + "\n\n") : (requestMsg + "\n\n");
        String bedrockInstructions = messagesConfig.getString(
                "tpa-bedrock-instructions",
                "&7Type: &e/tpaccept &7or &e/tpdeny &7to respond"
        );
        String bedrockFormatted = replaceTpaPlaceholders(
                bedrockInstructions, sender.getName(), worldName, worldDisplay, blockName, timeoutStr, delayStr, keepInventory
        );

        sendInteractiveTpaPrompt(
                target,
                fullMsgText,
                bedrockFormatted,
                acceptBtnText,
                acceptHoverText,
                "/tpaccept " + sender.getName(),
                denyBtnText,
                denyHoverText,
                "/tpdeny " + sender.getName()
        );
    }

    private void sendInteractiveTpaPrompt(Player recipient, String fullMsgText, String bedrockInstructions,
                                          String positiveBtnText, String positiveHoverText, String positiveCommand,
                                          String negativeBtnText, String negativeHoverText, String negativeCommand) {
        TextComponent mainMessage = new TextComponent();
        for (BaseComponent part : TextComponent.fromLegacyText(TextUtil.colorize(fullMsgText))) {
            mainMessage.addExtra(part);
        }

        if (dialogService.isBedrockPlayer(recipient)) {
            if (bedrockInstructions != null && !bedrockInstructions.isEmpty()) {
                mainMessage.addExtra(new TextComponent(TextUtil.colorize(bedrockInstructions)));
            }
        } else {
            TextComponent positiveBtn = createActionButton(positiveBtnText, positiveHoverText, positiveCommand);
            TextComponent negativeBtn = createActionButton(negativeBtnText, negativeHoverText, negativeCommand);

            mainMessage.addExtra(positiveBtn);
            String spacing = messagesConfig.getString("tpa-button-spacing", "     ");
            mainMessage.addExtra(new TextComponent(TextUtil.colorize(spacing)));
            mainMessage.addExtra(negativeBtn);
        }

        recipient.spigot().sendMessage(mainMessage);
    }

    private TextComponent createActionButton(String buttonText, String hoverText, String command) {
        TextComponent btn = new TextComponent();
        for (BaseComponent part : TextComponent.fromLegacyText(TextUtil.colorize(buttonText))) {
            btn.addExtra(part);
        }
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(TextUtil.colorize(hoverText)))));
        return btn;
    }

    private String getFormattedBlockUnder(Location location) {
        Block blockUnder = location.getBlock().getRelative(BlockFace.DOWN);
        String rawBlockName = blockUnder.getType().name().replace("_", " ").toLowerCase(Locale.ENGLISH);

        String[] words = rawBlockName.split(" ");
        StringBuilder capitalizedBlock = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalizedBlock.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return capitalizedBlock.toString().trim();
    }

    private String replaceTpaPlaceholders(String template, String playerName, String worldName,
                                          String worldDisplay, String blockName, String timeoutStr,
                                          String delayStr, String keepInventory) {
        if (template == null) {
            return "";
        }
        return template.replace("{player}", playerName)
                .replace("{world}", worldName)
                .replace("{world_display}", worldDisplay)
                .replace("{block}", blockName)
                .replace("{timeout}", timeoutStr)
                .replace("{delay}", delayStr)
                .replace("{keep_inventory}", keepInventory);
    }

    private String formatDelay(double delay) {
        if (delay == (long) delay) {
            return String.valueOf((long) delay);
        }
        return String.valueOf(delay);
    }
}
