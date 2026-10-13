import arc.*;
import arc.files.*;
import arc.math.geom.*;
import arc.mock.*;
import arc.util.*;
import arc.struct.*;
import mindustry.entities.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.maps.planet.*;
import mindustry.net.*;
import mindustry.entities.units.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.*;

import java.util.Locale;
import static mindustry.Vars.*;

/** Real game-tick combat trial. No resources, waves, health or victory are changed by this driver. */
public class LegalWatchTrial{
    static final Fi assets = new Fi(System.getProperty("trial.assets", "trial-work/control031-assets"));
    static boolean defend, over;
    static Team winner;
    static Player pilot;
    static int built, withdraws, deposits;
    static int fuelCursor;
    static final Seq<BuildPlan> queue = new Seq<>();
    static final Seq<BuildPlan> repairPlans = new Seq<>();
    static final ObjectSet<BuildPlan> repairOrders = new ObjectSet<>();
    static final Seq<Point2> guns = new Seq<>();
    static final Seq<Point2> fuel = new Seq<>();
    static final Vec2 motion = new Vec2();
    static Fi record;
    static Fi lossRecord;
    static Fi damageRecord;
    static long started;
    static int frame;
    static float depositReady;
    static SectorPreset mission;
    static Mode mode;
    static long campaignCodeTime, mapCodeTime;
    static String actualVersion;
    static boolean sharedProfile, gateVerified, directMapQA;
    static Fi profile;
    static int turretControls;
    static int modeStarsBefore, missionStarsBefore, mainStarsBefore;
    static boolean branchRewardBefore;
    static Runnable planSetup, startupObserver, beforeStep, afterStep, finalizeTrial;
    static Point2 controlledArtillery;
    static int constructionCopperReserve;
    static int lateCyclonePlastaniumReserve = 80;

