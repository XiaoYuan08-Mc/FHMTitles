package cn.fhm.titles.core;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责聊天 / TAB / 头顶三处渲染与 RGB 动画。
 * 所有 Bukkit 调用都在主线程，聊天渲染器只读缓存，避免异步线程访问实体。
 */
public final class DisplayService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;

    private final Map<UUID, Render> cache = new ConcurrentHashMap<>();
    private final Map<UUID, TextDisplay> heads = new ConcurrentHashMap<>();
    /** 动画称号的纯文本缓存：每 tick 都要用，别反复 deserialize */
    private final Map<String, PlainCache> plainCache = new ConcurrentHashMap<>();
    private cn.fhm.titles.resource.ImageTitleService images;
    private cn.fhm.titles.hook.PapiHook papi;
    private double phase = 0;

    private record PlainCache(String raw, String plain) {
    }

    public DisplayService(JavaPlugin plugin, Settings settings, Lang lang, TitleRegistry registry, DataStore store) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.registry = registry;
        this.store = store;
    }

    public record Render(Component prefix, Component suffix, Component tab, Component head, String titleId) {
        public static final Render EMPTY = new Render(Component.empty(), Component.empty(),
                Component.empty(), Component.empty(), "");
    }

    public void setImages(cn.fhm.titles.resource.ImageTitleService images) {
        this.images = images;
    }

    public void setPapi(cn.fhm.titles.hook.PapiHook papi) {
        this.papi = papi;
    }

    /** 解析称号文本 / 后缀里的 %papi_xxx% 占位符（未装 PlaceholderAPI 时原样返回）。 */
    private String resolve(Player player, String raw) {
        if (papi == null || player == null || raw == null || raw.isEmpty()) {
            return raw;
        }
        return papi.apply(player, raw);
    }

    public Title equippedTitle(PlayerData data) {
        if (data.equipped == null || data.equipped.isEmpty()) {
            return null;
        }
        Title title = registry.get(data.equipped);
        if (title == null || !title.enabled || !data.ownedValid(title.id)) {
            return null;
        }
        return title;
    }

    public Render render(Player player) {
        PlayerData data = store.get(player);
        Title title = equippedTitle(data);
        if (title == null) {
            return Render.EMPTY;
        }
        return render(title, player);
    }

    public Render render(Title title, Player player) {
        Component titleComponent = titleComponent(title, player);
        Component suffix = title.suffix == null || title.suffix.isEmpty()
                ? Component.empty() : TextUtil.parse(resolve(player, title.suffix));
        Map<String, Component> tokens = new HashMap<>();
        tokens.put("prefix", titleComponent);
        tokens.put("title", titleComponent);
        tokens.put("suffix", suffix);
        tokens.put("player", Component.text(player == null ? "" : player.getName()));

        Component tab = TextUtil.compose(settings.tabFormat, tokens);
        Component head = TextUtil.compose(settings.headFormat, tokens);
        return new Render(titleComponent, suffix, tab, head, title.id);
    }

    public Component titleComponent(Title title) {
        return titleComponent(title, null);
    }

    /**
     * 图片称号：客户端已加载资源包时输出自定义字体字形，否则回退成文字。
     * player 为 null 表示不检测资源包状态（用于预览）。
     */
    public Component titleComponent(Title title, Player player) {
        Component text = textComponent(title, player);
        if (images == null || !images.hasImage(title)) {
            return text;
        }
        Component image;
        if (images.renderable(title, player)) {
            image = images.glyphComponent(title);
        } else {
            image = images.fallback(title);
        }
        if (image.equals(Component.empty())) {
            return text;
        }
        // 分隔空格必须显式用原版字体：自定义字体里没有空格字形，否则会渲染成缺字方块
        Component gap = Component.text(" ").style(Style.style().font(Key.key("minecraft", "default")));
        if (text.equals(Component.empty())) {
            return image.append(gap);
        }
        return image.append(gap).append(text);
    }

    private Component textComponent(Title title, Player player) {
        String raw = resolve(player, title.text);
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (!title.animated) {
            return TextUtil.parse(raw);
        }
        List<String> palette = title.palette == null || title.palette.isEmpty() ? settings.defaultPalette : title.palette;
        return TextUtil.parse(TextUtil.animatedGradient(plainText(title, raw), palette, phase));
    }

    /**
     * 动画称号每 rgb-interval-ticks 就要重新染色，原文不变时没必要每次再做一次
     * MiniMessage 反序列化 + 纯文本化。
     */
    private String plainText(Title title, String raw) {
        PlainCache cached = plainCache.get(title.id);
        if (cached != null && cached.raw().equals(raw)) {
            return cached.plain();
        }
        String plain = TextUtil.plain(TextUtil.parse(raw));
        plainCache.put(title.id, new PlainCache(raw, plain));
        return plain;
    }

    public Component prefix(Player player) {
        Render render = cache.get(player.getUniqueId());
        return render == null ? Component.empty() : render.prefix();
    }

    public Component suffix(Player player) {
        Render render = cache.get(player.getUniqueId());
        return render == null ? Component.empty() : render.suffix();
    }

    public Render cached(Player player) {
        return cache.getOrDefault(player.getUniqueId(), Render.EMPTY);
    }

    /** 重建某玩家的三处显示（主线程调用）。 */
    public void refresh(Player player) {
        PlayerData data = store.get(player);
        Title title = equippedTitle(data);
        Render render = title == null ? Render.EMPTY : render(title, player);
        cache.put(player.getUniqueId(), render);

        if (title == null || !settings.tabEnabled || !data.showTab) {
            player.playerListName(null);
        } else {
            player.playerListName(render.tab());
        }

        if (title == null || !settings.headEnabled || !data.showHead) {
            removeHead(player);
        } else {
            TextDisplay display = heads.get(player.getUniqueId());
            if (display == null || !display.isValid()) {
                display = spawnHead(player);
            }
            if (display != null) {
                display.text(render.head());
            }
        }
    }

    public void refreshAll() {
        plainCache.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private TextDisplay spawnHead(Player player) {
        try {
            Location location = player.getLocation();
            TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setShadowed(false);
                entity.setSeeThrough(true);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setViewRange((float) settings.headViewDistance);
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, (float) settings.headYOffset, 0f),
                        new Quaternionf(),
                        new Vector3f((float) settings.headScale, (float) settings.headScale, (float) settings.headScale),
                        new Quaternionf()));
            });
            player.addPassenger(display);
            heads.put(player.getUniqueId(), display);
            return display;
        } catch (Exception e) {
            plugin.getLogger().warning("创建头顶称号失败: " + e.getMessage());
            return null;
        }
    }

    public void removeHead(Player player) {
        TextDisplay display = heads.remove(player.getUniqueId());
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public void clearAll() {
        for (TextDisplay display : heads.values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        heads.clear();
        cache.clear();
    }

    /** RGB 动画推进，每 rgbIntervalTicks 执行一次。 */
    public void tickAnimation() {
        phase += 0.02d;
        if (phase >= 1.0d) {
            phase -= 1.0d;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = store.get(player);
            Title title = equippedTitle(data);
            if (title == null) {
                continue;
            }
            TextDisplay display = heads.get(player.getUniqueId());
            // 头顶实体被别的插件 / 指令清掉后没人重建，这里兜一下底
            if (display != null && !display.isValid()) {
                heads.remove(player.getUniqueId());
                refresh(player);
                continue;
            }
            if (!title.animated) {
                continue;
            }
            Render render = render(title, player);
            cache.put(player.getUniqueId(), render);
            if (display != null) {
                display.text(render.head());
            }
        }
    }

    public void onQuit(Player player) {
        removeHead(player);
        cache.remove(player.getUniqueId());
    }
}

