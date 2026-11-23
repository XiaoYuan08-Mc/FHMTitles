package cn.fhm.titles.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Map;

/**
 * MiniMessage 统一入口。
 * 支持把 %token% 转换为 <token> 自闭合标签，这样 <white>%player%</white> 这类写法不会因为切分而破坏标签。
 */
public final class TextUtil {

    /** 组件插槽占位符：先把格式串解析成带槽的模板，之后每帧只替换槽，避免重复解析整条格式。 */
    public static final String SLOT = "\u0001";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TextUtil() {
    }

    public static MiniMessage mini() {
        return MM;
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    public static Component compose(String format, Map<String, Component> tokens) {
        if (format == null || format.isEmpty()) {
            return Component.empty();
        }
        String replaced = format;
        TagResolver.Builder builder = TagResolver.builder();
        if (tokens != null) {
            for (Map.Entry<String, Component> entry : tokens.entrySet()) {
                replaced = replaced.replace("%" + entry.getKey() + "%", "<" + entry.getKey() + ">");
                builder.resolver(TagResolver.resolver(entry.getKey(), Tag.selfClosingInserting(entry.getValue())));
            }
        }
        // 配置里写 \n 表示换行（头顶两行：称号在上、名字在下）
        replaced = replaced.replace("\\n", "\n");
        return MM.deserialize(replaced, builder.build()).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String escape(String value) {
        return value == null ? "" : MM.escapeTags(value);
    }

    /** 把 &amp; 颜色代码翻译成 MiniMessage；已经是 MiniMessage 的原文原样返回。 */
    public static String toMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.indexOf('&') < 0 && raw.indexOf('\u00a7') < 0) {
            return raw;
        }
        Component legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(raw);
        return MM.serialize(legacy);
    }

    /**
     * 逐字染色，用于 RGB 流光称号。text 为纯文本（可含 MiniMessage 以外的普通字符）。
     */
    public static String animatedGradient(String text, List<String> palette, double phase) {
        if (palette == null || palette.size() < 2) {
            return text == null ? "" : escape(text);
        }
        int[] colors = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            colors[i] = parseHex(palette.get(i));
        }
        return animatedGradient(text, colors, phase);
    }

    /**
     * 调色板已经解析成 int 的版本。
     * 动画帧在载入期就要生成几十份，别每一帧再把 "#ff4b4b" 解析一遍。
     */
    public static String animatedGradient(String text, int[] colors, double phase) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (colors == null || colors.length < 2) {
            return escape(text);
        }
        StringBuilder out = new StringBuilder(text.length() * 14);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                out.append(' ');
                continue;
            }
            double pos = ((double) i / Math.max(1, len) + phase) % 1.0d;
            double scaled = pos * colors.length;
            int from = (int) Math.floor(scaled) % colors.length;
            int to = (from + 1) % colors.length;
            double frac = scaled - Math.floor(scaled);
            int color = blend(colors[from], colors[to], frac);
            out.append("<#");
            appendHex6(out, color);
            out.append('>');
            // 只对真正会被 MiniMessage 解释的字符做转义，逐字调 escapeTags 在动画路径上太贵
            if (ch == '<' || ch == '\\') {
                out.append('\\');
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static void appendHex6(StringBuilder out, int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        for (int pad = hex.length(); pad < 6; pad++) {
            out.append('0');
        }
        out.append(hex);
    }

    public static int parseHex(String input) {
        if (input == null) {
            return 0xFFFFFF;
        }
        String s = input.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static int blend(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
