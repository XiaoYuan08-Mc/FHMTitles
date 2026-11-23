package cn.fhm.titles.model;

import cn.fhm.titles.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Locale;

/**
 * 称号粒子的编译结果。
 * 粒子配置里 "DUST:#ff8800:1.2" 这种字符串以前在每个粒子周期都要重新解析一遍，
 * 连 DustOptions 都要重新 new；现在解析一次，运行时直接拿现成的参数。
 */
public record ParticleSpec(Particle particle, Color color, float size, int count, Object extra) {

    public static ParticleSpec parse(String raw, int configuredCount) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim();
        Color color = null;
        double size = 1.0d;
        int idx = raw.indexOf(':');
        if (idx > 0) {
            name = raw.substring(0, idx).trim();
            String rest = raw.substring(idx + 1);
            String[] parts = rest.split(":");
            if (!parts[0].isBlank()) {
                String hex = parts[0].startsWith("#") ? parts[0] : "#" + parts[0];
                color = Color.fromRGB(TextUtil.parseHex(hex));
            }
            if (parts.length > 1) {
                try {
                    size = Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) {
                    size = 1.0d;
                }
            }
        }
        Particle particle;
        String key = name.toUpperCase(Locale.ROOT);
        // 旧配置里的 REDSTONE 就是现在的 DUST（带颜色/大小）
        if ("REDSTONE".equals(key)) {
            key = "DUST";
        }
        try {
            particle = Particle.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int count = Math.max(1, Math.min(60, configuredCount));
        float dustSize = (float) Math.max(0.3d, Math.min(3.0d, size));
        Object extra = null;
        if (particle == Particle.DUST) {
            Color dustColor = color == null ? Color.fromRGB(0xFFD966) : color;
            extra = new Particle.DustOptions(dustColor, dustSize);
        }
        return new ParticleSpec(particle, color, dustSize, count, extra);
    }
}
