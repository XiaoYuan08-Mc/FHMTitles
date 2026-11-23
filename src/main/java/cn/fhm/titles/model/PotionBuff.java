package cn.fhm.titles.model;

import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 称号 BUFF 的编译结果。
 * 原来每次佩戴 / 每 30 秒补 BUFF 都要把 "SPEED:2" 这种字符串重新 split、查表，
 * 现在在 titles.yml 载入时解析一次，运行时只读 record。
 */
public record PotionBuff(PotionEffectType type, int amplifier) {

    private static final int MAX_AMPLIFIER = 30;

    public static List<PotionBuff> parseAll(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<PotionBuff> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            PotionBuff buff = parse(line);
            if (buff != null) {
                out.add(buff);
            }
        }
        return List.copyOf(out);
    }

    private static PotionBuff parse(String raw) {
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
        // 配置里写 1 表示一级，药水等级从 0 开始算
        return new PotionBuff(type, Math.min(MAX_AMPLIFIER, level - 1));
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType resolve(String name) {
        PotionEffectType type = PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
        if (type == null) {
            type = PotionEffectType.getByName(name.toLowerCase(Locale.ROOT));
        }
        if (type != null) {
            return type;
        }
        // 1.20.5 之后改过一批效果名，旧 titles.yml 里的写法继续兼容
        String legacy = switch (name.toUpperCase(Locale.ROOT)) {
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "DAMAGE_RESISTANCE" -> "RESISTANCE";
            case "FAST_DIGGING" -> "HASTE";
            case "SLOW" -> "SLOWNESS";
            case "JUMP" -> "JUMP_BOOST";
            case "CONFUSION" -> "NAUSEA";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            default -> null;
        };
        return legacy == null ? null : PotionEffectType.getByName(legacy);
    }
}
