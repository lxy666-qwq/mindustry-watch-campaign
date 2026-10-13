import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;

/** New JVM load only; no logic.play, ticks, resources or scores are written. */
public class SerpuloNativeRead033{
    public static void main(String[] args)throws Exception{
        Fi attempt=new Fi(args[0]),out=new Fi(args[1]);
        if(out.exists())throw new IllegalArgumentException("New output required");out.mkdirs();
        Fi profile=out.child("profile");profile.mkdirs();attempt.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/mindustry-star-work/SerpuloNativeRead033.java").copyTo(out.child("driver-source.java"));
        Fi assets=new Fi("local-work/mindustry-star-work/star033-runtime/assets");
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String path){return assets.child(path);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();
        Core.settings=new Settings();Core.settings.setDataDirectory(profile);Core.settings.setJson(JsonIO.json);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        var mission=content.sector(args[2]);int totalBefore=CampaignStars.totalStars();
        int[] before=new int[3];for(var mode:Mode.values())before[mode.ordinal()]=CampaignStars.stars(mission,mode);
        SaveIO.load(attempt.child("end.msav"));
        var expectedTags=JsonIO.read(StringMap.class,attempt.child("final-tags.json").readString());
        SerpuloNativeState033.equal("tags",expectedTags,state.rules.tags);
        boolean worldCompared=attempt.child("final-world.json").exists();
        int fields=0;
        if(worldCompared){var expectedWorld=JsonIO.read(StringMap.class,attempt.child("final-world.json").readString());SerpuloNativeState033.equal("terrain/buildings",expectedWorld,SerpuloNativeState033.world());fields=expectedWorld.size;}
        for(var mode:Mode.values())if(before[mode.ordinal()]!=CampaignStars.stars(mission,mode))throw new IllegalStateException("Loading changed best score");
        if(totalBefore!=CampaignStars.totalStars())throw new IllegalStateException("Loading changed total score");
        String result="version="+WatchCampaign.version+"\nactualCode="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nmission="+mission.name+"\nnormal="+before[0]+"\nhero="+before[1]+"\ntactical="+before[2]+
            "\nmainStars="+CampaignStars.mainStars()+"\nallTagsEqual=true\ntagCount="+expectedTags.size+"\nterrainAndBuildingFieldsCompared="+worldCompared+"\nworldFieldCount="+fields+
            "\nworld="+world.width()+"x"+world.height()+"\nwave="+state.wave+"\nfinished="+state.rules.tags.getBool("stars.finished")+"\nearned="+state.rules.tags.getInt("stars.earned",0)+
            "\nminimumCore="+CampaignStars.minimumCoreHealth()+"\ncoreLosses="+CampaignStars.coreLosses()+"\nelapsed="+CampaignStars.elapsed()+"\nplaced="+CampaignStars.placed()+
            "\ncore="+(state.rules.defaultTeam.core()==null?"none":state.rules.defaultTeam.core().items)+"\nscoresUnchangedByLoad=true\nnoTicksOrReplay=true\nreadback=passed\n";
        out.child("result.txt").writeString(result);System.out.print(result);System.exit(0);
    }
}
