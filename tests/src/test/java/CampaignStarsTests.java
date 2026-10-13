import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.payloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.util.stream.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class CampaignStarsTests{
    record Setting(String key, boolean present, int value){}
    Seq<Setting> settings;
    ObjectMap<Sector, Boolean> captures;

    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
        Core.settings.setJson(JsonIO.json);
    }

    @BeforeEach
    void prepare(){
        logic.reset();
        Time.setDeltaProvider(() -> 1f);
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
        logic.reset();
        for(var setting : settings){
            if(setting.present) Core.settings.put(setting.key, setting.value);
            else Core.settings.remove(setting.key);
        }
        for(var entry : captures) entry.key.info.wasCaptured = entry.value;
    }

    void start(SectorPreset preset, Mode mode){
        CampaignStars.loadMission(preset, mode);
        logic.play();
    }

    static Stream<Arguments> mainMissions(){
        init();
        return Stream.of(Planets.serpulo, Planets.erekir).flatMap(p -> {
            var levels = CampaignStars.mainLevels(p);
            return IntStream.range(0, levels.size).mapToObj(levels::get);
        })
            .flatMap(p -> Stream.of(Mode.values()).map(m -> Arguments.of(p.name, m)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("mainMissions")
    void everyOriginalMainMapLoadsWithSameTerrainAndIndependentRun(String name, Mode mode){
        var preset = content.sector(name);
        var originalInfo = preset.sector.info;
        var originalSave = preset.sector.save;
        var originalFile = preset.generator.map.file;
        int width = preset.generator.map.width, height = preset.generator.map.height;
        start(preset, mode);
        assertEquals(width, world.width());
        assertEquals(height, world.height());
        assertEquals(originalFile, preset.generator.map.file);
        assertSame(originalInfo, preset.sector.info);
        assertSame(originalSave, preset.sector.save);
        assertNull(state.rules.sector, "star replay must never own a native sector save");
        assertSame(preset.planet, state.rules.planet);
        assertTrue(CampaignStars.active());
        assertSame(preset, CampaignStars.currentPreset());
        assertEquals(mode, CampaignStars.currentMode());
        assertTrue(state.rules.defaultTeam.cores().size > 0);
        var limits = CampaignStars.limits(preset);
        assertEquals(limits.deadline(), state.rules.tags.getFloat("stars.deadline"));
        assertEquals(limits.buildBudget(), state.rules.tags.getInt("stars.buildBudget"));
        assertTrue(CampaignStars.describe(preset, mode).length() > 20);
        if(mode == Mode.NORMAL) assertEquals(3, CampaignStars.scoreVictory());
        if(mode == Mode.TACTICAL) assertEquals(0, CampaignStars.scoreVictory(), "initial buildings cannot fulfill new construction tasks");
    }

    @Test
    void eligibilityIs46MainLevelsAndTenBranches(){
        assertEquals(29, CampaignStars.mainLevels(Planets.serpulo).size);
        assertEquals(17, CampaignStars.mainLevels(Planets.erekir).size);
        assertEquals(56, content.sectors().count(CampaignStars::eligible));
        assertFalse(CampaignStars.eligible(content.sector("fallenVessel")));
        assertFalse(CampaignStars.eligible(content.sector("geothermalStronghold")));
        assertFalse(CampaignStars.eligible(content.sector("cruxscape")));
    }

    @Test
    void scoresAreMaximaAndTotalCannotExceed280(){
        var first = SectorPresets.groundZero;
        CampaignStars.saveBest(first, Mode.NORMAL, 3);
        CampaignStars.saveBest(first, Mode.NORMAL, 1);
        CampaignStars.saveBest(first, Mode.HEROIC, 1);
        CampaignStars.saveBest(first, Mode.HEROIC, 1);
        assertEquals(4, CampaignStars.stars(first));
        assertEquals(4, CampaignStars.mainStars());
        assertThrows(IllegalArgumentException.class, () -> CampaignStars.saveBest(first, Mode.HEROIC, 2));
        for(var preset : content.sectors()){
            if(CampaignStars.eligible(preset)){
                for(var mode : Mode.values()) CampaignStars.saveBest(preset, mode, mode.maximum);
            }
        }
        assertEquals(230, CampaignStars.mainStars());
        assertEquals(280, CampaignStars.totalStars());
    }

    @Test
    void priorVanillaCaptureGrantsOnlyOneHonestStar(){
        SectorPresets.groundZero.sector.info.wasCaptured = true;
        assertEquals(1, CampaignStars.stars(SectorPresets.groundZero, Mode.NORMAL));
        assertEquals(1, CampaignStars.mainStars());
        assertTrue(CampaignStars.normalComplete(SectorPresets.groundZero));
        assertEquals(0, CampaignStars.stars(SectorPresets.groundZero, Mode.HEROIC));
    }

    @Test
    void distinctDifficultyChangesOnlyEnemyPressure(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        float health = state.rules.waveTeam.rules().unitHealthMultiplier;
        float damage = state.rules.waveTeam.rules().unitDamageMultiplier;
        float spacing = state.rules.waveSpacing;
        float friendlyDamage = state.rules.defaultTeam.rules().unitDamageMultiplier;
        logic.reset();
        start(SectorPresets.groundZero, Mode.HEROIC);
        assertEquals(health * 1.25f, state.rules.waveTeam.rules().unitHealthMultiplier, 0.0001f);
        assertEquals(damage * 1.15f, state.rules.waveTeam.rules().unitDamageMultiplier, 0.0001f);
        assertEquals(spacing * 0.85f, state.rules.waveSpacing, 0.0001f);
        assertEquals(friendlyDamage, state.rules.defaultTeam.rules().unitDamageMultiplier);
        logic.reset();
        start(SectorPresets.groundZero, Mode.TACTICAL);
        assertEquals(health * 1.12f, state.rules.waveTeam.rules().unitHealthMultiplier, 0.0001f);
        assertEquals(damage * 1.08f, state.rules.waveTeam.rules().unitDamageMultiplier, 0.0001f);
        assertEquals(spacing * 0.95f, state.rules.waveSpacing, 0.0001f);
    }

    @Test
    void normalGradeKeepsDamageHistoryEvenAfterRepair(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        var core = state.rules.defaultTeam.core();
        core.health = core.maxHealth * 0.65f;
        CampaignStars.update();
        core.heal();
        CampaignStars.update();
        assertEquals(0.65f, CampaignStars.minimumCoreHealth(), 0.0001f);
        assertEquals(2, CampaignStars.scoreVictory());
        assertEquals(2, CampaignStars.recordVictory());
        assertEquals(2, CampaignStars.recordVictory(), "duplicate victory has no second reward");
        assertEquals(2, CampaignStars.stars(SectorPresets.groundZero));
    }

    @ParameterizedTest
    @CsvSource({"NORMAL,0.35,0.65,2", "HEROIC,0.151,0.849,0", "TACTICAL,0.401,0.599,0"})
    void sameFrameEnvironmentalDamageAndRepairKeepLowestHealth(Mode mode, float damage, float minimum, int score){
        start(SectorPresets.groundZero, mode);
        var core = state.rules.defaultTeam.core();
        core.damage(core.maxHealth * damage);
        assertEquals(minimum, core.healthf(), 0.0001f);
        core.heal();
        CampaignStars.update();
        assertEquals(1f, core.healthf());
        assertEquals(minimum, CampaignStars.minimumCoreHealth(), 0.0001f,
            "native non-bullet damage must be recorded before a repair in the same frame");
        assertEquals(score, CampaignStars.scoreVictory());
        var file = saveDirectory.child("stars-environmental-damage-" + mode + ".msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertEquals(minimum, CampaignStars.minimumCoreHealth(), 0.0001f);
        assertEquals(score, CampaignStars.scoreVictory());
    }

    @Test
    void enemyCoreAndFriendlyWallDamageCannotLowerPlayerCoreGrade(){
        start(SectorPresets.groundZero, Mode.HEROIC);
        var enemyTile = emptyTileFor(Blocks.coreShard.size);
        enemyTile.setBlock(Blocks.coreShard, state.rules.waveTeam);
        var enemy = enemyTile.build;
        enemy.damage(enemy.maxHealth * 0.2f);
        enemy.heal();
        var wall = place(Blocks.copperWall);
        wall.damage(wall.maxHealth * 0.5f);
        wall.heal();
        CampaignStars.update();
        assertEquals(1f, CampaignStars.minimumCoreHealth());
        assertEquals(1, CampaignStars.scoreVictory());
    }

    @Test
    void uninstrumentedNativeCoreDamageDoesNotCreateStarHistory(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        state.rules.tags.remove("stars.preset");
        var core = state.rules.defaultTeam.core();
        core.damage(core.maxHealth * 0.35f);
        core.heal();
        CampaignStars.update();
        assertFalse(CampaignStars.active());
        assertEquals(1f, CampaignStars.minimumCoreHealth());
    }

    @Test
    void finishedRunKeepsItsRecordedHealthAndGrade(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        assertEquals(3, CampaignStars.recordVictory());
        var core = state.rules.defaultTeam.core();
        core.damage(core.maxHealth * 0.35f);
        core.heal();
        CampaignStars.update();
        assertEquals(1f, CampaignStars.minimumCoreHealth());
        assertEquals(3, CampaignStars.recordVictory());
    }

    @Test
    void anyFriendlyCoreDamageCountsBeforeRepair(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        var tile = emptyTileFor(Blocks.coreShard.size);
        tile.setBlock(Blocks.coreShard, state.rules.defaultTeam);
        var second = tile.build;
        assertTrue(state.rules.defaultTeam.cores().contains(core -> core == second));
        second.damage(second.maxHealth * 0.35f);
        second.heal();
        CampaignStars.update();
        assertEquals(0.65f, CampaignStars.minimumCoreHealth(), 0.0001f);
        assertEquals(2, CampaignStars.scoreVictory());
    }

    @Test
    void coreLossGradeAndRunFailureRemainRecorded(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        Events.fire(new BlockDestroyEvent(state.rules.defaultTeam.core().tile));
        assertEquals(1, CampaignStars.coreLosses());
        assertEquals(1, CampaignStars.scoreVictory());
        Events.fire(new GameOverEvent(state.rules.waveTeam));
        assertTrue(state.rules.tags.getBool("stars.failed"));
        assertTrue(state.rules.tags.getBool("stars.finished"));
        assertEquals(0, CampaignStars.recordVictory());
        assertEquals(0, CampaignStars.stars(SectorPresets.groundZero));
    }

    @Test
    void heroicNeedsTimeCoreIntegrityAndLimitedBuildingLosses(){
        start(SectorPresets.groundZero, Mode.HEROIC);
        assertEquals(1, CampaignStars.scoreVictory());
        state.rules.tags.put("stars.elapsed", Float.toString(CampaignStars.limits(SectorPresets.groundZero).deadline() + 1f));
        assertEquals(0, CampaignStars.scoreVictory());
        state.rules.tags.put("stars.elapsed", "0");
        CampaignStars.recordCoreHealth(0.84f);
        assertEquals(0, CampaignStars.scoreVictory());
        state.rules.tags.put("stars.minCore", "1");
        state.stats.buildingsDestroyed = CampaignStars.limits(SectorPresets.groundZero).lossBudget() + 1;
        assertEquals(0, CampaignStars.recordVictory());
        assertTrue(CampaignStars.result().contains("0 / 1"));
        assertEquals(0, CampaignStars.stars(SectorPresets.groundZero, Mode.HEROIC));
    }

    @Test
    void tacticalRequiresNewFacilitiesAndConstructionBudget(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        assertFalse(CampaignStars.tacticalTaskComplete());
        transport(Blocks.conveyor, 16, false);
        var first = place(Blocks.mechanicalDrill);
        var second = place(Blocks.mechanicalDrill);
        assertFalse(CampaignStars.tacticalTaskComplete(), "idle facilities have not worked");
        mine(first);
        mine(second);
        CampaignStars.update();
        assertTrue(CampaignStars.tacticalTaskComplete(), CampaignStars.status());
        assertEquals(1, CampaignStars.scoreVictory());
        var file = saveDirectory.child("stars-working-logistics-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertTrue(CampaignStars.tacticalTaskComplete(), "worked new facilities remain qualified after reload");
        assertEquals(1, CampaignStars.scoreVictory());
        state.rules.tags.put("stars.placed", Integer.toString(CampaignStars.limits(SectorPresets.groundZero).buildBudget() + 1));
        assertEquals(0, CampaignStars.scoreVictory());
    }

    @Test
    void emptyRoutersConveyorsAndStoredItemsDoNotFulfillTransport(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        var first = place(Blocks.mechanicalDrill);
        var second = place(Blocks.mechanicalDrill);
        mine(first);
        mine(second);
        for(int i = 0; i < 20; i++){
            place(Blocks.router).items.add(Items.copper, 1);
            place(Blocks.conveyor).handleItem(first, Items.copper);
        }
        CampaignStars.update();
        assertFalse(CampaignStars.tacticalTaskComplete(), "inventory alone and routers are not actual transport");
        transport(Blocks.conveyor, 16, false);
        assertTrue(CampaignStars.tacticalTaskComplete());
        state.rules.tags.put("stars.elapsed", Float.toString(CampaignStars.elapsed() + 61f));
        assertFalse(CampaignStars.tacticalTaskComplete(), "production must be recent at victory");
        first.items.clear();
        second.items.clear();
        mine(first);
        mine(second);
        assertTrue(CampaignStars.tacticalTaskComplete());
    }

    @Test
    void ductActualDeliveryIsRecognizedAndUntaggedTransportDoesNotLeak(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        transport(Blocks.duct, 16, false);
        mine(place(Blocks.mechanicalDrill));
        mine(place(Blocks.mechanicalDrill));
        assertTrue(CampaignStars.tacticalTaskComplete());
        state.rules.tags.remove("stars.preset");
        int tags = state.rules.tags.size;
        transport(Blocks.conveyor, 16, false);
        assertEquals(tags, state.rules.tags.size, "global output hooks do not affect untagged games");
    }

    @Test
    void newDrillsKeepOutputIdentityAcrossAirliftSaveAndDropsCostBudget(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        transport(Blocks.conveyor, 16, false);
        var first = place(Blocks.mechanicalDrill);
        var second = place(Blocks.mechanicalDrill);
        mine(first);
        mine(second);
        assertTrue(CampaignStars.tacticalTaskComplete());
        var carrier = UnitTypes.oct.create(state.rules.defaultTeam);
        carrier.set(first);
        carrier.add();
        var payloadCarrier = (Payloadc)carrier;
        assertTrue(payloadCarrier.canPickup(first));
        assertTrue(payloadCarrier.canPickup(second));
        payloadCarrier.pickup(first);
        payloadCarrier.pickup(second);
        int carrierId = carrier.id;
        int budget = CampaignStars.placed();
        assertFalse(CampaignStars.tacticalTaskComplete(), "carried facilities are not surviving placed facilities");
        var file = saveDirectory.child("stars-airlift-new-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restored = Groups.unit.getByID(carrierId);
        assertNotNull(restored, "native SaveVersion preserves carrier unit ID");
        var payloads = (Payloadc)restored;
        assertEquals(2, payloads.payloads().size, "native payload order is preserved");
        assertEquals(Blocks.mechanicalDrill, ((BuildPayload)payloads.payloads().get(0)).block());
        var destination = emptyTileFor(2);
        restored.set(destination.worldx() + Blocks.mechanicalDrill.offset, destination.worldy() + Blocks.mechanicalDrill.offset);
        assertTrue(payloads.dropLastPayload());
        assertEquals(budget + 1, CampaignStars.placed());
        destination = emptyTileFor(2);
        restored.set(destination.worldx() + Blocks.mechanicalDrill.offset, destination.worldy() + Blocks.mechanicalDrill.offset);
        assertTrue(payloads.dropLastPayload());
        assertEquals(budget + 2, CampaignStars.placed());
        assertTrue(CampaignStars.tacticalTaskComplete(), "new facilities retain prior actual output when airlifted and reloaded");
    }

    @Test
    void initialFacilityAirliftNeverBecomesNewAfterSave(){
        Building initial = null;
        for(var preset : CampaignStars.mainLevels(Planets.erekir)){
            logic.reset();
            start(preset, Mode.TACTICAL);
            initial = Groups.build.find(b -> b.team == state.rules.defaultTeam && b.canPickup() && b.block.size <= 3 &&
                (b.block instanceof mindustry.world.blocks.distribution.Duct || b.block instanceof mindustry.world.blocks.distribution.Conveyor));
            if(initial != null) break;
        }
        assertNotNull(initial);
        var carrier = UnitTypes.oct.create(state.rules.defaultTeam);
        carrier.set(initial);
        carrier.add();
        var payloads = (Payloadc)carrier;
        assertTrue(payloads.canPickup(initial));
        payloads.pickup(initial);
        int carrierId = carrier.id;
        var file = saveDirectory.child("stars-airlift-initial-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restored = Groups.unit.getByID(carrierId);
        var restoredPayloads = (Payloadc)restored;
        var block = ((BuildPayload)restoredPayloads.payloads().peek()).block();
        var destination = emptyTileFor(block.size);
        restored.set(destination.worldx() + block.offset, destination.worldy() + block.offset);
        assertTrue(restoredPayloads.dropLastPayload());
        assertFalse(state.rules.tags.getBool("stars.constructed." + state.rules.tags.getInt("stars.asset." + destination.pos())),
            "a relocated initial structure still has an initial identity");
        assertFalse(CampaignStars.tacticalTaskComplete());
    }

    @Test
    void newFacilityIdentitySurvivesSaveInsidePayloadConveyor(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        transport(Blocks.conveyor, 16, false);
        var first = place(Blocks.mechanicalDrill);
        mine(first);
        mine(place(Blocks.mechanicalDrill));
        int identity = CampaignStars.identity(first);
        var receiver = place(Blocks.payloadConveyor);
        int receiverPosition = receiver.pos();
        var carrier = UnitTypes.oct.create(state.rules.defaultTeam);
        carrier.set(first);
        carrier.add();
        var payloads = (Payloadc)carrier;
        payloads.pickup(first);
        carrier.set(receiver);
        assertTrue(payloads.dropLastPayload());
        assertTrue(receiver.getPayload() instanceof BuildPayload);
        assertFalse(CampaignStars.tacticalTaskComplete());
        int carrierId = carrier.id;
        int budget = CampaignStars.placed();
        var file = saveDirectory.child("stars-facility-in-payload-conveyor-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restored = Groups.unit.getByID(carrierId);
        var restoredPayloads = (Payloadc)restored;
        restoredPayloads.addPayload(world.build(receiverPosition).takePayload());
        var destination = emptyTileFor(2);
        restored.set(destination.worldx() + Blocks.mechanicalDrill.offset, destination.worldy() + Blocks.mechanicalDrill.offset);
        assertTrue(restoredPayloads.dropLastPayload());
        assertEquals(budget + 1, CampaignStars.placed());
        assertEquals(identity, CampaignStars.identity(destination.build));
        assertTrue(CampaignStars.tacticalTaskComplete(), "an actual newly built drill keeps its output after a carrier-to-conveyor save");
    }

    @Test
    void genericSaveRestoresRunAndCannotOverwriteNativeSector(){
        start(SectorPresets.frozenForest, Mode.NORMAL);
        var oldInfo = SectorPresets.frozenForest.sector.info;
        var oldSave = SectorPresets.frozenForest.sector.save;
        CampaignStars.recordCoreHealth(0.63f);
        state.rules.tags.put("stars.elapsed", "321.5");
        state.rules.tags.put("stars.placed", "48");
        state.stats.buildingsDestroyed = 9;
        var file = saveDirectory.child("stars-run-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertNull(state.rules.sector);
        assertSame(oldInfo, SectorPresets.frozenForest.sector.info);
        assertSame(oldSave, SectorPresets.frozenForest.sector.save);
        assertEquals(0.63f, CampaignStars.minimumCoreHealth());
        assertEquals(321.5f, CampaignStars.elapsed());
        assertEquals(48, CampaignStars.placed());
        assertEquals(9, state.stats.buildingsDestroyed);
        assertEquals(2, CampaignStars.recordVictory());
    }

    @Test
    void savedFailureCannotAwardStarsAfterReload(){
        start(SectorPresets.groundZero, Mode.HEROIC);
        Events.fire(new GameOverEvent(state.rules.waveTeam));
        var file = saveDirectory.child("stars-failure-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertTrue(state.rules.tags.getBool("stars.failed"));
        assertEquals(0, CampaignStars.recordVictory());
        assertEquals(0, CampaignStars.stars(SectorPresets.groundZero));
    }

    void awardAllBranches(){
        for(var preset : WatchCampaign.sectors){
            for(var mode : Mode.values()) CampaignStars.saveBest(preset, mode, mode.maximum);
        }
    }

    @Test
    void permanentBonusesAreCappedAndRepeatedApplicationIsIdempotent(){
        awardAllBranches();
        start(SectorPresets.groundZero, Mode.NORMAL);
        float build = state.rules.tags.getFloat("stars.rewardBuildBase");
        float refund = state.rules.tags.getFloat("stars.rewardRefundBase");
        assertEquals(build * 1.08f, state.rules.defaultTeam.rules().buildSpeedMultiplier, 0.0001f);
        assertEquals(Math.min(1f, refund + 0.1f), state.rules.deconstructRefundMultiplier, 0.0001f);
        int copper = state.rules.defaultTeam.core().items.get(Items.copper);
        int coal = state.rules.defaultTeam.core().items.get(Items.coal);
        CampaignStars.applyRewards(state.rules);
        CampaignStars.applyRewards(state.rules);
        assertEquals(build * 1.08f, state.rules.defaultTeam.rules().buildSpeedMultiplier, 0.0001f);
        assertEquals(copper, state.rules.defaultTeam.core().items.get(Items.copper));
        assertEquals(coal, state.rules.defaultTeam.core().items.get(Items.coal));
        assertEquals(50, coal);
        assertEquals(0, state.rules.tags.getInt("stars.coreLosses"));
        assertEquals(1f, state.rules.defaultTeam.rules().unitDamageMultiplier);
        assertEquals(1f, state.rules.defaultTeam.rules().blockDamageMultiplier);
    }

    @Test
    void finiteCargoOnlyAppliesOnFreshNormalRunAndSurvivesSaving(){
        awardAllBranches();
        start(SectorPresets.groundZero, Mode.NORMAL);
        int copper = state.rules.defaultTeam.core().items.get(Items.copper);
        int lead = state.rules.defaultTeam.core().items.get(Items.lead);
        assertEquals(50, state.rules.defaultTeam.core().items.get(Items.coal));
        var file = saveDirectory.child("stars-supplies-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        CampaignStars.applyRewards(state.rules);
        assertEquals(copper, state.rules.defaultTeam.core().items.get(Items.copper));
        assertEquals(lead, state.rules.defaultTeam.core().items.get(Items.lead));
        assertEquals(50, state.rules.defaultTeam.core().items.get(Items.coal));
        assertEquals(0, CampaignStars.stars(SectorPresets.groundZero));
        logic.reset();
        start(SectorPresets.groundZero, Mode.HEROIC);
        assertEquals(0, state.rules.defaultTeam.core().items.get(Items.coal));
    }

    @Test
    void untaggedCustomRulesReceiveNoPermanentRewards(){
        awardAllBranches();
        var rules = new Rules();
        CampaignStars.applyRewards(rules);
        assertEquals(1f, rules.teams.get(rules.defaultTeam).buildSpeedMultiplier);
        assertEquals(0.5f, rules.deconstructRefundMultiplier);
        assertFalse(rules.tags.containsKey("stars.rewardSupplies"));
    }

    @Test
    void branchPointsNeverUnlockOtherBranches(){
        awardAllBranches();
        assertEquals(0, CampaignStars.mainStars());
        assertEquals(50, CampaignStars.totalStars());
        assertFalse(CampaignStars.branchUnlocked(0));
        CampaignStars.saveBest(WatchCampaign.anchors[0], Mode.NORMAL, 3);
        assertTrue(CampaignStars.branchUnlocked(0));
        assertFalse(CampaignStars.branchUnlocked(1));
    }

    @Test
    void intersectKeepsItsOriginalAttackAfterWavesPhase(){
        start(SectorPresets.intersect, Mode.NORMAL);
        assertTrue(state.rules.tags.getBool("stars.attackAfterWaves"));
        assertEquals(0, state.rules.winWave, "native generic win check cannot finish phase one");
        assertTrue(state.rules.waveTeam.cores().size > 0);
        state.wave = state.rules.tags.getInt("stars.phaseWave");
        state.enemies = 0;
        CampaignStars.update();
        assertTrue(state.rules.attackMode);
        assertFalse(state.rules.waves);
        assertFalse(state.rules.tags.getBool("stars.attackAfterWaves"));
        assertFalse(state.rules.tags.getBool("stars.finished"));
    }

    @Test
    void lakeTacticalAllowsItsAccessibleWaterCondenser(){
        CampaignStars.saveBest(SectorPresets.aegis, Mode.NORMAL, 1);
        start(SectorPresets.lake, Mode.TACTICAL);
        assertTrue(CampaignStars.available(Blocks.ventCondenser));
        transport(Blocks.reinforcedConduit, 12, true);
        var first = place(Blocks.ventCondenser);
        var second = place(Blocks.ventCondenser);
        assertFalse(CampaignStars.tacticalTaskComplete());
        //Native liquid output is needed; efficiency and warmup alone do not count.
        first.efficiency = second.efficiency = 1f;
        ((mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild)first).warmup = 1f;
        ((mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild)second).warmup = 1f;
        CampaignStars.update();
        assertFalse(CampaignStars.tacticalTaskComplete());
        ((mindustry.world.blocks.production.AttributeCrafter.AttributeCrafterBuild)first).attrsum = 9f;
        ((mindustry.world.blocks.production.AttributeCrafter.AttributeCrafterBuild)second).attrsum = 9f;
        first.updateTile();
        second.updateTile();
        assertTrue(first.liquids.get(Liquids.water) > 0f);
        assertTrue(second.liquids.get(Liquids.water) > 0f);
        assertTrue(CampaignStars.tacticalTaskComplete());
    }

    @Test
    void emptyConduitsAndDryPumpsNeverCountAsUsefulFacilities(){
        start(SectorPresets.ruinousShores, Mode.TACTICAL);
        for(int i = 0; i < 12; i++) place(Blocks.conduit).liquids.add(Liquids.water, 1f);
        var first = place(Blocks.mechanicalPump);
        var second = place(Blocks.mechanicalPump);
        first.tile.setFloor(Blocks.stone.asFloor());
        second.tile.setFloor(Blocks.stone.asFloor());
        first.onProximityUpdate();
        second.onProximityUpdate();
        for(int i = 0; i < 60; i++){
            first.update();
            second.update();
        }
        assertFalse(CampaignStars.tacticalTaskComplete());
        transport(Blocks.conduit, 12, true);
        first.tile.setFloor(Blocks.water.asFloor());
        second.tile.setFloor(Blocks.water.asFloor());
        first.onProximityUpdate();
        second.onProximityUpdate();
        first.update();
        second.update();
        assertTrue(first.liquids.get(Liquids.water) > 0f);
        assertTrue(second.liquids.get(Liquids.water) > 0f);
        assertTrue(CampaignStars.tacticalTaskComplete());
    }

    @ParameterizedTest
    @ValueSource(strings = {"conveyor", "duct"})
    void realClosedItemLoopsDoNotSupplyAnyTerminal(String name){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(7, 7);
        var block = content.block(name);
        Seq<Building> cycle = new Seq<>();
        for(int i = 0; i < 4; i++) cycle.add(at(block, origin.x + i, origin.y, 0));
        for(int i = 0; i < 4; i++) cycle.add(at(block, origin.x + 4, origin.y + i, 1));
        for(int i = 0; i < 4; i++) cycle.add(at(block, origin.x + 4 - i, origin.y + 4, 2));
        for(int i = 0; i < 4; i++) cycle.add(at(block, origin.x, origin.y + 4 - i, 3));
        cycle.first().handleItem(cycle.first(), Items.copper);
        run(cycle, 2000);
        assertTrue(cycle.count(this::transported) == 16, "all sixteen loop segments actually hand off the item");
        assertEquals(1, cycle.sum(b -> b.items.get(Items.copper)), "the item truly circulates instead of disappearing");
        assertEquals(0, suppliedCount(), "a pure loop has no real core/ammunition/production endpoint");
        mine(place(Blocks.mechanicalDrill));
        mine(place(Blocks.mechanicalDrill));
        assertFalse(CampaignStars.tacticalTaskComplete(), "two unrelated working drills do not make the closed loop useful");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeCoreUnloadReturnsDoNotEarnSupply(boolean otherCore){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(16, 9);
        int x = origin.x + 2, y = origin.y + 2;
        var source = at(Blocks.coreShard, x, y, 0);
        source.items.set(Items.copper, 20);
        var unloader = at(Blocks.unloader, x + 2, y, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)unloader).sortItem = Items.copper;
        Seq<Building> route = new Seq<>();
        if(otherCore){
            for(int i = 3; i <= 6; i++) route.add(at(Blocks.conveyor, x + i, y, 0));
            at(Blocks.coreShard, x + 8, y, 0);
        }else{
            route.add(at(Blocks.conveyor, x + 3, y, 0));
            route.add(at(Blocks.conveyor, x + 4, y, 1));
            route.add(at(Blocks.conveyor, x + 4, y + 1, 1));
            route.add(at(Blocks.conveyor, x + 4, y + 2, 2));
            route.add(at(Blocks.conveyor, x + 3, y + 2, 2));
            route.add(at(Blocks.conveyor, x + 2, y + 2, 2));
            route.add(at(Blocks.conveyor, x + 1, y + 2, 2));
            route.add(at(Blocks.conveyor, x, y + 2, 3));
        }
        run(new Seq<Building>().add(unloader).addAll(route), 1800);
        assertEquals(route.size, route.count(this::transported), "native unloader and conveyors really return core stock");
        assertEquals(0, suppliedCount(), "neither same-core nor different-core return establishes a new origin");
        assertTrue(state.rules.tags.getBool("stars.core." + CampaignStars.identity(source)));
    }

    @Test
    void coreAmmunitionSupplyStopsAtCoreHistoryBoundary(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(24, 8);
        int x = origin.x + 2, y = origin.y + 2;
        var source = at(Blocks.coreShard, x, y, 0);
        var firstUnloader = at(Blocks.unloader, x + 2, y, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)firstUnloader).sortItem = Items.copper;
        Seq<Building> returned = new Seq<>(), ammunition = new Seq<>();
        for(int i = 3; i <= 6; i++) returned.add(at(Blocks.conveyor, x + i, y, 0));
        var middle = at(Blocks.coreShard, x + 8, y, 0);
        source.items.set(Items.copper, 100);
        run(new Seq<Building>().add(firstUnloader).addAll(returned), 1000);
        assertEquals(4, returned.count(this::transported));
        assertEquals(0, suppliedCount());
        var secondUnloader = at(Blocks.unloader, x + 10, y, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)secondUnloader).sortItem = Items.copper;
        for(int i = 11; i <= 14; i++) ammunition.add(at(Blocks.conveyor, x + i, y, 0));
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)at(Blocks.duo, x + 15, y, 0);
        run(new Seq<Building>().add(secondUnloader).addAll(ammunition), 1000);
        assertTrue(turret.totalAmmo > 0, "finite physical copper becomes valid native turret ammunition");
        assertEquals(4, ammunition.count(this::supplied));
        assertEquals(0, returned.count(this::supplied), "ammo supply must not reach through a core to its unrelated historical return inputs");
        assertTrue(state.rules.tags.getBool("stars.core." + CampaignStars.identity(middle)));
    }

    @Test
    void coreProductionSupplyNeedsRealConsumedOutput(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(16, 8);
        int x = origin.x + 2, y = origin.y + 2;
        var core = at(Blocks.coreShard, x, y, 0);
        core.items.set(Items.coal, 20);
        var unloader = at(Blocks.unloader, x + 2, y, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)unloader).sortItem = Items.coal;
        Seq<Building> line = new Seq<>();
        for(int i = 3; i <= 6; i++) line.add(at(Blocks.conveyor, x + i, y, 0));
        var factory = at(Blocks.graphitePress, x + 7, y, 0);
        run(new Seq<Building>().add(unloader).addAll(line), 1000);
        assertTrue(factory.items.get(Items.coal) > 0);
        assertEquals(0, suppliedCount(), "a stockpiled input in an idle factory is not a consumed production endpoint");
        run(Seq.with(factory), 150);
        assertTrue(factory.items.get(Items.graphite) > 0, "the native recipe really consumes coal and outputs graphite");
        assertEquals(4, suppliedCount(), "core stock may legitimately feed a real producing factory");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realMiningAndCraftingOutputsRemainValidCoreOrigins(boolean crafted){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(16, 8);
        int x = origin.x + 2, y = origin.y + 2;
        var producer = at(crafted ? Blocks.graphitePress : Blocks.mechanicalDrill, x, y, 0);
        Item output = crafted ? Items.graphite : Items.copper;
        if(crafted){
            producer.items.add(Items.coal, 5);
            run(Seq.with(producer), 150);
            assertTrue(producer.items.get(output) > 0);
        }else mine(producer);
        Seq<Building> route = new Seq<>();
        for(int i = 2; i <= 5; i++) route.add(at(Blocks.conveyor, x + i, y, 0));
        var core = at(Blocks.coreShard, x + 7, y, 0);
        int before = core.items.get(output);
        assertTrue(producer.dump(output), "a physically produced item enters the real conveyor route");
        run(route, 800);
        assertEquals(before + 1, core.items.get(output));
        assertEquals(4, suppliedCount());
        assertTrue(state.rules.tags.getBool("stars.origin.i" + output.name + ":" + CampaignStars.identity(producer) + ":4"));
    }

    @Test
    void aFactoryOriginIsSpecificToItsActualOutputItem(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(16, 8);
        int x = origin.x + 2, y = origin.y + 2;
        var producer = at(Blocks.graphitePress, x, y, 0);
        producer.items.add(Items.coal, 5);
        run(Seq.with(producer), 150);
        assertTrue(producer.items.get(Items.graphite) > 0);
        Seq<Building> route = new Seq<>();
        for(int i = 2; i <= 5; i++) route.add(at(Blocks.conveyor, x + i, y, 0));
        var core = at(Blocks.coreShard, x + 7, y, 0);
        //Fixture inventory distinguishes old copper stock from the factory's actual graphite output.
        producer.items.add(Items.copper, 1);
        int copper = core.items.get(Items.copper);
        assertTrue(producer.dump(Items.copper));
        run(route, 800);
        assertEquals(copper + 1, core.items.get(Items.copper));
        assertEquals(0, suppliedCount(), "past graphite output is not an origin for the copper it happens to forward");
        assertTrue(producer.dump(Items.graphite));
        run(route, 800);
        assertEquals(4, suppliedCount());
    }

    @Test
    void genuineMineBranchDoesNotCreditTheMergedCoreReturnBranch(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(22, 16);
        int x = origin.x + 9, y = origin.y + 5;
        var drill = at(Blocks.mechanicalDrill, x - 6, y, 0);
        mine(drill);
        Seq<Building> mined = new Seq<>(), returned = new Seq<>(), common = new Seq<>();
        for(int i = -4; i <= -1; i++) mined.add(at(Blocks.conveyor, x + i, y, 0));
        var router = at(Blocks.router, x, y, 0);
        var source = at(Blocks.coreShard, x, y + 6, 0);
        var unloader = at(Blocks.unloader, x, y + 4, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)unloader).sortItem = Items.copper;
        for(int i = 3; i >= 1; i--) returned.add(at(Blocks.conveyor, x, y + i, 3));
        for(int i = 1; i <= 4; i++) common.add(at(Blocks.conveyor, x + i, y, 0));
        var target = at(Blocks.coreShard, x + 6, y, 0);
        source.items.set(Items.copper, 20);
        var active = new Seq<Building>().add(unloader).addAll(returned).add(router).addAll(common);
        run(active, 1000);
        assertEquals(3, returned.count(this::transported));
        assertEquals(4, common.count(this::transported));
        assertEquals(0, suppliedCount());
        int before = target.items.get(Items.copper);
        active.remove(unloader);
        assertTrue(drill.dump(Items.copper));
        run(new Seq<Building>().addAll(mined).addAll(active), 1000);
        assertTrue(target.items.get(Items.copper) > before, "finite remaining stock and the actual mine output arrive after native unloading stops");
        assertEquals(4, mined.count(this::supplied));
        assertEquals(4, common.count(this::supplied));
        assertEquals(0, returned.count(this::supplied), "one genuine merged origin cannot grant credit to the pure core-only upstream branch");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coreLinkedStorageManualInputAndUnloadReturnDoNotCreateOrigins(boolean otherCore){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(18, 9);
        int x = origin.x + 2, y = origin.y + 2;
        var core = at(Blocks.coreShard, x, y, 0);
        var storage = (mindustry.world.blocks.storage.StorageBlock.StorageBuild)at(Blocks.container, x + 2, y, 0);
        assertSame(core, storage.linkedCore, "native proximity joins the container to the core inventory");
        assertSame(core.items, storage.items);
        unitInput(storage, Items.copper, 1);
        int storageToken = CampaignStars.identity(storage);
        assertTrue(state.rules.tags.getBool("stars.core." + storageToken));
        assertFalse(state.rules.tags.containsKey("stars.origin.icopper:" + storageToken + ":4"));
        var unloader = at(Blocks.unloader, x + 4, y, 0);
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)unloader).sortItem = Items.copper;
        Seq<Building> route = new Seq<>();
        if(otherCore){
            for(int i = 5; i <= 8; i++) route.add(at(Blocks.conveyor, x + i, y, 0));
            at(Blocks.coreShard, x + 10, y, 0);
        }else{
            route.add(at(Blocks.conveyor, x + 5, y, 0));
            route.add(at(Blocks.conveyor, x + 6, y, 1));
            route.add(at(Blocks.conveyor, x + 6, y + 1, 1));
            for(int i = 6; i >= 1; i--) route.add(at(Blocks.conveyor, x + i, y + 2, 2));
            route.add(at(Blocks.conveyor, x, y + 2, 3));
        }
        core.items.set(Items.copper, 20);
        run(new Seq<Building>().add(unloader).addAll(route), 1500);
        assertEquals(route.size, route.count(this::transported));
        assertEquals(0, suppliedCount(), "core stock leaving through its real attached container is still core stock");
    }

    @Test
    void aFormerLinkedStorageKeepsItsCoreBoundaryAfterDestructionAndSave(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(18, 9);
        int x = origin.x + 2, y = origin.y + 2;
        var storage = (mindustry.world.blocks.storage.StorageBlock.StorageBuild)at(Blocks.container, x + 2, y, 0);
        unitInput(storage, Items.copper, 1);
        int storagePosition = storage.pos(), token = CampaignStars.identity(storage);
        assertTrue(state.rules.tags.getBool("stars.origin.icopper:" + token + ":4"), "a real standalone manual source predates the core attachment");
        var sourceCore = at(Blocks.coreShard, x, y, 0);
        assertSame(sourceCore, storage.linkedCore);
        var unloader = at(Blocks.unloader, x + 4, y, 0);
        int unloaderPosition = unloader.pos();
        ((mindustry.world.blocks.storage.Unloader.UnloaderBuild)unloader).sortItem = Items.copper;
        Seq<Building> route = new Seq<>();
        for(int i = 5; i <= 8; i++) route.add(at(Blocks.conveyor, x + i, y, 0));
        at(Blocks.coreShard, x + 10, y, 0);
        sourceCore.items.set(Items.copper, 30);
        run(new Seq<Building>().add(unloader).addAll(route), 1000);
        assertEquals(0, suppliedCount(), "the new core link takes precedence over the older standalone origin");
        assertTrue(state.rules.tags.getBool("stars.core." + token));
        sourceCore.tile.remove();
        assertNull(storage.linkedCore);
        assertTrue(storage.items.get(Items.copper) > 0, "native core removal leaves finite redistributed stock in the detached container");
        var file = saveDirectory.child("stars-historical-linked-storage.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restored = (mindustry.world.blocks.storage.StorageBlock.StorageBuild)world.build(storagePosition);
        assertNull(restored.linkedCore);
        assertEquals(token, CampaignStars.identity(restored));
        assertTrue(state.rules.tags.getBool("stars.core." + token));
        assertTrue(state.rules.tags.getBool("stars.origin.icopper:" + token + ":4"));
        int stock = restored.items.get(Items.copper);
        Seq<Building> updates = Seq.with(world.build(unloaderPosition));
        for(int i = 5; i <= 8; i++) updates.add(world.tile(x + i, y).build);
        run(updates, 1200);
        assertTrue(restored.items.get(Items.copper) < stock, "the restored native unloader actually takes remaining detached stock");
        assertEquals(0, suppliedCount(), "losing the live core object does not reactivate a historical storage origin");
    }

    @Test
    void zeroAmountUnitAnimationAndUntypedStoredItemsDoNotCreateOrigins(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(12, 7);
        var first = at(Blocks.conveyor, origin.x, origin.y + 2, 0);
        var second = at(Blocks.conveyor, origin.x + 1, origin.y + 2, 0);
        var core = at(Blocks.coreShard, origin.x + 3, origin.y + 2, 0);
        var unit = UnitTypes.alpha.create(state.rules.defaultTeam);
        InputHandler.transferItemTo(unit, Items.copper, 0, first.x, first.y, first);
        first.handleItem(first, Items.copper);
        int before = core.items.get(Items.copper);
        run(Seq.with(first, second), 600);
        assertEquals(before + 1, core.items.get(Items.copper));
        assertEquals(0, suppliedCount(), "neither a zero-amount effect nor unproven stored copper is a real origin");
        unitInput(first, Items.copper, 1);
        run(Seq.with(first, second), 600);
        assertEquals(2, suppliedCount(), "a later positive native manual delivery does establish its own origin");
    }

    @Test
    void actualCoreChainSuppliesThroughBridgeAndRouter(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(13, 6);
        var first = at(Blocks.conveyor, origin.x, origin.y + 2, 0);
        var bridge = at(Blocks.itemBridge, origin.x + 1, origin.y + 2, 0);
        var landing = at(Blocks.itemBridge, origin.x + 4, origin.y + 2, 0);
        ((mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)bridge).link = landing.pos();
        var router = at(Blocks.router, origin.x + 5, origin.y + 2, 0);
        var last = at(Blocks.conveyor, origin.x + 6, origin.y + 2, 0);
        var core = at(Blocks.coreShard, origin.x + 8, origin.y + 2, 0);
        int before = core.items.get(Items.copper);
        unitInput(first, Items.copper, 1);
        run(Seq.with(first, bridge, landing, router, last), 3000);
        assertEquals(before + 1, core.items.get(Items.copper));
        assertTrue(supplied(first));
        assertTrue(supplied(last));
        assertEquals(2, suppliedCount(), "bridges and routers preserve the route but are not basic-transport quota tiles");
    }

    @Test
    void crossingJunctionLanesNeverShareAFalseCoreEndpoint(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(11, 11);
        int x = origin.x + 4, y = origin.y + 4;
        var transport = Blocks.conveyor;
        var west = at(transport, x - 2, y, 0);
        var westNear = at(transport, x - 1, y, 0);
        var south = at(transport, x, y - 2, 1);
        var southNear = at(transport, x, y - 1, 1);
        var junction = at(Blocks.junction, x, y, 0);
        var core = at(Blocks.coreShard, x + 2, y, 0);
        var deadEnd = at(Blocks.router, x, y + 1, 0);
        int before = core.items.get(Items.copper);
        unitInput(west, Items.copper, 1);
        unitInput(south, Items.copper, 1);
        //Leave the northern terminal as storage; it never consumes or delivers the item.
        run(Seq.with(west, westNear, south, southNear, junction), 1500);
        assertEquals(before + 1, core.items.get(Items.copper));
        assertEquals(1, deadEnd.items.get(Items.copper));
        assertTrue(supplied(west));
        assertTrue(supplied(westNear));
        assertTrue(transported(south));
        assertTrue(transported(southNear));
        assertFalse(supplied(south));
        assertFalse(supplied(southNear), "the crossing route only reached an idle router");
    }

    @Test
    void massDriverActualPoweredShotPreservesItsSupplyChain(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(22, 9);
        int y = origin.y + 2;
        var first = at(Blocks.conveyor, origin.x + 1, y, 0);
        var sender = (mindustry.world.blocks.distribution.MassDriver.MassDriverBuild)at(Blocks.massDriver, origin.x + 3, y, 0);
        var receiver = (mindustry.world.blocks.distribution.MassDriver.MassDriverBuild)at(Blocks.massDriver, origin.x + 12, y, 0);
        var last = at(Blocks.conveyor, origin.x + 14, y, 0);
        var core = at(Blocks.coreShard, origin.x + 16, y, 0);
        sender.link = receiver.pos();
        sender.items.add(Items.copper, 9);
        unitInput(first, Items.copper, 1);
        Seq<Building> generators = new Seq<>();
        for(int i = 0; i < 4; i++){
            var generator = at(Blocks.combustionGenerator, origin.x + 6 + i, origin.y + 6, 0);
            generator.items.add(Items.coal, 20);
            generators.add(generator);
        }
        var graph = generators.first().power.graph;
        for(var generator : generators) graph.addGraph(generator.power.graph);
        graph.addGraph(sender.power.graph);
        graph.addGraph(receiver.power.graph);
        int before = core.items.get(Items.copper);
        boolean projectileSeen = false;
        for(int i = 0; i < 1200; i++){
            Time.update();
            for(var generator : generators) generator.update();
            generators.first().power.graph.update();
            first.update();
            sender.update();
            receiver.update();
            last.update();
            projectileSeen |= Groups.bullet.size() > 0;
            Groups.bullet.update();
            CampaignStars.update();
        }
        assertTrue(projectileSeen, "the native powered sender actually fires its payload projectile");
        assertTrue(generators.contains(g -> g.items.get(Items.coal) < 20), "native generators actually consume fuel");
        assertEquals(before + 10, core.items.get(Items.copper));
        assertTrue(supplied(first), "the upstream conveyor survives the remote handoff in the supply graph");
        assertTrue(supplied(last));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedOrEnemyMassDriverCargoDoesNotCreatePlayerFlow(boolean enemy){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var origin = rectangle(14, 7);
        var sender = (mindustry.world.blocks.distribution.MassDriver.MassDriverBuild)at(Blocks.massDriver, origin.x + 2, origin.y + 2, 0);
        var receiver = (mindustry.world.blocks.distribution.MassDriver.MassDriverBuild)at(Blocks.massDriver, origin.x + 10, origin.y + 2, 0);
        int from = CampaignStars.identity(sender), to = CampaignStars.identity(receiver);
        if(enemy) receiver.changeTeam(Team.crux);
        else receiver.items.add(Items.copper, Blocks.massDriver.itemCapacity * 2);
        var data = new mindustry.world.blocks.distribution.MassDriver.DriverBulletData();
        data.from = sender;
        data.to = receiver;
        data.items[Items.copper.id] = 10;
        var bolt = ((mindustry.world.blocks.distribution.MassDriver)Blocks.massDriver).bullet.create(sender, sender.team,
            receiver.x, receiver.y, 0f, 0f, 1f, 1f, data);
        //Use the native landing entry with a real projectile; zero accepted capacity and a captured receiver cannot create player supply.
        receiver.handlePayload(bolt, data);
        assertFalse(state.rules.tags.containsKey("stars.flow.icopper:" + from + ":4>icopper:" + to + ":4"));
        assertEquals(enemy ? 10 : Blocks.massDriver.itemCapacity * 2, receiver.items.get(Items.copper));
    }

    @Test
    void liquidBridgeAndRouterPreserveRealAmmunitionSupply(){
        start(SectorPresets.ruinousShores, Mode.TACTICAL);
        var origin = rectangle(13, 6);
        int y = origin.y + 2;
        var first = at(Blocks.conduit, origin.x, y, 0);
        var bridge = at(Blocks.bridgeConduit, origin.x + 1, y, 0);
        var receiver = at(Blocks.bridgeConduit, origin.x + 5, y, 0);
        ((mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)bridge).link = receiver.pos();
        var router = at(Blocks.liquidRouter, origin.x + 6, y, 0);
        var last = at(Blocks.conduit, origin.x + 7, y, 0);
        var turret = at(Blocks.wave, origin.x + 8, y, 0);
        first.handleLiquid(first, Liquids.water, 8f);
        run(Seq.with(first, bridge, receiver, router, last), 3000);
        assertTrue(turret.liquids.get(Liquids.water) > 0f, "water actually crosses the bridge and router into valid turret ammunition");
        assertTrue(supplied(first));
        assertTrue(supplied(last));
    }

    @Test
    void liquidSupplyNeedsActualGeneratorOutputAfterInput(){
        start(SectorPresets.ruinousShores, Mode.TACTICAL);
        var origin = rectangle(12, 6);
        Seq<Building> pipes = new Seq<>();
        for(int i = 0; i < 8; i++) pipes.add(at(Blocks.conduit, origin.x + i, origin.y + 2, 0));
        var generator = at(Blocks.steamGenerator, origin.x + 8, origin.y + 2, 0);
        pipes.first().handleLiquid(pipes.first(), Liquids.water, 8f);
        run(pipes, 600);
        assertTrue(generator.liquids.get(Liquids.water) > 0f);
        assertEquals(0, suppliedCount(), "stored liquid in a nonoperating consumer is not productive supply");
        generator.items.add(Items.coal, 2);
        for(int i = 0; i < 10; i++){
            generator.update();
            CampaignStars.update();
        }
        assertTrue(generator.getPowerProduction() > 0f, "native generator consumes fuel and water to generate power");
        assertEquals(8, suppliedCount());
    }

    Seq<Building> coolantLine(Block turretBlock, Liquid liquid){
        var origin = rectangle(22, 9);
        Seq<Building> pipes = new Seq<>();
        int y = origin.y + 3;
        for(int i = 0; i < 12; i++) pipes.add(at(Blocks.reinforcedConduit, origin.x + i, y, 0));
        var turret = at(turretBlock, origin.x + 12 + turretBlock.size / 2, y, 0);
        pipes.first().handleLiquid(pipes.first(), liquid, 20f);
        run(pipes, 1200);
        assertTrue(turret.liquids.get(liquid) > 0f, "native conduits actually deliver coolant");
        pipes.add(turret);
        return pipes;
    }

    @ParameterizedTest
    @ValueSource(strings = {"breach", "duo"})
    void nativeFixedAndFilteredCoolantConfirmSupplyOnlyAfterConsumption(String name){
        start(SectorPresets.lake, Mode.TACTICAL);
        var line = coolantLine(content.block(name), Liquids.water);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        assertEquals(0, suppliedCount(), "filling an idle turret does not confirm supply");
        turret.handleStack(name.equals("breach") ? Items.beryllium : Items.copper, 20, null);
        float before = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertTrue(turret.liquids.get(Liquids.water) < before, "the original reload actually consumes water");
        assertEquals(12, suppliedCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"breach", "duo"})
    void fullyReloadedNativeTurretDoesNotCreditStoredCoolant(String name){
        start(SectorPresets.lake, Mode.TACTICAL);
        var line = coolantLine(content.block(name), Liquids.water);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        turret.handleStack(name.equals("breach") ? Items.beryllium : Items.copper, 20, null);
        turret.reloadCounter = ((mindustry.world.blocks.defense.turrets.ReloadTurret)turret.block).reload;
        float before = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertEquals(before, turret.liquids.get(Liquids.water));
        assertEquals(0, suppliedCount());
    }

    @Test
    void coolingCreditsOnlyTheLiquidThatNativeConsumerUsed(){
        start(SectorPresets.lake, Mode.TACTICAL);
        var line = coolantLine(Blocks.duo, Liquids.cryofluid);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        var water = at(Blocks.reinforcedConduit, turret.tileX(), turret.tileY() + 1, 3);
        water.handleLiquid(water, Liquids.water, 8f);
        run(Seq.with(water), 600);
        assertTrue(turret.liquids.get(Liquids.water) > 0f);
        assertSame(Liquids.water, turret.liquids.current());
        turret.handleStack(Items.copper, 20, null);
        float cryo = turret.liquids.get(Liquids.cryofluid), storedWater = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertTrue(turret.liquids.get(Liquids.water) < storedWater);
        assertEquals(cryo, turret.liquids.get(Liquids.cryofluid));
        assertTrue(supplied(water));
        assertEquals(0, line.count(this::supplied), "unused cryofluid route remains uncredited");
    }

    @Test
    void capturedNativeTurretCoolingDoesNotConfirmPlayerSupply(){
        start(SectorPresets.lake, Mode.TACTICAL);
        var line = coolantLine(Blocks.breach, Liquids.water);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        turret.changeTeam(Team.crux);
        turret.handleStack(Items.beryllium, 20, null);
        float before = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertTrue(turret.liquids.get(Liquids.water) < before);
        assertEquals(0, line.count(this::supplied));
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"NORMAL", "HEROIC"})
    void nativeCoolingOutsideTacticalDoesNotCreateSupplyEvidence(Mode mode){
        start(SectorPresets.lake, mode);
        var line = coolantLine(Blocks.breach, Liquids.water);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        turret.handleStack(Items.beryllium, 20, null);
        float before = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertTrue(turret.liquids.get(Liquids.water) < before);
        assertFalse(state.rules.tags.keys().toSeq().contains(k -> k.startsWith("stars.transferred.")));
    }

    @Test
    void nativeCoolingSupplyAndPendingFlowSurviveWorldSave(){
        start(SectorPresets.lake, Mode.TACTICAL);
        var line = coolantLine(Blocks.breach, Liquids.water);
        var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)line.pop();
        int turretPosition = turret.pos();
        var positions = line.map(Building::pos);
        var file = saveDirectory.child("stars-pending-native-coolant.msav");
        SaveIO.write(file);
        SaveIO.load(file);
        assertEquals(0, suppliedCount());
        turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)world.build(turretPosition);
        turret.handleStack(Items.beryllium, 20, null);
        float before = turret.liquids.get(Liquids.water);
        run(Seq.with(turret), 10);
        assertTrue(turret.liquids.get(Liquids.water) < before);
        assertEquals(12, suppliedCount());
        SaveIO.write(file);
        SaveIO.load(file);
        assertEquals(12, suppliedCount());
        for(var position : positions) assertTrue(supplied(world.build(position)));
    }

    @Test
    void actualSupplyAndMovedTransportIdentitySurviveSaveIO(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var line = transport(Blocks.conveyor, 16, false);
        var moved = line.get(4);
        int token = CampaignStars.identity(moved);
        int inputToken = CampaignStars.identity(line.first());
        assertTrue(state.rules.tags.getBool("stars.origin.icopper:" + inputToken + ":4"));
        assertTrue(state.rules.tags.keys().toSeq().contains(key -> key.startsWith("stars.core.")));
        assertEquals(16, suppliedCount());
        var carrier = UnitTypes.mega.create(state.rules.defaultTeam);
        carrier.set(moved);
        carrier.add();
        var payloads = (Payloadc)carrier;
        assertTrue(payloads.canPickup(moved));
        payloads.pickup(moved);
        int id = carrier.id;
        assertEquals(15, suppliedCount(), "carried transport is not a surviving placed tile");
        var file = saveDirectory.child("stars-terminal-payload-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        var restored = Groups.unit.getByID(id);
        assertNotNull(restored);
        var destination = emptyTileFor(1);
        restored.set(destination.worldx(), destination.worldy());
        int budget = CampaignStars.placed();
        ((Payloadc)restored).dropLastPayload();
        assertNotNull(destination.build);
        assertEquals(token, CampaignStars.identity(destination.build));
        assertTrue(supplied(destination.build));
        assertEquals(16, suppliedCount());
        assertEquals(budget + 1, CampaignStars.placed());
        assertTrue(state.rules.tags.getBool("stars.origin.icopper:" + inputToken + ":4"));
        assertTrue(state.rules.tags.keys().toSeq().contains(key -> key.startsWith("stars.core.")));
    }

    @Test
    void legacyVersionOneChunkReconfirmsAAndPreservesBWorldAndBestScores(){
        start(SectorPresets.groundZero, Mode.TACTICAL);
        var line = transport(Blocks.conveyor, 16, false);
        var firstDrill = place(Blocks.mechanicalDrill);
        var secondDrill = place(Blocks.mechanicalDrill);
        mine(firstDrill);
        mine(secondDrill);
        assertTrue(CampaignStars.tacticalTaskComplete());
        int firstPosition = line.first().pos(), firstToken = CampaignStars.identity(line.first());
        String producedKey = "stars.produced." + CampaignStars.identity(firstDrill);
        String producedTime = state.rules.tags.get(producedKey);
        int corePosition = state.rules.defaultTeam.core().pos();
        int stock = world.build(corePosition).items.get(Items.copper);
        int buildBudget = CampaignStars.placed();
        float elapsed = CampaignStars.elapsed();
        CampaignStars.recordCoreHealth(0.8f);
        CampaignStars.saveBest(SectorPresets.frozenForest, Mode.TACTICAL, 1);
        CampaignStars.saveBest(SectorPresets.frozenForest, Mode.NORMAL, 3);
        state.rules.tags.put("stars.flowVersion", "1");
        var file = saveDirectory.child("stars-version-one-core-return.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertEquals("2", state.rules.tags.get("stars.flowVersion"));
        assertEquals(0, suppliedCount());
        assertTrue(transported(world.build(firstPosition)));
        assertEquals(firstToken, CampaignStars.identity(world.build(firstPosition)));
        assertEquals(producedTime, state.rules.tags.get(producedKey), "Task B output timestamps are not logistics evidence");
        assertEquals(buildBudget, CampaignStars.placed());
        assertEquals(elapsed, CampaignStars.elapsed());
        assertEquals(0.8f, CampaignStars.minimumCoreHealth());
        assertEquals(stock, world.build(corePosition).items.get(Items.copper));
        assertEquals(1, CampaignStars.stars(SectorPresets.frozenForest, Mode.TACTICAL));
        assertEquals(3, CampaignStars.stars(SectorPresets.frozenForest, Mode.NORMAL));
        assertFalse(state.rules.tags.keys().toSeq().contains(key -> key.startsWith("stars.origin.") || key.startsWith("stars.core.")));
        var restoredFirst = world.build(firstPosition);
        Seq<Building> restored = new Seq<>();
        for(int i = 0; i < 16; i++) restored.add(world.tile(restoredFirst.tile.x + i, restoredFirst.tile.y).build);
        unitInput(restoredFirst, Items.copper, 1);
        run(restored, 1200);
        assertEquals(16, suppliedCount(), "one actual new supply can reconfirm the surviving transports");
        assertEquals(producedTime, state.rules.tags.get(producedKey));
    }

    @Test
    void branchBoardUnlockAlsoRespectsItsCounterRewardPrerequisite(){
        for(var preset : CampaignStars.mainLevels(Planets.serpulo)) CampaignStars.saveBest(preset, Mode.NORMAL, 3);
        for(var preset : CampaignStars.mainLevels(Planets.erekir)) CampaignStars.saveBest(preset, Mode.NORMAL, 3);
        var branch = WatchCampaign.sectors[5];
        assertTrue(CampaignStars.branchUnlocked(5), "star total and its normal anchor have been satisfied");
        assertFalse(CampaignStars.completedReward(4));
        assertFalse(CampaignStars.unlocked(branch), "the board must expose the same W5 full-star prerequisite as the launcher");
        for(var mode : Mode.values()) CampaignStars.saveBest(WatchCampaign.sectors[4], mode, mode.maximum);
        assertTrue(CampaignStars.completedReward(4));
        assertTrue(CampaignStars.unlocked(branch));
    }

    @Test
    void oldOutputOnlySaveMustReconfirmTerminalSupplyWithoutLosingBestStars(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var line = transport(Blocks.conveyor, 16, false);
        int first = line.first().pos();
        assertEquals(16, suppliedCount());
        CampaignStars.saveBest(SectorPresets.groundZero, Mode.TACTICAL, 1);
        state.rules.tags.remove("stars.flowVersion");
        state.rules.tags.keys().toSeq().each(key -> {
            if(key.startsWith("stars.flow.") || key.startsWith("stars.received.")) state.rules.tags.remove(key);
        });
        var file = saveDirectory.child("stars-legacy-flow-test.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertEquals(0, suppliedCount());
        assertEquals(1, CampaignStars.stars(SectorPresets.groundZero, Mode.TACTICAL));
        assertEquals("2", state.rules.tags.get("stars.flowVersion"));
        var firstBuild = world.build(first);
        Seq<Building> restored = new Seq<>();
        for(int i = 0; i < 16; i++) restored.add(world.tile(firstBuild.tile.x + i, firstBuild.tile.y).build);
        unitInput(firstBuild, Items.copper, 1);
        run(restored, 2000);
        assertEquals(16, suppliedCount(), "a genuine later core delivery confirms old placed transports under the new rule");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void oversizedLedgerSurvivesNativeSaveIO(Mode mode){
        start(SectorPresets.frozenForest, mode);
        var line = transport(Blocks.conveyor, 16, false);
        int firstPosition = line.first().pos();
        int firstIdentity = CampaignStars.identity(line.first());
        int expectedSupply = mode == Mode.TACTICAL ? 16 : 0;
        assertEquals(expectedSupply, suppliedCount());
        //Synthetic large ledger exercises native persistence; this is not gameplay evidence.
        for(int i = 10000; i < 11800; i++){
            state.rules.tags.put("stars.constructed." + i, "true");
            state.rules.tags.put("stars.flow.icopper:" + i + ":4>icopper:" + (i + 1) + ":4", "true");
        }
        state.rules.tags.put("stars.bandage.wall.123456", "87.5");
        state.rules.tags.put("stars.bandage.asset." + firstIdentity, "92.5");
        CampaignStars.recordCoreHealth(0.65f);
        String rawRules = JsonIO.write(state.rules);
        assertThrows(UTFDataFormatException.class, () -> {
            var stream = new DataOutputStream(new ByteArrayOutputStream());
            stream.writeUTF(rawRules);
        }, "the old rules string cannot store this ledger");
        var liveRules = state.rules;
        var liveTags = liveRules.tags;
        var expected = new StringMap();
        expected.putAll(liveTags);
        int placed = CampaignStars.placed(), score = CampaignStars.scoreVictory();
        float elapsed = CampaignStars.elapsed();
        var file = saveDirectory.child("stars-large-ledger-" + mode + ".msav");
        SaveIO.write(file);
        assertSame(liveRules, state.rules);
        assertSame(liveTags, state.rules.tags);
        assertEquals(expected, liveTags, "save serialization never removes live evidence");
        var metadata = SaveIO.getMeta(file).rules;
        assertEquals(SectorPresets.frozenForest.name, metadata.tags.get("stars.preset"));
        assertEquals(mode.name(), metadata.tags.get("stars.mode"));
        assertEquals("0.65", metadata.tags.get("stars.minCore"));
        assertFalse(metadata.tags.containsKey("stars.flow.icopper:10000:4>icopper:10001:4"));
        assertDoesNotThrow(() -> new DataOutputStream(new ByteArrayOutputStream()).writeUTF(JsonIO.write(metadata)));
        logic.reset();
        SaveIO.load(file);
        assertEquals(expected, state.rules.tags);
        assertSame(SectorPresets.frozenForest, CampaignStars.currentPreset());
        assertEquals(mode, CampaignStars.currentMode());
        assertEquals(firstIdentity, CampaignStars.identity(world.build(firstPosition)));
        assertEquals(expectedSupply, suppliedCount());
        assertEquals(placed, CampaignStars.placed());
        assertEquals(elapsed, CampaignStars.elapsed());
        assertEquals(0.65f, CampaignStars.minimumCoreHealth());
        assertEquals(score, CampaignStars.scoreVictory());
        var restored = world.build(firstPosition);
        restored.handleItem(restored, Items.copper);
        run(Seq.with(restored), 60);
        assertEquals(expectedSupply, suppliedCount(), "restored terminal evidence continues to participate in the same mission");
    }

    @Test
    void oldEmbeddedLedgerIsReadWithoutAChunk(){
        start(SectorPresets.frozenForest, Mode.TACTICAL);
        var line = transport(Blocks.conveyor, 16, false);
        int first = line.first().pos(), identity = CampaignStars.identity(line.first());
        assertEquals(16, suppliedCount());
        state.rules.tags.put("stars.flowVersion", "1");
        var legacy = new SaveReadState(world.context);
        legacy.ruleString = JsonIO.write(state.rules);
        SaveIO.getSaveWriter().readRules(legacy);
        Events.fire(new SaveLoadEvent(false));
        assertEquals(identity, CampaignStars.identity(world.build(first)));
        assertTrue(transported(world.build(first)), "native output and asset identity survive the old metadata read");
        assertEquals(0, suppliedCount(), "old core-return credit needs new version-two supply evidence");
        assertEquals("2", state.rules.tags.get("stars.flowVersion"));
    }

    @Test
    void ordinaryRulesKeepTheirExistingMetadataSerialization(){
        var rules = new Rules();
        rules.tags.put("native.custom.123", "unchanged");
        rules.tags.put("map.note", "原版关卡标签");
        assertEquals(JsonIO.write(rules), CampaignStars.rulesForSave(rules));
        state.rules = rules;
        assertFalse(CampaignStars.active());
        var file = saveDirectory.child("stars-ordinary-metadata.msav");
        SaveIO.write(file);
        logic.reset();
        SaveIO.load(file);
        assertEquals(rules.tags, state.rules.tags);
        assertFalse(CampaignStars.active());
    }

    @Test
    void failedMetadataWriteKeepsTheLiveLedger(){
        start(SectorPresets.groundZero, Mode.NORMAL);
        state.rules.tags.put("stars.constructed.123", "true");
        //An unrelated oversized external value still fails through the original save API.
        state.rules.tags.put("external.note", "x".repeat(70000));
        var rules = state.rules;
        var tags = rules.tags;
        var file = saveDirectory.child("stars-external-metadata-failure.msav");
        assertThrows(RuntimeException.class, () -> SaveIO.write(file));
        assertSame(rules, state.rules);
        assertSame(tags, state.rules.tags);
        assertEquals("true", tags.get("stars.constructed.123"));
        assertEquals(70000, tags.get("external.note").length());
    }

    boolean transported(Building building){
        return state.rules.tags.getBool("stars.transported." + CampaignStars.identity(building));
    }

    boolean supplied(Building building){
        return transported(building) && state.rules.tags.getBool("stars.transferred." + CampaignStars.identity(building));
    }

    int suppliedCount(){
        //Use the product query to reconstruct persisted flow state before reading its evidence tags.
        CampaignStars.tacticalTaskComplete();
        return state.rules.defaultTeam.data().buildings.count(b ->
            (b.block instanceof mindustry.world.blocks.distribution.Conveyor || b.block instanceof mindustry.world.blocks.distribution.Duct || b.block instanceof mindustry.world.blocks.liquid.Conduit) && supplied(b));
    }

    void run(Seq<Building> buildings, int ticks){
        for(int i = 0; i < ticks; i++){
            Time.update();
            for(var building : buildings) building.update();
            CampaignStars.update();
        }
    }

    void unitInput(Building recipient, Item item, int amount){
        //Fixture provides finite player inventory; the real InputHandler performs the positive accepted transfer.
        var unit = UnitTypes.alpha.create(state.rules.defaultTeam);
        unit.stack.item = item;
        unit.stack.amount = amount;
        unit.set(recipient);
        int accepted = recipient.acceptStack(item, amount, unit);
        assertEquals(amount, accepted, "the target has native capacity for the test's finite manual delivery");
        InputHandler.transferItemTo(unit, item, accepted, unit.x, unit.y, recipient);
        assertEquals(0, unit.stack.amount);
    }

    Tile rectangle(int width, int height){
        for(var origin : world.tiles){
            if(origin.x < 10 || origin.y < 10 || origin.x + width >= world.width() - 10 || origin.y + height >= world.height() - 10) continue;
            boolean clear = true;
            for(int x = 0; x < width && clear; x++) for(int y = 0; y < height; y++){
                var tile = world.tile(origin.x + x, origin.y + y);
                if(tile.build != null || tile.block() != Blocks.air){ clear = false; break; }
            }
            if(clear) return origin;
        }
        throw new AssertionError("No room for a native transfer route");
    }

    Building at(Block block, int x, int y, int rotation){
        var tile = world.tile(x, y);
        tile.setBlock(block, state.rules.defaultTeam, rotation);
        Events.fire(new BlockBuildEndEvent(tile, null, state.rules.defaultTeam, false, null));
        return tile.build;
    }

    Seq<Building> transport(Block block, int count, boolean liquid){
        Tile origin = null;
        for(var tile : world.tiles){
            if(tile.x < 10 || tile.y < 10 || tile.x + count + 3 >= world.width() - 10 || tile.y >= world.height() - 10) continue;
            boolean clear = true;
            for(int i = 0; i <= count + 2; i++){
                if(!nearbyClear(world.tile(tile.x + i, tile.y), 1)){ clear = false; break; }
            }
            if(clear){ origin = tile; break; }
        }
        assertNotNull(origin, "room for a real transport line");
        Seq<Building> line = new Seq<>();
        for(int i = 0; i < count; i++){
            var tile = world.tile(origin.x + i, origin.y);
            tile.setBlock(block, state.rules.defaultTeam, 0);
            Events.fire(new BlockBuildEndEvent(tile, null, state.rules.defaultTeam, false, null));
            line.add(tile.build);
        }
        //Actual core input or valid water ammunition are legitimate supply endpoints.
        var sinkTile = world.tile(origin.x + count + (liquid ? 0 : 1), origin.y);
        sinkTile.setBlock(liquid ? Blocks.wave : Blocks.coreShard, state.rules.defaultTeam);
        int copperBefore = liquid ? 0 : sinkTile.build.items.get(Items.copper);
        if(liquid) line.first().handleLiquid(line.first(), Liquids.water, 8f);
        else unitInput(line.first(), Items.copper, 1);
        for(int i = 0; i < 2000; i++){
            Time.update();
            for(var build : line) build.update();
        }
        if(liquid) assertTrue(sinkTile.build.liquids.get(Liquids.water) > 0f, "real water reaches the end of the conduit line");
        else assertTrue(sinkTile.build.items.get(Items.copper) > copperBefore, "real copper enters the core from the conveyor/duct line");
        return line;
    }

    Tile emptyTileFor(int size){
        for(var tile : world.tiles){
            if(tile.x > 10 && tile.y > 10 && tile.x < world.width() - 10 && tile.y < world.height() - 10 &&
                tile.block() == Blocks.air && tile.build == null && nearbyClear(tile, size)){
                for(int x = -size; x <= size; x++){
                    for(int y = -size; y <= size; y++) world.tile(tile.x + x, tile.y + y).setFloor(Blocks.stone.asFloor());
                }
                if(size == 2) tile.getLinkedTilesAs(Blocks.mechanicalDrill, new Seq<>()).each(linked -> linked.setOverlay(Blocks.oreCopper));
                return tile;
            }
        }
        fail("No empty placement tile");
        return null;
    }

    Building place(Block block){
        Tile tile = null;
        for(var candidate : world.tiles){
            if(candidate.build == null && candidate.block() == Blocks.air && candidate.x > 10 && candidate.y > 10 &&
                candidate.x < world.width() - 10 && candidate.y < world.height() - 10 && nearbyClear(candidate, block.size)){
                tile = candidate;
                break;
            }
        }
        assertNotNull(tile);
        tile.setBlock(block, state.rules.defaultTeam);
        Events.fire(new BlockBuildEndEvent(tile, null, state.rules.defaultTeam, false, null));
        return tile.build;
    }

    void mine(Building building){
        building.tile.getLinkedTiles(tile -> tile.setOverlay(Blocks.oreCopper));
        building.onProximityUpdate();
        for(int i = 0; i < 600; i++) building.update();
        assertTrue(building.items.get(Items.copper) > 0, "new drill actually produces copper");
        assertTrue(((DrillBuild)building).lastDrillSpeed > 0f);
    }

    boolean nearbyClear(Tile tile, int size){
        for(int x = -size; x <= size; x++){
            for(int y = -size; y <= size; y++){
                var nearby = world.tile(tile.x + x, tile.y + y);
                if(nearby == null || nearby.build != null || nearby.block() != Blocks.air) return false;
            }
        }
        return true;
    }
}
