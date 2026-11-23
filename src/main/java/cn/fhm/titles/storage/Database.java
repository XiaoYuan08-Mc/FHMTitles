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
 * SQLite / MySQL 统一访问层。所有 SQL 只在单线程执行器上跑，避免连接被并发使用。
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

    public void init() throws Exception {
        mysql = "mysql".equalsIgnoreCase(settings.storageType);
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/"
                    + settings.mysqlDatabase
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
            connection = DriverManager.getConnection(url, settings.mysqlUser, settings.mysqlPassword);
            table = settings.tablePrefix + "players";
        } else {
            Class.forName("org.sqlite.JDBC");
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("无法创建插件数据目录");
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "titles.db").getAbsolutePath());
            table = "players";
        }
        createTable();
        plugin.getLogger().info("存储已连接: " + (mysql ? "MySQL" : "SQLite"));
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

    public Map<UUID, PlayerData> loadAll() throws SQLException {
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
        try {
            executor.execute(() -> saveNow(data));
        } catch (RuntimeException ignored) {
            // 执行器已关闭
        }
    }

    public synchronized void saveNow(PlayerData data) {
        if (connection == null) {
            return;
        }
        String sql;
        if (mysql) {
            sql = "INSERT INTO `" + table + "` (`uuid`,`name`,`equipped`,`owned`,`coins`,`flags`,`purchased`,`claimed`,`updated`)"
                    + " VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                    + " `name`=VALUES(`name`),`equipped`=VALUES(`equipped`),`owned`=VALUES(`owned`),"
                    + "`coins`=VALUES(`coins`),`flags`=VALUES(`flags`),`purchased`=VALUES(`purchased`),"
                    + "`claimed`=VALUES(`claimed`),`updated`=VALUES(`updated`)";
        } else {
            sql = "INSERT INTO `" + table + "` (`uuid`,`name`,`equipped`,`owned`,`coins`,`flags`,`purchased`,`claimed`,`updated`)"
                    + " VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(`uuid`) DO UPDATE SET"
                    + " `name`=excluded.`name`,`equipped`=excluded.`equipped`,`owned`=excluded.`owned`,"
                    + "`coins`=excluded.`coins`,`flags`=excluded.`flags`,`purchased`=excluded.`purchased`,"
                    + "`claimed`=excluded.`claimed`,`updated`=excluded.`updated`";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.uuid.toString());
            ps.setString(2, data.name == null ? "" : data.name);
            ps.setString(3, data.equipped == null ? "" : data.equipped);
            ps.setString(4, data.ownedSerialize());
            ps.setLong(5, data.coins);
            ps.setString(6, data.flagsSerialize());
            ps.setString(7, PlayerData.setSerialize(data.purchased));
            ps.setString(8, PlayerData.setSerialize(data.claimed));
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            // 以前只 catch SQLException，序列化抛的运行时异常会让整次存档静默消失
            plugin.getLogger().warning("保存玩家称号数据失败 (" + data.uuid + "): " + e);
        }
    }

    public void backupAsync() {
        try {
            executor.execute(this::backupNow);
        } catch (RuntimeException ignored) {
            // 执行器已关闭
        }
    }

    private void backupNow() {
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

    @Override
    public void close() {
        executor.shutdown();
        boolean finished = false;
        try {
            finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!finished) {
            plugin.getLogger().warning("关服时仍有未写完的存档任务，最近几秒的称号数据可能丢失");
        }
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
