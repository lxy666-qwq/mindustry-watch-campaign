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
import mindustry.world.blocks.defense.MendProjector.*;
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
    static Fi fuelObservations;
    static int boosterSiliconBefore, observedBoosterSiliconConsumed;
    static float maximumBoosterHeat;
    static int bandageGraphiteBefore, bandageGraphiteConsumed, bandageBuiltAt = -1;
    static Point2 bandagePosition = new Point2(94, 47);
    static Fi bandageObservations;
    static Fi gridConfigurations;

    public static void main(String[] args) throws Exception{
        if(args.length < 4 || !args[3].equals("watch-6")) throw new IllegalArgumentException("Only watch-6 is supported");
        var output = new Fi(args[1]);
        output.mkdirs();
        new Fi("local-work/mindustry-star-work/LegalTideTrial.java").copyTo(output.child("tide-source.java"));
        observations = output.child("segment-observations.csv");
        counterSummary = output.child("counter-result.txt");
        bandageObservations = output.child("bandage-observations.csv");
        bandageObservations.writeString("seconds,event,graphiteConsumed,totalConsumed,remainingGraphite,powerStatus,storedEnergy\n");
        gridConfigurations = output.child("native-grid-configurations.csv");
        gridConfigurations.writeString("seconds,fromX,fromY,toX,toY,nativeLinkAccepted\n");
        observations.writeString("seconds,event,targetOwner,bulletType,x,y,powerStatus,storedEnergy,coreHealth\n");
        fuelObservations = output.child("native-fuel-logistics.csv");
        fuelObservations.writeString("seconds,block,x,y,coal,water,efficiency,powerStatus,storedEnergy,powerBalance\n");
        var buildDamage = output.child("counter-building-damage.csv");
        buildDamage.writeString("seconds,block,x,y,health,sourceOwner,bulletX,bulletY\n");
        planSetup = LegalTideTrial::planTide;
        constructionCopperReserve = 120;
        lateCyclonePlastaniumReserve = 20;
        if(args[2].equals("TACTICAL")) constructionLimit = 160;
        if(args[2].equals("HEROIC")) siliconAmmoReserve = () -> {
            int remaining = 0;
            for(var plan : queue) for(var stack : plan.block.requirements) if(stack.item == Items.silicon) remaining += stack.amount;
            return remaining;
        };
        if(args[2].equals("HEROIC")) artilleryTargetPriority = u -> state.wave == 22 && u.type == UnitTypes.bryde ? -2000000f : 0f;
        fuelPolicy = LegalTideTrial::shouldRefuel;
        criticalFuel = b -> b.block == WatchRewardBlocks.fieldBandage && b.items.get(Items.graphite) < 2 ||
            b.block == Blocks.steamGenerator && b.power.graph.getLastPowerStored() < 1500f && b.items.get(Items.coal) < 2;
        beforeStep = LegalTideTrial::observeBefore;
        afterStep = LegalTideTrial::observeAfter;
        finalizeTrial = LegalTideTrial::finishCounter;
        arc.Events.on(BuildDamageEvent.class, e -> {
            if(e.build.team == Team.sharded && e.build.tileX() >= 68 && e.build.tileX() <= 78 && e.build.tileY() >= 48 && e.build.tileY() <= 57){
                String owner = e.source.owner instanceof Unit u ? u.type.name : e.source.owner.getClass().getSimpleName();
                buildDamage.writeString(frame / 60f + "," + e.build.block.name + "," + e.build.tileX() + "," + e.build.tileY() + "," +
                    e.build.healthf() + "," + owner + "," + e.source.x / tilesize + "," + e.source.y / tilesize + "\n", true);
            }
        });
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
        if(mode == CampaignStars.Mode.HEROIC && (!CampaignStars.completedReward(0) || !WatchRewardBlocks.fieldBandage.unlockedNowHost()))
            throw new IllegalStateException("W1 actual five-star Bandage reward is required");
        var core = Team.sharded.core();
        if(core.items.get(Items.thorium) != 80 || core.items.get(Items.phaseFabric) != 40)
            throw new IllegalStateException("Native finite W6 kit was not supplied exactly once on the new mission");
        if(!Blocks.segment.unlockedNowHost() || !Blocks.segment.isPlaceable())
            throw new IllegalStateException("Actual earned Segment reward is unavailable");
        var stock = new StringBuilder("Actual native fresh-world core stock; no inventory has been changed by the driver.\n");
        for(var item : content.items()) stock.append(item.name).append('=').append(core.items.get(item)).append('\n');
        counterSummary.parent().child("native-starting-stock.txt").writeString(stock.toString());
        createTidePlans();
    }

    static void createTidePlans(){
        var base = mindustry.maps.planet.WatchMapGenerator.core(world.width());
        int x = base.x, y = base.y;
        segmentPosition = mode == CampaignStars.Mode.HEROIC ? new Point2(x - 13, y + 14) : new Point2(x - 13, y + 7);
        if(!mindustry.world.Build.validPlaceIgnoreUnits(Blocks.segment, Team.sharded, segmentPosition.x, segmentPosition.y, 0, true, true))
            throw new IllegalStateException("Segment plan is not a native buildable site: " + segmentPosition);
        arc.Events.on(BlockBuildEndEvent.class, e -> {
            if(!e.breaking && e.team == Team.sharded && e.tile.block() == Blocks.segment){
                segmentBuiltAt = frame;
                observations.writeString(frame / 60f + ",native-build-complete,,,,," + e.tile.build.power.status + "," + e.tile.build.power.graph.getLastPowerStored() + "," + Team.sharded.core().healthf() + "\n", true);
            }
            if(!e.breaking && e.team == Team.sharded && e.tile.block() == WatchRewardBlocks.fieldBandage) bandageBuiltAt = frame;
        });
        addGun(x - 5, y + 11, Blocks.scatter);
        addGun(x + 5, y + 11, Blocks.scatter);
        add(x, y - 3, 0, Blocks.powerNode);
        add(x, y + 3, 0, Blocks.powerNode);
        add(x - 5, y + 2, 0, Blocks.powerNode);
        add(x - 10, y + 2, 0, Blocks.powerNode);
        add(x - 12, y - 3, 0, Blocks.powerNode);
        add(segmentPosition.x, segmentPosition.y, 0, Blocks.segment);
        add(x - 7, y + 7, 0, Blocks.powerNode);

        if(mode == CampaignStars.Mode.HEROIC) heroCoalTransport(x, y); else{
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
        }
        addFuel(x + 4, y + 3, Blocks.graphitePress);
        add(x + 4, y + 2, 3, Blocks.conveyor);
        add(x + 4, y + 1, 3, Blocks.conveyor);
        add(x - 11, y + 8, 0, Blocks.mechanicalDrill);
        for(int cy = y + 7; cy > y; cy--) add(x - 11, cy, 3, Blocks.conveyor);
        for(int cx : new int[]{x - 16, x - 12}){
            add(cx, y + 10, 0, Blocks.pneumaticDrill);
            add(cx, y + 9, cx < x - 14 ? 0 : 2, Blocks.conveyor);
            add(cx + (cx < x - 14 ? 1 : -1), y + 9, cx < x - 14 ? 0 : 2, Blocks.conveyor);
        }
        add(x + 16, y + 10, 0, Blocks.pneumaticDrill);
        add(x + 16, y + 9, 2, Blocks.conveyor);
        add(x + 15, y + 9, 2, Blocks.conveyor);
        add(x + 16, y + 7, 0, Blocks.mechanicalDrill);
        add(x + 16, y + 6, 2, Blocks.conveyor);
        add(x + 15, y + 6, 2, Blocks.conveyor);

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
        add(x + 3, y - 4, 0, Blocks.battery);
        add(x + 4, y - 4, 0, Blocks.battery);

        for(int cy = y + 9; cy <= y + 21; cy += 6) add(x, cy, 0, Blocks.powerNode);
        add(x - 6, y + 9, 0, Blocks.powerNode);
        add(x - 12, y + 15, 0, Blocks.powerNode);
        add(x - 6, y + 21, 0, Blocks.powerNode);
        add(x - 12, y + 21, 0, Blocks.powerNode);
        add(x - 12, y + 27, 0, Blocks.powerNode);
        add(x + 5, y + 19, 0, Blocks.powerNode);
        for(var p : new Point2[]{new Point2(x - 13, y + 29),
            new Point2(x - 12, y + 17), new Point2(x - 10, y + 11), new Point2(x - 3, y + 2),
            new Point2(x - 3, y + 22), new Point2(x + 5, y + 22)}) add(p.x, p.y, 0, Blocks.mender);
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
        addGun(x - 4, y - 3, Blocks.hail);
        addGun(x - 6, y - 3, Blocks.hail);
        controlledArtillery = new Point2(x - 8, y + 9);
        for(int offset = -2; offset <= 2; offset++)
            if(mindustry.world.Build.validPlaceIgnoreUnits(Blocks.copperWall, Team.sharded, x + offset, y + 25, 0, true, true)) add(x + offset, y + 25, 0, Blocks.copperWall);
        if(mode == CampaignStars.Mode.TACTICAL) rearWater(x, y);
        if(mode == CampaignStars.Mode.HEROIC) heroicEquipment(x, y);
        for(var support : queue){
            int sx = support.x - (support.block.size - 1) / 2, sy = support.y - (support.block.size - 1) / 2;
            for(var other : queue){
                if(other == support || other.block == support.block && other.x == support.x && other.y == support.y) continue;
                int ox = other.x - (other.block.size - 1) / 2, oy = other.y - (other.block.size - 1) / 2;
                if(sx < ox + other.block.size && ox < sx + support.block.size && sy < oy + other.block.size && oy < sy + support.block.size)
                    throw new IllegalStateException("Support blueprint overlaps another planned block: " + support + " / " + other);
            }
        }
        //Cheap fighting positions precede the industrial investment; interception is needed for the late navy.
        queue.sort(plan -> priority(plan));
        guns.sort(pos -> {
            var planned = queue.find(p -> p.x == pos.x && p.y == pos.y);
            return planned != null && planned.block == Blocks.duo ? 0f : 1f;
        });
        for(var plan : queue) repairPlans.add(plan.copy());
        ObjectIntMap<mindustry.type.Item> costs = new ObjectIntMap<>();
        for(var plan : queue) for(var requirement : plan.block.requirements) costs.increment(requirement.item, 0, requirement.amount);
        initialTransportGlassReserve = costs.get(Items.metaglass, 0);
        var costText = new StringBuilder("plannedBuildings=" + queue.size + "\n");
        for(var item : content.items()) if(costs.containsKey(item)) costText.append(item.name).append('=').append(costs.get(item)).append('\n');
        if(counterSummary != null) counterSummary.parent().child("native-plan-costs.txt").writeString(costText.toString());
        System.out.println("Tide native plan cost from original block requirements:\n" + costText);
    }

    static float priority(BuildPlan plan){
        var block = plan.block;
        if(mode == CampaignStars.Mode.HEROIC && block == Blocks.powerNode && (plan.x == 88 || plan.x == 93 && plan.y == 45)) return 1.25f;
        if(mode == CampaignStars.Mode.HEROIC && block == Blocks.mender && plan.x == 95 && plan.y == 49) return 1.4f;
        if(block == WatchRewardBlocks.fieldBandage) return 1.5f;
        if(block == Blocks.duo || block == Blocks.copperWall && plan.y == 51) return 0f;
        if(block == Blocks.scatter) return 1f;
        if(block == Blocks.mechanicalDrill || block == Blocks.pneumaticDrill || block == Blocks.itemBridge || block == Blocks.conveyor || block == Blocks.graphitePress) return 2f;
        if(block == Blocks.copperWall || block == Blocks.copperWallLarge) return 3f;
        if(block == Blocks.cyclone || block == Blocks.ripple || block == Blocks.hail || block == Blocks.salvo || block == Blocks.wave) return 4f;
        if(block == Blocks.segment) return 7f;
        if(block == Blocks.lancer) return 8f;
        if(block == Blocks.mender) return 6f;
        return 5f;
    }

    static void rearWater(int x, int y){
        queue.removeAll(p -> p.block == Blocks.waterExtractor || p.block == Blocks.conduit || p.block == Blocks.bridgeConduit || p.block == Blocks.liquidRouter ||
            p.block == Blocks.copperWallLarge && p.x > x ||
            p.block == Blocks.mechanicalDrill && p.x == x - 11 && p.y == y + 8 ||
            p.block == Blocks.conveyor && p.x == x - 11 && p.y > y && p.y < y + 8);
        add(x + 5, y - 7, 0, Blocks.powerNode);
        add(x + 8, y - 4, 0, Blocks.powerNode);
        add(x + 8, y - 8, 0, Blocks.waterExtractor);
        add(x + 11, y - 8, 0, Blocks.waterExtractor);
        for(int cx = x + 11; cx > x + 7; cx--) add(cx, y - 9, 2, Blocks.conduit);
        add(x + 7, y - 9, 1, Blocks.conduit);
        for(int cy = y - 8; cy <= y - 3; cy++) add(x + 7, cy, 1, Blocks.conduit);
        bridge(x + 7, y - 2, Blocks.bridgeConduit, 0, 3);
        add(x + 7, y + 2, 0, Blocks.liquidRouter);
        bridge(x + 7, y + 3, Blocks.bridgeConduit, -4, 0);
        add(x + 3, y + 2, 2, Blocks.conduit);
        for(int cx = x + 2; cx >= x - 1; cx--) add(cx, y + 2, 2, Blocks.conduit);
        bridge(x - 2, y + 2, Blocks.bridgeConduit, -4, 0);
        add(x - 7, y + 2, 2, Blocks.conduit);
        add(x - 8, y + 2, 2, Blocks.conduit);
        add(x - 9, y + 2, 1, Blocks.conduit);
        add(x - 9, y + 3, 0, Blocks.liquidRouter);
        add(x - 9, y + 4, 1, Blocks.conduit);
        for(int cx = x - 8; cx <= x - 6; cx++) add(cx, y + 3, 0, Blocks.conduit);
        add(x - 6, y + 4, 0, Blocks.liquidRouter);
        for(int cy = y + 2; cy <= y + 4; cy++) add(x + 6, cy, 1, Blocks.conduit);
        add(x + 6, y + 5, 2, Blocks.conduit);
        add(x + 5, y + 5, 2, Blocks.conduit);
        add(x + 4, y + 5, 1, Blocks.conduit);
    }

    static void heroicEquipment(int x, int y){
        rearWater(x, y);
        queue.removeAll(p -> p.block == Blocks.multiPress || p.block == Blocks.conveyor && p.x == x - 4 && p.y > y && p.y <= y + 2 ||
            p.block == Blocks.duo && p.y == y + 24 && (p.x == x + 2 || p.x == x + 4) ||
            p.block == Blocks.mender && p.x == x + 5 && p.y == y + 22 ||
            p.block == Blocks.conveyor && p.x == x - 14 && p.y == y + 23);
        guns.removeAll(p -> p.y == y + 24 && (p.x == x + 2 || p.x == x + 4));
        fuel.removeAll(p -> p.x == x - 4 && p.y == y + 4);
        addGun(x + 2, y + 23, Blocks.salvo);
        addGun(x + 4, y + 22, Blocks.salvo);
        queue.removeAll(p -> p.block == Blocks.cyclone || p.block == Blocks.mender && p.x == 76 && p.y == 43 || p.block == Blocks.powerNode && p.x == 76 && p.y == 41);
        guns.removeAll(p -> p.x == 74 && p.y == 41);
        addGun(76,43,Blocks.cyclone);
        add(78,43,0,Blocks.mender);
        add(78,40,0,Blocks.powerNode);        add(x + 7, y + 23, 0, Blocks.mender);
        addFuel(bandagePosition.x, bandagePosition.y, WatchRewardBlocks.fieldBandage);
        add(x - 7, y + 5, 0, Blocks.wave);
        add(x - 14, y + 22, 0, Blocks.graphitePress);
        queue.removeAll(p -> p.block == Blocks.powerNode && p.x == 76 && p.y == 47);
        add(75,47,0,Blocks.powerNode);
        add(80,48,0,Blocks.powerNode);
        heroWater(x, y);
    }

    static void heroCoalTransport(int x, int y){
        add(x - 17, y + 27, 0, Blocks.pneumaticDrill);
        add(x - 14, y + 27, 0, Blocks.pneumaticDrill);
        add(x - 16, y + 26, 0, Blocks.conveyor);
        add(x - 15, y + 26, 0, Blocks.conveyor);
        for(var chain : new Point2[][]{
            {new Point2(74,52),new Point2(76,52),new Point2(76,48)},
            {new Point2(76,47),new Point2(80,47),new Point2(80,43),new Point2(84,43),new Point2(84,39),new Point2(84,35),new Point2(84,31),new Point2(84,29),new Point2(86,29),new Point2(86,25)}
        }){
            for(int i=0;i<chain.length-1;i++) queue.add(new BuildPlan(chain[i].x,chain[i].y,0,Blocks.itemBridge,new Point2(chain[i+1].x-chain[i].x,chain[i+1].y-chain[i].y)));
            add(chain[chain.length-1].x,chain[chain.length-1].y,0,Blocks.itemBridge);
        }
        add(x - 16, y + 24, 0, Blocks.mechanicalDrill);
        add(x - 14, y + 24, 0, Blocks.pneumaticDrill);
        add(x - 16, y + 23, 3, Blocks.conveyor);
        add(x - 16, y + 22, 3, Blocks.conveyor);
        add(x - 16, y + 21, 0, Blocks.conveyor);
        add(x - 15, y + 21, 0, Blocks.conveyor);
        queue.add(new BuildPlan(x - 14,y + 21,0,Blocks.itemBridge,new Point2(2,0)));
    }
    static void heroWater(int x, int y){
        queue.removeAll(p -> p.block == Blocks.waterExtractor || p.block == Blocks.conduit || p.block == Blocks.bridgeConduit || p.block == Blocks.liquidRouter ||
            p.block == Blocks.powerNode && (p.x == x + 5 && p.y == y - 7 || p.x == x + 8 && p.y == y - 4) ||
            p.block == Blocks.powerNode && p.x == x - 5 && p.y == y + 2 ||
            p.block == Blocks.mender && p.x == x - 3 && p.y == y + 2);
        add(x - 5, y + 1, 0, Blocks.powerNode);
        add(x - 3, y + 1, 0, Blocks.mender);
        add(x + 5, y + 9, 0, Blocks.powerNode);
        add(x + 6, y + 8, 0, Blocks.waterExtractor);
        add(x + 9, y + 8, 0, Blocks.waterExtractor);
        for(int cx = x + 9; cx > x + 6; cx--) add(cx, y + 7, 2, Blocks.conduit);
        add(x + 6, y + 7, 0, Blocks.liquidRouter);
        for(int cy = y + 6; cy >= y + 3; cy--) add(x + 6, cy, 3, Blocks.conduit);
        bridge(x + 6, y + 2, Blocks.bridgeConduit, -4, 0);
        for(int cx = x + 1; cx > x - 9; cx--) add(cx, y + 2, 2, Blocks.conduit);
        add(x - 9, y + 2, 1, Blocks.conduit);
        add(x - 9, y + 3, 0, Blocks.liquidRouter);
        add(x - 9, y + 4, 1, Blocks.conduit);
        add(x - 8, y + 3, 0, Blocks.conduit);
        add(x - 7, y + 3, 0, Blocks.conduit);
        add(x - 6, y + 3, 1, Blocks.conduit);
        add(x - 6, y + 4, 0, Blocks.liquidRouter);
    }

    static PointDefenseBuild segment(){
        var build = world.build(segmentPosition.pack());
        return build instanceof PointDefenseBuild p ? p : null;
    }

    static boolean shouldRefuel(Building building){
        var core = Team.sharded.core();
        if(building.block == Blocks.mender) return core.items.get(Items.silicon) > siliconReserve();
        if((building.block == Blocks.multiPress || building.block == Blocks.graphitePress) && core.items.get(Items.coal) < 100) return false;
        if(building.block == Blocks.steamGenerator || building.block == Blocks.combustionGenerator){
            if(mode == CampaignStars.Mode.HEROIC && building.block == Blocks.combustionGenerator && world.build(79, 31) != null && world.build(79, 31).block == Blocks.steamGenerator &&
                world.build(92, 32) != null && world.build(92, 32).block == Blocks.steamGenerator) return false;
            var counter = segment();
            return building.power.graph.getLastPowerStored() < 4500f || counter != null && counter.target != null && counter.target.isAdded();
        }
        return true;
    }

    static void observeBefore(){
        if(frame % 60 == 0) connectNativeGrid();
        var bandage = world.build(bandagePosition.pack());
        bandageGraphiteBefore = bandage != null && bandage.block == WatchRewardBlocks.fieldBandage ? bandage.items.get(Items.graphite) : 0;
        var booster = world.build(96, 45);
        boosterSiliconBefore = booster != null && booster.block == Blocks.mender ? booster.items.get(Items.silicon) : 0;
        var turret = segment();
        beforeTarget = turret == null ? null : turret.target;
        beforeAdded = beforeTarget != null && beforeTarget.isAdded();
        if(turret != null){
            beforeReload = turret.reloadCounter;
            if(beforeTarget != null){ beforeBulletId = beforeTarget.id; beforeDamage = beforeTarget.damage(); }
        }
    }

    static void connectNativeGrid(){
        for(var edge : new int[][]{{76,23,78,28}, {78,28,83,27}, {83,27,88,29}, {88,29,88,35}, {88,35,88,41},
            {88,41,93,45}, {88,35,93,35}, {93,35,92,32}, {78,28,79,31}, {86,19,88,23}, {88,23,88,29}, {73,32,75,33}, {78,28,75,33},
            {81,33,75,33}, {81,33,79,31}, {81,33,82,35}, {82,35,88,35}, {88,23,91,22}, {88,23,92,22}, {82,35,78,40}, {78,40,75,40}, {78,40,78,43}}){
            var from = world.build(edge[0], edge[1]);
            var to = world.build(edge[2], edge[3]);
            if(from != null && from.block == Blocks.powerNode && to != null && to.power != null && !from.power.links.contains(to.pos())){
                if(!((mindustry.world.blocks.power.PowerNode)from.block).linkValid(from, to)) continue;
                mindustry.input.InputHandler.tileConfig(pilot, from, to.pos());
                gridConfigurations.writeString(frame / 60f + "," + edge[0] + "," + edge[1] + "," + edge[2] + "," + edge[3] + "," + from.power.links.contains(to.pos()) + "\n", true);
            }
        }
    }

    static void observeAfter(){
        var bandage = world.build(bandagePosition.pack());
        if(bandage != null && bandage.block == WatchRewardBlocks.fieldBandage){
            int consumed = Math.max(0, bandageGraphiteBefore - bandage.items.get(Items.graphite));
            bandageGraphiteConsumed += consumed;
            if(consumed > 0 || frame % 300 == 0) bandageObservations.writeString(frame / 60f + "," + (consumed > 0 ? "native-consume-and-heal" : "native-sample") + "," + consumed + "," + bandageGraphiteConsumed + "," +
                bandage.items.get(Items.graphite) + "," + bandage.power.status + "," + bandage.power.graph.getLastPowerStored() + "\n", true);
        }
        if(frame % 300 == 0) for(var pos : fuel){
            var b = world.build(pos.pack());
            if(b != null && b.power != null && b.items != null) fuelObservations.writeString(frame / 60f + "," + b.block.name + "," + b.tileX() + "," + b.tileY() + "," +
                b.items.get(Items.coal) + "," + (b.liquids == null ? 0f : b.liquids.get(Liquids.water)) + "," + b.efficiency + "," + b.power.status + "," +
                b.power.graph.getLastPowerStored() + "," + b.power.graph.getPowerBalance() + "\n", true);
        }
        var booster = world.build(96, 45);
        if(booster instanceof MendBuild mender){
            observedBoosterSiliconConsumed += Math.max(0, boosterSiliconBefore - mender.items.get(Items.silicon));
            maximumBoosterHeat = Math.max(maximumBoosterHeat, mender.phaseHeat);
        }
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
            "\nobservedNativeBoosterSiliconConsumed=" + observedBoosterSiliconConsumed + "\nmaximumNativeMenderPhaseHeat=" + maximumBoosterHeat +
            "\nbandageBuiltAtSeconds=" + bandageBuiltAt / 60f + "\nobservedNativeBandageGraphiteConsumed=" + bandageGraphiteConsumed +
            "\nprofileScope=isolated-combat-qa-existing-actual-scores\nmainStars=" + CampaignStars.mainStars() + "\n";
        text += "driverClassLocation=" + LegalTideTrial.class.getProtectionDomain().getCodeSource().getLocation() + "\n";
        text += "rulesTags=" + state.rules.tags + "\n";
        var core = Team.sharded.core();
        if(core != null) for(var item : content.items()) text += "endingCore." + item.name + "=" + core.items.get(item) + "\n";
        if(over && winner == Team.sharded){
            var save = counterSummary.parent().child("end.msav");
            mindustry.io.SaveIO.write(save);
            text += "nativeEndSave=" + save.absolutePath() + "\n";
        }
        counterSummary.writeString(text);
        System.out.println(text);
    }
}
