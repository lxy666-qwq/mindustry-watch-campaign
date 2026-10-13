import arc.struct.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.game.Objectives.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.planet.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static mindustry.Vars.*;
import static mindustry.content.WatchCampaign.*;
import static org.junit.jupiter.api.Assertions.*;

public class WatchCampaignTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
        //The shared headless fixture calls Vars.init directly; normal launch also installs this serializer.
        arc.Core.settings.setJson(JsonIO.json);
    }

    @BeforeEach
    void reset(){ logic.reset(); }

    @AfterEach
    void finish(){ logic.reset(); }

    void load(int level){
        var params = new WorldParams();
        params.saveInfo = false;
        world.loadSector(sectors[level].sector, params);
        logic.play();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void mapsHaveReachableGroundRouteAndFiniteWaves(int level){
        load(level);
        int size = stages[level].size();
        var core = WatchMapGenerator.core(size);
        var entry = WatchMapGenerator.primary(size);
        assertEquals(size, world.width());
        assertEquals(size, sectors[level].generator.map.width);
        assertEquals(1, Team.sharded.cores().size);
        assertEquals(Blocks.spawn, world.tile(entry.x, entry.y).overlay());
        assertTrue(reachable(entry.x, entry.y, false).contains(world.tile(core.x, core.y).pos()), "primary route " + level);
        assertEquals(stages[level].waves() + 1, state.rules.winWave);
        assertTrue(state.rules.spawns.contains(g -> g.getSpawned(stages[level].waves() - 1) > 0), "last wave must actually spawn");
        assertFalse(state.rules.spawns.contains(g -> g.end >= stages[level].waves()), "no enemies scheduled after victory");
        assertTrue(WatchCampaign.available(Blocks.duo));
        assertTrue(state.rules.isBanned(Blocks.itemSource));
        assertFalse(state.rules.isBanned(Blocks.mechanicalDrill));
        for(var group : state.rules.spawns){
            assertEquals(Blocks.spawn, world.tile(group.spawn).overlay());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void everyTransitionRunsOnceAndLeavesMainRoute(int level){
        load(level);
        var stage = stages[level];
        for(var event : stage.encounters()){
            state.wave = event.wave() - 1;
            WatchCampaignEvents.beforeWave();
            assertFalse(WatchCampaignEvents.done(event), "transition ran early");
            state.wave = event.wave();
            WatchCampaignEvents.beforeWave();
            assertTrue(WatchCampaignEvents.done(event));
            var inventory = state.rules.defaultTeam.core().items;
            int copper = inventory.get(Items.copper), coal = inventory.get(Items.coal), silicon = inventory.get(Items.silicon);
            WatchCampaignEvents.beforeWave();
            assertEquals(copper, inventory.get(Items.copper));
            assertEquals(coal, inventory.get(Items.coal));
            assertEquals(silicon, inventory.get(Items.silicon));
            var core = WatchMapGenerator.core(stage.size());
            var spawn = WatchMapGenerator.primary(stage.size());
            assertTrue(reachable(spawn.x, spawn.y, false).contains(world.tile(core.x, core.y).pos()), "route lost after " + event);
        }
        assertFalse(WatchCampaignEvents.blackoutActive());
    }

    @Test
    void navalApproachCanReachFiringRangeBeforeAndAfterLowTide(){
        load(5);
        assertNavalRoute();
        state.wave = stages[5].waveOf(Change.lowTide);
        WatchCampaignEvents.beforeWave();
        assertNavalRoute();
    }

    void assertNavalRoute(){
        int size = world.width();
        var sea = WatchMapGenerator.naval(size);
        var core = state.rules.defaultTeam.core();
        var visited = reachable(sea.x, sea.y, true);
        assertTrue(visited.size > 50);
        boolean canFire = false;
        for(var iterator = visited.iterator(); iterator.hasNext;){
            var tile = world.tile(iterator.next());
            if(tile.dst(core) <= UnitTypes.risso.range + core.hitSize() / 2f) canFire = true;
        }
        assertTrue(canFire, "naval units must be able to approach and attack the core; range=" + UnitTypes.risso.range);
    }

    @Test
    void floodPreservesBuiltBridgeTilesAndOpensEastDetour(){
        load(3);
        int x = world.width() / 2 - 16, y = world.height() / 2;
        world.tile(x, y).setBlock(Blocks.copperWall, Team.sharded);
        var originalFloor = world.tile(x, y).floor();
        state.wave = 7;
        WatchCampaignEvents.beforeWave();
        assertEquals(Blocks.copperWall, world.tile(x, y).block());
        assertEquals(originalFloor, world.tile(x, y).floor());
        int flooded = 0;
        for(var tile : world.tiles) if(tile.floor() == Blocks.deepwater) flooded++;
        assertTrue(flooded > 100);
        var entry = WatchMapGenerator.primary(world.width());
        var core = WatchMapGenerator.core(world.width());
        assertTrue(reachable(entry.x, entry.y, false).contains(world.tile(core.x, core.y).pos()));
    }

    @Test
    void blackoutSurvivesRealSaveAndRestoresOnlyAffectedGenerators(){
        load(7);
        var core = WatchMapGenerator.core(world.width());
        var running = world.tile(core.x - 8, core.y - 7).build;
        world.tile(core.x + 8, core.y - 7).setBlock(Blocks.combustionGenerator, Team.sharded);
        var alreadyOff = world.tile(core.x + 8, core.y - 7).build;
        alreadyOff.enabled = false;
        state.wave = 8;
        WatchCampaignEvents.beforeWave();
        assertFalse(running.enabled);
        assertFalse(alreadyOff.enabled);
        var file = saveDirectory.child("watch-blackout-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertTrue(WatchCampaignEvents.blackoutActive());
        assertFalse(world.build(running.pos()).enabled);
        assertTrue(WatchCampaignEvents.done(stages[7].encounters()[0]));
        state.set(State.playing);
        world.tile(core.x + 12, core.y - 7).setBlock(Blocks.combustionGenerator, Team.sharded);
        var newGenerator = world.tile(core.x + 12, core.y - 7).build;
        WatchCampaignEvents.enforceBlackout();
        assertFalse(newGenerator.enabled);
        state.wave = 12;
        WatchCampaignEvents.beforeWave();
        assertTrue(world.build(running.pos()).enabled);
        assertFalse(world.build(alreadyOff.pos()).enabled);
        assertTrue(newGenerator.enabled);
        assertFalse(WatchCampaignEvents.blackoutActive());
    }

    @Test
    void supplyRewardDoesNotRepeatAfterSave(){
        load(0);
        state.wave = 4;
        WatchCampaignEvents.beforeWave();
        int copper = Team.sharded.core().items.get(Items.copper);
        var file = saveDirectory.child("watch-supply-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        WatchCampaignEvents.beforeWave();
        assertEquals(copper, Team.sharded.core().items.get(Items.copper));
    }

    @Test
    void technologyIsScopedAndBranchesRequireStars(){
        boolean previousUnlock = Blocks.spectre.unlocked();
        load(9);
        assertTrue(Blocks.spectre.unlockedNowHost());
        logic.reset();
        assertEquals(previousUnlock, Blocks.spectre.unlocked());
        assertFalse(WatchCampaign.available(Blocks.spectre));
        assertEquals(10, content.sectors().count(s -> WatchCampaign.level(s) >= 0));
        for(int i = 0; i < sectors.length; i++){
            assertSame(anchors[i].techNode, sectors[i].techNode.parent);
            assertTrue(sectors[i].techNode.objectives.contains(o -> o instanceof BranchStars));
        }
        state.rules.sector = SectorPresets.groundZero.sector;
        state.wave = 30;
        WatchCampaignEvents.beforeWave();
        assertEquals(0, state.rules.tags.size);
    }

    @Test
    void starterMiningLinesActuallyDeliverCopperAndLead(){
        load(0);
        int copper = Team.sharded.core().items.get(Items.copper), lead = Team.sharded.core().items.get(Items.lead);
        arc.util.Time.setDeltaProvider(() -> 1f);
        for(int i = 0; i < 3600; i++) Groups.build.each(build -> build.update());
        assertTrue(Team.sharded.core().items.get(Items.copper) > copper, "copper starter line delivers items");
        assertTrue(Team.sharded.core().items.get(Items.lead) > lead, "lead starter line delivers items");
    }

    @Test
    void beforeWaveHookOpensRouteBeforeSpawning(){
        load(0);
        state.wave = 7;
        logic.runWave();
        assertTrue(WatchCampaignEvents.done(stages[0].encounters()[1]));
        var gate = WatchMapGenerator.gate(world.width());
        assertEquals(Blocks.air, world.tile(gate.x, gate.y).block());
        assertEquals(8, state.wave);
    }

    IntSet reachable(int x, int y, boolean water){
        IntSet visited = new IntSet();
        IntQueue queue = new IntQueue();
        queue.addLast(world.tile(x, y).pos());
        visited.add(world.tile(x, y).pos());
        while(!queue.isEmpty()){
            var tile = world.tile(queue.removeFirst());
            for(var direction : arc.math.geom.Geometry.d4){
                var next = world.tile(tile.x + direction.x, tile.y + direction.y);
                if(next == null || next.block().isStatic() || (water ? !next.floor().isLiquid : next.floor().isDeep())) continue;
                if(visited.add(next.pos())) queue.addLast(next.pos());
            }
        }
        return visited;
    }
}
