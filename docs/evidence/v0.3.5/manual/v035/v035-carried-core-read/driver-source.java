import arc.*; import arc.files.*; import arc.math.geom.*; import arc.mock.*; import arc.struct.*; import arc.util.*;
import mindustry.*; import mindustry.content.*; import mindustry.core.*; import mindustry.entities.units.*;
import mindustry.game.*; import mindustry.gen.*; import mindustry.input.*; import mindustry.io.*; import mindustry.logic.*; import mindustry.net.*;
import java.util.*;
import static mindustry.Vars.*;

/** Loads an actual carried-cargo/world checkpoint; any continuation uses native input and movement. */
public class NativeCargoRead035{
    static int taskA() throws Exception{
        var method=CampaignStars.class.getDeclaredMethod("taskA",mindustry.type.SectorPreset.class);
        method.setAccessible(true);return (int)method.invoke(null,CampaignStars.currentPreset());
    }
    static int[] scores(){
        var values=new IntSeq();
        for(var preset:content.sectors())if(CampaignStars.eligible(preset))for(var mode:CampaignStars.Mode.values())values.add(CampaignStars.stars(preset,mode));
        return values.toArray();
    }
    static boolean logistics(String key){
        return key.startsWith("stars.flow.")||key.startsWith("stars.received.")||key.startsWith("stars.transferred.")||key.startsWith("stars.origin.")||key.startsWith("stars.core.")||key.startsWith("stars.cargo");
    }
    public static void main(String[] args) throws Exception{
        Fi source=new Fi(args[0]),out=new Fi(args[1]),profile=new Fi(args[2]);String name=args[3];
        if(out.exists())throw new IllegalStateException("Fresh evidence output required");out.mkdirs();
        profile.copyTo(out.child("profile"));
        new Fi("local-work/mindustry-star-work/manual-core-probe035/NativeCargoRead035.java").copyTo(out.child("driver-source.java"));
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String p){return new Fi(System.getProperty("trial.assets")).child(p);}};
        Core.graphics=new MockGraphics(){@Override public float getDeltaTime(){return 1f/60f;}};Core.audio=new MockAudio();Core.input=new MockInput();
        Core.settings=new Settings();Core.settings.setJson(JsonIO.json);Core.settings.setDataDirectory(out.child("profile"));
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        logicVars=new GlobalVars(){@Override public void update(){var current=Vars.player;Vars.player=null;try{super.update();}finally{Vars.player=current;}}};
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        int[] prior=scores();int mainBefore=CampaignStars.mainStars();
        var expectedTags=JsonIO.read(StringMap.class,source.child(name+"-tags.json").readString());
        var expectedWorld=JsonIO.read(StringMap.class,source.child(name+"-world.json").readString());
        SaveIO.load(source.child(name+".msav"));
        boolean legacy=expectedTags.getInt("stars.flowVersion",0)<3;int kept=0,cleared=0;
        if(legacy){
            for(var entry:expectedTags){
                if(entry.key.equals("stars.flowVersion"))continue;
                if(logistics(entry.key)){if(state.rules.tags.containsKey(entry.key))throw new IllegalStateException("Old logistics retained "+entry.key);cleared++;}
                else{if(!Objects.equals(entry.value,state.rules.tags.get(entry.key)))throw new IllegalStateException("Nonlogistics changed "+entry.key);kept++;}
            }
        }else OnsetStateAudit.equal("fresh native cargo tags",expectedTags,state.rules.tags);
        OnsetStateAudit.equal("fresh native cargo world",expectedWorld,OnsetStateAudit.world());
        if(!Arrays.equals(prior,scores()))throw new IllegalStateException("Reading changed best scores");
        int beforeA=taskA();
        var migratedTags=new StringMap();migratedTags.putAll(state.rules.tags);
        SaveIO.write(out.child("roundtrip.msav"));SaveIO.load(out.child("roundtrip.msav"));
        OnsetStateAudit.equal("roundtrip tags",migratedTags,state.rules.tags);
        OnsetStateAudit.equal("roundtrip world",expectedWorld,OnsetStateAudit.world());
        String result="runtime="+WatchCampaign.version+"\ncodeLocation="+CampaignStars.class.getProtectionDomain().getCodeSource().getLocation()+
            "\nsource="+source+"\ncheckpoint="+name+"\nlegacy="+legacy+"\nkeptNonlogistics="+kept+"\nclearedOldLogistics="+cleared+
            "\nloadedTags="+state.rules.tags.size+"\nworldFields="+expectedWorld.size+"\nworldFieldsAllEqual=true\nroundtripAllEqual=true\nscoreCount="+prior.length+
            "\nmainStars="+mainBefore+"\nbestScoresUnchanged=true\ninitialTaskA="+beforeA+"\n";
        if(name.equals("carried")){
            var props=new Properties();props.load(new java.io.StringReader(source.child("carried-position.txt").readString()));
            var unit=Groups.unit.getByID(Integer.parseInt(props.getProperty("unitId")));
            if(unit==null||unit.stack.amount!=1||unit.item()!=Items.copper)throw new IllegalStateException("Actual held copper missing");
            var receiver=world.build(Integer.parseInt(props.getProperty("receiver")));
            var pilot=Player.create();pilot.name="Native cargo continuation";pilot.team(Team.sharded);pilot.add();pilot.unit(unit);Vars.player=pilot;
            logic.play();boolean delivered=false;var motion=new Vec2();
            for(int i=0;i<900&&!state.gameOver;i++){
                Time.updateGlobal();
                if(!delivered){
                    motion.set(receiver.x-unit.x,receiver.y-unit.y);
                    unit.moveAt(motion.len()<=itemTransferRange*0.6f?motion.setZero():motion.setLength(unit.speed()));
                    if(pilot.within(receiver,itemTransferRange)){
                        InputHandler.transferInventory(pilot,receiver);delivered=unit.stack.amount==0;
                    }
                }else unit.moveAt(motion.setZero());
                asyncCore.begin();logic.update();asyncCore.end();
            }
            boolean mined=source.child("result.txt").readString().contains("minedControl=true");
            if(!delivered||taskA()!=(mined?24:0))throw new IllegalStateException("Actual held transfer counted wrong: delivered="+delivered+" A="+taskA());
            if(state.gameOver||!Arrays.equals(prior,scores()))throw new IllegalStateException("Isolated continuation changed scores");
            out.child("continued-tags.json").writeString(JsonIO.write(state.rules.tags));out.child("continued-world.json").writeString(JsonIO.write(OnsetStateAudit.world()));SaveIO.write(out.child("continued.msav"));
            result+="nativeContinuationSeconds=15\nactualHeldDelivery=true\nminedControl="+mined+"\nendingTaskA="+taskA()+"\ngameOver=false\n";
        }
        out.child("loaded-tags.json").writeString(JsonIO.write(migratedTags));out.child("loaded-world.json").writeString(JsonIO.write(expectedWorld));
        out.child("result.txt").writeString(result);System.out.print(result);System.exit(0);
    }
}
