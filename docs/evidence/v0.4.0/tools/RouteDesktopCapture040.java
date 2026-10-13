import arc.*;
import arc.backend.sdl.*;
import arc.files.*;
import arc.math.geom.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.desktop.*;
import mindustry.gen.*;
import mindustry.ui.*;
import static mindustry.Vars.*;

/** Real SDL navigation and save smoke checks on an isolated copy of genuine progress. */
public class RouteDesktopCapture040 extends DesktopLauncher{
    private final Fi output = new Fi(System.getProperty("route.capture.output"));
    private int step;
    private long started, changed;
    private final ObjectIntMap<String> scores = new ObjectIntMap<>();
    private final StringBuilder result = new StringBuilder();
    public RouteDesktopCapture040(){ super(new String[0]); }
    public static void main(String[] args){
        System.setProperty("nodiscord", "true");
        Vars.loadLogger();
        Settings preferences = new Settings();
        preferences.setDataDirectory(new Fi(System.getProperty("mindustry.data.dir")));
        preferences.load();
        preferences.put("locale", System.getProperty("route.locale", "zh_CN"));
        preferences.put("uiscale", 100);
        preferences.manualSave();
        new SdlApplication(new RouteDesktopCapture040(), new SdlConfig(){{
            title = "Mindustry v0.4.0 actual route navigation";
            width = Integer.getInteger("route.width", 1280); height = Integer.getInteger("route.height", 900); maximized = false;
        }});
    }
    private void require(boolean condition, String message){ if(!condition) throw new IllegalStateException("Route desktop: " + message); }
    private float age(){ return (System.nanoTime() - changed) / 1e9f; }
    private void next(int value){ step = value; changed = System.nanoTime(); }
    private void shot(String name){ output.mkdirs(); ScreenUtils.saveScreenshot(output.child(name + ".png")); result.append("image=").append(name).append('\n'); }
    private void click(String name){
        Element element = Core.scene.find(name);
        require(element != null, "missing element " + name);
        element.fireClick(); result.append("click=").append(name).append('\n');
    }
    private CampaignRouteView view(){ return (CampaignRouteView)Core.scene.find("campaign-route-map"); }
    private ScrollPane pane(){ return (ScrollPane)Core.scene.find("campaign-route-pane"); }
    private void locate(String preset){
        var node = view().route.nodes.find(item -> item.preset().name.equals(preset));
        require(node != null, "missing route node " + preset);
        pane().setScrollX(node.x() * arc.scene.ui.layout.Scl.scl(1f) - pane().getWidth() / 2f);
        pane().setScrollY(view().getPrefHeight() - node.y() * arc.scene.ui.layout.Scl.scl(1f) - pane().getHeight() / 2f); pane().updateVisualScroll();
    }
    private void visibleNode(String preset){
        Element element = Core.scene.find("route-" + preset);
        Vec2 center = element.localToStageCoordinates(new Vec2(element.getWidth() / 2f, element.getHeight() / 2f));
        Element hit = Core.scene.hit(center.x, center.y, true);
        require(hit == element || hit != null && hit.isDescendantOf(element), "node is not hit-testable " + preset);
    }
    private void clickVisibleNode(String preset){
        visibleNode(preset);
        click("route-" + preset);
    }
    private void dragRoute(){
        ScrollPane map = pane();
        float before = map.getScrollX();
        Vec2 point = map.localToStageCoordinates(new Vec2(map.getWidth() * 0.7f, map.getHeight() * 0.9f));
        int sx = Math.round(point.x), sy = Math.round(Core.graphics.getHeight() - point.y);
        require(Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft), "map did not accept pointer down");
        Core.scene.touchDragged(sx - 80, sy, 0);
        Core.scene.touchDragged(sx - 200, sy, 0);
        require(map.getScrollX() > before + 100f, "pointer drag did not scroll horizontally");
        result.append("pointerDragPixels=").append(map.getScrollX() - before).append('\n');
        map.cancel();
        Core.scene.touchUp(sx - 200, sy, 0, KeyCode.mouseLeft);
        map.setScrollX(0f); map.updateVisualScroll();
    }
    private void preserveScores(){
        for(var preset : content.sectors()) if(CampaignStars.eligible(preset)) for(var mode : Mode.values()){
            String key = CampaignStars.progressKey(preset, mode);
            require(scores.get(key, -1) == CampaignStars.stars(preset, mode), "changed score " + key);
        }
    }
    @Override public void update(){
        super.update();
        if(!clientLoaded) return;
        if(started == 0) started = changed = System.nanoTime();
        require((System.nanoTime() - started) / 1e9f < 120f, "timeout");
        if(age() < 1.5f) return;
        switch(step){
            case 0 -> {
                require(WatchCampaign.version.equals("0.4.0"), "incorrect runtime");
                Core.scene.root.getChildren().copy().each(e -> { if(e instanceof Dialog d) d.hide(); });
                for(var preset : content.sectors()) if(CampaignStars.eligible(preset)) for(var mode : Mode.values()) scores.put(CampaignStars.progressKey(preset, mode), CampaignStars.stars(preset, mode));
                result.append("version=0.4.0\nlocale=").append(Core.bundle.getLocale()).append("\nmainStars=").append(CampaignStars.mainStars()).append("\ntotalStars=").append(CampaignStars.totalStars()).append("\nscoreKeys=").append(scores.size).append('\n');
                WatchCampaign.starsDialog.show(); next(1);
            }
            case 1 -> { shot("01-planet-chooser"); click("route-planet-serpulo"); next(2); }
            case 2 -> {
                require(view().route.nodes.size == 35, "Serpulo coverage");
                visibleNode("groundZero");
                dragRoute();
                shot("02-serpulo-start");
                click("route-overview"); next(30);
            }
            case 3 -> {
                require(!((Button)Core.scene.find("route-launch")).isDisabled(), "starting mission blocked");
                shot("03-ground-zero-briefing");
                ((Dialog)Core.scene.find("route-detail-groundZero")).hide(); next(4);
            }
            case 4 -> { locate("windsweptIslands"); next(5); }
            case 5 -> { shot("04-serpulo-islands"); locate("watch-6"); next(6); }
            case 6 -> { clickVisibleNode("watch-6"); next(7); }
            case 7 -> {
                require(((Button)Core.scene.find("route-launch")).isDisabled(), "locked side mission enabled");
                click("route-mode-TACTICAL"); next(8);
            }
            case 8 -> { shot("05-locked-tide-tactical"); ((Dialog)Core.scene.find("route-detail-watch-6")).hide(); next(9); }
            case 9 -> { click("route-planets"); next(10); }
            case 10 -> { click("route-planet-erekir"); next(11); }
            case 11 -> {
                require(view().route.nodes.size == 21, "Erekir coverage");
                shot("06-erekir-start"); click("route-overview"); next(32);
            }
            case 12 -> { shot("07-erekir-caldera"); Core.graphics.setWindowSize(960, 720); next(13); }
            case 13 -> { shot("08-compact-route"); Core.graphics.setWindowSize(700, 900); next(14); }
            case 14 -> { click("route-planets"); next(15); }
            case 15 -> {
                shot("09-portrait-chooser"); Core.graphics.setWindowSize(1280, 900); next(16);
            }
            case 16 -> { click("route-planet-serpulo"); next(17); }
            case 17 -> { click("route-next"); shot("10-continue-route"); preserveScores(); locate("groundZero"); next(18); }
            case 18 -> { clickVisibleNode("groundZero"); next(19); }
            case 19 -> { click("route-launch"); next(20); }
            case 20 -> {
                require(CampaignStars.active() && CampaignStars.currentPreset() == SectorPresets.groundZero && CampaignStars.currentMode() == Mode.NORMAL, "actual launch did not open normal mission");
                require(!state.gameOver, "immediate mission failure");
                shot("11-route-launched-native-mission");
                control.saves.getCurrent().save(); preserveScores();
                result.append("nativeMission=groundZero\nmode=NORMAL\nlaunchAndSave=true\nscoreKeysUnchanged=168\nnewVictories=0\n");
                output.child("result.txt").writeString(result.toString());
                Core.app.exit(); next(21);
            }
            case 30 -> { shot("12-serpulo-full-panorama"); ((Dialog)Core.scene.find("route-overview-dialog")).hide(); next(31); }
            case 31 -> { clickVisibleNode("groundZero"); next(3); }
            case 32 -> { shot("13-erekir-full-panorama"); ((Dialog)Core.scene.find("route-overview-dialog")).hide(); locate("caldera-erekir"); next(12); }
        }
    }
}
