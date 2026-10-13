import arc.files.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.type.*;
import static mindustry.Vars.*;

/** Real later-branch combat using player plans, native supply and native game updates. */
public class LegalLaterWatchTrial extends LegalWatchTrial{
    static Fi caseOutput;
    public static void main(String[] args) throws Exception{
        if(args.length<4 || Integer.parseInt(args[3].substring(6))<7) throw new IllegalArgumentException("Watch7—10 required");
        caseOutput=new Fi(args[1]);caseOutput.mkdirs();
        new Fi("local-work/mindustry-star-work/LegalLaterWatchTrial.java").copyTo(caseOutput.child("later-source.java"));
        constructionCopperReserve=120;
        if(args[2].equals("TACTICAL")) constructionLimit=160;
        planSetup=LegalLaterWatchTrial::prepare;
        finalizeTrial=LegalLaterWatchTrial::saveEvidence;
        LegalWatchTrial.main(args);
    }
    static void prepare(){
        if(!WatchCampaign.version.equals("0.3.3")) throw new IllegalStateException("Fixedv033 required");
        var core=Team.sharded.core();
        var stock=new StringBuilder("Actual native initial loadout; no inventory modified by driver\n");
        for(var item:content.items())stock.append(item.name).append('=').append(core.items.get(item)).append('\n');
        caseOutput.child("native-starting-stock.txt").writeString(stock.toString());
        planDefence();
        var base=mindustry.maps.planet.WatchMapGenerator.core(world.width());
        if(Boolean.getBoolean("trial.laterArtillery")){
            queue.removeAll(p->p.block==Blocks.cyclone || p.block==Blocks.titaniumWallLarge);
            guns.removeAll(p->p.x==base.x+1 && p.y==base.y+12);
            for(var pos:new arc.math.geom.Point2[]{new arc.math.geom.Point2(base.x-11,base.y+15),new arc.math.geom.Point2(base.x+7,base.y+15)}){
                queue.add(new BuildPlan(pos.x,pos.y,0,Blocks.ripple));
                guns.add(pos);
            }
            preferRipplePlastanium=true;
            forceDuoCopper=true;
            siliconAmmoReserve=()->{
                int silicon=0;
                for(var p:queue)for(var req:p.block.requirements)if(req.item==Items.silicon)silicon+=(int)Math.ceil(req.amount*(1-p.progress));
                return silicon;
            };
            if(Boolean.getBoolean("trial.bossAim")){
                controlledArtillery=new arc.math.geom.Point2(base.x-11,base.y+15);
                artilleryTargetFilter=u->u.type==UnitTypes.scepter;
            }
        }
        if(Boolean.getBoolean("trial.laterShield")){
            queue.removeAll(p->p.block==Blocks.lancer || p.block==Blocks.ripple && p.x==base.x-11 && p.y==base.y+15 ||
                p.block==Blocks.steamGenerator && p.x==base.x-9 && p.y==base.y+5 || p.block==Blocks.powerNode && p.x==base.x+5 && p.y==base.y+19);
            guns.removeAll(p->p.x==base.x-11 && p.y==base.y+15);
            fuel.removeAll(p->p.x==base.x-9 && p.y==base.y+5);
            queue.add(new BuildPlan(base.x+4,base.y+18,0,Blocks.forceProjector));
            queue.add(new BuildPlan(base.x+8,base.y-7,0,Blocks.batteryLarge));
            queue.add(new BuildPlan(base.x+3,base.y-6,0,Blocks.powerNode));
            if(controlledArtillery!=null)controlledArtillery=new arc.math.geom.Point2(base.x-7,base.y+14);
        }
        queue.add(new BuildPlan(base.x+4,base.y+3,0,Blocks.graphitePress));
        queue.add(new BuildPlan(base.x+4,base.y+2,3,Blocks.conveyor));
        queue.add(new BuildPlan(base.x+4,base.y+1,3,Blocks.conveyor));
        fuel.add(new arc.math.geom.Point2(base.x+4,base.y+3));
        //Same native plans as the earlier branch driver, arranged to finish income before costly defences.
        queue.sort(p->{
            var b=p.block;
            if(b==Blocks.duo || b==Blocks.copperWall && p.y==mindustry.maps.planet.WatchMapGenerator.core(world.width()).y+25)return 0f;
            if(b==Blocks.scatter || b==Blocks.powerNode)return 1f;
            if(b==Blocks.mechanicalDrill || b==Blocks.pneumaticDrill || b==Blocks.conveyor || b==Blocks.itemBridge)return 2f;
            if(b==Blocks.graphitePress)return 2.3f;
            if(b==Blocks.copperWall || b==Blocks.copperWallLarge || b==Blocks.titaniumWallLarge)return 3f;
            if(b==Blocks.forceProjector)return 3.1f;
            if(Boolean.getBoolean("trial.laterArtillery") && b==Blocks.ripple)return 3.3f;
            if(b==Blocks.lancer || b==Blocks.hail || b==Blocks.ripple || b==Blocks.cyclone)return 4f;
            return 5f;
        });
        repairPlans.clear();repairPlans.addAll(queue);
        var costs=new ObjectIntMap<Item>();
        for(var p:queue)for(var req:p.block.requirements)costs.increment(req.item,0,req.amount);
        initialTransportGlassReserve=costs.get(Items.metaglass,0);
        var summary=new StringBuilder("laterArtillery="+Boolean.getBoolean("trial.laterArtillery")+"\nlaterShield="+Boolean.getBoolean("trial.laterShield")+"\nbossAim="+Boolean.getBoolean("trial.bossAim")+"\nplannedBuildings="+queue.size+"\n");
        for(var item:content.items())if(costs.containsKey(item))summary.append(item.name).append('=').append(costs.get(item)).append('\n');
        caseOutput.child("native-plan-costs.txt").writeString(summary.toString());
        caseOutput.child("boss-observations.csv").writeString("simSeconds,wave,boss,x,y,health,shield,liveRipples,nativeRippleShots,rippleAmmo\n");
        caseOutput.child("coal-production.csv").writeString("simSeconds,x,y,dominantItem,nativeDrillSpeed,storedItems,nativeOutputBlock,outputStored,coreCoal,coreGraphite\n");
        afterStep=()->{
            if(frame%3600==0)for(int dx:new int[]{-17,-14}){
                var b=world.build(base.x+dx,base.y+27);
                var output=world.build(base.x+dx,base.y+26);
                if(b instanceof mindustry.world.blocks.production.Drill.DrillBuild drill)caseOutput.child("coal-production.csv").writeString(frame/60f+","+b.tileX()+","+b.tileY()+","+drill.dominantItem+","+drill.lastDrillSpeed+","+b.items+","+(output==null?"no-building":output.block.name)+","+(output==null?"":output.items)+","+core.items.get(Items.coal)+","+core.items.get(Items.graphite)+"\n",true);
            }
            if(frame%60!=0)return;
            for(var enemy:mindustry.gen.Groups.unit){
                if(enemy.team!=Team.crux || enemy.type!=UnitTypes.scepter && enemy.type!=UnitTypes.zenith)continue;
                int live=0,shots=0;float ammo=0;
                for(var pos:guns){var b=world.build(pos.pack());if(b instanceof mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild turret && b.block==Blocks.ripple){live++;shots+=turret.totalShots;ammo+=turret.totalAmmo;}}
                caseOutput.child("boss-observations.csv").writeString(frame/60f+","+state.wave+","+enemy.type.name+","+enemy.x/tilesize+","+enemy.y/tilesize+","+enemy.health+","+enemy.shield+","+live+","+shots+","+ammo+"\n",true);
            }
        };
    }
    static void saveEvidence(){
        var text="actualVersion="+actualVersion+"\nmainStars="+CampaignStars.mainStars()+"\nW6.actualFullFive="+CampaignStars.completedReward(5)+"\n";
        for(var mode:CampaignStars.Mode.values())text+="actualSavedScore."+mode+"="+CampaignStars.stars(mission,mode)+"\n";
        for(var name:new String[]{"taskA","taskB"}){
            try{var m=CampaignStars.class.getDeclaredMethod(name,SectorPreset.class);m.setAccessible(true);text+="nativeObserved."+name+"="+m.invoke(null,mission)+"\n";}
            catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        }
        text+="tags="+state.rules.tags+"\n";
        if(over){var save=caseOutput.child("end.msav");SaveIO.write(save);text+="nativeEndSave="+save.absolutePath()+"\nnativeWinner="+winner+"\n";}
        caseOutput.child("native-end-evidence.txt").writeString(text);
    }
}
