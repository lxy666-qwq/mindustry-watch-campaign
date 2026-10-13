import arc.*;
import arc.files.*;
import arc.mock.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.net.*;
import static mindustry.Vars.*;

/** v0.3.5 copied-profile provenance read; never plays, writes scores or changes world stock. */
public class LegalReplayProfileRead{
    public static void main(String[] args){
        var assets = new Fi(System.getProperty("trial.assets"));
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(args[0]));
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        if(!WatchCampaign.version.equals("0.3.5") || CampaignStars.mainStars()!=0)
            throw new IllegalStateException("Fixed runtime and genuine main0 QA required");
        System.out.println("version="+WatchCampaign.version);
        System.out.println("codeLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("mainStars="+CampaignStars.mainStars());
        for(int i : new int[]{0,4,7}){
            var mission = WatchCampaign.sectors[i];
            if(!CampaignStars.completedReward(i)) throw new IllegalStateException("Missing actual earned reward: "+mission.name);
            for(var mode : CampaignStars.Mode.values()){
                int score=CampaignStars.stars(mission,mode);
                System.out.println(mission.name+"."+mode+"="+score);
                if(score!=(mode==CampaignStars.Mode.NORMAL?3:1)) throw new IllegalStateException("Actual best mismatch");
            }
            System.out.println(mission.name+".reward=true");
        }
        System.out.println("QA profile full-copy provenance; existing rewards replay; 100 mainline-star gate not claimed");
    }
}
