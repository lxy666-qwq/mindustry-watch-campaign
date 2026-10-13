import arc.*;
import arc.files.*;
import arc.math.geom.*;
import arc.math.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
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
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.production.BeamDrill;
import mindustry.world.meta.Attribute;
import mindustry.entities.units.BuildPlan;
import mindustry.entities.Units;
import mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild;
import mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild;
import java.util.*;
import static mindustry.Vars.*;

/** Dedicated Lake inspection/driver. Never changes resources, goals, waves, units or victory. */
public class Lake034Trial{
    static Fi output;
    static Player pilot;
    static boolean over;
    static Team winner;
    static int frame, built;
    static long started;
    static Mode mode;
    static boolean gate;
    static final Vec2 motion = new Vec2();
    static final Seq<BuildPlan> queue=new Seq<>();
    static final Seq<BuildPlan> originals=new Seq<>();
    static final Seq<BuildPlan> producers=new Seq<>();
    static final ObjectMap<BuildPlan,BuildPlan> outputPipes=new ObjectMap<>();
    static final IntSet reserved=new IntSet(), grid=new IntSet();
    static boolean planned, expansion;
    static int deposits, commands;
    static float depositReady;
    static int factoryPos, furnacePos, forwardPos;
    static final IntSeq factories = new IntSeq();
    static boolean flankReady;
    static boolean mainAssault;
    static boolean flankAssembled;
    static boolean northAssembled, northTurretCleared;
    static int navalStage;
    static final IntSet committed = new IntSet();
    static final int[] scoresBefore = new int[3];
    public static void main(String[] args) throws Exception{
        String action=args[0]; output=new Fi(args[1]); mode=args.length>2?Mode.valueOf(args[2]):Mode.NORMAL;
        int seconds=args.length>3?Integer.parseInt(args[3]):1800;
        output.mkdirs();
        try(var embedded=Lake034Trial.class.getClassLoader().getResourceAsStream("watch-version.txt")){
            output.child("loaded-watch-version.txt").writeBytes(embedded.readAllBytes());
        }
        Fi profile=output.child("profile");
        if(profile.exists()) throw new IllegalArgumentException("Require a fresh independent profile: "+profile);
        profile.mkdirs();
        new Fi(System.getProperty("trial.sourceSettings","local-work/aegis-trials/tactical-01-v033/profile/settings.bin")).copyTo(profile.child("settings.bin"));
        new Fi("local-work/lake-034-work/Lake034Trial.java").copyTo(output.child("driver-source.java"));
        new Fi("local-work/lake-034-work/LakeStateAudit.java").copyTo(output.child("state-audit-source.java"));
        started=System.nanoTime(); Log.useColors=false;
        Core.app=new MockApplication(); Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?output.child("loaded-watch-version.txt"):new Fi(System.getProperty("trial.assets","local-work/mindustry-star-work/star034-runtime/assets")).child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};
        Core.audio=new MockAudio(); Core.input=new MockInput(); Core.settings=new Settings();
        Core.settings.setDataDirectory(profile); Core.settings.setJson(JsonIO.json);
        headless=true; net=new Net(null); Vars.init(); Vars.loadSettings();
        logicVars=new GlobalVars(){@Override public void update(){var local=Vars.player; Vars.player=null; try{super.update();}finally{Vars.player=local;}}};
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent(); logic=new Logic(); netServer=new NetServer(); content.init();
        Fi runtimeCode=new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        output.child("runtime.txt").writeString("watchVersion="+WatchCampaign.version+"\nactualClassLocation="+runtimeCode+"\nactualCodeLocationModified="+runtimeCode.lastModified()+"\nprofile="+profile+"\nfixedDelta=1/60 native tick; no wave skip\n");
        for(var savedMode:Mode.values())scoresBefore[savedMode.ordinal()]=CampaignStars.stars(SectorPresets.lake,savedMode);
        output.child("inherited-onset.txt").writeString("NORMAL="+CampaignStars.stars(SectorPresets.onset,Mode.NORMAL)+"\nHEROIC="+CampaignStars.stars(SectorPresets.onset,Mode.HEROIC)+"\nTACTICAL="+CampaignStars.stars(SectorPresets.onset,Mode.TACTICAL)+"\n");
        output.child("inherited-aegis.txt").writeString("NORMAL="+CampaignStars.stars(SectorPresets.aegis,Mode.NORMAL)+"\nHEROIC="+CampaignStars.stars(SectorPresets.aegis,Mode.HEROIC)+"\nTACTICAL="+CampaignStars.stars(SectorPresets.aegis,Mode.TACTICAL)+"\nmainStars="+CampaignStars.mainStars()+"\ntotalStars="+CampaignStars.totalStars()+"\n");
        output.child("scores-before.txt").writeString("NORMAL="+scoresBefore[0]+"\nHEROIC="+scoresBefore[1]+"\nTACTICAL="+scoresBefore[2]+"\nsource="+System.getProperty("trial.sourceSettings","local-work/aegis-trials/tactical-01-v033/profile/settings.bin")+"\n");
        gate=CampaignStars.unlocked(SectorPresets.lake)&&CampaignProgression.allowed(SectorPresets.lake,mode)&&(mode==Mode.NORMAL||CampaignStars.normalComplete(SectorPresets.lake));
        if(!gate) throw new IllegalStateException("Native starting-map gate closed: "+mode);
        CampaignStars.loadMission(SectorPresets.lake,mode);
        Events.on(GameOverEvent.class,e->{over=true;winner=e.winner;});
        Events.on(BlockBuildEndEvent.class,e->{if(e.team==state.rules.defaultTeam&&!e.breaking)built++;});
        pilot=Player.create(); pilot.name="Native Lake tutorial trial"; pilot.team(state.rules.defaultTeam); Vars.player=pilot; pilot.add();
        logic.play(); CoreBlock.playerSpawn(state.rules.defaultTeam.core().tile,pilot);
        output.child("mission-description.txt").writeString(CampaignStars.describe(SectorPresets.lake,mode)+"\n");
        output.child("mission-limits.txt").writeString("limits="+CampaignStars.limits(SectorPresets.lake)+"\nrulesDeadline="+state.rules.tags.get("stars.deadline")+"\nrulesLossBudget="+state.rules.tags.get("stars.lossBudget")+"\n");
        Events.on(BlockDestroyEvent.class,e->{if(e.tile.team()==state.rules.defaultTeam)output.child("player-losses.csv").writeString(CampaignStars.elapsed()+","+e.tile.pos()+","+e.tile.block().name+"\n",true);});
        output.child("objectives.json").writeString(JsonIO.write(state.rules.objectives));
        StringBuilder map=new StringBuilder("size="+world.width()+"x"+world.height()+" core="+state.rules.defaultTeam.core().tile+" resources="+state.rules.defaultTeam.core().items+"\n");
        for(var b:Groups.build){
            if(b instanceof LogicBuild p) output.child("processor-"+b.tileX()+"-"+b.tileY()+".mlog").writeString(p.code);
            if(b.team==state.rules.defaultTeam) map.append(b.block.name).append(" @").append(b.tileX()).append(',').append(b.tileY()).append(" rotation=").append(b.rotation).append(" items=").append(b.items).append('\n');
            if(b.team==state.rules.waveTeam) output.child("enemy-map.txt").writeString(b.block.name+" "+b.tileX()+","+b.tileY()+"\n",true);
        }
        output.child("map.txt").writeString(map.toString());
        StringBuilder terrain=new StringBuilder("limit="+state.rules.limitX+","+state.rules.limitY+","+state.rules.limitWidth+","+state.rules.limitHeight+"\n");
        for(var tile:world.tiles){
            if(Mathf.dst(tile.x,tile.y,state.rules.defaultTeam.core().tileX(),state.rules.defaultTeam.core().tileY())>110) continue;
            if(Blocks.turbineCondenser.canPlaceOn(tile,state.rules.defaultTeam,0)&&Build.validPlaceIgnoreUnits(Blocks.turbineCondenser,state.rules.defaultTeam,tile.x,tile.y,0,true,true)) terrain.append("vent ").append(tile.x).append(',').append(tile.y).append('\n');
            if(tile.wallDrop()!=null&&(tile.wallDrop()==Items.beryllium||tile.wallDrop()==Items.graphite)) terrain.append("wall ").append(tile.x).append(',').append(tile.y).append(' ').append(tile.wallDrop().name).append('\n');
        }
        output.child("terrain.txt").writeString(terrain.toString());
        output.child("trial.csv").writeString("simSeconds,wallSeconds,wave,enemies,coreHealth,beryllium,graphite,silicon,tungsten,built,units,objectivesComplete,flags\n");
        snapshot();
        if(action.equals("inspect")){result("inspection only, no simulation");System.exit(0);}
        try{
            for(frame=0;frame<seconds*60&&!over;frame++){
                Time.updateGlobal();
                command(); asyncCore.begin(); logic.update(); asyncCore.end();
                if(frame%3600==0) snapshot();
                if(mainAssault&&frame%60==0)armySample();
                if(frame==900*60||frame==2400*60)checkpoint(frame/60);
                if(frame%60==0) Thread.sleep(2);
            }
        }catch(IllegalStateException failure){
            output.child("failure.txt").writeString(failure.toString()+"\n");
            snapshot(); result("driver failed: "+failure.getMessage());
            SaveIO.write(output.child("partial.msav"));
            throw failure;
        }
        snapshot(); result("native tick driver");
        output.child("rules-before-save.json").writeString(JsonIO.write(state.rules));
        output.child("final-tags.json").writeString(JsonIO.write(state.rules.tags));
        output.child("final-world.json").writeString(JsonIO.write(LakeStateAudit.world()));
        SaveIO.write(output.child("end.msav"));
        output.child("save-status.txt").writeString("end.msav SaveIO.write completed; fresh-process readback pending\n");
        System.exit(over&&winner==state.rules.defaultTeam?0:2);
    }
    static void checkpoint(int seconds){
        var tags=new StringMap();tags.putAll(state.rules.tags);
        var values=LakeStateAudit.world();int controlled=pilot.unit().id;
        output.child("checkpoint-"+seconds+"-tags.json").writeString(JsonIO.write(tags));
        output.child("checkpoint-"+seconds+"-world.json").writeString(JsonIO.write(values));
        Fi save=output.child("checkpoint-"+seconds+".msav");
        SaveIO.write(save);
        LakeStateAudit.equal("live tags after save",tags,state.rules.tags);
        SaveIO.load(save);
        //Restore the serialized native unit before diagnostics, even if equality reveals a failure.
        pilot.clearUnit();pilot.add();Vars.player=pilot;
        InputHandler.unitControl(pilot,Groups.unit.getByID(controlled));
        if(pilot.unit()==null||pilot.unit().id!=controlled)throw new IllegalStateException("Could not repossess native saved Evoke "+controlled);
        LakeStateAudit.equal("loaded tags",tags,state.rules.tags);
        var loaded=LakeStateAudit.world();
        output.child("checkpoint-"+seconds+"-loaded-world.json").writeString(JsonIO.write(loaded));
        LakeStateAudit.equal("loaded world",values,loaded);
        //Local players are not serialized. Repossess the very same saved native unit.
        state.set(mindustry.core.GameState.State.playing);
        output.child("checkpoints.txt").writeString("seconds="+seconds+" tags="+tags.size+" worldValues="+values.size+" equal=true controlledUnit="+controlled+" elapsed="+CampaignStars.elapsed()+" budget="+CampaignStars.placed()+" minCore="+CampaignStars.minimumCoreHealth()+"\n",true);
        System.out.println("NATIVE SAVE/LOAD CHECKPOINT PASSED "+seconds+" seconds, "+tags.size+" tags");
    }
    static boolean safeRepair(BuildPlan plan){return !Groups.unit.contains(u->u.team!=pilot.team()&&u.team!=Team.derelict&&u.within(plan.x*tilesize,plan.y*tilesize,30*tilesize));}
    static void command(){
        var core=state.rules.defaultTeam.core();
        if(core==null||pilot.dead())return;
        Unit unit=pilot.unit();
        unit.controlWeapons(false,false);
        if(!planned){plan();planned=true;}
        if(frame%600==0)for(var old:originals)if(old.tile().block()==Blocks.air&&safeRepair(old)&&Build.validPlaceIgnoreUnits(old.block,pilot.team(),old.x,old.y,old.rotation,true,true)&&!queue.contains(p->p.x==old.x&&p.y==old.y))queue.insert(0,old.copy());
        if(frame%300==0){
            var troops=Groups.unit.copy(new Seq<>()).select(u->u.team==pilot.team()&&u.isCommandable()&&!u.spawnedByCore);troops.sort(u->u.id);
            if(troops.size>=15)mainAssault=true;
            var ids=troops.mapInt(u->u.id).toArray();
            InputHandler.setUnitStance(pilot,ids,mindustry.ai.UnitStance.patrol,false);
            InputHandler.setUnitStance(pilot,ids,mindustry.ai.UnitStance.pursueTarget,false);
            if(mainAssault){
                int[][] points={{85,145},{85,255},{138,246},{161,245},{149,234}};
                var point=points[Math.min(navalStage,points.length-1)];
                var staging=new Vec2(point[0]*tilesize,point[1]*tilesize);
                boolean arrived=troops.count(u->u.within(staging,12*tilesize))>=Math.min(12,troops.size);
                if(navalStage<2&&arrived)navalStage++;
                if(navalStage==2&&arrived&&world.build(138,229)==null)navalStage++;
                if(navalStage==3&&arrived&&world.build(161,227)==null)navalStage++;
                Building enemy=null;
                if(navalStage==4&&arrived){
                    var left=world.build(138,229);var right=world.build(161,227);
                    enemy=left!=null&&left.team==state.rules.waveTeam?left:right!=null&&right.team==state.rules.waveTeam?right:Groups.build.find(b->b.block instanceof CoreBlock&&b.team==state.rules.waveTeam);
                }
                if(troops.any()){InputHandler.commandUnits(pilot,ids,enemy,null,enemy==null?staging:null,false,true);commands++;}
                for(int i=0;i<factories.size;i++)if(world.build(factories.get(i))!=null)InputHandler.commandBuilding(pilot,new int[]{factories.get(i)},new Vec2(85*tilesize,145*tilesize));
                output.child("naval-stage.csv").writeString(frame/60f+","+navalStage+","+staging+","+troops.size+","+troops.sumf(u->u.health)+","+enemy+"\n",true);
            }else{
                var guards=troops.copy();guards.truncate(Math.min(3,troops.size));
                var reserve=troops.copy();for(var guard:guards)reserve.remove(guard);
                if(guards.any()){InputHandler.commandUnits(pilot,guards.mapInt(u->u.id).toArray(),null,null,new Vec2(219*tilesize,92*tilesize),false,true);commands++;}
                if(reserve.any()){InputHandler.commandUnits(pilot,reserve.mapInt(u->u.id).toArray(),null,null,new Vec2(248*tilesize,98*tilesize),false,true);commands++;}
                for(int i=0;i<factories.size;i++)if(world.build(factories.get(i))!=null)InputHandler.commandBuilding(pilot,new int[]{factories.get(i)},new Vec2(248*tilesize,98*tilesize));
            }
        }
        unit.mineTile=null;
        if(queue.any()){
            var next=queue.first();
            if(next.tile().block()==next.block&&next.tile().team()==pilot.team()&&(!next.block.rotate||next.tile().build.rotation==next.rotation)){
                queue.remove(0);unit.clearBuilding();return;
            }
            if(!(next.tile().build instanceof ConstructBuild))for(var requirement:next.block.requirements){
                if(core.items.get(requirement.item)<requirement.amount){
                    unit.clearBuilding();
                    if(requirement.item==Items.beryllium||requirement.item==Items.graphite)mine(requirement.item);
                    else{
                        final BuildPlan stalled=next;
                        var affordable=queue.find(p->p!=stalled && Arrays.stream(p.block.requirements).allMatch(c->core.items.get(c.item)>=c.amount));
                        if(affordable!=null){queue.remove(affordable);queue.insert(0,affordable);unit.clearBuilding();return;}
                        service();
                    }
                    return;
                }
            }
            approach(unit,next.drawx(),next.drawy(),unit.type.buildRange*.75f);
            if(unit.plans.isEmpty()){
                if(next.initialized&&next.tile().block()==Blocks.air){next=new BuildPlan(next.x,next.y,next.rotation,next.block);queue.set(0,next);}
                unit.addBuild(next);
            }
            return;
        }
        if(frame%600==0){
            for(var old:originals)if(old.tile().block()==Blocks.air&&safeRepair(old)&&Build.validPlaceIgnoreUnits(old.block,pilot.team(),old.x,old.y,old.rotation,true,true))queue.add(old.copy());
        }
        service();
    }
    static void service(){
        var core=state.rules.defaultTeam.core();var unit=pilot.unit();
        Building target=null;Item item=null;
        var damaged=pilot.team().data().buildings.find(b->b.damaged()&&b.healthf()<0.9f&&b.block!=Blocks.duct&&b.block!=Blocks.reinforcedConduit);
        if(damaged!=null){
            approach(unit,damaged.x,damaged.y,unit.range()*0.6f);
            unit.aim(damaged);unit.controlWeapons(true,true);
            if(frame%60==0)output.child("native-repairs.csv").writeString(frame/60f+","+damaged+","+damaged.health+"\n",true);
            return;
        }
        for(var b:pilot.team().data().buildings){
            if(b instanceof ItemTurretBuild turret&&turret.block==Blocks.breach&&b.allowDeposit()&&turret.totalAmmo<15){target=b;item=Items.beryllium;break;}
        }
        var furnace=world.build(furnacePos);
        if(target==null&&furnace!=null&&furnace.allowDeposit()&&furnace.items.get(Items.graphite)<10&&(core.items.get(Items.graphite)>0||(unit.item()==Items.graphite&&unit.stack.amount>0))){target=furnace;item=Items.graphite;}
        int troops=pilot.team().data().countType(UnitTypes.elude);
        for(int i=0;i<factories.size&&target==null;i++){
            var factory=world.build(factories.get(i));
            if(factory instanceof UnitFactoryBuild&&factory.allowDeposit()){
                if(factory.items.get(Items.silicon)<50&&(core.items.get(Items.silicon)>0||(unit.item()==Items.silicon&&unit.stack.amount>0))){target=factory;item=Items.silicon;}
                else if(factory.items.get(Items.beryllium)<40&&(core.items.get(Items.beryllium)>0||(unit.item()==Items.beryllium&&unit.stack.amount>0))){target=factory;item=Items.beryllium;}
            }
        }
        if(unit.stack.amount>0){
            var receiver=target!=null&&unit.item()==item?target:core;
            approach(unit,receiver.x,receiver.y,itemTransferRange*.6f);
            if(pilot.within(receiver,itemTransferRange)&&frame/60f>=depositReady){
                InputHandler.transferInventory(pilot,receiver);deposits++;depositReady=frame/60f+state.rules.itemDepositCooldown;
            }
        }else if(target!=null){
            approach(unit,core.x,core.y,itemTransferRange*.6f);
            if(pilot.within(core,itemTransferRange))InputHandler.requestItem(pilot,core,item,unit.type.itemCapacity);
        }else if(core.items.get(Items.beryllium)<500)mine(Items.beryllium);
        else unit.moveAt(motion.setZero());
    }
    static void mine(Item item){
        var unit=pilot.unit();var core=pilot.team().core();
        if(unit.stack.amount>0&&(unit.item()!=item||unit.stack.amount>=unit.type.itemCapacity)){
            approach(unit,core.x,core.y,itemTransferRange*.6f);
            if(pilot.within(core,itemTransferRange)&&frame/60f>=depositReady){InputHandler.transferInventory(pilot,core);deposits++;depositReady=frame/60f+state.rules.itemDepositCooldown;}
            return;
        }
        Tile best=null;float distance=Float.POSITIVE_INFINITY;
        for(var tile:world.tiles){
            if(tile.wallDrop()!=item||!unit.validMine(tile,false)||(state.rules.limitMapArea&&(tile.x<state.rules.limitX||tile.y<state.rules.limitY||tile.x>=state.rules.limitX+state.rules.limitWidth||tile.y>=state.rules.limitY+state.rules.limitHeight)))continue;
            if(unit.dst2(tile.worldx(),tile.worldy())<distance){best=tile;distance=unit.dst2(tile.worldx(),tile.worldy());}
        }
        if(best==null)throw new IllegalStateException("No visible mineable wall for "+item);
        approach(unit,best.worldx(),best.worldy(),unit.type.mineRange*.6f);
        unit.mineTile=unit.validMine(best)?best:null;
    }
    static void approach(Unit unit,float x,float y,float distance){
        motion.set(x-unit.x,y-unit.y);
        unit.moveAt(motion.len()<=distance?motion.setZero():motion.setLength(unit.speed()));
    }
    static boolean free(Block block,int x,int y,int rotation){
        if(!CampaignStars.available(block)||!Build.validPlaceIgnoreUnits(block,pilot.team(),x,y,rotation,true,true))return false;
        int low=(block.size-1)/2;
        for(int dx=0;dx<block.size;dx++)for(int dy=0;dy<block.size;dy++)if(reserved.contains(Point2.pack(x-low+dx,y-low+dy)))return false;
        return true;
    }
    static void add(BuildPlan plan){
        queue.add(plan);originals.add(plan.copy());int low=(plan.block.size-1)/2;
        if(plan.block.outputsItems()&&plan.block!=Blocks.duct)producers.add(plan);
        for(int dx=0;dx<plan.block.size;dx++)for(int dy=0;dy<plan.block.size;dy++)reserved.add(Point2.pack(plan.x-low+dx,plan.y-low+dy));
        output.child("actions.txt").writeString("plan "+plan.block.name+" "+plan.x+","+plan.y+" r="+plan.rotation+"\n",true);
    }
    static void markGrid(BuildPlan plan){
        int low=(plan.block.size-1)/2;
        for(int dx=0;dx<plan.block.size;dx++)for(int dy=0;dy<plan.block.size;dy++)grid.add(Point2.pack(plan.x-low+dx,plan.y-low+dy));
    }
    static void power(BuildPlan plan){
        if(!plan.block.hasPower)return;
        if(grid.isEmpty()){markGrid(plan);return;}
        int width=world.width();int[] previous=new int[width*world.height()];Arrays.fill(previous,-2);
        ArrayDeque<Integer> open=new ArrayDeque<>();
        for(var tile:perimeter(plan))if(free(Blocks.beamNode,tile.x,tile.y,0)){int index=tile.y*width+tile.x;previous[index]=-1;open.add(index);}
        int end=-1;
        while(!open.isEmpty()&&end==-1){
            int index=open.removeFirst();Tile tile=world.tile(index%width,index/width);
            for(int dir=0;dir<4&&end==-1;dir++)for(int step=1;step<=10;step++){
                Tile next=world.tile(tile.x+Geometry.d4x(dir)*step,tile.y+Geometry.d4y(dir)*step);if(next==null)break;
                if(grid.contains(next.pos())){end=index;break;}
                int nextIndex=next.y*width+next.x;
                if(previous[nextIndex]==-2&&free(Blocks.beamNode,next.x,next.y,0)){previous[nextIndex]=index;open.add(nextIndex);}
            }
        }
        if(end<0)throw new IllegalStateException("No native node path to "+plan);
        Seq<Integer> path=new Seq<>();for(int p=end;p>=0;p=previous[p])path.add(p);
        for(int p:path){var node=new BuildPlan(p%width,p/width,0,Blocks.beamNode);add(node);markGrid(node);}
        markGrid(plan);
    }
    static Seq<Tile> perimeter(BuildPlan plan){
        Seq<Tile> result=new Seq<>();int low=(plan.block.size-1)/2;
        for(int dx=-1;dx<=plan.block.size;dx++)for(int dy=-1;dy<=plan.block.size;dy++){
            if((dx==-1||dx==plan.block.size)==(dy==-1||dy==plan.block.size))continue;
            var tile=world.tile(plan.x-low+dx,plan.y-low+dy);if(tile!=null)result.add(tile);
        }
        return result;
    }
    static boolean footprint(BuildPlan plan,Tile tile){int low=(plan.block.size-1)/2;return tile.x>=plan.x-low&&tile.x<plan.x-low+plan.block.size&&tile.y>=plan.y-low&&tile.y<plan.y-low+plan.block.size;}
    static boolean routeTile(Tile tile,BuildPlan from,BuildPlan to){
        if(!free(Blocks.duct,tile.x,tile.y,0))return false;
        //A native drill/crusher dumps into any accepted neighbouring pipe; keep unrelated feeds apart.
        for(var p:producers){
            if(p==from||p==to)continue;
            //Cliff crushers all produce sand; joining their neighbour feeds is intentional.
            if(from.block==Blocks.cliffCrusher&&p.block==Blocks.cliffCrusher)continue;
            for(var edge:perimeter(p))if(edge==tile)return false;
        }
        return true;
    }
    static void route(BuildPlan from,BuildPlan to){
        int width=world.width();int[] previous=new int[width*world.height()];Arrays.fill(previous,-2);ArrayDeque<Integer> open=new ArrayDeque<>();
        for(var tile:perimeter(from))if(routeTile(tile,from,to)&&(from.block!=Blocks.ductRouter||from.tile().relativeTo(tile)!=Mathf.mod(from.rotation+2,4))){int index=tile.y*width+tile.x;previous[index]=-1;open.add(index);}
        int end=-1,target=-1;
        while(!open.isEmpty()&&end<0){
            int index=open.removeFirst();Tile tile=world.tile(index%width,index/width);
            for(int dir=0;dir<4;dir++){
                var next=tile.nearby(dir);if(next==null)continue;
                if(footprint(to,next)){end=index;target=next.y*width+next.x;break;}
                int ni=next.y*width+next.x;if(previous[ni]==-2&&routeTile(next,from,to)){previous[ni]=index;open.add(ni);}
                if(!routeTile(next,from,to)){
                    boolean crossedPipe=false;
                    for(int length=1;length<=4;length++){
                        var hop=world.tile(tile.x+Geometry.d4x(dir)*length,tile.y+Geometry.d4y(dir)*length);
                        if(hop==null||hop.solid()||footprint(from,hop)||footprint(to,hop))break;
                        var plannedBlock=originals.find(p->footprint(p,hop));
                        if(plannedBlock!=null&&plannedBlock.block!=Blocks.duct&&plannedBlock.block!=Blocks.beamNode)break;
                        if(plannedBlock==null&&hop.build!=null&&hop.block()!=Blocks.duct&&hop.block()!=Blocks.beamNode)break;
                        if(length>1&&crossedPipe&&routeTile(hop,from,to)){
                            int hi=hop.y*width+hop.x;
                            if(previous[hi]==-2){previous[hi]=index;open.add(hi);}break;
                        }
                        if((plannedBlock!=null&&plannedBlock.block==Blocks.duct)||(hop.build!=null&&hop.block()==Blocks.duct))crossedPipe=true;
                    }
                }
            }
        }
        if(end<0)throw new IllegalStateException("No duct route "+from+" -> "+to);
        Seq<Integer> path=new Seq<>();for(int p=end;p>=0;p=previous[p])path.add(p);path.reverse();
        for(int i=0;i<path.size;i++){
            int p=path.get(i),q=i+1<path.size?path.get(i+1):target;Tile tile=world.tile(p%width,p/width),next=world.tile(q%width,q/width);
            boolean jump=Math.abs(tile.x-next.x)+Math.abs(tile.y-next.y)>1;
            boolean landed=i>0&&Math.abs(tile.x-path.get(i-1)%width)+Math.abs(tile.y-path.get(i-1)/width)>1;
            int direction=Math.abs(next.x-tile.x)>Math.abs(next.y-tile.y)?(next.x>tile.x?0:2):(next.y>tile.y?1:3);
            var pipe=new BuildPlan(tile.x,tile.y,direction,jump||landed?Blocks.ductBridge:Blocks.duct);
            add(pipe);if(i==0)outputPipes.put(from,pipe);
        }
    }
    static BuildPlan nearest(Block block,int x,int y){
        BuildPlan result=null;float distance=Float.POSITIVE_INFINITY;
        for(var tile:world.tiles)for(int r=0;r<(block.rotate?4:1);r++){
            if(Math.abs(tile.x-x)>30||Math.abs(tile.y-y)>30||!free(block,tile.x,tile.y,r))continue;
            float d=(tile.x-x)*(tile.x-x)+(tile.y-y)*(tile.y-y);
            if(d<distance){result=new BuildPlan(tile.x,tile.y,r,block);distance=d;}
        }
        if(result==null)throw new IllegalStateException("No placement for "+block+" near "+x+","+y);
        return result;
    }
    static BuildPlan bore(Item item,BuildPlan target){
        var block=(BeamDrill)Blocks.plasmaBore;BuildPlan result=null;float score=-Float.MAX_VALUE;
        for(var tile:world.tiles){
            if((state.rules.limitMapArea&&(tile.x<state.rules.limitX||tile.y<state.rules.limitY||tile.x>=state.rules.limitX+state.rules.limitWidth||tile.y>=state.rules.limitY+state.rules.limitHeight)))continue;
            for(int r=0;r<4;r++){
                if(!free(block,tile.x,tile.y,r))continue;
                int count=0;boolean other=false;
                for(int i=0;i<block.size;i++){
                    var start=new Point2();block.nearbySide(tile.x,tile.y,r,i,start);
                    for(int j=0;j<block.range;j++){var wall=world.tile(start.x+Geometry.d4x(r)*j,start.y+Geometry.d4y(r)*j);if(wall!=null&&wall.solid()){if(wall.wallDrop()==item)count++;else if(wall.wallDrop()!=null)other=true;break;}}
                }
                float value=count*100-(float)Math.hypot(tile.x-target.x,tile.y-target.y);
                if(count>0&&!other&&value>score){result=new BuildPlan(tile.x,tile.y,r,block);score=value;}
            }
        }
        if(result==null)throw new IllegalStateException("No "+item+" beam bore");
        add(result);
        //Keep the actual mining laser rays open when placing later structures.
        for(int i=0;i<block.size;i++){
            var start=new Point2();block.nearbySide(result.x,result.y,result.rotation,i,start);
            for(int j=0;j<block.range;j++){
                var tile=world.tile(start.x+Geometry.d4x(result.rotation)*j,start.y+Geometry.d4y(result.rotation)*j);
                if(tile==null||tile.solid())break;
                reserved.add(tile.pos());
            }
        }
        route(result,target);power(result);return result;
    }
    static void fork(BuildPlan producer,BuildPlan receiver){
        //Reuse a pipe that actually leaves this producer, preserving the native router input orientation.
        BuildPlan pipe=outputPipes.get(producer);
        if(pipe==null)throw new IllegalStateException("No native output pipe to fork: "+producer);
        int facing=Edges.getFacingEdge(producer.block,producer.x,producer.y,pipe.tile()).relativeTo(pipe.tile());
        var router=new BuildPlan(pipe.x,pipe.y,facing,Blocks.ductRouter);
        final int x=pipe.x,y=pipe.y;
        queue.set(queue.indexOf(p->p.x==x&&p.y==y),router);originals.set(originals.indexOf(p->p.x==x&&p.y==y),router.copy());
        route(router,receiver);
    }
    static BuildPlan splitNative(int x,int y,BuildPlan receiver){
        var original=world.build(x,y);
        if(original==null||original.block!=Blocks.duct)throw new IllegalStateException("Expected native feed at "+x+","+y);
        var router=new BuildPlan(x,y,original.rotation,Blocks.ductRouter);
        queue.add(router);originals.add(router.copy());reserved.add(router.tile().pos());
        route(router,receiver);return router;
    }
    static void splitPlanned(int x,int y,BuildPlan receiver){
        var pipe=originals.find(p->p.x==x&&p.y==y);
        if(pipe==null||pipe.block!=Blocks.duct)throw new IllegalStateException("Expected planned plain feed at "+x+","+y);
        var router=new BuildPlan(x,y,pipe.rotation,Blocks.ductRouter);
        queue.set(queue.indexOf(p->p.x==x&&p.y==y),router);originals.set(originals.indexOf(p->p.x==x&&p.y==y),router.copy());
        route(router,receiver);
    }
    static void liquidRoute(BuildPlan from,BuildPlan to){
        int width=world.width();int[] previous=new int[width*world.height()];Arrays.fill(previous,-2);ArrayDeque<Integer> open=new ArrayDeque<>();
        for(var tile:perimeter(from))if(free(Blocks.reinforcedConduit,tile.x,tile.y,0)){int n=tile.y*width+tile.x;previous[n]=-1;open.add(n);}
        int end=-1,target=-1;
        while(!open.isEmpty()&&end<0){
            int n=open.removeFirst();var tile=world.tile(n%width,n/width);
            for(int dir=0;dir<4;dir++){
                var next=tile.nearby(dir);if(next==null)continue;
                if(footprint(to,next)){end=n;target=next.y*width+next.x;break;}
                int ni=next.y*width+next.x;
                if(previous[ni]==-2&&free(Blocks.reinforcedConduit,next.x,next.y,0)){previous[ni]=n;open.add(ni);}
            }
        }
        if(end<0)throw new IllegalStateException("No native water route "+from+" -> "+to);
        Seq<Integer> path=new Seq<>();for(int n=end;n>=0;n=previous[n])path.add(n);path.reverse();
        for(int i=0;i<path.size;i++){
            int n=path.get(i),q=i+1<path.size?path.get(i+1):target;
            int dir=world.tile(n%width,n/width).relativeTo(world.tile(q%width,q/width));
            add(new BuildPlan(n%width,n/width,dir,Blocks.reinforcedConduit));
        }
    }
    static void plan(){
        output.child("actions.txt").writeString("Native Lake production driver; inherited actual Onset/Aegis five-star chain only; no resource/goal/wave/unit writes\n");
        var core=pilot.team().core();var corePlan=new BuildPlan(core.tileX(),core.tileY(),0,core.block);
        var originalGraph=world.build(246,88).power.graph;
        for(var b:pilot.team().data().buildings){
            var p=new BuildPlan(b.tileX(),b.tileY(),b.rotation,b.block);int low=(p.block.size-1)/2;
            for(int dx=0;dx<p.block.size;dx++)for(int dy=0;dy<p.block.size;dy++)reserved.add(Point2.pack(p.x-low+dx,p.y-low+dy));
            if(b.block==Blocks.plasmaBore){
                producers.add(p);var drill=(BeamDrill)b.block;
                for(int i=0;i<drill.size;i++){var ray=new Point2();drill.nearbySide(p.x,p.y,p.rotation,i,ray);for(int j=0;j<drill.range;j++){var t=world.tile(ray.x+Geometry.d4x(p.rotation)*j,ray.y+Geometry.d4y(p.rotation)*j);if(t==null||t.solid())break;reserved.add(t.pos());}}
            }
            if(b.power!=null&&b.power.graph==originalGraph)markGrid(p);
            if(b.block==Blocks.turbineCondenser||b.block==Blocks.cliffCrusher||b.block==Blocks.siliconArcFurnace||b.block==Blocks.plasmaBore||b.block==Blocks.beamNode||b.block==Blocks.breach)originals.add(p.copy());
        }
        for(int[] p:new int[][]{{248,98},{219,92},{85,145},{85,255},{138,246},{161,245},{149,234}})if(world.tile(p[0],p[1]).solid())throw new IllegalStateException("Native rally point is solid: "+Arrays.toString(p));
        furnacePos=Point2.pack(232,88);
        int[][] rearCoolant={{248,88,0},{249,88,0},{250,88,3},{250,87,3},{250,86,0}};
        for(var xyz:rearCoolant){var pipe=new BuildPlan(xyz[0],xyz[1],xyz[2],Blocks.reinforcedConduit);if(!free(pipe.block,pipe.x,pipe.y,pipe.rotation))throw new IllegalStateException("Native rear coolant placement blocked: "+pipe);add(pipe);}
        var vent=nearest(Blocks.turbineCondenser,224,106);add(vent);power(vent);
        int[][] pipes={{224,104,3},{224,103,0},{225,103,0},{226,103,0},{227,103,1},{227,104,1},{227,105,1},{227,106,1},{227,107,1},{227,108,1},{227,109,1},{227,110,1},{227,111,2},{226,111,2},{225,111,2}};
        for(var xyz:pipes){var pipe=new BuildPlan(xyz[0],xyz[1],xyz[2],Blocks.reinforcedConduit);if(!free(pipe.block,pipe.x,pipe.y,pipe.rotation))throw new IllegalStateException("Native coolant placement blocked: "+pipe);add(pipe);}
        var furnacePlan=new BuildPlan(232,88,0,Blocks.siliconArcFurnace);
        for(int i=0;i<2;i++){
            var crusher=nearest(Blocks.cliffCrusher,216,94+i*2);add(crusher);route(crusher,furnacePlan);power(crusher);
        }
        var firstFactory=nearest(Blocks.shipFabricator,244,75);firstFactory.rotation=1;add(firstFactory);for(int dx=-1;dx<=1;dx++)reserved.add(Point2.pack(firstFactory.x+dx,firstFactory.y+2));power(firstFactory);factoryPos=Point2.pack(firstFactory.x,firstFactory.y);factories.add(factoryPos);
        var siliconFeed=splitNative(233,86,firstFactory);
        //This native beryllium router side-loads an ordinary duct below it. A real router
        //accepting the silicon bridge from the west isolates that input direction.
        var contaminated=originals.find(p->p.x==242&&p.y==78);
        if(contaminated==null||contaminated.block!=Blocks.duct)throw new IllegalStateException("Expected silicon exit below native beryllium router");
        var isolated=new BuildPlan(242,78,0,Blocks.ductRouter);
        queue.set(queue.indexOf(p->p.x==242&&p.y==78),isolated);
        originals.set(originals.indexOf(p->p.x==242&&p.y==78),isolated.copy());
        output.child("actions.txt").writeString("replace planned duct242,78 with real west-input duct-router to prevent native beryllium side-input\n",true);
        var originalGraphite=world.build(235,90);
        var graphiteFeed=new BuildPlan(originalGraphite.tileX(),originalGraphite.tileY(),originalGraphite.rotation,originalGraphite.block);route(graphiteFeed,firstFactory);
        System.out.println("planned="+queue.size+" factories="+factories+" limits="+CampaignStars.limits(SectorPresets.lake));
    }
    static void armySample(){
        for(var u:pilot.team().data().units)if(u.isCommandable())output.child("assault.csv").writeString(frame/60f+","+u.id+","+u.x/tilesize+","+u.y/tilesize+","+u.health+","+u.command().attackTarget+","+u.command().targetPos+","+u.isShooting+","+u.command().hasStance(mindustry.ai.UnitStance.patrol)+","+u.command().hasStance(mindustry.ai.UnitStance.pursueTarget)+"\n",true);
    }
    static void snapshot(){
        var core=state.rules.defaultTeam.core();
        String row=frame/60f+","+((System.nanoTime()-started)/1e9f)+","+state.wave+","+state.enemies+","+(core==null?0:core.healthf())+","+(core==null?0:core.items.get(Items.beryllium))+","+(core==null?0:core.items.get(Items.graphite))+","+(core==null?0:core.items.get(Items.silicon))+","+(core==null?0:core.items.get(Items.tungsten))+","+built+","+state.rules.defaultTeam.data().units.size+","+state.rules.objectives.all.count(o->o.isCompleted())+",\""+state.rules.objectiveFlags+"\"\n";
        output.child("trial.csv").writeString(row,true);System.out.print(row);
        output.child("task-status.csv").writeString(frame/60f+",\""+CampaignStars.status().replace("\n"," | ")+"\"\n",true);
        output.child("combat.csv").writeString(frame/60f+","+CampaignStars.elapsed()+","+CampaignStars.minimumCoreHealth()+","+state.stats.buildingsDestroyed+","+pilot.team().data().countType(UnitTypes.elude)+","+Groups.build.count(b->b.block instanceof CoreBlock&&b.team==state.rules.waveTeam)+"\n",true);
        for(var b:pilot.team().cores())output.child("core-health.csv").writeString(frame/60f+","+b.tileX()+","+b.tileY()+","+b.health+"\n",true);
        for(var u:pilot.team().data().units)if(u.isCommandable())output.child("army.csv").writeString(frame/60f+","+u.id+","+u.x/tilesize+","+u.y/tilesize+","+u.health+","+u.command().attackTarget+","+u.command().targetPos+"\n",true);
        System.out.println("running objectives="+state.rules.objectives.all.select(o->!o.isCompleted()&&o.dependencyFinished()).map(o->o.getClass().getSimpleName()+":"+o.text()+":"+o.details()));
        StringBuilder diagnostic=new StringBuilder("seconds="+frame/60f+" pilot="+(pilot.unit()==null?"no controlled unit":pilot.unit().x/tilesize+","+pilot.unit().y/tilesize+" cargo="+pilot.unit().stack)+" onlyDepositCore="+state.rules.onlyDepositCore+" pending="+(queue.any()?queue.first():"none")+"\n");
        for(var b:pilot.team().data().buildings)if(b.block==Blocks.impactDrill||b.block==Blocks.plasmaBore||b.block==Blocks.cliffCrusher||b.block==Blocks.siliconArcFurnace||(b.block==Blocks.tankFabricator||b.block==Blocks.shipFabricator))diagnostic.append(b.block.name).append(" ").append(b.tileX()).append(',').append(b.tileY()).append(" eff=").append(b.efficiency).append(" enabled=").append(b.enabled).append(" items=").append(b.items).append(" power=").append(b.power==null?"none":b.power.status+" produced="+b.power.graph.getLastPowerProduced()+" need="+b.power.graph.getLastPowerNeeded()).append(b instanceof UnitFactoryBuild f?" payload="+f.payload+" front="+f.front()+" progress="+f.progress:"").append('\n');
        output.child("diagnostics.txt").writeString(diagnostic.toString(),true);
    }
    static void result(String scope){
        output.child("result.txt").writeString("mission=lake\nmode="+mode+"\nscope="+scope+"\ngateVerified="+gate+"\nfinished="+over+"\nwinner="+winner+"\nsimSeconds="+frame/60f+"\nbuilt="+built+"\ncommands="+commands+"\ndeposits="+deposits+"\npendingPlans="+queue.size+"\nnext="+(queue.any()?queue.first():"none")+"\nstars="+CampaignStars.stars(SectorPresets.lake,mode)+"\nminimumCore="+CampaignStars.minimumCoreHealth()+"\nlosses="+state.stats.buildingsDestroyed+"\nelapsed="+CampaignStars.elapsed()+"\nnormalBefore="+scoresBefore[0]+"\nheroBefore="+scoresBefore[1]+"\ntacticalBefore="+scoresBefore[2]+"\nnormalAfter="+CampaignStars.stars(SectorPresets.lake,Mode.NORMAL)+"\ntacticalAfter="+CampaignStars.stars(SectorPresets.lake,Mode.TACTICAL)+"\nearned="+state.rules.tags.get("stars.earned")+"\nruntimeWatchVersion="+WatchCampaign.version+"\nobjectives="+state.rules.objectives.all.count(o->o.isCompleted())+"/"+state.rules.objectives.all.size+"\nflags="+state.rules.objectiveFlags+"\nstatus="+CampaignStars.status()+"\nclassLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\n");
    }
}

