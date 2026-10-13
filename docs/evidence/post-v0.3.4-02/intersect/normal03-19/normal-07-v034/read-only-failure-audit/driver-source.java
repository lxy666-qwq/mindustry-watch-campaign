import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;import mindustry.game.*;import mindustry.logic.*;import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import java.util.*;
import static mindustry.Vars.*;
/** Private native Aegis-to-Intersect inspection; no simulation, resource or score writes. */
public class IntersectFailureAudit{
    public static void main(String[] args)throws Exception{
        Fi out=new Fi(args[0]);out.mkdirs();Fi profile=out.child("profile");if(profile.exists())throw new IllegalArgumentException("Require fresh private profile");profile.mkdirs();
        new Fi("local-work/lake-trials/tactical-02-v034/profile/settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/intersect-034-work/IntersectFailureAudit.java").copyTo(out.child("driver-source.java"));
        try(var v=IntersectFailureAudit.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(v.readAllBytes());}
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star034-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        out.child("gate.txt").writeString("runtime="+WatchCampaign.version+"\nclass="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsourceProfile=local-work/lake-trials/tactical-02-v034/profile/settings.bin\nonsetNormal="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.NORMAL)+"\nonsetHero="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.HEROIC)+"\nonsetTactical="+CampaignStars.stars(SectorPresets.onset,CampaignStars.Mode.TACTICAL)+"\naegisNormal="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.NORMAL)+"\naegisHero="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.HEROIC)+"\naegisTactical="+CampaignStars.stars(SectorPresets.aegis,CampaignStars.Mode.TACTICAL)+"\nmainStars="+CampaignStars.mainStars()+"\ntotalStars="+CampaignStars.totalStars()+"\nintersectUnlocked="+CampaignStars.unlocked(SectorPresets.intersect)+"\nallowed="+CampaignProgression.allowed(SectorPresets.intersect,CampaignStars.Mode.NORMAL)+"\n");
        StringBuilder tree=new StringBuilder("Actual Erekir named mainline gates from the genuine Onset/Aegis/Lake15-star settings; no direct later-map load.\n");
        for(var preset:CampaignStars.mainLevels(Planets.erekir))tree.append(preset.name).append(" ").append(preset.localizedName).append(" gate=").append(CampaignStars.unlocked(preset)&&CampaignProgression.allowed(preset,CampaignStars.Mode.NORMAL)).append(" parents=").append(CampaignProgression.parents(preset)).append(" normal=").append(CampaignStars.stars(preset,CampaignStars.Mode.NORMAL)).append('\n');
        out.child("actual-tree.txt").writeString(tree.toString());System.out.print(tree);
        if(!CampaignStars.unlocked(SectorPresets.intersect)||!CampaignProgression.allowed(SectorPresets.intersect,CampaignStars.Mode.NORMAL))throw new IllegalStateException("True inherited Erekir profile has not opened Intersect");
        SaveIO.load(new Fi(args[1]).child("end.msav"));
        StringBuilder details=new StringBuilder("READ ONLY native saved failure, no simulation; load may apply original zero-core unit cap.\n");
        for(var tile:world.tiles){var build=tile.build;if(build==null||build.tile!=tile||build.team!=state.rules.defaultTeam)continue;
            details.append(build.block.name).append(" @").append(tile.x).append(',').append(tile.y).append(" r=").append(build.rotation).append(" eff=").append(build.efficiency).append(" items=").append(build.items).append(" liquid=").append(build.liquids).append(" power=").append(build.power==null?"none":build.power.status+" prod="+build.power.graph.getLastPowerProduced()+" demand="+build.power.graph.getLastPowerNeeded()+" links="+build.power.links).append('\n');
            if(build instanceof mindustry.world.blocks.distribution.DirectionBridge.DirectionBridgeBuild bridge)details.append("  actualLink=").append(bridge.findLink()).append("\n");
        }
        out.child("build-details.txt").writeString(details.toString());
        StringBuilder nativeInfo=new StringBuilder("nativeWinWave="+state.rules.winWave+"\nwaveSpacing="+state.rules.waveSpacing/60f+"\ninitialWaveSpacing="+state.rules.initialWaveSpacing/60f+"\nwaveTime="+state.wavetime/60f+"\n");
        for(var spawn:state.rules.spawns)nativeInfo.append("spawn ").append(spawn.type.name).append(" begin=").append(spawn.begin).append(" end=").append(spawn.end).append(" spacing=").append(spawn.spacing).append(" amount=").append(spawn.unitAmount).append(" scaling=").append(spawn.unitScaling).append('\n');
        for(var tile:world.tiles)if(tile.overlay()==Blocks.spawn)nativeInfo.append("spawnPoint ").append(tile.x).append(',').append(tile.y).append('\n');
        for(var b:state.rules.defaultTeam.data().buildings)if(b.block instanceof mindustry.world.blocks.production.BeamDrill block){
            nativeInfo.append("nativeBore ").append(b.tileX()).append(',').append(b.tileY()).append(" r=").append(b.rotation).append(" actualBeamMaterials=");
            for(int i=0;i<block.size;i++){
                var ray=new arc.math.geom.Point2();block.nearbySide(b.tileX(),b.tileY(),b.rotation,i,ray);
                for(int j=0;j<block.range;j++){var tile=world.tile(ray.x+arc.math.geom.Geometry.d4x(b.rotation)*j,ray.y+arc.math.geom.Geometry.d4y(b.rotation)*j);if(tile==null||tile.solid()){nativeInfo.append(tile==null?"out":tile.wallDrop()).append(' ');break;}}
            }
            nativeInfo.append('\n');
        }
        out.child("native-defence-info.txt").writeString(nativeInfo.toString());System.out.print(nativeInfo);
        int left=250,bottom=300,right=295,top=355,scale=12;
        var mapImage=new java.awt.image.BufferedImage((right-left+1)*scale,(top-bottom+1)*scale,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=mapImage.createGraphics();
        for(int x=left;x<=right;x++)for(int y=bottom;y<=top;y++){
            var tile=world.tile(x,y);java.awt.Color color=new java.awt.Color(tile.floor().isLiquid?0x34527c:tile.solid()?0x444444:0xa7a09b);
            if(tile.floor().attributes.get(mindustry.world.meta.Attribute.steam)>0)color=java.awt.Color.ORANGE;
            if(tile.wallDrop()==Items.graphite)color=java.awt.Color.DARK_GRAY;
            if(tile.wallDrop()==Items.beryllium)color=new java.awt.Color(0x88ccbb);
            if(tile.drop()==Items.tungsten)color=new java.awt.Color(0x005500);
            if(tile.build!=null)color=tile.team()==state.rules.defaultTeam?java.awt.Color.CYAN:tile.team()==state.rules.waveTeam?java.awt.Color.RED:java.awt.Color.GRAY;
            if(tile.overlay()==Blocks.spawn)color=java.awt.Color.MAGENTA;
            g.setColor(color);g.fillRect((x-left)*scale,(top-y)*scale,scale,scale);
        }
        g.setColor(java.awt.Color.WHITE);g.setFont(new java.awt.Font("Arial",java.awt.Font.PLAIN,10));
        for(int x=left;x<=right;x+=10)g.drawString(Integer.toString(x),(x-left)*scale,12);
        for(int y=bottom;y<=top;y+=10)g.drawString(Integer.toString(y),0,(top-y)*scale+10);
        for(var b:Groups.build)if(b.tileX()>=left&&b.tileX()<=right&&b.tileY()>=bottom&&b.tileY()<=top&&(b.block instanceof mindustry.world.blocks.storage.CoreBlock||b.block instanceof mindustry.world.blocks.defense.turrets.BaseTurret||b.block==Blocks.turbineCondenser))g.drawString(b.block.name+" "+b.tileX()+","+b.tileY(),(b.tileX()-left)*scale,(top-b.tileY())*scale);
        g.dispose();javax.imageio.ImageIO.write(mapImage,"png",new java.io.File(out.child("native-map.png").absolutePath()));
        out.child("rules.json").writeString(JsonIO.write(state.rules));out.child("objectives.json").writeString(JsonIO.write(state.rules.objectives));
        var core=world.build(271,321);StringBuilder s=new StringBuilder("size="+world.width()+"x"+world.height()+" core="+core.tile+" initialItems="+core.items+" attack="+state.rules.attackMode+" waves="+state.rules.waves+" limit="+state.rules.limitMapArea+":"+state.rules.limitX+","+state.rules.limitY+","+state.rules.limitWidth+","+state.rules.limitHeight+"\n");
        for(var b:Groups.build){if(b instanceof LogicBuild p)out.child("processor-"+b.tileX()+"-"+b.tileY()+".mlog").writeString(p.code);s.append(b.team).append(' ').append(b.block.name).append(' ').append(b.tileX()).append(',').append(b.tileY()).append(" r=").append(b.rotation).append(" items=").append(b.items).append('\n');}
        out.child("map.txt").writeString(s.toString());s.setLength(0);
        for(var tile:world.tiles){if(arc.math.Mathf.dst(tile.x,tile.y,271,321)>120)continue;for(var block:new Block[]{Blocks.turbineCondenser,Blocks.ventCondenser})if(CampaignStars.available(block)&&Build.validPlaceIgnoreUnits(block,state.rules.defaultTeam,tile.x,tile.y,0,true,true))s.append("vent ").append(block.name).append(' ').append(tile.x).append(',').append(tile.y).append('\n');if(tile.wallDrop()!=null)s.append("wall ").append(tile.x).append(',').append(tile.y).append(' ').append(tile.wallDrop().name).append('\n');if(tile.drop()!=null)s.append("floor ").append(tile.x).append(',').append(tile.y).append(' ').append(tile.drop().name).append('\n');}
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
