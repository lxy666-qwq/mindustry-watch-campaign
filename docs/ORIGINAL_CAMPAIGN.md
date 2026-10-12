# 原始版本详细关卡设置（Mindustry v160.7）

原版玩法仍保留。本资料从`D:/软件工程/版本归档/v0.1.0/Mindustry.jar`读取原游戏自己的内容和地图，不使用守望的十关参数替代原版设置。

本次读取到95个预设地图：Serpulo 78个（32个命名关卡、46个编号预设），Erekir 17个。Serpulo的其他普通编号区块通过星球生成器生成，不属于这95个预设，无法用一张固定地图参数表代替。Erekir的原规则不允许降落普通编号区块。

## 如何查看原版

本地运行`D:/软件工程/版本归档/v0.1.0/本机启动.bat`。它直接使用已归档原版程序和独立存档。根目录的`开始游戏.bat`运行当前构建产物；最新版本里也保留两个原版星球。

下面每个关卡名都链接到详细页，含地图尺寸、资源、科技前置、波次参数、全部敌人编组、天气、地图目标和世界处理器脚本。对应原始地图仍在v0.1.0源码包及程序内部的`maps/`目录。

## 参数阅读方法

- `captureWave`、`winWave`是原代码的阈值字段，不能直接当作已经实际出兵的波数。引擎在出兵后增加`state.wave`，胜利还检查存活敌人和出兵状态；教学目标与脚本也会调整流程。
- `captureWave=0`不代表没有挑战。很多关卡是进攻模式，普通胜利判定为敌方阵营不再存活；地图目标、处理器和`canGameOver`等规则可进一步控制阶段。
- 交错丘陵的`attackAfterWaves=true`，先进行波次阶段，随后切换进攻；其余关卡以各详细页的初始规则为准。
- 波间时间是原始字段值，战役难度倍率和教程流程会影响实际等待。`initialWaveSpacing=0`时，普通启动流程以两倍波间隔作为默认值。
- 资源表表示初始地图扫描到的矿产与液体类型，不等于开局即可采集；设备研究、战争迷雾、建筑和敌军控制仍会限制利用，脚本也可能开放后续区域。地表格数统计包含地图边缘，不作为可建造面积。
- 未启用波次的进攻地图也可能保留默认编组，不能据此认定它会自动刷怪。天气`cooldown`等初始化值来自本次加载，不表示固定的事件触发时刻。
- 科技父节点与额外目标一起影响解锁。免费开放的编号预设也可能受战役实际降落条件影响。
- `requireUnlock=false`表示不需要研究关卡；`alwaysUnlocked=true`表示自带解锁。护盾前置另行列出，例如编号27受246、244、242三个区块影响。难度为原始字段，不按满分10解释；编号27的特殊难度值为13。
- **Serpulo编号已经重排**：例如零号地区在Java中保留原位置15，当前星球数据落点为170。查看或修改时应同时核对`planets/serpulo.json`，不能只用旧位置定位。

## 命名关卡

“阈值”列为原始`captureWave`字段，0不表示零波通关。


### Serpulo

