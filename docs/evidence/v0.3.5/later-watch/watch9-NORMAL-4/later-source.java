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
            if(Boolean.getBoolean("trial.laterArtillery") && b==Blocks.ripple)return 3.3f;
            if(b==Blocks.lancer || b==Blocks.hail || b==Blocks.ripple || b==Blocks.cyclone)return 4f;
            return 5f;
        });
        var costs=new ObjectIntMap<Item>();
        for(var p:queue)for(var req:p.block.requirements)costs.increment(req.item,0,req.amount);
        initialTransportGlassReserve=costs.get(Items.metaglass,0);
        var summary=new StringBuilder("laterArtillery="+Boolean.getBoolean("trial.laterArtillery")+"\nplannedBuildings="+queue.size+"\n");
        for(var item:content.items())if(costs.containsKey(item))summary.append(item.name).append('=').append(costs.get(item)).append('\n');
        caseOutput.child("native-plan-costs.txt").writeString(summary.toString());
    }
    static void saveEvidence(){
        var text="actualVersion="+actualVersion+"\nmainStars="+CampaignStars.mainStars()+"\nW6.actualFullFive="+CampaignStars.completedReward(5)+"\n";
        for(var mode:CampaignStars.Mode.values())text+="actualSavedScore."+mode+"="+CampaignStars.stars(mission,mode)+"\n";
        for(var name:new String[]{"taskA","taskB"}){
            try{var m=CampaignStars.class.getDeclaredMethod(name,SectorPreset.class);m.setAccessible(true);text+="nativeObserved."+name+"="+m.invoke(null,mission)+"\n";}
            catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        }
        text+="tags="+state.rules.tags+"\n";
        if(over&&winner==Team.sharded){var save=caseOutput.child("end.msav");SaveIO.write(save);text+="nativeEndSave="+save.absolutePath()+"\n";}
        caseOutput.child("native-end-evidence.txt").writeString(text);
    }
}
