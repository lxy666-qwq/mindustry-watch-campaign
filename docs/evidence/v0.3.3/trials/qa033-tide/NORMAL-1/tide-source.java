import arc.files.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.entities.units.*;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret.*;
import mindustry.world.blocks.production.SolidPump.*;
import static mindustry.Vars.*;

/** Native W6 counterplay: only real construction, logistics, input control and game ticks. */
public class LegalTideTrial extends LegalWatchTrial{
    static Fi observations, counterSummary;
    static int removals, damageReductions;
    static int segmentBuiltAt = -1;
    static float lowestActivePower = 1f, minimumStored = Float.POSITIVE_INFINITY;
    static Bullet beforeTarget;
    static int beforeBulletId;
    static float beforeReload, beforeDamage;
    static boolean beforeAdded;
    static final IntSet loggedRemovals = new IntSet();
    static Point2 segmentPosition;

    public static void main(String[] args) throws Exception{
        if(args.length < 4 || !args[3].equals("watch-6")) throw new IllegalArgumentException("Only watch-6 is supported");
        var output = new Fi(args[1]);
        output.mkdirs();
        new Fi("trial-work/LegalTideTrial.java").copyTo(output.child("tide-source.java"));
        observations = output.child("segment-observations.csv");
        counterSummary = output.child("counter-result.txt");
        observations.writeString("seconds,event,targetOwner,bulletType,x,y,powerStatus,storedEnergy,coreHealth\n");
        planSetup = LegalTideTrial::planTide;
        beforeStep = LegalTideTrial::observeBefore;
        afterStep = LegalTideTrial::observeAfter;
        finalizeTrial = LegalTideTrial::finishCounter;
        LegalWatchTrial.main(args);
    }

    static void add(int x, int y, int rotation, mindustry.world.Block block){ queue.add(new BuildPlan(x, y, rotation, block)); }
    static void addGun(int x, int y, mindustry.world.Block block){ add(x, y, 0, block); guns.add(new Point2(x, y)); }
    static void addFuel(int x, int y, mindustry.world.Block block){ add(x, y, 0, block); fuel.add(new Point2(x, y)); }
    static void bridge(int x, int y, mindustry.world.Block block, int dx, int dy){
        queue.add(new BuildPlan(x, y, 0, block, new Point2(dx, dy)));
        add(x + dx, y + dy, 0, block);
    }

    static void planTide(){
        if(!WatchCampaign.version.equals("0.3.3")) throw new IllegalStateException("A fixed v0.3.3 runtime is required");
        if(!CampaignStars.completedReward(4)) throw new IllegalStateException("W5 actual five-star reward is required");
        var core = Team.sharded.core();
        if(core.items.get(Items.thorium) != 80 || core.items.get(Items.phaseFabric) != 40)
            throw new IllegalStateException("Native finite W6 kit was not supplied exactly once on the new mission");
        if(!Blocks.segment.unlockedNowHost() || !Blocks.segment.isPlaceable())
            throw new IllegalStateException("Actual earned Segment reward is unavailable");
        createTidePlans();
    }

