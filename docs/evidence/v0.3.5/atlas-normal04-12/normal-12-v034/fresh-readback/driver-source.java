import arc.*;import arc.files.*;import arc.mock.*;import arc.struct.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.core.*;import mindustry.gen.*;import mindustry.io.*;import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;
/** Read-only fresh-process readback; differences are reported rather than hidden. */
public class AtlasReadback{
 public static void main(String[] args)throws Exception{
  Fi attempt=new Fi(args[0]),out=new Fi(args[1]);out.mkdirs();Fi profile=out.child("profile");
  if(profile.exists())throw new IllegalArgumentException("Require fresh readback profile");profile.mkdirs();
  attempt.child("profile/settings.bin").copyTo(profile.child("settings.bin"));
  new Fi("local-work/atlas-034-work/AtlasReadback.java").copyTo(out.child("driver-source.java"));
  new Fi("local-work/atlas-034-work/LakeStateAudit.java").copyTo(out.child("state-audit-source.java"));
  try(var stream=AtlasReadback.class.getClassLoader().getResourceAsStream("watch-version.txt")){out.child("loaded-watch-version.txt").writeBytes(stream.readAllBytes());}
  Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return p.equals("watch-version.txt")?out.child("loaded-watch-version.txt"):new Fi("local-work/mindustry-star-work/star034-runtime/assets").child(p);}};
  Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(profile);
  headless=true;net=new Net(null);Vars.init();Vars.loadSettings();Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
  for(var p:new mindustry.type.SectorPreset[]{SectorPresets.onset,SectorPresets.aegis,SectorPresets.lake}){
   if(CampaignStars.stars(p,CampaignStars.Mode.NORMAL)!=3||CampaignStars.stars(p,CampaignStars.Mode.HEROIC)!=1||CampaignStars.stars(p,CampaignStars.Mode.TACTICAL)!=1)throw new IllegalStateException("Missing genuine inherited 3/1/1: "+p);
  }
  if(CampaignStars.stars(SectorPresets.intersect,CampaignStars.Mode.NORMAL)!=3)throw new IllegalStateException("Missing real Intersect normal3 continuation");
  int mainBefore=CampaignStars.mainStars();int normalBefore=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.NORMAL);
  int heroBefore=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.HEROIC),tacticalBefore=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.TACTICAL);
  String saveName=args.length>2?args[2]:"end.msav";boolean complete=saveName.equals("end.msav");
  SaveIO.load(attempt.child(saveName));
  var tags=complete?JsonIO.read(StringMap.class,attempt.child("final-tags.json").readString()):state.rules.tags;
  if(complete)LakeStateAudit.equal("All fresh-process tags",tags,state.rules.tags);
  var actual=LakeStateAudit.world();var expected=complete?JsonIO.read(StringMap.class,attempt.child("final-world.json").readString()):actual;
  StringBuilder diffs=new StringBuilder();int differences=0;
  for(var entry:expected)if(!Objects.equals(entry.value,actual.get(entry.key))){differences++;diffs.append(entry.key).append(" saved=").append(entry.value).append(" loaded=").append(actual.get(entry.key)).append('\n');}
  for(var entry:actual)if(!expected.containsKey(entry.key)){differences++;diffs.append(entry.key).append(" saved=<missing> loaded=").append(entry.value).append('\n');}
  out.child("world-differences.txt").writeString(diffs.toString());
  if(mainBefore!=CampaignStars.mainStars()||normalBefore!=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.NORMAL)||heroBefore!=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.HEROIC)||tacticalBefore!=CampaignStars.stars(SectorPresets.atlas,CampaignStars.Mode.TACTICAL))throw new IllegalStateException("Load changed permanent scores");
  String result="runtimeWatchVersion="+WatchCampaign.version+"\nclassLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+"\nsourceSave="+attempt.child(saveName)+"\nallTagsEqual="+(complete?"true":"not captured before partial save")+"\ntags="+tags.size+"\nobservedWorldSaved="+(complete?expected.size:"not captured before partial save")+"\nobservedWorldLoaded="+actual.size+"\nobservedWorldDifferences="+(complete?differences:"not compared")+"\nallObservedWorldEqual="+(complete?differences==0:"not compared")+"\nmainStars="+CampaignStars.mainStars()+"\nnormalStars="+normalBefore+"\nsettingsScoresUnchanged=true\ncores="+state.rules.defaultTeam.cores().size+"\nunitCap="+mindustry.entities.Units.getCap(state.rules.defaultTeam)+"\nsavedUnits="+(complete?expected.get("units"):"not captured")+"\nloadedUnits="+actual.get("units")+"\nfinished="+state.rules.tags.getBool("stars.finished")+"\nearned="+state.rules.tags.getInt("stars.earned",0)+"\nminCore="+CampaignStars.minimumCoreHealth()+"\ncoreLosses="+CampaignStars.coreLosses()+"\nplaced="+CampaignStars.placed()+"\nelapsed="+CampaignStars.elapsed()+"\nnoSimulation=true\n";
  result+="heroicStars="+heroBefore+"\ntacticalStars="+tacticalBefore+"\nmode="+state.rules.tags.get("stars.mode")+"\n";
  out.child("result.txt").writeString(result);out.child("loaded-tags.json").writeString(JsonIO.write(state.rules.tags));out.child("loaded-world.json").writeString(JsonIO.write(actual));System.out.print(result);System.exit(0);
 }
}
