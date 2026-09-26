# 新·MTR电梯染色模组 / MTR Lift Color

[![license](https://img.shields.io/badge/license-MIT-green)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Loader](https://img.shields.io/badge/loader-Fabric-blue)
![MTR](https://img.shields.io/badge/MTR-4.0.3--4.0.4-orange)

给 [MTR（Minecraft Transit Railway）](https://github.com/MTransitRailway) 的电梯轿厢内部屏幕染色的 Fabric 模组：
支持 **分梯配置**（每台电梯一份 JSON）、调色面板、箭头大小 / 偏移调整，
以及测试中的 **图形化「自动化」** —— 像搭积木一样为「上行时 / 下行时」拼出箭头规则。

A Fabric mod that recolors the interior display screens of
[MTR (Minecraft Transit Railway)](https://github.com/MTransitRailway) lifts.
It supports **per-lift configuration** (one JSON file per lift), a color picker panel,
arrow size / offset tuning, and a work-in-progress **visual "Automation"** mode —
build arrow rules for "going up" / "going down" by snapping blocks together, Scratch-style.

## 功能 / Features

- **电梯染色 / Lift coloring**
  - 右键 MTR 电梯选择"调色"打开圆角半透明面板：hex 输入、RGB 滑块、预设色块、实时预览。
  - Right-click an MTR lift and pick "color" to open a rounded, translucent panel:
    hex input, RGB sliders, preset swatches and a live preview.
- **箭头配置 / Arrow settings**
  - 单独一页调节轿厢屏幕上箭头的大小与横向 / 纵向偏移。
  - A dedicated tab for the arrow's size and horizontal / vertical offset on the car display.
- **自动化（测试功能）/ Automation (beta)**
  - 顶部"自动化"入口 → 点"箭头"进入图形化编辑器：
    在「上行时 / 下行时」两条链上，把「箭头方向:向上 / 向下」和「箭头偏移X / Y」积木
    拖进链里（块与块之间用长直线连接），例如
    `上行时 ─ 箭头偏移X:0.12 ─ 箭头方向:向上`。
  - 滑块积木：**按住左右拖 = 调数值；长按（或竖直拿起）= 拖进 / 拖出链子**，跟图形化编程一样。
  - 「应用到电梯」按范围（仅当前维度 / 全部已存电梯）批量下发，服务器落盘并同步给所有玩家。
  - Open **Automation → Arrow**: snap blocks ("arrow up / down", "offset X / Y") into a
    "when going up" / "when going down" chain connected by long lines, e.g.
    `Going up ─ Offset X: 0.12 ─ Arrow: up`.
  - Slider blocks support two gestures: **press & move sideways = slide the value,
    hold (long-press) = pick up and drag** — just like visual programming.
  - "Apply" pushes the template to all lifts in scope; the server persists and broadcasts it.
- **导出 / 导入 / Export & import**
  - 导出把全部自动化配置集中复制到存档的 `mtrliftcolor/output/<十六进制id>.json`，
    不用逐层翻文件夹；导入反向读回。结果在聊天栏提示。
  - Export gathers every automation config into `<save>/mtrliftcolor/output/<hex-id>.json`
    (no folder digging); import reads them back. Results are reported in chat.
- **联机一致 / Multiplayer-safe**
  - 所有配置由服务器权威保存并同步；单人、局域网、专用服务器表现一致。
  - The server is authoritative for all data; single-player, LAN and dedicated servers behave the same.

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

自动化 JSON 示例 / Example automation file:

```json
{
  "version": 1,
  "liftId": "3675",
  "liftName": "Central Plaza Lift",
  "hexId": "0000000000000e5a",
  "dimNamespace": "minecraft",
  "dimPath": "overworld",
  "up":   [ { "t": 0, "v": 0.12 }, { "t": 2 } ],
  "down": [ { "t": 1, "v": -0.20 }, { "t": 3 } ]
}
```

`t`：0=横向偏移 offsetX，1=纵向偏移 offsetY，2=箭头向上 arrow up，3=箭头向下 arrow down。

## 安装 / Installation

1. 安装 Minecraft **1.20.1** + [Fabric Loader](https://fabricmc.net/use/) ≥ 0.16。
2. 安装 [Fabric API](https://modrinth.com/project/fabric-api) `0.92.x+1.20.1`。
3. 安装 [MTR](https://github.com/MTransitRailway/MTR) `4.0.3`–`4.0.4`（Fabric 版）。
4. 把 `mtrliftcolornew-<version>.jar` 放进 `mods/`。
   **客户端与服务端都要装**（联机时两边都需要同步协议）。

- Minecraft **1.20.1** with [Fabric Loader](https://fabricmc.net/use/) ≥ 0.16
- [Fabric API](https://modrinth.com/project/fabric-api) `0.92.x+1.20.1`
- [MTR](https://github.com/MTransitRailway/MTR) `4.0.3`–`4.0.4` (Fabric build)
- Drop `mtrliftcolornew-<version>.jar` into `mods/` — **on both client and server**
  (multiplayer relies on the sync protocol).

## 使用 / Usage

1. 用 MTR 的电梯生成器放好电梯。
2. 右键电梯 → 下半圆"调色"打开面板；上半圆仍然进入 MTR 原版编辑器。
3. 面板顶部第三段"自动化" → "箭头" → 拼积木链 → 返回后"应用到电梯"。
4. 导出/导入在自动化界面里点按钮，文件在存档 `mtrliftcolor/output/`，结果看聊天栏。

1. Place a lift with MTR's lift generator.
2. Right-click the lift → bottom half opens the color panel; top half still opens MTR's own editor.
3. Panel header → **Automation** → **Arrow** → build a chain → go back → **Apply**.
4. Export / Import buttons write to and read from `<save>/mtrliftcolor/output/`;
   feedback appears in chat.

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
