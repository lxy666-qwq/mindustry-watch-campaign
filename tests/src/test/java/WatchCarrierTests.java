import arc.*;
import arc.files.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.game.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class WatchCarrierTests{
    @BeforeAll static void start(){ ApplicationTests.launchApplication(); }
    @BeforeEach void world(){
        logic.reset();
        CampaignStars.loadMission(WatchCampaign.sectors[1], Mode.NORMAL);
        logic.play();
    }
    @AfterEach void clear(){ logic.reset(); }

    @ParameterizedTest
    @CsvSource({"1,13", "7,25", "9,30"})
    void transportWaveHasFiniteCountAndAvailableCounters(int level, int expectedWave){
        logic.reset();
        CampaignStars.loadMission(WatchCampaign.sectors[level], Mode.NORMAL);
        logic.play();
        var groups = state.rules.spawns.select(g -> g.type == WatchEnemies.carrier);
        assertEquals(1, groups.size);
        var group = groups.first();
        int spawned = 0;
        for(int wave = 0; wave < WatchCampaign.stages[level].waves(); wave++) spawned += group.getSpawned(wave);
        assertEquals(1, spawned);
        assertEquals(1, group.getSpawned(expectedWave - 1));
        assertTrue(state.rules.researched.contains(Blocks.scorch));
        assertTrue(state.rules.researched.contains(Blocks.arc));
    }

    @Test void deathReleasesExactlyTwoOrdinaryCrawlers(){
        var carrier = WatchEnemies.carrier.spawn(Team.crux, 600, 600);
        carrier.kill();
        assertEquals(2, Groups.unit.count(u -> u.type == UnitTypes.crawler && u.team == Team.crux));
        assertFalse(UnitTypes.crawler.abilities.contains(a -> a instanceof mindustry.entities.abilities.SpawnDeathAbility));
    }

    @Test void nativeSaveKeepsNewEnemyTypeAndDeathPayload(){
        WatchEnemies.carrier.spawn(Team.crux, 600, 600);
        Fi save = Fi.tempFile("watch-carrier-save");
        try{
            SaveIO.write(save);logic.reset();SaveIO.load(save);
            var carrier = Groups.unit.find(u -> u.type == WatchEnemies.carrier);
            assertNotNull(carrier);
            carrier.kill();
            assertEquals(2, Groups.unit.count(u -> u.type == UnitTypes.crawler && u.team == Team.crux));
        }finally{ save.delete(); }
    }
}
