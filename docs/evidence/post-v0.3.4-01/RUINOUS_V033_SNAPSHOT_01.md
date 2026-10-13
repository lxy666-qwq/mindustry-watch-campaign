# 遗迹海岸 v0.3.3 实战证据快照 01

本快照只收录已结束的 NORMAL-01～31。它独立于已发布的 32 个满分模式快照和 SupplementLedger03，供 post-v0.3.4-01 追加实战证据使用。后续对局不回写本索引。

31 次尝试中，22 次原生失败，8 次控制器/静态规划中止，1 次原生胜利且只获普通两星。遗迹海岸没有普通三星满分，也没有英雄或战术通过证据，因此本快照不增加 MAX 覆盖数。

## 真实进度与版本范围

每次从真实 progression-chain 设置复制到各自隔离目录，启动门禁在 loadMission 前核验；原共享档不写入，也不拼接不同尝试的成绩。27 获胜档的主线从 12 增至 14 星；其余完成的失败档仍为主线 12 星。

所有有 result.txt 的正常结束对局均记录运行产品版本 0.3.3、类来源 star033-runtime/Mindustry.jar、JAR 时间戳 1791877212532。中止对局没有写出最终版本字段；各自保存了源文件和实际异常，不能把缺失运行字段补写成实测结果。启动使用固定 JAR 在类路径首位和完整资源，不读取 D 目录开发类。

未修改核心库存、波次、血量、胜利、目标或评分。建设与拆除为原生 BuildPlan，取物/投放为玩家原生接口，采矿受原生能力及矿点检查。

## 两星胜利和新进程读回

27 由原生 GameOver 判为 sharded 获胜：4410.4 游戏秒，波次计数 30、实际出兵 29 次，464 次完成建设、306 个建筑损失；核心最低 27.09%，核心损失 0。结算满血不能抹去此前损伤，评分自然为普通 2 / 3 星。

end.msav 成功生成。在全新 JVM、独立设置副本中无 tick 读回：650 项规则标签、99570 项地形/建筑观察字段全等；NORMAL=2、HEROIC=0、TACTICAL=0、主线 14 星不变，最低核心 27.09% 原样保留。这证明此两星局的持久成绩与已比较世界字段，不能扩写为三星或全部内部状态验证。05 的失败存档另有 464 标签、100544 地形/建筑字段全等读回，不是胜利。

## 固定 case 索引

完整运行字段、异常及逐项文件路径见 [CSV 索引](ruinous-v033-snapshot-01-cases.csv)。下表中的“保存”只表示结束存档存在，未读回的局不标保存恢复通过。路径以工作证据目录为相对根。

| Case | 状态 | 星数 | 游戏秒 | 结果 | 结束存档 | 新进程读回 |
|---|---|---:|---:|---|---|---|
| 01 | 原生失败 | 0 | 877.26666 | [result](next-serpulo033/ruinousShores-NORMAL-01/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-01/end.msav) | 未执行 |
| 02 | 原生失败 | 0 | 2325.7334 | [result](next-serpulo033/ruinousShores-NORMAL-02/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-02/end.msav) | 未执行 |
| 03 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 04 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 05 | 原生失败 | 0 | 2324.9 | [result](next-serpulo033/ruinousShores-NORMAL-05/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-05/end.msav) | [全等读回](next-serpulo033/ruinousShores-NORMAL-05/fresh-readback/result.txt) |
| 06 | 原生失败 | 0 | 875.73334 | [result](next-serpulo033/ruinousShores-NORMAL-06/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-06/end.msav) | 未执行 |
| 07 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 08 | 原生失败 | 0 | 876.56665 | [result](next-serpulo033/ruinousShores-NORMAL-08/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-08/end.msav) | 未执行 |
| 09 | 原生失败 | 0 | 878.43335 | [result](next-serpulo033/ruinousShores-NORMAL-09/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-09/end.msav) | 未执行 |
| 10 | 原生失败 | 0 | 2616.7166 | [result](next-serpulo033/ruinousShores-NORMAL-10/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-10/end.msav) | 未执行 |
| 11 | 原生失败 | 0 | 2324.9666 | [result](next-serpulo033/ruinousShores-NORMAL-11/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-11/end.msav) | 未执行 |
| 12 | 原生失败 | 0 | 1220.65 | [result](next-serpulo033/ruinousShores-NORMAL-12/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-12/end.msav) | 未执行 |
| 13 | 原生失败 | 0 | 1455.3167 | [result](next-serpulo033/ruinousShores-NORMAL-13/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-13/end.msav) | 未执行 |
| 14 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 15 | 原生失败 | 0 | 4336.3667 | [result](next-serpulo033/ruinousShores-NORMAL-15/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-15/end.msav) | 未执行 |
| 16 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 17 | 原生失败 | 0 | 2327.8 | [result](next-serpulo033/ruinousShores-NORMAL-17/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-17/end.msav) | 未执行 |
| 18 | 原生失败 | 0 | 1170.2667 | [result](next-serpulo033/ruinousShores-NORMAL-18/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-18/end.msav) | 未执行 |
| 19 | 原生失败 | 0 | 3194.05 | [result](next-serpulo033/ruinousShores-NORMAL-19/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-19/end.msav) | 未执行 |
| 20 | 原生失败 | 0 | 1747.6833 | [result](next-serpulo033/ruinousShores-NORMAL-20/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-20/end.msav) | 未执行 |
| 21 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 22 | 原生失败 | 0 | 3629.0166 | [result](next-serpulo033/ruinousShores-NORMAL-22/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-22/end.msav) | 未执行 |
| 23 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 24 | 原生失败 | 0 | 3198.6333 | [result](next-serpulo033/ruinousShores-NORMAL-24/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-24/end.msav) | 未执行 |
| 25 | 原生失败 | 0 | 877.65 | [result](next-serpulo033/ruinousShores-NORMAL-25/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-25/end.msav) | 未执行 |
| 26 | 原生失败 | 0 | 2383.8167 | [result](next-serpulo033/ruinousShores-NORMAL-26/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-26/end.msav) | 未执行 |
| 27 | 原生胜利，未满分 | 2 | 4410.4 | [result](next-serpulo033/ruinousShores-NORMAL-27/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-27/end.msav) | [全等读回](next-serpulo033/ruinousShores-NORMAL-27/fresh-readback/result.txt) |
| 28 | 原生失败 | 0 | 4265.283 | [result](next-serpulo033/ruinousShores-NORMAL-28/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-28/end.msav) | 未执行 |
| 29 | 控制器中止 |  |  | 未生成；异常见 console | 未生成 | 未执行 |
| 30 | 原生失败 | 0 | 2759.2834 | [result](next-serpulo033/ruinousShores-NORMAL-30/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-30/end.msav) | 未执行 |
| 31 | 原生失败 | 0 | 3049.75 | [result](next-serpulo033/ruinousShores-NORMAL-31/result.txt) | [end.msav](next-serpulo033/ruinousShores-NORMAL-31/end.msav) | 未执行 |

中止记录保留实际错误：03 错误要求 Alpha 采钛；04 废料带路无可用路径；07 分离机出口与预留布局冲突；14 熔炉并不相邻；16 沙带没有出口；21 钛生产链受损后未处理重建；23 把施工占位当作完整设施；29 新日志错误假设子弹 owner 非空。中止局没有伪造结算或结束存档。

详细策略过程见 [后续主线记录](NEXT_SERPULO_v033.md)；它是继续更新的工作记录，本快照的 case 范围及 CSV 保持固定。
