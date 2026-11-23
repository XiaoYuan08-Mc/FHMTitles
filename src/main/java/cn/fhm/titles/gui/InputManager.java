package cn.fhm.titles.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 后台 GUI 的聊天输入框：管理员点按钮后直接在聊天栏输入新值。
 */
public final class InputManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new HashMap<>();

    public InputManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Component prompt, Consumer<String> consumer) {
        pending.put(player.getUniqueId(), consumer);
        player.closeInventory();
        player.sendMessage(prompt);
    }

    public boolean pending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    public boolean handleChat(Player player, Component message) {
        Consumer<String> consumer = pending.remove(player.getUniqueId());
        if (consumer == null) {
            return false;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("取消")) {
            player.sendMessage(Component.text("已取消输入"));
            return true;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> consumer.accept(text));
        return true;
    }
}
