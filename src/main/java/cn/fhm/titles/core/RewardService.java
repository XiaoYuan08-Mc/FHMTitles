package cn.fhm.titles.core;

import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 称号数量奖励：拥有称号数量达到阈值自动发放，带 GUI 领取记录。
 */
public final class RewardService {

    public enum RewardType {
        TITLE, COIN
    }

    public record Reward(int count, RewardType type, String value, long amount) {
    }

    private final JavaPlugin plugin;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;
    private final TitleService titles;
    private final List<Reward> rewards = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean checking = new java.util.concurrent.atomic.AtomicBoolean();

    public RewardService(JavaPlugin plugin, Lang lang, TitleRegistry registry, DataStore store, TitleService titles) {
        this.plugin = plugin;
        this.lang = lang;
        this.registry = registry;
        this.store = store;
        this.titles = titles;
        load();
    }

    public void load() {
        rewards.clear();
        List<?> raw = plugin.getConfig().getList("rewards");
        if (raw == null) {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("rewards");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection entry = section.getConfigurationSection(key);
                    if (entry == null) {
                        continue;
                    }
                    int count = parseInt(key, 0);
                    if (count > 0) {
                        RewardType type = parseType(entry.getString("type", "TITLE"));
                        if (type == null) {
                            continue;
                        }
                        rewards.add(new Reward(count, type,
                                entry.getString("value", ""),
                                entry.getLong("amount", 0)));
                    }
                }
            }
        } else {
            for (Object element : raw) {
                if (element instanceof java.util.Map<?, ?> map) {
                    int count = parseInt(map.get("count"), 0);
                    if (count <= 0) {
                        continue;
                    }
                    Object typeRaw = map.get("type");
                    RewardType type = parseType(typeRaw == null ? "TITLE" : String.valueOf(typeRaw));
                    if (type == null) {
                        continue;
                    }
                    Object valueRaw = map.get("value");
                    rewards.add(new Reward(count, type,
                            valueRaw == null ? "" : String.valueOf(valueRaw),
                            map.get("amount") instanceof Number n ? n.longValue() : 0));
                }
            }
        }
        rewards.sort(java.util.Comparator.comparingInt(Reward::count));
    }

    public List<Reward> rewards() {
        return rewards;
    }

    public int ownedCount(PlayerData data) {
        int total = 0;
        for (String id : data.owned.keySet()) {
            if (registry.get(id) != null) {
                total++;
            }
        }
        return total;
    }

    public void check(Player player) {
        // grant() 内部会回调 check()，这里加个闸防止重入导致提示乱序
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            doCheck(player);
        } finally {
            checking.set(false);
        }
    }

    private void doCheck(Player player) {
        PlayerData data = store.get(player);
        int owned = ownedCount(data);
        for (Reward reward : rewards) {
            String key = "count_" + reward.count();
            if (owned < reward.count() || data.claimed.contains(key)) {
                continue;
            }
            data.claimed.add(key);
            store.markDirty(player.getUniqueId());
            if (reward.type() == RewardType.TITLE) {
                Title title = registry.get(reward.value());
                if (title != null) {
                    titles.grant(player, title.id, -1, true);
                }
            } else {
                data.coins += reward.amount();
                player.sendMessage(lang.prefix().append(lang.msg("reward.coins", "amount", String.valueOf(reward.amount()))));
            }
            Component message = lang.msg("reward.unlocked", "count", String.valueOf(reward.count()));
            player.sendMessage(lang.prefix().append(message));
        }
    }

    public boolean claimed(Player player, int count) {
        return store.get(player).claimed.contains("count_" + count);
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 配置写错时不抛异常，只跳过这一条奖励。 */
    private RewardType parseType(String raw) {
        try {
            return RewardType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("[数量奖励] 未知 type: " + raw + "（只能是 TITLE 或 COIN），已跳过");
            return null;
        }
    }
}
