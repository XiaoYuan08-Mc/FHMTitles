# PlayerTitle 5.5.8-free 源码分析 & FHMTitles 1.2.0 优化记录

> 本文只借鉴设计思路与性能取舍，没有复制参考插件的类结构、方法实现、SQL 构造器或 GUI 库代码。
> 本轮改动全部落在 FHMTitles 现有架构上自写实现，可逐文件对照 git diff。

## 一、参考插件在做什么

把 `PlayerTitle-5.5.8-free` 反编译后按包读了一遍，它的组织方式大致是：

- **分层**：`api` / `service` / `core`（`buff`、`coin`、`reward`、`shop`、`purchase`）/ `listener` / `hook` / `inventory` / `lib`。
- **数据**：称号定义表 + 玩家佩戴表 + 称号币表；运行时把「展示 / 增益 / 粒子」三种佩戴结果分别放进 `TitleConstants` 的三张内存表，只在变更时刷新，聊天和占位符直接读缓存。
- **调度**：`HandySchedulerUtil` + `RegionSchedulerUtil` + `EntitySchedulerUtil`，用反射在 Bukkit 与 Folia 两套调度 API 之间切换。
- **存储**：HikariCP + 自己拼的 SQL + `DbExecution` 单线程异步，读写都不在主线程。
- **BUFF**：原版 `potion_effect` 之外还适配 AttributePlus、SX-Attribute、MythicLib 等属性插件；死亡、重生、图腾、换世界后重新挂上；支持世界级禁用。
- **经济**：`IBuyService` / `BuyStrategy` 策略化，金币、点券、物品、权限各自实现。
- **玩法**：展示 / 增益 / 粒子三个独立槽位、按收集数量发奖励、权限称号自动解锁、切换冷却、公开 API 与事件。
- **GUI**：配置化 inventory 库，菜单与物品基本由 yml 驱动。

它的优点集中在「**状态只在变更时重算，展示路径只读缓存**」和「**所有阻塞操作都排到单线程异步**」这两条原则上；缺点也很明显：一个功能一个服务类，抽象层很厚，二次开发要跟着它的数据模型走。

## 二、FHMTitles 的对照结论

强项：

- 图片称号 + 资源包分发（HTTP 服务、sha1 缓存、失败回退）是参考插件 free 版没有的；
- MiniMessage 全链路、SQLite/MySQL 双端、称号卡与随机卡、按数量发奖励、完整 GUI 与指令都已经具备；
- 佩戴状态本来就有 `Render` 缓存，聊天路径不查库。

短板：

- 只有单槽位，一个称号同时决定展示、BUFF、粒子；
- BUFF 只支持原版药水，没有属性插件适配；
- 没有 Folia 调度适配；
- 没有公开 API / 事件；
- 摘 BUFF 的判定偏宽：同类型无限时长效果可能误删玩家自己的药水；
- 称号 BUFF 没有世界策略；
- 存档每次改动立即写一条 SQL，批量场景下往返次数偏高。

## 三、本轮实际改了什么

