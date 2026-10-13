import arc.*;
import arc.files.*;
import arc.math.geom.*;
import arc.math.Mathf;
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
public class RuinousShoresTrial033{
    static final Fi assets = new Fi(System.getProperty("trial.assets", "local-work/mindustry-star-work/star033-runtime/assets"));
    static boolean defend, over;
    static Team winner;
    static Player pilot;
    static int built, withdraws, deposits;
    static final Seq<BuildPlan> queue = new Seq<>();
    static final Seq<BuildPlan> repairPlans = new Seq<>();
    static final IntSet completedPositions = new IntSet();
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
    static Point2 siliconSmelter;
    static Point2 glassKiln;
    static Point2 airFactory;
    static Point2 bandage;
    static Point2 scrapMelter, separator;
    static boolean collectingTitanium;
    static Item producedPickup;
    static Point2 pickupSource;
    static final Seq<BuildPlan> deconstructions = new Seq<>();
    static Building feedingTarget;
    static Item feedingAmmo;
    static final Seq<Point2> generators = new Seq<>();
    static final Seq<BuildPlan> coalMines = new Seq<>();
    static final ObjectMap<String, Seq<BuildPlan>> outputRoutes = new ObjectMap<>();
    static long campaignCodeTime, mapCodeTime, coreBlockCodeTime;
    static String runtimeWatchVersion; static long sourceJarTime;
    static float observedCoreMinimum=1f;

