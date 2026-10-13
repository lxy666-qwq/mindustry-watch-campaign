import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.io.*;import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;
/** Independent native readback of failed Lake Hero QA worlds; never simulates or awards scores. */
public class LakeDefeatCapAudit{
    public static void main(String[] args)throws Exception{
        Fi attempt=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();
        CampaignStars.Mode expectedMode=args.length>2?CampaignStars.Mode.valueOf(args[2]):CampaignStars.Mode.NORMAL;
        try(var embedded=LakeDefeatCapAudit.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(embedded.readAllBytes());}
        Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Readback profile must be fresh");
        profile.mkdirs();attempt.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/lake-034-work/LakeDefeatCapAudit.java").copyTo(out.child("driver-source.java"));
        new Fi("local-work/lake-034-work/LakeStateAudit.java").copyTo(out.child("state-audit-source.java"));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star034-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        int normal=CampaignStars.stars(SectorPresets.lake,CampaignStars.Mode.NORMAL),hero=CampaignStars.stars(SectorPresets.lake,CampaignStars.Mode.HEROIC),tactical=CampaignStars.stars(SectorPresets.lake,CampaignStars.Mode.TACTICAL);
        int inheritedN=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL), inheritedH=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC), inheritedT=CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL);
        if(inheritedN!=3||inheritedH!=1||inheritedT!=1)throw new IllegalStateException("True Onset 3/1/1 inheritance missing");
        if(CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL)!=3||CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC)!=1||CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL)!=1)throw new IllegalStateException("True Aegis 3/1/1 inheritance missing");
        SaveIO.load(attempt.child("end.msav"));
        var tags=JsonIO.read(StringMap.class,attempt.child("final-tags.json").readString());
        var expected=JsonIO.read(StringMap.class,attempt.child("final-world.json").readString());
        LakeStateAudit.equal("fresh process tags",tags,state.rules.tags);
        var loaded=LakeStateAudit.world();
        StringBuilder audit=new StringBuilder("nativeDefaultTeam="+state.rules.defaultTeam+"\ncores="+state.rules.defaultTeam.cores().size+"\nunitCap="+mindustry.entities.Units.getCap(state.rules.defaultTeam)+"\nsavedUnitCount="+expected.get("units")+"\nloadedUnitCount="+loaded.get("units")+"\neludeUseUnitCap="+UnitTypes.elude.useUnitCap+"\n");
        for(var entry:expected)if(entry.key.startsWith("unit.")&&entry.key.endsWith(".identity")&&!loaded.containsKey(entry.key))audit.append("missingSavedUnit=").append(entry.key).append(":").append(entry.value).append('\n');
        out.child("cap-audit.txt").writeString(audit.toString());out.child("loaded-world.json").writeString(JsonIO.write(loaded));
        System.out.print(audit);System.exit(0);
    }
}
