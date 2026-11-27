# FHMTitles

Minecraft 称号插件，支持 Paper / Purpur 26.3 和 Java 25。

称号可以是纯文本，也可以带药水效果、粒子、RGB 渐变，或者在名字前面挂一个小图标。内置 27 个称号，数据可以存 SQLite，也可以存 MySQL。

## 安装

1. 把 `FHMTitles-1.2.1.jar` 放进 `plugins/`，启动。
2. 首次启动会从 Paper 仓库下载 `sqlite-jdbc` 和 `mysql-connector-j`，需要联网一次。离线服可以提前放进服务端根目录的 `libraries/`。
3. 生成 `plugins/FHMTitles/`，里面有 `config.yml`、`titles.yml`、`lang/zh_CN.yml`、`titles.db` 和 `backUp/`。
4. 进服输入 `/titles` 打开界面。

命令别名 `/cht`、`/称号`，不会和原版的 `/title` 冲突。

软依赖：

- Vault：没装时金币支付不可用，其他支付方式正常。
- PlayerPoints：没装时点券支付不可用。
- PlaceholderAPI：没装时称号文本里的 `%papi_xxx%` 会原样显示。

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/titles` | 主界面 | `fhmtitles.use` |
| `/titles shop [分类]` | 分类商店 | `fhmtitles.shop` |
| `/titles list [分类]` | 查看已拥有的称号 | `fhmtitles.use` |
| `/titles info <ID>` | 称号详情 | `fhmtitles.use` |
| `/titles equip <ID>` / `unequip` | 佩戴或卸下 | `fhmtitles.equip` |
| `/titles toggle <chat\|tab\|head>` | 开关显示位置 | `fhmtitles.equip` |
| `/titles give <玩家> <ID> [天数]` | 发放，支持离线，-1 为永久 | `fhmtitles.admin` |
| `/titles take <玩家> <ID>` | 回收 | `fhmtitles.admin` |
| `/titles card <玩家> <ID> [天数] [数量]` | 发称号卡 | `fhmtitles.admin` |
| `/titles randomcard <玩家> <品质> [数量]` | 发随机称号卡 | `fhmtitles.admin` |
| `/titles coins <玩家> <give\|take\|set> <数量>` | 改称号币 | `fhmtitles.admin` |
| `/titles create <ID> <文本>` | 新建称号 | `fhmtitles.admin` |
| `/titles delete <ID>` | 删除称号并清理玩家数据 | `fhmtitles.admin` |
| `/titles image <ID> <文件> [字形数] [高度] [ascent]` | 指定图片并重建资源包 | `fhmtitles.admin` |
| `/titles pack [send [玩家\|all]]` | 重建或重发资源包 | `fhmtitles.admin` |
| `/titles reload` | 重载配置、称号、语言、资源包 | `fhmtitles.admin` |

## titles.yml

```yaml
titles:
  warrior:
    text: "<gradient:#ff4b4b:#ffb703>[战神]</gradient> "
    suffix: " <red>⚔</red>"
    tags: [TEXT]
    tier: EPIC
    icon: NETHERITE_SWORD
    model: 0
    lore: ["<gray>描述</gray>"]
    enabled: true
    days: 30
    weight: 15
    permission: ""
    buffs: ["SPEED:1"]
    particle: "FLAME"
    particle-count: 10
    animated: true
    palette: ["#ff4b4b", "#ffc93c"]
    image:
      file: icon_star.png
      glyphs: 1
      height: 16
      ascent: 13
      fallback: "✦[星辰]✦ "
    prices:
      - type: MONEY
        amount: 5000
      - type: ITEM
        item: NETHER_STAR
        item-amount: 3
```

`tags` 可选 `TEXT`、`BUFF`、`PARTICLE`、`IMAGE`、`RGB`。`tier` 可选 `COMMON`、`RARE`、`EPIC`、`LEGEND`、`MYTHIC`。`days` 为 -1 表示永久。

`config.yml` 常改的几项：`storage.type`、`storage.flush-seconds`、`display.chat-format`、`display.tab-format`、`display.head.*`、`display.animation-frames`、`title.buff-disabled-worlds`、`resource-pack.*`。改完 `/titles reload`；换存储类型和 `flush-seconds` 需要重启。

## 图片称号

图片走资源包里的自定义 bitmap 字体，插件会自动分配私用区码位并打包。步骤：

1. PNG 放在 `plugins/FHMTitles/images/`，透明底，尺寸和 `height` 一致。内置图标是 16×16、`height: 16`，缩放会糊。
2. 在 `titles.yml` 里写 `image:`，或者执行 `/titles image <ID> <文件名> [字形数] [高度] [ascent]`。
3. 插件生成 `resourcepack/`，打包成 zip，起内置 HTTP 服务，玩家进服自动下载。

包结构：

```
pack.mcmeta
assets/fhmtitles/font/titles.json
assets/fhmtitles/textures/title/<id>.png
```

图标字体是独立的 `fhmtitles:titles`，没改 `minecraft:default`。

## 说明

- 管理界面能改文本、启停、删除；价格、天数、材质这些改 `titles.yml` 后 reload。
- 称号 BUFF 是无限时长的药水效果。插件只移除自己加的那一层，不会动玩家自己喝的药水。
- 头顶称号是骑在玩家身上的 TextDisplay。人多时把 `display.head.view-distance` 调到 16 到 24。
- 资源包端口默认 8124，8123 留给家具插件，被占会往后顺延。
- 服务器在 NAT 或面板后面时，`resource-pack.public-url` 要填客户端能访问的地址。
- `resource-pack.required` 默认 false。玩家不装包只会在图片位置看到 fallback 文字；改 true 会把不装包的玩家踢下线。
- 图片显示成白方块，先看 `/titles pack` 打印的地址在浏览器里能不能下载到 zip。

## 构建

```powershell
.\gradlew.bat build --console=plain
```

需要 JDK 25。产物在 `build/libs/`。