    static void createTidePlans(){
        var base = mindustry.maps.planet.WatchMapGenerator.core(world.width());
        int x = base.x, y = base.y;
        segmentPosition = new Point2(x - 6, y - 2);
        arc.Events.on(BlockBuildEndEvent.class, e -> {
            if(!e.breaking && e.team == Team.sharded && e.tile.block() == Blocks.segment){
                segmentBuiltAt = frame;
                observations.writeString(frame / 60f + ",native-build-complete,,,,," + e.tile.build.power.status + "," + e.tile.build.power.graph.getLastPowerStored() + "," + Team.sharded.core().healthf() + "\n", true);
            }
        });
        addGun(x - 5, y + 11, Blocks.scatter);
        addGun(x + 5, y + 11, Blocks.scatter);
        add(x, y - 3, 0, Blocks.powerNode);
        add(x, y + 3, 0, Blocks.powerNode);
        add(x - 5, y + 2, 0, Blocks.powerNode);
        add(segmentPosition.x, segmentPosition.y, 0, Blocks.segment);

        add(x - 17, y + 27, 0, Blocks.pneumaticDrill);
        for(int cy = y + 26; cy >= y + 6; cy -= 5) bridge(x - 17, cy, Blocks.itemBridge, 0, -4);
        add(x - 17, y + 1, 3, Blocks.conveyor);
        for(int cx = x - 17; cx < x - 14; cx++) add(cx, y, 0, Blocks.conveyor);
        add(x - 14, y + 27, 0, Blocks.pneumaticDrill);
        for(int cx = x - 14; cx > x - 17; cx--) add(cx, y + 26, 2, Blocks.conveyor);
        add(x - 16, y + 24, 0, Blocks.mechanicalDrill);
        add(x - 14, y + 24, 0, Blocks.pneumaticDrill);
        add(x - 14, y + 23, 2, Blocks.conveyor);
        add(x - 15, y + 23, 2, Blocks.conveyor);
        add(x - 16, y + 23, 3, Blocks.conveyor);
        add(x - 16, y + 22, 3, Blocks.conveyor);
        add(x - 16, y + 21, 2, Blocks.conveyor);
        addFuel(x + 4, y + 3, Blocks.graphitePress);
        add(x + 4, y + 2, 3, Blocks.conveyor);
        add(x + 4, y + 1, 3, Blocks.conveyor);
        add(x - 11, y + 8, 0, Blocks.mechanicalDrill);
        for(int cy = y + 7; cy > y; cy--) add(x - 11, cy, 3, Blocks.conveyor);

        add(x - 10, y + 4, 0, Blocks.powerNode);
        add(x - 15, y + 6, 0, Blocks.powerNode);
        add(x - 19, y + 2, 0, Blocks.powerNode);
        add(x - 21, y + 4, 0, Blocks.waterExtractor);
        add(x - 23, y + 6, 0, Blocks.waterExtractor);
        add(x - 21, y + 6, 0, Blocks.conduit);
        add(x - 20, y + 6, 0, Blocks.conduit);
        for(int cy = y + 6; cy > y + 3; cy--) add(x - 19, cy, 3, Blocks.conduit);
        add(x - 19, y + 3, 0, Blocks.conduit);
        for(int offset : new int[]{-18, -15, -12}) bridge(x + offset, y + 3, Blocks.bridgeConduit, 2, 0);
        add(x - 9, y + 3, 0, Blocks.liquidRouter);
        add(x - 8, y + 3, 0, Blocks.conduit);
        add(x - 7, y + 3, 0, Blocks.conduit);
        add(x - 6, y + 3, 1, Blocks.conduit);
        add(x - 6, y + 4, 0, Blocks.liquidRouter);
        addFuel(x - 4, y + 4, Blocks.multiPress);
        add(x - 4, y + 2, 3, Blocks.conveyor);
        add(x - 4, y + 1, 3, Blocks.conveyor);
        add(x - 9, y + 4, 1, Blocks.conduit);
        addFuel(x - 9, y + 5, Blocks.steamGenerator);
        add(x - 6, y + 5, 1, Blocks.conduit);
        for(int cx = x - 6; cx < x + 4; cx++) add(cx, y + 6, 0, Blocks.conduit);
        addFuel(x + 4, y + 6, Blocks.steamGenerator);
        fuel.add(new Point2(x - 8, y - 7));
        addFuel(x + 1, y - 8, Blocks.combustionGenerator);
        addFuel(x + 3, y - 8, Blocks.combustionGenerator);

        for(int cy = y + 9; cy <= y + 21; cy += 6) add(x, cy, 0, Blocks.powerNode);
        add(x - 6, y + 9, 0, Blocks.powerNode);
        add(x - 12, y + 15, 0, Blocks.powerNode);
        add(x - 6, y + 21, 0, Blocks.powerNode);
        add(x - 12, y + 21, 0, Blocks.powerNode);
        add(x - 12, y + 27, 0, Blocks.powerNode);
        add(x + 5, y + 19, 0, Blocks.powerNode);
        for(var p : new Point2[]{new Point2(x - 13, y + 29), new Point2(x - 18, y + 28),
            new Point2(x - 12, y + 17), new Point2(x - 11, y + 11), new Point2(x - 3, y + 2),
            new Point2(x + 2, y + 2), new Point2(x - 3, y + 22), new Point2(x + 5, y + 22),
            new Point2(x, y + 22), new Point2(x + 10, y + 17)}) add(p.x, p.y, 0, Blocks.mender);
        for(int cx = x - 18; cx <= x - 14; cx++) add(cx, y + 29, 0, Blocks.copperWall);
        for(int cx = x - 18; cx <= x - 16; cx += 2) add(cx, y + 31, 0, Blocks.copperWallLarge);
        add(x - 20, y + 27, 0, Blocks.copperWallLarge);
        add(x + 9, y + 26, 0, Blocks.copperWallLarge);
        add(x + 11, y + 26, 0, Blocks.copperWallLarge);
        int forward = 0;
        for(int offset : new int[]{0, 2, -2, 4, -4, 6, -6, 8}){
            if(forward < 4 && mindustry.world.Build.validPlaceIgnoreUnits(Blocks.duo, Team.sharded, x + offset, y + 24, 0, true, true)){
                addGun(x + offset, y + 24, Blocks.duo);
                forward++;
            }
        }
        guns.add(new Point2(x - 7, y + 20), new Point2(x + 7, y + 20));
        addGun(x - 12, y + 30, Blocks.hail);
        addGun(x - 12, y + 28, Blocks.hail);
        addGun(x - 14, y + 15, Blocks.cyclone);
        addGun(x - 8, y + 9, Blocks.ripple);
        controlledArtillery = new Point2(x - 8, y + 9);
        for(int offset = -2; offset <= 2; offset++)
            if(mindustry.world.Build.validPlaceIgnoreUnits(Blocks.copperWall, Team.sharded, x + offset, y + 25, 0, true, true)) add(x + offset, y + 25, 0, Blocks.copperWall);
        for(var plan : queue) repairPlans.add(plan.copy());
        ObjectIntMap<mindustry.type.Item> costs = new ObjectIntMap<>();
        for(var plan : queue) for(var requirement : plan.block.requirements) costs.increment(requirement.item, 0, requirement.amount);
        var costText = new StringBuilder("plannedBuildings=" + queue.size + "\n");
        for(var item : content.items()) if(costs.containsKey(item)) costText.append(item.name).append('=').append(costs.get(item)).append('\n');
        if(counterSummary != null) counterSummary.parent().child("native-plan-costs.txt").writeString(costText.toString());
        System.out.println("Tide native plan cost from original block requirements:\n" + costText);
    }

