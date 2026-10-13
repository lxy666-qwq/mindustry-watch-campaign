import arc.*;
import arc.files.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.io.*;

/** Continuation of actual shared scores, using native resource/build/action APIs. */
public class CrossPlanetEarnedWatch034 extends CrossPlanetWatchTrial034{
    public static void main(String[] args)throws Exception{
        Fi output=new Fi(args[1]);
        if(output.exists())throw new IllegalArgumentException("New attempt directory required");
        output.mkdirs();
        new Fi("local-work/mindustry-star-work/crossplanet034-ui/CrossPlanetEarnedWatch034.java").copyTo(output.child("wrapper-source.java"));
        if(args[3].equals("watch-1"))preferGraphiteDuo=true;
        if(args[3].equals("watch-1")&&args[2].equals("TACTICAL"))planSetup=()->{
            planDefence();
            BuildPlan drill=new BuildPlan(83,34,0,Blocks.mechanicalDrill);queue.add(drill);repairPlans.add(drill.copy());
            for(int y=33;y>=27;y--){BuildPlan belt=new BuildPlan(83,y,3,Blocks.conveyor);queue.add(belt);repairPlans.add(belt.copy());}
        };
        startupObserver=()->{
            output.child("runtime.txt").writeString("actualVersion="+WatchCampaign.version+"\njar="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\njarModifiedAtStart="+campaignCodeTime+"\nassets="+assets+"\nprofile="+profile+"\ngateVerified="+gateVerified+"\nmainStarsBefore="+mainStarsBefore+"\nmissionStarsBefore="+missionStarsBefore+"\nbranchRewardBefore="+branchRewardBefore+"\nnoScoreMerging=true\n");
        };
        finalizeTrial=()->{
            SaveIO.write(output.child("end.msav"));
            output.child("final-tags.json").writeString(JsonIO.write(Vars.state.rules.tags));
            output.child("final-world.json").writeString(JsonIO.write(SerpuloNativeState033.world()));
            output.child("profile").mkdirs();Core.settings.getSettingsFile().copyTo(output.child("profile/settings.bin"));
        };
        CrossPlanetWatchTrial034.main(args);
    }
}
