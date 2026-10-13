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
import mindustry.desktop.*;
import mindustry.game.Saves.SaveSlot;
import mindustry.io.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/** Opens an actual completed combat world in the desktop client; no artificial score or inventory. */
public class StarHeroCapture033 extends DesktopLauncher{
    private int step;
    private long started, changed;
    private StringMap expected;
    private SaveSlot slot;
    private final Fi output = new Fi(System.getProperty("star.capture.output"));

    public StarHeroCapture033(){ super(new String[0]); }

    public static void main(String[] args){
        System.setProperty("nodiscord", "true");
        Vars.loadLogger();
        new SdlApplication(new StarHeroCapture033(), new SdlConfig(){{
            title = "Mindustry v0.3.3 actual world save regression";
            width = 1280;
            height = 720;
            maximized = false;
        }});
    }

    private void require(boolean condition, String message){
        if(!condition) throw new IllegalStateException("Ledger desktop regression: " + message);
    }

    private float age(){ return (System.nanoTime() - changed) / 1e9f; }
    private void next(int value){ step = value; changed = System.nanoTime(); }

    private void shot(String name){
        output.mkdirs();
        ScreenUtils.saveScreenshot(output.child(name + ".png"));
        Log.info("Desktop ledger image @", name);
    }

    @Override
    public void update(){
        super.update();
        if(!clientLoaded) return;
        if(started == 0) started = changed = System.nanoTime();
        require((System.nanoTime() - started) / 1e9f < 90f, "exceeded 90 seconds");
        switch(step){
            case 0 -> {
                if(age() < 3f) return;
                require(WatchCampaign.version.equals("0.3.3"), "wrong desktop version");
                Core.scene.root.getChildren().copy().each(e -> { if(e instanceof Dialog d) d.hide(); });
                next(1);
            }
            case 1 -> {
                if(age() < 1f) return;
                shot("00-menu-v0.3.3");
                new CampaignStarsDialog().show(SectorPresets.onset);
                next(10);
            }
            case 10 -> {
                if(age() < 2f) return;
                String requirement = CampaignProgression.requirements(WatchCampaign.sectors[5], Mode.NORMAL);
                require(requirement.contains("80") && requirement.contains("40") && requirement.contains(Blocks.segment.localizedName), "finite W6 prerequisite text missing");
                require(CampaignProgression.requiredReward(WatchCampaign.sectors[5], Mode.NORMAL) == 4, "W5 prerequisite missing");
                require(CampaignStars.stars(SectorPresets.onset) == 5, "actual mainline five stars missing");
                for(int index = 0; index < WatchCampaign.sectors.length; index++){
                    require(!CampaignStars.completedReward(index), "mainline five stars must not grant branch reward");
                }
                shot("01-actual-onset-five-stars-no-branch-reward");
                Core.scene.root.getChildren().copy().each(e -> { if(e instanceof Dialog d) d.hide(); });
                try{
                    slot = control.saves.importSave(new Fi(System.getProperty("star.capture.world")));
                }catch(java.io.IOException e){
                    throw new RuntimeException(e);
                }
                ui.load.runLoadSave(slot);
                next(2);
            }
            case 2 -> {
                if(!state.isPlaying() || !CampaignStars.active() || age() < 4f) return;
                require(CampaignStars.currentPreset() == SectorPresets.onset && CampaignStars.currentMode() == Mode.HEROIC, "wrong actual world");
                require(state.rules.tags.getBool("stars.finished") && state.rules.tags.getInt("stars.earned") == 1, "actual completed task record lost");
                require(CampaignStars.minimumCoreHealth() == 1f, "actual core minimum changed");
                require(state.rules.tags.size > 500, "actual long ledger missing");
                require(state.rules.tags.getInt("stars.flowVersion") == 2, "actual task lacks new provenance format");
                expected = new StringMap();
                expected.putAll(state.rules.tags);
                shot("02-actual-onset-heroic-world");
                slot.save();
                Log.info("Actual completed world: @ tags; normal=@; heroic=@; earned=@; no injected inventory/score",
                    expected.size, CampaignStars.stars(SectorPresets.onset, Mode.NORMAL), CampaignStars.stars(SectorPresets.onset, Mode.HEROIC), state.rules.tags.getInt("stars.earned"));
                ui.load.runLoadSave(slot);
                next(3);
            }
            case 3 -> {
                if(!state.isPlaying() || !CampaignStars.active() || age() < 4f) return;
                require(expected.equals(state.rules.tags), "SaveSlot reload changed the long ledger");
                require(state.rules.tags.getInt("stars.earned") == 1, "completed score changed");
                require(CampaignStars.minimumCoreHealth() == 1f, "minimum health changed");
                shot("03-actual-onset-save-slot-restored");
                Log.info("Desktop v0.3.3 ledger regression complete: native actual Onset heroic world import, SaveSlot save/reload, @ identical tags, earned=1. This GUI check does not add a playthrough.", expected.size);
                Core.app.exit();
                next(4);
            }
        }
    }
}
