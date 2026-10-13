# v0.3.3 核心回流修订暂存

这是 C 盘独立暂存成果，尚未写入 D 产品树、构建发布包或登记新增完整实战通关。基线是 D 中的 v0.3.2 CampaignStars/Tests，含专用台账保存块；运行测试时使用固定的 `onset-trials/Mindustry-v032-audit.jar` 与本目录 C 编译覆盖类。没有运行 Gradle。

## 集成文件

- `CampaignStars.java` → `core/src/mindustry/content/CampaignStars.java`
- `CampaignStarsTests.java` → `tests/src/test/java/CampaignStarsTests.java`
- `BuildingComp.java`、`InputHandler.java` 为完整源码副本，各只改一处原生调用；最小差异见 `native-hooks.patch`。
- `v033-staging.patch` 包含以上四个目标的逐行差异，不含摘要指纹。
- `Building.java` 只用于 C 手工编译验证，是现有生成类的对应调用副本。不要把它作为仓库源码集成；正常构建应由 BuildingComp 生成。
- `runtime-baseline/CampaignProgression.java` 只用于 C 测试，来自根任务已完成的 W6 前置实现，不是本子任务对 Progression 的修改。

## 实际行为

1. 原生正数量 `Building.produced(Item,int)` 同时登记该物品、该建筑资产的真实来源和原有产出任务；不把石墨产出证据扩展为该工厂库存里的铜。
2. 原生 `InputHandler.transferItemTo` 在正数量实际 `handleStack` 后登记玩家实物投放来源。0 数量传输动画不登记。人工投放到核心或附属核心库存也不登记新来源。
3. CoreBuild，以及原生 `linkedCore != null`、共享核心库存的 StorageBuild，均记为历史核心资产边界。边界随资产 token 保留，包括容器解绑、核心销毁和保存读回。
4. 收入核心的真实交接从本次最后输出来源开始回溯，在核心边界停止，再从同资源真实来源沿该候选子图向终端传播。只有位于真实来源路径上的基本运输设施可计数；合流处的纯核心卸载上游不能借矿源支路获分。
5. 核心送有效弹药、核心送后来真实产出的消费者仍可计数；这两类回溯同样在核心边界停止，不能穿回核心其他历史入库路线。液体物流、任务 B、预算和战斗数值不变。
6. `stars.flowVersion` 升为 2。战术续档在 SaveLoadEvent 后只清旧版 flow/received/transferred 及来源/核心证据，要求重新实际供给；transported、produced 时刻、资产/载荷、世界库存、预算、时间、最低核心生命与永久最佳星保留。新增 `stars.origin.*`/`stars.core.*` 走 v0.3.2 的专用保存块。
7. `unlocked` 的支线分支同时检查 CampaignProgression.allowed，使 W6 列表与启动按钮遵守同一个 W5 满五星前置。

规则仍是至少一次真实供给及静态历史交接证据，不是逐物品数量或完整时序追踪。正常玩家实际投放仍是允许来源；历史上真实使用过的路线保留其供给证据。曾并入核心的同一容器资产保留核心边界，直到原生拆建换为新资产，不逐件区分其后来混合库存。

## 已运行验证

`manual-junit-05.log`：C-only javac 编译后运行整类 CampaignStarsTests，**198 项，198 成功，0 失败／错误／跳过**；19.974 秒。测试工作目录 `staging/core/assets`，框架夹具路径明确解析到 `staging/tests/build/test_data`。源与运行类在本目录，固定基线资源复制在 `core/assets`；D 未写入。

新增或扩展了实际原生卸载／带传输、同与异核心、核心附属容器、容器解绑销毁后读档、真实钻机/石墨产出、弹药转换、工厂实际消费、同物品合流、不同物品来源、0 量传输、v1 保存迁移、W6 板面门禁。原有方向交叉、桥/路由/质量驱动器、载荷、超 64KiB 台账和普通规则隔离测试也随整类运行通过。测试中建筑和有限库存由夹具准备，不宣称为玩家完整实战。

保留此前所有试验日志：01 为 C 运行类路径缺 headless backend 的启动夹具错误；02 为合流库存比较夹具错误（同团队核心共享库存，后续仍在卸货）；03 为 196 全通过；04 的附属容器参数化测试发现并修复跨任务缓存初始化漏点，`recordCoreAsset` 现在先同步当前 rules 的流向缓存；05 为最终通过。失败日志不覆盖。

完整 D Gradle 回归、最终发布包、合法 W8 玩家操作对照和其他整场实战由根任务在集成后继续验证，本文件不报告这些尚未运行的结果。
