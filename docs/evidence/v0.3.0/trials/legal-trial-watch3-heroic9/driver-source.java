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
    static final Fi assets = new Fi("Mindustry/core/assets");
    static boolean defend, over;
    static Team winner;
    static Player pilot;
    static int built, withdraws, deposits;
    static final Seq<BuildPlan> queue = new Seq<>();
    static final Seq<BuildPlan> repairPlans = new Seq<>();
    static final Seq<Point2> guns = new Seq<>();
    static final Seq<Point2> fuel = new Seq<>();
    static final Vec2 motion = new Vec2();
    static Fi record;
    static Fi lossRecord;
    static long started;
    static int frame;
    static float depositReady;
    static SectorPreset mission;
    static Mode mode;
    static long campaignCodeTime, mapCodeTime;
    static boolean sharedProfile, gateVerified;
    static Fi profile;

    public static void main(String[] args) throws Exception{
        defend = args.length > 0 && args[0].equals("defend");
        Fi output = new Fi(args[1]);
        mode = args.length > 2 ? Mode.valueOf(args[2]) : Mode.NORMAL;
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 1800;
        output.mkdirs();
        sharedProfile = System.getProperty("trial.profile") != null;
        profile = sharedProfile ? new Fi(System.getProperty("trial.profile")) : output.child("profile");
        if(!sharedProfile && profile.exists()) throw new IllegalArgumentException("A new isolated profile is required: " + output);
        new Fi("trial-work/LegalWatchTrial.java").copyTo(output.child("driver-source.java"));
        record = output.child("trial.csv");
        lossRecord = output.child("destroyed.csv");
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
        campaignCodeTime = new Fi("Mindustry/core/build/classes/java/main/mindustry/content/CampaignStars.class").lastModified();
        mapCodeTime = new Fi("Mindustry/core/build/classes/java/main/mindustry/maps/planet/WatchMapGenerator.class").lastModified();
        pilot = Player.create();
        pilot.name = "Legal acceptance trial";
        pilot.team(Team.sharded);
        Events.on(GameOverEvent.class, e -> { over = true; winner = e.winner; });
        Events.on(BlockBuildEndEvent.class, e -> { if(e.team == Team.sharded && !e.breaking) built++; });
        Events.on(BlockDestroyEvent.class, e -> {
            if(e.tile.build != null && e.tile.build.team == Team.sharded) lossRecord.writeString((frame / 60f) + "," + e.tile.block().name + "," + e.tile.x + "," + e.tile.y + "\n", true);
        });
        Events.on(WithdrawEvent.class, e -> { if(e.player == pilot) withdraws++; });
        Events.on(DepositEvent.class, e -> { if(e.player == pilot) deposits++; });
        //Direct map selection is a combat strategy test, deliberately not a claim of campaign progression.
        mission = args.length > 3 ? content.sector(args[3]) : WatchCampaign.sectors[0];
        if(sharedProfile){
            gateVerified = CampaignStars.unlocked(mission) && CampaignProgression.allowed(mission, mode) &&
                (mode == Mode.NORMAL || CampaignStars.normalComplete(mission));
            if(!gateVerified) throw new IllegalStateException("Campaign gate rejects " + mission.name + " " + mode + " in " + profile);
        }
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
        if(defend) planDefence();
        for(frame = 0; frame < 60 * seconds && !over; frame++){
            Time.updateGlobal();
            if(defend) command();
            asyncCore.begin();
            logic.update();
            asyncCore.end();
            if(frame % 3600 == 0) snapshot();
            //Let the original asynchronous pathfinding thread advance, without skipping simulation ticks.
            if(frame % 60 == 0) Thread.sleep(2);
        }
        snapshot();
        String summary = "trial=" + (defend ? "legal-build" : "no-operation-control") +
            "\nselection=" + (sharedProfile ? "actual-campaign-progress; gate verified before loading" : "direct-map-combat-test; campaign gate not claimed") +
            "\nprofileScope=" + (sharedProfile ? "shared-actual-progress" : "isolated-combat-qa") + "\nprofile=" + profile.absolutePath() + "\ngateVerified=" + gateVerified +
            "\nmission=" + mission.name + "\nmode=" + mode.name() + "\nfinished=" + over +
            "\nwinner=" + (winner == null ? "none" : winner.name) +
            "\nwave=" + state.wave + "\nactualSpawnWaves=" + (state.wave - 1) +
            "\nsimSeconds=" + (frame / 60f) + "\nwallSeconds=" + wallSeconds() +
            "\nplacedBuildings=" + built + "\nwithdraws=" + withdraws + "\ndeposits=" + deposits +
            "\ncoreMinimum=" + CampaignStars.minimumCoreHealth() + "\nbuildingsDestroyed=" + state.stats.buildingsDestroyed +
            "\npendingPlans=" + queue.size + (queue.any() ? "\nnextPlan=" + queue.first() : "") +
            "\ncodeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation() +
            "\nCampaignStars.classModifiedAtStart=" + campaignCodeTime + "\nWatchMapGenerator.classModifiedAtStart=" + mapCodeTime +
            "\nwaveTimer=" + state.rules.waveTimer + "\nobjectives=" + state.rules.objectives.all.size + "\nobjectiveFlags=" + state.rules.objectiveFlags +
            "\nstarsAfter=" + CampaignStars.stars(mission) + "\nmainStarsAfter=" + CampaignStars.mainStars() +
            "\nbranchRewardAfter=" + (WatchCampaign.level(mission) >= 0 && CampaignStars.completedReward(WatchCampaign.level(mission))) +
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
        if(defend && WatchCampaign.level(mission) == 2){
            var press = world.build(base.x - 4, base.y + 4);
            if(press != null && press.block == Blocks.multiPress) System.out.println("press: items=" + press.items + " water=" + press.liquids.get(Liquids.water) + " power=" + press.power.status + " efficiency=" + press.efficiency);
            for(int off : new int[]{-18, -16, -15, -13, -12, -10}){
                var bridge = world.build(base.x + off, base.y + 3);
                if(bridge instanceof mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild b) System.out.println("water bridge: x=" + b.tileX() + " link=" + b.link + " water=" + b.liquids.get(Liquids.water));
            }
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
        if(mode != Mode.HEROIC || WatchCampaign.level(mission) != 0){
            queue.add(new BuildPlan(base.x - 17, base.y + 27, 0, level >= 1 ? Blocks.pneumaticDrill : Blocks.mechanicalDrill));
            for(int y = base.y + 26; y > base.y; y--) queue.add(new BuildPlan(base.x - 17, y, 3, Blocks.conveyor));
            for(int x = base.x - 17; x < base.x - 14; x++) queue.add(new BuildPlan(x, base.y, 0, Blocks.conveyor));
        }
        if(level >= 2){
            queue.add(new BuildPlan(base.x - 14, base.y + 27, 0, Blocks.pneumaticDrill));
            for(int x = base.x - 14; x > base.x - 17; x--) queue.add(new BuildPlan(x, base.y + 26, 2, Blocks.conveyor));
            for(int x = base.x - 7; x <= base.x; x++) queue.add(new BuildPlan(x, base.y - 8, 0, Blocks.solarPanel));
            queue.add(new BuildPlan(base.x + 2, base.y - 8, 0, Blocks.combustionGenerator));
            fuel.add(new Point2(base.x + 2, base.y - 8));
        }
        if(level == 2){
            queue.add(new BuildPlan(base.x - 23, base.y + 2, 0, Blocks.mechanicalPump));
            for(int x = base.x - 23; x < base.x - 18; x++) queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 18, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 16, base.y + 3, 0, Blocks.bridgeConduit));
            queue.add(new BuildPlan(base.x - 15, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 13, base.y + 3, 0, Blocks.bridgeConduit));
            queue.add(new BuildPlan(base.x - 12, base.y + 3, 0, Blocks.bridgeConduit, new Point2(2, 0)));
            queue.add(new BuildPlan(base.x - 10, base.y + 3, 0, Blocks.bridgeConduit));
            for(int x = base.x - 9; x < base.x - 6; x++) queue.add(new BuildPlan(x, base.y + 3, 0, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 3, 1, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 6, base.y + 4, 0, Blocks.conduit));
            queue.add(new BuildPlan(base.x - 4, base.y + 4, 0, Blocks.multiPress));
            fuel.add(new Point2(base.x - 4, base.y + 4));
            queue.add(new BuildPlan(base.x - 4, base.y + 2, 3, Blocks.conveyor));
            queue.add(new BuildPlan(base.x - 4, base.y + 1, 3, Blocks.conveyor));
        }
        {
            queue.add(new BuildPlan(base.x - 11, base.y + 8, 0, Blocks.mechanicalDrill));
            for(int y = base.y + 7; y > base.y; y--) queue.add(new BuildPlan(base.x - 11, y, 3, Blocks.conveyor));
        }
        if(mode == Mode.HEROIC || level >= 1){
            int forward = 0;
            for(int offset : new int[]{0, 2, -2, 4, -4, 6, -6, 8, -8, 10, -10, 12, -12}){
                int x = base.x + offset, y = base.y + 24;
                if(forward < 6 && Build.validPlaceIgnoreUnits(Blocks.duo, Team.sharded, x, y, 0, true, true)){
                    queue.add(new BuildPlan(x, y, 0, Blocks.duo));
                    guns.add(new Point2(x, y));
                    forward++;
                }
            }
        }
        guns.add(new Point2(base.x - 7, base.y + 20), new Point2(base.x + 7, base.y + 20));
        if(level >= 1){
            fuel.add(new Point2(base.x - 8, base.y - 7));
            for(int y = base.y - 3; y <= base.y + 21; y += 6) queue.add(new BuildPlan(base.x, y, 0, Blocks.powerNode));
            queue.add(new BuildPlan(base.x - 3, base.y + 22, 0, Blocks.mender));
            queue.add(new BuildPlan(base.x + (level == 2 ? 7 : 5), base.y + 22, 0, Blocks.mender));
            queue.add(new BuildPlan(base.x, base.y + 23, 0, Blocks.arc));
            queue.add(new BuildPlan(base.x + 8, base.y + 23, 0, Blocks.arc));
            if(level >= 2) queue.add(new BuildPlan(base.x + 1, base.y + 21, 0, Blocks.lancer));
            if(level == 2){
                queue.add(new BuildPlan(base.x, base.y + 22, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 11, base.y + 23, 0, Blocks.mender));
                queue.add(new BuildPlan(base.x + 9, base.y + 26, 0, Blocks.titaniumWallLarge));
                queue.add(new BuildPlan(base.x + 11, base.y + 26, 0, Blocks.titaniumWallLarge));
            }
        }
        for(int offset : new int[]{-6, -4, -2, 2, 4, 6}){
            var pos = new Point2(base.x + offset, base.y + 23);
            queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.scorch));
            guns.add(pos);
        }
        for(int offset : new int[]{-12, -8, -4, 4, 8, 12}){
            var pos = new Point2(base.x + offset, base.y + 17);
            queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.duo));
            guns.add(pos);
        }
        //Watch 1 has no air wave: the heroic plan saves its metal for ground defence and graphite ammunition.
        if(level >= 2 || (level == 0 && mode != Mode.HEROIC)){
            for(int offset : new int[]{-11, -5, 5, 11}){
                var pos = new Point2(base.x + offset, base.y + 11);
                queue.add(new BuildPlan(pos.x, pos.y, 0, Blocks.scatter));
                guns.add(pos);
            }
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
            queue.add(new BuildPlan(base.x + offset, base.y + 25, 0, Blocks.copperWall));
        }
        for(var plan : queue) repairPlans.add(plan.copy());
    }

    static void command(){
        var core = Team.sharded.core();
        if(core == null) return;
        if(pilot.dead()) return; //Player.update owns the native death delay and core respawn.
        Unit unit = pilot.unit();
        var enemy = Units.closestEnemy(Team.sharded, unit.x, unit.y, unit.range(), u -> true);
        unit.controlWeapons(enemy != null && unit.within(enemy, unit.range()));
        if(unit.isShooting) unit.aim(enemy);

        //Rebuilding a destroyed planned structure uses the same ordinary plan/resource consumption as its first construction.
        if(queue.isEmpty() && frame % 300 == 0){
            for(var plan : repairPlans){
                var tile = plan.tile();
                if(tile.block() == Blocks.air && Build.validPlaceIgnoreUnits(plan.block, Team.sharded, plan.x, plan.y, plan.rotation, true, true)) queue.add(plan.copy());
            }
        }

        if(queue.any()){
            BuildPlan next = queue.first();
            var tile = world.tile(next.x, next.y);
            if(tile.block() == next.block && tile.team() == Team.sharded){
                queue.remove(0);
                unit.clearBuilding();
            }else if(!Build.checkNoUnitOverlap(next.block, next.x, next.y)){
                //An enemy standing on a destroyed emplacement blocks native construction; supply live guns instead of waiting on that order.
                queue.remove(0);
                queue.add(next);
                unit.clearBuilding();
            }else if(java.util.Arrays.stream(next.block.requirements).anyMatch(stack -> core.items.get(stack.item) == 0)){
                //While a missing construction material is being produced, supply the working production line and guns.
                unit.clearBuilding();
            }else{
                approach(unit, next.drawx(), next.drawy(), unit.type.buildRange * 0.8f);
                if(unit.plans.isEmpty()){
                    //A blueprint destroyed while under construction is re-issued as a new native player order.
                    if(next.initialized && tile.block() == Blocks.air){
                        next = new BuildPlan(next.x, next.y, next.rotation, next.block);
                        queue.set(0, next);
                    }
                    unit.addBuild(next);
                }
                return;
            }
        }

        Building target = null;
        for(var pos : fuel){
            var b = world.build(pos.pack());
            if(b != null && b.team == Team.sharded && b.items.get(Items.coal) < (b.block == Blocks.multiPress ? 3 : 2) &&
                (core.items.get(Items.coal) > 0 || (unit.item() == Items.coal && unit.stack.amount > 0))){ target = b; break; }
        }
        if(target == null){
            for(var pos : guns){
                var b = world.build(pos.pack());
                if(b instanceof ItemTurretBuild turret && turret.getAmmoFraction() < 0.4f){ target = b; break; }
            }
        }
        if(target == null) return;
        Item ammo = target.block == Blocks.combustionGenerator || target.block == Blocks.graphitePress || target.block == Blocks.multiPress || target.block == Blocks.scorch ? Items.coal :
            target.block == Blocks.scatter ? Items.lead : WatchCampaign.level(mission) == 2 && core.items.get(Items.graphite) >= 5 ? Items.graphite :
            WatchCampaign.level(mission) >= 1 && core.items.get(Items.silicon) >= 5 ? Items.silicon :
            (mode == Mode.HEROIC || WatchCampaign.level(mission) >= 2) && core.items.get(Items.graphite) >= 5 ? Items.graphite : Items.copper;
        if(target.block == Blocks.duo && unit.stack.amount > 0 && (unit.item() == Items.graphite || unit.item() == Items.copper || unit.item() == Items.silicon)) ammo = unit.item();
        if(unit.stack.amount > 0 && unit.item() == ammo){
            approach(unit, target.x, target.y, itemTransferRange * 0.6f);
            if(pilot.within(target, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, target);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
            }
        }else if(unit.stack.amount > 0){
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, core);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
            }
        }else{
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && core.items.get(ammo) > 0) InputHandler.requestItem(pilot, core, ammo, unit.type.itemCapacity);
        }
    }

    static void approach(Unit unit, float x, float y, float within){
        motion.set(x - unit.x, y - unit.y);
        if(motion.len() <= within){ unit.moveAt(motion.setZero()); return; }
        unit.moveAt(motion.setLength(unit.speed()));
    }
}
