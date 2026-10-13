import arc.*;
import arc.files.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Branch placement and rules regressions, independent of the stars scoring tests. */
public class WatchBranchTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
        Core.settings.setJson(JsonIO.json);
    }

    @BeforeEach
    void reset(){ logic.reset(); }

    @AfterEach
    void finish(){ logic.reset(); }

    void load(SectorPreset preset){
        var params = new WorldParams();
        params.saveInfo = false;
        world.loadSector(preset.sector, params);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void branchLivesNextToItsOriginalAnchor(int index){
        var branch = WatchCampaign.sectors[index];
        var anchor = WatchCampaign.anchors[index];
        assertSame(anchor.planet, branch.planet);
        assertSame(branch, branch.sector.preset);
        assertTrue(anchor.sector.isNear(branch.sector), "branch must be physically adjacent: " + branch.name);
        assertEquals(index, WatchCampaign.level(branch));
        assertSame(anchor.techNode, branch.techNode.parent);
        assertTrue(branch.techNode.objectives.contains(o -> o instanceof WatchCampaign.BranchStars));
    }

    @Test
    void tenBranchesUseDistinctSectorsAcrossBothOfficialPlanets(){
        ObjectSet<Sector> assigned = new ObjectSet<>();
        int serpulo = 0, erekir = 0;
        for(var branch : WatchCampaign.sectors){
            assertTrue(assigned.add(branch.sector), "two branches replace the same sector");
            if(branch.planet == Planets.serpulo) serpulo++;
            if(branch.planet == Planets.erekir) erekir++;
        }
        assertEquals(10, assigned.size);
        assertEquals(6, serpulo);
        assertEquals(4, erekir);
        assertFalse(WatchCampaign.planet.visible);
        assertFalse(WatchCampaign.planet.accessible);
    }

    @Test
    void allOriginalNamedPresetLocationsRemainUnchanged(){
        // Current and legacy positions read from the archived v160.7 campaign export.
        String[] original = {
            "groundZero:170:15", "saltFlats:98:101", "testingGrounds:169:3", "frozenForest:64:86",
            "biomassFacility:23:81", "taintedWoods:145:221", "crateredBattleground:219:18",
            "ruinousShores:41:213", "perilousHarbor:214:47", "facility32m:65:64",
            "windsweptIslands:97:246", "stainedMountains:223:20", "extractionOutpost:213:165",
            "coastline:164:108", "weatheredChannels:166:39", "navalFortress:165:216",
            "frontier:86:50", "fungalPass:221:21", "infestedCanyons:85:210", "atolls:75:1",
            "sunkenPier:173:-1", "mycelialBastion:143:260", "overgrowth:142:134", "tarFields:99:23",
            "impact0078:266:227", "desolateRift:271:123", "nuclearComplex:228:130",
            "littoralShipyard:204:204", "planetaryTerminal:217:93", "fallenVessel:95:-1",
            "geothermalStronghold:264:264", "cruxscape:54:54", "onset:10:10", "aegis:88:88",
            "lake:41:41", "intersect:36:36", "atlas:14:14", "split:19:19", "basin:29:29",
            "marsh:25:25", "peaks:30:30", "ravine:39:39", "caldera-erekir:43:43",
            "stronghold:18:18", "crevice:3:3", "siege:58:58", "crossroads:37:37",
            "karst:5:5", "origin:12:12"
        };
        assertEquals(49, original.length);
        for(String entry : original){
            String[] fields = entry.split(":");
            var preset = content.sector(fields[0]);
            assertNotNull(preset, entry);
            assertEquals(Integer.parseInt(fields[1]), preset.sector.id, preset.name);
            assertEquals(Integer.parseInt(fields[2]), preset.originalPosition, preset.name);
            assertSame(preset, preset.sector.preset, preset.name);
            assertEquals(-1, WatchCampaign.level(preset), preset.name);
        }
    }

    @Test
    void onlyTechnologyMainlineMapsCountTowardBranchUnlocks(){
        assertEquals(29, CampaignStars.mainLevels(Planets.serpulo).size);
        assertEquals(17, CampaignStars.mainLevels(Planets.erekir).size);
        for(String key : new String[]{"fallenVessel", "geothermalStronghold", "cruxscape"}){
            assertFalse(CampaignStars.eligible(content.sector(key)));
        }
        for(var branch : WatchCampaign.sectors){
            assertTrue(CampaignStars.eligible(branch));
            assertFalse(CampaignStars.mainLevels(branch.planet).contains(branch));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8, 9})
    void erekirBranchRetainsPlayableSerpuloEquipmentAfterFinalPlanetRules(int index){
        Object originalUnlockSetting = Core.settings.get(Blocks.duo.name + "-unlocked", null);
        load(WatchCampaign.sectors[index]);
        assertSame(Planets.erekir, state.rules.planet);
        assertSame(Planets.erekir, state.rules.sector.planet);
        assertEquals(Planets.serpulo.defaultEnv, state.rules.env);
        assertSame(Team.crux, state.rules.waveTeam);
        assertSame(Team.sharded, state.rules.defaultTeam);
        assertFalse(state.rules.fog);
        assertFalse(state.rules.staticFog);
        assertFalse(state.rules.onlyDepositCore);
        assertTrue(UnitTypes.alpha.supportsEnv(state.rules.env));
        for(var block : WatchCampaign.equipment(index)){
            assertTrue(block.environmentBuildable(), "hidden build menu equipment: " + block.name);
            assertTrue(block.unlockedNowHost(), "missing temporary research: " + block.name);
            assertTrue(block.supportsEnv(state.rules.env), "unsupported equipment environment: " + block.name);
            assertFalse(state.rules.isBanned(block), "banned branch equipment: " + block.name);
        }
        for(var item : Items.serpuloItems){
            assertTrue(item.isOnPlanet(Planets.erekir), "unavailable branch item: " + item.name);
        }
        assertEquals(originalUnlockSetting, Core.settings.get(Blocks.duo.name + "-unlocked", null), "temporary branch research must not leak into campaign settings");
        assertSame(Blocks.coreShard, state.rules.defaultTeam.core().block);
        assertEquals(index, WatchCampaign.level());
    }

    @Test
    void originalErekirRulesAndBuildMenuRemainPlanetSpecific(){
        load(WatchCampaign.sectors[6]);
        assertTrue(Blocks.duo.environmentBuildable());
        logic.reset();
        load(SectorPresets.onset);
        assertSame(Planets.erekir, state.rules.planet);
        assertEquals(Planets.erekir.defaultEnv, state.rules.env);
        assertSame(Team.malis, state.rules.waveTeam);
        assertTrue(state.rules.fog);
        assertTrue(state.rules.staticFog);
        assertTrue(state.rules.onlyDepositCore);
        assertFalse(UnitTypes.alpha.supportsEnv(state.rules.env));
        assertFalse(WatchCampaign.available(Blocks.duo));
        assertFalse(Blocks.duo.environmentBuildable());
        assertFalse(Items.copper.isOnPlanet(Planets.erekir));
        assertEquals(-1, WatchCampaign.level());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void starThresholdAndAnchorCompletionAreIndependentRequirements(int index){
        try(var progress = new ProgressSnapshot()){
            var anchor = WatchCampaign.anchors[index];
            int threshold = WatchCampaign.starRequirements[index];
            seedOtherMainStars(threshold, anchor);
            assertEquals(threshold, CampaignStars.mainStars());
            assertFalse(CampaignStars.normalComplete(anchor));
            assertFalse(CampaignStars.branchUnlocked(index), "stars alone bypassed anchor completion");

            seedOtherMainStars(threshold - 1, anchor);
            Core.settings.put(CampaignStars.progressKey(anchor, Mode.NORMAL), 1);
            assertEquals(threshold, CampaignStars.mainStars());
            assertTrue(CampaignStars.normalComplete(anchor));
            assertTrue(CampaignStars.branchUnlocked(index));
            int counter = CampaignProgression.requiredReward(WatchCampaign.sectors[index], Mode.NORMAL);
            if(counter >= 0){
                assertFalse(CampaignStars.unlocked(WatchCampaign.sectors[index]), "stars and anchor cannot bypass required counter equipment");
                for(var mode : Mode.values()){
                    Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[counter], mode), mode.maximum);
                }
            }
            assertTrue(CampaignStars.unlocked(WatchCampaign.sectors[index]));

            seedOtherMainStars(threshold - 2, anchor);
            assertEquals(threshold - 1, CampaignStars.mainStars());
            assertTrue(CampaignStars.normalComplete(anchor));
            assertFalse(CampaignStars.branchUnlocked(index), "anchor completion bypassed star threshold");
        }
    }

    @Test
    void earnedBranchStarsCannotUnlockMoreBranches(){
        try(var progress = new ProgressSnapshot()){
            for(var branch : WatchCampaign.sectors){
                for(var mode : Mode.values()) Core.settings.put(CampaignStars.progressKey(branch, mode), mode.maximum);
            }
            assertEquals(50, CampaignStars.totalStars());
            assertEquals(0, CampaignStars.mainStars());
            for(int i = 0; i < WatchCampaign.sectors.length; i++) assertFalse(CampaignStars.branchUnlocked(i));
        }
    }

    @Test
    void existingPresetCannotBeSelectedAsAFreeBranchTile(){
        assertFalse(WatchCampaign.freeBranchTile(SectorPresets.groundZero.sector));
        assertFalse(WatchCampaign.freeBranchTile(WatchCampaign.sectors[0].sector));
    }

    @Test
    void savedPlayerBaseInfoExcludesAnOtherwiseFreeTile(){
        var sector = freeTile();
        String key = sector.planet.name + "-s-" + sector.id + "-info";
        boolean present = Core.settings.has(key);
        Object previous = Core.settings.get(key, null);
        try{
            var info = new SectorInfo();
            info.hasCore = true;
            Core.settings.putJson(key, info);
            assertFalse(WatchCampaign.freeBranchTile(sector));
            info.hasCore = false;
            Core.settings.putJson(key, info);
            assertTrue(WatchCampaign.freeBranchTile(sector));
        }finally{
            restore(key, present, previous);
        }
    }

    @Test
    void existingSectorFileExcludesATileBeforeSaveSlotsAreLoaded(){
        var sector = freeTile();
        Fi file = saveDirectory.child("sector-" + sector.planet.name + "-" + sector.id + ".msav");
        assertNull(sector.save, "test requires a sector without a loaded save slot");
        assertFalse(file.exists(), "test must never replace an existing save");
        try{
            file.writeString("WatchBranchTests occupancy marker");
            assertFalse(WatchCampaign.freeBranchTile(sector));
        }finally{
            assertTrue(file.delete(), "could not remove the test-created occupancy marker");
        }
        assertTrue(WatchCampaign.freeBranchTile(sector));
    }

    Sector freeTile(){
        var result = Planets.serpulo.sectors.find(s -> s.save == null && WatchCampaign.freeBranchTile(s));
        assertNotNull(result, "no unused fixture sector available");
        return result;
    }

    void seedOtherMainStars(int count, SectorPreset excluded){
        for(var planet : new Planet[]{Planets.serpulo, Planets.erekir}){
            for(var preset : CampaignStars.mainLevels(planet)){
                if(preset == excluded) continue;
                for(var mode : Mode.values()){
                    int score = Math.min(count, mode.maximum);
                    Core.settings.put(CampaignStars.progressKey(preset, mode), score);
                    count -= score;
                }
            }
        }
        assertEquals(0, count, "requested score exceeds available mainline stars");
    }

    static void restore(String key, boolean present, Object previous){
        if(present) Core.settings.put(key, previous);
        else Core.settings.remove(key);
    }

    static class ProgressSnapshot implements AutoCloseable{
        final ObjectMap<String, Object> previous = new ObjectMap<>();
        final ObjectSet<String> present = new ObjectSet<>();
        final ObjectMap<SectorPreset, Boolean> captures = new ObjectMap<>();

        ProgressSnapshot(){
            for(var preset : content.sectors()){
                if(!CampaignStars.eligible(preset)) continue;
                captures.put(preset, preset.sector.info.wasCaptured);
                preset.sector.info.wasCaptured = false;
                for(var mode : Mode.values()){
                    String key = CampaignStars.progressKey(preset, mode);
                    if(Core.settings.has(key)) present.add(key);
                    previous.put(key, Core.settings.get(key, null));
                    Core.settings.put(key, 0);
                }
            }
        }

        @Override
        public void close(){
            for(var entry : previous) restore(entry.key, present.contains(entry.key), entry.value);
            for(var entry : captures) entry.key.sector.info.wasCaptured = entry.value;
        }
    }
}