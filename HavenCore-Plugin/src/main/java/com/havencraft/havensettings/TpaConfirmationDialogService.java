package com.havencraft.havensettings;

import com.havencraft.havencore.util.TextUtil;
import com.viaversion.viaversion.api.Via;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dispatches TPA/TPAHere confirmation popups to the appropriate UI per client:
 * - Bedrock players (Floodgate): Cumulus ModalForm popup
 * - Java players >= 1.21.6 (protocol >= 771): Native Paper Dialog confirmation popup
 * - Legacy Java players (< 1.21.6 via ViaVersion) or fallback: Interactive chat prompt
 */
@SuppressWarnings("deprecation")
public final class TpaConfirmationDialogService {

    static final int MIN_DIALOG_PROTOCOL_VERSION = 771; // Minecraft 1.21.6
    private static final int DIALOG_BODY_WIDTH = 360;
    private static final int DIALOG_BUTTON_WIDTH = 135;
    private static final boolean PAPER_DIALOG_AVAILABLE = checkPaperDialogAvailable();

    public enum DisplayChannel {
        JAVA_DIALOG,
        BEDROCK_FORM,
        CHAT_FALLBACK
    }

    public record ConfirmationPrompt(
            String rawTitle,
            List<String> rawBodyLines,
            String rawAcceptLabel,
            String rawAcceptHover,
            String rawDenyLabel,
            String rawDenyHover,
            String fallbackAcceptCommand,
            String fallbackDenyCommand,
            String buttonSpacing,
            String bedrockInstructions,
            long callbackLifetimeSeconds,
            Runnable onAccept,
            Runnable onDeny,
            Runnable onDismiss
    ) {
        public ConfirmationPrompt(
                String rawTitle,
                List<String> rawBodyLines,
                String rawAcceptLabel,
                String rawAcceptHover,
                String rawDenyLabel,
                String rawDenyHover,
                String fallbackAcceptCommand,
                String fallbackDenyCommand,
                String buttonSpacing,
                long callbackLifetimeSeconds,
                Runnable onAccept,
                Runnable onDeny,
                Runnable onDismiss) {
            this(
                    rawTitle,
                    rawBodyLines,
                    rawAcceptLabel,
                    rawAcceptHover,
                    rawDenyLabel,
                    rawDenyHover,
                    fallbackAcceptCommand,
                    fallbackDenyCommand,
                    buttonSpacing,
                    null,
                    callbackLifetimeSeconds,
                    onAccept,
                    onDeny,
                    onDismiss
            );
        }
    }

