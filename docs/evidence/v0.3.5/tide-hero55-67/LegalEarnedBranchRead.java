import arc.*;
import arc.files.*;
import arc.mock.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.net.*;
import mindustry.game.*;
import static mindustry.Vars.*;

/** A fresh process reading native scores and an end save; never plays or grants progress. */
public class LegalEarnedBranchRead{
    public static void main(String[] args) throws Exception{
        var assets = new Fi(System.getProperty("trial.assets"));
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(args[0]));
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        var mission = content.sector(args[2]);
        if(CampaignStars.mainStars() != 0 || !CampaignStars.completedReward(4)) throw new IllegalStateException("Private QA provenance mismatch");
        for(var mode : Mode.values()){
            int actual = CampaignStars.stars(mission, mode);
            System.out.println("actualSavedScore." + mode + "=" + actual);
            if(actual != Integer.parseInt(args[3 + mode.ordinal()])) throw new IllegalStateException("Actual score mismatch: " + mode);
        }
        SaveIO.load(new Fi(args[1]));
        if(!state.rules.tags.get("stars.preset").equals(mission.name)) throw new IllegalStateException("Saved mission mismatch");
        System.out.println("version=" + WatchCampaign.version);
        System.out.println("codeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("mainStars=" + CampaignStars.mainStars());
        System.out.println("W1.actualFullFive=" + CampaignStars.completedReward(0));
        System.out.println("W5.actualFullFive=" + CampaignStars.completedReward(4));
        System.out.println("mission.actualFullFive=" + CampaignStars.completedReward(CampaignStars.branchIndex(mission)));
        System.out.println("savedMission=" + state.rules.tags.get("stars.preset"));
        System.out.println("savedMode=" + state.rules.tags.get("stars.mode"));
        System.out.println("savedWave=" + state.wave);
        System.out.println("savedEarned=" + state.rules.tags.get("stars.earned"));
        System.out.println("savedFinished=" + state.rules.tags.get("stars.finished"));
        System.out.println("savedMinimumCoreHealth=" + CampaignStars.minimumCoreHealth());
        System.out.println("nativePlaced=" + state.stats.buildingsBuilt);
        System.out.println("nativeLost=" + state.stats.buildingsDestroyed);
        System.out.println("taskComplete=" + CampaignStars.tacticalTaskComplete());
        for(var name : new String[]{"taskA", "taskB"}){
            var method = CampaignStars.class.getDeclaredMethod(name, mindustry.type.SectorPreset.class);
            method.setAccessible(true);
            System.out.println("savedObserved." + name + "=" + method.invoke(null, mission));
        }
        var core = Team.sharded.core();
        if(core == null) throw new IllegalStateException("Saved native winner core is missing");
        for(var item : content.items()) System.out.println("savedCore." + item.name + "=" + core.items.get(item));
        System.out.println("readOnlyReload=true; direct-map QA; normal campaign gate not claimed");
    }
}
