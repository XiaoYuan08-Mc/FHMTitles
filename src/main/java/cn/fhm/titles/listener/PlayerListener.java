package cn.fhm.titles.listener;

import cn.fhm.titles.core.DisplayService;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.gui.InputManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final TitleService titles;
    private final DisplayService display;
    private final InputManager input;

    public PlayerListener(JavaPlugin plugin, TitleService titles, DisplayService display, InputManager input) {
        this.plugin = plugin;
        this.titles = titles;
        this.display = display;
        this.input = input;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                titles.onJoin(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // 玩家没输入完就退出时，清掉待处理的 GUI 输入回调，别把 Player 引用留在内存里
        input.cancel(event.getPlayer());
        titles.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            display.removeHead(player);
            titles.resync(player);
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            display.removeHead(player);
            titles.resync(player);
        }, 3L);
    }
}
