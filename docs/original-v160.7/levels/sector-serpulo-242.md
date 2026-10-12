# 编号预设 242（sector-serpulo-242）

读取来源：归档的0.1.0原版程序，基于Mindustry v160.7；本页列出初始配置，未推进关卡脚本。

| 设置 | 原始配置 |
| --- | --- |
| 星球 | serpulo |
| 当前落点编号 | 242 |
| 源码原始位置 | 242 |
| 地图文件 | `maps/serpulo/hidden/242.msav` |
| 地图尺寸 | 600 × 600格 |
| 难度字段 | 10 |
| 初始模式 | 进攻 |
| captureWave / winWave | 0 / 0 |
| 启用波次 | 是 |
| 波间时间字段 | 140秒 |
| initialWaveSpacing字段 | 810秒 |
| 初始战争迷雾 | 否 |
| alwaysUnlocked字段 | 否 |
| requireUnlock字段 | 否 |
| isHidden字段（无介绍） | 是 |
| showHidden字段 | 否 |
| 护盾前置区块 | 无 |
| 科技树父节点 | 无 |
| 额外解锁目标 | 无 |
| 初始地图扫描到的资源 | 冷冻液、水、沙、煤炭、石油、钍、钛、铅、铜 |
| 地表主要类型（格数） | 黑沙(116998)、金属地基 9(78311)、页岩地(36995)、石头(35628)、苔藓地(29178)、金属地基 3(10984) |
| 初始天气规则 | 无 |
| 初始敌方核心数 | 9 |
| 初始出生标记数 | 1 |
| 波次编组数 | 26 |
| 地图目标数 | 7 |
| 世界处理器数 | 30 |

## 初始敌人编组

开始与结束列换算为玩家看到的波次编号（源码下标加1）。数量是每个适用出生点的编组初始数量；单位增长、多个出生点、地图脚本会影响实战总量。未设结束值的编组标为“持续”，但关卡仍受胜利条件约束。波次未启用时，存在默认编组也不会自动出兵。

| 单位 | 开始波 | 结束波 | 间隔波 | 初始数量 | 增长参数scaling | 数量上限 | 初始护盾/每波增长 | 状态 |
| --- | ---: | ---: | ---: | ---: | --- | ---: | --- | --- |
| 星辉（flare） | 1 | 25 | 1 | 20 | 0.4 | 30 | 50 / 60 | overdrive |
| 天垠（horizon） | 1 | 35 | 1 | 4 | 0.666667 | 10 | 300 / 100 | none |
| 苍穹（zenith） | 1 | 持续 | 1 | 5 | 0.5 | 10 | 200 / 101 | overclock |
| 苍穹（zenith） | 1 | 35 | 1 | 10 | 1 | 13 | 400 / 80 | none |
| 月影（antumbra） | 1 | 持续 | 1 | 1 | 2 | 8 | 4000 / 70 | overdrive |
| 月影（antumbra） | 1 | 持续 | 1 | 3 | 1.33333 | 6 | 4000 / 110 | overdrive |
| 幻型（poly） | 1 | 21 | 1 | 10 | 1.66667 | 11 | 120 / 70 | none |
| 天垠（horizon） | 2 | 持续 | 1 | 4 | 2 | 7 | 400 / 90 | overclock |
| 天垠（horizon） | 2 | 持续 | 1 | 4 | 2 | 7 | 300 / 70 | overdrive |
| 日蚀（eclipse） | 5 | 5 | 10 | 1 | 1 | 1 | 13000 / 0 | boss |
| 幻型（poly） | 7 | 持续 | 2 | 5 | 1.66667 | 11 | 120 / 70 | overclock |
| 幻型（poly） | 7 | 持续 | 3 | 5 | 1.66667 | 11 | 120 / 70 | overdrive |
| 雷霆（quad） | 10 | 35 | 1 | 1 | 1.17647 | 4 | 4000 / 300 | none |
| 日蚀（eclipse） | 13 | 持续 | 2 | 2 | 3.33333 | 6 | 12000 / 600 | overclock |
| 雷霆（quad） | 13 | 持续 | 2 | 2 | 1.11111 | 4 | 1200 / 300 | none |
| 日蚀（eclipse） | 15 | 持续 | 2 | 2 | 4 | 3 | 10000 / 800 | fast |
| 要塞（oct） | 15 | 持续 | 2 | 1 | 2.9994 | 5 | 10000 / 600 | shielded |
| 月影（antumbra） | 20 | 持续 | 3 | 4 | 1.11111 | 5 | 700 / 120 | fast |
| 雷霆（quad） | 20 | 持续 | 3 | 1 | 1 | 5 | 5000 / 250 | none |
| 雷霆（quad） | 20 | 持续 | 4 | 1 | 1 | 3 | 5000 / 400 | none |
| 要塞（oct） | 20 | 持续 | 5 | 2 | 1 | 3 | 10000 / 2000 | boss |
| 月影（antumbra） | 25 | 持续 | 3 | 2 | 不递增 | 2 | 7800 / 230 | overdrive |
| 月影（antumbra） | 25 | 持续 | 4 | 2 | 不递增 | 2 | 10000 / 200 | none |
| 月影（antumbra） | 35 | 持续 | 1 | 2 | 1 | 7 | 9000 / 200 | fast |
| 日蚀（eclipse） | 70 | 持续 | 10 | 3 | 不递增 | 3 | 75000 / 7000 | boss |
| 要塞（oct） | 70 | 持续 | 10 | 3 | 1 | 3 | 175000 / 15000 | spore-slowed |

