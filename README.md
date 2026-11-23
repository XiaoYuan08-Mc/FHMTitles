# FHMTitles · 称号系统（Purpur / Paper 26.3）

自研称号插件，编译目标 `paper-api 26.3.build.258-stable`、Java 25，已在 **Purpur 26.3（API 26.3.build.2660-stable）** 实机验证：插件加载、SQLite 建表、称号解析、指令、重载、自动备份全部正常。

- 产物：`build/libs/FHMTitles-1.0.0.jar`
- 源码：`src/main/java/cn/fhm/titles`，配置在 `src/main/resources`

## 安装

1. 把 `FHMTitles-1.0.0.jar` 丢进服务端 `plugins/`。
2. 首次启动会自动从 Paper 镜像下载 `sqlite-jdbc` 与 `mysql-connector-j`（放到服务器根目录 `libraries/`），**只需联网一次**；纯离线服务器可预先手动放入同名 jar。
3. 启动后生成 `plugins/FHMTitles/`：`config.yml`、`titles.yml`、`lang/zh_CN.yml`、`titles.db`、`backUp/`。
4. `/titles` 打开主界面。注意：插件注册的是 `/titles`（别名 `/cht`、`/称号`），**不占用原版 `/title`**，不会破坏屏幕标题指令。

软依赖（装了即自动接管，不装自动降级并给出提示）：

| 插件 | 用途 | 不装时 |
|---|---|---|
| Vault | 金币购买 | 金币价格不可用，其余支付方式正常 |
| PlayerPoints | 点券购买 | 点券价格不可用 |
| PlaceholderAPI | 变量输出/外部变量解析 | 变量扩展不注册 |

## 指令

| 指令 | 说明 | 权限 |
|---|---|---|
| `/titles` | 打开称号主界面 | `fhmtitles.use` |
| `/titles shop [分类]` | 打开分类商店（TEXT/BUFF/PARTICLE/IMAGE/RGB） | `fhmtitles.shop` |
| `/titles list [分类]` | 聊天栏查看已拥有称号 | `fhmtitles.use` |
| `/titles info <ID>` | 称号详情（分类/品质/时长/BUFF/价格） | `fhmtitles.use` |
| `/titles equip <ID>` / `unequip` | 佩戴 / 卸下 | `fhmtitles.equip` |
| `/titles toggle <chat\|tab\|head>` | 三处显示开关 | `fhmtitles.equip` |
| `/titles give <玩家> <ID> [天数]` | 发放称号（天数 -1 永久） | `fhmtitles.admin` |
| `/titles take <玩家> <ID>` | 回收称号 | `fhmtitles.admin` |
| `/titles card <玩家> <ID> [天数] [数量]` | 发放指定称号卡 | `fhmtitles.admin` |
| `/titles randomcard <玩家> <品质> [数量]` | 发放随机称号卡 | `fhmtitles.admin` |
| `/titles coins <玩家> <give\|take\|set> <数量>` | 调整称号币 | `fhmtitles.admin` |
| `/titles create <ID> <文本>` | 新建称号（支持 `&` 颜色与 MiniMessage） | `fhmtitles.admin` |
| `/titles delete <ID>` | 删除称号并联动清理玩家数据 | `fhmtitles.admin` |
| `/titles image <ID> <图片文件> [字形数] [高度] [ascent]` | 给称号指定 `images/` 下的图片并立即重建资源包 | `fhmtitles.admin` |
| `/titles pack [send [玩家\|all]]` | 重建资源包 / 把资源包重新推给玩家 | `fhmtitles.admin` |
| `/titles reload` | 重载配置/称号/语言，并重建资源包 | `fhmtitles.admin` |

## titles.yml 结构

