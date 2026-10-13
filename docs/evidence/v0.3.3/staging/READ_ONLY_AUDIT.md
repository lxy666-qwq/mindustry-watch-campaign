# v0.3.3 集成后只读复查

核对对象为 D 当前集成的 CampaignStars、BuildingComp、InputHandler 与原生运输／生产／保存代码。本复查没有写 D，没有运行 Gradle，也没有登记新增实战通关。

本次没有找到不经真实产出或正数量人工实际投放、仅将核心库存自动卸载回核心就能获得运输任务 A 的新增路径。下列结论依据具体当前代码；不扩展为逐物品或完整时间因果追踪的保证。

1. **首次实际产物不会漏来源。** `BuildingComp.java:1009` 的 offload 先调用 produced，再交给邻接 handleItem 或本地库存；`1047-1048` 只在正数量调用 recordItemProduction。普通 Drill 在 `Drill.java:318` 使用 offload。GenericCrafter 在 `GenericCrafter.java:311` 先实际 consume，`326` 逐个 offload 真实整数产物。BeamDrill 则在 `BeamDrill.java:257-258` 先实际 items.add、登记 produced，之后才 dump。因而首次向带、核心或交叉器实际输出时，`CampaignStars.java:539-548` 的同物品、同资产来源已经存在。

2. **人工投放遵守实际接收契约。** `InputHandler.java:539-541` 先由原生 acceptStack 算真正可接收量，再调用 transferItemTo；`255-258` 的新 hook 位于正数量 handleStack 之后。0 量的画面动画不写来源。Conveyor 重写 handleStack，所以 hook 放在 InputHandler 能覆盖真实玩家给带投放；只放 BuildingComp 基类会漏。核心及 linked Storage 的投放在 `CampaignStars.java:552-558` 只写核心边界，不写非核心 origin。

3. **Junction 通道不能借人工 lane4 来源。** `Junction.java:50-51` 和 `DuctJunction.java:145-146` 原生 acceptStack 均为 0；其 handleItem 根据输入方向缓冲，并按同一方向输出。`CampaignStars.java:457-463` 为交叉器每个直行方向独立节点，`581-606` 两次遍历只连实际资源／方向边。最终 transferred 虽按资产汇总，但 Junction 本身不属于基本传送带/物流管计数，算法也不使用 transferred 标志作为上游 origin，不能由另一通道的已记分标志继续传播信用。

4. **核心附属库存与历史 token 的处理符合原生库存契约。** `CoreBlock.java:769-770` 将附属 Storage 的 items 指向同一核心库存并设置 linkedCore；`StorageBlock.java:64-72` 的实际单物品接收转发给核心，而 Unloader 的输出来源仍可能是 Storage 本身。`CampaignStars.java:534-536` 因此把 CoreBuild 和 linked Storage 都当核心库存边界。`528-531` 先同步当前规则缓存，避免连续两局从头编号的 token 碰撞丢标签。边界存在持久 stars.core.<asset>，遍历在 `589` 停止，因此已观察为核心库存的容器解绑或核心对象消失后仍不穿透；现有附属容器同/异核心、历史解绑保存测试覆盖此行为。

5. **核心只评估当前交接来源，纯上游回流支路不会借合流矿路。** `CampaignStars.java:524` 从本次 source 节点开始，没有从整个 Core 的历史 incoming 集合开始。先取该来源的祖先候选图并在历史核心处截断，再仅从候选中的同物品非核心 origin 向下游传播（`596-603`）。纯 Core→卸载器→合流点是另一条没有 origin 的上游路径，不能因为合流点另收到矿物而反向获信用。弹药／真实生产使用非 origin 限制的终端遍历，但也在核心处截断，保留实际核心物资投入又不回穿整个核心旧入库图。

6. **保存与载荷身份的顺序一致。** `SaveVersion.java:65-73` 先读地图／实体，再读专用 custom chunk；`SaveIO.java:172-173` 在整个版本读完后才触发 SaveLoadEvent。`CampaignStars.java:136-140` 此时恢复载荷资产并重建／迁移流向，来源与历史核心标签已由块恢复。`BuildPayload.java:37-40` 是原生实际落地入口，恢复资产身份；`CampaignStars.java:388-396` 不将工厂新造或无追踪载荷当玩家新增建设，已追踪资产保留 token。单位及建筑内部载荷的存档快照在 `403-445` 递归保存／恢复；来源、核心边界和输送标志都以同一 token 标识，不因载荷落地位置改变而清除。正常保存读回及大台账、载荷测试已在 C 整类专项运行。

接受的规则边界需要继续明写：

- 在原生规则允许人工投放的关卡，正常玩家从核心拿有限铜，实际给新传送带投放，再送进核心，是明示保留的人工物流来源。它与自动卸载器的纯核心回流区别在玩家实际操作，并不要求这次铜由新矿机生产。
- 一个产出设施曾真正产出某物品后，同一资产、同物品 origin 会保留；以后该设施转发同物品混合库存仍能满足静态来源节点。新支路还须有实际资源交接边和真实终端，但系统不逐件区分“本次物品”是否正是最初产物。
- 同一资源的历史实际边会保留。若路由器、仓库或可实际回转的环路改变了后来资源的分配，本修订判断的是曾存在的真实来源→终端路径，而非逐帧重放每一粒物品的完整路线。纯单向核心上游支路已排除；历史有效来源可达的支路仍按既定静态证据标准计数。
- 曾被记录为核心库存的同一容器 token 保留边界，即使解绑后重新投入非核心物资，也不会用旧 token 作为新核心入库来源。这是保持历史核心库存边界的保守取舍；原生拆建会分配新资产。
