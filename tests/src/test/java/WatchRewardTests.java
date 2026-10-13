import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class WatchRewardTests{
    int[] oldScores = new int[3];
    boolean[] present = new boolean[3];

    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
        Core.settings.setJson(JsonIO.json);
    }

    @BeforeEach
    void setup(){
        logic.reset();
        Time.setDeltaProvider(() -> 1f);
        int index = 0;
        for(var mode : Mode.values()){
            String key = CampaignStars.progressKey(WatchCampaign.sectors[0], mode);
            present[index] = Core.settings.has(key);
            oldScores[index++] = Core.settings.getInt(key, 0);
            Core.settings.put(key, mode.maximum);
        }
        CampaignStars.loadMission(SectorPresets.frozenForest, Mode.NORMAL);
        logic.play();
        for(int x = 20; x <= 60; x++){
            for(int y = 20; y <= 60; y++){
                world.tile(x, y).setBlock(Blocks.air);
                world.tile(x, y).setFloor(Blocks.stone.asFloor());
            }
        }
    }

    @AfterEach
    void restore(){
        logic.reset();
        int index = 0;
        for(var mode : Mode.values()){
            String key = CampaignStars.progressKey(WatchCampaign.sectors[0], mode);
            if(present[index]) Core.settings.put(key, oldScores[index]);
            else Core.settings.remove(key);
            index++;
        }
    }

    Building build(Block block, int x, int y, Team team){
        world.tile(x, y).setBlock(block, team);
        Events.fire(new BlockBuildEndEvent(world.tile(x, y), null, team, false, null));
        return world.tile(x, y).build;
    }

    Building tower(int x, int y){
        assertTrue(Build.validPlace(WatchRewardBlocks.fieldBandage, Team.sharded, x, y, 0, false), "tower fixture uses a legal playable tile");
        var tower = build(WatchRewardBlocks.fieldBandage, x, y, Team.sharded);
        tower.items.add(Items.graphite, 10);
        return tower;
    }

    Building wall(int x, int y, Team team){
        var wall = build(Blocks.copperWall, x, y, team);
        wall.health = wall.maxHealth * 0.25f;
        return wall;
    }

    Building power(Building... consumers){
        var generator = build(Blocks.combustionGenerator, 25, 25, Team.sharded);
        generator.items.add(Items.coal, 20);
        if(consumers.length > 1){
            var additional = build(Blocks.combustionGenerator, 25, 27, Team.sharded);
            additional.items.add(Items.coal, 20);
            generator.power.graph.addGraph(additional.power.graph);
        }
        for(var consumer : consumers) generator.power.graph.addGraph(consumer.power.graph);
        return generator;
    }

    void run(Building generator, Building... towers){
        for(int i = 0; i < 10; i++){
            for(var producer : generator.power.graph.producers) producer.update();
            generator.power.graph.update();
            for(var tower : towers) tower.update();
        }
    }

    @Test
    void unlockIsScopedToFiveStarReward(){
        assertTrue(CampaignStars.available(WatchRewardBlocks.fieldBandage));
        assertEquals(2, WatchRewardBlocks.fieldBandage.requirements.length);
        assertEquals(50, WatchRewardBlocks.fieldBandage.requirements[0].amount);
        assertSame(Items.graphite, WatchRewardBlocks.fieldBandage.requirements[0].item);
        assertEquals(40, WatchRewardBlocks.fieldBandage.requirements[1].amount);
        assertSame(Items.silicon, WatchRewardBlocks.fieldBandage.requirements[1].item);
        Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[0], Mode.TACTICAL), 0);
        logic.reset();
        CampaignStars.loadMission(SectorPresets.groundZero, Mode.NORMAL);
        assertFalse(CampaignStars.available(WatchRewardBlocks.fieldBandage));
    }

    @Test
    void noElectricityOrNoGraphiteCannotHeal(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        float before = wall.health;
        for(int i = 0; i < 10; i++) tower.update();
        assertEquals(before, wall.health);
        assertEquals(10, tower.items.get(Items.graphite));
        tower.items.clear();
        var generator = power(tower);
        run(generator, tower);
        assertEquals(before, wall.health);
        assertFalse(state.rules.tags.containsKey(WatchRewardBlocks.fieldBandage.cooldownKey(wall)));
    }

    @Test
    void realPowerAndGraphiteRepairOneWallAtFifteenPercent(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        assertTrue(tower instanceof WatchRewardBlocks.FieldBandage.FieldBandageBuild, tower.getClass().getName());
        assertTrue(tower.team.data().buildings.contains(wall));
        assertTrue(tower.shouldConsume(), "enabled=" + tower.enabled + ", allowUpdate=" + tower.allowUpdate() + ", env=" + state.rules.env + ", supportsEnv=" + tower.block.supportsEnv(state.rules.env) + ", mapArea=" + state.rules.limitMapArea + ", active=" + CampaignStars.active() + ", reward=" + CampaignStars.completedReward(0) + ", native=" + state.rules.tags.getBool("stars.native") + ", min=" + wall.healthf());
        float before = wall.health;
        var generator = power(tower);
        run(generator, tower);
        assertTrue(tower.efficiency > 0f || tower.items.get(Items.graphite) < 10,
            "power=" + tower.power.status + ", produced=" + generator.getPowerProduction() + ", consumer=" + tower.power.graph.consumers.contains(tower) + ", generator=" + generator.enabled);
        assertEquals(before + wall.maxHealth * 0.15f, wall.health, 0.0001f);
        assertEquals(8, tower.items.get(Items.graphite));
        assertTrue(generator.items.get(Items.coal) < 20, "actual generator consumes fuel");
        assertEquals(60f, state.rules.tags.getFloat(WatchRewardBlocks.fieldBandage.cooldownKey(wall)));
    }

    @Test
    void twoTowersShareOneWallCooldown(){
        var first = tower(30, 30);
        var second = tower(30, 34);
        var wall = wall(35, 32, Team.sharded);
        float before = wall.health;
        var generator = power(first, second);
        run(generator, first, second);
        assertEquals(before + wall.maxHealth * 0.15f, wall.health, 0.0001f);
        assertEquals(18, first.items.get(Items.graphite) + second.items.get(Items.graphite));
        wall.health = before;
        run(generator, first, second);
        assertEquals(before, wall.health, "more towers cannot bypass the per-wall cooldown");
        state.rules.tags.put("stars.elapsed", "60");
        run(generator, first, second);
        assertEquals(before + wall.maxHealth * 0.15f, wall.health, 0.0001f);
        assertEquals(16, first.items.get(Items.graphite) + second.items.get(Items.graphite));
    }

    @Test
    void demolitionOfWallAndTowerDoesNotResetPositionCooldown(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        var generator = power(tower);
        run(generator, tower);
        float expected = wall.maxHealth * 0.25f;
        world.tile(35, 30).setBlock(Blocks.air);
        world.tile(30, 30).setBlock(Blocks.air);
        var rebuilt = tower(30, 30);
        var replacement = wall(35, 30, Team.sharded);
        generator.power.graph.addGraph(rebuilt.power.graph);
        run(generator, rebuilt);
        assertEquals(expected, replacement.health);
        assertEquals(10, rebuilt.items.get(Items.graphite));
    }

    @Test
    void realSaveKeepsWallCooldownAndSpentGraphite(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        var generator = power(tower);
        run(generator, tower);
        wall.health = wall.maxHealth * 0.25f;
        var file = saveDirectory.child("watch-bandage-cooldown-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restoredTower = world.build(30, 30);
        var restoredWall = world.build(35, 30);
        var restoredGenerator = world.build(25, 25);
        float before = restoredWall.health;
        restoredGenerator.power.graph.addGraph(restoredTower.power.graph);
        run(restoredGenerator, restoredTower);
        assertEquals(before, restoredWall.health);
        assertEquals(8, restoredTower.items.get(Items.graphite));
        assertEquals(60f, state.rules.tags.getFloat(WatchRewardBlocks.fieldBandage.cooldownKey(restoredWall)));
    }

    @Test
    void airliftingInjuredWallAfterSaveCannotResetItsCooldown(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        var generator = power(tower);
        run(generator, tower);
        assertEquals(8, tower.items.get(Items.graphite));
        wall.health = wall.maxHealth * 0.25f;
        int identity = CampaignStars.identity(wall);
        var carrier = UnitTypes.mega.create(Team.sharded);
        carrier.set(wall);
        carrier.add();
        var payloads = (Payloadc)carrier;
        assertTrue(payloads.canPickup(wall));
        payloads.pickup(wall);
        int carrierId = carrier.id;
        var file = saveDirectory.child("watch-bandage-moving-wall-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restoredCarrier = Groups.unit.getByID(carrierId);
        assertNotNull(restoredCarrier);
        restoredCarrier.set(world.tile(36, 33));
        assertTrue(((Payloadc)restoredCarrier).dropLastPayload());
        var restoredWall = world.build(36, 33);
        assertEquals(identity, CampaignStars.identity(restoredWall));
        var restoredTower = world.build(30, 30);
        var restoredGenerator = world.build(25, 25);
        restoredGenerator.power.graph.addGraph(restoredTower.power.graph);
        float before = restoredWall.health;
        run(restoredGenerator, restoredTower);
        assertEquals(before, restoredWall.health, "new position does not reset the same wall's shared cooldown");
        assertEquals(8, restoredTower.items.get(Items.graphite));
        state.rules.tags.put("stars.elapsed", "60");
        run(restoredGenerator, restoredTower);
        assertEquals(before + restoredWall.maxHealth * 0.15f, restoredWall.health, 0.0001f);
        assertEquals(6, restoredTower.items.get(Items.graphite));
    }

    @Test
    void nativeCampaignMetricsDoNotEnableRewardHealing(){
        var tower = tower(30, 30);
        var wall = wall(35, 30, Team.sharded);
        var generator = power(tower);
        state.rules.tags.put("stars.native", "true");
        float before = wall.health;
        run(generator, tower);
        assertEquals(before, wall.health);
        assertEquals(10, tower.items.get(Items.graphite));
    }

    @Test
    void healthyWallsEnemiesCoresAndSelfDoNotReceiveBandages(){
        var tower = tower(30, 30);
        var healthy = wall(34, 30, Team.sharded);
        healthy.health = healthy.maxHealth * 0.31f;
        var enemy = wall(35, 30, Team.crux);
        var core = build(Blocks.coreShard, 40, 30, Team.sharded);
        core.health = core.maxHealth * 0.1f;
        tower.health = tower.maxHealth * 0.1f;
        float healthyBefore = healthy.health, enemyBefore = enemy.health, coreBefore = core.health, ownBefore = tower.health;
        var generator = power(tower);
        run(generator, tower);
        assertEquals(healthyBefore, healthy.health);
        assertEquals(enemyBefore, enemy.health);
        assertEquals(coreBefore, core.health);
        assertEquals(ownBefore, tower.health);
        assertEquals(10, tower.items.get(Items.graphite));
    }

    @Test
    void distantWallAndUnsupportedCustomGameCannotUseBandage(){
        var tower = tower(30, 30);
        var far = wall(50, 30, Team.sharded);
        var generator = power(tower);
        float before = far.health;
        run(generator, tower);
        assertEquals(before, far.health);
        var near = wall(35, 30, Team.sharded);
        float nearBefore = near.health;
        state.rules.tags.remove("stars.preset");
        run(generator, tower);
        assertEquals(nearBefore, near.health);
        assertEquals(10, tower.items.get(Items.graphite));
    }
}
