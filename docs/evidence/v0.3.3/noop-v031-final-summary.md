# 168项原生无操作对照（冻结v0.3.1）

2026-10-13完成，56张参与地图各普通/英雄/战术，共168个互不合并的独立设置档。原生60Hz世界帧、AI、地图脚本、波次和GameOver；驾驶器不建造、不取料、不放料、不跳波、不改生命、不授星。每项最多1800游戏秒。

结果：140项原生敌方胜利（Crux105、Malis35），28项到时仍未结束；0项己方自然胜利，168项均0星，无驾驶器运行错误。超时进程按约定返回2，与游戏失败返回0区分。此结果仅适用于该时限与不操作策略，不证明后期所有AFK手法都无效，也不计入168项主动满分通关台账。

固定程序为归档v0.3.1 Mindustry.jar，固定全量资产为control031-assets；每个result实际版本均0.3.1、CodeLocation指向归档程序。结果中的两个classModifiedAtStart字段读取当时开发输出目录的参考时间，不证明加载字节码；以固定classpath及actualVersion为准。未执行任何摘要或指纹校验。

最初批次因读取中文多行status时错误采用ConvertFrom-StringData而在2项后退出，其失败日志保留；最终新目录批次用ASCII键行解析，168项均有原始结果/战斗CSV/敌方损毁CSV/驾驶器快照。两批不拼接成绩。

## 1800秒仍未结束的28项

| 地图 | 模式 | 原生wave计数 | 实际出兵次数 |
|---|---|---:|---:|
| groundZero | NORMAL | 1 | 0 |
| groundZero | HEROIC | 1 | 0 |
| groundZero | TACTICAL | 1 | 0 |
| frozenForest | NORMAL | 1 | 0 |
| frozenForest | HEROIC | 1 | 0 |
| frozenForest | TACTICAL | 1 | 0 |
| frontier | NORMAL | 1 | 0 |
| frontier | HEROIC | 1 | 0 |
| frontier | TACTICAL | 1 | 0 |
| fungalPass | NORMAL | 1 | 0 |
| fungalPass | HEROIC | 1 | 0 |
| fungalPass | TACTICAL | 1 | 0 |
| onset | NORMAL | 1 | 0 |
| onset | HEROIC | 1 | 0 |
| onset | TACTICAL | 1 | 0 |
| aegis | NORMAL | 1 | 0 |
| aegis | HEROIC | 1 | 0 |
| aegis | TACTICAL | 1 | 0 |
| split | NORMAL | 1 | 0 |
| split | HEROIC | 1 | 0 |
| split | TACTICAL | 1 | 0 |
| marsh | NORMAL | 1 | 0 |
| marsh | HEROIC | 1 | 0 |
| marsh | TACTICAL | 1 | 0 |
| peaks | NORMAL | 1 | 0 |
| peaks | HEROIC | 1 | 0 |
| peaks | TACTICAL | 1 | 0 |
| origin | NORMAL | 1 | 0 |
