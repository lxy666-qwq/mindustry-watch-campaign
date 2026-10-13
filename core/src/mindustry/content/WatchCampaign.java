package mindustry.content;

import arc.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.graphics.g3d.*;
import mindustry.maps.generators.*;
import mindustry.maps.planet.*;
import mindustry.mod.Mods.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static mindustry.content.Blocks.*;

/** A short, self-contained campaign with fixed terrain and announced wave transitions. */
public class WatchCampaign{
    public static Planet planet;
    public static String version;
    public static SectorPreset[] sectors = new SectorPreset[10];
    public static SectorPreset[] anchors;
    public static final int[] starRequirements = {3, 10, 20, 35, 50, 65, 80, 100, 120, 145};
    private static final int[] preferredSlots = {171, 220, 125, 174, 100, 37, 66, 52, 44, 77};
    public static CampaignStarsDialog starsDialog;

    public enum Change{
        supply, flank, armor, thaw, air, snow, flood, swarm, sandstorm, calm,
        navy, lowTide, spores, salvage, fast, blackout, powerRestored, eruption, boss
    }

    public record Encounter(int wave, Change change){}
    public record Stage(int size, int waves, Encounter[] encounters){
        public int waveOf(Change change){
            for(var event : encounters) if(event.change == change) return event.wave;
            return Integer.MAX_VALUE;
        }
    }

    public static final Stage[] stages = {
        new Stage(144, 12, new Encounter[]{event(4, Change.supply), event(7, Change.flank), event(11, Change.armor)}),
        new Stage(144, 16, new Encounter[]{event(5, Change.salvage), event(8, Change.flank), event(13, Change.armor)}),
        new Stage(160, 18, new Encounter[]{event(6, Change.thaw), event(10, Change.air), event(15, Change.snow)}),
        new Stage(160, 20, new Encounter[]{event(7, Change.flood), event(12, Change.supply), event(16, Change.swarm)}),
        new Stage(176, 22, new Encounter[]{event(7, Change.sandstorm), event(11, Change.calm), event(14, Change.flank), event(20, Change.boss)}),
        new Stage(176, 24, new Encounter[]{event(8, Change.navy), event(13, Change.lowTide), event(21, Change.boss)}),
        new Stage(176, 26, new Encounter[]{event(7, Change.spores), event(12, Change.salvage), event(18, Change.fast), event(24, Change.boss)}),
        new Stage(192, 28, new Encounter[]{event(8, Change.blackout), event(12, Change.powerRestored), event(17, Change.air), event(25, Change.armor)}),
        new Stage(192, 30, new Encounter[]{event(9, Change.eruption), event(15, Change.blackout), event(19, Change.powerRestored), event(27, Change.boss)}),
        new Stage(192, 32, new Encounter[]{event(7, Change.flank), event(12, Change.air), event(17, Change.blackout), event(21, Change.powerRestored), event(26, Change.boss), event(30, Change.armor)})
    };

    private static Encounter event(int wave, Change change){
        return new Encounter(wave, change);
    }

    public static String text(String key){
        return Core.bundle.get("watch." + key);
    }

    public static String name(int level){
        return Core.bundle.get("sector.watch-" + (level + 1) + ".name");
    }

    public static void load(){
        version = Core.files.internal("watch-version.txt").readString().trim();
        planet = new Planet("watch", Planets.sun, 0.85f, 1){{
            alwaysUnlocked = true;
            visible = accessible = false; // The archived v0.2.0 keeps the original standalone campaign.
            startSector = 0;
            generator = new BlankPlanetGenerator(){
                @Override
                public int getSectorSize(Sector sector){
                    return stages[sector.id < 10 ? sector.id : 0].size;
                }

                @Override
                public void getColor(Vec3 position, Color out){
                    out.set(position.y > 0.35f ? Color.valueOf("d5e7ed") : position.y < -0.3f ? Color.valueOf("c99164") : Color.valueOf("5c9f86"));
                }

                @Override
                public Sector findLaunchCandidate(Sector destination, Sector selected){
                    if(selected != null && selected.planet == planet && selected.hasBase() && !selected.isAttacked()) return selected;
                    return planet.sectors.find(s -> s.hasBase() && !s.isAttacked());
                }
            };
            meshLoader = () -> new HexMesh(this, 5);
            iconColor = atmosphereColor = Color.valueOf("65c7ae");
            landCloudColor = Color.valueOf("9ce5cf");
            atmosphereRadIn = 0.02f;
            atmosphereRadOut = 0.25f;
            allowLaunchToNumbered = false;
            allowSectorInvasion = false;
            allowWaves = true;
            clearSectorOnLose = true;
            allowCampaignRules = false;
            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.hideSpawns = false;
                r.placeRangeCheck = false;
                r.derelictRepair = true;
                r.coreDestroyClear = true;
            };
        }};