| 关卡（点击详细设置） | 当前编号 | 旧位置 | 尺寸 | 难度 | 初始模式 | 阈值 | 资源 |
| --- | ---: | ---: | --- | ---: | --- | ---: | --- |
| [零号地区](original-v160.7/levels/groundZero.md) | 170 | 15 | 256×256 | 1 | 波次防守 | 10 | 铅、铜 |
| [盐碱荒滩](original-v160.7/levels/saltFlats.md) | 98 | 101 | 300×300 | 5 | 进攻 | 0 | 废料、沙、煤炭、钛、铅、铜 |
| [实验禁区](original-v160.7/levels/testingGrounds.md) | 169 | 3 | 300×300 | 7 | 波次防守 | 33 | 废料、沙、煤炭、石油、钍、钛、铅、铜 |
| [冰冻森林](original-v160.7/levels/frozenForest.md) | 64 | 86 | 200×200 | 2 | 波次防守 | 15 | 煤炭、铅、铜 |
| [生物质合成区](original-v160.7/levels/biomassFacility.md) | 23 | 81 | 250×250 | 3 | 波次防守 | 20 | 废料、水、沙、煤炭、铅、铜 |
| [孢染丛林](original-v160.7/levels/taintedWoods.md) | 145 | 221 | 280×280 | 5 | 波次防守 | 33 | 水、沙、煤炭、石油、钛、铅、铜 |
| [陨坑战场](original-v160.7/levels/crateredBattleground.md) | 219 | 18 | 256×256 | 2 | 波次防守 | 20 | 废料、水、沙、煤炭、铅、铜 |
| [遗迹海岸](original-v160.7/levels/ruinousShores.md) | 41 | 213 | 298×298 | 3 | 波次防守 | 30 | 废料、水、沙、煤炭、铅、铜 |
| [深险峡湾](original-v160.7/levels/perilousHarbor.md) | 214 | 47 | 380×380 | 4 | 进攻 | 0 | 废料、水、沙、煤炭、钛、铅、铜 |
| [工业区 32M](original-v160.7/levels/facility32m.md) | 65 | 64 | 220×280 | 4 | 波次防守 | 25 | 废料、水、沙、煤炭、钛、铅、铜 |
| [风吹群岛](original-v160.7/levels/windsweptIslands.md) | 97 | 246 | 400×400 | 4 | 波次防守 | 30 | 废料、水、沙、煤炭、钛、铅、铜 |
| [绵延群山](original-v160.7/levels/stainedMountains.md) | 223 | 20 | 300×300 | 3 | 波次防守 | 30 | 废料、水、沙、煤炭、钛、铅、铜 |
| [萃取前哨](original-v160.7/levels/extractionOutpost.md) | 213 | 165 | 360×400 | 5 | 进攻 | 0 | 水、沙、煤炭、石油、钍、钛、铅、铜 |
| [边际海湾](original-v160.7/levels/coastline.md) | 164 | 108 | 432×432 | 5 | 波次防守 | 30 | 水、沙、煤炭、钍、钛、铅、铜 |
| [风化海峡](original-v160.7/levels/weatheredChannels.md) | 166 | 39 | 500×500 | 9 | 波次防守 | 40 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [海军要塞](original-v160.7/levels/navalFortress.md) | 165 | 216 | 400×400 | 8 | 进攻 | 0 | 水、沙、煤炭、石油、钍、钛、铅、铜 |
| [边陲哨站](original-v160.7/levels/frontier.md) | 86 | 50 | 320×320 | 4 | 进攻 | 0 | 水、沙、煤炭、钛、铅、铜 |
| [真菌通道](original-v160.7/levels/fungalPass.md) | 221 | 21 | 150×250 | 2 | 进攻 | 0 | 水、沙、煤炭、铅、铜 |
| [菌疫峡谷](original-v160.7/levels/infestedCanyons.md) | 85 | 210 | 200×265 | 4 | 进攻 | 0 | 废料、水、沙、煤炭、钛、铅、铜 |
| [环礁群岛](original-v160.7/levels/atolls.md) | 75 | 1 | 432×432 | 7 | 进攻 | 0 | 水、沙、煤炭、石油、钍、钛、铅、铜 |
| [沉没码头](original-v160.7/levels/sunkenPier.md) | 173 | -1 | 450×450 | 8 | 波次防守 | 50 | 水、沙、煤炭、石油、钍、钛、铅、铜 |
| [菌丝堡垒](original-v160.7/levels/mycelialBastion.md) | 143 | 260 | 300×300 | 8 | 进攻 | 0 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [增生区](original-v160.7/levels/overgrowth.md) | 142 | 134 | 344×344 | 5 | 进攻 | 0 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [焦油田](original-v160.7/levels/tarFields.md) | 99 | 23 | 260×260 | 5 | 波次防守 | 40 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [冲击区 0078](original-v160.7/levels/impact0078.md) | 266 | 227 | 300×300 | 7 | 波次防守 | 45 | 废料、水、煤炭、钍、钛、铅、铜 |
| [荒芜裂谷](original-v160.7/levels/desolateRift.md) | 271 | 123 | 110×400 | 8 | 波次防守 | 18 | 废料、水、沙、煤炭、钍、钛、铅、铜 |
| [核裂阵](original-v160.7/levels/nuclearComplex.md) | 228 | 130 | 250×250 | 7 | 波次防守 | 50 | 废料、水、沙、煤炭、钍、钛、铅、铜 |
| [海滨船厂](original-v160.7/levels/littoralShipyard.md) | 204 | 204 | 600×600 | 9 | 进攻 | 0 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [行星发射终端](original-v160.7/levels/planetaryTerminal.md) | 217 | 93 | 512×512 | 10 | 进攻 | 0 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [飞船遗址](original-v160.7/levels/fallenVessel.md) | 95 | -1 | 597×597 | 8 | 波次防守 | 70 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [熔石要塞](original-v160.7/levels/geothermalStronghold.md) | 264 | 264 | 470×470 | 10 | 进攻 | 0 | 废料、水、沙、煤炭、石油、钍、钛、铅、铜 |
| [cruxscape](original-v160.7/levels/cruxscape.md) | 54 | 54 | 414×414 | 10 | 进攻 | 0 | 水、沙、煤炭、钍、钛、铅、铜 |