    public static void main(String[] args) throws Exception{
        defend = args.length > 0 && args[0].equals("defend");
        Fi output = new Fi(args[1]);
        mode = args.length > 2 ? Mode.valueOf(args[2]) : Mode.NORMAL;
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 1800;
        output.mkdirs();
        boolean sharedProgression = true;
        Fi profile = output.child("profile");
        if(profile.exists()) throw new IllegalArgumentException("A new isolated profile is required: " + output);
        profile.mkdirs();
        new Fi("local-work/mindustry-star-work/progression-chain/profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/mindustry-star-work/RuinousShoresTrial033.java").copyTo(output.child("driver-source.java"));
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
        runtimeWatchVersion = WatchCampaign.version; sourceJarTime = new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI())).lastModified();
        coreBlockCodeTime = 0;
        campaignCodeTime = 0;
        mapCodeTime = 0;
        pilot = Player.create();
        pilot.name = "Legal acceptance trial";
        pilot.team(Team.sharded);
        Events.on(GameOverEvent.class, e -> { over = true; winner = e.winner; });
        Events.on(BlockBuildEndEvent.class, e -> { if(e.team == Team.sharded && !e.breaking){ built++; completedPositions.add(e.tile.pos()); } });
        Events.on(BlockDestroyEvent.class, e -> {
            if(e.tile.build != null && e.tile.build.team == Team.sharded) lossRecord.writeString((frame / 60f) + "," + e.tile.block().name + "," + e.tile.x + "," + e.tile.y + "\n", true);
        });
        Events.on(BuildDamageEvent.class,e->{
            if(e.build.team==Team.sharded&&e.build.block instanceof CoreBlock){
                var owner=e.source.owner;
                output.child("core-bullet-damage.csv").writeString(frame/60f+","+e.build.healthf()+","+(owner instanceof Unit u?u.type.name:owner==null?"none":owner.getClass().getSimpleName())+","+e.source.x/tilesize+","+e.source.y/tilesize+"\n",true);
            }
        });
        Events.on(WithdrawEvent.class, e -> { if(e.player == pilot) withdraws++; });
        Events.on(DepositEvent.class, e -> { if(e.player == pilot) deposits++; });
        //Direct map selection is a combat strategy test, deliberately not a claim of campaign progression.
        mission = args.length > 3 ? content.sector(args[3]) : WatchCampaign.sectors[0];
        if(mission != SectorPresets.ruinousShores) throw new IllegalArgumentException("This driver covers RuinousShores only");
        boolean gateVerified = false;
        if(sharedProgression){
            if(!CampaignStars.unlocked(mission) || !CampaignProgression.allowed(mission, mode) ||
                (mode != Mode.NORMAL && !CampaignStars.normalComplete(mission))) throw new IllegalStateException("Campaign gate is closed: " + mission.name + " " + mode);
            gateVerified = true;
        }
        System.out.println("profileScope=" + (sharedProgression ? "shared-progression" : "independent-map-strategy") +
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
            if(CampaignStars.minimumCoreHealth()<observedCoreMinimum){
                observedCoreMinimum=CampaignStars.minimumCoreHealth();
                var c=Team.sharded.core();
                output.child("core-minimum-changes.csv").writeString(frame/60f+","+state.wave+","+observedCoreMinimum+","+(c==null?0:c.healthf())+"\n",true);
                if(c!=null)for(var hostile:Groups.unit)if(hostile.team!=Team.sharded&&hostile.within(c,tilesize*80))output.child("core-damage-nearby-enemies.csv").writeString(frame/60f+","+hostile.type.name+","+hostile.x/tilesize+","+hostile.y/tilesize+","+hostile.health+"\n",true);
            }
            if(frame % 3600 == 0) snapshot();
            //Let the original asynchronous pathfinding thread advance, without skipping simulation ticks.
            if(frame % 60 == 0) Thread.sleep(2);
        }
        snapshot();
        output.child("final-tags.json").writeString(JsonIO.write(state.rules.tags));
        output.child("final-world.json").writeString(JsonIO.write(SerpuloNativeState033.world()));
        output.child("final-core.txt").writeString(state.rules.defaultTeam.core()==null?"none":state.rules.defaultTeam.core().items.toString());
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
            "\nsourceJarModifiedAtStart=" + sourceJarTime + "\nassetsLocation=" + assets.absolutePath() + "\nruntimeWatchVersion=" + runtimeWatchVersion + "\nCoreBlock.classModifiedAtStart=" + coreBlockCodeTime +
            "\nprofileScope=isolated copy of genuine progression-chain settings; original untouched\ngateVerified=" + gateVerified +
            "\nstarsAfter=" + CampaignStars.stars(mission, mode) + "\nmainStarsAfter=" + CampaignStars.mainStars() + "\nbranch0RewardAfter=" + CampaignStars.completedReward(0) +
            "\nsettingsFileExists=" + Core.settings.getSettingsFile().exists() +
            "\nwaveTimer=" + state.rules.waveTimer + "\nobjectives=" + state.rules.objectives.all.size + "\nobjectiveFlags=" + state.rules.objectiveFlags +
            "\nfrozenForestOpen=" + CampaignStars.unlocked(SectorPresets.frozenForest) + "\nobjectivesCompleted=" + state.rules.objectives.all.count(o -> o.isCompleted()) + "\nstatus=" + CampaignStars.status() + "\n";
        output.child("result.txt").writeString(summary);
        System.out.println(summary);
        SaveIO.write(output.child(over ? "end.msav" : "partial.msav"));
        output.child("result.txt").writeString("worldSaveWritten=true\n",true);
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
        System.out.println("tutorial objectives=" + state.rules.objectives.all.count(o -> o.isCompleted()) + "/" + state.rules.objectives.all.size +
            " flags=" + state.rules.objectiveFlags + " coreStats=" + state.stats.coreItemCount + " queue=" + queue.size +
            " gather=" + gather + " pending=" + (queue.any() ? queue.first() : "none"));
        if(!pilot.dead()){
            var u=pilot.unit();
            System.out.println("driverState unit="+u.tileX()+","+u.tileY()+" carry="+u.stack+" feed="+(feedingTarget==null?"none":feedingTarget.block.name+"@"+feedingTarget.tileX()+","+feedingTarget.tileY())+" feedAmmo="+feedingAmmo+" collect="+collectingTitanium+" gather="+gather+" goal="+gatherGoal+" plans="+u.plans+" updateBuilding="+u.updateBuilding+" canBuild="+u.canBuild());
        }
        for(Point2 pos:new Point2[]{graphitePress,siliconSmelter,glassKiln,separator,scrapMelter})if(pos!=null){
            var b=world.build(pos.pack());if(b!=null)System.out.println("productionState "+b.block.name+"@"+pos+" items="+b.items+" power="+(b.power==null?"none":b.power.status+"/stored="+b.power.graph.getLastPowerStored()+"/prod="+b.power.graph.getLastPowerProduced()+"/need="+b.power.graph.getLastPowerNeeded()));
        }
        for(var pos:guns){var b=world.build(pos.pack());if(b instanceof ItemTurretBuild turret)record.parent().child("gun-ammo.csv").writeString(frame/60f+","+b.block.name+","+b.tileX()+","+b.tileY()+","+turret.totalAmmo+","+turret.getAmmoFraction()+"\n",true);}
        for(var pos:guns){var b=world.build(pos.pack());if(b instanceof mindustry.world.blocks.defense.turrets.Turret.TurretBuild turret)record.parent().child("turret-shots.csv").writeString(frame/60f+","+b.block.name+","+b.tileX()+","+b.tileY()+","+turret.totalShots+","+(b.power==null?"none":b.power.status)+","+(b.power==null?0:b.power.graph.getLastPowerStored())+"\n",true);}
        if(frame%3600==0){
            for(var enemy:Groups.unit)if(enemy.team!=Team.sharded) record.parent().child("enemies.csv").writeString(frame/60f+","+enemy.type.name+","+enemy.x/tilesize+","+enemy.y/tilesize+","+enemy.health+"\n",true);
        }
    }

    static void planDefence(){
        var core=Team.sharded.core();
        System.out.println("Native shores start core="+core.tileX()+","+core.tileY()+" inventory="+core.items);
        for(int[] pos:new int[][]{{260,211},{262,208},{266,208},{262,214},{266,214},{205,32},{198,40},{201,40},{204,40},{196,35},{196,38}})
            deconstructions.add(new BuildPlan(pos[0],pos[1]));
        for(int i=0;i<3;i++)mineLine(Items.copper);
        for(int[] pos:new int[][]{{-4,3},{0,5},{5,3},{-4,-3},{5,-3}})turret(Blocks.duo,core.tileX()+pos[0],core.tileY()+pos[1]);
        mineLine(Items.lead);mineLine(Items.lead);
        turret(Blocks.scatter,core.tileX()+17,core.tileY()+11);
        turret(Blocks.scatter,core.tileX()-12,core.tileY()+11);
        turret(Blocks.scatter,core.tileX(),core.tileY()-5);
        for(int[] pos:new int[][]{{-7,5},{0,8},{7,5},{-7,-5},{7,-5}})turret(Blocks.scatter,core.tileX()+pos[0],core.tileY()+pos[1]);
        for(int i=0;i<8;i++)mineLine(Items.coal);
        //Construct the graphite production route before adding graphite-dependent weapons.
        planRepairGrid(core);
        turret(Blocks.powerNode,core.tileX()-6,core.tileY()+8);
        turret(Blocks.powerNode,core.tileX()-6,core.tileY()+13);
        turret(Blocks.mender,core.tileX()-7,core.tileY()+15);
        graphitePress=turret(Blocks.graphitePress,core.tileX()+4,core.tileY()-2);
        //Native player carries actual production output when the island has no open belt exit.
        //Sand is hand-mined through the original Alpha mining controls.
        siliconSmelter=turret(Blocks.siliconSmelter,core.tileX()-4,core.tileY()-2);

        Point2 siNode=turret(Blocks.powerNode,core.tileX()-3,core.tileY());
        queue.find(p->p.block==Blocks.powerNode&&p.x==siNode.x&&p.y==siNode.y).config=new Point2[]{new Point2(siliconSmelter.x-siNode.x,siliconSmelter.y-siNode.y),new Point2(core.tileX()-3-siNode.x,core.tileY()+5-siNode.y)};
        glassKiln=turret(Blocks.kiln,core.tileX()-4,core.tileY()-7);
        turret(Blocks.powerNode,glassKiln.x+1,glassKiln.y+2);

        planSteam(core);planSteam(core);
        for(int[] pos:new int[][]{{-7,0},{7,0}})turret(Blocks.scorch,core.tileX()+pos[0],core.tileY()+pos[1]);

        Seq<Point2> lancers=new Seq<>();
        for(int[] offset:new int[][]{{-5,2},{24,2}}){
            Point2 p=turret(Blocks.lancer,core.tileX()+offset[0],core.tileY()+offset[1]);lancers.add(p);
            Point2 node=turret(Blocks.powerNode,p.x,p.y-2);
            Seq<Point2> links=new Seq<>();links.add(new Point2(p.x-node.x,p.y-node.y));
            queue.find(q->q.block==Blocks.powerNode&&q.x==node.x&&q.y==node.y).config=links.toArray(Point2.class);
            if(core.dst(p.x*tilesize,p.y*tilesize)>tilesize*16){
                turret(Blocks.mender,p.x+2,p.y-2);
                for(int y=-2;y<=4;y+=2)if(free(Blocks.copperWallLarge,p.x+3,p.y+y))addPlan(new BuildPlan(p.x+3,p.y+y,0,Blocks.copperWallLarge));
            }
        }
        //The native launch filter can choose either island. Alpha can legally mine scrap and carry it across water.
        planSeparator(core);

        //Titanium is carried through native request/transfer controls when the chosen island has no belt exit.

        for(int x=core.tileX()+6;x<=core.tileX()+24;x+=6)turret(Blocks.powerNode,x,core.tileY()+4);
        for(int y=core.tileY()-6;y<=core.tileY()+16;y+=6)turret(Blocks.powerNode,core.tileX()+25,y);
        for(int[] pos:new int[][]{{-5,-5},{5,-5},{-5,5},{5,5}})turret(Blocks.mender,core.tileX()+pos[0],core.tileY()+pos[1]);
        for(int[] pos:new int[][]{{-5,-10},{5,-10},{-6,10},{6,10},{-10,2},{10,2}})turret(Blocks.hail,core.tileX()+pos[0],core.tileY()+pos[1]);
        bandage=turret(WatchRewardBlocks.fieldBandage,core.tileX()+2,core.tileY()+6);
        for(int side:new int[]{-8,8})for(int y=-6;y<=6;y+=2){
            int x=core.tileX()+side,wallY=core.tileY()+y;
            if(free(Blocks.copperWallLarge,x,wallY))addPlan(new BuildPlan(x,wallY,0,Blocks.copperWallLarge));
        }
        for(int dx=-8;dx<=8;dx++)for(int dy=-8;dy<=8;dy++)if(Math.abs(dx)==8||Math.abs(dy)==8){
            int x=core.tileX()+dx,y=core.tileY()+dy;
            if(free(Blocks.copperWall,x,y))addPlan(new BuildPlan(x,y,0,Blocks.copperWall));
        }        for(int i=0;i<2;i++)mineLine(Items.copper);
        for(int[] pos:new int[][]{{-17,18},{-17,-18},{6,-15},{-23,5}})turret(Blocks.scatter,core.tileX()+pos[0],core.tileY()+pos[1]);

        var pressPlan=queue.find(p->p.block==Blocks.graphitePress);
        queue.remove(pressPlan);
        queue.insert(queue.indexOf(p->p.block==Blocks.pneumaticDrill),pressPlan);

        for(var node:queue)if(node.block==Blocks.powerNode){
            Seq<Point2> link=new Seq<>();
            for(var candidate:queue)if(candidate!=node&&candidate.block.hasPower&&candidate.block==Blocks.powerNode&&Mathf.dst(candidate.x,candidate.y,node.x,node.y)<=6)link.add(new Point2(candidate.x-node.x,candidate.y-node.y));
            for(var candidate:queue)if(candidate!=node&&candidate.block.hasPower&&candidate.block!=Blocks.powerNode&&Mathf.dst(candidate.x,candidate.y,node.x,node.y)<=6&&link.size<10)link.add(new Point2(candidate.x-node.x,candidate.y-node.y));
            node.config=link.toArray(Point2.class);
        }
        for(var plan:queue)if(plan.block instanceof mindustry.world.blocks.defense.turrets.Turret||plan.block==Blocks.copperWall||plan.block==Blocks.copperWallLarge||plan.block==WatchRewardBlocks.fieldBandage||plan.block==Blocks.mender||plan.block==Blocks.mechanicalDrill||plan.block==Blocks.pneumaticDrill||plan.block==Blocks.powerNode||plan.block==Blocks.combustionGenerator||plan.block==Blocks.steamGenerator||plan.block==Blocks.mechanicalPump||plan.block==Blocks.graphitePress||plan.block==Blocks.siliconSmelter||plan.block==Blocks.kiln||plan.block==Blocks.separator||plan.block==Blocks.melter)repairPlans.add(plan.copy());
    }

    static void planSeparator(Building core){
        Tile best=null,melter=null;float bestDistance=Float.POSITIVE_INFINITY;
        for(Tile tile:world.tiles){
            if(core.dst(tile.worldx(),tile.worldy())>tilesize*14||!free(Blocks.separator,tile.x,tile.y))continue;
            for(int[] d:new int[][]{{-1,0},{-1,1},{2,0},{2,1},{0,-1},{1,-1},{0,2},{1,2}}){
                var m=world.tile(tile.x+d[0],tile.y+d[1]);
                if(m!=null&&free(Blocks.melter,m.x,m.y)&&core.dst(tile.worldx(),tile.worldy())<bestDistance){best=tile;melter=m;bestDistance=core.dst(tile.worldx(),tile.worldy());}
            }
        }
        if(best==null)throw new IllegalStateException("No native adjacent melter/separator position");
        addPlan(new BuildPlan(best.x,best.y,0,Blocks.separator));separator=new Point2(best.x,best.y);
        addPlan(new BuildPlan(melter.x,melter.y,0,Blocks.melter));scrapMelter=new Point2(melter.x,melter.y);
        turret(Blocks.powerNode,best.x,best.y+2);
        turret(Blocks.mender,best.x+2,best.y+1);
        System.out.println("native separator="+separator+" actual adjacent melter="+scrapMelter);
    }
    static void planSteam(Building core){
        Tile best=null,pump=null;float bestDistance=Float.POSITIVE_INFINITY;
        for(Tile tile:world.tiles){
            if(core.dst(tile.worldx(),tile.worldy())>tilesize*16||!free(Blocks.steamGenerator,tile.x,tile.y))continue;
            for(int[] d:new int[][]{{-1,0},{-1,1},{2,0},{2,1},{0,-1},{1,-1},{0,2},{1,2}}){
                var p=world.tile(tile.x+d[0],tile.y+d[1]);
                if(p!=null&&p.floor().liquidDrop==Liquids.water&&free(Blocks.mechanicalPump,p.x,p.y)&&core.dst(tile.worldx(),tile.worldy())<bestDistance){best=tile;pump=p;bestDistance=core.dst(tile.worldx(),tile.worldy());}
            }
        }
        if(best==null)throw new IllegalStateException("No native steam/pump shoreline position");
        addPlan(new BuildPlan(pump.x,pump.y,0,Blocks.mechanicalPump));
        addPlan(new BuildPlan(best.x,best.y,0,Blocks.steamGenerator));
        generators.insert(0,new Point2(best.x,best.y));
        turret(Blocks.powerNode,best.x,best.y+2);
        turret(Blocks.mender,best.x+2,best.y+1);
        System.out.println("native steam="+best.x+","+best.y+" adjacent water pump="+pump.x+","+pump.y);
    }
    static void planRepairGrid(Building core){
        Seq<Point2> menders = new Seq<>(), batteries = new Seq<>(), electric = new Seq<>();
        for(int[] pos:new int[][]{{-10,8},{10,4}})electric.add(turret(Blocks.arc,core.tileX()+pos[0],core.tileY()+pos[1]));
        for(int[] pos:new int[][]{{0,2},{6,2},{2,-2},{-2,2}})menders.add(turret(Blocks.mender,core.tileX()+pos[0],core.tileY()+pos[1]));
        for(int x : new int[]{-4,4}) generators.add(turret(Blocks.combustionGenerator, core.tileX() + x, core.tileY() + 2));
        for(int x : new int[]{-4,-2,0,2,4}) batteries.add(turret(Blocks.battery, core.tileX() + x, core.tileY() + 3));
        Point2 left = turret(Blocks.powerNode, core.tileX() - 3, core.tileY() + 5);
        Point2 right = turret(Blocks.powerNode, core.tileX() + 3, core.tileY() + 5);
        for(Point2 node : new Point2[]{left, right}){
            Seq<Point2> links = new Seq<>();
            for(var target : menders) if(node.dst(target) <= 6f) links.add(new Point2(target.x-node.x,target.y-node.y));
            for(var target : electric) if(node.dst(target) <= 6f) links.add(new Point2(target.x-node.x,target.y-node.y));
            for(var target : generators) if(node.dst(target) <= 6f) links.add(new Point2(target.x-node.x,target.y-node.y));
            for(var target : batteries) if(node.dst(target) <= 6f) links.add(new Point2(target.x-node.x,target.y-node.y));
            var other = node == left ? right : left;
            if(node.dst(other) <= 6f) links.add(new Point2(other.x-node.x,other.y-node.y));
            queue.find(p -> p.block == Blocks.powerNode && p.x == node.x && p.y == node.y).config = links.toArray(Point2.class);
        }
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
        var core = Team.sharded.core();
        Block drillBlock=item==Items.coal&&coalMines.size<4?Blocks.pneumaticDrill:Blocks.mechanicalDrill;
        Tile best = null;
        float score = -Float.MAX_VALUE;
        for(Tile tile : world.tiles){
            if(!free(drillBlock, tile.x, tile.y)) continue;
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
        if(item==Items.coal&&coalMines.size>=6&&core.dst(best.worldx(),best.worldy())>tilesize*35){System.out.println("No extra remote coal line selected at "+best.x+","+best.y);return;}
        BuildPlan drill=new BuildPlan(best.x,best.y,0,drillBlock);
        addPlan(drill);if(item==Items.coal)coalMines.add(drill.copy());
        outputToCore(best, item.name);
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
                var existing = outputRoutes.get(label);
                BuildPlan join = existing == null ? null : existing.find(p -> p.x == next.x && p.y == next.y);
                if(join != null && join.rotation != (direction + 2) % 4){ end = index; coreTile = next.y * w + next.x; break; }
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
        IntMap<BuildPlan> routePlans = new IntMap<>();
        for(int i = 0; i < path.size;){
            int index = path.get(i), next = i + 1 < path.size ? path.get(i + 1) : coreTile;
            Tile t = world.tile(index % w, index / w), to = world.tile(next % w, next / w);
            int direction = t.relativeTo(to), j = i;
            if(false && i > 0 && i < path.size - 2){
                while(j + 1 < path.size - 1 && j - i < 4){
                    int nextIndex = path.get(j + 1);
                    Tile nextTile = world.tile(nextIndex % w,nextIndex / w);
                    if(t.absoluteRelativeTo(nextTile.x, nextTile.y) != direction || (t.x != nextTile.x && t.y != nextTile.y)) break;
                    j++;
                }
            }
            if(j-i >= 2){
                int endIndex=path.get(j);
                Tile endTile=world.tile(endIndex%w,endIndex/w);
                BuildPlan startPlan=routePlans.get(index);
                if(startPlan==null){ startPlan=new BuildPlan(t.x,t.y,0,Blocks.itemBridge); addPlan(startPlan); routePlans.put(index,startPlan); }
                startPlan.config=new Point2(endTile.x-t.x,endTile.y-t.y);
                if(routePlans.get(endIndex)==null){
                    BuildPlan landing=new BuildPlan(endTile.x,endTile.y,0,Blocks.itemBridge);
                    addPlan(landing); routePlans.put(endIndex,landing);
                }
                i=j;
            }else{
                if(routePlans.get(index)==null){
                    BuildPlan belt=new BuildPlan(t.x,t.y,direction,Blocks.conveyor);
                    addPlan(belt); routePlans.put(index,belt); outputRoutes.get(label,Seq::new).add(belt);
                }
                i++;
            }
        }
        int bridgeEndpoints = 0;
        for(var plan : routePlans.values()) if(plan.block == Blocks.itemBridge) bridgeEndpoints++;
        System.out.println("planned " + label + " output " + best.x + "," + best.y + " pathLength=" + path.size +
            " bridgeEndpoints=" + bridgeEndpoints);
    }

    static void mine(Item item){
        var unit = pilot.unit();
        var core = Team.sharded.core();
        //Alpha can mine copper/lead; coal is supplied by the actual constructed drills.
        if(item == Items.coal){
            unit.mineTile=null;
            Building source=null;float distance=Float.POSITIVE_INFINITY;
            for(var plan:coalMines){var b=world.build(plan.x,plan.y);
                if(b!=null&&(b.block==Blocks.mechanicalDrill||b.block==Blocks.pneumaticDrill)&&b.items.get(Items.coal)>0&&unit.dst2(b)<distance){source=b;distance=unit.dst2(b);}
            }
            if(source!=null){producedPickup=Items.coal;pickupSource=new Point2(source.tileX(),source.tileY());collectingTitanium=true;collectTitanium();}
            else approach(unit,core.x,core.y+tilesize*6f,tilesize);
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
        if(collectingTitanium){collectTitanium();return;}
        if(deconstructions.any()){
            BuildPlan next=deconstructions.first();
            if(next.tile().block()==Blocks.air){deconstructions.remove(0);unit.clearBuilding();return;}
            if(!Build.validBreak(Team.sharded,next.x,next.y))throw new IllegalStateException("Illegal salvage target "+next);
            unit.mineTile=null;approach(unit,next.x*tilesize,next.y*tilesize,unit.type.buildRange*.8f);
            if(unit.plans.isEmpty())unit.addBuild(next);
            return;
        }
        if(frame%300==0){
            boolean rebuilding=false;
            for(var plan:repairPlans){
                boolean vital=plan.block==Blocks.mender||plan.block==Blocks.powerNode||plan.block==Blocks.steamGenerator||plan.block==Blocks.mechanicalPump||plan.block==Blocks.graphitePress||plan.block==Blocks.siliconSmelter||plan.block==Blocks.kiln||plan.block==Blocks.separator||plan.block==Blocks.melter;
                if(vital&&completedPositions.contains(Point2.pack(plan.x,plan.y))&&plan.tile().block()==Blocks.air&&
                    !queue.contains(p->p.x==plan.x&&p.y==plan.y)&&
                    Units.closestEnemy(Team.sharded,plan.drawx(),plan.drawy(),160f,u->true)==null&&
                    Build.validPlaceIgnoreUnits(plan.block,Team.sharded,plan.x,plan.y,plan.rotation,true,true)){
                    queue.insert(0,plan.copy());rebuilding=true;
                }
            }
            if(rebuilding){gather=null;feedingTarget=null;unit.clearBuilding();}
        }
        if(frame%300==0&&core.items.get(Items.coal)<20){
            Seq<BuildPlan> supply=new Seq<>();supply.addAll(coalMines);supply.addAll(outputRoutes.get("coal"));
            for(var plan:supply)if(plan.tile().block()==Blocks.air&&!queue.contains(p->p.x==plan.x&&p.y==plan.y)&&Build.validPlaceIgnoreUnits(plan.block,Team.sharded,plan.x,plan.y,plan.rotation,true,true)){
                queue.insert(0,plan.copy());gather=null;feedingTarget=null;unit.clearBuilding();
            }
        }        var hostileAir=Units.closestEnemy(Team.sharded,core.x,core.y,tilesize*65,u->u.isFlying());
        if(hostileAir!=null&&(feedingTarget==null||feedingTarget.block!=Blocks.scatter)){
            Building aa=null;float closest=Float.POSITIVE_INFINITY;
            for(var pos:guns){var b=world.build(pos.pack());if(b instanceof ItemTurretBuild turret&&b.block==Blocks.scatter&&turret.getAmmoFraction()<.6f&&b.dst(hostileAir)<closest){aa=b;closest=b.dst(hostileAir);}}
            if(aa!=null){
                unit.clearBuilding();unit.mineTile=null;
                Item aaAmmo=scatterAmmo(core);
                if(core.items.get(aaAmmo)>0||(unit.stack.amount>0&&unit.item()==aaAmmo)){feedingTarget=aa;feedingAmmo=aaAmmo;feed(aa,aaAmmo);return;}
                gather=Items.lead;gatherGoal=80;feedingTarget=null;
            }
        }        if(feedingTarget!=null&&feedingTarget.block==Blocks.scorch&&core.items.get(Items.coal)<400)feedingTarget=null;
        if(feedingTarget != null && feedingAmmo == Items.coal && core.items.get(Items.coal) == 0 && unit.stack.amount == 0){
            feedingTarget = null;
        }
        if(feedingTarget != null){
            if(feedingTarget.tile.build != feedingTarget){ feedingTarget = null; }
            else{ unit.clearBuilding(); unit.mineTile = null; feed(feedingTarget, feedingAmmo); return; }
        }
        if(frame % 300 == 0){
            for(var plan : outputRoutes.get("coal")){
                if(plan.tile().block() == Blocks.air && core.dst(plan.drawx(), plan.drawy()) <= tilesize * 12 &&
                    Units.closestEnemy(Team.sharded, plan.drawx(), plan.drawy(), 240f, u -> true) == null &&
                    !queue.contains(p -> p.x == plan.x && p.y == plan.y) &&
                    Build.validPlaceIgnoreUnits(plan.block, Team.sharded, plan.x, plan.y, plan.rotation, true, true)){
                    queue.insert(0, plan.copy()); gather = null;
                }
            }
        }
        if(gather != null && state.enemies > 0){
            for(var pos : guns){
                var building = world.build(pos.pack());
                if(building instanceof ItemTurretBuild turret && (building.block!=Blocks.scorch||core.items.get(Items.coal)>=400) &&
                    turret.getAmmoFraction() < (building.block==Blocks.scatter ? .8f : .2f)){
                    Item ammo = building.block == Blocks.scatter ? scatterAmmo(core) : building.block == Blocks.scorch ? Items.coal : (building.block == Blocks.hail || building.block == Blocks.ripple) ? Items.graphite : Items.copper;
                    if(core.items.get(ammo) > 0){
                        feedingTarget = building; feedingAmmo = ammo;
                        unit.clearBuilding(); unit.mineTile = null; feed(building, ammo); return;
                    }
                }
            }
        }

        if(gather != null){
            for(var pos:generators){
                var gen=world.build(pos.pack());
                if(gen!=null&&(gen.block==Blocks.combustionGenerator||gen.block==Blocks.steamGenerator)&&needsGeneratorFuel(gen,pos)&&gen.items.get(Items.coal)<3&&core.items.get(Items.coal)>0){feedingTarget=gen;feedingAmmo=Items.coal;unit.clearBuilding();unit.mineTile=null;feed(gen,Items.coal);return;}
            }
            if(core.items.get(gather) < gatherGoal){
                unit.clearBuilding();
                if(gather == Items.graphite){
                    if(!ensureGraphitePress()) return;
                    var press = world.build(graphitePress.pack());
                    if(press.items.get(Items.graphite)>0){producedPickup=Items.graphite;pickupSource=graphitePress;collectingTitanium=true;collectTitanium();return;}
                    if(press.items.get(Items.coal) < 5) feed(press, Items.coal);
                    else approach(unit, core.x, core.y, itemTransferRange * 0.6f);
                }else if(gather==Items.silicon){
                    if(!ensureSiliconSmelter())return;
                    var smelter=world.build(siliconSmelter.pack());
                    if(smelter.items.get(Items.silicon)>0){producedPickup=Items.silicon;pickupSource=siliconSmelter;collectingTitanium=true;collectTitanium();return;}
                    if(smelter.items.get(Items.coal)<3)feed(smelter,Items.coal);
                    else if(smelter.items.get(Items.sand)<6)feed(smelter,Items.sand);
                    else approach(unit,core.x,core.y,itemTransferRange*.6f);
                }else if(gather==Items.metaglass){
                    var kiln=world.build(glassKiln.pack());
                    if(kiln==null||kiln.block!=Blocks.kiln){
                        gather=null;unit.clearBuilding();
                        if(!queue.contains(p->p.x==glassKiln.x&&p.y==glassKiln.y))queue.insert(0,new BuildPlan(glassKiln.x,glassKiln.y,0,Blocks.kiln));
                        return;
                    }
                    if(kiln.items.get(Items.metaglass)>0){producedPickup=Items.metaglass;pickupSource=glassKiln;collectingTitanium=true;collectTitanium();return;}
                    if(kiln.items.get(Items.lead)<3)feed(kiln,Items.lead);
                    else if(kiln.items.get(Items.sand)<3)feed(kiln,Items.sand);
                    else approach(unit,core.x,core.y,itemTransferRange*.6f);
                }else if(gather==Items.titanium){
                    var sep=world.build(separator.pack());
                    if(sep!=null&&sep.block==Blocks.separator&&sep.items.total()>0){producedPickup=separatorPickup(sep);pickupSource=separator;collectingTitanium=true;collectTitanium();return;}
                    var melter=world.build(scrapMelter.pack());
                    if(melter==null||melter.block!=Blocks.melter)throw new IllegalStateException("Titanium production chain not available for rebuild");
                    if(melter.items.get(Items.scrap)<5)feed(melter,Items.scrap);
                    else approach(unit,core.x,core.y,itemTransferRange*.6f);
                }else mine(gather);
                return;
            }
            gather = null;
        }
        for(var pos : generators){
            var gen = world.build(pos.pack());
            if(gen != null && (gen.block == Blocks.combustionGenerator || gen.block == Blocks.steamGenerator) && needsGeneratorFuel(gen, pos) && gen.items.get(Items.coal) < 3 && core.items.get(Items.coal) > 0){
                feedingTarget = gen; feedingAmmo = Items.coal;
                unit.clearBuilding(); unit.mineTile = null; feed(gen, Items.coal); return;
            }
        }
        var bandageBuild=world.build(bandage.pack());
        if(bandageBuild!=null&&bandageBuild.block==WatchRewardBlocks.fieldBandage&&bandageBuild.items.get(Items.graphite)<4&&core.items.get(Items.graphite)>=4){
            feedingTarget=bandageBuild;feedingAmmo=Items.graphite;unit.clearBuilding();unit.mineTile=null;feed(bandageBuild,Items.graphite);return;
        }
        for(var pos : guns){
            var building = world.build(pos.pack());
            if(building instanceof ItemTurretBuild turret && (building.block!=Blocks.scorch||core.items.get(Items.coal)>=400) && turret.getAmmoFraction() < (building.block==Blocks.scatter ? .8f : .2f)){
                Item ammo = building.block == Blocks.scatter ? scatterAmmo(core) : building.block == Blocks.scorch ? Items.coal :
                    (building.block == Blocks.hail || building.block == Blocks.ripple) ? Items.graphite : Items.copper;
                if(core.items.get(ammo) > 0 || (unit.stack.amount > 0 && unit.item() == ammo)){
                    feedingTarget = building; feedingAmmo = ammo;
                    unit.clearBuilding(); unit.mineTile = null; feed(building, ammo); return;
                }
            }
        }
        unit.mineTile = null;
        if(queue.isEmpty() && frame % 300 == 0){
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
            if(building instanceof ItemTurretBuild turret && (building.block!=Blocks.scorch||core.items.get(Items.coal)>=400) && turret.getAmmoFraction() < 0.4f){ target = building; break; }
        }
        if(target == null){
            for(var pos : generators){
                var gen = world.build(pos.pack());
                if(gen != null && (gen.block == Blocks.combustionGenerator || gen.block == Blocks.steamGenerator) && needsGeneratorFuel(gen, pos) && gen.items.get(Items.coal) < 8){ target = gen; ammo = Items.coal; break; }
            }
        }
        var press = world.build(graphitePress.pack());
        if(press!=null&&press.block!=Blocks.graphitePress)press=null;
        if(target==null&&press!=null&&press.items.get(Items.graphite)>=5&&core.items.get(Items.graphite)<300){producedPickup=Items.graphite;pickupSource=graphitePress;collectingTitanium=true;collectTitanium();return;}
        if(target == null && press != null && core.items.get(Items.graphite) < 300 && press.items.get(Items.coal) < 5){
            target = press; ammo = Items.coal;
        }
        var smelter=world.build(siliconSmelter.pack());
        if(smelter!=null&&smelter.block!=Blocks.siliconSmelter)smelter=null;
        if(target==null&&smelter!=null&&smelter.items.get(Items.silicon)>=5&&core.items.get(Items.silicon)<300){producedPickup=Items.silicon;pickupSource=siliconSmelter;collectingTitanium=true;collectTitanium();return;}
        if(target==null&&smelter!=null&&core.items.get(Items.silicon)<300){
            if(smelter.items.get(Items.coal)<3){target=smelter;ammo=Items.coal;}
            else if(smelter.items.get(Items.sand)<6){target=smelter;ammo=Items.sand;}
        }
        var melter=world.build(scrapMelter.pack());
        var sep=world.build(separator.pack());
        if(melter!=null&&melter.block!=Blocks.melter)melter=null;
        if(sep!=null&&sep.block!=Blocks.separator)sep=null;
        if(target==null&&sep!=null&&sep.items.total()>=8&&core.items.get(Items.titanium)<180){producedPickup=separatorPickup(sep);pickupSource=separator;collectingTitanium=true;collectTitanium();return;}
        if(target==null&&melter!=null&&core.items.get(Items.titanium)<250&&melter.items.get(Items.scrap)<5){target=melter;ammo=Items.scrap;}
        var emergencyRepair=world.build(bandage.pack());
        if(target==null&&emergencyRepair!=null&&emergencyRepair.block==WatchRewardBlocks.fieldBandage&&emergencyRepair.items.get(Items.graphite)<4){target=emergencyRepair;ammo=Items.graphite;}
        if(target == null){ if(core.items.get(Items.copper) < 1500) mine(Items.copper); return; }
        if(ammo == null) ammo = target.block == Blocks.scatter ? scatterAmmo(core) : target.block == Blocks.scorch ? Items.coal : (target.block == Blocks.hail || target.block == Blocks.ripple) ? Items.graphite : core.items.get(Items.graphite) >= 30 ? Items.graphite : Items.copper;
        feedingTarget = target; feedingAmmo = ammo;
        feed(target, ammo);
    }

    static boolean serviceFactory(Unit unit,Building core){
        var factory=world.build(airFactory.pack());
        if(factory==null||factory.block!=Blocks.airFactory||Groups.unit.count(u->u.team==Team.sharded&&u.type==UnitTypes.mono)>=4)return false;
        for(Item item:new Item[]{Items.silicon,Items.lead})if(factory.items.get(item)<(item==Items.silicon?30:15)){
            unit.clearBuilding();unit.mineTile=null;
            if(core.items.get(item)==0&&(unit.stack.amount==0||unit.item()!=item)){gather=item;gatherGoal=80;return false;}
            feedingTarget=factory;feedingAmmo=item;feed(factory,item);return true;
        }
        return false;
    }
    static Item scatterAmmo(Building core){return core.items.get(Items.metaglass)>20?Items.metaglass:Items.lead;}
    static boolean needsGeneratorFuel(Building gen, Point2 pos){
        if(gen.block==Blocks.combustionGenerator&&gen.power.graph.getLastPowerStored()>gen.power.graph.getLastCapacity()*.25f&&generators.contains(p->{var b=world.build(p.pack());return b!=null&&b.block==Blocks.steamGenerator;}))return false;
        return pos == generators.first() || state.enemies > 0 ||
            gen.power.graph.getLastPowerStored() < gen.power.graph.getLastCapacity() * 0.75f;
    }

    static void feed(Building target, Item ammo){
        var core = Team.sharded.core();
        var unit = pilot.unit();
        if(core.items.get(ammo) == 0 && unit.stack.amount == 0){
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
        gather = null; feedingTarget = null;
        if(Units.closestEnemy(Team.sharded, graphitePress.x * tilesize, graphitePress.y * tilesize, 240f, u -> true) == null &&
            !queue.contains(p -> p.x == graphitePress.x && p.y == graphitePress.y)){
            queue.insert(0, new BuildPlan(graphitePress.x, graphitePress.y, 0, Blocks.graphitePress));
            System.out.println("native graphite producer lost; queued real-cost rebuild at " + graphitePress);
        }
        return false;
    }

    static boolean ensureSiliconSmelter(){
        var building=world.build(siliconSmelter.pack());
        if(building!=null&&building.block==Blocks.siliconSmelter)return true;
        gather=null;feedingTarget=null;
        if(Units.closestEnemy(Team.sharded,siliconSmelter.x*tilesize,siliconSmelter.y*tilesize,240f,u->true)==null&&
            !queue.contains(p->p.x==siliconSmelter.x&&p.y==siliconSmelter.y))queue.insert(0,new BuildPlan(siliconSmelter.x,siliconSmelter.y,0,Blocks.siliconSmelter));
        return false;
    }

    static void collectTitanium(){
        var core=Team.sharded.core();var unit=pilot.unit();
        unit.clearBuilding();unit.mineTile=null;
        if(unit.stack.amount>0){
            approach(unit,core.x,core.y,itemTransferRange*.6f);
            if(pilot.within(core,itemTransferRange)&&frame/60f>=depositReady){
                InputHandler.transferInventory(pilot,core);depositReady=frame/60f+state.rules.itemDepositCooldown;
                if(unit.stack.amount==0)collectingTitanium=false;
            }
        }else{
            var sep=world.build(pickupSource.pack());
            if(sep==null||sep.items.get(producedPickup)==0){collectingTitanium=false;return;}
            approach(unit,sep.x,sep.y,itemTransferRange*.6f);
            if(pilot.within(sep,itemTransferRange))InputHandler.requestItem(pilot,sep,producedPickup,unit.type.itemCapacity);
        }
    }
    static Item separatorPickup(Building sep){
        if(sep.items.get(Items.titanium)>0)return Items.titanium;
        for(var item:new Item[]{Items.copper,Items.lead,Items.graphite})if(sep.items.get(item)>0)return item;
        throw new IllegalStateException("Unexpected separator product");
    }

    static void approach(Unit unit, float x, float y, float within){
        motion.set(x - unit.x, y - unit.y);
        if(motion.len() <= within){ unit.moveAt(motion.setZero()); return; }
        unit.moveAt(motion.setLength(unit.speed()));
    }
}























