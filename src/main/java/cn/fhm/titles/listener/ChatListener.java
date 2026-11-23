package cn.fhm.titles.listener;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.DisplayService;
import cn.fhm.titles.gui.InputManager;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public final class ChatListener implements Listener {

    private final Settings settings;
    private final DisplayService display;
    private final DataStore store;
    private final InputManager input;

    public ChatListener(Settings settings, DisplayService display, DataStore store, InputManager input) {
        this.settings = settings;
        this.display = display;
        this.store = store;
        this.input = input;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (input.handleChat(player, event.message())) {
            event.setCancelled(true);
            return;
        }
        if (!settings.chatEnabled) {
            return;
        }
        PlayerData data = store.peek(player.getUniqueId());
        if (data != null && !data.showChat) {
            return;
        }
        // 聊天格式每 viewer 都会被渲染一次。以前每个 viewer 都重新解析一遍格式串，
        // 现在每条消息只解析一次，把消息本体留成占位槽，按 viewer 替换。
        Map<String, Component> tokens = new HashMap<>();
        tokens.put("prefix", display.prefix(player));
        tokens.put("suffix", display.suffix(player));
        tokens.put("player", Component.text(player.getName()));
        tokens.put("message", Component.text(TextUtil.SLOT));
        Component template = TextUtil.compose(settings.chatFormat, tokens);
        if (template.equals(Component.empty())) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> template.replaceText(
                TextReplacementConfig.builder()
                        .matchLiteral(TextUtil.SLOT)
                        .replacement(message)
                        .build()));
    }
}