        anchors = new SectorPreset[]{SectorPresets.groundZero, SectorPresets.crateredBattleground,
            SectorPresets.frozenForest, SectorPresets.ruinousShores, SectorPresets.tarFields,
            SectorPresets.windsweptIslands, SectorPresets.marsh, SectorPresets.atlas,
            SectorPresets.caldera, SectorPresets.origin};
        for(int i = 0; i < stages.length; i++){
            int level = i;
            var preset = new SectorPreset("watch-" + (i + 1), (LoadedMod)null);
            preset.generator = new WatchMapGenerator(preset, level);
            preset.initialize(anchors[i].planet, branchPosition(i).id, true);
            preset.description = text("stage." + (i + 1) + ".briefing");
            preset.captureWave = stages[i].waves + 1; // state.wave counts the next wave; play the final wave before capture.
            preset.difficulty = 1f + i * 0.7f;
            preset.alwaysUnlocked = false;
            preset.showHidden = true;
            preset.addStartingItems = true;
            preset.overrideLaunchDefaults = true;
            preset.isLastSector = false;
            preset.rules = rules -> configure(rules, level);
            sectors[i] = preset;
        }
        for(int i = 0; i < sectors.length; i++){
            new TechTree.TechNode(anchors[i].techNode, sectors[i], ItemStack.empty).objectives.add(new BranchStars(i));
        }
        WatchCampaignEvents.register();
        CampaignStars.register();
        Events.on(ClientLoadEvent.class, e -> {
            starsDialog = new CampaignStarsDialog();
            ui.menufrag.addButton(Core.bundle.format("stars.menu", version), mindustry.gen.Icon.star, starsDialog::show);
        });
    }

    /** Select a nearby unused tile without replacing an original preset or player base. */
    public static Sector branchPosition(int level){
        var anchor = anchors[level].sector;
        String key = "stars.branch-position." + (level + 1);
        int saved = Core.settings.getInt(key, -1);
        if(saved >= 0 && saved < anchor.planet.sectors.size){
            var sector = anchor.planet.sectors.get(saved);
            if(freeBranchTile(sector)) return sector;
        }
        var preferred = anchor.planet.sectors.get(preferredSlots[level]);
        var result = freeBranchTile(preferred) ? preferred : anchor.near().copy().sort((a, b) -> Integer.compare(a.id, b.id)).find(WatchCampaign::freeBranchTile);
        if(result == null){
            result = anchor.planet.sectors.copy().sort((a, b) -> Float.compare(a.tile.v.dst2(anchor.tile.v), b.tile.v.dst2(anchor.tile.v))).find(WatchCampaign::freeBranchTile);
        }
        if(result == null) throw new IllegalStateException("No free tile for branch " + level);
        Core.settings.put(key, result.id);
        return result;
    }

    public static boolean freeBranchTile(Sector sector){
        String infoKey = sector.planet.name + "-s-" + sector.id + "-info";
        return sector.preset == null && !saveDirectory.child("sector-" + sector.planet.name + "-" + sector.id + ".msav").exists()
            && !(Core.settings.has(infoKey) && Core.settings.getJson(infoKey, SectorInfo.class, SectorInfo::new).hasCore);
    }

    public static class BranchStars implements Objectives.Objective{
        public int branch;
        public BranchStars(int branch){ this.branch = branch; }
        public boolean complete(){ return CampaignStars.branchUnlocked(branch); }
        public String display(){ return Core.bundle.format("stars.requirement", starRequirements[branch], anchors[branch].localizedName); }
    }

    public static int level(SectorPreset preset){
        if(preset == null) return -1;
        for(int i = 0; i < sectors.length; i++) if(sectors[i] == preset) return i;
        return -1;
    }

    public static int level(){
        var preset = state.rules.sector == null ? null : state.rules.sector.preset;
        if(preset == null){
            String name = state.rules.tags.get("stars.preset");
            if(name != null) preset = content.sector(name);
        }
        return level(preset);
    }

    /** Scoped unlocks: branches never write free equipment research into the vanilla campaign. */
    public static boolean available(UnlockableContent content){
        return level() >= 0 && state.rules.researched.contains(content);
    }

    public static Seq<Block> equipment(int level){
        var blocks = Seq.with(coreShard, conveyor, junction, router, sorter, invertedSorter, overflowGate, underflowGate, itemBridge,
            mechanicalDrill, pneumaticDrill, mechanicalPump, conduit, liquidRouter, liquidJunction, bridgeConduit,
            duo, scatter, scorch, copperWall, copperWallLarge,
            graphitePress, combustionGenerator, powerNode, battery, mender, message, illuminator);
        if(level >= 1) blocks.addAll(hail, siliconSmelter, kiln, solarPanel, arc, lancer);
        if(level >= 2) blocks.addAll(salvo, wave, titaniumWall, titaniumWallLarge, titaniumConveyor, multiPress, liquidTank);
        if(level >= 3) blocks.addAll(steamGenerator, batteryLarge, container, unloader, laserDrill, waterExtractor, separator);
        if(level >= 4) blocks.addAll(swarmer, ripple, cyclone, cryofluidMixer, plastaniumCompressor, sporePress, cultivator, pyratiteMixer, blastMixer);
        if(level >= 5) blocks.addAll(tsunami, navalFactory, airFactory, groundFactory, additiveReconstructor, repairPoint);
        if(level >= 6) blocks.addAll(segment, mendProjector, coalCentrifuge, oilExtractor, thermalGenerator, differentialGenerator);
        if(level >= 7) blocks.addAll(fuse, thoriumWall, thoriumWallLarge, surgeSmelter, spectre, impactReactor, blastMixer, pyratiteMixer);
        if(level >= 8) blocks.addAll(phaseWeaver, forceProjector, surgeWall, surgeWallLarge);
        return blocks;
    }

    public static void configure(Rules rules, int level){
        Stage stage = stages[level];
        rules.planet = sectors[level].planet;
        rules.env = Planets.serpulo.defaultEnv;
        rules.attributes.clear();
        rules.attributes.add(Planets.serpulo.defaultAttributes);
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.fog = rules.staticFog = false;
        rules.onlyDepositCore = false;
        rules.hideSpawns = false;
        rules.placeRangeCheck = false;
        rules.derelictRepair = true;
        rules.coreDestroyClear = true;
        rules.waves = rules.waveTimer = true;
        rules.attackMode = false;
        rules.winWave = stage.waves + 1;
        rules.waveSpacing = 60f * (level == 0 ? 55 : level < 4 ? 65 : 75);
        rules.initialWaveSpacing = 60f * 180f;
        rules.dropZoneRadius = 8f * tilesize;
        rules.enemyCoreBuildRadius = 0f;
        rules.airUseSpawns = true;
        rules.buildSpeedMultiplier = 1.5f;
        rules.blockWhitelist = true;
        rules.bannedBlocks.clear();
        var equipment = equipment(level);
        rules.bannedBlocks.addAll(equipment);
        rules.researched.clear();
        rules.researched.addAll(equipment);
        rules.researched.addAll(Items.serpuloItems);
        rules.researched.addAll(UnitTypes.alpha, UnitTypes.mono, UnitTypes.poly, UnitTypes.dagger, UnitTypes.flare, UnitTypes.risso);
        if(level >= 5) rules.researched.addAll(UnitTypes.crawler, UnitTypes.nova, UnitTypes.retusa, UnitTypes.mace, UnitTypes.atrax,
            UnitTypes.pulsar, UnitTypes.horizon, UnitTypes.minke, UnitTypes.oxynoe);
                rules.loadout = Seq.with(new ItemStack(Items.copper, 1000), new ItemStack(Items.lead, 700), new ItemStack(Items.coal, 200), new ItemStack(Items.graphite, 200));
        if(level >= 1) rules.loadout.add(new ItemStack(Items.silicon, 300), new ItemStack(Items.metaglass, 150));
        if(level >= 2) rules.loadout.add(new ItemStack(Items.titanium, 350));
        if(level >= 4) rules.loadout.add(new ItemStack(Items.plastanium, 250));
        if(level >= 7) rules.loadout.add(new ItemStack(Items.thorium, 500), new ItemStack(Items.surgeAlloy, 150), new ItemStack(Items.phaseFabric, 150));
        rules.weather.clear();
        rules.spawns.clear();
        int main = WatchMapGenerator.primary(stage.size).pack(), side = WatchMapGenerator.flank(stage.size).pack(), sea = WatchMapGenerator.naval(stage.size).pack();
        group(rules, UnitTypes.dagger, 1, stage.waves, 1, 2 + level / 2, 4f, main);
        if(level >= 1) group(rules, UnitTypes.crawler, 3, stage.waves, 3, 2 + level / 3, 5f, main);
        if(level >= 2) group(rules, UnitTypes.mace, 5, stage.waves, 3, 1, 6f, main);
        int flankWave = Math.min(stage.waveOf(Change.flank), Math.min(stage.waveOf(Change.fast), Math.min(stage.waveOf(Change.lowTide), stage.waveOf(Change.eruption))));
        if(flankWave < Integer.MAX_VALUE){
            group(rules, stage.waveOf(Change.fast) == flankWave ? UnitTypes.crawler : UnitTypes.dagger, flankWave, stage.waves, 2, 2 + level / 3, 6f, side);
        }
        int airWave = stage.waveOf(Change.air);
        if(airWave < Integer.MAX_VALUE) group(rules, UnitTypes.flare, airWave, stage.waves, 2, 3 + level / 2, 6f, main);
        else if(level >= 3) group(rules, UnitTypes.flare, 5, stage.waves, 4, 2 + level / 3, 8f, main);
        if(level == 5){
            group(rules, UnitTypes.risso, stage.waveOf(Change.navy), stage.waves, 2, 2, 5f, sea);
            group(rules, UnitTypes.minke, 16, stage.waves, 4, 1, 8f, sea);
        }
        int swarm = stage.waveOf(Change.swarm);
        if(swarm < Integer.MAX_VALUE) group(rules, UnitTypes.crawler, swarm, swarm + 2, 1, 12, 3f, main);
        int armor = stage.waveOf(Change.armor);
        if(armor < Integer.MAX_VALUE) group(rules, level >= 7 ? UnitTypes.fortress : UnitTypes.mace, armor, Math.min(armor + 2, stage.waves), 2, 1, 8f, main);
        int boss = stage.waveOf(Change.boss);
        if(boss < Integer.MAX_VALUE){
            var guard = group(rules, level >= 7 ? UnitTypes.scepter : level == 5 ? UnitTypes.bryde : UnitTypes.fortress, boss, boss, 1, 1, Float.MAX_VALUE, level == 5 ? sea : main);
            guard.effect = StatusEffects.boss;
            if(level == 9){
                var escort = group(rules, UnitTypes.zenith, boss, boss, 1, 2, Float.MAX_VALUE, main);
                escort.effect = StatusEffects.boss;
            }
        }
    }

    private static SpawnGroup group(Rules rules, UnitType type, int begin, int end, int spacing, int amount, float scaling, int spawn){
        var group = new SpawnGroup(type);
        group.begin = begin - 1;
        group.end = end - 1;
        group.spacing = spacing;
        group.unitAmount = amount;
        group.unitScaling = scaling;
        group.max = 18;
        group.spawn = spawn;
        rules.spawns.add(group);
        return group;
    }
}
