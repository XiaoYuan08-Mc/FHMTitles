package cn.fhm.titles.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 软依赖桥接。未安装时全部方法静默降级。
 */
public final class PapiHook {

    private final boolean available;

    public PapiHook() {
        available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean available() {
        return available;
    }

    public String apply(Player player, String text) {
        if (!available || player == null || text == null || text.isEmpty()) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
