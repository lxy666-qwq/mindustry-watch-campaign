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

/** A new process reading a naturally lost native world. It never ticks or writes scores. */
public class LegalFailedOutcomeRead{
    public static void main(String[] args) throws Exception{
        var assets=new Fi(System.getProperty("trial.assets"));
        Core.app=new MockApplication();
        Core.files=new MockFiles(){@Override public Fi internal(String path){return assets.child(path);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(args[0]));
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        var mission=content.sector(args[2]);
        if(CampaignStars.mainStars()!=0 || !CampaignStars.completedReward(4))throw new IllegalStateException("Private QA provenance mismatch");
        for(var mode:Mode.values()){
            int actual=CampaignStars.stars(mission,mode);
            System.out.println("actualSavedScore."+mode+"="+actual);
            if(actual!=Integer.parseInt(args[3+mode.ordinal()]))throw new IllegalStateException("Actual score mismatch");
        }
        SaveIO.load(new Fi(args[1]));
        if(!state.rules.tags.get("stars.preset").equals(mission.name) || !state.rules.tags.getBool("stars.finished") || state.rules.tags.getInt("stars.earned")!=0 || Team.sharded.cores().size!=0)throw new IllegalStateException("Saved natural loss mismatch");
        System.out.println("version="+WatchCampaign.version);
        System.out.println("codeLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("mainStars="+CampaignStars.mainStars());
        System.out.println("savedMission="+state.rules.tags.get("stars.preset"));
        System.out.println("savedMode="+state.rules.tags.get("stars.mode"));
        System.out.println("savedWave="+state.wave);
        System.out.println("savedEarned="+state.rules.tags.get("stars.earned"));
        System.out.println("savedFinished="+state.rules.tags.get("stars.finished"));
        System.out.println("savedMinimumCoreHealth="+CampaignStars.minimumCoreHealth());
        System.out.println("nativePlaced="+state.stats.buildingsBuilt);
        System.out.println("nativeLost="+state.stats.buildingsDestroyed);
        System.out.println("friendlyCores="+Team.sharded.cores().size);
        System.out.println("nativeEnemyUnits="+state.rules.waveTeam.data().units.size);
        System.out.println("savedWorldSize="+world.width()+"x"+world.height());
        System.out.println("readOnlyReload=true; direct-map QA; no normal campaign gate claimed");
    }
}
