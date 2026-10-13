# 官方46关目标机制只读审核

审查范围：当前 CampaignStars.loadMission、Logic、MapObjectives、UnlockableContent、CoreBlock、原版导出的46关 Rules JSON及其82份世界处理器脚本，共119个地图目标。

另外实际加载了46张地图到独立 C 盘审查运行环境，核对加载后的规则、队伍核心、地图目标数与分阶段标签；没有推进波次、摧毁核心、伪造胜利或授星。实际加载过程保留0星且未创建原生扇区存档。结果见 actual-loaded-goals.tsv。

## 结论与范围

- 当前加载后20关是有限波次防守，25关是攻击，1关是防守后攻击（intersect）。全部canGameOver=true，玩家核心存在。
- 46关实际初始核心队伍只有defaultTeam及waveTeam，因此非战役进攻判定的“只剩一个存活队伍”与原战役“waveTeam无核心”针对初始配置一致。世界处理器也未发现向第三队伍创建核心的语句；残余单位不妨碍两种判定，因为TeamData.isAlive只检查核心。
- 所有有限防守关仍有正winWave。intersect单独以stars.phaseWave=9保存原波次阶段，暂将winWave=0，波次清空后再启用attackMode，避免第一阶段提前评分。
- rules.sector=null后走Logic的普通地图结算：防守及进攻通过GameOverEvent到CampaignStars.recordVictory，毋须SectorCaptureEvent。当前原图脚本／目标completionLogicCode没有调用sectorCapture或gameover，也没有@campaign/@sector守卫。
- 地图目标仍由Logic在所有非编辑游戏内更新，世界处理器也无campaign执行限制；没有证据表明仅因sector=null导致某一个原图剧情硬阻塞。
- 这是源码和46张真实初始地图状态审核，不是46关完整通关证据。资源供应、战斗、限时与战术可达性需要持续实战记录，不能由本报告认定平衡通过。

## 具体来源

1. core/src/mindustry/content/CampaignStars.java将sector置null；104-108监听默认队伍GameOver；238-242保存attackAfterWaves；450-455结束波次后进入攻击阶段。
2. core/src/mindustry/core/Logic.java在非campaign检查玩家失败／进攻胜利／防守胜利并发GameOverEvent；395-403的sectorCapture在null时只关闭波次与攻击，不会发事件，但目前46关没有脚本或目标调用这个入口。
3. core/src/mindustry/game/Teams.java的isAlive仅hasCore；实际46关初始核心队伍见TSV，没有额外第三队伍核心。
4. core/src/mindustry/core/Logic.java无campaign门槛地更新objectives；core/src/mindustry/world/blocks/logic/LogicBlock.java以disableWorldProcessors关闭特权逻辑，无sector空值门槛。
5. core/src/mindustry/game/MapObjectives.java及339的Research／Produce检查content.unlocked。core/src/mindustry/content/CampaignStars.java把原图Research/Produce/Build/Unit目标内容放入本次任务researched；core/src/mindustry/ctype/UnlockableContent.java通过CampaignStars.available读取本次researched。因此Onset的silicon-arc-furnace研究不会被原生基地科技状态卡住；Marsh与Peaks的研究步骤同理。
6. Onset原规则objective[0..19]仍保留采矿、累计向核心送入200铍／100石墨／50硅、坦克建造、指挥、弹药、防守与扩大区域阶段，详见critical-objectives.txt。docs/original-v160.7/scripts/onset/301_127.mlog:1-4检查breach实际弹药；300_127:1-5等待defStart并真实产生两辆Stell；280_208:1-4等待openMap后扩大区域。没有给这些生产／建造／弹药旗默认完成值。
7. GroundZero同样必须真实供应资源、建设炮塔／墙并满足弹药旗，95_42.mlog:1-5在allowWaves后开启计时波次。无操作测试长期没有波次属于原教程行为，不能认定加载失败。
8. core/src/mindustry/world/blocks/storage/CoreBlock.java无campaign条件地累计coreItemCount；只有sector.info出口统计有isCampaign条件，因此Onset/GroundZero的核心物流目标仍能统计。

## 原脚本是否覆盖挑战倍率

精确扫描46关82份原脚本及全部目标completionLogicCode：未发现setrule unitHealth/unitDamage/blockHealth/blockDamage/waveSpacing或attackMode/canGameOver语句。没有实证需要为这46关新增持续恢复倍率的通用补丁。

实际存在的阶段规则：
- docs\original-v160.7\scripts\onset\281_208.mlog:3:setrule unitBuildSpeed speed @crux 0 100 100
- docs\original-v160.7\scripts\basin\153_57.mlog:7:setrule rtsMinWeight weight @malis 0 100 100
- docs\original-v160.7\scripts\basin\153_57.mlog:9:setrule unitBuildSpeed weight @malis 0 100 100

这些unitBuildSpeed/rtsMinWeight决定敌方生产节奏／编队阈值，loadMission当前挑战未改这些字段，保留原阶段语义。普通编号图sector-serpulo-254的waveSpacing脚本不在本次46主线范围。

未来新增脚本如果真的直接赋值挑战修改的字段，最小修复应在原setrule赋值路径按“原脚本值×本次模式倍率”计算，且只作用带五星任务标签的目标敌队；不能每帧重复乘现值或覆盖为固定基础值。当前扫描没有这类实际目标脚本，故本审核未建议现在添加这种推测性改动。
