package cn.fhm.titles.econ;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * PlayerPoints 点券软依赖（纯反射）。
 */
public final class PointsHook {

    private Object api;
    private Method look;
    private Method take;
    private Method give;
    private boolean available;

    public PointsHook() {
        resolve();
    }

    private void resolve() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (plugin == null) {
                return;
            }
            Object rawApi = plugin.getClass().getMethod("getAPI").invoke(plugin);
            if (rawApi == null) {
                return;
            }
            Class<?> apiClass = rawApi.getClass();
            look = apiClass.getMethod("look", UUID.class);
            take = apiClass.getMethod("take", UUID.class, int.class);
            give = apiClass.getMethod("give", UUID.class, int.class);
            api = rawApi;
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    public int balance(Player player) {
        if (!available) {
            return 0;
        }
        try {
            Object value = look.invoke(api, player.getUniqueId());
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public boolean take(Player player, int amount) {
        if (!available || amount <= 0) {
            return false;
        }
        try {
            Object result = take.invoke(api, player.getUniqueId(), amount);
            return result instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean give(Player player, int amount) {
        if (!available) {
            return false;
        }
        try {
            Object result = give.invoke(api, player.getUniqueId(), amount);
            return result instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