    static PointDefenseBuild segment(){
        var build = world.build(segmentPosition.pack());
        return build instanceof PointDefenseBuild p ? p : null;
    }

    static void observeBefore(){
        var turret = segment();
        beforeTarget = turret == null ? null : turret.target;
        beforeAdded = beforeTarget != null && beforeTarget.isAdded();
        if(turret != null){
            beforeReload = turret.reloadCounter;
            if(beforeTarget != null){ beforeBulletId = beforeTarget.id; beforeDamage = beforeTarget.damage(); }
        }
    }

    static void observeAfter(){
        var turret = segment();
        if(turret == null) return;
        var target = turret.target;
        //A matching native target plus a reload reset gives a conservative lower bound on real interceptions.
        if(beforeAdded && target == beforeTarget && target.id == beforeBulletId && turret.reloadCounter < beforeReload){
            if(!target.isAdded() && loggedRemovals.add(target.id)){
                removals++;
                log(turret, target, "observed-native-removal");
            }else if(target.isAdded() && target.damage() < beforeDamage){
                damageReductions++;
                log(turret, target, "observed-native-damage-reduction");
            }
        }
        if(target != null && target.isAdded()){
            lowestActivePower = Math.min(lowestActivePower, turret.power.status);
            minimumStored = Math.min(minimumStored, turret.power.graph.getLastPowerStored());
        }
        if(frame % 60 == 0) log(turret, target, "native-grid-sample");
    }

    static void log(PointDefenseBuild turret, Bullet target, String event){
        String owner = target != null && target.owner instanceof Unit u ? u.type.name : "";
        var core = Team.sharded.core();
        observations.writeString(frame / 60f + "," + event + "," + owner + "," + (target == null ? "" : target.type.getClass().getSimpleName()) + "," +
            (target == null ? "" : target.x / tilesize) + "," + (target == null ? "" : target.y / tilesize) + "," + turret.power.status + "," + turret.power.graph.getLastPowerStored() + "," + (core == null ? 0f : core.healthf()) + "\n", true);
    }

    static void finishCounter(){
        String text = "segmentBuiltAtSeconds=" + segmentBuiltAt / 60f + "\nobservedNativeRemovalsLowerBound=" + removals +
            "\nobservedNativeDamageReductions=" + damageReductions + "\nminimumActivePowerStatus=" + lowestActivePower +
            "\nminimumStoredWhileTargetActive=" + minimumStored + "\nmode=" + mode + "\nactualVersion=" + actualVersion +
            "\nprofileScope=isolated-combat-qa-existing-actual-scores\nmainStars=" + CampaignStars.mainStars() + "\n";
        counterSummary.writeString(text);
        System.out.println(text);
    }
}
