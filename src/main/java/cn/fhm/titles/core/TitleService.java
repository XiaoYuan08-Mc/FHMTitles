package cn.fhm.titles.core;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 称号核心逻辑：发放 / 回收 / 佩戴 / 到期 / 删除联动。
 */
public final class TitleService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;
    private final DisplayService display;
    private final BuffService buff;
    private final RewardService rewards;

    public TitleService(JavaPlugin plugin, Settings settings, Lang lang, TitleRegistry registry,
                        DataStore store, DisplayService display, BuffService buff) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.registry = registry;
        this.store = store;
        this.display = display;
        this.buff = buff;
        this.rewards = new RewardService(plugin, lang, registry, store, this);
    }

    public RewardService rewards() {
        return rewards;
    }

    public DisplayService display() {
        return display;
    }

    public Title equipped(Player player) {
        return display.equippedTitle(store.get(player));
    }

    public boolean owns(Player player, String id) {
        return store.get(player).ownedValid(id);
    }

    public boolean equip(Player player, String id, boolean announce) {
        PlayerData data = store.get(player);
        Title title = registry.get(id);
        if (title == null) {
            player.sendMessage(lang.prefix().append(lang.msg("error.title-not-found", "id", id)));
            return false;
        }
        if (!title.enabled) {
            player.sendMessage(lang.prefix().append(lang.msg("error.title-disabled", "id", title.id)));
            return false;
        }
        if (!data.ownedValid(title.id)) {
            player.sendMessage(lang.prefix().append(lang.msg("error.not-owned", "title", title.id)));
            return false;
        }
        if (!title.permission.isEmpty() && !player.hasPermission(title.permission)) {
            player.sendMessage(lang.prefix().append(lang.msg("error.no-permission-title", "title", title.id)));
            return false;
        }
        Title previous = display.equippedTitle(data);
        data.equipped = title.id;
        buff.clear(player, previous);
        display.refresh(player);
        buff.apply(player, title);
        store.markDirty(player.getUniqueId());
        if (announce) {
            Map<String, Component> rich = new HashMap<>();
            rich.put("title", display.titleComponent(title, player));
            player.sendMessage(lang.prefix().append(lang.msg("title.equipped", rich)));
            if (settings.actionbarOnEquip) {
                player.sendActionBar(lang.msg("title.equipped-bar", rich));
            }
        }
        return true;
    }

    public void unequip(Player player) {
        PlayerData data = store.get(player);
        Title previous = display.equippedTitle(data);
        data.equipped = "";
        buff.clear(player, previous);
        display.refresh(player);
        store.markDirty(player.getUniqueId());
    }

    public boolean grant(Player player, String id, int days, boolean notify) {
        Title title = registry.get(id);
        if (title == null || !title.enabled) {
            return false;
        }
        PlayerData data = store.get(player);
        boolean permanent = days < 0 || title.permanent();
        data.grant(title.id, permanent ? -1 : days);
        if (data.equipped.isEmpty()) {
            data.equipped = title.id;
            display.refresh(player);
            buff.apply(player, title);
        }
        store.markDirty(player.getUniqueId());
        store.save(player.getUniqueId());
        if (notify) {
            notifyAcquire(player, title, permanent ? -1 : days);
        }
        rewards.check(player);
        return true;
    }

    /**
     * 离线发放：只改数据（进服时 onJoin 会自动刷新显示并补 BUFF）。
     * 供 /titles give、称号卡邮箱类场景使用。
     */
    public boolean grantOffline(PlayerData data, String id, int days) {
        Title title = registry.get(id);
        if (title == null || !title.enabled || data == null) {
            return false;
        }
        boolean permanent = days < 0 || title.permanent();
        data.grant(title.id, permanent ? -1 : days);
        if (data.equipped.isEmpty()) {
            data.equipped = title.id;
        }
        store.markDirty(data.uuid);
        store.save(data.uuid);
        return true;
    }

    /** 离线回收。 */
    public boolean revokeOffline(PlayerData data, String id) {
        if (data == null || !data.owned.containsKey(id)) {
            return false;
        }
        data.revoke(id);
        store.markDirty(data.uuid);
        store.save(data.uuid);
        return true;
    }

    /** 获取提示支持 CHAT / ACTIONBAR / TITLE 三种展示方式。 */    private void notifyAcquire(Player player, Title title, int days) {
        Map<String, Component> rich = new HashMap<>();
        rich.put("title", display.titleComponent(title, player));
        Component message = days < 0
                ? lang.msg("title.acquired-permanent", rich)
                : lang.msg("title.acquired-temp", rich, "days", String.valueOf(days));
        switch (settings.acquireMessageType) {
            case "ACTIONBAR" -> player.sendActionBar(message);
            case "TITLE" -> player.showTitle(net.kyori.adventure.title.Title.title(message, Component.empty(),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                            java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(600))));
            case "NONE" -> {
                // 不提示
            }
            default -> player.sendMessage(lang.prefix().append(message));
        }
    }

    public boolean revoke(Player player, String id) {
        PlayerData data = store.get(player);
        if (!data.owned.containsKey(id)) {
            return false;
        }
        boolean wasEquipped = data.equipped.equals(id);
        Title previous = wasEquipped ? registry.get(id) : null;
        data.revoke(id);
        if (wasEquipped) {
            buff.clear(player, previous);
        }
        display.refresh(player);
        store.markDirty(player.getUniqueId());
        store.save(player.getUniqueId());
        return true;
    }

    public void addCoins(Player player, long amount) {
        PlayerData data = store.get(player);
        data.coins = Math.max(0, data.coins + amount);
        store.markDirty(player.getUniqueId());
    }

    public void onJoin(Player player) {
        PlayerData data = store.get(player);
        data.name = player.getName();
        cleanupExpired(player, data, false);
        // 上一次下线时残留的称号 BUFF 先按当前佩戴情况清干净，再补上
        Title title = display.equippedTitle(data);
        buff.stripStale(player, title);
        display.refresh(player);
        if (title != null) {
            buff.apply(player, title);
        }
        rewards.check(player);
    }

    public void onQuit(Player player) {
        store.save(player.getUniqueId());
        display.onQuit(player);
        buff.forget(player);
    }

    /** 到期检查：每 expireCheckSeconds 执行一次。 */
    public void tickExpiry() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = store.get(player);
            cleanupExpired(player, data, true);
            if (display.equippedTitle(data) != null) {
                buff.ensure(player, data);
            }
        }
    }

    private void cleanupExpired(Player player, PlayerData data, boolean notify) {
        boolean changed = false;
        Title lost = null;
        for (String id : new java.util.ArrayList<>(data.owned.keySet())) {
            if (!data.ownedValid(id)) {
                data.owned.remove(id);
                if (data.equipped.equals(id)) {
                    data.equipped = "";
                    lost = registry.get(id);
                }
                changed = true;
                if (notify) {
                    player.sendMessage(lang.prefix().append(lang.msg("title.expired", "title", id)));
                }
            }
        }
        if (lost != null) {
            buff.clear(player, lost);
        }
        if (changed) {
            store.markDirty(player.getUniqueId());
            store.save(player.getUniqueId());
            display.refresh(player);
        }
    }

    /** 删除称号定义时的联动清理（玩家仓库 / 已购记录 / 佩戴状态）。 */
    public void deleteDefinition(String id) {
        Title removed = registry.get(id);
        registry.remove(id);
        registry.save();
        for (PlayerData data : store.all()) {
            boolean changed = false;
            if (data.owned.remove(id) != null) {
                changed = true;
            }
            if (data.purchased.remove(id)) {
                changed = true;
            }
            boolean wasEquipped = data.equipped.equals(id);
            if (wasEquipped) {
                data.equipped = "";
                changed = true;
            }
            if (changed) {
                store.markDirty(data.uuid);
                store.save(data.uuid);
            }
            if (wasEquipped) {
                Player online = Bukkit.getPlayer(data.uuid);
                if (online != null) {
                    buff.clearTypes(online, buff.parseEffects(removed));
                    display.refresh(online);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (store.get(player).equipped.isEmpty()) {
                display.refresh(player);
            }
        }
    }

    public void reloadAll() {
        registry.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = store.get(player);
            Title title = display.equippedTitle(data);
            buff.stripStale(player, title);
            display.refresh(player);
            if (title != null) {
                buff.ensure(player, data);
            }
        }
        display.refreshAll();
    }
}
