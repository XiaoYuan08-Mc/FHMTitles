package cn.fhm.titles.resource;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.util.TextUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 图片称号：把 PNG 打成资源包里的自定义 bitmap 字体字形（Unicode 私用区 U+E000 起），
 * 再由组件用 Style#font 指定该字体渲染，聊天 / TAB / 头顶都能显示成图片。
 *
 * 资源包结构:
 *   pack.mcmeta
 *   assets/fhmtitles/font/titles.json          -> 每个称号一个 bitmap provider
 *   assets/fhmtitles/textures/title/<id>.png   -> 原始图片
 *
 * 分发方式：插件内置 HTTP 服务托管 zip（也可在 config 里填自己的外链）。
 */
public final class ImageTitleService {

    public static final String FONT_NAMESPACE = "fhmtitles";
    public static final String FONT_NAME = "titles";
    public static final Key FONT_KEY = Key.key(FONT_NAMESPACE + ":" + FONT_NAME);

    private final JavaPlugin plugin;
    private final Settings settings;
    private final TitleRegistry registry;

    private final File imagesDir;
    private final File packDir;
    private final File zipFile;
    private final File glyphFile;

    /** 称号ID -> 分配到的码位列表 */
    private final Map<String, List<Integer>> glyphs = new LinkedHashMap<>();
    private final Map<UUID, Boolean> packLoaded = new ConcurrentHashMap<>();

    private volatile byte[] packBytes = new byte[0];
    private volatile String packHash = "";
    private volatile String packUrl = "";
    private volatile long builtAt = 0L;
    private HttpServer http;

