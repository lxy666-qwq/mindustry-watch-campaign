import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;import mindustry.game.*;import mindustry.logic.*;import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import java.util.*;
import static mindustry.Vars.*;
/** Private native Aegis-to-Intersect inspection; no simulation, resource or score writes. */
public class IntersectNetworkAudit{
    public static void main(String[] args)throws Exception{
        Fi out=new Fi(args[0]);out.mkdirs();Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Require fresh private profile");profile.mkdirs();
        new Fi("local-work/lake-trials/tactical-02-v034/profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/intersect-034-work/IntersectNetworkAudit.java").copyTo(out.child("driver-source.java"));
        try(var v=IntersectNetworkAudit.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(v.readAllBytes());}
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star034-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        out.child("gate.txt").writeString("runtime="+WatchCampaign.version+"\nclass="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsourceProfile=local-work/lake-trials/tactical-02-v034/profile/settings.bin\nonsetNormal="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL)+"\nonsetHero="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC)+"\nonsetTactical="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL)+"\naegisNormal="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL)+"\naegisHero="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC)+"\naegisTactical="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL)+"\nmainStars="+CampaignStars.mainStars()+"\ntotalStars="+CampaignStars.totalStars()+"\nintersectUnlocked="+CampaignStars.unlocked(SectorPresets.intersect)+"\nallowed="+CampaignProgression.allowed(SectorPresets.intersect,CampaignStars.Mode.NORMAL)+"\n");
        StringBuilder tree=new StringBuilder("Actual Erekir named mainline gates from the genuine Onset/Aegis/Lake15-star settings; no direct later-map load.\n");
        for(var preset:CampaignStars.mainLevels(Planets.erekir))tree.append(preset.name).append(" ").append(preset.localizedName).append(" gate=").append(CampaignStars.unlocked(preset)&&CampaignProgression.allowed(preset,CampaignStars.Mode.NORMAL)).append(" parents=").append(CampaignProgression.parents(preset)).append(" normal=").append(CampaignStars.stars(preset,CampaignStars.Mode.NORMAL)).append('\n');
        out.child("actual-tree.txt").writeString(tree.toString());System.out.print(tree);
        if(!CampaignStars.unlocked(SectorPresets.intersect)||!CampaignProgression.allowed(SectorPresets.intersect,CampaignStars.Mode.NORMAL))throw new IllegalStateException("True inherited Erekir profile has not opened Intersect");
        SaveIO.load(new Fi(args[1]));
        StringBuilder details=new StringBuilder("READ ONLY native saved failure, no simulation; load may apply original zero-core unit cap.\n");
        for(var tile:world.tiles){var build=tile.build;if(build==null||build.tile!=tile||build.team!=state.rules.defaultTeam)continue;
            details.append(build.block.name).append(" @").append(tile.x).append(',').append(tile.y).append(" r=").append(build.rotation).append(" eff=").append(build.efficiency).append(" items=").append(build.items).append(" liquid=").append(build.liquids==null?"none":"water="+build.liquids.get(Liquids.water)).append(" power=").append(build.power==null?"none":build.power.status+" prod="+build.power.graph.getLastPowerProduced()+" demand="+build.power.graph.getLastPowerNeeded()+" links="+build.power.links).append('\n');
            if(build instanceof mindustry.world.blocks.distribution.DirectionBridge.DirectionBridgeBuild bridge)details.append("  actualLink=").append(bridge.findLink()).append("\n");
        }
        out.child("build-details.txt").writeString(details.toString());
        for(var u:Groups.unit) details.append("unit ").append(u.id).append(" type=").append(u.type).append(" team=").append(u.team).append(" spawnedByCore=").append(u.spawnedByCore).append(" commandable=").append(u.isCommandable()).append(" controller=").append(u.controller()).append(" position=").append(u.x/tilesize).append(',').append(u.y/tilesize).append('\n');
        for(var b:Groups.build)if(b instanceof mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild f)details.append("factory ").append(b).append(" frontTile=").append(b.tile.nearby(0,2)).append(" frontSolid=").append(b.tile.nearby(0,2).solid()).append(" payload=").append(f.payload==null?"none":f.payload.unit.type+" @"+f.payload.unit.x/tilesize+","+f.payload.unit.y/tilesize+" rotate="+f.payload.unit.rotation+" canpass="+f.payload.unit.canPass(mindustry.core.World.toTile(f.payload.unit.x),mindustry.core.World.toTile(f.payload.unit.y))).append("\n");
        out.child("build-details.txt").writeString(details.toString());System.exit(0);
    }
}
