package cn.fhm.titles.storage;

import cn.fhm.titles.model.PlayerData;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存 + 落盘调度。
 *
 * 发放 / 回收 / 购买 / 到期 / 领奖 / 下线这类关键写入每次都会立即排队写盘；
 * storage.flush-seconds > 0 时，只有切换佩戴 / 显示开关这类高频改动会攒成脏 UUID，
 * 每 N 秒合成一次批量写入。设为 0 表示这类改动也逐次立即写（旧版行为）。
 */
public final class DataStore {

    private final Database database;
    private final int flushSeconds;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public DataStore(Database database, int flushSeconds) {
        this.database = database;
        this.flushSeconds = Math.max(0, flushSeconds);
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
        String name = player.getName();
        if (!name.equals(data.name)) {
            // 改名后离线按名字查找会找不到人，同时标脏让下一轮写盘更新
            data.name = name;
            markDirty(data.uuid);
        }
        return data;
    }

    public PlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerData> all() {
        return cache.values();
    }

    public void markDirty(UUID uuid) {
        if (uuid == null) {
            return;
        }
        // flush-seconds = 0 表示高频改动也逐次写盘，不能只标脏后没人收尾
        if (flushSeconds <= 0) {
            save(uuid);
            return;
        }
        dirty.add(uuid);
    }

    /**
     * 立即落盘。发放、回收、购买、到期这类改动丢了玩家会找上门，不能等合并写盘；
     * 只有切换佩戴 / 显示开关这类高频可重放的操作才走 markDirty + 定时批量。
     */
    public void save(UUID uuid) {
        if (uuid == null) {
            return;
        }
        dirty.remove(uuid);
        writeAsync(uuid);
    }

    /** 下线 / 关键操作要立刻落盘，走这个入口。 */
    public void flushPlayer(UUID uuid) {
        dirty.remove(uuid);
        writeAsync(uuid);
    }

    /** 定时批量落盘：主线程取快照，DB 线程一次 executBatch；失败的行回填成脏，下轮再试。 */
    public void flushDirty() {
        if (dirty.isEmpty() || cache.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(dirty.size());
        List<PlayerData> batch = new ArrayList<>(dirty.size());
        for (UUID uuid : dirty) {
            PlayerData data = cache.get(uuid);
            if (data != null) {
                ids.add(uuid);
                batch.add(data.copy());
            }
        }
        dirty.removeAll(ids);
        database.saveBatchAsync(batch, success -> {
            if (!success) {
                dirty.addAll(ids);
            }
        });
    }

    public void saveSync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            database.saveNow(data.copy());
        }
        dirty.remove(uuid);
    }

    public void saveAll() {
        List<PlayerData> batch = new ArrayList<>(cache.size());
        for (PlayerData data : cache.values()) {
            batch.add(data.copy());
        }
        dirty.clear();
        database.saveBatchAsync(batch);
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

    private void writeAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            // 传快照，别让 DB 线程读主线程正在改的集合；写失败则重新标脏，下一轮再试
            database.saveAsync(data.copy(), success -> {
                if (!success) {
                    dirty.add(uuid);
                }
            });
        }
    }
}
