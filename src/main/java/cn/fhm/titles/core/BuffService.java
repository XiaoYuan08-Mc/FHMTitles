package cn.fhm.titles.core;

import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号 BUFF：佩戴期间维持无限时长药水效果，摘下 / 切换 / 到期 / 删定义时只移除本插件加上的那一层，
 * 不会连带删掉玩家自己喝的同类型药水。
 */
public final class BuffService {

    /** 无限时长；用字面值避免不同版本 API 常量名不一致。 */
    private static final int INFINITE = -1;

    private final TitleRegistry registry;

    /** 玩家 -> (效果类型 -> 本插件加上的等级)，用来判断"这层效果是不是我们加的"。 */
    private final Map<UUID, Map<PotionEffectType, Integer>> applied = new ConcurrentHashMap<>();

    public BuffService(TitleRegistry registry) {
        this.registry = registry;
    }

    private record Parsed(PotionEffectType type, int amplifier) {
    }

    private Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        String name = parts[0].trim();
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        }
        PotionEffectType type = resolve(name);
        if (type == null) {
            return null;
        }
        return new Parsed(type, Math.min(30, level - 1));
    }

    @SuppressWarnings("deprecation")
    private PotionEffectType resolve(String name) {
        PotionEffectType type = PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
        if (type == null) {
            type = PotionEffectType.getByName(name.toLowerCase(Locale.ROOT));
        }
        return type;
    }

    public Set<PotionEffectType> parseEffects(Title title) {
        Set<PotionEffectType> out = new HashSet<>();
        if (title == null || title.buffs == null) {
            return out;
        }
        for (String raw : title.buffs) {
            Parsed parsed = parse(raw);
            if (parsed != null) {
                out.add(parsed.type());
            }
        }
        return out;
    }

    public void apply(Player player, Title title) {
        if (player == null || title == null || title.buffs == null) {
            return;
        }
        Map<PotionEffectType, Integer> own = applied.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        for (String raw : title.buffs) {
            Parsed parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(parsed.type(), INFINITE, parsed.amplifier(), true, false, false));
            own.put(parsed.type(), parsed.amplifier());
        }
    }

    /** 摘下 / 切换称号时移除旧称号加上的效果。 */
    public void clear(Player player, Title previous) {
        if (previous == null) {
            return;
        }
        clearTypes(player, parseEffects(previous));
    }

    /** 称号定义被删除时拿不到 Title 对象，按类型清理。 */
    public void clearTypes(Player player, Collection<PotionEffectType> types) {
        if (player == null || types == null || types.isEmpty()) {
            return;
        }
        Map<PotionEffectType, Integer> own = applied.get(player.getUniqueId());
        for (PotionEffectType type : types) {
            PotionEffect current = player.getPotionEffect(type);
            Integer amplifier = own == null ? null : own.remove(type);
            if (current == null) {
                continue;
            }
            // 只有"无限时长"或"等级与本插件记录一致"的效果才认定是我们加的
            boolean ours = current.getDuration() == INFINITE
                    || (amplifier != null && current.getAmplifier() == amplifier);
            if (ours) {
                player.removePotionEffect(type);
            }
        }
        if (own != null && own.isEmpty()) {
            applied.remove(player.getUniqueId());
        }
    }

    /**
     * 进服 / 重载时清理残留：把所有称号可能加过的效果里，不属于当前佩戴称号的那些去掉，
     * 避免换了称号或删了定义后旧 BUFF 一直挂着。
     */
    public void stripStale(Player player, Title equipped) {
        if (player == null) {
            return;
        }
        Set<PotionEffectType> keep = parseEffects(equipped);
        Set<PotionEffectType> all = new HashSet<>();
        if (registry != null) {
            for (Title title : registry.all()) {
                all.addAll(parseEffects(title));
            }
        }
        all.removeAll(keep);
        clearTypes(player, all);
    }

    /** 佩戴称号后防止被其他效果覆盖，定期补一次。 */
    public void ensure(Player player, PlayerData data) {
        if (data.equipped == null || data.equipped.isEmpty()) {
            return;
        }
        Title title = registry.get(data.equipped);
        if (title == null || title.buffs == null) {
            return;
        }
        Map<PotionEffectType, Integer> own = applied.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        for (String raw : title.buffs) {
            Parsed parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(parsed.type());
            if (current == null || (current.getDuration() != INFINITE && current.getDuration() < 20)
                    || current.getAmplifier() != parsed.amplifier()) {
                player.addPotionEffect(new PotionEffect(parsed.type(), INFINITE, parsed.amplifier(), true, false, false));
            }
            own.put(parsed.type(), parsed.amplifier());
        }
    }

    public void forget(Player player) {
        if (player != null) {
            applied.remove(player.getUniqueId());
        }
    }
}
