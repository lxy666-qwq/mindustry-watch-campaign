import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.net.*;
import static mindustry.Vars.*;

/** Synthetic damage/heal reproducer, deliberately not a battle or a scored victory. */
public class CoreMinimumProbe{
    public static void main(String[] args){
        Fi assets = new Fi(System.getProperty("trial.assets", "core/assets"));
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(args[0]));
        Core.settings.setJson(JsonIO.json);
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        Time.setDeltaProvider(() -> 1f);
        CampaignStars.loadMission(WatchCampaign.sectors[0], CampaignStars.Mode.HEROIC);
        logic.play();
        var core = state.rules.defaultTeam.core();
        CampaignStars.update();
        float before = core.health;
        //Building.damage(float) is the native route used by fire/environmental damage.
        core.damage(before * 0.151f);
        float damaged = core.healthf();
        core.heal(before * 0.151f);
        CampaignStars.update();
        float recorded = state.rules.tags.getFloat("stars.minCore", -1f);
        System.out.println("nativeDamagedHealth=" + damaged);
        System.out.println("nativeHealedHealth=" + core.healthf());
        System.out.println("recordedMinimum=" + recorded);
        System.out.println("gameOver=" + state.gameOver);
        System.out.println("stars=" + CampaignStars.totalStars());
        System.out.println("syntheticProbe=true; no victory, no score, not a playthrough");
    }
}