### Erekir

| 关卡（点击详细设置） | 当前编号 | 旧位置 | 尺寸 | 难度 | 初始模式 | 阈值 | 资源 |
| --- | ---: | ---: | --- | ---: | --- | ---: | --- |
| [始发地区](original-v160.7/levels/onset.md) | 10 | 10 | 512×512 | 1 | 进攻 | 0 | 石墨、铍 |
| [庇护前哨](original-v160.7/levels/aegis.md) | 88 | 88 | 400×400 | 3 | 进攻 | 0 | 石墨、钨、铍 |
| [岩浆湖](original-v160.7/levels/lake.md) | 41 | 41 | 320×320 | 4 | 进攻 | 0 | 石墨、矿渣、铍 |
| [交错丘陵](original-v160.7/levels/intersect.md) | 36 | 36 | 484×484 | 5 | 波次后转进攻 | 9 | 石墨、矿渣、芳油、钨、铍 |
| [风化山脉](original-v160.7/levels/atlas.md) | 14 | 14 | 586×586 | 5 | 进攻 | 0 | 石墨、矿渣、钨、铍 |
| [横断山谷](original-v160.7/levels/split.md) | 19 | 19 | 600×600 | 2 | 进攻 | 0 | 石墨、矿渣、钨、铍 |
| [风蚀盆地](original-v160.7/levels/basin.md) | 29 | 29 | 586×586 | 6 | 进攻 | 0 | 石墨、矿渣、芳油、钨、铍 |
| [芳油湿地](original-v160.7/levels/marsh.md) | 25 | 25 | 586×586 | 4 | 进攻 | 0 | 石墨、芳油、钨、铍 |
| [横堑峰峦](original-v160.7/levels/peaks.md) | 30 | 30 | 310×390 | 3 | 进攻 | 0 | 石墨、芳油、钨、铍 |
| [贫瘠峡谷](original-v160.7/levels/ravine.md) | 39 | 39 | 494×494 | 4 | 波次防守 | 24 | 石墨、矿渣、芳油、钨、铍 |
| [破碎火山](original-v160.7/levels/caldera-erekir.md) | 43 | 43 | 450×450 | 4 | 进攻 | 0 | 石墨、矿渣、芳油、钨、铍 |
| [晶石要塞](original-v160.7/levels/stronghold.md) | 18 | 18 | 586×586 | 7 | 进攻 | 0 | 石墨、芳油、钍、钨、铍 |
| [碳岩裂隙](original-v160.7/levels/crevice.md) | 3 | 3 | 494×494 | 6 | 波次防守 | 46 | 石墨、矿渣、芳油、钍、钨、铍 |
| [平行岭谷](original-v160.7/levels/siege.md) | 58 | 58 | 400×600 | 8 | 进攻 | 0 | 石墨、矿渣、芳油、钨、铍 |
| [十字路口](original-v160.7/levels/crossroads.md) | 37 | 37 | 600×420 | 7 | 进攻 | 0 | 石墨、矿渣、芳油、钍、钨、铍 |
| [岩溶洞穴](original-v160.7/levels/karst.md) | 5 | 5 | 600×600 | 9 | 波次防守 | 10 | 石墨、矿渣、芳油、钍、钨、铍 |
| [起源](original-v160.7/levels/origin.md) | 12 | 12 | 600×600 | 10 | 进攻 | 0 | 石墨、矿渣、芳油、钍、钨、铍 |

