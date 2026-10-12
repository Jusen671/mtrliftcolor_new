# 新·MTR电梯染色模组 / MTR Lift Color

[![license](https://img.shields.io/badge/license-MIT-green)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Loader](https://img.shields.io/badge/loader-Fabric-blue)
![MTR](https://img.shields.io/badge/MTR-4.0.3--4.0.5-orange)

> 当前版本 / Current version: **1.0.2**（兼容 MTR 4.0.3 – 4.0.5）

给 [MTR（Minecraft Transit Railway）](https://github.com/MTransitRailway) 的电梯轿厢内部屏幕染色的 Fabric 模组：
支持 **分梯配置**（每台电梯一份 JSON）、调色面板、箭头与楼层文字的大小 / 偏移调整、
手持驾驶钥匙开电梯门，以及测试中的 **图形化「自动化」** —— 像搭积木一样为「上行时 / 下行时」拼出显示规则。

A Fabric mod that recolors the interior display screens of
[MTR (Minecraft Transit Railway)](https://github.com/MTransitRailway) lifts.
It supports **per-lift configuration** (one JSON file per lift), a color picker panel,
arrow **and floor-number text** size / offset tuning, opening lift doors with MTR's driver key,
and a work-in-progress **visual "Automation"** mode —
build display rules for "going up" / "going down" by snapping blocks together, Scratch-style.

## 功能 / Features

- **电梯染色 / Lift coloring**
  - 右键 MTR 电梯选择"调色"打开圆角半透明面板：hex 输入、RGB 数值框、预设色块、实时预览。
  - Right-click an MTR lift and pick "color" to open a rounded, translucent panel:
    hex input, RGB number fields, preset swatches and a live preview.
- **电梯显示设置 / Display settings**
  - 调色面板顶部的"电梯显示设置"页，把轿厢屏幕上**箭头**和**楼层文字**的大小、横向 / 纵向偏移放在一起调，
    还包含**三角钥匙检修门**的开 / 关时长。
  - 数值一律用输入框：手机上点一下直接打字，也能按住左右拖动改值。
  - 图形化编辑器里的可拖放区域也比原来更大，积木更好拿。
  - A dedicated "Display settings" tab tunes the **arrow** and the **floor-number text**
    together: size and horizontal / vertical offset. Every value is a number field —
    tap to type on mobile, or press & drag sideways to change it.
- **三角钥匙检修开门 / Maintenance door opening with the driver key**
  - 手持 MTR 自带的车辆驾驶钥匙（基础 / 高级 / 创造都行）右击电梯层门，门**缓缓打开并保持开着**；
    再点一次同一扇门，门**缓缓关严**并解除联锁。本模组**不新增任何钥匙物品**。
    潜行右击不受影响，照常拿钥匙去点车辆。
  - 门开着 = 安全回路断开：这台电梯被**就地钉住**（速度归零、轨道进度锁死），
    绝不"平层"跳到最近楼层。轿厢里已经按下的楼层会被暂时"借走"保存，
    门关严之后按原顺序继续跑完。
  - 电梯不在本层时照样能打开那一层的层门（现实里的井道检修做法），门不会抖。
  - 开 / 关时长是**分梯设置**（面板"电梯显示设置 → 三角钥匙检修门"，默认 2600 ms），
    切一次定一次、随包广播，所有玩家看到的开合速度完全一致。
  - 联机表现：服务器验包（区块已加载、确实是电梯层门、玩家够得着）之后
    重算整组门坐标并广播给所有人；中途进服的玩家会收到一份"当前还开着哪些门"的快照。
  - 与 YTE（云筑交通拓展）等自带平层的附属模组共存：本模组只覆写门开度的**读取**、
    不去写门方块实体的字段，也不会把别的模组设置的冷却值一起改掉。
  - Right-click a landing door with MTR's built-in driver key (basic / advanced / creative) and it
    **opens slowly and stays open**; right-click it again and it **closes** and the interlock clears.
    **No new key item is added.** Sneaking still uses the key on vehicles.
    While a door is open the safety circuit counts as broken, so that lift is **pinned in place**
    (speed zeroed, rail progress locked) — never leveled to the nearest floor. Floors already
    pressed inside the car are borrowed aside and run in order once the doors have closed.
    You can also open a door on a floor the car is *not* at (real shaft-inspection behaviour),
    without jitter. Open / close duration is a **per-lift setting** (panel → Display settings →
    maintenance door, 2600 ms by default), fixed at toggle time and broadcast, so every player sees
    the same speed. On a server the request is validated, the whole door group is resolved
    server-side and broadcast; players joining mid-way get a snapshot of which doors are open.
    Coexists with leveling add-ons such as YTE: the mod overrides the *read* of the door value and
    never rewrites a cool-down another mod set.
- **电梯维护围挡 / Lift maintenance barrier**
  - 隐藏测试方块，两种折角，不在创造物品栏里，只能用指令获取：
    - `/give @s mtrliftcolor:lift_maintenance_barrier_90`（或旧名 `..._barrier`）—— 90° 围挡，左右两片各折 45°；
    - `/give @s mtrliftcolor:lift_maintenance_barrier_45` —— 45° 围挡，左右两片各折 22.5°。
  - 模型按玩家改版的 `barrier_45.bbmodel` 重做：顶部横管贯穿三段、两端向下弯出小钩，
    折页与两端各一根立柱，底下是扁平底座；贴图用玩家的新版黄布 + 红圈禁止符号，
    布面上的字走游戏自带字体实时绘制。
  - A hidden test block in two fold angles, not in the creative menu:
    `/give @s mtrliftcolor:lift_maintenance_barrier_90` (45° per side) and
    `/give @s mtrliftcolor:lift_maintenance_barrier_45` (22.5° per side); the old name
    `..._barrier` still gives the 90° one. The model follows the player-revised
    `barrier_45.bbmodel`; the yellow-fabric texture is the player's, and the printed
    warnings are drawn live with Minecraft's own font.
- **自动化（测试功能）/ Automation (beta)**
  - 顶部"自动化"入口 → 点"电梯显示设置"进入图形化编辑器：
    在「上行时 / 下行时」两条链上，把箭头和楼层文字的积木
    （箭头方向 / 大小 / 偏移，楼层文字大小 / 偏移）拖进链里（块与块之间用长直线连接），例如
    `上行时 ─ 箭头偏移X:0.12 ─ 箭头方向:向上`。
  - 数值积木也可以直接点开输入框打字，手机上照样好用。
  - 「应用到电梯」按范围（仅当前维度 / 全部已存电梯）批量下发，服务器落盘并同步给所有玩家。
  - Open **Automation → Display settings**: snap arrow and floor-text blocks
    ("arrow up / down", size, offset X / Y; floor-text size / offset) into a
    "when going up" / "when going down" chain connected by long lines, e.g.
    `Going up ─ Offset X: 0.12 ─ Arrow: up`.
  - "Apply" pushes the template to all lifts in scope; the server persists and broadcasts it.
- **导出 / 导入 / Export & import**
  - 导出把每台电梯的**完整设置**（显示参数 + 自动化积木链）各写成一个文件放进存档的
    `mtrliftcolor/output/<十六进制id>.json`，一梯一文件，不用逐层翻文件夹；导入反向读回。
    结果在聊天栏提示。
  - 导入也认旧版"只有积木链"的导出文件，那种情况下只恢复积木链、不动配色。
  - Export writes one file per lift into `<save>/mtrliftcolor/output/<hex-id>.json`, containing
    both the display settings and the automation chains (no folder digging); import reads them
    back. Legacy automation-only exports still import — they just leave the colors alone.
- **联机一致 / Multiplayer-safe**
  - 所有配置由服务器权威保存并同步；单人、局域网、专用服务器表现一致。
  - The server is authoritative for all data; single-player, LAN and dedicated servers behave the same.

## 1.0.2 修复的问题 / What's fixed in 1.0.2

- **三角钥匙开门：整套重做（这一轮的重点）**
  - 以前"借 MTR 的到站冷却值把车停住"的做法被推翻：冷却值是别的模组（YTE）也在读写的字段，
    借它停车就会出现"门还开着电梯又自己开走"、以及 YTE 平层被触发这一类问题。
  - 现在改成**检修联锁**：以门方块坐标为主键维护一张检修状态表（`LiftMaintenanceRegistry`），
    只要一台电梯还有检修门没关严，就在 `Lift.tick` 里把速度归零、轨道进度钉回进检修那一刻，
    一拍一拍地按回去，并在这一拍结束时再钉一次（防别的模组在同一拍里改动）。
    车停在哪儿就是哪儿，不会跳层。
  - 轿厢里已按下的楼层在进检修时被"借走"、退出检修时按原顺序还回队列，检修期间新按的排在后面。
    不清空、也不留在队列里被 MTR 的"到站即出队"吃掉，所以门关严之后之前按的楼层照样跑完。
  - 层门开度改为**覆写读取**（门方块实体的 `getDoorValue`），不再往字段里写。
    抽搐的根因就是多写入者同一帧互相覆盖；只覆写读取之后，谁写都不影响渲染看到的值。
    开度上限跟 MTR 的门模型一致取 0.75（全开值），两边不会出现"一个开到 100% 另一个只到 75%"。
  - 一扇门的两列 × 两层作为一个整体记录（`LiftDoorGroup` 用 MTR 自己的 `getSideDirection` 解析），
    不会出现"一半开一半关"。开到位之后一直保持，再点一次才关。
  - 新增**双向同步**：客户端本地立刻开播（点下去一定有反馈），服务器验包（区块已加载、
    确实是电梯层门、玩家够得着）后重算整组门坐标并广播给所有玩家，中途进服的玩家补收快照。
    以前只有点钥匙那名玩家看得见开门动画，别人那边毫无反应。
  - The old trick of abusing MTR's arrival cool-down to park the car is gone: that field is shared
    with other mods such as YTE, which is why the lift could run off with the doors still open.
    The key now writes a maintenance record per door block position; while any of a lift's doors
    are not fully closed, `Lift.tick` zeroes its speed and re-pins its rail progress every tick, so
    it stops exactly where it was. Queued car buttons are borrowed aside and returned in order when
    the doors close, so previously pressed floors still run. The landing-door value became a *read*
    override on the block entity instead of a write, which removes the multi-writer jitter, capped
    at MTR's own full-open value 0.75 so the two models agree. Both columns and both rows of a door
    are handled as one group, a door stays open until toggled again, and the whole exchange is
    synchronised both ways (server-validated broadcast plus a join-time snapshot), so every player
    sees the same animation instead of only the one holding the key.
- **新增分梯设置：检修门开 / 关时长**
  - 面板"电梯显示设置"页多出"三角钥匙检修门"一组，可分别设开门与关门时长（600 – 15000 ms，
    默认 2600 ms），跟这台电梯的其余设置一起存档、一起同步。
  - 时长在切换那一刻定死并随包广播，所有玩家看到的开合速度一致（不做随机时长，
    随机出来的忽快忽慢在玩家眼里只是不稳定）。
  - The panel gained a maintenance-door group with separate open / close durations per lift
    (600 - 15000 ms, 2600 by default), persisted and synced like the other per-lift settings.
    The duration is fixed at toggle time and broadcast, so everybody sees the same speed.
- **存档数据结构整理**
  - 服务端内存原来是两张表（配色一张、自动化一张），各自重复存一份电梯名 / 十六进制 id / 维度，
    改配色与改积木链分别更新自己那份，时间一长两边就可能不一致。
    现在合并成**一台电梯一条记录**，元数据只有一个主人；读盘也按这个模型走（先显示后积木链，
    不受目录遍历顺序影响，同一台电梯绝不会被拆成两条记录）。磁盘目录结构完全没变，旧存档直接读。
  - 导出改成**一梯一文件**（`version: 2`，同时包含 `display` 与 `up`/`down`），
    导入同时认旧版只含积木链的文件（那种情况只恢复积木链、不动配色）。
  - The server kept two parallel maps (colours and automation), each duplicating lift name / hex id
    / dimension, so the two could drift apart. There is now a single record per lift with one owner
    for the metadata, and world loading follows the same model (display files first, then chains, so
    traversal order can never split one lift into two records). The on-disk layout is unchanged, so
    old worlds load as-is. Exports become one file per lift (`version: 2`: display + chains);
    legacy automation-only exports still import and simply leave the colours alone.

- **电梯维护围挡换成玩家改版**
  - 贴图用玩家这一轮上传的新版黄布 + 红圈禁止符号（1024×1024）。
  - 模型按玩家改版的 `barrier_45.bbmodel` 重做；90° 版"照着 45° 改"，只把左右两片布的折角
    从 22.5° 换成 45°，其余几何完全一致。
  - The barrier now uses the player's new texture and the player-revised `barrier_45.bbmodel`;
    the 90° version is the same model with the side folds changed from 22.5° to 45°.

## 文件结构 / Data Layout

配置位于**服务器存档根目录**（单人游戏就是你的世界文件夹）：
All files live in the **server save root** (in single-player, your world folder):

```text
<存档 / save>/mtrliftcolor/
├── minecraft/
│   ├── overworld/lift_configs/
│   ├── the_nether/lift_configs/          # 读档即自动创建三大维度目录，与 MTR 原版一致
│   └── the_end/lift_configs/             #   dimension folders are created on world load, like MTR
│       └── <两位短码 2-char code>/       # 按电梯 id 生成的分桶目录 bucketed by lift id
│           ├── <十六进制id>.json          # 配色 / per-lift color config
│           └── auto/
│               └── <十六进制id>.json      # 自动化积木链 / automation chains
└── output/
    └── <十六进制id>.json                  # 集中导出目录 / flat export folder
```

集中导出文件示例（一梯一文件，`version: 2`）/ Example export file (one per lift):

```json
{
  "version": 2,
  "liftId": "3675",
  "liftName": "Central Plaza Lift",
  "hexId": "0000000000000e5a",
  "dimNamespace": "minecraft",
  "dimPath": "overworld",
  "display": {
    "displayColor": -16776961,
    "arrowScale": 1.0, "arrowOffsetX": 0.0, "arrowOffsetY": 0.0, "arrowDirection": 0,
    "textScale": 1.0, "textOffsetX": 0.0, "textOffsetY": 0.0,
    "doorOpenMs": 2600, "doorCloseMs": 2600
  },
  "up":   [ { "t": 0, "v": 0.12 }, { "t": 2 } ],
  "down": [ { "t": 1, "v": -0.20 }, { "t": 3 } ]
}
```

存档里的两份文件仍然各自独立（`<hexid>.json` 只有显示字段、`auto/<hexid>.json` 只有积木链），
只有集中导出的时候才合成上面这一份。/ Inside the save the two files stay separate; only
the flat export merges them into one document per lift.

`t`：0=箭头横向偏移，1=箭头纵向偏移，2=箭头向上，3=箭头向下，
4=楼层文字大小，5=楼层文字横向偏移，6=楼层文字纵向偏移，7=箭头大小（只有带数值的块才有 `v`）。

## 安装 / Installation

1. 安装 Minecraft **1.20.1** + [Fabric Loader](https://fabricmc.net/use/) ≥ 0.16。
2. 安装 [Fabric API](https://modrinth.com/project/fabric-api) `0.92.x+1.20.1`。
3. 安装 [MTR](https://github.com/MTransitRailway/MTR) `4.0.3`–`4.0.5`（Fabric 版）。
4. 把 `mtrliftcolornew-<version>.jar` 放进 `mods/`。
   **客户端与服务端都要装**（联机时两边都需要同步协议）。

- Minecraft **1.20.1** with [Fabric Loader](https://fabricmc.net/use/) ≥ 0.16
- [Fabric API](https://modrinth.com/project/fabric-api) `0.92.x+1.20.1`
- [MTR](https://github.com/MTransitRailway/MTR) `4.0.3`–`4.0.5` (Fabric build)
- Drop `mtrliftcolornew-<version>.jar` into `mods/` — **on both client and server**
  (multiplayer relies on the sync protocol).

## 使用 / Usage

1. 用 MTR 的电梯生成器放好电梯。
2. 右键电梯 → 下半圆"调色"打开面板；上半圆仍然进入 MTR 原版编辑器。
3. 面板顶部第三段"自动化" → "电梯显示设置" → 拼积木链 → 返回后"应用到电梯"。
4. 导出/导入在自动化界面里点按钮，文件在存档 `mtrliftcolor/output/`，结果看聊天栏。
5. 想检修开门就拿 MTR 的车辆驾驶钥匙右击电梯层门：门缓缓打开并**保持开着**，电梯就地停住；
   再点一次同一扇门，门缓缓关严，电梯接着跑之前按下的楼层。
   开 / 关快慢在面板"电梯显示设置 → 三角钥匙检修门"里按梯调。
6. 围挡方块用 `/give @s mtrliftcolor:lift_maintenance_barrier_90`（90°）或
   `/give @s mtrliftcolor:lift_maintenance_barrier_45`（45°）获取。

1. Place a lift with MTR's lift generator.
2. Right-click the lift → bottom half opens the color panel; top half still opens MTR's own editor.
3. Panel header → **Automation** → **Display settings** → build a chain → go back → **Apply**.
4. Export / Import buttons write to and read from `<save>/mtrliftcolor/output/`;
   feedback appears in chat.
5. To open a door for inspection, right-click the landing door with MTR's driver key: it opens
   slowly and **stays open** while the lift is pinned in place. Right-click the same door again and
   it closes slowly, after which the lift resumes the floors pressed earlier. Speed is tuned per
   lift under **Display settings → maintenance door**.
6. Get a barrier with `/give @s mtrliftcolor:lift_maintenance_barrier_90` (90°) or
   `/give @s mtrliftcolor:lift_maintenance_barrier_45` (45°).

## 开发 / Building

```bash
./gradlew build
# 产物 / output: build/libs/mtrliftcolornew-<version>.jar
```

- 源码：`src/main/java`（入口）与 `src/mtr/java`（MTR 集成、网络、界面、mixin）。
- Source: `src/main/java` (entrypoints) and `src/mtr/java` (MTR integration, networking, GUI, mixins).

## 已知限制 / Known Limitations

- 自动化界面操作的是服务器已存有配色记录的电梯；从没存过配置的电梯先保存一次配色即可纳入管理。
  The Automation UI targets lifts that already have a saved config; save a color once to enroll a lift.
- "自动化"为测试功能，链的形态与文件字段可能随版本调整。
  Automation is experimental; chain format and file fields may change.

## 许可 / License

[MIT](LICENSE) © xiao
