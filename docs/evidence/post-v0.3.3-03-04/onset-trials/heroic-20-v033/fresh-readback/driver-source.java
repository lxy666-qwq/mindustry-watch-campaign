import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.io.*;import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;
/** Independent native readback of the earned Heroic world; never simulates or awards scores. */
public class OnsetHeroicReadback{
    public static void main(String[] args)throws Exception{
        Fi attempt=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();
        Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Readback profile must be fresh");
        profile.mkdirs();attempt.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/onset-hero033-work/OnsetHeroicReadback.java").copyTo(out.child("driver-source.java"));
        new Fi("local-work/onset-hero033-work/OnsetStateAudit.java").copyTo(out.child("state-audit-source.java"));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return new Fi("local-work/mindustry-star-work/star033-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        int normal=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL),hero=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC),tactical=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL);
        SaveIO.load(attempt.child("end.msav"));
        var tags=JsonIO.read(StringMap.class,attempt.child("final-tags.json").readString());
        var expected=JsonIO.read(StringMap.class,attempt.child("final-world.json").readString());
        OnsetStateAudit.equal("fresh process tags",tags,state.rules.tags);
        OnsetStateAudit.equal("fresh process world",expected,OnsetStateAudit.world());
        if(normal!=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL)||hero!=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC)||tactical!=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL))throw new IllegalStateException("Loading changed saved scores");
        if(!state.rules.tags.getBool("stars.finished")||state.rules.tags.getInt("stars.earned",0)!=1||!state.rules.tags.get("stars.mode").equals("HEROIC")||normal!=3||hero!=1||tactical!=1)throw new IllegalStateException("Expected naturally completed five-star Onset profile");
        String result="watchVersion="+WatchCampaign.version+"\nactualClassLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsave="+attempt.child("end.msav")+"\nprofile="+profile+"\ntags="+tags.size+"\nallTagsEqual=true\nworldValues="+expected.size+"\nallWorldValuesEqual=true\nnormalStars="+normal+"\nheroicStars="+hero+"\ntacticalStars="+tactical+"\nsettingsScoresUnchangedByLoad=true\nfinished="+state.rules.tags.getBool("stars.finished")+"\nearned="+state.rules.tags.getInt("stars.earned",0)+"\nobjectives="+state.rules.objectives.all.count(o->o.isCompleted())+"/"+state.rules.objectives.all.size+"\nminCore="+CampaignStars.minimumCoreHealth()+"\ncoreLosses="+CampaignStars.coreLosses()+"\nlosses="+state.stats.buildingsDestroyed+"\nelapsed="+CampaignStars.elapsed()+"\nplaced="+CampaignStars.placed()+"\nstatus="+CampaignStars.status()+"\n";
        out.child("loaded-tags.json").writeString(JsonIO.write(state.rules.tags));out.child("loaded-world.json").writeString(JsonIO.write(OnsetStateAudit.world()));
        out.child("result.txt").writeString(result);System.out.print(result);System.exit(0);
    }
}
