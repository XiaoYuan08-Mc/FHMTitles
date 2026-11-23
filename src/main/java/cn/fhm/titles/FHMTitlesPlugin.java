package cn.fhm.titles;

import cn.fhm.titles.command.TitleCommand;
import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.BuffService;
import cn.fhm.titles.core.DisplayService;
import cn.fhm.titles.core.ParticleService;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.gui.GuiManager;
import cn.fhm.titles.gui.InputManager;
import cn.fhm.titles.hook.PapiHook;
import cn.fhm.titles.item.CardFactory;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.listener.CardListener;
import cn.fhm.titles.listener.ChatListener;
import cn.fhm.titles.listener.GuiListener;
import cn.fhm.titles.listener.PlayerListener;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.shop.ShopService;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class FHMTitlesPlugin extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private TitleRegistry registry;
    private Database database;
    private DataStore store;
    private DisplayService display;
    private BuffService buff;
    private ParticleService particles;
    private TitleService titles;
    private ShopService shop;
    private GuiManager gui;
    private CardFactory cards;
    private InputManager input;
    private PapiHook papi;
    private cn.fhm.titles.resource.ImageTitleService images;

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public TitleRegistry registry() {
        return registry;
    }

    public DataStore store() {
        return store;
    }

    public DisplayService display() {
        return display;
    }

    public BuffService buff() {
        return buff;
    }

    public TitleService titles() {
        return titles;
    }

    public ShopService shop() {
        return shop;
    }

    public GuiManager gui() {
        return gui;
    }

    public CardFactory cards() {
        return cards;
    }

    public PapiHook papi() {
        return papi;
    }

    public cn.fhm.titles.resource.ImageTitleService images() {
        return images;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        lang = new Lang(this);
        lang.load(settings.language);
        registry = new TitleRegistry(this);
        registry.load();

        database = new Database(this, settings);
        try {
            database.init();
        } catch (Exception e) {
            getLogger().severe("数据库初始化失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new DataStore(database);
        try {
            store.loadAll();
        } catch (SQLException e) {
            getLogger().severe("读取玩家数据失败: " + e.getMessage());
        }

        display = new DisplayService(this, settings, lang, registry, store);
        images = new cn.fhm.titles.resource.ImageTitleService(this, settings, registry);
        display.setImages(images);
        buff = new BuffService(registry);
        particles = new ParticleService(registry, store);
        titles = new TitleService(this, settings, lang, registry, store, display, buff);
        shop = new ShopService(this, settings, lang, store, titles);
        input = new InputManager(this);
        gui = new GuiManager(this, settings, lang, registry, store, titles, shop, input);
        cards = new CardFactory(this, settings, lang, registry, store, titles);
        papi = new PapiHook();
        display.setPapi(papi);

        PluginCommand command = getCommand("titles");
        if (command != null) {
            TitleCommand executor = new TitleCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this, titles, display, buff), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(settings, display, store, input), this);
        getServer().getPluginManager().registerEvents(new CardListener(cards), this);
        getServer().getPluginManager().registerEvents(new cn.fhm.titles.listener.ResourcePackListener(this), this);

        long expireTicks = settings.expireCheckSeconds * 20L;
        Bukkit.getScheduler().runTaskTimer(this, titles::tickExpiry, expireTicks, expireTicks);
        Bukkit.getScheduler().runTaskTimer(this, display::tickAnimation, 1L, settings.rgbIntervalTicks);
        Bukkit.getScheduler().runTaskTimer(this, particles::tick, 1L, settings.particleIntervalTicks);
        if (settings.backupEnabled) {
            Bukkit.getScheduler().runTaskTimer(this, () -> database.backupAsync(),
                    20L * 60L, 20L * 60L * settings.backupIntervalMinutes);
        }

        registerPlaceholders();

        try {
            images.start();
        } catch (Throwable t) {
            getLogger().warning("图片称号模块初始化失败: " + t.getMessage());
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            titles.onJoin(player);
            images.onJoin(player);
        }

        getLogger().info("FHMTitles 已加载，共 " + registry.all().size() + " 个称号定义" + (papi.available() ? "，PlaceholderAPI 已挂载" : ""));
    }

    private void registerPlaceholders() {
        if (!papi.available()) {
            return;
        }
        try {
            new cn.fhm.titles.papi.FHMTitleExpansion(this).register();
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI 扩展注册失败: " + t.getMessage());
        }
    }

    public void reloadAll() {
        settings.load();
        lang.load(settings.language);
        // registry.load() 与在线玩家的 BUFF / 显示刷新都在 titles.reloadAll() 里
        titles.reloadAll();
        titles.rewards().load();
        try {
            images.reload();
        } catch (Throwable t) {
            getLogger().warning("重建资源包失败: " + t.getMessage());
        }
        display.refreshAll();
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (images != null) {
            images.stop();
        }
        if (display != null) {
            display.clearAll();
        }
        if (store != null) {
            // 关服路径必须同步写完：以前只往队列里塞，数据库 close 等不到队尾就丢了
            store.saveDirtySync();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("FHMTitles 已卸载");
    }
}
