package cn.fhm.titles.listener;

import cn.fhm.titles.FHMTitlesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * 资源包分发与状态跟踪：玩家接过包并成功加载后才显示图片字形，否则显示回退文字。
 */
public final class ResourcePackListener implements Listener {

    private final FHMTitlesPlugin plugin;

    public ResourcePackListener(FHMTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.images() == null) {
            return;
        }
        plugin.images().onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (plugin.images() == null) {
            return;
        }
        plugin.images().onStatus(event.getPlayer(), event.getID(), event.getStatus().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.images() != null) {
            plugin.images().onQuit(event.getPlayer());
        }
    }
}