## Serpulo编号预设地图

这些是原版已内置的地图文件，不是本项目新增关卡。

| 编号（点击详细设置） | 尺寸 | 难度 | 初始模式 | 阈值 | 敌方核心 |
| --- | --- | ---: | --- | ---: | ---: |
| [0](original-v160.7/levels/sector-serpulo-0.md) | 350×350 | 8 | 进攻 | 0 | 5 |
| [6](original-v160.7/levels/sector-serpulo-6.md) | 420×420 | 8 | 进攻 | 0 | 4 |
| [13](original-v160.7/levels/sector-serpulo-13.md) | 430×430 | 8 | 进攻 | 0 | 3 |
| [16](original-v160.7/levels/sector-serpulo-16.md) | 432×432 | 5 | 进攻 | 0 | 2 |
| [19](original-v160.7/levels/sector-serpulo-19.md) | 414×414 | 8 | 进攻 | 0 | 4 |
| [20](original-v160.7/levels/sector-serpulo-20.md) | 413×413 | 8 | 进攻 | 0 | 4 |
| [24](original-v160.7/levels/sector-serpulo-24.md) | 440×440 | 8 | 进攻 | 0 | 3 |
| [27](original-v160.7/levels/sector-serpulo-27.md) | 600×600 | 13 | 进攻 | 0 | 28 |
| [30](original-v160.7/levels/sector-serpulo-30.md) | 432×432 | 10 | 进攻 | 0 | 3 |
| [47](original-v160.7/levels/sector-serpulo-47.md) | 440×440 | 8 | 进攻 | 0 | 3 |
| [55](original-v160.7/levels/sector-serpulo-55.md) | 400×500 | 10 | 进攻 | 0 | 3 |
| [66](original-v160.7/levels/sector-serpulo-66.md) | 430×430 | 8 | 进攻 | 0 | 3 |
| [67](original-v160.7/levels/sector-serpulo-67.md) | 470×470 | 10 | 进攻 | 0 | 3 |
| [69](original-v160.7/levels/sector-serpulo-69.md) | 300×300 | 5 | 进攻 | 0 | 4 |
| [76](original-v160.7/levels/sector-serpulo-76.md) | 430×430 | 8 | 进攻 | 0 | 4 |
| [92](original-v160.7/levels/sector-serpulo-92.md) | 450×450 | 8 | 进攻 | 0 | 3 |
| [94](original-v160.7/levels/sector-serpulo-94.md) | 320×320 | 5 | 进攻 | 0 | 2 |
| [111](original-v160.7/levels/sector-serpulo-111.md) | 600×600 | 10 | 进攻 | 0 | 5 |
| [116](original-v160.7/levels/sector-serpulo-116.md) | 414×414 | 5 | 进攻 | 0 | 3 |
| [127](original-v160.7/levels/sector-serpulo-127.md) | 418×418 | 8 | 进攻 | 0 | 2 |
| [133](original-v160.7/levels/sector-serpulo-133.md) | 418×418 | 8 | 进攻 | 0 | 8 |
| [161](original-v160.7/levels/sector-serpulo-161.md) | 414×414 | 8 | 进攻 | 0 | 4 |
| [162](original-v160.7/levels/sector-serpulo-162.md) | 415×440 | 10 | 进攻 | 0 | 4 |
| [176](original-v160.7/levels/sector-serpulo-176.md) | 440×440 | 8 | 进攻 | 0 | 8 |
| [180](original-v160.7/levels/sector-serpulo-180.md) | 440×440 | 8 | 进攻 | 0 | 4 |
| [185](original-v160.7/levels/sector-serpulo-185.md) | 370×370 | 5 | 进攻 | 0 | 2 |
| [191](original-v160.7/levels/sector-serpulo-191.md) | 390×390 | 10 | 进攻 | 0 | 2 |
| [192](original-v160.7/levels/sector-serpulo-192.md) | 464×464 | 8 | 进攻 | 0 | 3 |
| [197](original-v160.7/levels/sector-serpulo-197.md) | 414×414 | 8 | 进攻 | 0 | 2 |
| [200](original-v160.7/levels/sector-serpulo-200.md) | 440×440 | 3 | 进攻 | 0 | 2 |
| [202](original-v160.7/levels/sector-serpulo-202.md) | 275×275 | 5 | 波次防守 | 33 | 0 |
| [207](original-v160.7/levels/sector-serpulo-207.md) | 434×434 | 8 | 进攻 | 0 | 3 |
| [225](original-v160.7/levels/sector-serpulo-225.md) | 414×414 | 8 | 进攻 | 0 | 5 |
| [230](original-v160.7/levels/sector-serpulo-230.md) | 450×550 | 5 | 进攻 | 0 | 4 |
| [240](original-v160.7/levels/sector-serpulo-240.md) | 300×300 | 8 | 进攻 | 0 | 3 |
| [242](original-v160.7/levels/sector-serpulo-242.md) | 600×600 | 10 | 进攻 | 0 | 9 |
| [243](original-v160.7/levels/sector-serpulo-243.md) | 600×600 | 10 | 进攻 | 0 | 4 |
| [244](original-v160.7/levels/sector-serpulo-244.md) | 520×520 | 10 | 进攻 | 0 | 4 |
| [245](original-v160.7/levels/sector-serpulo-245.md) | 350×600 | 10 | 进攻 | 0 | 21 |
| [246](original-v160.7/levels/sector-serpulo-246.md) | 432×432 | 10 | 进攻 | 0 | 6 |
| [247](original-v160.7/levels/sector-serpulo-247.md) | 512×512 | 10 | 进攻 | 0 | 5 |
| [248](original-v160.7/levels/sector-serpulo-248.md) | 375×375 | 5 | 进攻 | 0 | 2 |
| [254](original-v160.7/levels/sector-serpulo-254.md) | 430×430 | 10 | 进攻 | 0 | 5 |
| [259](original-v160.7/levels/sector-serpulo-259.md) | 414×414 | 8 | 进攻 | 0 | 1 |
| [263](original-v160.7/levels/sector-serpulo-263.md) | 480×480 | 10 | 波次防守 | 0 | 3 |
| [265](original-v160.7/levels/sector-serpulo-265.md) | 414×414 | 8 | 进攻 | 0 | 2 |

