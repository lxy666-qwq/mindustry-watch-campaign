// Fixed v0.3.5, one native replay, genuine prior W1/W5/W8 rewards; no campaign100-star gate claim.
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
        new Fi("local-work/mindustry-star-work/qa035-watch8-tactical-replay-1/source/LegalLaterWatchTrial.java").copyTo(caseOutput.child("later-source.java"));
        constructionCopperReserve=120;
        if(args[2].equals("TACTICAL")) constructionLimit=160;
        planSetup=LegalLaterWatchTrial::prepare;
        finalizeTrial=LegalLaterWatchTrial::saveEvidence;
        LegalWatchTrial.main(args);
    }
    static void prepare(){
        if(!WatchCampaign.version.equals("0.3.5")) throw new IllegalStateException("Fixedv035 required");
        if(CampaignStars.mainStars()!=0 || !CampaignStars.completedReward(0) || !CampaignStars.completedReward(4) || !CampaignStars.completedReward(7)) throw new IllegalStateException("Actual earned QA profile provenance mismatch");
        if(modeStarsBefore!=1 || missionStarsBefore!=5 || !branchRewardBefore) throw new IllegalStateException("W8 must be an existing full-five replay");
        var core=Team.sharded.core();
        var stock=new StringBuilder("Actual native initial loadout; no inventory modified by driver\n");
        for(var item:content.items())stock.append(item.name).append('=').append(core.items.get(item)).append('\n');
        caseOutput.child("native-starting-stock.txt").writeString(stock.toString());
        planDefence();
        var base=mindustry.maps.planet.WatchMapGenerator.core(world.width());
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
            if(b==Blocks.lancer || b==Blocks.hail || b==Blocks.ripple || b==Blocks.cyclone)return 4f;
            return 5f;
        });
        var costs=new ObjectIntMap<Item>();
        for(var p:queue)for(var req:p.block.requirements)costs.increment(req.item,0,req.amount);
        initialTransportGlassReserve=costs.get(Items.metaglass,0);
        var summary=new StringBuilder("plannedBuildings="+queue.size+"\n");
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
        var save=caseOutput.child(over ? "end.msav" : "world.msav");SaveIO.write(save);text+="nativeWorldSave="+save.absolutePath()+"\nnativeWinner="+winner+"\n";
        caseOutput.child("native-end-evidence.txt").writeString(text);
    }
}