    public static void main(String[] args) throws Exception{
        defend = args.length > 0 && args[0].equals("defend");
        Fi output = new Fi(args[1]);
        mode = args.length > 2 ? Mode.valueOf(args[2]) : Mode.NORMAL;
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 1800;
        output.mkdirs();
        sharedProfile = System.getProperty("trial.profile") != null;
        directMapQA = Boolean.getBoolean("trial.directMapQA");
        if(directMapQA && !sharedProfile) throw new IllegalArgumentException("Existing actual QA profile required for direct-map continuation");
        profile = sharedProfile ? new Fi(System.getProperty("trial.profile")) : output.child("profile");
        if(!sharedProfile && profile.exists()) throw new IllegalArgumentException("A new isolated profile is required: " + output);
        new Fi("trial-work/LegalWatchTrial.java").copyTo(output.child("driver-source.java"));
        record = output.child("trial.csv");
        lossRecord = output.child("destroyed.csv");
        damageRecord = output.child("core-damage.csv");
        damageRecord.writeString("simSeconds,health,owner,bulletType,x,y\n");
        lossRecord.writeString("simSeconds,block,x,y\n");
        record.writeString("simSeconds,wallSeconds,wave,enemies,coreHealth,copper,lead,coal,placed,withdraws,deposits,gunsAlive,duoAmmo,scorchAmmo,silicon,titanium,powerStored,powerBalance\n");
        started = System.nanoTime();
        Log.useColors = false;
        Core.app = new MockApplication();
        Core.files = new MockFiles(){
            @Override public Fi internal(String path){ return assets.child(path); }
        };
        Core.graphics = new MockGraphics(){
            @Override public float getDeltaTime(){ return 1f / 60f; }
        };
        Core.audio = new MockAudio();
        Core.input = new MockInput();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(profile);
        Core.settings.setJson(JsonIO.json);
        headless = true;
        net = new Net(null);
        Vars.init();
        Vars.loadSettings();
        //A headless backend has no Control/audio client; the native local player still owns movement.
        logicVars = new GlobalVars(){
            @Override public void update(){
                var local = Vars.player;
                Vars.player = null;
                try{ super.update(); }finally{ Vars.player = local; }
            }
        };
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();
        logic = new Logic();
        netServer = new NetServer();
        content.init();
        var codeArtifact = new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        campaignCodeTime = codeArtifact.isDirectory() ? codeArtifact.child("mindustry/content/CampaignStars.class").lastModified() : codeArtifact.lastModified();
        mapCodeTime = codeArtifact.isDirectory() ? codeArtifact.child("mindustry/maps/planet/WatchMapGenerator.class").lastModified() : codeArtifact.lastModified();
        actualVersion = WatchCampaign.version;
        pilot = Player.create();
        pilot.name = "Legal acceptance trial";
        pilot.team(Team.sharded);
        Events.on(GameOverEvent.class, e -> { over = true; winner = e.winner; });
        Events.on(BlockBuildEndEvent.class, e -> { if(e.team == Team.sharded && !e.breaking) built++; });
        Events.on(BlockDestroyEvent.class, e -> {
            if(e.tile.build != null && e.tile.build.team == Team.sharded) lossRecord.writeString((frame / 60f) + "," + e.tile.block().name + "," + e.tile.x + "," + e.tile.y + "\n", true);
        });
        Events.on(BuildDamageEvent.class, e -> {
            if(e.build.block instanceof CoreBlock && e.build.team == Team.sharded){
                String owner = e.source.owner instanceof Unit u ? u.type.name : e.source.owner.getClass().getSimpleName();
                damageRecord.writeString(frame / 60f + "," + e.build.healthf() + "," + owner + "," + e.source.type.getClass().getSimpleName() + "," + e.source.x / tilesize + "," + e.source.y / tilesize + "\n", true);
            }
        });
        Events.on(WithdrawEvent.class, e -> { if(e.player == pilot) withdraws++; });
        Events.on(DepositEvent.class, e -> { if(e.player == pilot) deposits++; });
        //Direct map selection is a combat strategy test, deliberately not a claim of campaign progression.
        mission = args.length > 3 ? content.sector(args[3]) : WatchCampaign.sectors[0];
        modeStarsBefore = CampaignStars.stars(mission, mode);
        missionStarsBefore = CampaignStars.stars(mission);
        mainStarsBefore = CampaignStars.mainStars();
        branchRewardBefore = WatchCampaign.level(mission) >= 0 && CampaignStars.completedReward(WatchCampaign.level(mission));
        if(sharedProfile && !directMapQA){
            gateVerified = CampaignStars.unlocked(mission) && CampaignProgression.allowed(mission, mode) &&
                (mode == Mode.NORMAL || CampaignStars.normalComplete(mission));
            if(!gateVerified) throw new IllegalStateException("Campaign gate rejects " + mission.name + " " + mode + " in " + profile);
        }
        if(directMapQA && mode != Mode.NORMAL && !CampaignStars.normalComplete(mission))
            throw new IllegalStateException("A genuine normal completion is required before a QA challenge");
        CampaignStars.loadMission(mission, mode);
        int branchLevel = WatchCampaign.level(mission);
        if(branchLevel >= 1){
            var base = WatchMapGenerator.core(WatchCampaign.stages[branchLevel].size());
            Building node = null;
            for(int x = base.x - 5; x <= base.x; x++){
                var b = world.build(x, base.y - 7);
                if(b != null && b.block == Blocks.powerNode){ node = b; break; }
            }
            var battery = world.build(base.x + 3, base.y - 7);
            System.out.println("initial grid: node=" + node.tileX() + "," + node.tileY() + " links=" + node.power.links + " battery=" + battery.pos() + " batteryLinked=" + node.power.links.contains(battery.pos()) + " sameGraph=" + (node.power.graph == battery.power.graph));
        }
        Vars.player = pilot;
        pilot.add(); //World.load clears entity groups; register the player after loading the isolated mission.
        logic.play();
        CoreBlock.playerSpawn(state.rules.defaultTeam.core().tile, pilot);
        if(startupObserver != null) startupObserver.run();
        if(defend){
            if(planSetup == null) planDefence();
            else planSetup.run();
            for(var plan : queue){
                if(!Build.validPlaceIgnoreUnits(plan.block, Team.sharded, plan.x, plan.y, plan.rotation, true, true))
                    throw new IllegalStateException("Invalid initial blueprint: " + plan + " floor=" + plan.tile().floor().name + " block=" + plan.tile().block().name);
            }
        }
        for(frame = 0; frame < 60 * seconds && !over; frame++){
            Time.updateGlobal();
            if(defend) command();
            if(beforeStep != null) beforeStep.run();
            asyncCore.begin();
            logic.update();
            asyncCore.end();
            if(afterStep != null) afterStep.run();
            if(frame % 3600 == 0) snapshot();
            //Let the original asynchronous pathfinding thread advance, without skipping simulation ticks.
            if(frame % 60 == 0) Thread.sleep(2);
        }
        snapshot();
        if(finalizeTrial != null) finalizeTrial.run();
        String summary = "trial=" + (defend ? "legal-build" : "no-operation-control") +
            "\nselection=" + (sharedProfile && !directMapQA ? "actual-campaign-progress; gate verified before loading" : "direct-map-combat-test; campaign gate not claimed") +
            "\nprofileScope=" + (directMapQA ? "isolated-combat-qa-existing-actual-scores" : sharedProfile ? "shared-actual-progress" : "isolated-combat-qa") + "\nprofile=" + profile.absolutePath() + "\ngateVerified=" + gateVerified +
            "\nmission=" + mission.name + "\nmode=" + mode.name() + "\nfinished=" + over +
            "\nwinner=" + (winner == null ? "none" : winner.name) +
            "\nwave=" + state.wave + "\nactualSpawnWaves=" + (state.wave - 1) +
            "\nsimSeconds=" + (frame / 60f) + "\nwallSeconds=" + wallSeconds() +
            "\nplacedBuildings=" + built + "\nwithdraws=" + withdraws + "\ndeposits=" + deposits +
            "\nturretControls=" + turretControls +
            "\nfriendlyMonoSurvivors=" + Groups.unit.count(u -> u.team == Team.sharded && u.type == UnitTypes.mono) +
            "\ncoreMinimum=" + CampaignStars.minimumCoreHealth() + "\nbuildingsDestroyed=" + state.stats.buildingsDestroyed +
            "\npendingPlans=" + queue.size + (queue.any() ? "\nnextPlan=" + queue.first() : "") +
            "\nactualVersion=" + actualVersion + "\ncodeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation() +
            "\nassetsSource=" + assets.absolutePath() + "\nCampaignStars.classModifiedAtStart=" + campaignCodeTime + "\nWatchMapGenerator.classModifiedAtStart=" + mapCodeTime +
            "\nwaveTimer=" + state.rules.waveTimer + "\nobjectives=" + state.rules.objectives.all.size + "\nobjectiveFlags=" + state.rules.objectiveFlags +
            "\nmodeStarsBefore=" + modeStarsBefore + "\nmissionStarsBefore=" + missionStarsBefore + "\nmainStarsBefore=" + mainStarsBefore + "\nbranchRewardBefore=" + branchRewardBefore +
            "\nmodeStarsAfter=" + CampaignStars.stars(mission, mode) +
            "\nstarsAfter=" + CampaignStars.stars(mission) + "\nmainStarsAfter=" + CampaignStars.mainStars() +
            "\nbranchRewardAfter=" + (WatchCampaign.level(mission) >= 0 && CampaignStars.completedReward(WatchCampaign.level(mission))) +
            "\nfrozenForestUnlocked=" + (CampaignStars.unlocked(SectorPresets.frozenForest) && CampaignProgression.allowed(SectorPresets.frozenForest, Mode.NORMAL)) +
            "\nsettingsFile=" + Core.settings.getSettingsFile().absolutePath() + "\nsettingsExists=" + Core.settings.getSettingsFile().exists() +
            "\nstatus=" + CampaignStars.status() + "\n";
        output.child("result.txt").writeString(summary);
        System.out.println(summary);
        System.exit(over ? 0 : 2);
    }

