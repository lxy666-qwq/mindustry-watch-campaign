import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.net.*;
import mindustry.world.*;
import java.util.*;
import static mindustry.Vars.*;

/** No ticks or actions: inspect a separate copy of genuinely earned settings. */
public class BiomassAvailabilityAudit{
    public static void main(String[] args) throws Exception{
        Fi out=new Fi(args[0]);
        if(out.exists()) throw new IllegalArgumentException("New output directory required");
        out.mkdirs();
        Fi original=new Fi("local-work/mindustry-star-work/progression-chain/profile/settings.bin");
        Fi isolated=out.child("profile");isolated.mkdirs();
        original.copyTo(isolated.child("settings.bin"));
        new Fi("local-work/mindustry-star-work/BiomassAvailabilityAudit.java").copyTo(out.child("driver-source.java"));
        Fi assets=new Fi("local-work/mindustry-star-work/star033-runtime/assets");
        Core.app=new MockApplication();
        Core.files=new MockFiles(){@Override public Fi internal(String path){return assets.child(path);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();
        Core.settings=new Settings();Core.settings.setDataDirectory(isolated);Core.settings.setJson(JsonIO.json);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        Fi jar=new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        StringBuilder result=new StringBuilder("version="+WatchCampaign.version+"\njar="+jar+"\njarModified="+jar.lastModified()+"\nassets="+assets+
            "\nsettingsScope=isolated copy of genuinely earned shared progress; original not opened by Settings\nnoTicks=true\nnoBuildOrBreak=true\nnoScoreSeeding=true\n");
        var preset=SectorPresets.biomassFacility;
        int before=CampaignStars.totalStars();
        result.append("mainStars=").append(CampaignStars.mainStars()).append("\ntotalStarsBefore=").append(before)
            .append("\nW1Reward=").append(CampaignStars.completedReward(0)).append("\nW2Reward=").append(CampaignStars.completedReward(1))
            .append("\nunlocked=").append(CampaignStars.unlocked(preset)).append("\nnormalGate=").append(CampaignProgression.allowed(preset,Mode.NORMAL)).append('\n');
        CampaignStars.loadMission(preset,Mode.NORMAL);logic.play();
        result.append("core=").append(state.rules.defaultTeam.core().tileX()).append(',').append(state.rules.defaultTeam.core().tileY())
            .append("\nrefundMultiplier=").append(state.rules.deconstructRefundMultiplier).append("\nbuildCostMultiplier=").append(state.rules.buildCostMultiplier).append('\n');
        for(var b:new mindustry.world.Block[]{Blocks.arc,Blocks.lancer,Blocks.siliconSmelter,Blocks.pulverizer,Blocks.combustionGenerator,Blocks.steamGenerator,Blocks.graphitePress,Blocks.pneumaticDrill,Blocks.battery,Blocks.powerNode,Blocks.mechanicalDrill,Blocks.conveyor,Blocks.itemBridge}){
            result.append("block=").append(b.name).append(" researchedInStarMission=").append(CampaignStars.available(b)).append(" nativePlaceable=").append(b.isPlaceable()).append(" costs=").append(Arrays.toString(b.requirements)).append('\n');
        }
        for(int[] point:new int[][]{{52,115},{74,124},{74,126},{76,143},{76,146},{92,87},{81,156},{83,156}}){
            var tile=world.tile(point[0],point[1]);
            result.append("site=").append(point[0]).append(',').append(point[1]).append(" block=").append(tile.block().name)
                .append(" team=").append(tile.team().name).append(" validBreak=").append(Build.validBreak(state.rules.defaultTeam,point[0],point[1])).append(" theoreticalCompleteRefund=");
            for(var req:tile.block().requirements)result.append(req.item.name).append(':').append(arc.math.Mathf.round(req.amount*state.rules.buildCostMultiplier*state.rules.deconstructRefundMultiplier)).append(',');
            result.append('\n');
        }
        if(before!=CampaignStars.totalStars())throw new IllegalStateException("Inspection changed scores");
        result.append("totalStarsAfter=").append(CampaignStars.totalStars()).append("\nnoCombatOrVictory=true\n");
        out.child("result.txt").writeString(result.toString());
        System.out.print(result);System.exit(0);
    }
}