```yaml
titles:
  warrior:
    text: "<gradient:#ff4b4b:#ffb703>[战神]</gradient> "   # 前缀文本（MiniMessage）
    suffix: " <red>⚔</red>"                                 # 后缀文本，可空
    tags: [TEXT]            # TEXT / BUFF / PARTICLE / IMAGE / RGB，决定进哪些商店
    tier: EPIC              # COMMON / RARE / EPIC / LEGEND / MYTHIC（随机卡按此抽取）
    icon: NETHERITE_SWORD   # GUI 展示材质
    model: 0                # 自定义模型数据，0 为不使用
    lore: ["<gray>描述</gray>"]
    enabled: true
    days: 30                # -1 永久，>0 限时天数
    weight: 15              # 随机卡权重
    permission: ""          # 佩戴/购买所需权限节点，空为无限制
    buffs: ["SPEED:1"]      # 药水效果:等级，等级从 1 开始
    particle: "FLAME"       # 粒子名，或 "DUST:#ff8800:1.2"
    particle-count: 10
    animated: true          # RGB 流光
    palette: ["#ff4b4b", "#ffc93c"]   # 留空用 config 默认色板
    image-glyph: "✦"        # 旧版占位字段，已由下面的 image 取代，可留空
    image:                  # 图片称号（可选）
      file: glyph.png       # 文件在 plugins/FHMTitles/images/ 下
      glyphs: 1             # 一张图切成几个字形，宽度必须能被整除
      height: 16            # 字形像素高度，8 约等于一行字，徽章建议 14~18
      ascent: 14            # 向上偏移像素，约等于 height-2 时坐在基线上
      fallback: "✦[绘]✦ "   # 客户端没装资源包时显示的替代文字
    prices:
      - type: MONEY         # MONEY / POINTS / COIN / ITEM
        amount: 5000
      - type: ITEM
        item: NETHER_STAR
        item-amount: 3
```

## 图片称号（PNG → 自定义字体字形）

Minecraft 没有原生的“图片文字”，通用做法是**资源包自定义 bitmap 字体**。插件已经把这条链路全部自动化：

1. 把 PNG 放进 `plugins/FHMTitles/images/`（带透明底、高度 ≤ 32px、内容不要留白）。
2. 在 `titles.yml` 里给称号写 `image:`，或直接执行 `/titles image <ID> <文件名> [字形数] [高度] [ascent]`。
3. 插件自动完成：分配私用区码位（U+E000 起，映射持久化在 `images/glyphs.yml`）→ 生成 `resourcepack/` → 打包成 `resourcepack.zip`（固定时间戳，同内容同 SHA-1）→ 起内置 HTTP 服务对外托管 → 玩家进服自动下发。
4. 生成的资源包结构：

```
pack.mcmeta                                     {"pack":{"min_format":65,"max_format":200,...}}
assets/fhmtitles/font/titles.json               每个称号一个 bitmap provider
assets/fhmtitles/textures/title/<id>.png        原图
```

渲染时图片段单独用 `fhmtitles:titles` 这个字体，普通文字段仍走默认字体，不会覆盖原版字体（不改 `minecraft:default`）。

分发与兼容：

- `resource-pack.port` 是内置 HTTP 端口；服务器在 NAT / 面板 / 反代后面时，必须把 `resource-pack.public-url` 填成客户端能访问到的完整地址（如 `https://cdn.example.com/fhm/pack.zip`），否则客户端下载不到包。
- `resource-pack.required: false`（默认）：拒绝加载的玩家只是在图片位置看到 `fallback` 文字，功能不受影响；改成 `true` 会把不装包的玩家踢下线。
- `packLoaded` 状态按玩家跟踪：收到 `SUCCESSFULLY_LOADED` 才切成图片，`DECLINED / FAILED_DOWNLOAD / INVALID_URL / DISCARDED` 回退文字，加载完成后自动刷新 TAB 与头顶。
- 26.3 的 `pack.mcmeta` 用 `min_format` / `max_format`（1.21.9 起不再用 `supported_formats`），默认区间 65–200 覆盖当前资源包格式；若以后版本号漂移，改 `resource-pack.min-format / max-format` 即可。
- 客户端已下载过的旧包靠 SHA-1 缓存命中；重新生成且内容不变时 hash 不变，玩家不会重复下载。

调试：

- `/titles pack` 会打印图片数量、包大小、前 8 位 hash、访问地址，以及每条校验失败原因（缺图、宽高比过极端、宽度不能被字形数整除）。
- 直接用浏览器访问资源包地址可确认能否下载；`pack.mcmeta`/字体 JSON 生成在 `plugins/FHMTitles/resourcepack/`，可直接审阅。
- 示例图 `glyph.png`（“绘梦”徽章）首次启动自动释放，`glyph` 称号开箱即可看到效果。