`scaling`按原引擎公式解释：在本编组满足出兵间隔时，数量为`min(amount + int(((waveIndex-begin)/spacing)/scaling), max)`。特定坐标出生、载荷、携带资源和队伍覆盖保留在下方原始规则中。

## 完整规则与地图事件

- [原生规则JSON](../rules/sector-serpulo-242.json)：包含编组、载荷、天气频率、出生坐标、目标与标志等已写入字段。默认字段由原引擎补全。
- 地图目标类型：Timer × 7。具体条件、父目标与提示标记见规则JSON。
- 世界处理器脚本（文件名为地图格坐标）：[465_125](../scripts/sector-serpulo-242/465_125.mlog), [465_128](../scripts/sector-serpulo-242/465_128.mlog), [466_122](../scripts/sector-serpulo-242/466_122.mlog), [466_125](../scripts/sector-serpulo-242/466_125.mlog), [466_128](../scripts/sector-serpulo-242/466_128.mlog), [467_122](../scripts/sector-serpulo-242/467_122.mlog), [467_125](../scripts/sector-serpulo-242/467_125.mlog), [467_128](../scripts/sector-serpulo-242/467_128.mlog), [468_122](../scripts/sector-serpulo-242/468_122.mlog), [468_128](../scripts/sector-serpulo-242/468_128.mlog), [469_122](../scripts/sector-serpulo-242/469_122.mlog), [469_125](../scripts/sector-serpulo-242/469_125.mlog), [469_128](../scripts/sector-serpulo-242/469_128.mlog), [470_122](../scripts/sector-serpulo-242/470_122.mlog), [470_125](../scripts/sector-serpulo-242/470_125.mlog), [470_128](../scripts/sector-serpulo-242/470_128.mlog), [470_130](../scripts/sector-serpulo-242/470_130.mlog), [471_122](../scripts/sector-serpulo-242/471_122.mlog), [471_128](../scripts/sector-serpulo-242/471_128.mlog), [472_122](../scripts/sector-serpulo-242/472_122.mlog), [472_128](../scripts/sector-serpulo-242/472_128.mlog), [473_125](../scripts/sector-serpulo-242/473_125.mlog), [473_130](../scripts/sector-serpulo-242/473_130.mlog), [475_128](../scripts/sector-serpulo-242/475_128.mlog), [477_123](../scripts/sector-serpulo-242/477_123.mlog), [477_128](../scripts/sector-serpulo-242/477_128.mlog), [478_123](../scripts/sector-serpulo-242/478_123.mlog), [478_125](../scripts/sector-serpulo-242/478_125.mlog), [479_123](../scripts/sector-serpulo-242/479_123.mlog), [480_123](../scripts/sector-serpulo-242/480_123.mlog)。建筑关联链接和其余地图状态仍保存在原始`.msav`文件中。

[返回原版关卡总览](../../ORIGINAL_CAMPAIGN.md)
