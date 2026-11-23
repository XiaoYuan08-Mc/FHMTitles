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
                        rewards.add(new Reward(count,
                                RewardType.valueOf(entry.getString("type", "TITLE").toUpperCase()),
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
                    String type = (typeRaw == null ? "TITLE" : String.valueOf(typeRaw)).toUpperCase();
                    Object valueRaw = map.get("value");
                    rewards.add(new Reward(count, RewardType.valueOf(type),
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
}
