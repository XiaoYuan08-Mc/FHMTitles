package cn.fhm.titles.listener;

import cn.fhm.titles.core.BuffService;
import cn.fhm.titles.core.DisplayService;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.model.Title;
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
    private final BuffService buff;

    public PlayerListener(JavaPlugin plugin, TitleService titles, DisplayService display, BuffService buff) {
        this.plugin = plugin;
        this.titles = titles;
        this.display = display;
        this.buff = buff;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> titles.onJoin(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        titles.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.removeHead(player);
            display.refresh(player);
            Title title = titles.equipped(player);
            buff.apply(player, title);
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.removeHead(player);
            display.refresh(player);
        }, 3L);
    }
}
