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
import java.util.Arrays;
import java.util.ArrayDeque;
import static mindustry.Vars.*;

/** Real game-tick combat trial. No resources, waves, health or victory are changed by this driver. */
public class BiomassPowerTrial033{
    static final Fi assets = new Fi(System.getProperty("trial.assets", "D:/软件工程/Mindustry/core/assets"));
    static boolean defend, over;
    static Team winner;
    static Player pilot;
    static int built, withdraws, deposits;
    static final Seq<BuildPlan> queue = new Seq<>();
    static final Seq<BuildPlan> repairPlans = new Seq<>();
    static final Seq<BuildPlan> deconstructions = new Seq<>();
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
    static final IntSet reserved = new IntSet();
    static Item gather = Items.copper;
    static int gatherGoal = 250;
    static Point2 graphitePress;
    static Building feedingTarget;
    static Item feedingAmmo;
    static final Seq<Point2> generators = new Seq<>();
    static final Seq<Point2> coalSources = new Seq<>();
    static long campaignCodeTime, mapCodeTime, coreBlockCodeTime;
    static String runtimeWatchVersion;
    static long sourceJarTime;

    public static void main(String[] args) throws Exception{
        defend = args.length > 0 && args[0].equals("defend");
        Fi output = new Fi(args[1]);
        mode = args.length > 2 ? Mode.valueOf(args[2]) : Mode.NORMAL;
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 1800;
        output.mkdirs();
        String sharedPath = System.getProperty("trial.profile");
        boolean sharedProgression = sharedPath != null;
        Fi profile = sharedProgression ? new Fi(sharedPath) : output.child("profile");
        if(!sharedProgression && profile.exists()) throw new IllegalArgumentException("A new isolated profile is required: " + output);
        new Fi("local-work/mindustry-star-work/next-mainline033/BiomassPowerTrial033.java").copyTo(output.child("driver-source.java"));
        record = output.child("trial.csv");
        lossRecord = output.child("destroyed.csv");
        lossRecord.writeString("simSeconds,block,x,y\n");
        record.writeString("simSeconds,wallSeconds,wave,enemies,coreHealth,copper,lead,coal,placed,withdraws,deposits,gunsAlive,duoAmmo,scorchAmmo\n");
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
        runtimeWatchVersion = WatchCampaign.version;
        var artifact = new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        if(artifact.isDirectory()) throw new IllegalStateException("A fixed release jar is required");
        coreBlockCodeTime = campaignCodeTime = mapCodeTime = sourceJarTime = artifact.lastModified();
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
        if(mission != SectorPresets.biomassFacility) throw new IllegalArgumentException("This driver covers BiomassFacility only");
        boolean gateVerified = false;
        if(sharedProgression){
            if(!CampaignStars.unlocked(mission) || !CampaignProgression.allowed(mission, mode) ||
                (mode != Mode.NORMAL && !CampaignStars.normalComplete(mission))) throw new IllegalStateException("Campaign gate is closed: " + mission.name + " " + mode);
            gateVerified = true;
        }
        System.out.println("profileScope=" + (sharedProgression ? "independent-copy-of-genuine-main12-chain" : "independent-map-strategy") +
            " gateVerified=" + gateVerified + " starsBefore=" + CampaignStars.stars(mission, mode) + " mainStarsBefore=" + CampaignStars.mainStars());
        CampaignStars.loadMission(mission, mode);
        int branchLevel = WatchCampaign.level(mission);
        if(branchLevel >= 1){
            var base = WatchMapGenerator.core(WatchCampaign.stages[branchLevel].size());
            var node = world.build(base.x - 4, base.y - 7);
            var battery = world.build(base.x + 3, base.y - 7);
            System.out.println("initial grid: batteryLinked=" + node.power.links.contains(battery.pos()) + " sameGraph=" + (node.power.graph == battery.power.graph));
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
            "\nselection=" + (sharedProgression ? "campaign-progression-gates-verified" : "direct-map-combat-test; campaign gate not claimed") + "\nmission=" + mission.name + "\nmode=" + mode.name() + "\nfinished=" + over +
            "\nwinner=" + (winner == null ? "none" : winner.name) +
            "\nwave=" + state.wave + "\nactualSpawnWaves=" + (state.wave - 1) +
            "\nsimSeconds=" + (frame / 60f) + "\nwallSeconds=" + wallSeconds() +
            "\nplacedBuildings=" + built + "\nwithdraws=" + withdraws + "\ndeposits=" + deposits +
            "\ncoreMinimum=" + CampaignStars.minimumCoreHealth() + "\nbuildingsDestroyed=" + state.stats.buildingsDestroyed +
            "\npendingPlans=" + queue.size + (queue.any() ? "\nnextPlan=" + queue.first() : "") +
            "\ncodeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation() +
            "\nCampaignStars.classModifiedAtStart=" + campaignCodeTime + "\nWatchMapGenerator.classModifiedAtStart=" + mapCodeTime +
            "\nruntimeWatchVersion=" + runtimeWatchVersion + "\nCoreBlock.classModifiedAtStart=" + coreBlockCodeTime +
            "\nsourceJarModifiedAtStart=" + sourceJarTime + "\nassetsLocation=" + assets.absolutePath() +
            "\nprofileScope=" + (sharedProgression ? "shared-progression" : "independent-map-strategy") + "\ngateVerified=" + gateVerified +
            "\nstarsAfter=" + CampaignStars.stars(mission, mode) + "\nmainStarsAfter=" + CampaignStars.mainStars() + "\nbranch0RewardAfter=" + CampaignStars.completedReward(0) +
            "\nsettingsFileExists=" + Core.settings.getSettingsFile().exists() +
            "\nwaveTimer=" + state.rules.waveTimer + "\nobjectives=" + state.rules.objectives.all.size + "\nobjectiveFlags=" + state.rules.objectiveFlags +
            "\nfrozenForestOpen=" + CampaignStars.unlocked(SectorPresets.frozenForest) + "\nobjectivesCompleted=" + state.rules.objectives.all.count(o -> o.isCompleted()) + "\nstatus=" + CampaignStars.status() + "\n";
        SaveIO.write(output.child("end.msav"));
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
        if(defend && frame == 0 && queue.any() && !pilot.dead()){
            var next = queue.first();
            System.out.println("driver: added=" + pilot.isAdded() + " dead=" + pilot.dead() + " unit=" + pilot.unit() + " plans=" + pilot.unit().plans.size +
                " next=" + next + " tile=" + next.tile().block() + " valid=" + Build.validPlaceIgnoreUnits(next.block, Team.sharded, next.x, next.y, next.rotation, true, true) +
                " unlocked=" + next.block.unlockedNowHost() + " env=" + next.block.environmentBuildable() + " placeable=" + next.block.isPlaceable() + " dist=" + pilot.unit().dst(next.drawx(), next.drawy()));
        }
        String line = (frame / 60f) + "," + wallSeconds() + "," + state.wave + "," + state.enemies + "," +
            (core == null ? 0 : core.healthf()) + "," + (core == null ? 0 : core.items.get(Items.copper)) + "," +
            (core == null ? 0 : core.items.get(Items.lead)) + "," + (core == null ? 0 : core.items.get(Items.coal)) + "," + built + "," + withdraws + "," + deposits + "," + gunsAlive + "," + duoAmmo + "," + scorchAmmo + "\n";
        record.writeString(line, true);
        System.out.print(line);
        for(var pos : guns){ var b = world.build(pos.pack()); if(b != null && (b.block == Blocks.arc || b.block == Blocks.lancer)) System.out.println("real counter " + b.block.name + " pos=" + pos + " power=" + b.power.status + " graph=" + b.power.graph.getPowerBalance()); }
        System.out.println("tutorial objectives=" + state.rules.objectives.all.count(o -> o.isCompleted()) + "/" + state.rules.objectives.all.size +
            " flags=" + state.rules.objectiveFlags + " coreStats=" + state.stats.coreItemCount + " queue=" + queue.size +
            " gather=" + gather + " pending=" + (queue.any() ? queue.first() : "none"));
    }

    static void planDefence(){
        var core = Team.sharded.core();
        System.out.println("Native biomass power start: core=" + core.tileX() + "," + core.tileY() + " inventory=" + core.items + " refund=" + state.rules.deconstructRefundMultiplier);
        int[][] salvage = {{52,115}, {76,143}, {76,146}};
        Block[] expected = {Blocks.container, Blocks.airFactory, Blocks.additiveReconstructor};
        for(int i = 0; i < salvage.length; i++){
            Tile tile = world.tile(salvage[i][0], salvage[i][1]);
            if(tile.block() != expected[i] || tile.team() != Team.derelict || !Build.validBreak(Team.sharded, tile.x, tile.y)) throw new IllegalStateException("Expected native salvage unavailable: " + tile);
            deconstructions.add(new BuildPlan(tile.x, tile.y));
            System.out.println("planned native salvage " + tile.block().name + " at " + tile.x + "," + tile.y);
        }
        Events.on(BlockBuildEndEvent.class, e -> {
            if(e.breaking) System.out.println("native demolition completed at " + e.tile.x + "," + e.tile.y + " t=" + frame/60f + " coreItems=" + core.items);
        });
        mineLine(Items.copper);
        mineLine(Items.lead);
        for(int x : new int[]{132, 145}) turret(Blocks.arc, x, 65);
        turret(Blocks.lancer, 138, 64);
        for(int x : new int[]{130, 145}) turret(Blocks.scatter, x, 61);
        Seq<Point2> powered = new Seq<>();
        for(var plan : queue) if(plan.block == Blocks.arc || plan.block == Blocks.lancer) powered.add(new Point2(plan.x, plan.y));
        for(int x = 130; x < 138; x++){
            Point2 gen = turret(Blocks.combustionGenerator, x, 49);
            generators.add(gen); powered.add(gen);
        }
        for(int x = 130; x < 138; x++) powered.add(turret(Blocks.battery, x, 50));
        Seq<Point2> nodes = new Seq<>();
        nodes.add(turret(Blocks.powerNode, 133, 53));
        nodes.add(turret(Blocks.powerNode, 137, 58));
        nodes.add(turret(Blocks.powerNode, 140, 63));
        nodes.add(turret(Blocks.powerNode, 145, 64));
        for(var node : nodes){
            Seq<Point2> links = new Seq<>();
            for(var other : nodes) if(other != node && node.dst(other) <= 6f) links.add(new Point2(other.x-node.x, other.y-node.y));
            for(var target : powered) if(node.dst(target) <= 6f && links.size < 10) links.add(new Point2(target.x-node.x, target.y-node.y));
            queue.find(p -> p.block == Blocks.powerNode && p.x == node.x && p.y == node.y).config = links.toArray(Point2.class);
            System.out.println("native node plan " + node + " links=" + links);
        }
        for(int i = 0; i < 5; i++) mineLine(Items.coal, i < 2);
        graphitePress = turret(Blocks.graphitePress, 135, 48);
        outputToCore(world.tile(graphitePress.pack()), "graphite");
        for(int x : new int[]{134, 142}) turret(Blocks.scatter, x, 60);
        for(int x = 130; x <= 148; x += 2) if(free(Blocks.copperWallLarge, x, 69)) addPlan(new BuildPlan(x,69,0,Blocks.copperWallLarge));
        for(var plan : queue) if(plan.block != Blocks.conveyor) repairPlans.add(plan.copy());
        System.out.println("native planned blocks=" + queue.size + " counter costs=" + java.util.Arrays.toString(Blocks.lancer.requirements));
    }

    static boolean free(Block block, int x, int y){
        if(!Build.validPlaceIgnoreUnits(block, Team.sharded, x, y, 0, true, true)) return false;
        int low = (block.size - 1) / 2;
        for(int dx = 0; dx < block.size; dx++) for(int dy = 0; dy < block.size; dy++){
            if(reserved.contains(Point2.pack(x - low + dx, y - low + dy))) return false;
        }
        return true;
    }

    static void addPlan(BuildPlan plan){
        queue.add(plan);
        int low = (plan.block.size - 1) / 2;
        for(int dx = 0; dx < plan.block.size; dx++) for(int dy = 0; dy < plan.block.size; dy++){
            reserved.add(Point2.pack(plan.x - low + dx, plan.y - low + dy));
        }
    }

    static Point2 turret(Block block, int x, int y){
        Tile best = null;
        float distance = Float.POSITIVE_INFINITY;
        for(int dx = -5; dx <= 5; dx++) for(int dy = -5; dy <= 5; dy++){
            if(free(block, x + dx, y + dy) && dx * dx + dy * dy < distance){
                best = world.tile(x + dx, y + dy); distance = dx * dx + dy * dy;
            }
        }
        if(best == null) throw new IllegalStateException("No native-valid position for " + block + " near " + x + "," + y);
        addPlan(new BuildPlan(best.x, best.y, 0, block));
        guns.add(new Point2(best.x, best.y));
        return new Point2(best.x, best.y);
    }

    static void mineLine(Item item){
        mineLine(item, true);
    }

    static void mineLine(Item item, boolean automated){
        var core = Team.sharded.core();
        Tile best = null;
        float score = -Float.MAX_VALUE;
        for(Tile tile : world.tiles){
            if(!free(Blocks.mechanicalDrill, tile.x, tile.y)) continue;
            int ore = 0, other = 0;
            for(int dx = 0; dx < 2; dx++) for(int dy = 0; dy < 2; dy++){
                Tile part = world.tile(tile.x + dx, tile.y + dy);
                if(part.drop() == item) ore++;
                else if(part.drop() != null) other++;
            }
            float value = ore * 2f - core.dst(tile.worldx(), tile.worldy()) / tilesize;
            if(ore > other && value > score){ best = tile; score = value; }
        }
        if(best == null) throw new IllegalStateException("No drill position for " + item);
        addPlan(new BuildPlan(best.x, best.y, 0, Blocks.mechanicalDrill));
        if(item == Items.coal){
            coalSources.add(new Point2(best.x, best.y));
            System.out.println("planned actual coal drill " + best.x + "," + best.y + "; native Alpha can carry its real output if the conveyor is cut");
        }
        if(automated) outputToCore(best, item.name);
    }

    static void outputToCore(Tile best, String label){
        var core = Team.sharded.core();
        int w = world.width();
        int[] prev = new int[w * world.height()];
        Arrays.fill(prev, -2);
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for(int dx = -1; dx <= 2; dx++) for(int dy = -1; dy <= 2; dy++){
            if((dx == -1 || dx == 2) == (dy == -1 || dy == 2)) continue;
            Tile t = world.tile(best.x + dx, best.y + dy);
            if(t != null && free(Blocks.conveyor, t.x, t.y)){
                int index = t.y * w + t.x; prev[index] = -1; open.add(index);
            }
        }
        int end = -1, coreTile = -1;
        while(!open.isEmpty() && end == -1){
            int index = open.removeFirst();
            Tile t = world.tile(index % w, index / w);
            for(int direction = 0; direction < 4; direction++){
                Tile next = t.nearby(direction);
                if(next == null) continue;
                if(next.build == core){ end = index; coreTile = next.y * w + next.x; break; }
                int nextIndex = next.y * w + next.x;
                if(prev[nextIndex] == -2 && free(Blocks.conveyor, next.x, next.y)){
                    prev[nextIndex] = index; open.add(nextIndex);
                }
            }
        }
        if(end == -1) throw new IllegalStateException("No conveyor route from " + label + " to core");
        Seq<Integer> path = new Seq<>();
        for(int p = end; p >= 0; p = prev[p]) path.add(p);
        path.reverse();
        for(int i = 0; i < path.size; i++){
            int index = path.get(i), next = i + 1 < path.size ? path.get(i + 1) : coreTile;
            Tile t = world.tile(index % w, index / w), to = world.tile(next % w, next / w);
            addPlan(new BuildPlan(t.x, t.y, t.relativeTo(to), Blocks.conveyor));
        }
        System.out.println("planned " + label + " output " + best.x + "," + best.y + " with " + path.size + " native resource-to-core conveyors");
    }

    static void mine(Item item){
        var unit = pilot.unit();
        var core = Team.sharded.core();
        //Alpha can mine copper/lead; coal is supplied by the actual constructed drills.
        if(item == Items.coal){
            unit.mineTile = null;
            if(unit.stack.amount > 0){
                approach(unit, core.x, core.y, itemTransferRange * 0.6f);
                if(pilot.within(core, itemTransferRange) && frame / 60f >= depositReady){
                    InputHandler.transferInventory(pilot, core);
                    depositReady = frame / 60f + state.rules.itemDepositCooldown;
                }
                return;
            }
            Building source = null;
            for(var pos : coalSources){
                var building = world.build(pos.pack());
                if(building != null && building.block == Blocks.mechanicalDrill && building.team == Team.sharded &&
                    (source == null || building.items.get(Items.coal) > source.items.get(Items.coal))) source = building;
            }
            if(source == null){
                var pos = coalSources.first();
                queue.insert(0, new BuildPlan(pos.x, pos.y, 0, Blocks.mechanicalDrill));
                gather = null;
                return;
            }
            approach(unit, source.x, source.y, itemTransferRange * 0.6f);
            if(pilot.within(source, itemTransferRange) && source.items.get(Items.coal) >= 15){
                InputHandler.requestItem(pilot, source, Items.coal, Math.min(unit.type.itemCapacity, source.items.get(Items.coal)));
            }
            return;
        }
        if(unit.stack.amount > 0 && (unit.item() != item || unit.stack.amount >= unit.type.itemCapacity)){
            unit.mineTile = null;
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, core);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
            }
            return;
        }
        Tile closest = null;
        float distance = Float.POSITIVE_INFINITY;
        for(Tile tile : world.tiles){
            if(tile.drop() == item && unit.validMine(tile, false) && world.getDarkness(tile.x, tile.y) < 3 &&
                unit.dst2(tile.worldx(), tile.worldy()) < distance){
                closest = tile; distance = unit.dst2(tile.worldx(), tile.worldy());
            }
        }
        if(closest == null) throw new IllegalStateException("No player-mineable " + item);
        approach(unit, closest.worldx(), closest.worldy(), unit.type.mineRange * 0.5f);
        unit.mineTile = unit.validMine(closest) ? closest : null;
    }

    static void command(){
        var core = Team.sharded.core();
        if(core == null || pilot.dead()) return;
        Unit unit = pilot.unit();
        var enemy = Units.closestEnemy(Team.sharded, unit.x, unit.y, unit.range(), u -> true);
        unit.controlWeapons(enemy != null && unit.within(enemy, unit.range()));
        if(unit.isShooting) unit.aim(enemy);
        if(deconstructions.any()){
            BuildPlan next = deconstructions.first();
            if(next.tile().block() == Blocks.air){ deconstructions.remove(0); unit.clearBuilding(); return; }
            approach(unit, next.drawx(), next.drawy(), unit.type.buildRange * 0.8f);
            if(unit.plans.isEmpty()) unit.addBuild(next);
            return;
        }
        if(feedingTarget != null){
            if(feedingTarget.tile.build != feedingTarget){ feedingTarget = null; }
            else{ unit.clearBuilding(); unit.mineTile = null; feed(feedingTarget, feedingAmmo); return; }
        }
        if(gather != null){
            if(core.items.get(gather) < gatherGoal){
                unit.clearBuilding();
                if(gather == Items.graphite){
                    if(!ensureGraphitePress()) return;
                    var press = world.build(graphitePress.pack());
                    if(press.items.get(Items.coal) < 5) feed(press, Items.coal);
                    else approach(unit, core.x, core.y, itemTransferRange * 0.6f);
                }else mine(gather);
                return;
            }
            gather = null;
        }
        for(var pos : generators){
            var generator = world.build(pos.pack());
            if(generator != null && generator.block == Blocks.combustionGenerator && generator.items.get(Items.coal) < 3 && core.items.get(Items.coal) > 0){
                feedingTarget = generator; feedingAmmo = Items.coal;
                unit.clearBuilding(); unit.mineTile = null; feed(generator, Items.coal); return;
            }
        }
        for(var pos : guns){
            var building = world.build(pos.pack());
            if(building instanceof ItemTurretBuild turret && turret.getAmmoFraction() < 0.2f){
                Item ammo = building.block == Blocks.scatter ? Items.lead : building.block == Blocks.scorch ? Items.coal :
                    building.block == Blocks.hail ? Items.graphite : Items.copper;
                if(core.items.get(ammo) > 0 || (unit.stack.amount > 0 && unit.item() == ammo)){
                    feedingTarget = building; feedingAmmo = ammo;
                    unit.clearBuilding(); unit.mineTile = null; feed(building, ammo); return;
                }
            }
        }
        unit.mineTile = null;
        if(mode != Mode.HEROIC && queue.isEmpty() && frame % 300 == 0){
            for(var plan : repairPlans){
                if(plan.tile().block() == Blocks.air && Units.closestEnemy(Team.sharded, plan.drawx(), plan.drawy(), 240f, u -> true) == null && Build.validPlaceIgnoreUnits(plan.block, Team.sharded, plan.x, plan.y, plan.rotation, true, true)) queue.add(plan.copy());
            }
        }
        if(queue.any()){
            BuildPlan next = queue.first();
            if(next.tile().block() == next.block && next.tile().team() == Team.sharded){
                queue.remove(0); unit.clearBuilding(); return;
            }else{
                for(var requirement : next.block.requirements){
                    if(core.items.get(requirement.item) == 0){
                        gather = requirement.item; gatherGoal = requirement.item == Items.graphite ? 30 : 80; unit.clearBuilding(); return;
                    }
                }
                approach(unit, next.drawx(), next.drawy(), unit.type.buildRange * 0.8f);
                if(unit.plans.isEmpty()){
                    if(next.initialized && next.tile().block() == Blocks.air){
                        next = new BuildPlan(next.x, next.y, next.rotation, next.block); queue.set(0, next);
                    }
                    unit.addBuild(next);
                }
                return;
            }
        }
        if(feedingTarget != null){
            if(feedingTarget.tile.build != feedingTarget){ feedingTarget = null; }
            else{ feed(feedingTarget, feedingAmmo); return; }
        }
        Building target = null;
        Item ammo = null;
        for(var pos : guns){
            var building = world.build(pos.pack());
            if(building instanceof ItemTurretBuild turret && turret.getAmmoFraction() < 0.4f){ target = building; break; }
        }
        if(target == null){
            for(var pos : generators){
                var gen = world.build(pos.pack());
                if(gen != null && gen.items.get(Items.coal) < 8){ target = gen; ammo = Items.coal; break; }
            }
        }
        var press = mode == Mode.HEROIC ? null : world.build(graphitePress.pack());
        if(target == null && press != null && core.items.get(Items.graphite) < 180 && press.items.get(Items.coal) < 5){
            target = press; ammo = Items.coal;
        }
        if(target == null){ if(core.items.get(Items.copper) < 1500) mine(Items.copper); return; }
        if(ammo == null) ammo = target.block == Blocks.scatter ? Items.lead : target.block == Blocks.scorch ? Items.coal : target.block == Blocks.hail ? Items.graphite : core.items.get(Items.graphite) >= 30 ? Items.graphite : Items.copper;
        feedingTarget = target; feedingAmmo = ammo;
        feed(target, ammo);
    }

    static void feed(Building target, Item ammo){
        var core = Team.sharded.core();
        var unit = pilot.unit();
        if(core.items.get(ammo) == 0 && unit.stack.amount == 0){
            feedingTarget = null;
            gather = ammo; gatherGoal = ammo == Items.graphite ? 30 : 10;
            if(ammo == Items.graphite){
                feedingTarget = null;
                if(!ensureGraphitePress()) return;
                var press = world.build(graphitePress.pack());
                if(press.items.get(Items.coal) < 5) feed(press, Items.coal);
                else approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            }else mine(ammo);
            return;
        }
        if(unit.stack.amount > 0 && unit.item() == ammo){
            approach(unit, target.x, target.y, itemTransferRange * 0.6f);
            if(pilot.within(target, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, target);
                depositReady = frame / 60f + state.rules.itemDepositCooldown;
                //Finish one real delivery before selecting a different resource/recipient.
                if(feedingTarget == target) feedingTarget = null;
            }
        }else if(unit.stack.amount > 0){
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange) && frame / 60f >= depositReady){
                InputHandler.transferInventory(pilot, core); depositReady = frame / 60f + state.rules.itemDepositCooldown;
            }
        }else{
            approach(unit, core.x, core.y, itemTransferRange * 0.6f);
            if(pilot.within(core, itemTransferRange)) InputHandler.requestItem(pilot, core, ammo, unit.type.itemCapacity);
        }
    }

    static boolean ensureGraphitePress(){
        var building = world.build(graphitePress.pack());
        if(building != null && building.block == Blocks.graphitePress) return true;
        //A producer was actually destroyed by native combat; rebuild it through a real plan.
        gather = null; feedingTarget = null;
        if(Units.closestEnemy(Team.sharded, graphitePress.x * tilesize, graphitePress.y * tilesize, 240f, u -> true) == null &&
            !queue.contains(p -> p.x == graphitePress.x && p.y == graphitePress.y)){
            queue.insert(0, new BuildPlan(graphitePress.x, graphitePress.y, 0, Blocks.graphitePress));
            System.out.println("native graphite producer lost; queued real-cost rebuild at " + graphitePress);
        }
        return false;
    }

    static void approach(Unit unit, float x, float y, float within){
        motion.set(x - unit.x, y - unit.y);
        if(motion.len() <= within){ unit.moveAt(motion.setZero()); return; }
        unit.moveAt(motion.setLength(unit.speed()));
    }
}
