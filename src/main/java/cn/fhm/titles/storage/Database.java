package cn.fhm.titles.storage;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.model.PlayerData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite / MySQL 统一访问层。所有 SQL 只在这条单线程执行器上跑，避免连接被并发使用。
 *
 * 1.2.0 的改动：
 * - 单连接的所有访问都串行化在同一把锁上，主线程跟异步写盘不会再抢同一个 Connection；
 * - 写失败（MySQL 掉线、SQLite 文件被占用）会重建连接再重试一次，而不是从此静默失败；
 * - 新增批量 upsert，合并写盘时一次往返写完一批玩家。
 */
public final class Database implements AutoCloseable {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FHMTitles-DB");
        t.setDaemon(true);
        return t;
    });

    private Connection connection;
    private boolean mysql;
    private String table;

    public Database(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public synchronized void init() throws Exception {
        mysql = "mysql".equalsIgnoreCase(settings.storageType);
        table = mysql ? settings.tablePrefix + "players" : "players";
        connection = open();
        createTable();
        plugin.getLogger().info("存储已连接: " + (mysql ? "MySQL" : "SQLite"));
    }

    private Connection open() throws Exception {
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/"
                    + settings.mysqlDatabase
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&autoReconnect=true&tcpKeepAlive=true"
                    + "&rewriteBatchedStatements=true";
            return DriverManager.getConnection(url, settings.mysqlUser, settings.mysqlPassword);
        }
        Class.forName("org.sqlite.JDBC");
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("无法创建插件数据目录");
        }
        return DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "titles.db").getAbsolutePath());
    }

    private void createTable() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`uuid` VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "`name` VARCHAR(32) NOT NULL DEFAULT '',"
                + "`equipped` VARCHAR(64) NOT NULL DEFAULT '',"
                + "`owned` TEXT,"
                + "`coins` BIGINT NOT NULL DEFAULT 0,"
                + "`flags` VARCHAR(16) NOT NULL DEFAULT '1|1|1',"
                + "`purchased` TEXT,"
                + "`claimed` TEXT,"
                + "`updated` BIGINT NOT NULL DEFAULT 0)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private String upsertSql() {
        if (mysql) {
            return "INSERT INTO `" + table + "` (`uuid`,`name`,`equipped`,`owned`,`coins`,`flags`,`purchased`,`claimed`,`updated`)"
                    + " VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                    + " `name`=VALUES(`name`),`equipped`=VALUES(`equipped`),`owned`=VALUES(`owned`),"
                    + "`coins`=VALUES(`coins`),`flags`=VALUES(`flags`),`purchased`=VALUES(`purchased`),"
                    + "`claimed`=VALUES(`claimed`),`updated`=VALUES(`updated`)";
        }
        return "INSERT INTO `" + table + "` (`uuid`,`name`,`equipped`,`owned`,`coins`,`flags`,`purchased`,`claimed`,`updated`)"
                + " VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(`uuid`) DO UPDATE SET"
                + " `name`=excluded.`name`,`equipped`=excluded.`equipped`,`owned`=excluded.`owned`,"
                + "`coins`=excluded.`coins`,`flags`=excluded.`flags`,`purchased`=excluded.`purchased`,"
                + "`claimed`=excluded.`claimed`,`updated`=excluded.`updated`";
    }

    public synchronized Map<UUID, PlayerData> loadAll() throws SQLException {
        Map<UUID, PlayerData> out = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM `" + table + "`")) {
            while (rs.next()) {
                try {
                    PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
                    data.name = rs.getString("name");
                    data.equipped = rs.getString("equipped");
                    data.ownedDeserialize(rs.getString("owned"));
                    data.coins = rs.getLong("coins");
                    data.flagsDeserialize(rs.getString("flags"));
                    data.purchased.addAll(PlayerData.setDeserialize(rs.getString("purchased")));
                    data.claimed.addAll(PlayerData.setDeserialize(rs.getString("claimed")));
                    out.put(data.uuid, data);
                } catch (IllegalArgumentException ignored) {
                    // 非法 UUID 数据跳过
                }
            }
        }
        return out;
    }

    public void saveAsync(PlayerData data) {
        saveAsync(data, null);
    }

    public void saveAsync(PlayerData data, java.util.function.Consumer<Boolean> result) {
        boolean submitted = submit(() -> {
            boolean ok = saveNow(data);
            if (result != null) {
                result.accept(ok);
            }
        });
        // 执行器已关闭（正在关服）时任务会被拒绝，必须把结果回调成失败，
        // 让上层把 UUID 重新标脏，交给 onDisable 的同步落盘收尾
        if (!submitted && result != null) {
            result.accept(false);
        }
    }

    public void saveBatchAsync(List<PlayerData> rows) {
        saveBatchAsync(rows, null);
    }

    public void saveBatchAsync(List<PlayerData> rows, java.util.function.Consumer<Boolean> result) {
        if (rows == null || rows.isEmpty()) {
            if (result != null) {
                result.accept(true);
            }
            return;
        }
        List<PlayerData> snapshot = List.copyOf(rows);
        boolean submitted = submit(() -> {
            boolean ok = saveBatchNow(snapshot);
            if (result != null) {
                result.accept(ok);
            }
        });
        if (!submitted && result != null) {
            result.accept(false);
        }
    }

    private boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public synchronized boolean saveNow(PlayerData data) {
        if (connection == null || data == null) {
            return false;
        }
        try {
            writeOne(data);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家称号数据失败 (" + data.uuid + "): " + e);
            // MySQL 掉线是最常见的原因：重建连接后重试一次，别让这行数据就这么没了
            if (reconnect()) {
                try {
                    writeOne(data);
                    return true;
                } catch (Exception retry) {
                    plugin.getLogger().warning("重连后仍然保存失败 (" + data.uuid + "): " + retry);
                }
            }
            return false;
        }
    }

    private synchronized boolean saveBatchNow(List<PlayerData> rows) {
        if (connection == null || rows.isEmpty()) {
            return false;
        }
        try {
            writeBatch(rows);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("批量保存称号数据失败 (" + rows.size() + " 行): " + e);
            if (reconnect()) {
                try {
                    writeBatch(rows);
                    return true;
                } catch (Exception retry) {
                    plugin.getLogger().warning("重连后批量保存仍然失败 (" + rows.size() + " 行): " + retry);
                }
            }
            return false;
        }
    }

    private void writeOne(PlayerData data) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(upsertSql())) {
            bind(ps, data);
            ps.executeUpdate();
        }
    }

    private void writeBatch(List<PlayerData> rows) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(upsertSql())) {
            for (PlayerData data : rows) {
                bind(ps, data);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void bind(PreparedStatement ps, PlayerData data) throws SQLException {
        ps.setString(1, data.uuid.toString());
        ps.setString(2, data.name == null ? "" : data.name);
        ps.setString(3, data.equipped == null ? "" : data.equipped);
        ps.setString(4, data.ownedSerialize());
        ps.setLong(5, data.coins);
        ps.setString(6, data.flagsSerialize());
        ps.setString(7, PlayerData.setSerialize(data.purchased));
        ps.setString(8, PlayerData.setSerialize(data.claimed));
        ps.setLong(9, System.currentTimeMillis());
    }

    /** 连接坏了就重开一条；调用方已经持有数据库锁。 */
    private boolean reconnect() {
        try {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 已经断了的连接关不掉也无所谓
                }
            }
            connection = open();
            createTable();
            plugin.getLogger().info("存储连接已重建: " + (mysql ? "MySQL" : "SQLite"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("存储连接重建失败: " + e.getMessage());
            connection = null;
            return false;
        }
    }

    public void backupAsync() {
        if (!submit(this::backupNow)) {
            plugin.getLogger().warning("备份任务未能提交：数据库执行器已关闭");
        }
    }

    private synchronized void backupNow() {
        if (connection == null) {
            return;
        }
        File folder = new File(plugin.getDataFolder(), "backUp");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建备份目录");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File out = new File(folder, "titles-" + stamp + ".sql");
        try (Writer writer = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            writer.write("-- FHMTitles backup " + stamp + "\n");
            writer.write("-- table: " + table + "\n\n");
            int count = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                while (rs.next()) {
                    writer.write(insertStatement(rs));
                    writer.write("\n");
                    count++;
                }
            }
            writer.write("-- rows: " + count + "\n");
            plugin.getLogger().info("称号数据已备份: " + out.getName() + " (" + count + " 行)");
        } catch (Exception e) {
            plugin.getLogger().warning("备份失败: " + e.getMessage());
            return;
        }
        pruneBackups(folder);
    }

    private String insertStatement(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder("INSERT INTO `").append(table)
                .append("` (`uuid`,`name`,`equipped`,`owned`,`coins`,`flags`,`purchased`,`claimed`) VALUES (");
        sb.append(quote(rs.getString("uuid"))).append(',');
        sb.append(quote(rs.getString("name"))).append(',');
        sb.append(quote(rs.getString("equipped"))).append(',');
        sb.append(quote(rs.getString("owned"))).append(',');
        sb.append(rs.getLong("coins")).append(',');
        sb.append(quote(rs.getString("flags"))).append(',');
        sb.append(quote(rs.getString("purchased"))).append(',');
        sb.append(quote(rs.getString("claimed"))).append(");");
        return sb.toString();
    }

    private String quote(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private void pruneBackups(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.startsWith("titles-") && name.endsWith(".sql"));
        if (files == null) {
            return;
        }
        List<File> list = new ArrayList<>();
        for (File file : files) {
            list.add(file);
        }
        list.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = settings.backupKeep; i < list.size(); i++) {
            if (!list.get(i).delete()) {
                plugin.getLogger().warning("无法删除旧备份: " + list.get(i).getName());
            }
        }
    }

    /**
     * 停止接收新的写盘任务，并把队列里已经排着的任务跑完。
     * 关服时必须先调它、再落盘最后一批脏数据，否则排队中的旧快照可能覆盖新写入。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("关服时仍有未写完的存档任务，最近几秒的称号数据可能丢失");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
        // 先等执行器收尾再拿锁，免得执行器卡在锁上导致 await 超时
        synchronized (this) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {
                // 忽略关闭异常
            }
            connection = null;
        }
    }
}
