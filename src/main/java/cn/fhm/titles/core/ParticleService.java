package cn.fhm.titles.core;

import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class ParticleService {

    private final TitleRegistry registry;
    private final DataStore store;

    public ParticleService(TitleRegistry registry, DataStore store) {
        this.registry = registry;
        this.store = store;
    }

    public void tick() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = store.get(player);
            if (data.equipped == null || data.equipped.isEmpty()) {
                continue;
            }
            Title title = registry.get(data.equipped);
            if (title == null || !title.enabled || data.ownedValid(title.id) == false) {
                continue;
            }
            spawn(player, title);
        }
    }

    public void spawn(Player player, Title title) {
        if (title.particle == null || title.particle.isBlank()) {
            return;
        }
        String raw = title.particle.trim();
        String name = raw;
        Color color = null;
        double size = 1.0d;
        int idx = raw.indexOf(':');
        if (idx > 0) {
            name = raw.substring(0, idx);
            String rest = raw.substring(idx + 1);
            String[] parts = rest.split(":");
            if (!parts[0].isBlank()) {
                int rgb = TextUtil.parseHex(parts[0].startsWith("#") ? parts[0] : "#" + parts[0]);
                color = Color.fromRGB(rgb);
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
        try {
            particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        Location location = player.getLocation().add(0, 1.9d, 0);
        int count = Math.max(1, Math.min(60, title.particleCount));
        double spread = 0.35d;
        Object data = null;
        if (particle == Particle.DUST || particle.name().contains("DUST")) {
            if (color == null) {
                color = Color.fromRGB(0xFFD966);
            }
            data = new Particle.DustOptions(color, (float) Math.max(0.3d, Math.min(3.0d, size)));
        }
        player.getWorld().spawnParticle(particle, location, count, spread, 0.2d, spread, 0.02d, data);
    }
}
