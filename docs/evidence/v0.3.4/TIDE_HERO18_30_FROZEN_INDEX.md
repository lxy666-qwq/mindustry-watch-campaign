# v0.3.3 守望奖励与英雄失败冻结索引

本索引仅指已经结束且保留源文件的真实原生循环案例。固定程序为 star033-runtime/Mindustry.jar，完整资产为 star033-runtime/assets，程序修改时间1791877212532；classpath没有D开发classes。禁止把胜利但未满足英雄评分的案例记为通关。独立QA直接选图，不声称主线菜单树已抵达，实际主线星数为0。没有人工注星、合并成绩、补库存、跳波或改敌人。

## 实际奖励来源

qa033-watch1-earned/NORMAL-2、HEROIC-1、TACTICAL-2 是同一私有QA档顺序实际完成的第一支线3/1/1；NORMAL-1只得2，TACTICAL-1当次0，二者失败/低星证据保留。TACTICAL-2/save-readback.txt 是新进程只读证据：W1实际满5，W5实际满5，mainStars=0，当次战术earned1，任务A43/B2，生命92.734545%。W1 Bandage由真实第五颗星开放。相关独立说明为 WATCH1_QA_EARNED_V033.md；不导出profile/settings.bin或用户配置。

## 第六支线英雄18至30

所有案例当次英雄均为0，当前真实W6成绩3/0/1。每目录保留 result.txt、driver-source.java、tide-source.java、trial.csv、destroyed.csv、counter-result.txt、segment-observations.csv、native-fuel-logistics.csv、native-starting-stock.txt、native-plan-costs.txt、bandage-observations.csv、native-grid-configurations.csv。自然胜利才有正常SaveIO生成的end.msav；失败不伪造结束存档。

| 案例 | 自然赢家 | 波数 / 秒 | 核心最低 | 完成建造 / 损失 | 当次英雄星 |
| --- | --- | --- | --- | --- | --- |
| HEROIC-18 | crux | 21 / 1472.6333 | 核心毁灭 | 130 / 43 | 0 |
| HEROIC-19 | sharded | 24 / 1676.4166 | 63.3127% | 141 / 24 | 0 |
| HEROIC-20 | sharded | 24 / 1676.0834 | 100.0000% | 146 / 29 | 0 |
| HEROIC-21 | crux | 15 / 1110.6 | 核心毁灭 | 125 / 31 | 0 |
| HEROIC-22 | sharded | 24 / 1676.15 | 64.5864% | 135 / 25 | 0 |
| HEROIC-23 | crux | 21 / 1470.25 | 核心毁灭 | 131 / 15 | 0 |
| HEROIC-24 | crux | 21 / 1470.6666 | 核心毁灭 | 130 / 25 | 0 |
| HEROIC-25 | sharded | 24 / 1681.15 | 76.4773% | 139 / 35 | 0 |
| HEROIC-26 | sharded | 24 / 1673.4667 | 84.9210% | 141 / 39 | 0 |
| HEROIC-27 | crux | 21 / 1469.6833 | 核心毁灭 | 128 / 35 | 0 |
| HEROIC-28 | crux | 10 / 769.3 | 核心毁灭 | 96 / 4 | 0 |
| HEROIC-29 | crux | 10 / 791.63336 | 核心毁灭 | 95 / 6 | 0 |
| HEROIC-30 | crux | 21 / 1467.0333 | 核心毁灭 | 128 / 42 | 0 |

H18绷带塔部署过晚，没有消耗石墨，不能判断奖励有效性。H19在7.6833秒正常建成，实际消耗56石墨，即28次符合原生条件的墙体治疗；但生命63.3127%、损失24仍不达标。H20核心100%且裂解有效目标期电力最低1，实际23次绷带治疗，仍29座损失超过22。H21的裂解蓝图与后续煤桥重叠，属于驱动错误，不作为难度证据。H22真实120次匹配拦截下界、41次绷带治疗，但生命64.5864%、损失25，仍0星。H23/24核心电池有能量而西侧节点被毁导致裂解断电，随后通过正常节点配置建立备用连接。H25大额人工送煤仍使后段煤耗尽。H26双Duo+第二Ripple低成本方案损失39、生命84.921%，严格0星。

H27加入一Salvo+Lancer及两座正常充电的新电池，观察实际存能11689；Hail硅弹吃掉工程硅，裂解只建69.5%，不能推断装备或有限配给不足。H28/H29早期第10波失败；H29源码换行替换未生效，仍将Cyclone改成Hail，属于试验驱动修改错误，源与失败记录保留。H30已实际恢复Cyclone+单Salvo并延后Lancer、保留Segment硅130；裂解788.76666秒建成、实际89次匹配移除下界，但后期煤电耗尽，损失42，21波核心毁灭。Lancer只建21.9%，剩余工程硅仍需保护；不作为Lancer无效的证据。

原生配方：Segment Si130/Th80/Phase40/Ti40；Bandage Graph50/Si40，符合条件每次实际消耗2石墨；Steam各Si30，两座共60；Lancer Si60。H30原生129蓝图按block.requirements求和Si290/Ti305/Meta149；初始Si300，只剩10硅可自由用于弹药/强化，不可同时假定额外大量硅弹。两座新Battery每座Cu5/Pb20，存能来自原生发电，没有注入。H28/H29原生成本Graph620，不用先前手算误写的623。

公开快照只复制上述结束案例目录与只读报告，不复制整个profile、settings、存档账户目录或开发配置。已有普通三星NORMAL-4与战术星TACTICAL-4证据继续保留，英雄任何0星不能增加完整模式覆盖数。