## 原始依据与验证

- `core/src/mindustry/content/SectorPresets.java`：命名预设、胜利阈值、难度与基础属性。
- `core/src/mindustry/maps/SectorSubmissions.java`：社区预设地图注册。
- `core/assets/maps/`：具体地图、矿点、出生点、建筑、目标与处理器配置。
- `core/assets/planets/serpulo.json`：Serpulo落点重排。
- `SerpuloTechTree.java`与`ErekirTechTree.java`：解锁顺序及研究、占领前置。
- `Planets.java`、`Rules.java`、`Logic.java`、`SpawnGroup.java`：星球规则、默认字段、胜利判定及出兵公式。

读取时使用独立工作目录，未推进脚本或人工通关。95个地图均由归档原版引擎加载；导出的95份规则JSON可解析，敌人编组和地图目标数量与运行时读取结果一致；326份世界处理器文本与地图中的处理器数量一致。原始读取记录保存在本机`工作记录/原版关卡提取.log`。

[全部预设元数据JSON](original-v160.7/campaign.json) · [守望十关设置](CAMPAIGN.md)

读取工具：[OriginalCampaignExport.java](../tools/coursework/OriginalCampaignExport.java)。以JDK17编译时，将v0.1.0的`Mindustry.jar`放入classpath，再运行`OriginalCampaignExport <独立输出目录>`；本资料使用的是归档原版JAR。