| 模块 | 改动 | 预期收益 |
| --- | --- | --- |
| `model/Title`、新增 `PotionBuff` / `ParticleSpec` | `titles.yml` 载入时把 `buffs`、`particle`、`palette` 编译成运行时对象 | 补 BUFF、粒子周期、动画帧不再 split 字符串 / `Particle.valueOf` / 解析 `#RRGGBB` |
| `core/DisplayService` | 动画称号按 `display.animation-frames` 预生成组件帧，动画周期只取帧；头顶格式只解析一次，称号文字留占位槽，每帧只 `replaceText` | 动画周期不再 MiniMessage 反序列化、不再每帧建 token Map；没有动画称号在线时整轮直接返回 |
| `core/DisplayService` | `refresh` 里称号文本只解析一次，前缀 / 头顶共用同一个 Component | 每次刷新少一次（动画称号少两次）反序列化 |
| `listener/ChatListener` | 每条消息只解析一次 `chat-format`，消息本体作为槽按 viewer 替换 | N 个接收者从 N 次解析降到 1 次 |
| `core/ParticleService` | 只读 `title.particleSpec`；全库没有粒子称号时整轮跳过 | 粒子周期无字符串分配，空库时零开销 |
| `core/BuffService` | 进服清残留改用「BUFF 类型 → 等级」索引，按称号库版本号失效重建 | 每个玩家进服从 O(全库 × BUFF 条数) 降到 O(索引条目) |
| `core/BuffService` | 摘效果收紧为「无限时长 + 等级与称号声明一致」，并记录本插件加过的等级 | 插件重载后也不会误删玩家自己喝的同类型药水 |
| `core/BuffService`、`PlayerListener` | 新增 `title.buff-disabled-worlds`，换世界 / 重生时 `resync`：先摘本插件的效果，再按新世界策略补回 | 世界禁用生效，跨世界不再残留 BUFF |
| `storage/DataStore` | 新增 `storage.flush-seconds`：只有切换佩戴 / 显示开关这类高频改动攒批；发放、回收、购买、到期、领奖、下线仍然立即写；备份前先把脏行刷进库；设为 0 则高频改动也逐次立即写 | 连续切换称号从「每次一条 SQL」变成「一个周期一条批量 SQL」，关键数据又不延迟 |
| `storage/Database` | 单连接访问全部串行化；写失败重建连接并重试一次；新增批量 upsert；失败的行回填成脏留到下一轮；关服先排空队列再写最后一批 | MySQL 掉线不再静默丢档；关服不会出现旧快照覆盖新数据 |
| 资源与元数据 | `config.yml` 新增三个键、`plugin.yml` 新增 `fhmtitles.buff.bypass-world`、版本 1.2.0 | 可配置、可授权 |

调用次数上的变化（按 100 个在线玩家估）：

- 进服清残留：以前每人扫全库并解析所有 BUFF 串；现在索引只在一轮 reload 里建一次。
- 动画周期（假设 100 人佩戴动画称号、4 tick 间隔）：以前每秒约 500 次格式串反序列化 + 500 次 token Map 构造 + 500 次 PAPI 解析；现在只有取帧和组件替换，反序列化为 0。
- 聊天（100 人收到同一条消息）：以前 100 次 `chat-format` 解析；现在 1 次。
- 连续 10 次切换称号：以前 10 条 upsert；现在 1 条批量 upsert（仍在 flush 周期内时）。

### 完整检查阶段补的修补

第二轮把改动逐条复查了一遍，又补了这些洞：

- **读档失败不再硬跑**：`store.loadAll()` 抛 SQL 异常时直接停用插件并关连接，避免带着空缓存在线，玩家一进服就把原存档覆盖成空数据。
- **`flush-seconds: 0` 真的等于旧版行为**：之前 0 只是不注册批量任务，`markDirty` 的佩戴 / 开关改动反而一直不落盘。现在 0 会走 `save()` 逐次立即写；同时保留 1 秒兜底任务，只重试写失败被回填成脏的行。
- **DB 执行器关闭时不再静默丢写**：`saveAsync` / `saveBatchAsync` 提交被拒绝时回调失败，`DataStore` 会把 UUID 重新标脏，交给关服时的同步落盘收尾。
- **备份前先刷脏**：备份任务先 `flushDirty()` 再 `backupAsync()`，两者在同一个单线程 DB 执行器上按顺序跑，备份不会漏掉刚合并写盘前的改动。
- **后台禁用 / 启用称号会重对齐 BUFF**：以前只刷新显示，佩戴者身上的 BUFF 会一直留到重生 / 换世界。现在走 `TitleService.refreshPlayers()`，禁用时摘掉、启用时补回。
- **reload 会重建周期任务**：到期检查 / RGB / 粒子 / 备份的间隔在任务创建时写死，改了 `config.yml` 后现在会 cancel + 重新注册；头顶实体也会先移除再生成，`display.head.scale / y-offset / view-distance` 改完能生效。
- **异步聊天线程不再发包**：GUI 输入取消提示改回主线程发送；玩家退出时清掉未完成的输入回调，避免 Player 引用留在 `InputManager` 里。
- **`addCoins` 立即写盘**；旧版 BUFF 名（`INCREASE_DAMAGE` / `DAMAGE_RESISTANCE` / `FAST_DIGGING` 等）和 `REDSTONE` 粒子做兼容映射；非法 BUFF / 粒子在载入期打警告，不再静默消失。
- **动画 tick 的佩戴校验**：称号到期 / 被回收 / 被换掉后，4 tick 内就会停动画并刷新，不用等 30 秒一次的到期检查。

