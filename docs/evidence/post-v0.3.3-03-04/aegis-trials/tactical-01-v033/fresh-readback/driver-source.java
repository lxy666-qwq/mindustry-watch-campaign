import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.io.*;import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;
/** Independent native readback of the earned Aegis Normal world; never simulates or awards scores. */
public class Aegis033Readback{
    public static void main(String[] args)throws Exception{
        Fi attempt=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();
        CampaignStars.Mode expectedMode=args.length>2?CampaignStars.Mode.valueOf(args[2]):CampaignStars.Mode.NORMAL;
        try(var embedded=Aegis033Readback.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(embedded.readAllBytes());}
        Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Readback profile must be fresh");
        profile.mkdirs();attempt.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/aegis-033-work/Aegis033Readback.java").copyTo(out.child("driver-source.java"));
        new Fi("local-work/aegis-033-work/AegisStateAudit.java").copyTo(out.child("state-audit-source.java"));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star033-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        int normal=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL),hero=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC),tactical=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL);
        int inheritedN=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL), inheritedH=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC), inheritedT=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL);
        if(inheritedN!=3||inheritedH!=1||inheritedT!=1)throw new IllegalStateException("True Onset 3/1/1 inheritance missing");
        SaveIO.load(attempt.child("end.msav"));
        var tags=JsonIO.read(StringMap.class,attempt.child("final-tags.json").readString());
        var expected=JsonIO.read(StringMap.class,attempt.child("final-world.json").readString());
        AegisStateAudit.equal("fresh process tags",tags,state.rules.tags);
        AegisStateAudit.equal("fresh process world",expected,AegisStateAudit.world());
        if(normal!=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL)||hero!=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC)||tactical!=CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL))throw new IllegalStateException("Loading changed saved scores");
        if(!state.rules.tags.getBool("stars.finished")||state.rules.tags.getInt("stars.earned",0)!=expectedMode.maximum||!state.rules.tags.get("stars.mode").equals(expectedMode.name())||normal!=3||hero!=(expectedMode==CampaignStars.Mode.NORMAL?0:1)||tactical!=(expectedMode==CampaignStars.Mode.TACTICAL?1:0))throw new IllegalStateException("Expected naturally completed Aegis profile");
        String result="watchVersion="+WatchCampaign.version+"\nactualClassLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsave="+attempt.child("end.msav")+"\nprofile="+profile+"\ntags="+tags.size+"\nallTagsEqual=true\nworldValues="+expected.size+"\nallWorldValuesEqual=true\nnormalStars="+normal+"\nheroicStars="+hero+"\ntacticalStars="+tactical+"\ninheritedOnsetNormal="+inheritedN+"\ninheritedOnsetHeroic="+inheritedH+"\ninheritedOnsetTactical="+inheritedT+"\nmainStars="+CampaignStars.mainStars()+"\ntotalStars="+CampaignStars.totalStars()+"\nsettingsScoresUnchangedByLoad=true\nfinished="+state.rules.tags.getBool("stars.finished")+"\nearned="+state.rules.tags.getInt("stars.earned",0)+"\nobjectives="+state.rules.objectives.all.count(o->o.isCompleted())+"/"+state.rules.objectives.all.size+"\nminCore="+CampaignStars.minimumCoreHealth()+"\ncoreLosses="+CampaignStars.coreLosses()+"\nlosses="+state.stats.buildingsDestroyed+"\nelapsed="+CampaignStars.elapsed()+"\nplaced="+CampaignStars.placed()+"\nstatus="+CampaignStars.status()+"\n";
        out.child("loaded-tags.json").writeString(JsonIO.write(state.rules.tags));out.child("loaded-world.json").writeString(JsonIO.write(AegisStateAudit.world()));
        out.child("result.txt").writeString(result);System.out.print(result);System.exit(0);
    }
}
