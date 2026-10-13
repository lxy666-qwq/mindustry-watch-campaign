import arc.*;
import arc.backend.sdl.*;
import arc.files.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.GameState.State;
import mindustry.desktop.*;
import mindustry.game.Saves.SaveSlot;
import mindustry.io.*;
import mindustry.ui.dialogs.*;
import static mindustry.Vars.*;

/** Actual desktop import/save/reload of an unfinished native coolant probe; no scores are created. */
public class StarCoolantCapture034 extends DesktopLauncher{
    private int step;
    private long started, changed;
    private StringMap expected;
    private SaveSlot slot;
    private final Fi output=new Fi(System.getProperty("star.capture.output"));
    public StarCoolantCapture034(){super(new String[0]);}
    public static void main(String[] args){
        System.setProperty("nodiscord","true");
        Vars.loadLogger();
        new SdlApplication(new StarCoolantCapture034(),new SdlConfig(){{title="Mindustry v0.3.4 actual coolant save regression";width=1280;height=900;maximized=false;}});
    }
    private void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Coolant desktop regression: "+message);}
    private float age(){return(System.nanoTime()-changed)/1e9f;}
    private void next(int value){step=value;changed=System.nanoTime();}
    private void hideDialogs(){Core.scene.root.getChildren().copy().each(e->{if(e instanceof Dialog d)d.hide();});}
    private void shot(String name){output.mkdirs();ScreenUtils.saveScreenshot(output.child(name+".png"));Log.info("Desktop coolant image @",name);}
    private void coolantTask(){
        new CampaignStarsDialog().show(SectorPresets.lake);
        var button=Core.scene.find(e->e instanceof TextButton b && b.getText().toString().equals(Core.bundle.get("stars.tactical")));
        require(button!=null,"native tactical selector missing");
        button.fireClick();
    }
    @Override public void update(){
        if((step==2 || step==3) && CampaignStars.active() && CampaignStars.currentPreset()==SectorPresets.lake) state.set(State.paused);
        super.update();
        if(!clientLoaded)return;
        if(started==0)started=changed=System.nanoTime();
        require((System.nanoTime()-started)/1e9f<90f,"exceeded 90 seconds");
        switch(step){
            case 0->{if(age()<3f)return;require(WatchCampaign.version.equals("0.3.4"),"wrong runtime");hideDialogs();next(1);}
            case 1->{
                if(age()<1f)return;
                shot("00-menu-v0.3.4");
                try{slot=control.saves.importSave(new Fi(System.getProperty("star.capture.world")));}catch(java.io.IOException e){throw new RuntimeException(e);}
                ui.load.runLoadSave(slot);next(2);
            }
            case 2->{
                if(!CampaignStars.active() || CampaignStars.currentPreset()!=SectorPresets.lake)return;
                state.set(State.paused);
                if(age()<3f)return;
                require(CampaignStars.currentMode()==Mode.TACTICAL,"wrong native task");
                require(!state.rules.tags.getBool("stars.finished"),"probe incorrectly recorded victory");
                require(CampaignStars.stars(SectorPresets.lake)==0 && CampaignStars.mainStars()==10,"actual scores changed");
                require(!CampaignStars.normalComplete(SectorPresets.lake),"closed tactical gate changed");
                require(CampaignStars.describe(SectorPresets.lake,Mode.TACTICAL).contains("消耗冷却液"),"actual coolant wording missing");
                try{var method=CampaignStars.class.getDeclaredMethod("taskA",mindustry.type.SectorPreset.class);method.setAccessible(true);require((int)method.invoke(null,SectorPresets.lake)>=12,"actual coolant supply lost");}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                shot("01-actual-unfinished-coolant-world");
                expected=new StringMap();expected.putAll(state.rules.tags);
                slot.save();
                output.child("desktop-state.txt").writeString("runtime=0.3.4\nmission=lake\nmode=TACTICAL\nfinished=false\nmainStars=10\nlakeStars=0\ntags="+expected.size+"\nstatus="+CampaignStars.status()+"\n");
                coolantTask();next(10);
            }
            case 10->{if(age()<2f)return;shot("02-native-coolant-task-and-closed-gate");hideDialogs();ui.load.runLoadSave(slot);next(3);}
            case 3->{
                if(!CampaignStars.active() || CampaignStars.currentPreset()!=SectorPresets.lake)return;
                state.set(State.paused);
                if(age()<3f)return;
                require(expected.equals(state.rules.tags),"SaveSlot reload changed evidence tags");
                require(CampaignStars.stars(SectorPresets.lake)==0 && CampaignStars.mainStars()==10,"reload changed scores");
                shot("03-native-save-slot-restored");
                Log.info("Desktop v0.3.4 complete: unfinished Lake coolant world, @ equal tags, main10/Lake0 unchanged; no playthrough added.",expected.size);
                Core.app.exit();next(4);
            }
        }
    }
}
