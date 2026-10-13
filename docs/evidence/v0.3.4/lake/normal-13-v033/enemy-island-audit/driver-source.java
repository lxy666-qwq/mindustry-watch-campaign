import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild;
import java.util.*;
import static mindustry.Vars.*;
/** Reads a preserved native world without simulation or score changes. */
public class LakeSnapshotRead{
    public static void main(String[] args)throws Exception{
        Fi attempt=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();
        new Fi("local-work/lake-033-work/LakeSnapshotRead.java").copyTo(out.child("driver-source.java"));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return new Fi("trial-work/star033-runtime/assets").child(p);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(out.child("profile"));
        out.child("profile").mkdirs();attempt.child("profile/settings.bin").copyTo(out.child("profile/settings.bin"));
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        SaveIO.load(attempt.child(args.length>2?args[2]:"end.msav"));
        StringBuilder s=new StringBuilder("version="+WatchCampaign.version+" class="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\n");
        s.append("cap=").append(mindustry.entities.Units.getCap(state.rules.defaultTeam)).append(" ruleCap=").append(state.rules.unitCap).append(" variable=").append(state.rules.unitCapVariable).append(" stellCount=").append(state.rules.defaultTeam.data().countType(UnitTypes.stell)).append("\n");
        for(var b:Groups.build) if(b instanceof mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild f) s.append(b.team).append(" factory ").append(b.tileX()).append(",").append(b.tileY()).append(" rotation=").append(b.rotation).append(" current=").append(f.currentPlan).append(" eff=").append(f.efficiency).append(" progress=").append(f.progress).append(" payload=").append(f.payload).append(" front=").append(f.front()).append(" items=").append(f.items).append(" power=").append(f.power.status).append(" enabled=").append(f.enabled).append("\n");
        for(var u:Groups.unit)s.append(u.team).append(' ').append(u.type.name).append(" id=").append(u.id).append(" pos=").append(u.x/tilesize).append(',').append(u.y/tilesize).append(" hp=").append(u.health).append(" target=").append(u.isCommandable()?u.command().attackTarget:null).append(" move=").append(u.isCommandable()?u.command().targetPos:null).append('\n');
        for(var b:Groups.build)if(b instanceof ItemTurretBuild t)s.append(b.team).append(" turret ").append(b.block.name).append(' ').append(b.tileX()).append(',').append(b.tileY()).append(" hp=").append(b.health).append(" ammo=").append(t.totalAmmo).append(" bullet=").append(t.peekAmmo()==null?"none":t.peekAmmo().damage+" range="+t.peekAmmo().range).append(" water=").append(t.liquids.get(Liquids.water)).append('\n');
        for(var b:Groups.build)if(b.team==state.rules.defaultTeam&&b.tileX()<295&&b.tileY()<125&&b.items!=null)s.append("stock ").append(b.block.name).append(' ').append(b.tileX()).append(',').append(b.tileY()).append(" r=").append(b.rotation).append(" items=").append(b.items).append(" front=").append(b.front()).append(" link=").append(b instanceof mindustry.world.blocks.distribution.DuctBridge.DuctBridgeBuild bridge?bridge.findLink():null).append('\n');
        StringBuilder tiles=new StringBuilder("columns228..260; #=natural solid,+friendly solid,.=friendly nonsolid,~=liquid,space=clear\n");
        for(int y=Integer.getInteger("grid.top",115);y>=Integer.getInteger("grid.bottom",69);y--){tiles.append(y).append(" ");for(int x=Integer.getInteger("grid.left",228);x<=Integer.getInteger("grid.right",260);x++){var t=world.tile(x,y);tiles.append(t.build!=null?(t.block().solid?'+':'.'):t.solid()?'#':t.floor().isLiquid?'~':' ');}tiles.append('\n');}
        out.child("tile-grid.txt").writeString(tiles.toString());
        out.child("result.txt").writeString(s.toString());System.out.print(s);System.exit(0);
    }
}
