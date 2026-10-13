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

/** Fresh-process readback only: neither starts a mission nor writes progress. */
public class LegalTideSaveRead{
    public static void main(String[] args) throws Exception{
        var assets = new Fi(System.getProperty("trial.assets"));
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(args[0]));
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        var mission = WatchCampaign.sectors[5];
        for(var mode : Mode.values()) System.out.println("actualSavedScore." + mode + "=" + CampaignStars.stars(mission, mode));
        if(CampaignStars.stars(mission, Mode.NORMAL) != Integer.parseInt(args[2])) throw new IllegalStateException("Actual saved score mismatch");
        if(CampaignStars.mainStars() != 0 || !CampaignStars.completedReward(4)) throw new IllegalStateException("Private QA profile provenance mismatch");
        SaveIO.load(new Fi(args[1]));
        System.out.println("version=" + WatchCampaign.version);
        System.out.println("codeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("world=" + world.width() + "x" + world.height());
        System.out.println("savedMission=" + state.rules.tags.get("stars.preset"));
        System.out.println("savedMode=" + state.rules.tags.get("stars.mode"));
        System.out.println("savedWave=" + state.wave);
        System.out.println("savedEarned=" + state.rules.tags.get("stars.earned"));
        System.out.println("savedFinished=" + state.rules.tags.get("stars.finished"));
        System.out.println("savedMinimumCoreHealth=" + CampaignStars.minimumCoreHealth());
        System.out.println("taskComplete=" + CampaignStars.tacticalTaskComplete());
        for(var name : new String[]{"taskA", "taskB"}){
            var method = CampaignStars.class.getDeclaredMethod(name, mindustry.type.SectorPreset.class);
            method.setAccessible(true);
            System.out.println("savedObserved." + name + "=" + method.invoke(null, mission));
        }
        var core = Team.sharded.core();
        if(core == null) throw new IllegalStateException("Saved winner core missing");
        for(var item : content.items()) System.out.println("savedCore." + item.name + "=" + core.items.get(item));
        if(core.items.get(Items.thorium) != 0 || core.items.get(Items.phaseFabric) != 0) throw new IllegalStateException("Finite counter kit was not fully spent");
        System.out.println("readOnlyNativeSaveReload=passed; campaign menu progression not claimed");
    }
}
