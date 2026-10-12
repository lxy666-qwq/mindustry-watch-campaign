# 编号预设 245（sector-serpulo-245）

读取来源：归档的0.1.0原版程序，基于Mindustry v160.7；本页列出初始配置，未推进关卡脚本。

| 设置 | 原始配置 |
| --- | --- |
| 星球 | serpulo |
| 当前落点编号 | 245 |
| 源码原始位置 | 245 |
| 地图文件 | `maps/serpulo/hidden/245.msav` |
| 地图尺寸 | 350 × 600格 |
| 难度字段 | 10 |
| 初始模式 | 进攻 |
| captureWave / winWave | 0 / 0 |
| 启用波次 | 是 |
| 波间时间字段 | 150秒 |
| initialWaveSpacing字段 | 3300秒 |
| 初始战争迷雾 | 否 |
| alwaysUnlocked字段 | 否 |
| requireUnlock字段 | 否 |
| isHidden字段（无介绍） | 是 |
| showHidden字段 | 否 |
| 护盾前置区块 | 无 |
| 科技树父节点 | 无 |
| 额外解锁目标 | 无 |
| 初始地图扫描到的资源 | 冷冻液、水、沙、煤炭、石油、钍、钛、铅、铜 |
| 地表主要类型（格数） | 金属地基 1(64745)、沙子(38298)、金属地基 7(12890)、金属地基 12(12669)、黑沙(12408)、金属地基 2(6299) |
| 初始天气规则 | 降雨、雾 |
| 初始敌方核心数 | 21 |
| 初始出生标记数 | 1 |
| 波次编组数 | 18 |
| 地图目标数 | 5 |
| 世界处理器数 | 28 |

## 初始敌人编组

开始与结束列换算为玩家看到的波次编号（源码下标加1）。数量是每个适用出生点的编组初始数量；单位增长、多个出生点、地图脚本会影响实战总量。未设结束值的编组标为“持续”，但关卡仍受胜利条件约束。波次未启用时，存在默认编组也不会自动出兵。

| 单位 | 开始波 | 结束波 | 间隔波 | 初始数量 | 增长参数scaling | 数量上限 | 初始护盾/每波增长 | 状态 |
| --- | ---: | ---: | ---: | ---: | --- | ---: | --- | --- |
| 死星（corvus） | 1 | 持续 | 3 | 2 | 1.66667 | 12 | 2 / 175 | none |
| 血蛭（spiroct） | 1 | 持续 | 1 | 5 | 0.45045 | 67 | 3 / 50 | none |
| 天蝎（toxopid） | 1 | 持续 | 2 | 3 | 1.33333 | 14 | 1 / 150 | none |
| 星辉（flare） | 1 | 持续 | 2 | 2 | 0.526316 | 10 | 1 / 25 | none |
| 天垠（horizon） | 1 | 持续 | 2 | 1 | 0.769231 | 5 | 15 / 30 | none |
| 幻型（poly） | 1 | 持续 | 3 | 3 | 0.833333 | 7 | 4 / 30 | none |
| 巨像（mega） | 1 | 持续 | 4 | 4 | 0.833333 | 3 | 4 / 20 | none |
| 苍穹（zenith） | 2 | 持续 | 2 | 2 | 0.869565 | 3 | 5 / 30 | none |
| 月影（antumbra） | 2 | 持续 | 2 | 1 | 0.769231 | 1 | 250 / 125 | none |
| 耀星（quasar） | 3 | 持续 | 2 | 5 | 0.476191 | 37 | 1 / 15 | none |
| 毒蛊（arkyid） | 3 | 持续 | 3 | 2 | 2.22222 | 9 | 40 / 95 | none |
| 日蚀（eclipse） | 3 | 持续 | 3 | 1 | 1.25 | 1 | 3 / 12 | none |
| 天蝎（toxopid） | 5 | 持续 | 5 | 1 | 不递增 | 0 | 3000 / 0 | boss |
| 死星（corvus） | 7 | 持续 | 10 | 1 | 不递增 | 1 | 2000 / 0 | boss |
| 雷霆（quad） | 15 | 持续 | 5 | 5 | 0.5 | 11 | 5 / 150 | boss |
| 灾星（vela） | 20 | 40 | 2 | 5 | 0.333333 | 23 | 60 / 0 | none |
| 王座（reign） | 30 | 56 | 5 | 5 | 0.333333 | 15 | 150 / 400 | boss |
| 要塞（oct） | 35 | 50 | 7 | 2 | 0.5 | 10 | 15 / 700 | none |

`scaling`按原引擎公式解释：在本编组满足出兵间隔时，数量为`min(amount + int(((waveIndex-begin)/spacing)/scaling), max)`。特定坐标出生、载荷、携带资源和队伍覆盖保留在下方原始规则中。

## 完整规则与地图事件

- [原生规则JSON](../rules/sector-serpulo-245.json)：包含编组、载荷、天气频率、出生坐标、目标与标志等已写入字段。默认字段由原引擎补全。
- 地图目标类型：Timer × 5。具体条件、父目标与提示标记见规则JSON。
- 世界处理器脚本（文件名为地图格坐标）：[281_570](../scripts/sector-serpulo-245/281_570.mlog), [281_571](../scripts/sector-serpulo-245/281_571.mlog), [281_572](../scripts/sector-serpulo-245/281_572.mlog), [281_573](../scripts/sector-serpulo-245/281_573.mlog), [282_570](../scripts/sector-serpulo-245/282_570.mlog), [282_571](../scripts/sector-serpulo-245/282_571.mlog), [282_572](../scripts/sector-serpulo-245/282_572.mlog), [282_573](../scripts/sector-serpulo-245/282_573.mlog), [283_570](../scripts/sector-serpulo-245/283_570.mlog), [283_571](../scripts/sector-serpulo-245/283_571.mlog), [283_572](../scripts/sector-serpulo-245/283_572.mlog), [283_573](../scripts/sector-serpulo-245/283_573.mlog), [284_570](../scripts/sector-serpulo-245/284_570.mlog), [284_571](../scripts/sector-serpulo-245/284_571.mlog), [284_572](../scripts/sector-serpulo-245/284_572.mlog), [284_573](../scripts/sector-serpulo-245/284_573.mlog), [285_570](../scripts/sector-serpulo-245/285_570.mlog), [285_571](../scripts/sector-serpulo-245/285_571.mlog), [285_572](../scripts/sector-serpulo-245/285_572.mlog), [285_573](../scripts/sector-serpulo-245/285_573.mlog), [286_570](../scripts/sector-serpulo-245/286_570.mlog), [286_571](../scripts/sector-serpulo-245/286_571.mlog), [286_572](../scripts/sector-serpulo-245/286_572.mlog), [286_573](../scripts/sector-serpulo-245/286_573.mlog), [287_570](../scripts/sector-serpulo-245/287_570.mlog), [287_571](../scripts/sector-serpulo-245/287_571.mlog), [287_572](../scripts/sector-serpulo-245/287_572.mlog), [287_573](../scripts/sector-serpulo-245/287_573.mlog)。建筑关联链接和其余地图状态仍保存在原始`.msav`文件中。

[返回原版关卡总览](../../ORIGINAL_CAMPAIGN.md)
