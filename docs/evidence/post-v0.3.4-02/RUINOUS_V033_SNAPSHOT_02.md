# 遗迹海岸 v0.3.3 实战证据快照 02

只收录已结束的普通模式 32～36，独立于快照 01、已发布版本和运行中的后续挑战。原快照不回写。5 次尝试分别为 3 次原生失败、1 次两星胜利、1 次三星胜利；本范围没有静态预检或控制器中止。01 中的 8 次中止仍保留原分类。

36 是遗迹海岸普通模式首次真实满分样例，增加 1 种 MAX 模式覆盖；33 的两星胜利不增加 MAX。英雄和战术在本冻结节点没有通过证据。所有对局使用固定 0.3.3 JAR 首位及完整资产，JAR 启动时间戳 1791877212532；每次启动门禁真实核验，设置由真实 progression-chain 复制，原共享档不写入，不拼接各局成绩。

36：原生 GameOver/sharded，4399.45 游戏秒，wave30/实际29次出兵，298 次建设、76 个建筑损失、0核心损失，最低生命77.4545%，自然取得普通3星、主线15星。双浪涌以原生成本建成并实际射击；硅炉使用真实砂和煤产出硅，玩家原生搬运供弹。库存、波次、血量、敌人、胜利及评分均未更改。

36 的结束存档在新 JVM 无 tick 读回，560 标签和101095地形/建筑观察字段全等，N3/H0/T0、主线15星不变。33 同样读回599标签/100561字段全等、N2/H0/T0/main14；其末波核心最低20.58%，没有把结算满血误记三星。字段范围为全地图地板/覆盖层/方块及锚点建筑队伍、方向、血量、物品、液体，不扩写为全部单位或内部世界状态。

## 固定文件索引

所有原始 runtime、source、console、result、存档和新进程读回路径见 [CSV](ruinous-v033-snapshot-02-cases.csv)，路径以工作证据目录为相对根。没有执行读回的失败局只标存档已生成。

| Case | 实际结论 | 游戏秒 | 建设/损失 | 核心最低 | 星数 | 结果 | 新进程读回 |
|---|---|---:|---:|---:|---:|---|---|
| ruinousShores-NORMAL-32 | 原生失败 | 3198.35 | 359/182 | -1.2379039E-4 | 0 | [result](next-serpulo033/ruinousShores-NORMAL-32/result.txt) | 未执行 |
| ruinousShores-NORMAL-33 | 胜利、两星 | 4412.217 | 365/155 | 0.20584728 | 2 | [result](next-serpulo033/ruinousShores-NORMAL-33/result.txt) | [全等读回](next-serpulo033/ruinousShores-NORMAL-33-read/result.txt) |
| ruinousShores-NORMAL-34 | 原生失败 | 3343.3833 | 323/176 | -0.0063636363 | 0 | [result](next-serpulo033/ruinousShores-NORMAL-34/result.txt) | 未执行 |
| ruinousShores-NORMAL-35 | 原生失败 | 4356.3 | 471/329 | -0.04697643 | 0 | [result](next-serpulo033/ruinousShores-NORMAL-35/result.txt) | 未执行 |
| ruinousShores-NORMAL-36 | 胜利、三星 | 4399.45 | 298/76 | 0.77454543 | 3 | [result](next-serpulo033/ruinousShores-NORMAL-36/result.txt) | [全等读回](next-serpulo033/ruinousShores-NORMAL-36-read/result.txt) |

32 的末期蓝瑟仍正常供电，但冰雹断弹。34 的补建抢占库存，浪涌没有建成。35 的浪涌记录520次原生射击，但第16波已跌破三星生命要求，末波仍失守。这些失败分别保留供电、弹药、破坏及核心弹伤日志，不改写成通过或不可通关。
