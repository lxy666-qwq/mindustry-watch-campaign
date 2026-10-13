# 原生战斗试玩驱动

这三个独立入口供复查具体策略和真实星级进度，不属于游戏主程序。`LegalWatchTrial`负责守望支线防守，`TutorialTrial`完成零号地区的采矿、建设、供弹及原图教程；`TrialProfileRead`只读重启后的星级成绩。

游戏按60 Hz原生更新运行，使用真实建造计划、移动、单位武器、敌军寻路与资源消耗。玩家补弹使用原生`InputHandler`取料、投放和间隔。驱动不增加库存、不设置目标旗、不改波次和生命、不触发胜利、不直接写入星数。运行内使用无图形后端，缺少客户端音频元数据的更新被省略，战斗、物流、地图目标、星级结算和存盘继续执行。它能验证一个具体策略，不能代替真实客户端完整游玩的操作体验。

从仓库根目录使用JDK17编译，先完成`desktop:dist`：

```powershell
New-Item -ItemType Directory -Force trial-classes
javac -encoding UTF-8 -cp 'core/build/classes/java/main;desktop/build/libs/Mindustry.jar' -d trial-classes tools/coursework/playtrial/LegalWatchTrial.java tools/coursework/playtrial/TutorialTrial.java tools/coursework/playtrial/TrialProfileRead.java
```

同一全新进度档的示例链：

```powershell
java -Dtrial.profile=trial-work/chain/profile -cp 'trial-classes;core/build/classes/java/main;desktop/build/libs/Mindustry.jar' TutorialTrial defend trial-work/chain/groundZero-normal NORMAL groundZero 1800
java -Dtrial.profile=trial-work/chain/profile -cp 'trial-classes;core/build/classes/java/main;desktop/build/libs/Mindustry.jar' LegalWatchTrial defend trial-work/chain/watch1-normal NORMAL watch-1 1800
java -Dtrial.profile=trial-work/chain/profile -cp 'trial-classes;core/build/classes/java/main;desktop/build/libs/Mindustry.jar' LegalWatchTrial defend trial-work/chain/watch1-heroic HEROIC watch-1 1800
java -Dtrial.profile=trial-work/chain/profile -cp 'trial-classes;core/build/classes/java/main;desktop/build/libs/Mindustry.jar' LegalWatchTrial defend trial-work/chain/watch1-tactical TACTICAL watch-1 1800
java -cp 'trial-classes;core/build/classes/java/main;desktop/build/libs/Mindustry.jar' TrialProfileRead trial-work/chain/profile watch-1 3 1 1
```

带`trial.profile`时，启动前断言真实星级档满足关卡门禁，包括普通前置和支线满星装备。共享链必须从零开始，不能把独立试验的分数合并。省略该参数时，每次使用输出目录中的新独立档，直接选图用于关内策略测试，明确不声称完成战役解锁。输出目录必须是新目录。

驱动读取`core/assets`与当前核心编译类；可用`trial.assets`、`trial.classes`和`trial.source`指定对应路径。使用仓库同级Arc与匹配JDK17。Windows类路径分隔符为`;`，Linux为`:`。

每次留下`result.txt`、`trial.csv`、`destroyed.csv`及当次`driver-source.java`；设置文件留在本地。记录自然出兵数、胜者、核心最低生命、建筑损失、合法建造/取料/放料、任务评分和类文件修改时间。进程退出与地图胜利必须结合实际星级解释，未达英雄/战术限制时可出现“战斗胜利、0星”。无操作或策略程序错误不能当作关卡难度结论。

已发布证据位于[试玩台账](../../../docs/playthrough-v0.3.0.md)和[evidence/v0.3.0](../../../docs/evidence/v0.3.0/)。部分早期记录属于收紧终端物流规则前的编译版本，台账分开标记，不能据此宣布当前版本的全部168项任务通过。
