package cn.fhm.titles.core;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.PotionBuff;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号 BUFF：佩戴期间维持无限时长药水效果，摘下 / 切换 / 到期 / 删定义时只移除插件加上的那一层，
 * 不会连带删掉玩家自己喝的同类型药水。
 *
 * 1.2.0 的两处改动：
 * 1. BUFF 词条在称号载入时已经编译成 PotionBuff，热路径不再 split 字符串；
 * 2. 进服清残留用的「全库 BUFF → 等级」索引只在称号库变更时重建，而不是每个玩家进服扫一遍全库。
 */
public final class BuffService {

    /** 无限时长；用字面值避免不同版本 API 常量名不一致。 */
    private static final int INFINITE = -1;
    private static final String BYPASS_WORLD_PERMISSION = "fhmtitles.buff.bypass-world";

    private final TitleRegistry registry;
    private final Settings settings;

    /** 玩家 -> (效果类型 -> 插件加上的等级)，用来判断"这层效果是不是我们加的"。 */
    private final Map<UUID, Map<PotionEffectType, Integer>> applied = new ConcurrentHashMap<>();

    private volatile int indexVersion = -1;
    private volatile Map<PotionEffectType, Set<Integer>> titleBuffIndex = Map.of();

    public BuffService(TitleRegistry registry, Settings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    /** 该世界里是否允许挂称号 BUFF。 */
    public boolean worldAllowed(Player player) {
        if (player == null) {
            return false;
        }
        List<String> disabled = settings == null ? null : settings.buffDisabledWorlds;
        if (disabled == null || disabled.isEmpty()) {
            return true;
        }
        if (!disabled.contains(player.getWorld().getName())) {
            return true;
        }
        return player.hasPermission(BYPASS_WORLD_PERMISSION);
    }

    public void apply(Player player, Title title) {
        if (player == null || title == null || title.potionBuffs.isEmpty()) {
            return;
        }
        if (!worldAllowed(player)) {
            return;
        }
        Map<PotionEffectType, Integer> own = applied.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        for (PotionBuff buff : title.potionBuffs) {
            player.addPotionEffect(new PotionEffect(buff.type(), INFINITE, buff.amplifier(), true, false, false));
            own.put(buff.type(), buff.amplifier());
        }
    }

    /** 摘下 / 切换称号时移除旧称号加上的效果。 */
    public void clear(Player player, Title previous) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, Integer> own = applied.get(uuid);
        if ((previous == null || previous.potionBuffs.isEmpty()) && (own == null || own.isEmpty())) {
            return;
        }
        // 旧称号声明过的类型 + 插件记录过的类型，两边都清一遍；
        // 只清「无限时长且等级对得上」的那一层，玩家自己喝的同类型药水不动
        Set<PotionEffectType> types = new java.util.LinkedHashSet<>();
        if (previous != null) {
            for (PotionBuff buff : previous.potionBuffs) {
                types.add(buff.type());
            }
        }
        if (own != null) {
            types.addAll(own.keySet());
        }
        for (PotionEffectType type : types) {
            PotionEffect current = player.getPotionEffect(type);
            Integer recorded = own == null ? null : own.get(type);
            int expected = expectedAmplifier(previous, type, recorded);
            boolean ours = current != null && current.getDuration() == INFINITE
                    && (recorded != null ? current.getAmplifier() == recorded
                    : expected != Integer.MIN_VALUE && current.getAmplifier() == expected);
            if (ours) {
                player.removePotionEffect(type);
            }
            if (own != null) {
                own.remove(type);
            }
        }
        if (own != null && own.isEmpty()) {
            applied.remove(uuid);
        }
    }

    private int expectedAmplifier(Title title, PotionEffectType type, Integer recorded) {
        if (recorded != null) {
            return recorded;
        }
        if (title == null) {
            return Integer.MIN_VALUE;
        }
        for (PotionBuff buff : title.potionBuffs) {
            if (buff.type().equals(type)) {
                return buff.amplifier();
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * 进服 / 重载时清理残留：把所有称号可能加过的效果里，不属于当前佩戴称号的那些去掉。
     * 判定收紧到"无限时长 + 等级与某个称号声明的等级一致"，玩家自己喝的同类型药水不会被动。
     */
    public void stripStale(Player player, Title equipped) {
        if (player == null) {
            return;
        }
        Set<PotionEffectType> keep = equipped == null ? Set.of() : typesOf(equipped);
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, Integer> own = applied.get(uuid);
        for (Map.Entry<PotionEffectType, Set<Integer>> entry : index().entrySet()) {
            if (keep.contains(entry.getKey())) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current == null || current.getDuration() != INFINITE) {
                continue;
            }
            if (!entry.getValue().contains(current.getAmplifier())) {
                continue;
            }
            player.removePotionEffect(entry.getKey());
            // 效果已经被摘掉，账本也要同步，避免以后误把玩家自己的同类型无限效果当成我们的
            if (own != null) {
                own.remove(entry.getKey());
            }
        }
        if (own != null && own.isEmpty()) {
            applied.remove(uuid);
        }
    }

    /** 佩戴称号后防止被其他效果覆盖，定期补一次。 */
    public void ensure(Player player, PlayerData data) {
        if (player == null || data == null || data.equipped == null || data.equipped.isEmpty()) {
            return;
        }
        Title title = registry.get(data.equipped);
        if (title == null || title.potionBuffs.isEmpty()) {
            return;
        }
        if (!worldAllowed(player)) {
            // 走进禁用世界后，插件之前挂上的那一层要摘掉，不然会跟着玩家一直生效
            clear(player, title);
            return;
        }
        Map<PotionEffectType, Integer> own = applied.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        for (PotionBuff buff : title.potionBuffs) {
            PotionEffect current = player.getPotionEffect(buff.type());
            if (current == null || (current.getDuration() != INFINITE && current.getDuration() < 20)
                    || current.getAmplifier() != buff.amplifier()) {
                player.addPotionEffect(new PotionEffect(buff.type(), INFINITE, buff.amplifier(), true, false, false));
            }
            own.put(buff.type(), buff.amplifier());
        }
    }

    public void forget(Player player) {
        if (player != null) {
            applied.remove(player.getUniqueId());
        }
    }

    private Set<PotionEffectType> typesOf(Title title) {
        if (title.potionBuffs.isEmpty()) {
            return Set.of();
        }
        Set<PotionEffectType> out = new HashSet<>(title.potionBuffs.size());
        for (PotionBuff buff : title.potionBuffs) {
            out.add(buff.type());
        }
        return out;
    }

    /**
     * 全库「BUFF 类型 -> 可能出现的等级」索引。
     * 以前每次进服都要遍历所有称号、再解析所有 BUFF 字符串；现在只在称号库版本变化时重建。
     */
    private Map<PotionEffectType, Set<Integer>> index() {
        int version = registry == null ? 0 : registry.version();
        if (version == indexVersion) {
            return titleBuffIndex;
        }
        Map<PotionEffectType, Set<Integer>> index = new HashMap<>();
        if (registry != null) {
            for (Title title : registry.all()) {
                for (PotionBuff buff : title.potionBuffs) {
                    index.computeIfAbsent(buff.type(), key -> new HashSet<>()).add(buff.amplifier());
                }
            }
        }
        Map<PotionEffectType, Set<Integer>> frozen = new HashMap<>(index.size());
        for (Map.Entry<PotionEffectType, Set<Integer>> entry : index.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        titleBuffIndex = Map.copyOf(frozen);
        indexVersion = version;
        return titleBuffIndex;
    }
}
