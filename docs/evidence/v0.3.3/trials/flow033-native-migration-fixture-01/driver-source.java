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

/** Loads a genuine in-progress v0.3.2 battle, checks migration without gameplay or scoring. */
public class StarFlowMigrationProbe033{
    public static void main(String[] args) throws Exception{
        Fi old = new Fi(args[0]), out = new Fi(args[1]);
        if(out.exists()) throw new IllegalArgumentException("A fresh migration evidence directory is required");
        out.mkdirs();
        new Fi(args[2]).copyTo(out.child("profile"));
        new Fi("trial-work/StarFlowMigrationProbe033.java").copyTo(out.child("driver-source.java"));
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return new Fi("trial-work/star033-runtime/assets").child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setJson(JsonIO.json); Core.settings.setDataDirectory(out.child("profile"));
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        if(!WatchCampaign.version.equals("0.3.3")) throw new IllegalStateException("Fixed v0.3.3 runtime required");
        int[] scores = scores();
        var original = JsonIO.read(StringMap.class, old.child("checkpoint-900-tags.json").readString());
        var worldExpected = JsonIO.read(StringMap.class, old.child("checkpoint-900-world.json").readString());
        SaveIO.load(old.child("checkpoint-900.msav"));
        if(state.rules.tags.getInt("stars.flowVersion") != 2) throw new IllegalStateException("Legacy flow format was not migrated");
        int kept = 0, cleared = 0;
        for(var entry : original){
            if(entry.key.equals("stars.flowVersion")) continue;
            if(logistics(entry.key)){
                if(state.rules.tags.containsKey(entry.key)) throw new IllegalStateException("Old logistics evidence retained: " + entry.key);
                cleared++;
            }else{
                if(!Objects.equals(entry.value, state.rules.tags.get(entry.key))) throw new IllegalStateException("Non-logistics field changed: " + entry.key);
                kept++;
            }
        }
        OnsetStateAudit.equal("genuine legacy world after migration", worldExpected, OnsetStateAudit.world());
        var task = CampaignStars.class.getDeclaredMethod("taskA", mindustry.type.SectorPreset.class);
        task.setAccessible(true);
        if((int)task.invoke(null, SectorPresets.onset) != 0) throw new IllegalStateException("Legacy supply credit was not reset");
        if(!Arrays.equals(scores, scores())) throw new IllegalStateException("Migration changed best scores");
        var migrated = new StringMap(); migrated.putAll(state.rules.tags);
        SaveIO.write(out.child("migrated.msav"));
        OnsetStateAudit.equal("live migrated tags after save", migrated, state.rules.tags);
        SaveIO.load(out.child("migrated.msav"));
        OnsetStateAudit.equal("migrated tags after readback", migrated, state.rules.tags);
        OnsetStateAudit.equal("migrated native world after readback", worldExpected, OnsetStateAudit.world());
        if(!Arrays.equals(scores, scores())) throw new IllegalStateException("Readback changed best scores");
        out.child("migrated-tags.json").writeString(JsonIO.write(state.rules.tags));
        out.child("migrated-world.json").writeString(JsonIO.write(OnsetStateAudit.world()));
        String result = "actualVersion=" + WatchCampaign.version + "\ncodeLocation=" + CampaignStars.class.getProtectionDomain().getCodeSource().getLocation() +
            "\nsource=genuine-v032-onset-tactical-900-second-checkpoint\noldTags=" + original.size + "\nkeptNonLogisticsTags=" + kept +
            "\nclearedOldLogisticsTags=" + cleared + "\nflowVersion=2\ntaskA=0\nworldValues=" + worldExpected.size +
            "\nallWorldFieldsEqual=true\nallOtherTagsEqual=true\nmigratedSaveReadbackEqual=true\nbestScoresUnchanged=" + Arrays.toString(scores) +
            "\nplaced=" + CampaignStars.placed() + "\nminCore=" + CampaignStars.minimumCoreHealth() + "\nelapsed=" + CampaignStars.elapsed() +
            "\nnoSimulation=true\nnoVictoryOrAward=true\n";
        out.child("result.txt").writeString(result); System.out.print(result);
        System.exit(0);
    }
    static boolean logistics(String key){
        return key.startsWith("stars.flow.") || key.startsWith("stars.received.") || key.startsWith("stars.transferred.") ||
            key.startsWith("stars.origin.") || key.startsWith("stars.core.");
    }
    static int[] scores(){ return new int[]{CampaignStars.stars(SectorPresets.onset, Mode.NORMAL), CampaignStars.stars(SectorPresets.onset, Mode.HEROIC), CampaignStars.stars(SectorPresets.onset, Mode.TACTICAL)}; }
}
