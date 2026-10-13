import arc.*; import arc.files.*; import arc.mock.*; import arc.struct.*; import mindustry.*; import mindustry.content.*;
import mindustry.core.*; import mindustry.game.*; import mindustry.io.*; import mindustry.net.*; import java.util.*;
import static mindustry.Vars.*;

/** Independent native readback of historical victories; no simulation, score writes or gate bypass claim. */
public class IndependentWatchRead033{
    public static void main(String[] args)throws Exception{
        var attempt=new Fi(args[0]);var out=new Fi(args[1]);var profile=new Fi(args[2]);
        if(out.exists())throw new IllegalStateException("New evidence directory required");out.mkdirs();profile.copyTo(out);
        new Fi("local-work/mindustry-star-work/manual-core-probe035/IndependentWatchRead033.java").copyTo(out.child("reader-source.java"));
        var fields=new Properties();fields.load(new java.io.StringReader(attempt.child("result.txt").readString()));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return new Fi(System.getProperty("trial.assets")).child(p);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(out.child("profile"));
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        var mission=content.sector(fields.getProperty("mission"));var mode=CampaignStars.Mode.valueOf(fields.getProperty("mode"));
        var before=new StringMap();for(var preset:content.sectors())if(CampaignStars.eligible(preset))for(var m:CampaignStars.Mode.values())before.put(CampaignStars.progressKey(preset,m),Integer.toString(CampaignStars.stars(preset,m)));
        SaveIO.load(attempt.child("end.msav"));
        if(!WatchCampaign.version.equals(fields.getProperty("actualVersion"))||CampaignStars.currentPreset()!=mission||CampaignStars.currentMode()!=mode)throw new IllegalStateException("Wrong fixed runtime or saved mission");
        if(!state.rules.tags.getBool("stars.finished")||state.rules.tags.getBool("stars.failed")||state.rules.tags.getInt("stars.earned",0)!=mode.maximum)throw new IllegalStateException("Not an actual full-score victory");
        if(CampaignStars.stars(mission,mode)!=mode.maximum||CampaignStars.mainStars()!=0||!CampaignStars.completedReward(0)||!CampaignStars.completedReward(4))throw new IllegalStateException("Actual isolated score provenance mismatch");
        if(state.wave!=Integer.parseInt(fields.getProperty("wave"))||CampaignStars.minimumCoreHealth()!=Float.parseFloat(fields.getProperty("coreMinimum"))||CampaignStars.placed()!=Integer.parseInt(fields.getProperty("placedBuildings"))||state.stats.buildingsDestroyed!=Integer.parseInt(fields.getProperty("buildingsDestroyed")))throw new IllegalStateException("Saved battle fields differ from native end report");
        for(var entry:before)if(!entry.value.equals(Integer.toString(Core.settings.getInt(entry.key,0))))throw new IllegalStateException("Reading changed scores "+entry.key);
        int a=0,b=0;for(var task:new String[]{"taskA","taskB"}){var method=CampaignStars.class.getDeclaredMethod(task,mindustry.type.SectorPreset.class);method.setAccessible(true);int value=(int)method.invoke(null,mission);if(task.equals("taskA"))a=value;else b=value;}
        if(mode==CampaignStars.Mode.TACTICAL&&!CampaignStars.tacticalTaskComplete())throw new IllegalStateException("Saved tactical task incomplete");
        var result="fixedVersion="+WatchCampaign.version+"\ncodeLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nmission="+mission.name+"\nmode="+mode+"\nloadedEarned="+state.rules.tags.get("stars.earned")+"\nactualModeStars="+CampaignStars.stars(mission,mode)+"\nmainStars=0\ngateClaimed=false\nwave="+state.wave+"\nplaced="+CampaignStars.placed()+"\nlosses="+state.stats.buildingsDestroyed+"\nminimumCore="+CampaignStars.minimumCoreHealth()+"\nA="+a+"\nB="+b+"\nall168BestScoresUnchanged=true\nnoSimulation=true\nnoAwardsCreated=true\n";
        out.child("result.txt").writeString(result);System.out.print(result);System.exit(0);
    }
}
