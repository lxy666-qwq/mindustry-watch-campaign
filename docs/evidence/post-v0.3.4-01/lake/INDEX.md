# 岩浆湖v0.3.4独立QA冻结索引

统一固定程序：local-work/mindustry-star-work/star034-runtime/Mindustry.jar（classpath首位）、同目录assets。程序自报版本、源、console、CSV、实际msav及读回都在每个独立case内冻结，不以当前驾驶器后续mtime回填旧运行。私人profile/settings只留本机，公开材料不包含设置或账号数据。

|case|结果|新进程验证|
|---|---|---|
|coolant-01-v034|隔离装备对照，A14、19.75真实水耗，未胜利/0新星|601标签/1281观察字段全等；旧A==0断言exit2，新版只读预期验收exit0。|
|normal-14-v034|原生胜利、Normal3，1706.9872任务秒，核心100%、建筑损失0|390标签/1471观察字段全等，真实main13。|
|heroic-01-v034|原生败退/英雄0，建筑损失29，核心摧毁|标签相等但失败世界5艘Elude因原生0单位上限读回消失；原差异及defeat-cap-audit保留，不称全等。|
|heroic-02-v034|原生胜利但损失43>22，英雄0|完整标签与观察字段全等，main13/earned0。|
|heroic-03-v034|3600秒未胜利/超时，英雄0，核心100%、损失12|完整标签与观察字段全等，main13/earned0。|
|heroic-04-v034|3600秒未胜利/超时，英雄0，核心100%、损失9|完整标签与观察字段全等，main13/earned0。|
|heroic-05-v034|原生胜利、Heroic1，1724.8146秒<2580，核心100%、损失8<=22|397标签/1463观察字段全等，真实main14。|
|tactical-01-v034|1680秒诊断在ConstructBuild上读取LiquidModule而中止，未记通关|仅900秒checkpoint，异常导致无终局end；FIXTURE_FAILURE/source/console保留。|
|tactical-02-v034|原生胜利、Tactical1，A20/B2/建设135<302，1721.52秒|1023标签/1517观察字段全等，真实main15，精确A/B在fresh-task-audit。|

所有case路径为 local-work/lake-trials/<case>/。各成功case的QA_REPORT列出真实门禁、沿用设置来源、原生存读继续与精确要求。原始v033普通失败08—13另见Lake-failed-08-13-index.md，全部保留。v034发布快照冻结32项不回写；英雄05与战术02为发布后的2项新增实际满分，由总台账管理者核对后记入补充。
