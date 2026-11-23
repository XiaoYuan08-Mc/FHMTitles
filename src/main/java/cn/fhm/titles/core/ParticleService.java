package cn.fhm.titles.core;

import cn.fhm.titles.model.ParticleSpec;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 称号粒子周期。
 * 粒子的名字 / 颜色 / 大小 / DustOptions 都在载入期解析好了，这里只做坐标计算和发包；
 * 全库没有带粒子的称号时整个周期直接返回。
 */
public final class ParticleService {

    private final TitleRegistry registry;
    private final DataStore store;

    public ParticleService(TitleRegistry registry, DataStore store) {
        this.registry = registry;
        this.store = store;
    }

    public void tick() {
        if (!registry.hasParticles()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = store.get(player);
            if (data.equipped == null || data.equipped.isEmpty()) {
                continue;
            }
            Title title = registry.get(data.equipped);
            if (title == null || !title.enabled || !data.ownedValid(title.id) || title.particleSpec == null) {
                continue;
            }
            spawn(player, title.particleSpec);
        }
    }

    public void spawn(Player player, Title title) {
        if (title == null || title.particleSpec == null) {
            return;
        }
        spawn(player, title.particleSpec);
    }

    public void spawn(Player player, ParticleSpec spec) {
        Location location = player.getLocation().add(0, 1.9d, 0);
        double spread = 0.35d;
        try {
            if (spec.extra() != null) {
                player.getWorld().spawnParticle(spec.particle(), location, spec.count(),
                        spread, 0.2d, spread, 0.02d, spec.extra());
            } else {
                player.getWorld().spawnParticle(spec.particle(), location, spec.count(),
                        spread, 0.2d, spread, 0.02d);
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // 需要额外数据的粒子（BLOCK / ITEM 等）在不传 data 时会抛异常，直接跳过这一帧
        }
    }
}
