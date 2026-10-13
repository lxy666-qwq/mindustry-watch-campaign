import arc.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.game.*;
import mindustry.type.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import mindustry.io.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class CampaignProgressionTests{
    record Setting(String key, boolean present, int value){}
    Seq<Setting> settings;
    ObjectMap<Sector, Boolean> captures;

    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void prepare(){
        logic.reset();
        settings = new Seq<>();
        captures = new ObjectMap<>();
        for(var preset : content.sectors()){
            if(!CampaignStars.eligible(preset)) continue;
            captures.put(preset.sector, preset.sector.info.wasCaptured);
            preset.sector.info.wasCaptured = false;
            for(var mode : Mode.values()){
                String key = CampaignStars.progressKey(preset, mode);
                settings.add(new Setting(key, Core.settings.has(key), Core.settings.getInt(key, 0)));
                Core.settings.put(key, 0);
            }
        }
    }

    @AfterEach
    void restore(){
        for(var setting : settings){
            if(setting.present) Core.settings.put(setting.key, setting.value);
            else Core.settings.remove(setting.key);
        }
        for(var entry : captures) entry.key.info.wasCaptured = entry.value;
        logic.reset();
    }

    void complete(SectorPreset preset){
        for(var mode : Mode.values()) Core.settings.put(CampaignStars.progressKey(preset, mode), mode.maximum);
    }

    @Test
    void all56LevelsAnd168ModesAreReachableWithoutCircularStarGates(){
        //This is a prerequisite/capacity proof using simulated scores, not a playthrough.
        boolean changed;
        int passes = 0;
        do{
            changed = false;
            for(var preset : content.sectors()){
                if(!CampaignStars.eligible(preset) || !CampaignStars.unlocked(preset)) continue;
                for(var mode : Mode.values()){
                    if(!CampaignProgression.allowed(preset, mode) || (mode != Mode.NORMAL && !CampaignStars.normalComplete(preset))) continue;
                    if(CampaignStars.stars(preset, mode) < mode.maximum){
                        Core.settings.put(CampaignStars.progressKey(preset, mode), mode.maximum);
                        changed = true;
                    }
                }
            }
            assertTrue(++passes <= 168, "circular or nonconverging progression");
        }while(changed);
        assertEquals(230, CampaignStars.mainStars(), "some main modes are blocked by unreachable requirements");
        assertEquals(280, CampaignStars.totalStars(), "a branch threshold exceeds the reachable preceding star capacity");
    }

    @Test
    void normalMilestonesRequireTheirFiveStarCounterBranch(){
        var milestones = Seq.with(SectorPresets.frozenForest, SectorPresets.ruinousShores, SectorPresets.biomassFacility,
            SectorPresets.stainedMountains, SectorPresets.windsweptIslands, SectorPresets.impact0078, SectorPresets.navalFortress,
            SectorPresets.ravine, SectorPresets.peaks, SectorPresets.stronghold, SectorPresets.crevice);
        for(var preset : milestones){
            int index = CampaignProgression.requiredReward(preset, Mode.NORMAL);
            assertTrue(index >= 0);
            var branch = WatchCampaign.sectors[index];
            Core.settings.put(CampaignStars.progressKey(branch, Mode.NORMAL), 3);
            Core.settings.put(CampaignStars.progressKey(branch, Mode.HEROIC), 1);
            Core.settings.put(CampaignStars.progressKey(branch, Mode.TACTICAL), 0);
            assertFalse(CampaignProgression.allowed(preset, Mode.NORMAL), "four stars cannot grant equipment");
            Core.settings.put(CampaignStars.progressKey(branch, Mode.TACTICAL), 1);
            assertTrue(CampaignProgression.allowed(preset, Mode.NORMAL));
            assertTrue(CampaignProgression.requirements(preset, Mode.NORMAL).contains(branch.localizedName));
        }
    }

    @Test
    void finalBranchDoesNotBlockItsOwnOriginAnchor(){
        assertTrue(CampaignProgression.allowed(SectorPresets.origin, Mode.NORMAL));
        assertFalse(CampaignProgression.allowed(SectorPresets.origin, Mode.HEROIC));
        assertFalse(CampaignProgression.allowed(SectorPresets.origin, Mode.TACTICAL));
        complete(WatchCampaign.sectors[9]);
        assertTrue(CampaignProgression.allowed(SectorPresets.origin, Mode.HEROIC));
        assertTrue(CampaignProgression.allowed(SectorPresets.origin, Mode.TACTICAL));
    }

    @Test
    void mainStarsNeverGrantEquipmentAndBranchFourStarsDoNotGrantIt(){
        var rules = new Rules();
        rules.planet = Planets.serpulo;
        rules.tags.put("stars.preset", "groundZero");
        complete(SectorPresets.groundZero);
        CampaignProgression.applyRewards(rules);
        assertTrue(rules.researched.isEmpty());
        Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[0], Mode.NORMAL), 3);
        Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[0], Mode.HEROIC), 1);
        CampaignProgression.applyRewards(rules);
        assertTrue(rules.researched.isEmpty());
        Core.settings.put(CampaignStars.progressKey(WatchCampaign.sectors[0], Mode.TACTICAL), 1);
        CampaignProgression.applyRewards(rules);
        assertTrue(rules.researched.contains(Blocks.scatter));
        assertTrue(rules.researched.contains(WatchRewardBlocks.fieldBandage));
    }

    @Test
    void nativeCampaignRulesNeverReceiveFreePersistentEquipment(){
        complete(WatchCampaign.sectors[0]);
        var rules = new Rules();
        rules.planet = Planets.serpulo;
        rules.sector = SectorPresets.groundZero.sector;
        CampaignProgression.applyRewards(rules);
        assertTrue(rules.researched.isEmpty());
    }

    @Test
    void everyRewardHasExplicitEquipmentInBothPlanetFamilies(){
        for(int i = 0; i < 10; i++){
            assertFalse(CampaignProgression.rewardSet(i, false).isEmpty());
            assertFalse(CampaignProgression.rewardSet(i, true).isEmpty());
        }
        assertSame(SectorPresets.groundZero, CampaignProgression.primaryParent(WatchCampaign.sectors[0]));
        assertTrue(CampaignProgression.parents(SectorPresets.caldera).contains(SectorPresets.peaks));
        assertTrue(CampaignProgression.parents(SectorPresets.caldera).contains(SectorPresets.ravine));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void strongholdCounterKitBuildsOneAfflictAndSurvivesSaveWithoutRefill(Mode mode){
        complete(WatchCampaign.sectors[7]);
        CampaignStars.loadMission(SectorPresets.stronghold, mode);
        logic.play();
        var core = state.rules.defaultTeam.core();
        assertEquals(100, core.items.get(Items.surgeAlloy));
        assertTrue(CampaignStars.available(Blocks.afflict));
        CampaignProgression.applyRewards(state.rules);
        assertEquals(1, state.rules.loadout.count(stack -> stack.item == Items.surgeAlloy));
        assertEquals(100, state.rules.loadout.find(stack -> stack.item == Items.surgeAlloy).amount);
        //Spending part of the finite kit remains spent after a genuine save/reload.
        core.items.remove(Items.surgeAlloy, 40);
        var file = saveDirectory.child("stronghold-counter-" + mode + ".msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        CampaignProgression.applyRewards(state.rules);
        assertEquals(60, state.rules.defaultTeam.core().items.get(Items.surgeAlloy));
        assertEquals(100, state.rules.loadout.find(stack -> stack.item == Items.surgeAlloy).amount);
    }

    @Test
    void fourStarBranchAndOtherMapsCannotReceiveStrongholdKit(){
        var branch = WatchCampaign.sectors[7];
        Core.settings.put(CampaignStars.progressKey(branch, Mode.NORMAL), 3);
        Core.settings.put(CampaignStars.progressKey(branch, Mode.HEROIC), 1);
        var rules = new Rules();
        rules.planet = Planets.erekir;
        rules.tags.put("stars.preset", "stronghold");
        CampaignProgression.applyRewards(rules);
        assertFalse(rules.loadout.contains(stack -> stack.item == Items.surgeAlloy));
        complete(branch);
        rules.tags.put("stars.preset", "onset");
        CampaignProgression.applyRewards(rules);
        assertFalse(rules.loadout.contains(stack -> stack.item == Items.surgeAlloy));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void tideRequiresFullSandRewardAndFiniteSegmentMaterialsSurviveSave(Mode mode){
        var sand = WatchCampaign.sectors[4];
        var tide = WatchCampaign.sectors[5];
        Core.settings.put(CampaignStars.progressKey(sand, Mode.NORMAL), 3);
        Core.settings.put(CampaignStars.progressKey(sand, Mode.HEROIC), 1);
        assertEquals(4, CampaignProgression.requiredReward(tide, mode));
        assertFalse(CampaignProgression.allowed(tide, mode));
        complete(sand);
        assertTrue(CampaignProgression.allowed(tide, mode));
        assertTrue(CampaignProgression.requirements(tide, mode).contains(Blocks.segment.localizedName));
        CampaignStars.loadMission(tide, mode);
        logic.play();
        var core = state.rules.defaultTeam.core();
        assertTrue(CampaignStars.available(Blocks.segment));
        for(var requirement : Blocks.segment.requirements){
            assertTrue(core.items.get(requirement.item) >= requirement.amount, "Segment material missing: " + requirement.item.name);
        }
        assertEquals(80, core.items.get(Items.thorium));
        assertEquals(40, core.items.get(Items.phaseFabric));
        CampaignProgression.applyRewards(state.rules);
        assertEquals(1, state.rules.loadout.count(stack -> stack.item == Items.thorium));
        assertEquals(1, state.rules.loadout.count(stack -> stack.item == Items.phaseFabric));
        core.items.remove(Items.thorium, 40);
        core.items.remove(Items.phaseFabric, 20);
        var file = saveDirectory.child("tide-counter-" + mode + ".msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        CampaignProgression.applyRewards(state.rules);
        assertEquals(40, state.rules.defaultTeam.core().items.get(Items.thorium));
        assertEquals(20, state.rules.defaultTeam.core().items.get(Items.phaseFabric));
        assertEquals(80, state.rules.loadout.find(stack -> stack.item == Items.thorium).amount);
        assertEquals(40, state.rules.loadout.find(stack -> stack.item == Items.phaseFabric).amount);
    }

    @Test
    void tideKitIsUnavailableAtFourStarsOrOnOtherMissions(){
        var sand = WatchCampaign.sectors[4];
        Core.settings.put(CampaignStars.progressKey(sand, Mode.NORMAL), 3);
        Core.settings.put(CampaignStars.progressKey(sand, Mode.HEROIC), 1);
        var rules = new Rules();
        rules.planet = Planets.serpulo;
        rules.tags.put("stars.preset", "watch-6");
        CampaignProgression.applyRewards(rules);
        assertFalse(rules.loadout.contains(stack -> stack.item == Items.thorium || stack.item == Items.phaseFabric));
        complete(sand);
        rules.tags.put("stars.preset", "groundZero");
        CampaignProgression.applyRewards(rules);
        assertFalse(rules.loadout.contains(stack -> stack.item == Items.thorium || stack.item == Items.phaseFabric));
        rules.tags.remove("stars.preset");
        String nativeLoadout = rules.loadout.toString();
        CampaignProgression.applyRewards(rules);
        assertEquals(nativeLoadout, rules.loadout.toString());
    }
}
