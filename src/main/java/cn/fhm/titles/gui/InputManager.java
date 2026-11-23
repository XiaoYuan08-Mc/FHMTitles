package cn.fhm.titles.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 后台 GUI 的聊天输入框：管理员点按钮后直接在聊天栏输入新值。
 * 主线程 request 与 AsyncChatEvent 线程 remove 并发，所以用 ConcurrentHashMap。
 */
public final class InputManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

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
            // 这里跑在 AsyncChatEvent 线程上，发包/发消息必须回主线程
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> player.sendMessage(Component.text("已取消输入")));
            return true;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> consumer.accept(text));
        return true;
    }
}
