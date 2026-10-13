import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;import mindustry.game.*;import mindustry.logic.*;import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import java.util.*;
import static mindustry.Vars.*;
/** Private native Aegis-to-Lake inspection; no simulation, resource or score writes. */
public class LakeInspector{
    public static void main(String[] args)throws Exception{
        Fi out=new Fi(args[0]);out.mkdirs();Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Require fresh private profile");profile.mkdirs();
        new Fi("local-work/aegis-trials/tactical-01-v033/profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/lake-033-work/LakeInspector.java").copyTo(out.child("driver-source.java"));
        try(var v=LakeInspector.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(v.readAllBytes());}
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star033-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        out.child("gate.txt").writeString("runtime="+WatchCampaign.version+"\nclass="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsourceProfile=local-work/aegis-trials/tactical-01-v033/profile/settings.bin\nonsetNormal="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL)+"\nonsetHero="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC)+"\nonsetTactical="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL)+"\naegisNormal="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL)+"\naegisHero="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC)+"\naegisTactical="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL)+"\nmainStars="+CampaignStars.mainStars()+"\ntotalStars="+CampaignStars.totalStars()+"\nlakeUnlocked="+CampaignStars.unlocked(SectorPresets.lake)+"\nallowed="+CampaignProgression.allowed(SectorPresets.lake,CampaignStars.Mode.NORMAL)+"\n");
        if(!CampaignStars.unlocked(SectorPresets.lake)||!CampaignProgression.allowed(SectorPresets.lake,CampaignStars.Mode.NORMAL))throw new IllegalStateException("True inherited Erekir profile has not opened Lake");
        CampaignStars.loadMission(SectorPresets.lake,CampaignStars.Mode.NORMAL);logic.play();
        out.child("rules.json").writeString(JsonIO.write(state.rules));out.child("objectives.json").writeString(JsonIO.write(state.rules.objectives));
        var core=state.rules.defaultTeam.core();StringBuilder s=new StringBuilder("size="+world.width()+"x"+world.height()+" core="+core.tile+" initialItems="+core.items+" attack="+state.rules.attackMode+" waves="+state.rules.waves+" limit="+state.rules.limitMapArea+":"+state.rules.limitX+","+state.rules.limitY+","+state.rules.limitWidth+","+state.rules.limitHeight+"\n");
        for(var b:Groups.build){if(b instanceof LogicBuild p)out.child("processor-"+b.tileX()+"-"+b.tileY()+".mlog").writeString(p.code);s.append(b.team).append(' ').append(b.block.name).append(' ').append(b.tileX()).append(',').append(b.tileY()).append(" r=").append(b.rotation).append(" items=").append(b.items).append('\n');}
        out.child("map.txt").writeString(s.toString());s.setLength(0);
        for(var tile:world.tiles){if(arc.math.Mathf.dst(tile.x,tile.y,core.tileX(),core.tileY())>120)continue;for(var block:new Block[]{Blocks.turbineCondenser,Blocks.ventCondenser})if(CampaignStars.available(block)&&Build.validPlaceIgnoreUnits(block,state.rules.defaultTeam,tile.x,tile.y,0,true,true))s.append("vent ").append(block.name).append(' ').append(tile.x).append(',').append(tile.y).append('\n');if(tile.wallDrop()!=null)s.append("wall ").append(tile.x).append(',').append(tile.y).append(' ').append(tile.wallDrop().name).append('\n');if(tile.drop()!=null)s.append("floor ").append(tile.x).append(',').append(tile.y).append(' ').append(tile.drop().name).append('\n');}
        out.child("terrain.txt").writeString(s.toString());s.setLength(0);
        for(var b:content.blocks())if(CampaignStars.available(b))s.append(b.name).append(' ').append(Arrays.toString(b.requirements)).append('\n');out.child("equipment.txt").writeString(s.toString());
        StringBuilder res=new StringBuilder(); int tungstenFloor=0,tungstenWall=0;
        for(var tile:world.tiles){if(tile.drop()==Items.tungsten)tungstenFloor++;if(tile.wallDrop()==Items.tungsten)tungstenWall++;}
        res.append("tungstenFloorTiles=").append(tungstenFloor).append("\ntungstenWallTiles=").append(tungstenWall).append("\n");
        for(var b:Groups.build)if(b.items!=null&&b.items.get(Items.tungsten)>0)res.append("stock ").append(b.team).append(" ").append(b.block).append(" ").append(b.tile).append(" tungsten=").append(b.items.get(Items.tungsten)).append("\n");
        for(var b:content.blocks())if(CampaignStars.available(b)&&b.consumesLiquid(Liquids.water))res.append("availableWaterConsumer ").append(b.name).append(" cost=").append(Arrays.toString(b.requirements)).append("\n");
        int mineable=0,drillSites=0;
        for(var tile:world.tiles){if(((mindustry.world.blocks.production.Drill)Blocks.impactDrill).canMine(tile))mineable++;if(Blocks.impactDrill.canPlaceOn(tile,state.rules.defaultTeam,0))drillSites++;}
        res.append("impactMineableTiles=").append(mineable).append("\nimpactPossibleSitesIgnoringFogAndOccupancy=").append(drillSites).append("\n");
        out.child("resource-audit.txt").writeString(res.toString());
        SaveIO.write(out.child("initial.msav"));System.out.println(out.child("gate.txt").readString());System.out.println(out.child("map.txt").readString().split("\n")[0]);System.out.println(out.child("objectives.json").readString());System.exit(0);
    }
}
