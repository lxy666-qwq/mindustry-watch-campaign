import arc.*;
import arc.backend.sdl.*;
import arc.files.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.desktop.*;

import static mindustry.Vars.*;

/** Desktop regression only: synthetic native damage/repair, no victory or awarded score. */
public class StarCoreHealthCapture extends DesktopLauncher{
    private int step;
    private long started, changed;
    private final Fi output = new Fi(System.getProperty("star.capture.output"));

    public StarCoreHealthCapture(){ super(new String[0]); }

    public static void main(String[] args){
        System.setProperty("nodiscord", "true");
        Vars.loadLogger();
        new SdlApplication(new StarCoreHealthCapture(), new SdlConfig(){{
            title = "Mindustry v0.3.1 core health regression";
            width = 1280;
            height = 720;
            maximized = false;
        }});
    }

    private void require(boolean condition, String message){
        if(!condition) throw new IllegalStateException("Core health desktop regression: " + message);
    }

    private float age(){ return (System.nanoTime() - changed) / 1e9f; }
    private void next(int value){ step = value; changed = System.nanoTime(); }

    private void shot(String name){
        output.mkdirs();
        ScreenUtils.saveScreenshot(output.child(name + ".png"));
        Log.info("Desktop regression image @", name);
    }

    private TextButton button(Group root, String label){
        var button = root.find(e -> e instanceof TextButton b && b.getText().toString().equals(label));
        require(button instanceof TextButton, "missing button " + label);
        return (TextButton)button;
    }

    @Override
    public void update(){
        super.update();
        if(!clientLoaded) return;
        if(started == 0) started = changed = System.nanoTime();
        require((System.nanoTime() - started) / 1e9f < 80f, "desktop regression exceeded 80 seconds");
        switch(step){
            case 0 -> {
                if(age() < 3f) return;
                require(dataDirectory.absolutePath().replace('\\', '/').equalsIgnoreCase(
                    new Fi(System.getProperty("star.capture.profile")).absolutePath().replace('\\', '/')), "wrong dedicated profile");
                require(WatchCampaign.version.equals("0.3.1"), "wrong build version");
                require(CampaignStars.totalStars() == 0, "profile must begin without stars");
                Core.scene.root.getChildren().copy().each(e -> { if(e instanceof Dialog d) d.hide(); });
                next(1);
            }
            case 1 -> {
                if(age() < 1f) return;
                shot("00-menu-v0.3.1");
                button(Core.scene.root, Core.bundle.format("stars.menu", WatchCampaign.version)).fireClick();
                next(2);
            }
            case 2 -> {
                if(age() < 1f) return;
                var card = WatchCampaign.starsDialog.find(e -> e instanceof TextButton b && b.getText().toString().contains(SectorPresets.groundZero.localizedName));
                require(card instanceof TextButton, "zero ground card absent");
                ((TextButton)card).fireClick();
                next(3);
            }
            case 3 -> {
                if(age() < 1f) return;
                button(Core.scene.getDialog(), Core.bundle.get("stars.launch")).fireClick();
                next(4);
            }
            case 4 -> {
                if(!state.isPlaying() || !CampaignStars.active() || age() < 3f) return;
                require(CampaignStars.currentPreset() == SectorPresets.groundZero && CampaignStars.currentMode() == Mode.NORMAL, "wrong normal map");
                require(state.rules.sector == null && !control.saves.getCurrent().isSector(), "wrong save ownership");
                var core = state.rules.defaultTeam.core();
                require(core.healthf() == 1f, "starting core is already hurt");
                core.damage(core.maxHealth * 0.35f);
                float dipped = core.healthf();
                core.heal();
                require(Math.abs(dipped - 0.65f) < 0.0001f, "native damage amount differs");
                require(Math.abs(CampaignStars.minimumCoreHealth() - dipped) < 0.0001f, "same-frame repair erased the damage");
                require(core.healthf() == 1f && CampaignStars.scoreVictory() == 2, "normal grade did not retain the minimum");
                Log.info("SYNTHETIC REGRESSION ONLY: native float damage=@; same-frame repaired=@; recorded minimum=@; hypothetical normal grade=@; no victory, no stars",
                    dipped, core.healthf(), CampaignStars.minimumCoreHealth(), CampaignStars.scoreVictory());
                next(5);
            }
            case 5 -> {
                if(age() < 2f) return;
                shot("01-same-frame-core-minimum");
                control.saves.getCurrent().save();
                ui.load.runLoadSave(control.saves.getCurrent());
                next(6);
            }
            case 6 -> {
                if(!state.isPlaying() || !CampaignStars.active() || age() < 4f) return;
                require(Math.abs(CampaignStars.minimumCoreHealth() - 0.65f) < 0.0001f, "reload lost the recorded minimum");
                require(state.rules.defaultTeam.core().healthf() == 1f, "repair was not saved");
                require(CampaignStars.totalStars() == 0 && !state.gameOver, "regression awarded a score");
                shot("02-restored-core-minimum");
                Log.info("Desktop v0.3.1 regression complete: version/menu/UI normal launch, native damage/repair, SaveSlot reload, recorded minimum=65%, real core=100%, stars=0; NOT A PLAYTHROUGH.");
                Core.app.exit();
                next(7);
            }
        }
    }
}
