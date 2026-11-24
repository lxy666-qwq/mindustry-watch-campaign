import arc.*;
import arc.files.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.input.*;
import mindustry.game.*;
import org.junit.jupiter.api.*;
import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class WatchHeroTests{
    int[] previous = new int[3];
    boolean[] present = new boolean[3];

    @BeforeAll static void start(){
        ApplicationTests.launchApplication();
        Core.settings.setJson(JsonIO.json);
    }

    @BeforeEach void setup(){
        logic.reset();
        Time.setDeltaProvider(() -> 1f);
        int i = 0;
        for(var mode : Mode.values()){
            String key = CampaignStars.progressKey(WatchCampaign.sectors[1], mode);
            present[i] = Core.settings.has(key);
            previous[i++] = Core.settings.getInt(key);
            Core.settings.put(key, mode.maximum);
        }
        CampaignStars.loadMission(WatchCampaign.sectors[0], Mode.NORMAL);
        logic.play();
        Team.sharded.core().items.set(Items.graphite, 100);
        Team.sharded.core().items.set(Items.silicon, 100);
    }

    @AfterEach void restore(){
        logic.reset();
        int i = 0;
        for(var mode : Mode.values()){
            String key = CampaignStars.progressKey(WatchCampaign.sectors[1], mode);
            if(present[i]) Core.settings.put(key, previous[i]); else Core.settings.remove(key);
            i++;
        }
    }

    WatchHeroes.SentinelPost.SentinelPostBuild post(int x){
        world.tile(x, 30).setBlock(WatchHeroes.post, Team.sharded);
        return (WatchHeroes.SentinelPost.SentinelPostBuild)world.tile(x, 30).build;
    }

    @Test void deployConsumesMaterialsAndCanBeControlled(){
        var unit = post(65).deploy();
        assertNotNull(unit);
        assertEquals(60, Team.sharded.core().items.get(Items.graphite));
        assertEquals(70, Team.sharded.core().items.get(Items.silicon));
        var operator = Player.create();
        operator.team(Team.sharded);
        InputHandler.unitControl(operator, unit);
        assertSame(unit, operator.unit());
    }

    @Test void anotherPostOrDestroyedHeroCannotGrantAnotherDeployment(){
        var first = post(65);
        var unit = first.deploy();
        unit.remove();
        first.tile.setBlock(WatchHeroes.post, Team.sharded);
        assertNull(post(69).deploy());
        assertEquals(60, Team.sharded.core().items.get(Items.graphite));
    }

    @Test void insufficientMaterialsAndMissingRewardDoNotConsumeDeployment(){
        var post = post(65);
        Team.sharded.core().items.set(Items.graphite, 39);
        assertNull(post.deploy());
        assertFalse(state.rules.tags.getBool(WatchHeroes.deployedKey));
        Team.sharded.core().items.set(Items.graphite, 100);
        Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[1], Mode.TACTICAL), 0);
        assertNull(post.deploy());
        assertEquals(100, Team.sharded.core().items.get(Items.graphite));
    }

    @Test void saveReloadPreservesHeroAndSpentDeployment(){
        var unit = post(65).deploy();
        unit.set(560, 280);
        Fi save = Fi.tempFile("watch-hero-save");
        try{
            SaveIO.write(save);
            logic.reset();
            SaveIO.load(save);
            assertTrue(state.rules.tags.getBool(WatchHeroes.deployedKey));
            assertEquals(1, Groups.unit.count(u -> u.type == WatchHeroes.sentinel));
            assertNull(post(69).deploy());
        }finally{ save.delete(); }
    }
}
