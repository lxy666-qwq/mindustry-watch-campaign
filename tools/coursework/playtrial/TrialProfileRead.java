import arc.*;
import arc.files.*;
import arc.mock.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.net.*;
import static mindustry.Vars.*;

/** Read-only reload of a trial's actual saved star progress; never writes scores. */
public class TrialProfileRead{
    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(args[0]));
        if(!Core.settings.getSettingsFile().exists()) throw new IllegalStateException("No actual settings file");
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        var preset = content.sector(args[1]);
        for(var mode : Mode.values()){
            int actual = CampaignStars.stars(preset, mode);
            int expected = Integer.parseInt(args[2 + mode.ordinal()]);
            if(actual != expected) throw new IllegalStateException(preset.name + " " + mode + " expected " + expected + ", actual " + actual);
            System.out.println(preset.name + "." + mode + "=" + actual);
        }
        System.out.println("mainStars=" + CampaignStars.mainStars());
        System.out.println("branch0Stars=" + CampaignStars.stars(WatchCampaign.sectors[0]));
        System.out.println("branch0Reward=" + CampaignStars.completedReward(0));
        System.out.println("frozenForestOpen=" + (CampaignStars.unlocked(SectorPresets.frozenForest) && CampaignProgression.allowed(SectorPresets.frozenForest, Mode.NORMAL)));
        System.out.println("readOnlyReload=passed");
    }
}
