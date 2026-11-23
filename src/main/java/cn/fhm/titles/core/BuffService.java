package cn.fhm.titles.core;

import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 称号 BUFF：佩戴期间维持无限时长药水效果，摘下/切换时精确移除本插件加上的效果。
 */
public final class BuffService {

    private final TitleRegistry registry;

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
        if (title == null) {
            return;
        }
        for (String raw : title.buffs) {
            Parsed parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(parsed.type(), -1, parsed.amplifier(), true, false, false));
        }
    }

    public void clear(Player player, Title previous) {
        if (previous == null) {
            return;
        }
        for (PotionEffectType type : parseEffects(previous)) {
            try {
                player.removePotionEffect(type);
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    /** 佩戴称号后防止被其他效果覆盖，定期补一次。 */
    public void ensure(Player player, PlayerData data) {
        if (data.equipped == null || data.equipped.isEmpty()) {
            return;
        }
        Title title = registry.get(data.equipped);
        if (title == null) {
            return;
        }
        for (String raw : title.buffs) {
            Parsed parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(parsed.type());
            if (current == null || current.getDuration() < 20) {
                player.addPotionEffect(new PotionEffect(parsed.type(), -1, parsed.amplifier(), true, false, false));
            }
        }
    }
}
