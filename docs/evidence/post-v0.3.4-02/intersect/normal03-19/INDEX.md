# 交错丘陵 v0.3.4 独立实战冻结：普通03–19

冻结上限为 normal-19-v034；20及以后不属于本快照。实际设置来自同一真实始发3/1/1→庇护3/1/1→岩浆湖3/1/1（主线15星），交错丘陵原生门禁开放。不存在跨星球拼分，范围内未获得星数或满分。

固定运行程序：`local-work/mindustry-star-work/star034-runtime/Mindustry.jar`，同目录`assets`；所有运行记录的实际 CampaignStars 类来源均为该Jar，版本0.3.4。classpath中Jar在驾驶器classes之前。驾驶器仅使用固定原生1/60秒ticks、BuildPlan、采矿、原生维修武器、InputHandler物流/部队命令和真实制造厂生产；未改D产品、地图目标、资源、敌人、波次、时间或成绩。

本文件夹复制已结束案例的源代码、console、result、CSV、原生世界及已完成的新进程读回；不含profile或settings。原始本机profile保持在各案例目录，未删除或改写。保存字段比较范围见各state-audit-source.java，仅包括实际采集的观察字段；没有扩展为全世界逐字段保证。

17次中11次0 tick蓝图规划中止、5次实际失核失败、1次九波后进攻超时。0 tick失败属于驾驶器布置与原生地形/供线契约不匹配，不能当作关卡无法完成。普通9波完成后必须自然攻下敌方核心；未攻下不授星。

| 案例 | 分类 | 游戏秒 | 成绩 | 原因或事实 | 新进程原生读回 |
|---|---|---:|---|---|---|
| normal-03-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | Native ground command point is solid: [284, 307] | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-04-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No native water route BuildPlan{x=278, y=310, rotation=0, block=turbine-condenser, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=281, y=303, rotation=0, block=breach, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-05-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=272, y=318, rotation=1, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=276, y=329, rotation=1, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-06-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=278, y=341, rotation=2, block=plasma-bore, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=276, y=329, rotation=1, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-07-v034 | 原生失核失败 | 1249.8833 | 0星 | wave见trial.csv；建造192，命令0，核心最低-0.001099141 | 433标签一致；931观察字段→931，差异0 |
| normal-08-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=283, y=284, rotation=0, block=impact-drill, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=271, y=321, rotation=0, block=core-bastion, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-09-v034 | 原生失核失败 | 1254.0333 | 0星 | wave见trial.csv；建造172，命令163，核心最低-7.3958247E-4 | 392标签一致；883观察字段→879，差异5 |
| normal-10-v034 | 原生失核失败 | 1249.65 | 0星 | wave见trial.csv；建造172，命令162，核心最低-0.0035857793 | 391标签一致；887观察字段→883，差异5 |
| normal-11-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=269, y=322, rotation=0, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=266, y=339, rotation=2, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-12-v034 | 原生失核失败 | 715.81665 | 0星 | wave见trial.csv；建造199，命令0，核心最低0.0 | 451标签一致；934观察字段→934，差异0 |
| normal-13-v034 | 原生失核失败 | 1274.0834 | 0星 | wave见trial.csv；建造259，命令139，核心最低-0.006453069 | 560标签一致；1132观察字段→1124，差异9 |
| normal-14-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=269, y=322, rotation=0, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=266, y=339, rotation=2, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-15-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No duct route BuildPlan{x=269, y=322, rotation=0, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=276, y=329, rotation=2, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-16-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No native directional duct route BuildPlan{x=269, y=322, rotation=0, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=276, y=329, rotation=2, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-17-route-diagnostic-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No native directional duct route BuildPlan{x=269, y=322, rotation=0, block=duct-router, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=276, y=329, rotation=2, block=tank-fabricator, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-18-v034 | 0 tick蓝图预检中止 | 0.0 | 未战斗/0星 | No native water route BuildPlan{x=267, y=315, rotation=0, block=turbine-condenser, breaking=false, progress=0.0, initialized=false, config=null} -> BuildPlan{x=280, y=325, rotation=0, block=diffuse, breaking=false, progress=0.0, initialized=false, config=null} | partial.msav新进程可读；未在保存前捕获全字段，不能声称全字段一致 |
| normal-19-v034 | 4800秒上限未攻下核心 | 4800.0 | 0星 | wave见trial.csv；建造269，命令1231，核心最低0.45357782 | 571标签一致；1173观察字段→1173，差异0 |

## 保存与续玩边界

- 07：900秒433标签/916观察字段同进程保存续玩全等；结束世界新进程433标签/931字段全等。
- 09/10：900秒检查见原`checkpoints.txt`；结束世界标签全等，分别缺1个Stell及总单位计数字段（5个差异）。失核后玩家单位上限为0，原生UnitComp.add在读回添加超上限单位时触发unitCapDeath；身份、位置、生命、携带物字段随单位消失。没有把它们标成世界全等。
- 12：未到900秒即失核；结束新进程451标签/934字段全等。
- 13：900秒566标签/1158观察字段续玩全等；结束560标签一致，缺2个Stell和单位总计（9个差异），同样保留具体world-differences.txt。
- 19：900秒548标签/1106观察字段、2400秒571标签/1177观察字段续玩全等；结束新进程571标签/1173观察字段全等，仍未胜利且0星。
- 全部17个原生世界（11个partial、6个end）由独立新进程实际读取；读档后原有主线15星与交错0星保持，读档未授分。partial案例只证明可读及观察事实，未捕获保存前比较基准。

## 原始诊断保留

- 07只读FailureAudit已写建筑诊断/图像，随后对已毁核心的诊断访问发生NullPointerException；原console保留。该诊断错误未当游戏失败原因，也不当完整读回通过。独立fresh-process-readback另行成功记录实际世界。
- 10在源准备Python脚本报ValueError后运行了仍在磁盘的09策略；冻结的Java源码与实际旧策略一致，属于真实重复策略失核，不能称调整后的策略失败。
- 14初次javac报告lambda捕获非final变量，未启动Java；修正只读辅助方法后才运行冻结的0 tick布置，源码以实际案例driver-source为准。没有重构造未保存的编译失败代码。
- 19原生Stell停在181,325区域，实际结束世界只读寻路发现该地面区至敌核心近区没有无阻挡地面路线。之后使用原生已开放的Merui制造与跨山壁进攻在上限外案例21得到真实普通2星；不回填本冻结范围。

本快照不增加MAX覆盖；全部168项主动实战仍未完成。资料索引记录事实，不据这些失败认定关卡不可能。
