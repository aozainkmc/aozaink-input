# aozaink-input

开发工程名为 `aozaink-input`；面向玩家的发布产物名为 `molu-input`。

官方样例输入模块。当前玩家可见入口有两条：黄符三格书写 UI，以及原版白纸触发的临时施写。它负责采集笔迹、提交识别，并在成符时做**客观评分 → 品质评级**写进黄符；不实现具体玩法效果（交给 gameplay）。

## 职责

- 采集黄符三格内的鼠标笔迹
- 采集原版白纸临时施写的世界空间笔迹
- 构造 `InkRecognitionRequest` 和 `InkSource`
- 通过统一网络路径把 `InkTrace` 提交给服务端，由服务端识别、评分和成符
- 提供黄符方块、黄符物品和三格 UI 的基础交互
- 成符时对每格做**客观评分**（`TalismanScorer`：置信度立方 / 笔画数 / 点数 / 书写时长，与校准基线比对得综合分）并定**品质等级**，写进黄符 `CustomData` 的 `aozaink:grade<格>`；开发模式下 `InkScoreListener` 在聊天打印评分明细
- 广播中性的输入结果信号（如成符、尾修暴乱），不在 input 内解释成玩法或成就

## 不负责

- ONNX 识别逻辑（交给 core）
- 具体技能、伤害、状态、世界效果（交给 gameplay 模块）
- 成就/Advancement 语义（交给 gameplay 模块）

## 黄符三格 UI

黄符是横向三格版面：前两格为咒位，第三格为尾修槽。没有上一格、下一格、留空按钮或额外确认列表；每格预览的 top1 就是该格最终提交字。

这是**格式协议**，不是全生态字表协议。Input 负责三格数据、识别、评分和尾修稳定性流程；玩法模块分别拥有前两格可写的基础字、第三格可写的尾修字及其效果。当前 `强/续/广/穿` 只是 Sigillum 首发尾修；未来 Arsenal 等模块必须登记自己的尾修字，而不是复用它们。

每格独立保存笔迹和识别状态：

```java
InkTrace trace;
boolean dirty;
String recognizedGlyph;
float confidence;
int simplifiedStrokeCount;
```

输入规则：

- 鼠标在某格按下：该格开始一笔。
- 拖动：向该格当前笔追加点。
- 松开：结束该格这一笔。
- 多笔画必须保留为多个 stroke，不把笔画连成一条线。
- 某格停止书写 0.7 秒后，只自动识别该格。
- 继续在该格书写时，该格进入 dirty，旧 top1 结果作废。
- 没有有效 stroke/point 的格提交为空字符串。

提交规则：

- 点击“成符”时，对三个格子做最终检查。
- 有笔迹但未识别的格子会立即识别。
- 空格提交为 `""`。
- 第三格有尾修字（`强/续/广/穿`）时，先做独立稳定性判定：基础成功率 80%，以该字校准基线笔画数为标准，每差一笔扣 5%。稳定性失败则不进入成符分类/品质逻辑。
- 尾修失败会移除黄符方块，在方块中心播放 3 秒朱砂红粒子预警，随后触发半径 5 格、无方块破坏的爆炸伤害，并以 action bar 显示“尾修失败，符力暴乱”。
- 得到 `slot1/slot2/slot3` 后，在单人 integrated server 上生成黄符物品并移除黄符方块。
- 背包满时物品掉落。

判定规则：

| 类型 | 条件 |
|------|------|
| 指定符 | `slot1 = 1-9`, `slot2 = 技能字`, `slot3 = 空` |
| 刻印符 | 前两格包含且只包含一个 `刻`；除 `刻` 外最多一个技能字，或 1-2 个合法修饰字用于操作已有刻印；第三格只能为空或修饰字 |
| 组合符 | 三格内包含至少一个技能字，可混入修饰字；不能包含数字或 `刻`；重复字、广/穿同时出现等结构由玩法侧判废 |
| 废符 | 其他格式 |

当前已实现的 Sigillum 语法：12 个印契基础字 `镇 封 退 引 火 雷 护 净 斩 明 吸 魄`，结构字 `刻` 与印契尾修 `强 续 广 穿`；数字 `一..九`（暂兼容到 `1..9`）由 Input 用于指定/绑定。未来模块的语法字表由模块注册，详见 [`../GLYPH_OWNERSHIP.md`](../GLYPH_OWNERSHIP.md)。Input 只提交实际轨迹，Core 自动调用统一模型的轨迹入口。

## 黄符方块和物品

- `aozaink_input:yellow_talisman`：空白黄符是可放置 BlockItem，只能放在工作台上。最大堆叠 **64**。
- 合成配方：3 × `minecraft:paper` + 1 × `minecraft:yellow_dye` → 6 × `aozaink_input:yellow_talisman`。
- 已成符黄符写有 `CustomData`，不能再放置；gameplay 右键释放/使用。
- 右键黄符方块：打开三格书写 UI。
- 左键破坏黄符方块：所有模式都掉回黄符物品（手动掉落，不依赖战利品表）。
- 方块贴图来自项目根目录 `yellow_talisman_block2.png`，物品贴图 `scroll2.png`。
- 成符后产出 `aozaink_input:yellow_talisman`，三格字、符类型、每格**品质**（`aozaink:grade<格>`）写入 `CustomData`；tooltip 显示「类型 / 品质 / 字」。
- **叠加规则**：品质是离散值（非每次书写的浮点分），所以「同组合 + 同类型 + 同品质」的符 NBT 一致，可叠加到 64。

## 原版白纸临时施写

临时施写使用原版 `minecraft:paper`，不需要毛笔或法杖。

- 主手拿原版纸右键：在玩家面前展开一张放大的白纸书写面。
- 左键按住/拖动：在白纸面上写字，保留多 stroke 结构。
- 再次右键：收束白纸并提交一次轨迹识别。
- 当前单人版本识别后只广播/附着 `InkMark` 并显示 top1，不产生伤害、状态或方块效果。
- 白纸临时施写固定提交轨迹和基础强度；法杖 tier 不参与普通玩家入口。

## 与 core 的交互

```java
AozaiInkCoreApi.registerGlyphs(TALISMAN_GLYPHS);
```

- 黄符三格和原版白纸临时施写都使用 `SOURCE_TRAJECTORY = "classic_taiji_traj"` 作为来源元数据。
- 识别请求构造为 `InkRecognitionRequest { trace, null, candidates, ttl, source }`。
- Core 根据请求实际载荷自动使用统一 ONNX 的轨迹输出；`sourceId` 不再承担引擎路由职责。

## 开发模式

`aozaink-input` 注册 `/aozaink_input dev` 命令（需要 op 权限），用于切换当前游戏会话的开发模式。未来所有调试/管理子命令都需要先开启此模式才能使用。

## 构建依赖

```groovy
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    implementation project(":aozaink-core")
}
```

只依赖 core，不依赖 gameplay 模块。Input 同时提供 Molu 共享交互层：指定符绑定、白纸快速吟唱解析、默认 M 菜单，以及供玩法模块注册字典说明和栏目数据的 `MoluMenuRegistry`。依赖方向始终是 gameplay → Input，不反向依赖。
