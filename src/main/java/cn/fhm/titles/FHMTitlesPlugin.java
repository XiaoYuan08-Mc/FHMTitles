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
import org.bukkit.scheduler.BukkitTask;

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
    private BukkitTask expireTask;
    private BukkitTask animationTask;
    private BukkitTask particleTask;
    private BukkitTask backupTask;
    private BukkitTask flushTask;

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
            database.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new DataStore(database, settings.flushSeconds);
        try {
            store.loadAll();
        } catch (SQLException e) {
            // 读档失败时继续跑会在玩家进服后用空数据覆盖原存档，宁可先停用
            getLogger().severe("读取玩家数据失败，已停用插件以避免覆盖存档: " + e.getMessage());
            database.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        display = new DisplayService(this, settings, lang, registry, store);
        images = new cn.fhm.titles.resource.ImageTitleService(this, settings, registry);
        display.setImages(images);
        buff = new BuffService(registry, settings);
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

        getServer().getPluginManager().registerEvents(new PlayerListener(this, titles, display, input), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(settings, display, store, input), this);
        getServer().getPluginManager().registerEvents(new CardListener(cards), this);
        getServer().getPluginManager().registerEvents(new cn.fhm.titles.listener.ResourcePackListener(this), this);

        // flush-seconds = 0 时 markDirty 会逐次立即写；这里仍留 1 秒兜底，
        // 只处理写失败被回填成脏的行，平时 dirty 为空，检查成本可以忽略
        long flushTicks = Math.max(1, settings.flushSeconds) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(this, store::flushDirty, flushTicks, flushTicks);
        schedulePeriodicTasks();

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
        // 周期任务在创建时就把间隔写死了，reload 改了间隔要重建任务才生效
        schedulePeriodicTasks();
    }

    /** 到期检查 / RGB 动画 / 粒子 / 备份的周期任务；reload 改间隔后重建。 */
    private void schedulePeriodicTasks() {
        if (expireTask != null) {
            expireTask.cancel();
        }
        if (animationTask != null) {
            animationTask.cancel();
        }
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (backupTask != null) {
            backupTask.cancel();
            backupTask = null;
        }
        long expireTicks = settings.expireCheckSeconds * 20L;
        expireTask = Bukkit.getScheduler().runTaskTimer(this, titles::tickExpiry, expireTicks, expireTicks);
        animationTask = Bukkit.getScheduler().runTaskTimer(this, display::tickAnimation, 1L, settings.rgbIntervalTicks);
        particleTask = Bukkit.getScheduler().runTaskTimer(this, particles::tick, 1L, settings.particleIntervalTicks);
        if (settings.backupEnabled) {
            backupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                // 先把脏行刷进库，再备份；两者都在同一个 DB 执行器上按顺序执行
                store.flushDirty();
                database.backupAsync();
            }, 20L * 60L, 20L * 60L * settings.backupIntervalMinutes);
        }
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
            // 先让 DB 队列跑完，再同步写最后一批脏数据，最后才关连接：
            // 反过来写的话，队列里更旧的快照会在关服瞬间覆盖掉刚写的这一版
            if (database != null) {
                database.shutdown();
            }
            store.saveDirtySync();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("FHMTitles 已卸载");
    }
}