验证方式：本机 Paper 26.3 build 259（Java 25）实际启动，加载 27 个称号定义、SQLite 存储、资源包 HTTP 服务；执行 `/titles reload`、`/titles pack`、`/titles create`、`/titles delete`、离线 `/titles give` / `/titles take` / `/titles coins`，并在发放后 2 秒直接强杀进程，重启后 SQLite 里 `owned` / `equipped` / `coins` 均为强杀前的新值。

## 四、行为变化与注意事项

1. **TAB 行保持静态**：原本 TAB 也只在刷新时更新一帧，本轮明确维持这一行为。RGB 动画继续作用在头顶与聊天前缀，避免每 4 tick 给全服玩家列表发包。
2. **PAPI 后缀在动画期间不再逐帧解析**：刷新时解析一次，帧内复用。称号文本本身的 PAPI 仍按玩家解析。
3. **崩溃最多丢 `flush-seconds` 秒的高频改动**（佩戴 / 显示开关）；发放、回收、购买、到期、领奖、下线已经立即写盘。把 `flush-seconds` 设成 0，这类改动也逐次立即写。
4. **`storage.flush-seconds` 不支持 reload 热更**，需重启；其余新增配置项可以 reload。
5. **非法粒子 / BUFF 配置现在在载入期就被跳过并打警告日志**，不会每个粒子周期静默失败。
6. **摘 BUFF 变保守**：只处理无限时长且等级匹配的那一层，玩家自己的有限时长药水不会被顺带清掉。

## 五、明确没有照搬的部分

- **三独立槽位（展示 / 增益 / 粒子）**：这是玩法与数据模型改动，不是性能优化。照搬它的三张常量表会把现有 `PlayerData.equipped` 单一字段撕成三份，还要改 GUI、卡片、到期逻辑和迁移脚本，收益主要是玩法深度。若要做，建议独立设计：表上新增 `equipped_show` / `equipped_buff` / `equipped_particle` 三列 + 一次性迁移，GUI 拆三个页签，到期按槽位分别校验。
- **属性插件 BUFF 适配**：需要额外 `compileOnly` 依赖和反射兼容层，先不引入依赖与兼容风险。
- **Folia 调度**：现有任务会遍历在线玩家、给实体挂乘客，必须按 region / entity 调度器重构并实机压测，不能盲改。
- **HikariCP 连接池 + 自拼 SQL 构造器**：单服体量下单连接 + 串行化已经够用；引入连接池会改变事务与关服语义。
- **公开 API / 事件、付费策略接口**：等数据模型稳定、确有第三方调用需求后再定签名。

## 六、后续优先级建议

1. 切换称号冷却（低成本，直接压住连点带来的写库与组件重建）；
2. 属性插件 BUFF 适配（中成本，建议独立模块 + 失败降级）；
3. 三槽位（高成本，先定数据迁移方案再动代码）；
4. Folia 适配（高成本，需要真实 Folia 环境跑任务与实体挂载）；
5. API / 事件（中低成本，等模型稳定）。

## 附：本轮改动文件

- `src/main/java/cn/fhm/titles/model/Title.java`
- `src/main/java/cn/fhm/titles/model/TitleRegistry.java`
- `src/main/java/cn/fhm/titles/model/PotionBuff.java`（新增）
- `src/main/java/cn/fhm/titles/model/ParticleSpec.java`（新增）
- `src/main/java/cn/fhm/titles/util/TextUtil.java`
- `src/main/java/cn/fhm/titles/config/Settings.java`
- `src/main/java/cn/fhm/titles/core/DisplayService.java`
- `src/main/java/cn/fhm/titles/core/BuffService.java`
- `src/main/java/cn/fhm/titles/core/ParticleService.java`
- `src/main/java/cn/fhm/titles/core/TitleService.java`
- `src/main/java/cn/fhm/titles/listener/PlayerListener.java`
- `src/main/java/cn/fhm/titles/listener/ChatListener.java`
- `src/main/java/cn/fhm/titles/storage/DataStore.java`
- `src/main/java/cn/fhm/titles/storage/Database.java`
- `src/main/java/cn/fhm/titles/FHMTitlesPlugin.java`
- `src/main/resources/config.yml`、`plugin.yml`、`README.md`、`build.gradle.kts`
