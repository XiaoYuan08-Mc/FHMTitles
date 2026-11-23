package cn.fhm.titles.model;

import cn.fhm.titles.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Title {

    public final String id;
    public String text = "";
    public String suffix = "";
    public Set<Category> tags = EnumSet.of(Category.TEXT);
    public Tier tier = Tier.COMMON;
    public Material icon = Material.NAME_TAG;
    public int model = 0;
    public List<String> lore = new ArrayList<>();
    public boolean enabled = true;
    public int days = -1;
    public int weight = 10;
    public String permission = "";
    public List<PriceEntry> prices = new ArrayList<>();
    public List<String> buffs = new ArrayList<>();
    public String particle = "";
    public int particleCount = 8;
    public boolean animated = false;
    public List<String> palette = new ArrayList<>();
    public String imageGlyph = "";
    public String imageFile = "";
    public int imageGlyphs = 1;
    public int imageHeight = 9;
    public int imageAscent = 7;
    public String imageFallback = "";
    public int sort = 0;

    /*
     * 以下是载入期算好的运行时数据，不写进 titles.yml。
     * BUFF 词条和粒子参数在热路径上会被反复用到（补 BUFF、粒子周期、动画帧），
     * 与其每次重新 split 字符串，不如在载入时解析一次。
     */
    public transient List<PotionBuff> potionBuffs = List.of();
    public transient ParticleSpec particleSpec;
    public transient int[] paletteRgb = new int[0];

    public Title(String id) {
        this.id = id;
    }

    /** titles.yml 载入 / 后台新增称号后调用；只依赖当前字段，可重复调用。 */
    public void compile() {
        potionBuffs = PotionBuff.parseAll(buffs);
        particleSpec = ParticleSpec.parse(particle, particleCount);
        int[] rgb = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            rgb[i] = TextUtil.parseHex(palette.get(i));
        }
        paletteRgb = rgb;
    }

    public boolean hasTag(Category category) {
        return tags.contains(category);
    }

    public boolean permanent() {
        return days < 0;
    }

    public PriceEntry price(PriceType type) {
        for (PriceEntry entry : prices) {
            if (entry.type == type) {
                return entry;
            }
        }
        return null;
    }

    public static Title load(String id, ConfigurationSection section) {
        Title title = new Title(id);
        title.text = section.getString("text", "");
        title.suffix = section.getString("suffix", "");
        title.tags = EnumSet.noneOf(Category.class);
        for (String tag : section.getStringList("tags")) {
            Category category = Category.parse(tag);
            if (category != null) {
                title.tags.add(category);
            }
        }
        if (title.tags.isEmpty()) {
            title.tags.add(Category.TEXT);
        }
        title.tier = Tier.parse(section.getString("tier", "COMMON"));
        Material icon = Material.matchMaterial(section.getString("icon", "NAME_TAG"));
        title.icon = icon == null ? Material.NAME_TAG : icon;
        title.model = section.getInt("model", 0);
        title.lore = new ArrayList<>(section.getStringList("lore"));
        title.enabled = section.getBoolean("enabled", true);
        title.days = section.getInt("days", -1);
        title.weight = section.getInt("weight", 10);
        title.permission = section.getString("permission", "");
        title.buffs = new ArrayList<>(section.getStringList("buffs"));
        title.particle = section.getString("particle", "");
        title.particleCount = section.getInt("particle-count", 8);
        title.animated = section.getBoolean("animated", false);
        title.palette = new ArrayList<>(section.getStringList("palette"));
        title.imageGlyph = section.getString("image-glyph", "");
        title.sort = section.getInt("sort", 0);

        ConfigurationSection image = section.getConfigurationSection("image");
        if (image != null) {
            title.imageFile = image.getString("file", "");
            title.imageGlyphs = Math.max(1, image.getInt("glyphs", 1));
            title.imageHeight = Math.max(1, image.getInt("height", 9));
            title.imageAscent = image.getInt("ascent", 7);
            title.imageFallback = image.getString("fallback", "");
        }

        title.prices = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("prices")) {
            PriceEntry entry = new PriceEntry();
            Object type = raw.get("type");
            entry.type = type == null ? PriceType.MONEY : parseType(String.valueOf(type));
            Object amount = raw.get("amount");
            entry.amount = amount instanceof Number n ? n.doubleValue() : parseDouble(amount);
            Object item = raw.get("item");
            if (item != null) {
                Material material = Material.matchMaterial(String.valueOf(item));
                entry.item = material == null ? Material.DIAMOND : material;
            }
            Object itemAmount = raw.get("item-amount");
            entry.itemAmount = itemAmount instanceof Number n ? n.intValue() : (int) parseDouble(itemAmount);
            if (entry.type == PriceType.ITEM && entry.amount <= 0) {
                entry.amount = entry.itemAmount;
            }
            title.prices.add(entry);
        }
        title.compile();
        return title;
    }

    public void save(ConfigurationSection section) {
        section.set("text", text);
        section.set("suffix", suffix);
        List<String> tagNames = new ArrayList<>();
        for (Category category : tags) {
            tagNames.add(category.name());
        }
        section.set("tags", tagNames);
        section.set("tier", tier.name());
        section.set("icon", icon.name());
        section.set("model", model);
        section.set("lore", lore);
        section.set("enabled", enabled);
        section.set("days", days);
        section.set("weight", weight);
        section.set("permission", permission);
        section.set("buffs", buffs);
        section.set("particle", particle);
        section.set("particle-count", particleCount);
        section.set("animated", animated);
        section.set("palette", palette);
        section.set("image-glyph", imageGlyph);
        section.set("sort", sort);
        if (imageFile == null || imageFile.isEmpty()) {
            section.set("image", null);
        } else {
            ConfigurationSection image = section.createSection("image");
            image.set("file", imageFile);
            image.set("glyphs", imageGlyphs);
            image.set("height", imageHeight);
            image.set("ascent", imageAscent);
            image.set("fallback", imageFallback);
        }

        List<Map<String, Object>> priceMaps = new ArrayList<>();
        for (PriceEntry entry : prices) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", entry.type.name());
            map.put("amount", entry.amount);
            if (entry.type == PriceType.ITEM) {
                map.put("item", entry.item.name());
                map.put("item-amount", entry.itemAmount);
            }
            priceMaps.add(map);
        }
        section.set("prices", priceMaps);
    }

    public static PriceType parseType(String raw) {
        try {
            return PriceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PriceType.MONEY;
        }
    }

    private static double parseDouble(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
