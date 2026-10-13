import arc.*;
import arc.files.*;
import arc.math.geom.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;
import java.util.Locale;
import static mindustry.Vars.*;

/** Native player-construction boundary probe; no victory or campaign progress is awarded. */
public class LegalManualCargoProbe035{
    static final Fi assets = new Fi(System.getProperty("trial.assets", "local-work/mindustry-star-work/star035-runtime/assets"));
    static final Seq<BuildPlan> plans = new Seq<>();
    static final IntSet reserved = new IntSet();
    static final Seq<Point2> factories = new Seq<>();
    static final Vec2 motion = new Vec2();
    static Player pilot;
    static int frame, built, withdrawn, deposited, expectedPlans;
    static float nextDeposit, phaseOneStart = -1, factoriesStarted = -1;
    static boolean captured;
    static int taskAWithoutFactories;
    static Fi record;
    static SectorPreset mission;
    static Point2 manualInputCell;
    static Point2 actualMine;
    static Fi output;
    static boolean carriedSaved;
    static final boolean minedControl = Boolean.getBoolean("trial.minedControl");

    public static void main(String[] args) throws Exception{
        output = new Fi(args[0]);
        if(output.child("profile").exists()) throw new IllegalStateException("Probe requires a new isolated profile");
        output.mkdirs();
        new Fi("local-work/mindustry-star-work/manual-core-probe035/LegalManualCargoProbe035.java").copyTo(output.child("driver-source.java"));
        record = output.child("probe.csv");
        record.writeString("simSeconds,coreCopper,taskA,taskB,placed,withdrawn,deposited,gameOver\n");
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(){ @Override public float getDeltaTime(){ return 1f / 60f; } };
        Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(output.child("profile"));
        Core.settings.setJson(JsonIO.json);
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        logicVars = new GlobalVars(){ @Override public void update(){
            var local = Vars.player; Vars.player = null;
            try{ super.update(); }finally{ Vars.player = local; }
        }};
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        mission = WatchCampaign.sectors[7];
        CampaignStars.loadMission(mission, CampaignStars.Mode.TACTICAL);
        var core = Team.sharded.core();
        boolean unloaderAllowed = CampaignStars.available(Blocks.unloader);
        boolean graphiteAllowed = CampaignStars.available(Blocks.graphitePress);
        if(!unloaderAllowed || !graphiteAllowed) throw new IllegalStateException("Probe equipment is not native-allowed");
        pilot = Player.create(); pilot.name = "Native boundary probe"; pilot.team(Team.sharded);
        Vars.player = pilot; pilot.add(); logic.play(); CoreBlock.playerSpawn(core.tile, pilot);
        int initialCopper = core.items.get(Items.copper);
        String initialItems = core.items.toString();
        Events.on(BlockBuildEndEvent.class, e -> { if(e.team == Team.sharded && !e.breaking) built++; });
        Events.on(WithdrawEvent.class, e -> { if(e.player == pilot) withdrawn++; });
        Events.on(DepositEvent.class, e -> { if(e.player == pilot) deposited++; });
        planLoop(core);
        expectedPlans = plans.size;
        if(minedControl) planNativeMine();
        for(frame = 0; frame < 150 * 60 && !state.gameOver; frame++){
            Time.updateGlobal(); command(); asyncCore.begin(); logic.update(); asyncCore.end();
            if(frame % 600 == 0) snapshot();
            if(frame % 60 == 0) Thread.sleep(2);
        }
        snapshot();
        String result = "probe=" + (minedControl ? "native-drill-manual-conveyor-core-input" : "native-player-core-withdraw-manual-conveyor-core-return") + "\nselection=independent-equipment-boundary; campaign gate not claimed\n" +
            "mission=" + mission.name + "\nmode=TACTICAL\nwatchVersion=" + WatchCampaign.version +
            "\ncodeSource=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation() +
            "\nsourceJarModified=" + new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI())).lastModified() +
            "\nassets=" + assets.absolutePath() + "\nunloaderAllowed=" + unloaderAllowed + "\ngraphiteAllowed=" + graphiteAllowed +
            "\ninitialCopper=" + initialCopper + "\ninitialNativeItems=" + initialItems + "\nendingCopper=" + (Team.sharded.core() == null ? 0 : Team.sharded.core().items.get(Items.copper)) +
            "\nminedControl=" + minedControl + "\nactualMine=" + actualMine + "\ncarriedCheckpoint=" + carriedSaved + "\nnativePlaced=" + built + "\nexpectedLoopPlans=" + expectedPlans + "\npendingPlans=" + plans.size +
            "\nwithdrawals=" + withdrawn + "\ndeposits=" + deposited + "\nphaseOneTaskA=" + taskAWithoutFactories +
            "\nendingTaskA=" + count("taskA") + "\nendingTaskB=" + count("taskB") +
            "\ntacticalTaskComplete=" + CampaignStars.tacticalTaskComplete() + "\nhypotheticalScoreOnly=" + CampaignStars.scoreVictory() +
            "\npermanentStars=" + CampaignStars.totalStars() + "\ngameOver=" + state.gameOver +
            "\nminimumCore=" + CampaignStars.minimumCoreHealth() + "\nwave=" + state.wave + "\nsimSeconds=" + frame / 60f +
            "\nstatus=" + CampaignStars.status() + "\n";
        saveObserved("end");
        output.child("result.txt").writeString(result); System.out.println(result);
        System.exit(0);
    }

    static int count(String name) throws Exception{
        var method = CampaignStars.class.getDeclaredMethod(name, SectorPreset.class);
        method.setAccessible(true);
        return (Integer)method.invoke(null, mission);
    }

    static void snapshot() throws Exception{
        String row = frame / 60f + "," + (Team.sharded.core() == null ? 0 : Team.sharded.core().items.get(Items.copper)) +
            "," + count("taskA") + "," + count("taskB") + "," + built + "," + withdrawn + "," + deposited + "," + state.gameOver + "\n";
        record.writeString(row, true); System.out.print(row);
    }

    static boolean valid(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        if(tile == null || tile.block() != Blocks.air || !Build.validPlaceIgnoreUnits(block, Team.sharded, x, y, 0, true, true)) return false;
        int low = (block.size - 1) / 2;
        for(int dx = 0; dx < block.size; dx++) for(int dy = 0; dy < block.size; dy++){
            Tile part = world.tile(x - low + dx, y - low + dy);
            if(part == null || part.block() != Blocks.air || reserved.contains(part.pos())) return false;
        }
        return true;
    }

    static void planLoop(Building core){
        System.out.println("Actual core=" + core.tileX() + "," + core.tileY() + " block=" + core.block.name + " size=" + core.block.size);
        for(int dy = 12; dy >= -12; dy--){
            StringBuilder row = new StringBuilder();
            for(int dx = -20; dx <= 20; dx++){
                Tile tile = world.tile(core.tileX() + dx, core.tileY() + dy);
                row.append(tile.build == core ? 'C' : tile.block() != Blocks.air ? '#' : valid(Blocks.conveyor, tile.x, tile.y) ? '.' : '~');
            }
            System.out.println(row);
        }
        for(int mirrorX : new int[]{1, -1}) for(int mirrorY : new int[]{1, -1}){
            for(int width = 7; width <= 12; width++) for(int height = 7; height <= 12; height++){
                Seq<Point2> cells = new Seq<>();
                for(int y = 3; y <= height; y++) cells.add(new Point2(core.tileX() - mirrorX, core.tileY() + mirrorY * y));
                for(int x = 0; x <= width; x++) cells.add(new Point2(core.tileX() + mirrorX * x, core.tileY() + mirrorY * height));
                for(int y = height - 1; y >= 2; y--) cells.add(new Point2(core.tileX() + mirrorX * width, core.tileY() + mirrorY * y));
                for(int x = width - 1; x >= 1; x--) cells.add(new Point2(core.tileX() + mirrorX * x, core.tileY() + mirrorY * 2));
                int ux = core.tileX() - mirrorX, uy = core.tileY() + mirrorY * 2;
                if(!valid(Blocks.unloader, ux, uy) || cells.size < 24 || cells.contains(p -> !valid(Blocks.conveyor, p.x, p.y))) continue;
                manualInputCell = cells.first();
                for(int i = 0; i < cells.size; i++){
                    Point2 cell = cells.get(i), next = i + 1 < cells.size ? cells.get(i + 1) : new Point2(core.tileX() + mirrorX, core.tileY() + mirrorY);
                    add(new BuildPlan(cell.x, cell.y, world.tile(cell.pack()).relativeTo(world.tile(next.pack())), Blocks.conveyor));
                }
                System.out.println("native loop plans=" + plans.size + " core=" + core.tileX() + "," + core.tileY());
                return;
            }
        }
        throw new IllegalStateException("No native-valid 24-conveyor core return circuit");
    }

    static void add(BuildPlan plan){
        plans.add(plan);
        int low = (plan.block.size - 1) / 2;
        for(int dx = 0; dx < plan.block.size; dx++) for(int dy = 0; dy < plan.block.size; dy++) reserved.add(Point2.pack(plan.x - low + dx, plan.y - low + dy));
    }

    static void planFactories(){
        var core = Team.sharded.core();
        for(int i = 0; i < 2; i++){
            Tile best = null; float distance = Float.POSITIVE_INFINITY;
            for(int dx = -10; dx <= 10; dx++) for(int dy = -10; dy <= 10; dy++){
                int x = core.tileX() + dx, y = core.tileY() + dy;
                if(valid(Blocks.graphitePress, x, y) && dx * dx + dy * dy < distance){ best = world.tile(x, y); distance = dx * dx + dy * dy; }
            }
            if(best == null) throw new IllegalStateException("No legal graphite press placement");
            factories.add(new Point2(best.x, best.y)); add(new BuildPlan(best.x, best.y, 0, Blocks.graphitePress));
        }
    }

    static void command() throws Exception{
        if(pilot.dead() || Team.sharded.core() == null) return;
        Unit unit = pilot.unit();
        if(plans.any()){
            BuildPlan next = plans.first();
            if(next.tile().block() == next.block && next.tile().team() == Team.sharded){ plans.remove(0); unit.clearBuilding(); return; }
            for(var requirement : next.block.requirements) if(Team.sharded.core().items.get(requirement.item) == 0) throw new IllegalStateException("Native starting stock depleted: " + requirement.item);
            approach(unit, next.drawx(), next.drawy(), unit.type.buildRange * 0.8f);
            if(unit.plans.isEmpty()) unit.addBuild(next);
            return;
        }
        unit.clearBuilding();
        if(phaseOneStart < 0) phaseOneStart = frame / 60f;
        if(!captured && frame / 60f - phaseOneStart >= 30){
            taskAWithoutFactories = count("taskA"); captured = true;
            System.out.println("Copper-only core return taskA=" + taskAWithoutFactories + ", taskB=" + count("taskB"));
            planFactories(); return;
        }
        if(!captured){
            var core=Team.sharded.core();
            Building source = minedControl ? world.build(actualMine.pack()) : core;
            if(unit.stack.amount == 0){
                approach(unit,source.x,source.y,itemTransferRange*0.6f);
                if(pilot.within(source,itemTransferRange))InputHandler.requestItem(pilot,source,Items.copper,1);
            }else{
                if(!carriedSaved){
                    carriedSaved = true;
                    saveObserved("carried");
                    output.child("carried-position.txt").writeString("unitId="+unit.id+"\nreceiver="+manualInputCell.pack()+"\ncargo="+unit.item().name+":"+unit.stack.amount+"\n");
                }
                var receiver=world.build(manualInputCell.pack());
                approach(unit,receiver.x,receiver.y,itemTransferRange*0.6f);
                if(pilot.within(receiver,itemTransferRange) && frame/60f>=nextDeposit){
                    InputHandler.transferInventory(pilot,receiver); nextDeposit=frame/60f+state.rules.itemDepositCooldown;
                }
            }
            return;
        }
        if(factoriesStarted < 0) factoriesStarted = frame / 60f;
        var core = Team.sharded.core();
        if(unit.stack.amount > 0){
            Building target = core;
            if(unit.item() == Items.coal){
                for(var pos : factories){ var factory = world.build(pos.pack()); if(factory.items.get(Items.coal) < 10){ target = factory; break; } }
            }
            approach(unit, target.x, target.y, itemTransferRange * 0.6f);
            if(pilot.within(target, itemTransferRange) && frame / 60f >= nextDeposit){ InputHandler.transferInventory(pilot, target); nextDeposit = frame / 60f + state.rules.itemDepositCooldown; }
            return;
        }
        for(var pos : factories){
            var factory = world.build(pos.pack());
            if(factory.items.get(Items.graphite) >= 5){
                approach(unit, factory.x, factory.y, itemTransferRange * 0.6f);
                if(pilot.within(factory, itemTransferRange)) InputHandler.requestItem(pilot, factory, Items.graphite, unit.type.itemCapacity);
                return;
            }
        }
        for(var pos : factories){
            var factory = world.build(pos.pack());
            if(factory.items.get(Items.coal) < 10){
                approach(unit, core.x, core.y, itemTransferRange * 0.6f);
                if(pilot.within(core, itemTransferRange)) InputHandler.requestItem(pilot, core, Items.coal, unit.type.itemCapacity);
                return;
            }
        }
    }

    static void saveObserved(String name){
        output.child(name+"-tags.json").writeString(JsonIO.write(state.rules.tags));
        output.child(name+"-world.json").writeString(JsonIO.write(CargoStateAudit035.world()));
        SaveIO.write(output.child(name+".msav"));
    }

    static void planNativeMine(){
        Tile chosen = null;
        float nearest = Float.POSITIVE_INFINITY;
        for(var tile : world.tiles){
            if(!valid(Blocks.mechanicalDrill,tile.x,tile.y)) continue;
            int copper = tile.getLinkedTilesAs(Blocks.mechanicalDrill,new Seq<>()).count(t -> t.drop()==Items.copper);
            if(copper == 0) continue;
            float distance = tile.dst(Team.sharded.core().tile);
            if(distance < nearest){chosen=tile;nearest=distance;}
        }
        if(chosen == null) throw new IllegalStateException("No native copper ore and legal mechanical drill footprint");
        actualMine = new Point2(chosen.x,chosen.y);
        add(new BuildPlan(chosen.x,chosen.y,0,Blocks.mechanicalDrill));
        System.out.println("Genuine copper mine planned at "+actualMine+" oreTiles="+chosen.getLinkedTilesAs(Blocks.mechanicalDrill,new Seq<>()).count(t -> t.drop()==Items.copper));
    }

    static void approach(Unit unit, float x, float y, float within){
        motion.set(x - unit.x, y - unit.y);
        unit.moveAt(motion.len() <= within ? motion.setZero() : motion.setLength(unit.speed()));
    }
}