    static float wallSeconds(){ return (System.nanoTime() - started) / 1e9f; }

    static void snapshot(){
        var core = Team.sharded.core();
        int gunsAlive = 0;
        float duoAmmo = 0, scorchAmmo = 0;
        for(var pos : guns){
            var gun = world.build(pos.pack());
            if(gun instanceof ItemTurretBuild turret){
                gunsAlive++;
                if(gun.block == Blocks.duo) duoAmmo += turret.totalAmmo;
                if(gun.block == Blocks.scorch) scorchAmmo += turret.totalAmmo;
            }
        }
        if(defend && queue.any() && !pilot.dead()){
            var next = queue.first();
            System.out.println("driver: added=" + pilot.isAdded() + " dead=" + pilot.dead() + " unit=" + pilot.unit() + " plans=" + pilot.unit().plans.size +
                " next=" + next + " tile=" + next.tile().block() + " valid=" + Build.validPlaceIgnoreUnits(next.block, Team.sharded, next.x, next.y, next.rotation, true, true) +
                " unlocked=" + next.block.unlockedNowHost() + " env=" + next.block.environmentBuildable() + " placeable=" + next.block.isPlaceable() + " dist=" + pilot.unit().dst(next.drawx(), next.drawy()));
        }
        var base = WatchCampaign.level(mission) >= 0 ? WatchMapGenerator.core(WatchCampaign.stages[WatchCampaign.level(mission)].size()) : null;
        var battery = base == null ? null : world.build(base.x + 3, base.y - 7);
        if(defend && (WatchCampaign.level(mission) == 2 || WatchCampaign.level(mission) >= 4)){
            var press = world.build(base.x - 4, base.y + 4);
            if(press != null && press.block == Blocks.multiPress) System.out.println("press: items=" + press.items + " water=" + press.liquids.get(Liquids.water) + " power=" + press.power.status + " efficiency=" + press.efficiency);
            for(int off : new int[]{-18, -16, -15, -13, -12, -10}){
                var bridge = world.build(base.x + off, base.y + 3);
                if(bridge instanceof mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild b) System.out.println("water bridge: x=" + b.tileX() + " link=" + b.link + " water=" + b.liquids.get(Liquids.water));
            }
        }
        if(defend && WatchCampaign.level(mission) == 5){
            for(var pos : guns){
                var b = world.build(pos.pack());
                if(b instanceof ItemTurretBuild turret && b.block != Blocks.duo && b.block != Blocks.scatter)
                    System.out.println("coast gun: " + b.block.name + " at=" + b.tileX() + "," + b.tileY() + " ammo=" + turret.totalAmmo + " health=" + b.healthf());
            }
            if(state.wave >= 16) Groups.unit.each(u -> u.team == Team.crux && u.type.naval, u ->
                System.out.println("enemy ship: " + u.type.name + " at=" + u.x / tilesize + "," + u.y / tilesize + " health=" + u.health + " shield=" + u.shield));
        }
        String line = (frame / 60f) + "," + wallSeconds() + "," + state.wave + "," + state.enemies + "," +
            (core == null ? 0 : core.healthf()) + "," + (core == null ? 0 : core.items.get(Items.copper)) + "," +
            (core == null ? 0 : core.items.get(Items.lead)) + "," + (core == null ? 0 : core.items.get(Items.coal)) + "," + built + "," + withdraws + "," + deposits + "," + gunsAlive + "," + duoAmmo + "," + scorchAmmo + "," +
            (core == null ? 0 : core.items.get(Items.silicon)) + "," + (core == null ? 0 : core.items.get(Items.titanium)) + "," +
            (battery == null ? 0 : battery.power.graph.getLastPowerStored()) + "," + (battery == null ? 0 : battery.power.graph.getPowerBalance()) + "\n";
        record.writeString(line, true);
        System.out.print(line);
    }

