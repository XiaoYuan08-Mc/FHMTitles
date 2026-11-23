package cn.fhm.titles.listener;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.DisplayService;
import cn.fhm.titles.gui.InputManager;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
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
        PlayerData data = store.get(player);
        if (!data.showChat) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Map<String, Component> tokens = new HashMap<>();
            tokens.put("prefix", display.prefix(source));
            tokens.put("suffix", display.suffix(source));
            tokens.put("player", Component.text(source.getName()));
            tokens.put("message", message);
            return TextUtil.compose(settings.chatFormat, tokens);
        });
    }
}