    static String extractBaseCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
        int spaceIdx = trimmed.indexOf(' ');
        return spaceIdx >= 0 ? trimmed.substring(0, spaceIdx) : trimmed;
    }

    static String formatBedrockInstructions(String template, String fallbackAcceptCommand, String fallbackDenyCommand) {
        if (template != null && template.isBlank()) {
            return "";
        }
        String effectiveTemplate = template != null
                ? template
                : "&7Type: &e{accept_cmd} &7to accept or &e{deny_cmd} &7to deny";
        String acceptCmd = extractBaseCommand(fallbackAcceptCommand);
        String denyCmd = extractBaseCommand(fallbackDenyCommand);
        return effectiveTemplate
                .replace("{accept_cmd}", acceptCmd)
                .replace("{deny_cmd}", denyCmd);
    }

    private final JavaPlugin plugin;

    public TpaConfirmationDialogService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public DisplayChannel showConfirmation(Player recipient, ConfirmationPrompt prompt) {
        if (recipient == null || !recipient.isOnline() || prompt == null) {
            return DisplayChannel.CHAT_FALLBACK;
        }

        if (isBedrockPlayer(recipient) && isFloodgatePluginEnabled()) {
            try {
                if (BedrockFormHelper.sendModal(plugin, recipient, prompt)) {
                    return DisplayChannel.BEDROCK_FORM;
                }
            } catch (Throwable ignored) {
                // Fall back to chat prompt if Floodgate form fails
            }
        } else if (supportsNativeJavaDialog(recipient)) {
            try {
                JavaDialogHelper.show(plugin, recipient, prompt);
                return DisplayChannel.JAVA_DIALOG;
            } catch (Throwable ignored) {
                // Fall back to chat prompt if native dialog fails
            }
        }

        sendChatFallback(recipient, prompt);
        return DisplayChannel.CHAT_FALLBACK;
    }

    public void sendChatFallback(Player recipient, ConfirmationPrompt prompt) {
        if (recipient == null || !recipient.isOnline() || prompt == null) {
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        if (prompt.rawTitle() != null && !prompt.rawTitle().isBlank()) {
            messageBuilder.append(prompt.rawTitle()).append("\n");
        }
        for (String line : prompt.rawBodyLines()) {
            messageBuilder.append(line == null ? "" : line).append("\n");
        }
        messageBuilder.append("\n");

        TextComponent mainMessage = new TextComponent();
        for (BaseComponent part : TextComponent.fromLegacyText(TextUtil.colorize(messageBuilder.toString()))) {
            mainMessage.addExtra(part);
        }

        if (isBedrockPlayer(recipient)) {
            String bedrockHint = formatBedrockInstructions(
                    prompt.bedrockInstructions(),
                    prompt.fallbackAcceptCommand(),
                    prompt.fallbackDenyCommand()
            );
            if (!bedrockHint.isEmpty()) {
                mainMessage.addExtra(new TextComponent(TextUtil.colorize(bedrockHint)));
            }
        } else {
            TextComponent acceptBtn = createChatButton(
                    formatChatButtonLabel(prompt.rawAcceptLabel()),
                    prompt.rawAcceptHover(),
                    prompt.fallbackAcceptCommand()
            );
            TextComponent denyBtn = createChatButton(
                    formatChatButtonLabel(prompt.rawDenyLabel()),
                    prompt.rawDenyHover(),
                    prompt.fallbackDenyCommand()
            );

            mainMessage.addExtra(acceptBtn);
            String spacing = prompt.buttonSpacing() != null ? prompt.buttonSpacing() : "     ";
            mainMessage.addExtra(new TextComponent(TextUtil.colorize(spacing)));
            mainMessage.addExtra(denyBtn);
        }

        recipient.spigot().sendMessage(mainMessage);
    }

    static String formatChatButtonLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "[]";
        }
        if (label.contains("[") && label.contains("]")) {
            return label;
        }
        String stripped = label.replace("[", "").replace("]", "");
        int index = 0;
        while (index < stripped.length()) {
            char c = stripped.charAt(index);
            if ((c == '&' || c == '§') && index + 1 < stripped.length()) {
                char next = stripped.charAt(index + 1);
                if (next == '#' && index + 7 < stripped.length()) {
                    index += 8;
                    continue;
                }
                index += 2;
                continue;
            }
            break;
        }
        return stripped.substring(0, index) + "[" + stripped.substring(index) + "]";
    }

    private TextComponent createChatButton(String buttonText, String hoverText, String command) {
        TextComponent btn = new TextComponent();
        for (BaseComponent part : TextComponent.fromLegacyText(TextUtil.colorize(buttonText))) {
            btn.addExtra(part);
        }
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        if (hoverText != null && !hoverText.isBlank()) {
            btn.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new Text(TextComponent.fromLegacyText(TextUtil.colorize(hoverText)))
            ));
        }
        return btn;
    }

    public boolean supportsNativeJavaDialog(Player player) {
        if (!PAPER_DIALOG_AVAILABLE || isBedrockPlayer(player)) {
            return false;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            try {
                int protocol = ViaHelper.getPlayerProtocol(player.getUniqueId());
                if (protocol > 0) {
                    return protocol >= MIN_DIALOG_PROTOCOL_VERSION;
                }
            } catch (Throwable ignored) {
                // Assume native server client if ViaVersion query fails
            }
        }
        return true;
    }

    public boolean isBedrockPlayer(Player player) {
        if (isFloodgatePluginEnabled()) {
            try {
                if (BedrockFormHelper.isFloodgatePlayer(player.getUniqueId())) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object apiInstance = geyserApiClass.getMethod("api").invoke(null);
                Object isBedrock = geyserApiClass.getMethod("isBedrockPlayer", UUID.class)
                        .invoke(apiInstance, player.getUniqueId());
                if (isBedrock instanceof Boolean && (Boolean) isBedrock) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        String name = player.getName();
        return name.startsWith("*") || name.startsWith(".");
    }

    private static boolean isFloodgatePluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    private static boolean checkPaperDialogAvailable() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
            Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void runOnMainThread(JavaPlugin plugin, Runnable action) {
        if (action == null || !plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    /**
     * Isolated helper so Paper Dialog classes are only loaded on Paper 1.21.6+ / 26.2 servers.
     */
    private static final class JavaDialogHelper {
        private static void show(JavaPlugin plugin, Player recipient, ConfirmationPrompt prompt) {
            Component title = TextUtil.parseLegacy(prompt.rawTitle());

            List<Component> bodyComponents = new ArrayList<>(prompt.rawBodyLines().size());
            for (String line : prompt.rawBodyLines()) {
                bodyComponents.add(line == null || line.isEmpty() ? Component.empty() : TextUtil.parseLegacy(line));
            }
            Component joinedBody = Component.join(JoinConfiguration.newlines(), bodyComponents);

            long lifetimeSeconds = Math.max(60L, prompt.callbackLifetimeSeconds());
            ClickCallback.Options options = ClickCallback.Options.builder()
                    .uses(1)
                    .lifetime(Duration.ofSeconds(lifetimeSeconds))
                    .build();

            ActionButton acceptButton = buildButton(
                    prompt.rawAcceptLabel(),
                    prompt.rawAcceptHover(),
                    (view, audience) -> runOnMainThread(plugin, prompt.onAccept()),
                    options
            );
            ActionButton denyButton = buildButton(
                    prompt.rawDenyLabel(),
                    prompt.rawDenyHover(),
                    (view, audience) -> runOnMainThread(plugin, prompt.onDeny()),
                    options
            );

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(joinedBody, DIALOG_BODY_WIDTH)))
                            .build())
                    // Use MultiAction instead of Confirmation so buttons sit directly below the body
                    // instead of being pinned to the very bottom footer of the screen.
                    .type(DialogType.multiAction(List.of(acceptButton, denyButton))
                            .columns(2)
                            .build())
            );

            recipient.showDialog(dialog);
        }

        private static ActionButton buildButton(String label,
                                                String tooltip,
                                                io.papermc.paper.registry.data.dialog.action.DialogActionCallback callback,
                                                ClickCallback.Options options) {
            ActionButton.Builder btnBuilder = ActionButton.builder(TextUtil.parseLegacy(label))
                    .width(DIALOG_BUTTON_WIDTH)
                    .action(DialogAction.customClick(callback, options));
            if (tooltip != null && !tooltip.isBlank()) {
                btnBuilder.tooltip(TextUtil.parseLegacy(tooltip));
            }
            return btnBuilder.build();
        }
    }

    /**
     * Isolated helper so Floodgate/Cumulus classes are only loaded when Floodgate is installed.
     */
    private static final class BedrockFormHelper {
        private static boolean isFloodgatePlayer(UUID uuid) {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.isFloodgatePlayer(uuid);
        }

        private static boolean sendModal(JavaPlugin plugin, Player recipient, ConfirmationPrompt prompt) {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null || !api.isFloodgatePlayer(recipient.getUniqueId())) {
                return false;
            }

            String title = TextUtil.colorize(prompt.rawTitle());
            String content = TextUtil.colorize(String.join("\n", prompt.rawBodyLines()));
            String acceptText = TextUtil.colorize(prompt.rawAcceptLabel());
            String denyText = TextUtil.colorize(prompt.rawDenyLabel());

            ModalForm form = ModalForm.builder()
                    .title(title)
                    .content(content)
                    .button1(acceptText)
                    .button2(denyText)
                    .validResultHandler(response -> {
                        if (response.clickedFirst()) {
                            runOnMainThread(plugin, prompt.onAccept());
                        } else {
                            runOnMainThread(plugin, prompt.onDeny());
                        }
                    })
                    .closedOrInvalidResultHandler(() -> runOnMainThread(plugin, prompt.onDismiss()))
                    .build();

            return api.sendForm(recipient.getUniqueId(), form);
        }
    }

    /**
     * Isolated helper so ViaVersion classes are only loaded when ViaVersion is installed.
     */
    private static final class ViaHelper {
        private static int getPlayerProtocol(UUID uuid) {
            return Via.getAPI().getPlayerVersion(uuid);
        }
    }
}
