import arc.*;import arc.files.*;import arc.mock.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;
/** Read-only tile and native unit movement inspection; no simulation or score changes. */
public class AtlasRouteAudit{
 public static void main(String[] args)throws Exception{
  Fi source=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();Fi profile=out.child("profile");
  if(profile.exists())throw new IllegalStateException("Require fresh private profile");profile.mkdirs();
  source.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
  new Fi("local-work/atlas-034-work/AtlasRouteAudit.java").copyTo(out.child("driver-source.java"));
  try(var stream=AtlasRouteAudit.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(stream.readAllBytes());}
  Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star034-runtime/assets").child(p);}};
  Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
  headless=true;net=new Net(null);Vars.init();Vars.loadSettings();Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
  SaveIO.load(source.child(args.length>2?args[2]:"end.msav"));
  StringBuilder text=new StringBuilder("runtime="+WatchCampaign.version+"\nclass="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nmainStars="+CampaignStars.mainStars()+"\nnoSimulation=true\n");
  var stell=Groups.unit.find(u->u.team==state.rules.defaultTeam&&u.type==UnitTypes.stell);
  var merui=Groups.unit.find(u->u.team==state.rules.defaultTeam&&u.type==UnitTypes.merui);
  for(int[] p:new int[][]{{275,482},{278,448},{275,420},{264,408},{265,470},{267,450},{266,430},{263,409},{261,403},{253,402},{245,399}}){
   var tile=world.tile(p[0],p[1]);text.append(p[0]).append(',').append(p[1]).append(" block=").append(tile.block()).append(" floor=").append(tile.floor()).append(" solid=").append(tile.solid()).append(" stellCanPass=").append(stell==null?"no saved stell":stell.canPass(p[0],p[1])).append(" meruiCanPass=").append(merui==null?"no saved merui":merui.canPass(p[0],p[1])).append(" discovered=").append(fogControl.isDiscovered(state.rules.defaultTeam,p[0],p[1])).append('\n');
  }
  out.child("result.txt").writeString(text.toString());System.out.print(text);System.exit(0);
 }
}
