# FHMTitles

Minecraft 称号插件，跑在 Paper / Purpur 26.3、Java 25 上。称号可以是纯文本，可以带药水效果、粒子和 RGB 流光，也能直接把一张 PNG 挂在名字前面当图片称号。

自带 27 个称号，装好就能用。其中 10 个是图片称号，17 个是文本、BUFF、粒子和 RGB。数据存 SQLite 或 MySQL。

## 安装

1. jar 丢进 `plugins/`，启动。
2. 首次启动会从 Paper 仓库拉 `sqlite-jdbc` 和 `mysql-connector-j` 到服务端根目录 `libraries/`，需要联网一次。离线服可以提前把这两个 jar 放进去。
3. 生成 `plugins/FHMTitles/`：`config.yml`、`titles.yml`、`lang/zh_CN.yml`、`titles.db`、`backUp/`。
4. 游戏里 `/titles` 打开界面。

命令是 `/titles`，别名 `/cht`、`/称号`，不占原版 `/title`。

软依赖：

- Vault：没装的话金币价格买不了，其它支付方式照常
- PlayerPoints：没装的话点券价格买不了
- PlaceholderAPI：没装的话称号文本里的 `%papi_xxx%` 原样显示

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/titles` | 主界面 | `fhmtitles.use` |
| `/titles shop [分类]` | 分类商店，TEXT/BUFF/PARTICLE/IMAGE/RGB | `fhmtitles.shop` |
| `/titles list [分类]` | 聊天栏看已拥有 | `fhmtitles.use` |
| `/titles info <ID>` | 称号详情 | `fhmtitles.use` |
| `/titles equip <ID>` / `unequip` | 佩戴 / 卸下 | `fhmtitles.equip` |
| `/titles toggle <chat\|tab\|head>` | 显示开关 | `fhmtitles.equip` |
| `/titles give <玩家> <ID> [天数]` | 发放，支持离线玩家，-1 为永久 | `fhmtitles.admin` |
| `/titles take <玩家> <ID>` | 回收，支持离线玩家 | `fhmtitles.admin` |
| `/titles card <玩家> <ID> [天数] [数量]` | 发称号卡 | `fhmtitles.admin` |
| `/titles randomcard <玩家> <品质> [数量]` | 发随机称号卡 | `fhmtitles.admin` |
| `/titles coins <玩家> <give\|take\|set> <数量>` | 改称号币 | `fhmtitles.admin` |
| `/titles create <ID> <文本>` | 新建称号 | `fhmtitles.admin` |
| `/titles delete <ID>` | 删除并清理玩家数据 | `fhmtitles.admin` |
| `/titles image <ID> <文件> [字形数] [高度] [ascent]` | 指定图片并重建资源包 | `fhmtitles.admin` |
| `/titles pack [send [玩家\|all]]` | 重建 / 重发资源包 | `fhmtitles.admin` |
| `/titles reload` | 重载配置、称号、语言、资源包 | `fhmtitles.admin` |

后三个只在玩家手里有装备时才有意义，控制台发不了称号卡。

## titles.yml

```yaml
titles:
  warrior:
    text: "<gradient:#ff4b4b:#ffb703>[战神]</gradient> "
    suffix: " <red>⚔</red>"
    tags: [TEXT]                  # TEXT / BUFF / PARTICLE / IMAGE / RGB
    tier: EPIC                    # COMMON / RARE / EPIC / LEGEND / MYTHIC
    icon: NETHERITE_SWORD
    model: 0                      # 自定义模型数据，0 不用
    lore: ["<gray>描述</gray>"]
    enabled: true
    days: 30                      # -1 永久
    weight: 15                    # 随机卡权重
    permission: ""
    buffs: ["SPEED:1"]
    particle: "FLAME"             # 或 DUST:#ff8800:1.2
    particle-count: 10
    animated: true
    palette: ["#ff4b4b", "#ffc93c"]
    image:                        # 图片称号，可选
      file: glyph.png
      glyphs: 1
      height: 16
      ascent: 14
      fallback: "✦[绘]✦ "
    prices:
      - type: MONEY
        amount: 5000
      - type: ITEM
        item: NETHER_STAR
        item-amount: 3
```

`config.yml` 常改的几项：`storage.type`（sqlite / mysql）、`display.chat-format`、`display.tab-format`、`display.head.*`、`resource-pack.*`。改完 `/titles reload`，换存储类型要重启。

## 图片称号

原理是资源包里的自定义 bitmap 字体，插件把这条链路包了：

1. PNG 放 `plugins/FHMTitles/images/`，透明底，高度别超过 32px，上下不要留白。
2. `titles.yml` 里写 `image:`，或者 `/titles image <ID> <文件名> [字形数] [高度] [ascent]`。
3. 插件分配私用区码位（U+E000 起，映射存在 `images/glyphs.yml`），生成 `resourcepack/`，打包成 `resourcepack.zip`，起内置 HTTP 服务对外托管，玩家进服自动下发。

生成的包结构：

```
pack.mcmeta
assets/fhmtitles/font/titles.json
assets/fhmtitles/textures/title/<id>.png
```

图片段单独走 `fhmtitles:titles` 字体，文字段还是原版字体，没动 `minecraft:default`。

几种情况：

- 服务器在 NAT 或面板后面，必须把 `resource-pack.public-url` 填成客户端能访问的地址，不然下不到包。
- `resource-pack.required` 默认 false，玩家拒绝加载只是在图片位置看到 `fallback` 文字；改 true 会把不装包的玩家踢下线。
- 端口默认 8124（8123 留给家具插件），被占会自动往后顺延 10 个，日志里会写真实端口。
- 包内容没变时 SHA-1 不变，客户端走缓存不会重复下载。
- 图片显示成白方块，先看 `/titles pack` 打印的地址能不能在浏览器里下载到 zip。多半是端口冲突或者 `public-url` 填错。

## 几个说明

- 管理界面能改文本、启停、删除；价格、天数、材质这些去 `titles.yml` 改完 reload。
- `reload` 能热更称号、语言、显示格式、经济和奖励配置，`storage` 段要重启。
- 称号 BUFF 是无限时长药水效果，插件记录自己加的是哪一层，摘称号时只清这一层，不会动玩家自己喝的。
- 头顶称号是骑在玩家身上的 TextDisplay，人多可以把 `display.head.view-distance` 调到 16~24。
- 只认自己包的 UUID 回执，别的插件发的资源包加载成功不会被当成我们的。

## 构建

```
gradle build
```

需要 JDK 25，Gradle 会用 toolchain 自己找。

```
cn.fhm.titles
├─ core      称号、显示、BUFF、粒子、奖励
├─ model     数据模型与称号库
├─ storage   SQLite / MySQL
├─ gui       主界面、商店、仓库、管理
├─ shop      四种支付
├─ item      称号卡
├─ resource  图片称号与资源包
├─ listener  玩家、聊天、GUI、卡片、资源包
├─ command   指令
├─ econ      Vault / PlayerPoints 反射桥
└─ papi      PlaceholderAPI 扩展
```
