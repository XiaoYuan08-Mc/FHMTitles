package cn.fhm.titles.storage;

import cn.fhm.titles.model.PlayerData;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存 + 落盘调度，玩家离线后数据仍留在缓存里，减少数据库压力。
 */
public final class DataStore {

    private final Database database;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public DataStore(Database database) {
        this.database = database;
    }

    public void loadAll() throws SQLException {
        cache.clear();
        cache.putAll(database.loadAll());
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    public PlayerData get(Player player) {
        PlayerData data = get(player.getUniqueId());
        data.name = player.getName();
        return data;
    }

    public PlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerData> all() {
        return cache.values();
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        // 传快照，别让 DB 线程读主线程正在改的集合
        database.saveAsync(data.copy());
        dirty.remove(uuid);
    }

    public void saveSync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            database.saveNow(data.copy());
        }
        dirty.remove(uuid);
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            database.saveAsync(data.copy());
        }
        dirty.clear();
    }

    public void saveDirtySync() {
        for (UUID uuid : dirty) {
            PlayerData data = cache.get(uuid);
            if (data != null) {
                database.saveNow(data.copy());
            }
        }
        dirty.clear();
    }

    public Set<UUID> dirtySet() {
        return dirty;
    }

    public Database database() {
        return database;
    }
}
