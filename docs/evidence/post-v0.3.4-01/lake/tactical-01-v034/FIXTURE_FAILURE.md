# 战术01诊断器中止

本例在1680秒记录两座原图涡轮经原生拆建改为冷凝器时，第二座仍是ConstructBuild，无LiquidModule。驾驶器snapshot直接读b.liquids.get导致NullPointerException，属于诊断夹具错误，不是游戏建造或存档失败。

过程原生门禁开放，真实继承Lake普通3/英雄1、main14；原图2/2目标、15舰已实际生产，未写资源/目标/成绩。已保留source、console、CSV、900秒checkpoint，但异常导致未写终局result/end.msav，不声称通关或完整世界保存。下一例只让诊断对实际正在建造的非液体建筑输出constructing，动作不改。
