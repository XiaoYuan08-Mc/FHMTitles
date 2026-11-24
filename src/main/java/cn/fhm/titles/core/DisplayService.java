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
import net.kyori.adventure.text.TextReplacementConfig;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责聊天 / TAB / 头顶三处渲染与 RGB 动画。
 * 所有 Bukkit 调用都在主线程，聊天渲染器只读缓存，避免异步线程访问实体。
 *
 * 动画的两层缓存：
 * - 每个称号按原文预生成 N 帧组件，动画周期只按 phase 取帧，不再每帧拼字符串 + MiniMessage 反序列化；
 * - 头顶格式只解析一次，把称号文字留成一个占位槽，每帧只替换槽里的组件。
 */
public final class DisplayService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;

    private final Map<UUID, Render> cache = new ConcurrentHashMap<>();
    private final Map<UUID, TextDisplay> heads = new ConcurrentHashMap<>();
    /** 只有正在佩戴动画称号的玩家才在这个表里，动画周期直接遍历它，没动画时整轮空转。 */
    private final Map<UUID, AnimState> anim = new ConcurrentHashMap<>();
    /** 动画帧缓存：称号 ID + 解析后的原文 + 帧数 + 调色板哈希。 */
    private final Map<String, FrameSet> frames = new ConcurrentHashMap<>();

    private cn.fhm.titles.resource.ImageTitleService images;
    private cn.fhm.titles.hook.PapiHook papi;
    private double phase = 0;

    private static final int MAX_FRAME_CACHE = 512;

    private record FrameSet(Component[] components) {
    }

    private record AnimState(Title title, String raw, Component headTemplate, Component tab, Component suffix) {
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
        Component text = textComponent(title, player);
        Component titleText = text.equals(Component.empty()) ? fallbackText(title) : text;
        Component prefix = withImage(title, player, text);
        Component suffix = title.suffix == null || title.suffix.isEmpty()
                ? Component.empty() : TextUtil.parse(resolve(player, title.suffix));
        Map<String, Component> tokens = new HashMap<>();
        tokens.put("prefix", prefix);
        tokens.put("prefix_text", titleText);
        tokens.put("title", prefix);
        tokens.put("title_text", titleText);
        tokens.put("suffix", suffix);
        tokens.put("player", Component.text(player == null ? "" : player.getName()));

        Component tab;
        if (settings.tabImage) {
            tab = TextUtil.compose(settings.tabFormat, tokens);
        } else {
            // tab 行高只有 9px，16px 的图片字形会把这一行顶成两行，所以默认只放文字
            Map<String, Component> tabTokens = new HashMap<>(tokens);
            tabTokens.put("prefix", titleText);
            tabTokens.put("title", titleText);
            tab = TextUtil.compose(settings.tabFormat, tabTokens);
        }
        Component head = TextUtil.compose(settings.headFormat, tokens);
        return new Render(prefix, suffix, tab, head, title.id);
    }

    public Component titleComponent(Title title) {
        return titleComponent(title, null);
    }

    /**
     * 图片称号：客户端已加载资源包时输出自定义字体字形，否则回退成文字。
     * player 为 null 表示不检测资源包状态（用于预览）。
     */
    public Component titleComponent(Title title, Player player) {
        return withImage(title, player, textComponent(title, player));
    }

    /** 只有文字、不带图片字形的版本。 */
    public Component titleText(Title title, Player player) {
        Component text = textComponent(title, player);
        if (!text.equals(Component.empty())) {
            return text;
        }
        return fallbackText(title);
    }

    private Component fallbackText(Title title) {
        if (images != null && images.hasImage(title)) {
            return images.fallback(title);
        }
        return Component.empty();
    }

    private Component withImage(Title title, Player player, Component text) {
        if (images == null || !images.hasImage(title)) {
            return text;
        }
        Component image = images.renderable(title, player)
                ? images.glyphComponent(title) : images.fallback(title);
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
        return animatedFrame(title, raw);
    }

    /**
     * 动画称号取帧：同一个称号 + 同一段原文只生成一次帧数组，
     * 帧数是 settings.animation-frames，phase 走完一轮正好回到第一帧。
     */
    private Component animatedFrame(Title title, String raw) {
        int count = Math.max(8, settings.animationFrames);
        int[] palette = title.paletteRgb.length >= 2 ? title.paletteRgb : settings.defaultPaletteRgb;
        String key = title.id + TextUtil.SLOT + raw + TextUtil.SLOT + count + TextUtil.SLOT
                + java.util.Arrays.hashCode(palette);
        FrameSet set = frames.get(key);
        if (set == null) {
            if (frames.size() > MAX_FRAME_CACHE) {
                frames.clear();
            }
            String plain = TextUtil.plain(TextUtil.parse(raw));
            Component[] components = new Component[count];
            for (int i = 0; i < count; i++) {
                components[i] = TextUtil.parse(TextUtil.animatedGradient(plain, palette,
                        (double) i / count));
            }
            set = new FrameSet(components);
            frames.put(key, set);
        }
        Component[] components = set.components();
        int index = (int) (phase * components.length);
        if (index < 0) {
            index = 0;
        } else if (index >= components.length) {
            index = components.length - 1;
        }
        return components[index];
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

        if (title == null || !title.animated) {
            anim.remove(player.getUniqueId());
        } else {
            anim.put(player.getUniqueId(), new AnimState(title, resolve(player, title.text),
                    headTemplate(title, player, render), render.tab(), render.suffix()));
        }

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

    /**
     * 头顶格式只解析一次，称号文字位置留成占位槽。
     * 图片称号的图标是静态的，也一并留在模板里，动画只换文字，图标不会每帧重建。
     */
    private Component headTemplate(Title title, Player player, Render render) {
        Component slot = Component.text(TextUtil.SLOT);
        Component prefixSlot = slot;
        if (images != null && images.hasImage(title)) {
            Component image = images.renderable(title, player)
                    ? images.glyphComponent(title) : images.fallback(title);
            if (!image.equals(Component.empty())) {
                Component gap = Component.text(" ").style(Style.style().font(Key.key("minecraft", "default")));
                prefixSlot = image.append(gap).append(slot);
            }
        }
        Map<String, Component> tokens = new HashMap<>();
        tokens.put("prefix", prefixSlot);
        tokens.put("prefix_text", slot);
        tokens.put("title", prefixSlot);
        tokens.put("title_text", slot);
        tokens.put("suffix", render.suffix());
        tokens.put("player", Component.text(player.getName()));
        return TextUtil.compose(settings.headFormat, tokens);
    }

    public void refreshAll() {
        // 头顶实体的缩放 / 偏移 / 视距在生成时写死，reload 改了这些配置必须重建实体
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeHead(player);
        }
        frames.clear();
        anim.clear();
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
            if (!player.addPassenger(display)) {
                display.remove();
                plugin.getLogger().warning("头顶称号挂载失败（玩家已有乘客）: " + player.getName());
                return null;
            }
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
        anim.clear();
        frames.clear();
    }

    /** RGB 动画推进，每 rgbIntervalTicks 执行一次。 */
    public void tickAnimation() {
        if (anim.isEmpty()) {
            return;
        }
        phase += 0.02d;
        if (phase >= 1.0d) {
            phase -= 1.0d;
        }
        Iterator<Map.Entry<UUID, AnimState>> iterator = anim.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AnimState> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            AnimState state = entry.getValue();
            Title title = state.title();
            // 称号被删 / 被 reload 换成了新对象：这一轮交给 refresh 重新决定要不要继续动画
            if (!title.enabled || registry.get(title.id) != title) {
                iterator.remove();
                refresh(player);
                continue;
            }
            // 到期 / 被回收 / 手动换称号时，30 秒一次的到期检查可能还没跑到，
            // 这里用缓存做一次廉价校验，避免已失效的称号继续占着头顶
            PlayerData data = store.peek(entry.getKey());
            if (data == null || !title.id.equals(data.equipped) || !data.ownedValid(title.id)) {
                iterator.remove();
                refresh(player);
                continue;
            }
            Component frame = animatedFrame(title, state.raw());
            TextDisplay display = heads.get(entry.getKey());
            // 头顶实体被别的插件 / 指令清掉后没人重建，这里兜一下底
            if (display != null && !display.isValid()) {
                heads.remove(entry.getKey());
                refresh(player);
                continue;
            }
            Component head = null;
            if (display != null) {
                head = state.headTemplate().replaceText(TextReplacementConfig.builder()
                        .matchLiteral(TextUtil.SLOT)
                        .replacement(frame)
                        .build());
                display.text(head);
            }
            Render previous = cache.get(entry.getKey());
            Component keptHead = previous == null ? Component.empty() : previous.head();
            // 聊天读的是这份缓存，prefix 换帧之后聊天里的称号也跟着动
            cache.put(entry.getKey(), new Render(frame, state.suffix(), state.tab(),
                    head == null ? keptHead : head, title.id));
        }
    }

    public void onQuit(Player player) {
        removeHead(player);
        cache.remove(player.getUniqueId());
        anim.remove(player.getUniqueId());
    }
}