    static void planDefence(){
        var base = WatchMapGenerator.core(WatchCampaign.stages[WatchCampaign.level(mission)].size());
        int level = WatchCampaign.level(mission);
        if(level >= 4 || level == 3 && mode == Mode.HEROIC){
            for(int offset : new int[]{-5, 5}){
                var pos = new Point2(base.x + offset, base.y + 11);
                queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.scatter));
                guns.add(pos);
            }
        }
        if(level == 5){
            queue.add(new BuildPlan(base.x, base.y - 3, 0, Blocks.powerNode));
            queue.add(new BuildPlan(base.x, base.y + 3, 0, Blocks.powerNode));
            queue.add(new BuildPlan(base.x, base.y + 9, 0, Blocks.powerNode));
            queue.add(new BuildPlan(base.x + 5, base.y + 9, 0, Blocks.airFactory, 1));
            fuel.add(new Point2(base.x + 5, base.y + 9));
            for(var pos : new Point2[]{new Point2(base.x - 12, base.y + 30), new Point2(base.x - 12, base.y + 28)}){
                queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.hail));
                guns.add(pos);
            }
            var pos = new Point2(base.x - 14, base.y + 15);
            queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.cyclone));
            guns.add(pos);
        }
        if(mode != Mode.HEROIC || WatchCampaign.level(mission) != 0){
            queue.add(new BuildPlan(base.x - 17, base.y + 27, 0, level >= 1 ? Blocks.pneumaticDrill : Blocks.mechanicalDrill));
            if(level == 5){
                for(int y = base.y + 26; y >= base.y + 6; y -= 5){
                    queue.add(new BuildPlan(base.x - 17, y, 0, Blocks.itemBridge, new Point2(0, -4)));
                    queue.add(new BuildPlan(base.x - 17, y - 4, 0, Blocks.itemBridge));
                }
                queue.add(new BuildPlan(base.x - 17, base.y + 1, 3, Blocks.conveyor));
            }else for(int y = base.y + 26; y > base.y; y--) queue.add(new BuildPlan(base.x - 17, y, 3, Blocks.conveyor));
            for(int x = base.x - 17; x < base.x - 14; x++) queue.add(new BuildPlan(x, base.y, 0, Blocks.conveyor));
        }
        if(level >= 2){
            queue.add(new BuildPlan(base.x - 14, base.y + 27, 0, Blocks.pneumaticDrill));
            for(int x = base.x - 14; x > base.x - 17; x--) queue.add(new BuildPlan(x, base.y + 26, 2, Blocks.conveyor));
            if(level != 5) for(int x = base.x - 7; x <= base.x; x++) queue.add(new BuildPlan(x, base.y - 8, 0, Blocks.solarPanel));
            if(level < 4 && (level != 3 || mode == Mode.NORMAL)){
                queue.add(new BuildPlan(base.x + 2, base.y - 8, 0, Blocks.combustionGenerator));
                fuel.add(new Point2(base.x + 2, base.y - 8));
            }
        }
        if(level == 5){
            //A third coal source can be restored using copper even when the graphite chain is interrupted.
            queue.add(new BuildPlan(base.x - 16, base.y + 24, 0, Blocks.mechanicalDrill));
            queue.add(new BuildPlan(base.x - 14, base.y + 24, 0, Blocks.pneumaticDrill));
            for(int x = base.x - 14; x > base.x - 17; x--) queue.add(new BuildPlan(x, base.y + 23, x == base.x - 16 ? 3 : 2, Blocks.conveyor));
            queue.add(new BuildPlan(base.x - 16, base.y + 22, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x - 16, base.y + 21, 2, Blocks.conveyor));
            queue.add(new BuildPlan(base.x + 4, base.y + 3, 0, Blocks.graphitePress));
            fuel.add(new Point2(base.x + 4, base.y + 3));
            queue.add(new BuildPlan(base.x + 4, base.y + 2, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x + 4, base.y + 1, 3, Blocks.conveyor));
        }
        if(level == 3 && mode != Mode.NORMAL){
            queue.add(new BuildPlan(base.x - 27, base.y + 2, 0, Blocks.mechanicalPump));
            queue.add(new BuildPlan(base.x - 26, base.y + 1, 0, Blocks.mechanicalPump));
            queue.add(new BuildPlan(base.x - 26, base.y + 2, 1, Blocks.conduit));
            for(int x = base.x - 27; x < base.x - 18; x++) queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.conduit));
            for(int offset : new int[]{-18, -15, -12}){
                queue.add(new BuildPlan(base.x + offset, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
                queue.add(new BuildPlan(base.x + offset + 2, base.y + 3, 0, Blocks.bridgeConduit));
            }
            for(int x = base.x - 9; x < base.x - 6; x++) queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 3, 1, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 4, 0, mode == Mode.HEROIC ? Blocks.liquidRouter : Blocks.conduit));
            queue.add(new BuildPlan(base.x - 5, base.y + 4, 0, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 4, base.y + 4, 0, Blocks.steamGenerator));
            fuel.add(new Point2(base.x - 4, base.y + 4));
            queue.add(new BuildPlan(base.x + 4, base.y + 3, 0, Blocks.graphitePress));
            fuel.add(new Point2(base.x + 4, base.y + 3));
            queue.add(new BuildPlan(base.x + 4, base.y + 2, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x + 4, base.y + 1, 3, Blocks.conveyor));
            if(mode == Mode.HEROIC){
                queue.add(new BuildPlan(base.x - 6, base.y + 5, 1, Blocks.conduit));
                for(int x = base.x - 6; x < base.x + 4; x++) queue.add(new BuildPlan(x, base.y + 6, 0, Blocks.conduit));
                queue.add(new BuildPlan(base.x + 4, base.y + 6, 0, Blocks.steamGenerator));
                fuel.add(new Point2(base.x + 4, base.y + 6));
                queue.add(new BuildPlan(base.x + 8, base.y + 3, 0, Blocks.graphitePress));
                fuel.add(new Point2(base.x + 8, base.y + 3));
                queue.add(new BuildPlan(base.x + 8, base.y + 2, 3, Blocks.conveyor));
                queue.add(new BuildPlan(base.x + 8, base.y + 1, 3, Blocks.conveyor));
            }
        }
        if(level == 2 || level >= 4){
            if(level >= 4){
                queue.add(new BuildPlan(base.x, base.y - 3, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x, base.y + 3, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 5, base.y + 2, 0, Blocks.powerNode));
            }
            if(level == 5){
                queue.add(new BuildPlan(base.x - 6, base.y + 9, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 11, base.y + 11, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x - 12, base.y + 15, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 15, base.y + 13, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 19, base.y + 13, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x - 21, base.y + 13, 0, Blocks.mender));
                for(int x = base.x - 20; x <= base.x - 18; x++){
                    queue.add(new BuildPlan(x, base.y + 15, 0, Blocks.mechanicalPump));
                    queue.add(new BuildPlan(x, base.y + 14, x == base.x - 18 ? 3 : 0, Blocks.conduit));
                }
                for(int y = base.y + 13; y > base.y + 3; y--) queue.add(new BuildPlan(base.x - 18, y, 3, Blocks.conduit));
            }else{
                queue.add(new BuildPlan(base.x - 23, base.y + 2, 0, Blocks.mechanicalPump));
                if(level >= 4){
                    queue.add(new BuildPlan(base.x - 24, base.y + 2, 0, Blocks.mechanicalPump));
                    queue.add(new BuildPlan(base.x - 22, base.y + 2, 0, Blocks.mechanicalPump));
                }
                for(int x = base.x - 23; x < base.x - 18; x++) queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.conduit));
            }
            queue.add(new BuildPlan(base.x - 18, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 16, base.y + 3, 0, Blocks.bridgeConduit));
            queue.add(new BuildPlan(base.x - 15, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 13, base.y + 3, 0, Blocks.bridgeConduit));
            queue.add(new BuildPlan(base.x - 12, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 10, base.y + 3, 0, Blocks.bridgeConduit));
            for(int x = base.x - 9; x < base.x - 6; x++) queue.add(new BuildPlan(x, base.y + 3, 0,
                level >= 4 && x == base.x - 9 ? Blocks.liquidRouter : Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 3, 1, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 4, 0, level >= 4 ? Blocks.liquidRouter : Blocks.conduit));
            queue.add(new BuildPlan(base.x - 4, base.y + 4, 0, Blocks.multiPress));
            fuel.add(new Point2(base.x - 4, base.y + 4));
            queue.add(new BuildPlan(base.x - 4, base.y + 2, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x - 4, base.y + 1, 3, Blocks.conveyor));
            if(level >= 4){
                queue.add(new BuildPlan(base.x - 9, base.y + 4, 1, Blocks.conduit));
                queue.add(new BuildPlan(base.x - 9, base.y + 5, 0, Blocks.steamGenerator));
                fuel.add(new Point2(base.x - 9, base.y + 5));
                queue.add(new BuildPlan(base.x - 6, base.y + 5, 1, Blocks.conduit));
                for(int x = base.x - 6; x < base.x + 4; x++) queue.add(new BuildPlan(x, base.y + 6, 0, Blocks.conduit));
                queue.add(new BuildPlan(base.x + 4, base.y + 6, 0, Blocks.steamGenerator));
                fuel.add(new Point2(base.x + 4, base.y + 6));
            }
        }
        {
            queue.add(new BuildPlan(base.x - 11, base.y + 8, 0, Blocks.mechanicalDrill));
            for(int y = base.y + 7; y > base.y; y--) queue.add(new BuildPlan(base.x - 11, y, 3, Blocks.conveyor));
        }
        if(mode == Mode.HEROIC || level >= 1){
            int forward = 0;
            int forwardCount = level >= 4 || level == 3 && mode == Mode.HEROIC ? 4 : 6;
            for(int offset : new int[]{0, 2, -2, 4, -4, 6, -6, 8, -8, 10, -10, 12, -12}){
                int x = base.x + offset, y = base.y + 24;
                if(forward < forwardCount && Build.validPlaceIgnoreUnits(Blocks.duo, Team.sharded, x, y, 0, true, true)){
                    queue.add(new BuildPlan(x, y, 0, Blocks.duo));
                    guns.add(new Point2(x, y));
                    forward++;
                }
            }
        }
        guns.add(new Point2(base.x - 7, base.y + 20), new Point2(base.x + 7, base.y + 20));
        if(level >= 1){
            fuel.add(new Point2(base.x - 8, base.y - 7));
            for(int y = level >= 4 ? base.y + 9 : base.y - 3; y <= base.y + 21; y += 6) queue.add(new BuildPlan(base.x, y, 0, Blocks.powerNode));
            if(level == 5){
                queue.add(new BuildPlan(base.x - 6, base.y + 21, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 12, base.y + 21, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 12, base.y + 27, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 13, base.y + 29, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x - 18, base.y + 28, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x - 12, base.y + 17, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x - 5, base.y + 27, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x - 5, base.y + 28, 0, Blocks.mender));
                for(var pos : new Point2[]{new Point2(base.x - 3, base.y + 2), new Point2(base.x + 2, base.y + 2)})
                    queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.mender));
                for(int x = base.x - 18; x <= base.x - 14; x++) queue.add(new BuildPlan(x, base.y + 29, 0, Blocks.copperWall));
                for(int x = base.x - 18; x <= base.x - 16; x += 2) queue.add(new BuildPlan(x, base.y + 31, 0, Blocks.copperWallLarge));
                queue.add(new BuildPlan(base.x - 20, base.y + 27, 0, Blocks.copperWallLarge));
            }
            queue.add(new BuildPlan(base.x - 3, base.y + 22, 0, Blocks.mender));
            queue.add(new BuildPlan(base.x + (level == 2 ? 7 : 5), base.y + 22, 0, Blocks.mender));
            if(level < 4){
                queue.add(new BuildPlan(base.x, base.y + 23, 0, Blocks.arc));
                queue.add(new BuildPlan(base.x + 8, base.y + 23, 0, Blocks.arc));
            }
            if(level >= 2) queue.add(new BuildPlan(base.x + 1, base.y + 21, 0, Blocks.lancer));
            if(level == 2){
                queue.add(new BuildPlan(base.x, base.y + 22, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 11, base.y + 23, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 9, base.y + 26, 0, Blocks.titaniumWallLarge));
                queue.add(new BuildPlan(base.x + 11, base.y + 26, 0, Blocks.titaniumWallLarge));
            }
            if(level == 3 && mode == Mode.HEROIC){
                queue.add(new BuildPlan(base.x, base.y + 22, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 5, base.y + 19, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x + 10, base.y + 21, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 9, base.y + 26, 0, Blocks.titaniumWallLarge));
                queue.add(new BuildPlan(base.x + 11, base.y + 26, 0, Blocks.titaniumWallLarge));
                for(int offset : new int[]{-2, 2}){
                    var pos = new Point2(base.x + offset, base.y + 15);
                    queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.hail));
                    guns.add(pos);
                }
            }
            if(level >= 4){
                queue.add(new BuildPlan(base.x, base.y + 22, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 5, base.y + 19, 0, Blocks.powerNode));
                queue.add(new BuildPlan(base.x + 10, base.y + (level == 5 ? 17 : 21), 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 9, base.y + 26, 0, Blocks.titaniumWallLarge));
                queue.add(new BuildPlan(base.x + 11, base.y + 26, 0, Blocks.copperWallLarge));
            }
        }
        for(int offset : level >= 4 ? new int[]{} : new int[]{-6, -4, -2, 2, 4, 6}){
            var pos = new Point2(base.x + offset, base.y + 23);
            queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.scorch));
            guns.add(pos);
        }
        for(int offset : level >= 4 || level == 3 && mode == Mode.HEROIC ? new int[]{} : new int[]{-12, -8, -4, 4, 8, 12}){
            var pos = new Point2(base.x + offset, base.y + 17);
            queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.duo));
            guns.add(pos);
        }
        //Watch 1 has no air wave: the heroic plan saves its metal for ground defence and graphite ammunition.
        if((level >= 2 || (level == 0 && mode != Mode.HEROIC)) && !(level == 3 && mode == Mode.HEROIC) && level < 4){
            for(int offset : level == 3 && mode == Mode.HEROIC ? new int[]{-5, 5} : new int[]{-11, -5, 5, 11}){
                var pos = new Point2(base.x + offset, base.y + 11);
                queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.scatter));
                guns.add(pos);
            }
        }
        if(level >= 4){
            if(level != 5){
                for(int offset : new int[]{-2, 2}){
                    var pos = new Point2(base.x + offset, base.y + 15);
                    queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.hail));
                    guns.add(pos);
                }
                var cyclone = new Point2(base.x + 1, base.y + 12);
                queue.add(new BuildPlan(cyclone.x, cyclone.y, 0, Blocks.cyclone));
                guns.add(cyclone);
            }
            if(level == 5){
                //Native graphite artillery covers the southern water exit close to the core.
                for(int off : new int[]{-4, -6}){
                    var southGun = new Point2(base.x + off, base.y - 3);
                    queue.add(new BuildPlan(southGun.x, southGun.y, 0, Blocks.hail));
                    guns.add(southGun);
                }
                for(int y = base.y - 6; y <= base.y - 2; y += 2)
                    queue.add(new BuildPlan(base.x - 8, y, 0, Blocks.copperWallLarge));
            }
            var ripple = level == 5 ? new Point2(base.x - 7, base.y + 30) : new Point2(base.x - 7, base.y + 14);
            queue.add(new BuildPlan(ripple.x, ripple.y, 0, Blocks.ripple));
            guns.add(ripple);
        }
        if(mode == Mode.TACTICAL && level == 1){
            for(int x : new int[]{base.x - 3, base.x + 4}){
                queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.graphitePress));
                fuel.add(new Point2(x, base.y + 3));
                queue.add(new BuildPlan(x, base.y + 2, 3, Blocks.conveyor));
                queue.add(new BuildPlan(x, base.y + 1, 3, Blocks.conveyor));
            }
            queue.add(new BuildPlan(base.x - 14, base.y + 27, 0, Blocks.pneumaticDrill));
            queue.add(new BuildPlan(base.x - 15, base.y + 27, 3, Blocks.conveyor));
            for(int y = base.y + 26; y > base.y; y--) queue.add(new BuildPlan(base.x - 15, y, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x - 15, base.y, 0, Blocks.conveyor));
        }
        for(int offset = -2; offset <= 2; offset++){
            if(Build.validPlaceIgnoreUnits(Blocks.copperWall, Team.sharded, base.x + offset, base.y + 25, 0, true, true))
                queue.add(new BuildPlan(base.x + offset, base.y + 25, 0, Blocks.copperWall));
        }
        for(var plan : queue) repairPlans.add(plan.copy());
        if(level == 5) controlledArtillery = new Point2(base.x - 7, base.y + 30);
    }

    static void command(){
        var core = Team.sharded.core();
        if(core == null) return;
        if(pilot.dead()) return; //Player.update owns the native death delay and core respawn.
        Unit unit = pilot.unit();
        if(controlledArtillery != null){
            var b = world.build(controlledArtillery.pack());
            if(b instanceof ItemTurretBuild turret && turret.block == Blocks.ripple){
                var ship = Units.bestEnemy(Team.sharded, turret.x, turret.y, turret.range(), u -> u.type.naval, (u, x, y) -> u.dst2(core.x, core.y));
                if(ship != null && turret.hasAmmo()){
                    if(unit != turret.unit()){
                        InputHandler.unitControl(pilot, turret.unit());
                        turretControls++;
                        System.out.println("native player turret control at " + frame / 60f + " target=" + ship.type.name);
                    }
                    unit = pilot.unit();
                    var aim = Predict.intercept(turret, ship, turret.peekAmmo().speed);
                    unit.aim(aim.x, aim.y);
                    unit.controlWeapons(true);
                    return;
                }
                if(unit == turret.unit()){
                    InputHandler.unitClear(pilot);
                    System.out.println("native player turret release at " + frame / 60f);
                    return;
                }
            }
        }
        var enemy = Units.closestEnemy(Team.sharded, unit.x, unit.y, unit.range(), u -> true);
        unit.controlWeapons(enemy != null && unit.within(enemy, unit.range()));
        if(unit.isShooting) unit.aim(enemy);

        //Rebuilding a destroyed planned structure uses the same ordinary plan/resource consumption as its first construction.
        if(frame % 300 == 0 && (queue.isEmpty() || queue.allMatch(repairOrders::contains))){
            for(var plan : repairPlans){
                var tile = plan.tile();
                if(!queue.contains(q -> q.x == plan.x && q.y == plan.y) && tile.block() == Blocks.air && Build.validPlaceIgnoreUnits(plan.block, Team.sharded, plan.x, plan.y, plan.rotation, true, true) &&
                    Units.closestEnemy(Team.sharded, plan.drawx(), plan.drawy(), 240f, u -> true) == null){
                    var repair = plan.copy();
                    queue.add(repair);
                    repairOrders.add(repair);
                }
            }
        }

        if(queue.any()){
            BuildPlan next = queue.first();
            var tile = world.tile(next.x, next.y);
            if(tile.block() == next.block && tile.team() == Team.sharded){
                queue.remove(0);
                unit.clearBuilding();
            }else if(repairOrders.contains(next) && Units.closestEnemy(Team.sharded, next.drawx(), next.drawy(), 240f, u -> true) != null){
                //A repair order waits until hostile fire has left the location; it does not repeatedly place unfinished blocks into live fire.
                queue.remove(0);
                queue.add(next);
                unit.clearBuilding();
            }else if(!Build.checkNoUnitOverlap(next.block, next.x, next.y)){
                //An enemy standing on a destroyed emplacement blocks native construction; supply live guns instead of waiting on that order.
                queue.remove(0);
                queue.add(next);
                unit.clearBuilding();
            }else if(java.util.Arrays.stream(next.block.requirements).anyMatch(stack -> core.items.get(stack.item) == 0 ||
                stack.item == Items.copper && core.items.get(Items.copper) < constructionCopperReserve)){
                //While a missing construction material is being produced, supply the working production line and guns.
                if(repairOrders.contains(next)){
                    queue.remove(0);
                    queue.add(next);
                }
                unit.clearBuilding();
            }else{
                approach(unit, next.drawx(), next.drawy(), unit.type.buildRange * 0.8f);
                if(unit.plans.isEmpty()){
                    //A blueprint destroyed while under construction is re-issued as a new native player order.
                    if(next.initialized && tile.block() == Blocks.air){
                        next = new BuildPlan(next.x, next.y, next.rotation, next.block);
                        queue.set(0, next);
                        repairOrders.add(next);
                    }
                    unit.addBuild(next);
                }
                return;
            }
        }

        Building target = null;
        for(var pos : guns){
            var b = world.build(pos.pack());
            if(b instanceof ItemTurretBuild turret && turret.getAmmoFraction() < 0.12f && canSupply(b, core, unit)){ target = b; break; }
        }
        if(target == null){
            for(int index = 0; index < fuel.size; index++){
                var pos = fuel.get((fuelCursor + index) % fuel.size);
                var b = world.build(pos.pack());
                if(b != null && b.block == Blocks.airFactory && Groups.unit.count(u -> u.team == Team.sharded && u.type == UnitTypes.mono) >= 3) continue;
                var input = b != null && b.block == Blocks.airFactory ? (b.items.get(Items.silicon) < 30 ? Items.silicon : Items.lead) : Items.coal;
                int desired = b != null && b.block == Blocks.airFactory ? input == Items.silicon ? 30 : 15 : b != null && b.block == Blocks.multiPress ? 3 : 2;
                if(b != null && b.team == Team.sharded && b.block.hasItems && b.items.get(input) < desired &&
                    (core.items.get(input) > 0 || (unit.item() == input && unit.stack.amount > 0))){ target = b; break; }
            }
        }
        if(target == null){
            for(var pos : guns){
                var b = world.build(pos.pack());
                if(b instanceof ItemTurretBuild turret && turret.getAmmoFraction() < 0.4f && canSupply(b, core, unit)){ target = b; break; }
            }
        }
        if(target == null) return;
        Item ammo = target.block == Blocks.combustionGenerator || target.block == Blocks.steamGenerator || target.block == Blocks.graphitePress || target.block == Blocks.multiPress || target.block == Blocks.scorch ? Items.coal :
            target.block == Blocks.airFactory ? target.items.get(Items.silicon) < 30 ? Items.silicon : Items.lead :
            target.block == Blocks.scatter ? Items.lead : target.block == Blocks.cyclone ?
                (WatchCampaign.level(mission) == 5 ? core.items.get(Items.plastanium) > cycloneReserve() ? Items.plastanium : Items.metaglass : core.items.get(Items.plastanium) > 0 ? Items.plastanium : Items.metaglass) :
            target.block == Blocks.ripple && WatchCampaign.level(mission) == 5 && state.wave >= 16 && core.items.get(Items.plastanium) > 0 ? Items.plastanium :
            target.block == Blocks.hail || target.block == Blocks.ripple ? (core.items.get(Items.graphite) > 0 ? Items.graphite : Items.silicon) :
            WatchCampaign.level(mission) == 2 && core.items.get(Items.graphite) >= 5 ? Items.graphite :
            WatchCampaign.level(mission) >= 1 && core.items.get(Items.silicon) >= 5 ? Items.silicon :
            (mode == Mode.HEROIC || WatchCampaign.level(mission) >= 2) && core.items.get(Items.graphite) >= 5 ? Items.graphite : Items.copper;
        if(target.block == Blocks.duo && unit.stack.amount > 0 && (unit.item() == Items.graphite || unit.item() == Items.copper || unit.item() == Items.silicon)) ammo = unit.item();
        if(target.block == Blocks.duo && (WatchCampaign.level(mission) >= 4 || WatchCampaign.level(mission) == 3 && mode == Mode.HEROIC)) ammo =
            unit.stack.amount > 0 && (unit.item() == Items.copper || unit.item() == Items.silicon) ? unit.item() :
                core.items.get(Items.silicon) > 0 ? Items.silicon : Items.copper;
        if(target.block == Blocks.duo && WatchCampaign.level(mission) == 5) ammo = Items.copper;
        if((target.block == Blocks.hail || target.block == Blocks.ripple) && unit.stack.amount > 0 && (unit.item() == Items.graphite || unit.item() == Items.silicon)) ammo = unit.item();
        if(target.block == Blocks.ripple && unit.stack.amount > 0 && unit.item() == Items.plastanium) ammo = unit.item();
        if(target.block == Blocks.cyclone && unit.stack.amount > 0 && (unit.item() == Items.plastanium || unit.item() == Items.metaglass)) ammo = unit.item();
        if(unit.stack.amount > 0 && unit.item() == ammo){
            approach(unit, target.x, target.y, itemTransferRange * 0.6f);
            if(pilot.within(target, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, target);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
                for(int index = 0; index < fuel.size; index++){
                    if(fuel.get(index).pack() == target.pos()) fuelCursor = (index + 1) % fuel.size;
                }
            }
        }else if(unit.stack.amount > 0){
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, core);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
            }
        }else{
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && core.items.get(ammo) > 0){
                int available = core.items.get(ammo);
                if(WatchCampaign.level(mission) == 5 && target.block == Blocks.cyclone) available -= ammo == Items.metaglass ? 30 : cycloneReserve();
                if(available > 0) InputHandler.requestItem(pilot, core, ammo, Math.min(available, unit.type.itemCapacity));
            }
        }
    }

    static boolean canSupply(Building gun, Building core, Unit unit){
        if(gun.block == Blocks.scorch) return core.items.get(Items.coal) > 0 || (unit.item() == Items.coal && unit.stack.amount > 0);
        if(gun.block == Blocks.scatter) return core.items.get(Items.lead) > 0 || (unit.item() == Items.lead && unit.stack.amount > 0);
        if(gun.block == Blocks.cyclone) return core.items.get(Items.plastanium) > (WatchCampaign.level(mission) == 5 ? cycloneReserve() : 0) || core.items.get(Items.metaglass) > (WatchCampaign.level(mission) == 5 ? 30 : 0) ||
            (unit.stack.amount > 0 && (unit.item() == Items.plastanium || unit.item() == Items.metaglass));
        if(gun.block == Blocks.ripple && WatchCampaign.level(mission) == 5 && (core.items.get(Items.plastanium) > 0 || unit.item() == Items.plastanium && unit.stack.amount > 0)) return true;
        if(gun.block == Blocks.hail || gun.block == Blocks.ripple) return core.items.get(Items.graphite) > 0 || core.items.get(Items.silicon) > 0 ||
            (unit.stack.amount > 0 && (unit.item() == Items.graphite || unit.item() == Items.silicon));
        if(gun.block == Blocks.duo && (WatchCampaign.level(mission) >= 4 || WatchCampaign.level(mission) == 3 && mode == Mode.HEROIC)) return core.items.get(Items.copper) > 0 || core.items.get(Items.silicon) > 0 ||
            (unit.stack.amount > 0 && (unit.item() == Items.copper || unit.item() == Items.silicon));
        return core.items.get(Items.silicon) > 0 || core.items.get(Items.graphite) > 0 || core.items.get(Items.copper) > 0 ||
            (unit.stack.amount > 0 && (unit.item() == Items.silicon || unit.item() == Items.graphite || unit.item() == Items.copper));
    }

    static void approach(Unit unit, float x, float y, float within){
        motion.set(x - unit.x, y - unit.y);
        if(motion.len() <= within){ unit.moveAt(motion.setZero()); return; }
        unit.moveAt(motion.setLength(unit.speed()));
    }

    static int cycloneReserve(){ return state.wave >= 23 ? lateCyclonePlastaniumReserve : 80; }
}
