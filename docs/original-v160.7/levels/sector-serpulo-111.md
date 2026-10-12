# 编号预设 111（sector-serpulo-111）

读取来源：归档的0.1.0原版程序，基于Mindustry v160.7；本页列出初始配置，未推进关卡脚本。

| 设置 | 原始配置 |
| --- | --- |
| 星球 | serpulo |
| 当前落点编号 | 111 |
| 源码原始位置 | 111 |
| 地图文件 | `maps/serpulo/hidden/111.msav` |
| 地图尺寸 | 600 × 600格 |
| 难度字段 | 10 |
| 初始模式 | 进攻 |
| captureWave / winWave | 0 / 0 |
| 启用波次 | 是 |
| 波间时间字段 | 120秒 |
| initialWaveSpacing字段 | 300秒 |
| 初始战争迷雾 | 否 |
| alwaysUnlocked字段 | 否 |
| requireUnlock字段 | 否 |
| isHidden字段（无介绍） | 是 |
| showHidden字段 | 否 |
| 护盾前置区块 | 无 |
| 科技树父节点 | 无 |
| 额外解锁目标 | 无 |
| 初始地图扫描到的资源 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| 地表主要类型（格数） | 沙子(157944)、深水(73065)、黑沙(28558)、水(21191)、浅滩(18482)、盐碱地(17083) |
| 初始天气规则 | 降雨、雾 |
| 初始敌方核心数 | 5 |
| 初始出生标记数 | 1 |
| 波次编组数 | 21 |
| 地图目标数 | 2 |
| 世界处理器数 | 19 |

## 初始敌人编组

开始与结束列换算为玩家看到的波次编号（源码下标加1）。数量是每个适用出生点的编组初始数量；单位增长、多个出生点、地图脚本会影响实战总量。未设结束值的编组标为“持续”，但关卡仍受胜利条件约束。波次未启用时，存在默认编组也不会自动出兵。

| 单位 | 开始波 | 结束波 | 间隔波 | 初始数量 | 增长参数scaling | 数量上限 | 初始护盾/每波增长 | 状态 |
| --- | ---: | ---: | ---: | ---: | --- | ---: | --- | --- |
| 星辉（flare） | 1 | 持续 | 2 | 1 | 2 | 70 | 50 / 25 | none |
| 星辉（flare） | 1 | 持续 | 2 | 1 | 2 | 70 | 50 / 25 | none |
| 苍穹（zenith） | 1 | 持续 | 2 | 1 | 3.0303 | 40 | 200 / 25 | overdrive |
| 梭鱼（risso） | 1 | 7 | 2 | 2 | 0.666667 | 40 | 250 / 50 | none |
| 天垠（horizon） | 2 | 持续 | 2 | 3 | 0.333333 | 25 | 0 / 25 | none |
| 苍穹（zenith） | 5 | 持续 | 5 | 2 | 0.666667 | 20 | 0 / 25 | overdrive |
| 巨像（mega） | 5 | 持续 | 2 | 3 | 2 | 10 | 300 / 25 | overdrive |
| 飞鲨（minke） | 7 | 30 | 2 | 1 | 0.5 | 30 | 0 / 50 | none |
| 戟鲸（bryde） | 7 | 25 | 3 | 4 | 0.833333 | 10 | 0 / 50 | none |
| 苍穹（zenith） | 11 | 20 | 2 | 2 | 1.33333 | 20 | 120 / 60 | none |
| 月影（antumbra） | 15 | 70 | 5 | 1 | 4 | 10 | 500 / 50 | overdrive |
| 巨像（mega） | 15 | 持续 | 5 | 3 | 2 | 15 | 100 / 50 | none |
| 电鳗（oxynoe） | 15 | 持续 | 2 | 5 | 2 | 20 | 100 / 50 | none |
| 雷霆（quad） | 20 | 持续 | 3 | 1 | 4 | 30 | 100 / 100 | overdrive |
| 玄武（aegires） | 20 | 持续 | 3 | 1 | 2 | 40 | 200 / 50 | none |
| 月影（antumbra） | 35 | 70 | 5 | 3 | 4 | 10 | 100 / 25 | none |
| 蛟龙（sei） | 40 | 持续 | 3 | 1 | 0.5 | 40 | 100 / 100 | none |
| 龙王（navanax） | 40 | 持续 | 5 | 1 | 3.0303 | 5 | 1000 / 100 | boss |
| 海神（omura） | 45 | 持续 | 5 | 1 | 3.0303 | 30 | 1000 / 100 | boss |
| 日蚀（eclipse） | 50 | 持续 | 5 | 1 | 5 | 15 | 1000 / 100 | boss |
| 独影（mono） | 9999 | 持续 | 1 | 1 | 不递增 | 1 | 1000000000 / 1000000000 | boss |

`scaling`按原引擎公式解释：在本编组满足出兵间隔时，数量为`min(amount + int(((waveIndex-begin)/spacing)/scaling), max)`。特定坐标出生、载荷、携带资源和队伍覆盖保留在下方原始规则中。

## 完整规则与地图事件

- [原生规则JSON](../rules/sector-serpulo-111.json)：包含编组、载荷、天气频率、出生坐标、目标与标志等已写入字段。默认字段由原引擎补全。
- 地图目标类型：Timer × 2。具体条件、父目标与提示标记见规则JSON。
- 世界处理器脚本（文件名为地图格坐标）：[1_1](../scripts/sector-serpulo-111/1_1.mlog), [2_2](../scripts/sector-serpulo-111/2_2.mlog), [3_1](../scripts/sector-serpulo-111/3_1.mlog), [3_11](../scripts/sector-serpulo-111/3_11.mlog), [3_13](../scripts/sector-serpulo-111/3_13.mlog), [3_3](../scripts/sector-serpulo-111/3_3.mlog), [3_5](../scripts/sector-serpulo-111/3_5.mlog), [3_7](../scripts/sector-serpulo-111/3_7.mlog), [3_9](../scripts/sector-serpulo-111/3_9.mlog), [4_10](../scripts/sector-serpulo-111/4_10.mlog), [4_12](../scripts/sector-serpulo-111/4_12.mlog), [4_14](../scripts/sector-serpulo-111/4_14.mlog), [4_2](../scripts/sector-serpulo-111/4_2.mlog), [4_6](../scripts/sector-serpulo-111/4_6.mlog), [5_1](../scripts/sector-serpulo-111/5_1.mlog), [5_3](../scripts/sector-serpulo-111/5_3.mlog), [6_2](../scripts/sector-serpulo-111/6_2.mlog), [8_1](../scripts/sector-serpulo-111/8_1.mlog), [8_2](../scripts/sector-serpulo-111/8_2.mlog)。建筑关联链接和其余地图状态仍保存在原始`.msav`文件中。

[返回原版关卡总览](../../ORIGINAL_CAMPAIGN.md)