    public ImageTitleService(JavaPlugin plugin, Settings settings, TitleRegistry registry) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        File data = plugin.getDataFolder();
        this.imagesDir = new File(data, "images");
        this.packDir = new File(data, "resourcepack");
        this.zipFile = new File(data, "resourcepack.zip");
        this.glyphFile = new File(data, "images/glyphs.yml");
    }

    // ---------------------------------------------------------------- 生命周期

    public void start() {
        if (!imagesDir.isDirectory()) {
            imagesDir.mkdirs();
        }
        saveDemoImage();
        loadGlyphs();
        if (settings.packEnabled) {
            if (settings.packBuildOnStart) {
                build();
            } else {
                if (zipFile.isFile()) {
                    try {
                        packBytes = Files.readAllBytes(zipFile.toPath());
                        packHash = sha1(packBytes);
                        packUrl = resolveUrl();
                        builtAt = zipFile.lastModified();
                    } catch (IOException e) {
                        plugin.getLogger().warning("读取已有资源包失败: " + e.getMessage());
                    }
                }
            }
            startHttp();
        }
        if (!packUrl.isEmpty()) {
            plugin.getLogger().info("图片称号资源包地址: " + packUrl);
        }
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    /** /titles reload 时按新配置重建资源包并重启 HTTP 服务。 */
    public void reload() {
        stop();
        if (!settings.packEnabled) {
            return;
        }
        build();
        startHttp();
    }

    /** 玩家进服时下发资源包。 */
    public void onJoin(Player player) {
        packLoaded.put(player.getUniqueId(), Boolean.FALSE);
        if (settings.packEnabled && settings.packSendOnJoin) {
            send(player);
        }
    }

    public void onQuit(Player player) {
        packLoaded.remove(player.getUniqueId());
    }

    /** 资源包状态回调。 */
    public void onStatus(Player player, String status) {
        boolean loaded = "SUCCESSFULLY_LOADED".equals(status);
        boolean failed = "DECLINED".equals(status) || "FAILED_DOWNLOAD".equals(status)
                || "FAILED_RELOAD".equals(status) || "INVALID_URL".equals(status) || "DISCARDED".equals(status);
        if (loaded) {
            packLoaded.put(player.getUniqueId(), Boolean.TRUE);
        } else if (failed) {
            packLoaded.put(player.getUniqueId(), Boolean.FALSE);
        } else {
            return;
        }
        // 资源包加载完成后刷新一次显示，让图片称号从回退文字切成图片
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin instanceof cn.fhm.titles.FHMTitlesPlugin main) {
                main.display().refresh(player);
            }
        });
    }

    public boolean packLoaded(Player player) {
        if (player == null) {
            return true;
        }
        return Boolean.TRUE.equals(packLoaded.get(player.getUniqueId()));
    }

    // ---------------------------------------------------------------- 渲染

    public boolean hasImage(Title title) {
        return title != null && title.imageFile != null && !title.imageFile.isEmpty();
    }

    /** 该称号的图片是否可用（有图 + 已分配字形 + 客户端已加载资源包）。 */
    public boolean renderable(Title title, Player player) {
        return hasImage(title) && glyphs.containsKey(title.id) && packLoaded(player);
    }

    /** 图片字形组件（使用自定义字体）。 */
    public Component glyphComponent(Title title) {
        List<Integer> codes = glyphs.get(title.id);
        if (codes == null || codes.isEmpty()) {
            return Component.empty();
        }
        StringBuilder sb = new StringBuilder(codes.size());
        for (int code : codes) {
            sb.appendCodePoint(code);
        }
        return Component.text(sb.toString())
                .style(Style.style().font(FONT_KEY).decoration(TextDecoration.ITALIC, false));
    }

    /** 客户端没加载资源包时的回退文本。 */
    public Component fallback(Title title) {
        if (!settings.imageFallbackEnabled) {
            return Component.empty();
        }
        if (title.imageFallback != null && !title.imageFallback.isEmpty()) {
            return TextUtil.parse(title.imageFallback);
        }
        if (title.text != null && !title.text.isEmpty()) {
            return TextUtil.parse(title.text);
        }
        return TextUtil.parse(settings.imageFallbackText.replace("%title%", title.id));
    }

    // ---------------------------------------------------------------- 资源包构建

    public record BuildResult(int counts, int bytes, String hash, String url, List<String> problems) {
    }

    /** 重建资源包 + zip + hash，并刷新访问地址。调用方需保证在主线程。 */
    public BuildResult build() {
        List<String> problems = new ArrayList<>();
        Map<String, BufferedImage> prepared = new LinkedHashMap<>();

        // 1. 收集图片 + 校验尺寸
        for (Title title : registry.all()) {
            if (!hasImage(title) || !title.enabled) {
                continue;
            }
            File file = new File(imagesDir, title.imageFile);
            if (!file.isFile()) {
                problems.add(title.id + ": 找不到图片 images/" + title.imageFile);
                continue;
            }
            BufferedImage image;
            try {
                image = ImageIO.read(file);
            } catch (IOException e) {
                problems.add(title.id + ": 读取图片失败 " + e.getMessage());
                continue;
            }
            if (image == null) {
                problems.add(title.id + ": 不是有效的图片文件 " + title.imageFile);
                continue;
            }
            int glyphCount = Math.max(1, title.imageGlyphs);
            if (image.getWidth() % glyphCount != 0) {
                problems.add(title.id + ": 图片宽度 " + image.getWidth() + " 不能被字形数 "
                        + glyphCount + " 整除");
                continue;
            }
            int cell = image.getWidth() / glyphCount;
            if (cell > 0 && image.getHeight() / (double) cell > 4.0d) {
                problems.add(title.id + ": 图片太窄太高，单字符高宽比建议 ≤ 4");
            }
            prepared.put(title.id, image);
        }

        // 2. 分配字形码位
        glyphs.keySet().retainAll(prepared.keySet());
        int next = settings.glyphCharStart;
        for (List<Integer> codes : glyphs.values()) {
            for (int code : codes) {
                next = Math.max(next, code + 1);
            }
        }
        for (Map.Entry<String, BufferedImage> entry : prepared.entrySet()) {
            Title title = registry.get(entry.getKey());
            int need = Math.max(1, title.imageGlyphs);
            List<Integer> codes = glyphs.get(title.id);
            if (codes == null || codes.size() != need) {
                codes = new ArrayList<>(need);
                for (int i = 0; i < need; i++) {
                    codes.add(next++);
                }
                glyphs.put(title.id, codes);
            }
        }
        saveGlyphs();

        // 3. 落盘资源包目录
        deleteTree(packDir);
        File assets = new File(packDir, "assets/" + FONT_NAMESPACE);
        File fontDir = new File(assets, "font");
        File textureDir = new File(assets, "textures/title");
        fontDir.mkdirs();
        textureDir.mkdirs();

        StringBuilder providers = new StringBuilder();
        for (Map.Entry<String, BufferedImage> entry : prepared.entrySet()) {
            Title title = registry.get(entry.getKey());
            File target = new File(textureDir, title.id + ".png");
            try {
                ImageIO.write(entry.getValue(), "png", target);
            } catch (IOException e) {
                problems.add(title.id + ": 导出贴图失败 " + e.getMessage());
                continue;
            }
            if (providers.length() > 0) {
                providers.append(",\n");
            }
            providers.append("  {\"type\":\"bitmap\",\"file\":\"").append(FONT_NAMESPACE)
                    .append(":title/").append(title.id).append(".png\"")
                    .append(",\"ascent\":").append(title.imageAscent)
                    .append(",\"height\":").append(title.imageHeight)
                    .append(",\"chars\":[\"").append(escapeCodes(glyphs.get(title.id))).append("\"]}");
        }
        String fontJson = "{\"providers\":[\n" + providers + "\n]}\n";
        write(new File(fontDir, FONT_NAME + ".json"), fontJson);

        String meta = "{\n  \"pack\": {\n    \"description\": \"FHMTitles 图片称号资源包\",\n"
                + "    \"min_format\": " + settings.packMinFormat + ",\n"
                + "    \"max_format\": " + settings.packMaxFormat + "\n  }\n}\n";
        write(new File(packDir, "pack.mcmeta"), meta);

        // 4. 打包（固定时间戳，保证同内容同 hash，客户端可走缓存）
        byte[] zip;
        try {
            zip = zipDirectory(packDir);
        } catch (IOException e) {
            problems.add("打包失败: " + e.getMessage());
            zip = new byte[0];
        }
        try {
            Files.write(zipFile.toPath(), zip);
        } catch (IOException e) {
            problems.add("写入 resourcepack.zip 失败: " + e.getMessage());
        }

        packBytes = zip;
        packHash = sha1(zip);
        packUrl = resolveUrl();
        builtAt = System.currentTimeMillis();

        for (String problem : problems) {
            plugin.getLogger().warning("[图片称号] " + problem);
        }
        plugin.getLogger().info("资源包已生成: " + prepared.size() + " 张图片, "
                + zip.length + " 字节, sha1=" + packHash);

        // 5. 让已经在线的玩家重拉新包（hash 变了客户端才会重新下载）
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (settings.packEnabled && settings.packSendOnJoin) {
                send(player);
            }
        }
        return new BuildResult(prepared.size(), zip.length, packHash, packUrl, problems);
    }

    /** 手动把资源包推给某个玩家 / 所有人。 */
    public void send(Player player) {
        if (packBytes.length == 0 || packUrl.isEmpty() || packHash.isEmpty()) {
            return;
        }
        try {
            player.setResourcePack(packUrl, packHash, settings.packRequired, TextUtil.parse(settings.packPrompt));
        } catch (Throwable t) {
            plugin.getLogger().warning("下发资源包失败(" + player.getName() + "): " + t.getMessage());
        }
    }

    private String resolveUrl() {
        String custom = settings.packPublicUrl == null ? "" : settings.packPublicUrl.trim();
        if (!custom.isEmpty()) {
            return custom;
        }
        if (settings.packPort <= 0) {
            return "";
        }
        String host = Bukkit.getIp();
        if (host == null || host.isBlank() || "0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + settings.packPort + "/pack.zip";
    }

    private void startHttp() {
        if (settings.packPort <= 0 || !settings.packEnabled) {
            return;
        }
        try {
            http = HttpServer.create(new InetSocketAddress(settings.packPort), 0);
            http.createContext("/pack.zip", this::servePack);
            http.createContext("/", exchange -> {
                byte[] body = "FHMTitles resource pack".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            http.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "FHMTitles-Pack-HTTP");
                thread.setDaemon(true);
                return thread;
            }));
            http.start();
            plugin.getLogger().info("资源包 HTTP 服务已启动，端口 " + settings.packPort);
        } catch (IOException e) {
            plugin.getLogger().warning("资源包 HTTP 服务启动失败（端口 " + settings.packPort + " 可能被占用）: "
                    + e.getMessage());
            http = null;
        }
    }

    private void servePack(HttpExchange exchange) throws IOException {
        byte[] body = packBytes;
        if (body.length == 0) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.getResponseHeaders().add("Cache-Control", "public, max-age=300");
        exchange.getResponseHeaders().add("ETag", "\"" + packHash + "\"");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public String packHash() {
        return packHash;
    }

    public String packUrl() {
        return packUrl;
    }

    public int packSize() {
        return packBytes.length;
    }

    public long builtAt() {
        return builtAt;
    }

    public int imageCount() {
        return glyphs.size();
    }

    public java.util.Set<String> imageTitleIds() {
        return java.util.Collections.unmodifiableSet(glyphs.keySet());
    }

    /** 给某个称号指定图片文件并立即重建资源包。 */
    public String assignImage(String titleId, String file, int glyphCount, int height, int ascent) {
        Title title = registry.get(titleId);
        if (title == null) {
            return "找不到称号 " + titleId;
        }
        File image = new File(imagesDir, file);
        if (!image.isFile()) {
            return "images/" + file + " 不存在";
        }
        title.imageFile = file;
        title.imageGlyphs = Math.max(1, glyphCount);
        title.imageHeight = height;
        title.imageAscent = ascent;
        if (!title.tags.contains(cn.fhm.titles.model.Category.IMAGE)) {
            title.tags.add(cn.fhm.titles.model.Category.IMAGE);
        }
        registry.save();
        build();
        return null;
    }

    // ---------------------------------------------------------------- 字形表持久化

    private void loadGlyphs() {
        glyphs.clear();
        if (!glyphFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(glyphFile);
        for (String key : yaml.getKeys(false)) {
            List<Integer> codes = yaml.getIntegerList(key);
            if (!codes.isEmpty()) {
                glyphs.put(key.toLowerCase(Locale.ROOT), codes);
            }
        }
    }

    private void saveGlyphs() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, List<Integer>> entry : glyphs.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        try {
            File parent = glyphFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            yaml.save(glyphFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存字形表失败: " + e.getMessage());
        }
    }

    private String escapeCodes(List<Integer> codes) {
        StringBuilder sb = new StringBuilder();
        if (codes == null) {
            return "";
        }
        for (int code : codes) {
            // JSON 里字形必须写成反斜杠加 u 的四位十六进制转义
            sb.append(String.format("\\u%04X", code));
        }
        return sb.toString();
    }

    private void saveDemoImage() {
        File demo = new File(imagesDir, "glyph.png");
        if (demo.isFile()) {
            return;
        }
        try (InputStream in = plugin.getResource("images/glyph.png")) {
            if (in != null) {
                Files.copy(in, demo.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("释放示例图片失败: " + e.getMessage());
        }
    }

    private void write(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("写入 " + file.getName() + " 失败: " + e.getMessage());
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static byte[] zipDirectory(File root) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        List<File> files = new ArrayList<>();
        collect(root, files);
        files.sort(Comparator.comparing(File::getPath));
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.setLevel(9);
            for (File file : files) {
                String name = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                out.putNextEntry(entry);
                out.write(Files.readAllBytes(file.toPath()));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else {
                out.add(child);
            }
        }
    }

    private static String sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public File imagesDir() {
        return imagesDir;
    }

    public Map<String, List<Integer>> glyphMap() {
        return java.util.Collections.unmodifiableMap(new HashMap<>(glyphs));
    }
}
