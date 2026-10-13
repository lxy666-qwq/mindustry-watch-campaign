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

/** Dedicated Aegis inspection/driver. Never changes resources, goals, waves, units or victory. */
public class Aegis033Trial{
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
    static final IntSet committed = new IntSet();
    static final int[] scoresBefore = new int[3];
    public static void main(String[] args) throws Exception{
        String action=args[0]; output=new Fi(args[1]); mode=args.length>2?Mode.valueOf(args[2]):Mode.NORMAL;
        int seconds=args.length>3?Integer.parseInt(args[3]):1800;
        output.mkdirs();
        try(var embedded=Aegis033Trial.class.getClassLoader().getResourceAsStream("watch-version.txt")){
            output.child("loaded-watch-version.txt").writeBytes(embedded.readAllBytes());
        }
        Fi profile=output.child("profile");
        if(profile.exists()) throw new IllegalArgumentException("Require a fresh independent profile: "+profile);
        profile.mkdirs();
        new Fi("local-work/onset-trials/heroic-20-v033/profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/aegis-033-work/Aegis033Trial.java").copyTo(output.child("driver-source.java"));
        new Fi("local-work/aegis-033-work/AegisStateAudit.java").copyTo(output.child("state-audit-source.java"));
        started=System.nanoTime(); Log.useColors=false;
        Core.app=new MockApplication(); Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?output.child("loaded-watch-version.txt"):new Fi(System.getProperty("trial.assets","local-work/mindustry-star-work/star033-runtime/assets")).child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};
        Core.audio=new MockAudio(); Core.input=new MockInput(); Core.settings=new Settings();
        Core.settings.setDataDirectory(profile); Core.settings.setJson(JsonIO.json);
        headless=true; net=new Net(null); Vars.init(); Vars.loadSettings();
        logicVars=new GlobalVars(){@Override public void update(){var local=Vars.player; Vars.player=null; try{super.update();}finally{Vars.player=local;}}};
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent(); logic=new Logic(); netServer=new NetServer(); content.init();
        Fi runtimeCode=new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        output.child("runtime.txt").writeString("watchVersion="+WatchCampaign.version+"\nactualClassLocation="+runtimeCode+"\nactualCodeLocationModified="+runtimeCode.lastModified()+"\nprofile="+profile+"\nfixedDelta=1/60 native tick; no wave skip\n");
        for(var savedMode:Mode.values())scoresBefore[savedMode.ordinal()]=CampaignStars.stars(SectorPresets.aegis,savedMode);
        output.child("inherited-onset.txt").writeString("NORMAL="+CampaignStars.stars(SectorPresets.onset,Mode.NORMAL)+"\nHEROIC="+CampaignStars.stars(SectorPresets.onset,Mode.HEROIC)+"\nTACTICAL="+CampaignStars.stars(SectorPresets.onset,Mode.TACTICAL)+"\n");
        output.child("scores-before.txt").writeString("NORMAL="+scoresBefore[0]+"\nHEROIC="+scoresBefore[1]+"\nTACTICAL="+scoresBefore[2]+"\nsource=local-work/onset-trials/heroic-20-v033/profile/settings.bin\n");
        gate=CampaignStars.unlocked(SectorPresets.aegis)&&CampaignProgression.allowed(SectorPresets.aegis,mode)&&(mode==Mode.NORMAL||CampaignStars.normalComplete(SectorPresets.aegis));
        if(!gate) throw new IllegalStateException("Native starting-map gate closed: "+mode);
        CampaignStars.loadMission(SectorPresets.aegis,mode);
        Events.on(GameOverEvent.class,e->{over=true;winner=e.winner;});
        Events.on(BlockBuildEndEvent.class,e->{if(e.team==state.rules.defaultTeam&&!e.breaking)built++;});
        pilot=Player.create(); pilot.name="Native Aegis tutorial trial"; pilot.team(state.rules.defaultTeam); Vars.player=pilot; pilot.add();
        logic.play(); CoreBlock.playerSpawn(state.rules.defaultTeam.core().tile,pilot);
        output.child("mission-limits.txt").writeString("limits="+CampaignStars.limits(SectorPresets.aegis)+"\nrulesDeadline="+state.rules.tags.get("stars.deadline")+"\nrulesLossBudget="+state.rules.tags.get("stars.lossBudget")+"\n");
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
        output.child("final-world.json").writeString(JsonIO.write(AegisStateAudit.world()));
        SaveIO.write(output.child("end.msav"));
        output.child("save-status.txt").writeString("end.msav SaveIO.write completed; fresh-process readback pending\n");
        System.exit(over&&winner==state.rules.defaultTeam?0:2);
    }
    static void checkpoint(int seconds){
        var tags=new StringMap();tags.putAll(state.rules.tags);
        var values=AegisStateAudit.world();int controlled=pilot.unit().id;
        output.child("checkpoint-"+seconds+"-tags.json").writeString(JsonIO.write(tags));
        output.child("checkpoint-"+seconds+"-world.json").writeString(JsonIO.write(values));
        Fi save=output.child("checkpoint-"+seconds+".msav");
        SaveIO.write(save);
        AegisStateAudit.equal("live tags after save",tags,state.rules.tags);
        SaveIO.load(save);
        //Restore the serialized native unit before diagnostics, even if equality reveals a failure.
        pilot.clearUnit();pilot.add();Vars.player=pilot;
        InputHandler.unitControl(pilot,Groups.unit.getByID(controlled));
        if(pilot.unit()==null||pilot.unit().id!=controlled)throw new IllegalStateException("Could not repossess native saved Evoke "+controlled);
        AegisStateAudit.equal("loaded tags",tags,state.rules.tags);
        var loaded=AegisStateAudit.world();
        output.child("checkpoint-"+seconds+"-loaded-world.json").writeString(JsonIO.write(loaded));
        AegisStateAudit.equal("loaded world",values,loaded);
        //Local players are not serialized. Repossess the very same saved native unit.
        state.set(mindustry.core.GameState.State.playing);
        output.child("checkpoints.txt").writeString("seconds="+seconds+" tags="+tags.size+" worldValues="+values.size+" equal=true controlledUnit="+controlled+" elapsed="+CampaignStars.elapsed()+" budget="+CampaignStars.placed()+" minCore="+CampaignStars.minimumCoreHealth()+"\n",true);
        System.out.println("NATIVE SAVE/LOAD CHECKPOINT PASSED "+seconds+" seconds, "+tags.size+" tags");
    }
    static void command(){
        var core=state.rules.defaultTeam.core();
        if(core==null||pilot.dead())return;
        Unit unit=pilot.unit();
        if(!planned){plan();planned=true;}
        if(frame%300==0){
            var troops=Groups.unit.copy(new Seq<>()).select(u->u.team==pilot.team()&&u.isCommandable()&&!u.spawnedByCore);
            Building enemy=Groups.build.find(b->b.block instanceof CoreBlock&&b.team!=pilot.team()&&b.team!=Team.derelict&&(!state.rules.limitMapArea||b.tileY()<state.rules.limitY+state.rules.limitHeight));
            var turret=Groups.build.copy(new Seq<>()).select(b->b.block.category==Category.turret&&b.team!=pilot.team()&&b.team!=Team.derelict&&(!state.rules.limitMapArea||b.tileY()<state.rules.limitY+state.rules.limitHeight)).min(b->b.dst2(176*tilesize,108*tilesize));
            if(turret!=null)enemy=turret;
            if(enemy!=null&&troops.size>=15)mainAssault=true;
            boolean assault=enemy!=null&&mainAssault;
            Vec2 destination=assault?new Vec2(enemy.x,enemy.y):new Vec2(150*tilesize,155*tilesize);
            if(!troops.isEmpty()){InputHandler.commandUnits(pilot,troops.mapInt(u->u.id).toArray(),assault?enemy:null,null,assault?null:destination,false,true);commands++;}
            for(int i=0;i<factories.size;i++){int pos=factories.get(i);if(world.build(pos)!=null)InputHandler.commandBuilding(pilot,new int[]{pos},destination);}
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
                    else service();
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
            for(var old:originals)if(old.tile().block()==Blocks.air&&Build.validPlaceIgnoreUnits(old.block,pilot.team(),old.x,old.y,old.rotation,true,true))queue.add(old.copy());
        }
        service();
    }
    static void service(){
        var core=state.rules.defaultTeam.core();var unit=pilot.unit();
        Building target=null;Item item=null;
        for(var b:pilot.team().data().buildings){
            if(b instanceof ItemTurretBuild turret&&turret.block==Blocks.breach&&b.allowDeposit()&&turret.totalAmmo<15){target=b;item=Items.beryllium;break;}
        }
        var furnace=world.build(furnacePos);
        if(target==null&&furnace!=null&&furnace.allowDeposit()&&furnace.items.get(Items.graphite)<10&&(core.items.get(Items.graphite)>0||(unit.item()==Items.graphite&&unit.stack.amount>0))){target=furnace;item=Items.graphite;}
        int troops=pilot.team().data().countType(UnitTypes.stell);
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
            if(tile.wallDrop()!=item||!unit.validMine(tile,false)||tile.x<state.rules.limitX||tile.y<state.rules.limitY||tile.x>=state.rules.limitX+state.rules.limitWidth||tile.y>=state.rules.limitY+state.rules.limitHeight)continue;
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
                        if(length>1&&crossedPipe&&routeTile(hop,from,to)){
                            int hi=hop.y*width+hop.x;
                            if(previous[hi]==-2){previous[hi]=index;open.add(hi);}break;
                        }
                        if(plannedBlock!=null&&plannedBlock.block==Blocks.duct)crossedPipe=true;
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
            if(tile.x<state.rules.limitX||tile.y<state.rules.limitY||tile.x>=state.rules.limitX+state.rules.limitWidth||tile.y>=state.rules.limitY+state.rules.limitHeight)continue;
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
        output.child("actions.txt").writeString("Native Aegis production driver; inherited true Onset 3/1/1 only; no resource/goal/wave/unit writes\n");
        var core=pilot.team().core();var corePlan=new BuildPlan(core.tileX(),core.tileY(),0,core.block);
        var initialGenerator=world.build(135,115);var originalGraph=initialGenerator.power.graph;
        for(var b:pilot.team().data().buildings){
            var p=new BuildPlan(b.tileX(),b.tileY(),b.rotation,b.block);
            int low=(p.block.size-1)/2;
            for(int dx=0;dx<p.block.size;dx++)for(int dy=0;dy<p.block.size;dy++)reserved.add(Point2.pack(p.x-low+dx,p.y-low+dy));
            if(b.block==Blocks.plasmaBore){
                producers.add(p);var drill=(BeamDrill)b.block;
                for(int i=0;i<drill.size;i++){var start=new Point2();drill.nearbySide(p.x,p.y,p.rotation,i,start);for(int j=0;j<drill.range;j++){var t=world.tile(start.x+Geometry.d4x(p.rotation)*j,start.y+Geometry.d4y(p.rotation)*j);if(t==null||t.solid())break;reserved.add(t.pos());}}
            }
            if(b.power!=null&&b.power.graph==originalGraph)markGrid(p);
        }
        var graphiteGenerator=world.build(209,73);var graphiteGraph=graphiteGenerator.power.graph;
        power(new BuildPlan(graphiteGenerator.tileX(),graphiteGenerator.tileY(),0,graphiteGenerator.block));
        for(var b:pilot.team().data().buildings)if(b.power!=null&&b.power.graph==graphiteGraph)markGrid(new BuildPlan(b.tileX(),b.tileY(),b.rotation,b.block));
        for(int[] pos:new int[][]{{169,129},{191,127}}){var vent=nearest(Blocks.turbineCondenser,pos[0],pos[1]);add(vent);power(vent);}
        var furnace=nearest(Blocks.siliconArcFurnace,195,112);add(furnace);power(furnace);furnacePos=Point2.pack(furnace.x,furnace.y);
        splitNative(195,102,furnace);
        for(int i=0;i<3;i++){var crusher=nearest(Blocks.cliffCrusher,furnace.x,furnace.y);add(crusher);route(crusher,furnace);power(crusher);}
        route(furnace,corePlan);
        var firstFactory=nearest(Blocks.tankFabricator,180,126);add(firstFactory);power(firstFactory);factoryPos=Point2.pack(firstFactory.x,firstFactory.y);factories.add(factoryPos);
        fork(furnace,firstFactory);
        var siliconFeed=originals.find(p->p.block==Blocks.ductRouter&&p.x==outputPipes.get(furnace).x&&p.y==outputPipes.get(furnace).y);
        var berylliumFeed=splitNative(174,105,firstFactory);
        var secondFactory=nearest(Blocks.tankFabricator,185,128);secondFactory.rotation=1;add(secondFactory);for(int dx=-1;dx<=1;dx++)reserved.add(Point2.pack(secondFactory.x+dx,secondFactory.y+2));power(secondFactory);factories.add(Point2.pack(secondFactory.x,secondFactory.y));
        splitPlanned(186,125,secondFactory);splitPlanned(178,123,secondFactory);
        var gun=nearest(Blocks.breach,174,150);add(gun);
        BuildPlan drill=null;float best=-Float.MAX_VALUE;
        for(var tile:world.tiles){if(!free(Blocks.impactDrill,tile.x,tile.y,0))continue;int count=0;for(var t:tile.getLinkedTilesAs(Blocks.impactDrill,new Seq<>()))if(t.drop()==Items.tungsten)count++;float score=count*100-Mathf.dst(tile.x,tile.y,135,115);if(count>0&&score>best){drill=new BuildPlan(tile.x,tile.y,0,Blocks.impactDrill);best=score;}}
        if(drill==null)throw new IllegalStateException("No accessible native tungsten deposit");
        add(drill);power(drill);liquidRoute(new BuildPlan(135,115,0,Blocks.turbineCondenser),drill);route(drill,corePlan);
        System.out.println("planned="+queue.size+" factories="+factories+" tungsten="+drill+" limits="+CampaignStars.limits(SectorPresets.aegis));
    }
    static void snapshot(){
        var core=state.rules.defaultTeam.core();
        String row=frame/60f+","+((System.nanoTime()-started)/1e9f)+","+state.wave+","+state.enemies+","+(core==null?0:core.healthf())+","+(core==null?0:core.items.get(Items.beryllium))+","+(core==null?0:core.items.get(Items.graphite))+","+(core==null?0:core.items.get(Items.silicon))+","+(core==null?0:core.items.get(Items.tungsten))+","+built+","+state.rules.defaultTeam.data().units.size+","+state.rules.objectives.all.count(o->o.isCompleted())+",\""+state.rules.objectiveFlags+"\"\n";
        output.child("trial.csv").writeString(row,true);System.out.print(row);
        output.child("combat.csv").writeString(frame/60f+","+CampaignStars.elapsed()+","+CampaignStars.minimumCoreHealth()+","+state.stats.buildingsDestroyed+","+pilot.team().data().countType(UnitTypes.stell)+","+Groups.build.count(b->b.block instanceof CoreBlock&&b.team==state.rules.waveTeam)+"\n",true);
        for(var b:pilot.team().cores())output.child("core-health.csv").writeString(frame/60f+","+b.tileX()+","+b.tileY()+","+b.health+"\n",true);
        for(var u:pilot.team().data().units)if(u.isCommandable())output.child("army.csv").writeString(frame/60f+","+u.id+","+u.x/tilesize+","+u.y/tilesize+","+u.health+","+u.command().attackTarget+","+u.command().targetPos+"\n",true);
        System.out.println("running objectives="+state.rules.objectives.all.select(o->!o.isCompleted()&&o.dependencyFinished()).map(o->o.getClass().getSimpleName()+":"+o.text()+":"+o.details()));
        StringBuilder diagnostic=new StringBuilder("seconds="+frame/60f+" pilot="+(pilot.unit()==null?"no controlled unit":pilot.unit().x/tilesize+","+pilot.unit().y/tilesize+" cargo="+pilot.unit().stack)+" onlyDepositCore="+state.rules.onlyDepositCore+" pending="+(queue.any()?queue.first():"none")+"\n");
        for(var b:pilot.team().data().buildings)if(b.block==Blocks.impactDrill||b.block==Blocks.plasmaBore||b.block==Blocks.cliffCrusher||b.block==Blocks.siliconArcFurnace||b.block==Blocks.tankFabricator)diagnostic.append(b.block.name).append(" ").append(b.tileX()).append(',').append(b.tileY()).append(" eff=").append(b.efficiency).append(" enabled=").append(b.enabled).append(" items=").append(b.items).append(" power=").append(b.power==null?"none":b.power.status+" produced="+b.power.graph.getLastPowerProduced()+" need="+b.power.graph.getLastPowerNeeded()).append(b instanceof UnitFactoryBuild f?" payload="+f.payload+" front="+f.front()+" progress="+f.progress:"").append('\n');
        output.child("diagnostics.txt").writeString(diagnostic.toString(),true);
    }
    static void result(String scope){
        output.child("result.txt").writeString("mission=aegis\nmode="+mode+"\nscope="+scope+"\ngateVerified="+gate+"\nfinished="+over+"\nwinner="+winner+"\nsimSeconds="+frame/60f+"\nbuilt="+built+"\ncommands="+commands+"\ndeposits="+deposits+"\npendingPlans="+queue.size+"\nnext="+(queue.any()?queue.first():"none")+"\nstars="+CampaignStars.stars(SectorPresets.aegis,mode)+"\nminimumCore="+CampaignStars.minimumCoreHealth()+"\nlosses="+state.stats.buildingsDestroyed+"\nelapsed="+CampaignStars.elapsed()+"\nnormalBefore="+scoresBefore[0]+"\nheroBefore="+scoresBefore[1]+"\ntacticalBefore="+scoresBefore[2]+"\nnormalAfter="+CampaignStars.stars(SectorPresets.aegis,Mode.NORMAL)+"\ntacticalAfter="+CampaignStars.stars(SectorPresets.aegis,Mode.TACTICAL)+"\nearned="+state.rules.tags.get("stars.earned")+"\nruntimeWatchVersion="+WatchCampaign.version+"\nobjectives="+state.rules.objectives.all.count(o->o.isCompleted())+"/"+state.rules.objectives.all.size+"\nflags="+state.rules.objectiveFlags+"\nstatus="+CampaignStars.status()+"\nclassLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\n");
    }
}