## 25 项特性对照| # | 特性 | 实现 |
|---|---|---|
| 1 | MySQL / SQLite | `storage.type`，同一套 SQL 抽象；SQLite 单连接 + 单线程执行器 |
| 2 | 按类型打开不同商店 | 主界面 5 个分类入口，`open:shop:<分类>` |
| 3 | 称号管理 GUI | `/titles` 内管理员按钮：左键启停、右键聊天栏改文本、Shift+左键删除 |
| 4 | 称号仓库 | “我的称号”分页列表，点击即换、再点卸下 |
| 5 | 数量奖励 + GUI | `rewards:` 阈值，达到自动发放，`/titles` 内可看领取状态 |
| 6 | 指定称号卡 | `/titles card`，物品带 PDC 记录 ID+天数，右键消耗 |
| 7 | 随机称号卡 | `/titles randomcard`，按品质建池、权重抽取、优先未拥有 |
| 8 | 多种购买方式 | Vault 金币 / PlayerPoints 点券 / 称号币 / 任意物品（真实扣除） |
| 9 | 限时 / 永久出售 | `days` 字段，购买后写入到期时间戳 |
| 10 | 文本与 lore 自定义 | 全部消息走 `lang/zh_CN.yml`，称号 lore 在 titles.yml |
| 11 | 性能 | 渲染结果主线程缓存、聊天渲染器只读缓存、DB 单线程、动画/粒子间隔可调、头顶 `view-range` |
| 12 | PlaceholderAPI | `%fhmtitles_display% / _prefix / _suffix / _name / _equipped / _count / _coins / _chat / _tab / _head / _has_<ID>%` |
| 13 | 删除称号联动 | 删除时清空所有玩家的仓库、已购记录、佩戴状态 |
| 14 | 删除 BUFF 联动 | BUFF 挂在称号定义上，称号删除后效果随佩戴清理失效 |
| 15 | 商店隐藏已购 | `shop.hide-purchased: true`，买过的称号只对该玩家隐藏 |
| 16 | 聊天显示开关 | 主界面 / `/titles toggle chat` |
| 17 | TAB 与头顶开关 | 同上，分别独立 |
| 18 | 称号 BUFF | 无限时长药水效果，切换即精确移除，掉线/死亡后补挂 |
| 19 | 获取消息类型 | `title.acquire-message-type: CHAT / ACTIONBAR / TITLE / NONE` |
| 20 | 自定义材质 | `icon` + `model`（自定义模型数据） |
| 21 | 粒子特效 | `particle` 支持普通粒子与染色 DUST |
| 21b | 图片称号 | PNG → 自定义 bitmap 字体字形，自动生成/托管资源包，未装包回退文字 |
| 22 | UUID 存储 | 主键为 UUID 字符串，改名不丢数据 |
| 23 | MiniMessage | 全链路 MiniMessage，`&` 颜色码在建称号时自动转换 |
| 24 | 自动备份 | `backUp/titles-时间戳.sql`，按 `keep` 数量滚动清理 |
| 25 | 多语言 | `lang/<code>.yml`，`config.yml` 的 `language` 切换 |

## 已知边界

- 管理 GUI 目前能改文本、启停、删除；**改价格/天数/材质/粒子**请在 `titles.yml` 改完 `/titles reload`（这是刻意保留的“高级编辑走文件”路线，避免后台输入框堆成表单）。
- 图片称号的纵向对齐由 `ascent` / `height` 控制，不同字体缩放档位下观感会有差异，建议在客户端里微调到合适值后固定下来。
- 头顶称号是 TextDisplay 乘客实体，位置/大小/可见距离在 `config.yml` 的 `display.head` 三项里调；人多时可把 `view-distance` 调到 16～24。
- Vault 与 PlayerPoints 走反射桥接，未装时相关价格直接不可购买并提示，不会抛异常。

## 二次开发

```powershell
gradle build          # 需 JDK 25（Gradle 会用 toolchain 自动准备）
gradle runServer      # 未配置，建议直接用测试服验证
```

主要包结构：

```
cn.fhm.titles
├─ core      TitleService / DisplayService / BuffService / ParticleService / RewardService
├─ model     Title / TitleRegistry / PlayerData / Category / Tier / PriceEntry
├─ storage   Database（SQLite|MySQL）/ DataStore
├─ gui       GuiManager（主界面/商店/仓库/管理/奖励/确认）/ InputManager
├─ shop      ShopService（四种支付校验与扣款）
├─ item      CardFactory（指定卡 / 随机卡）
├─ resource  ImageTitleService（PNG → 字形 → 资源包 → HTTP 分发）
├─ listener  Player / Chat / Gui / Card / ResourcePack
├─ command   TitleCommand
├─ econ      VaultHook / PointsHook（反射）
├─ hook      PapiHook
└─ papi      FHMTitleExpansion
```
