# 编号预设 67（sector-serpulo-67）

读取来源：归档的0.1.0原版程序，基于Mindustry v160.7；本页列出初始配置，未推进关卡脚本。

| 设置 | 原始配置 |
| --- | --- |
| 星球 | serpulo |
| 当前落点编号 | 67 |
| 源码原始位置 | 67 |
| 地图文件 | `maps/serpulo/hidden/67.msav` |
| 地图尺寸 | 470 × 470格 |
| 难度字段 | 10 |
| 初始模式 | 进攻 |
| captureWave / winWave | 0 / 0 |
| 启用波次 | 是 |
| 波间时间字段 | 140秒 |
| initialWaveSpacing字段 | 1200秒 |
| 初始战争迷雾 | 否 |
| alwaysUnlocked字段 | 否 |
| requireUnlock字段 | 否 |
| isHidden字段（无介绍） | 是 |
| showHidden字段 | 否 |
| 护盾前置区块 | 无 |
| 科技树父节点 | 无 |
| 额外解锁目标 | 无 |
| 初始地图扫描到的资源 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| 地表主要类型（格数） | 雪(77260)、黑沙(37837)、页岩地(33211)、石头(30647)、冰(16597)、玄武岩(6445) |
| 初始天气规则 | 无 |
| 初始敌方核心数 | 3 |
| 初始出生标记数 | 3 |
| 波次编组数 | 30 |
| 地图目标数 | 7 |
| 世界处理器数 | 10 |

## 初始敌人编组

开始与结束列换算为玩家看到的波次编号（源码下标加1）。数量是每个适用出生点的编组初始数量；单位增长、多个出生点、地图脚本会影响实战总量。未设结束值的编组标为“持续”，但关卡仍受胜利条件约束。波次未启用时，存在默认编组也不会自动出兵。

| 单位 | 开始波 | 结束波 | 间隔波 | 初始数量 | 增长参数scaling | 数量上限 | 初始护盾/每波增长 | 状态 |
| --- | ---: | ---: | ---: | ---: | --- | ---: | --- | --- |
| 尖刀（dagger） | 1 | 持续 | 1 | 1 | 1 | 20 | 0 / 0 | none |
| 爬虫（crawler） | 1 | 15 | 1 | 1 | 1 | 30 | 0 / 0 | fast |
| 星辉（flare） | 1 | 持续 | 2 | 2 | 0.5 | 15 | 0 / 0 | overclock |
| 战锤（mace） | 3 | 持续 | 1 | 1 | 2 | 20 | 0 / 0 | none |
| 尖刀（dagger） | 5 | 15 | 1 | 1 | 1 | 5 | 0 / 0 | none |
| 堡垒（fortress） | 7 | 持续 | 2 | 2 | 2 | 10 | 0 / 0 | overclock |
| 天垠（horizon） | 7 | 17 | 2 | 2 | 1 | 40 | 0 / 0 | overclock |
| 耀星（quasar） | 10 | 持续 | 2 | 2 | 2 | 10 | 0 / 0 | overclock |
| 堡垒（fortress） | 13 | 20 | 2 | 4 | 1 | 40 | 0 / 0 | shielded |
| 毒蛛（atrax） | 13 | 20 | 2 | 4 | 1 | 40 | 0 / 0 | shielded |
| 权杖（scepter） | 15 | 持续 | 2 | 1 | 3.0303 | 10 | 0 / 5 | overdrive |
| 灾星（vela） | 17 | 持续 | 2 | 1 | 2 | 40 | 10 / 5 | none |
| 血蛭（spiroct） | 17 | 持续 | 1 | 1 | 1 | 15 | 0 / 0 | fast |
| 苍穹（zenith） | 20 | 30 | 1 | 5 | 0.4 | 10 | 0 / 5 | none |
| 苍穹（zenith） | 20 | 持续 | 1 | 2 | 0.5 | 15 | 0 / 0 | overclock |
| 雷霆（quad） | 20 | 持续 | 1 | 1 | 2 | 7 | 5 / 5 | overdrive |
| 毒蛊（arkyid） | 23 | 持续 | 2 | 1 | 1 | 10 | 5 / 5 | shielded |
| 王座（reign） | 25 | 持续 | 10 | 1 | 1 | 7 | 20 / 10 | boss |
| 苍穹（zenith） | 27 | 持续 | 2 | 2 | 0.5 | 20 | 5 / 5 | fast |
| 死星（corvus） | 30 | 持续 | 10 | 1 | 1 | 10 | 20 / 5 | boss |
| 苍穹（zenith） | 30 | 持续 | 1 | 2 | 0.5 | 10 | 5 / 5 | fast |
| 月影（antumbra） | 30 | 持续 | 2 | 1 | 1 | 5 | 10 / 5 | overdrive |
| 月影（antumbra） | 30 | 持续 | 2 | 1 | 1 | 10 | 5 / 5 | fast |
| 雷霆（quad） | 35 | 持续 | 1 | 1 | 2 | 5 | 20 / 10 | overclock |
| 要塞（oct） | 35 | 持续 | 5 | 1 | 2 | 2 | 10 / 5 | overdrive |
| 权杖（scepter） | 37 | 持续 | 1 | 1 | 不递增 | 20 | 10 / 10 | overclock |
| 王座（reign） | 40 | 持续 | 10 | 1 | 1 | 3 | 20 / 10 | boss |
| 日蚀（eclipse） | 40 | 持续 | 10 | 2 | 0.5 | 10 | 50 / 10 | boss |
| 天蝎（toxopid） | 55 | 持续 | 5 | 2 | 0.5 | 10 | 100 / 20 | boss |
| 月影（antumbra） | 90 | 持续 | 1 | 5 | 0.2 | 30 | 5 / 5 | overclock |

`scaling`按原引擎公式解释：在本编组满足出兵间隔时，数量为`min(amount + int(((waveIndex-begin)/spacing)/scaling), max)`。特定坐标出生、载荷、携带资源和队伍覆盖保留在下方原始规则中。

## 完整规则与地图事件

- [原生规则JSON](../rules/sector-serpulo-67.json)：包含编组、载荷、天气频率、出生坐标、目标与标志等已写入字段。默认字段由原引擎补全。
- 地图目标类型：DestroyBlocks × 4、Timer × 2、DestroyBlock × 1。具体条件、父目标与提示标记见规则JSON。
- 世界处理器脚本（文件名为地图格坐标）：[8_71](../scripts/sector-serpulo-67/8_71.mlog), [8_72](../scripts/sector-serpulo-67/8_72.mlog), [8_73](../scripts/sector-serpulo-67/8_73.mlog), [8_74](../scripts/sector-serpulo-67/8_74.mlog), [8_75](../scripts/sector-serpulo-67/8_75.mlog), [9_71](../scripts/sector-serpulo-67/9_71.mlog), [9_72](../scripts/sector-serpulo-67/9_72.mlog), [9_73](../scripts/sector-serpulo-67/9_73.mlog), [9_74](../scripts/sector-serpulo-67/9_74.mlog), [9_75](../scripts/sector-serpulo-67/9_75.mlog)。建筑关联链接和其余地图状态仍保存在原始`.msav`文件中。

[返回原版关卡总览](../../ORIGINAL_CAMPAIGN.md)
