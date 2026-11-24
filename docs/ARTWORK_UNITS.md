# v0.6.0独立单位美术

两个PNG均通过本次内置图像生成工具制作，生成模式为新图，透明背景，无参考图。不是从Mindustry原单位贴图改色，也不是从王国保卫战提取素材。实际可分发文件保存在 `core/assets/watch-units/`，随程序和源码发布。

| 文件 | 用途 | 接入 |
|---|---|---|
| `sentinel.png` | 灰色机体、青绿四翼和金色驾驶舱的支援机 | 哨兵机体、预览、单位图标，固定18世界单位绘制 |
| `carrier.png` | 橙灰履带底盘、装甲和琥珀乘员舱 | 运兵车机体、预览、单位图标，固定25世界单位绘制 |

生成提示语的要点如下：

> Sentinel: ONE original centered top-down support aircraft pointing UP, graphite-grey body, mint/turquoise armor and a small gold cockpit, four swept prongs, strong readable silhouette at 32x32, flat game sprite, no text, no floor or cast shadow, true transparent background, 15% margin. An original design, not an existing Mindustry sprite or a Kingdom Rush asset.

> Carrier: ONE original centered top-down armored brood-carrier tank pointing UP, squat hexagonal grey chassis, orange armor, two tracks, short front cannon, amber chamber containing two folded spider drones, strong readable silhouette at 32x32, flat game sprite, no text, no floor or cast shadow, true transparent background, 15% margin. An original design, not an existing game asset.

生成后原样复制PNG，没有用代码重绘、拼接或抠图。通用小型武器、爬虫和部署台仍使用上游素材；不把这些部分称为新原画。

`WatchSpriteUnitType`在内容加载时直接创建纹理，并注册到现有图集，由图集管理释放；不在内容加载回调中再次阻塞等待同一资源管理器。SDL实景中的白色受击闪烁仍沿用上游绘制机制，展示截图使用暂停场景显示原色。演示中的人工单位/材料仅用于显示验收，不计实战成绩。
