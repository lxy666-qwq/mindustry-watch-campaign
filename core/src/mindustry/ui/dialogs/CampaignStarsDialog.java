package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.core.GameState.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

/** Mission board and briefing for the five-star campaign. */
public class CampaignStarsDialog extends BaseDialog{
    private Planet selectedPlanet;
    private Planet routePlanet;
    private ScrollPane routePane;
    private CampaignRouteView routeView;
    private final ObjectFloatMap<Planet> scrolls = new ObjectFloatMap<>();
    private final ObjectMap<SectorPreset, TextureRegion> scenes = new ObjectMap<>();
    private final ObjectMap<Planet, TextureRegion> maps = new ObjectMap<>();
    private Mode selectedMode = Mode.NORMAL;
    private BaseDialog detail;

    public CampaignStarsDialog(){
        super("@stars.title");
        shouldPause = true;
        addCloseButton();
        shown(() -> { selectedPlanet = null; rebuild(); });
        hidden(this::rememberRoute);
        onResize(this::rebuild);
    }

    private String text(String key){
        return Core.bundle.get("stars." + key);
    }

    private String modeName(Mode mode){
        return text(switch(mode){
            case NORMAL -> "normal";
            case HEROIC -> "heroic";
            case TACTICAL -> "tactical";
        });
    }

    private float contentWidth(){
        return Math.min(1160f, Core.graphics.getWidth() / Scl.scl(1f) - 48f);
    }

    private int mainMaximum(){
        return (CampaignStars.mainLevels(Planets.serpulo).size + CampaignStars.mainLevels(Planets.erekir).size) * 5;
    }

    private TextureRegion scenery(SectorPreset preset){
        return scenes.get(preset, () -> {
            String file = "route-scenes/" + preset.name + ".png";
            Core.assets.load(file, Texture.class);
            Core.assets.finishLoadingAsset(file);
            return new TextureRegion(Core.assets.get(file, Texture.class));
        });
    }

    private TextureRegion map(Planet planet){
        return maps.get(planet, () -> {
            String file = "route-maps/" + planet.name + "-v0.4.0.png";
            Core.assets.load(file, Texture.class);
            Core.assets.finishLoadingAsset(file);
            return new TextureRegion(Core.assets.get(file, Texture.class));
        });
    }

    private void rememberRoute(){
        if(routePane != null) scrolls.put(routePlanet, routePane.getScrollX());
    }

    private void choosePlanet(Planet planet){
        rememberRoute();
        selectedPlanet = planet;
        rebuild();
    }

    private void rebuild(){
        rememberRoute();
        routePane = null;
        cont.clear();
        cont.top();
        cont.table(header -> {
            header.add(Core.bundle.format("stars.mainprogress", CampaignStars.mainStars(), mainMaximum())).color(Pal.accent).pad(5f).row();
            header.add(Core.bundle.format("stars.branchprogress", CampaignStars.totalStars() - CampaignStars.mainStars())).pad(3f);
        }).growX().row();
        if(selectedPlanet == null){
            cont.add(text("route.choose")).wrap().width(contentWidth()).color(Color.lightGray).pad(12f).row();
            Table planets = new Table();
            for(var planet : new Planet[]{Planets.serpulo, Planets.erekir}){
                int branches = 0;
                for(var branch : WatchCampaign.sectors) if(branch.planet == planet) branches++;
                int count = branches;
                Button button = planets.button(card -> {
                    card.margin(18f);
                    card.image(Icon.planet).size(48f).color(planet.iconColor).row();
                    card.add(planet.localizedName).fontScale(1.5f).color(planet.iconColor).pad(10f).row();
                    card.image(map(planet)).size(360f, 120f).row();
                    card.add(Core.bundle.format("stars.route.count", CampaignStars.mainLevels(planet).size, count)).pad(8f).row();
                    card.add(text("route.open")).color(Pal.accent);
                }, () -> choosePlanet(planet)).width(Math.min(420f, contentWidth() - 20f)).height(350f).pad(8f).get();
                button.name = "route-planet-" + planet.name;
                if(contentWidth() < 800f) planets.row();
            }
            cont.pane(planets).width(contentWidth()).growY().scrollX(false);
            return;
        }
        cont.add(selectedPlanet.localizedName + " · " + text("route.title")).color(selectedPlanet.iconColor).pad(6f).row();
        cont.add(text("route.hint")).wrap().width(contentWidth()).fontScale(0.85f).color(Color.lightGray).padBottom(7f).row();
        routePlanet = selectedPlanet;
        routeView = new CampaignRouteView(selectedPlanet, map(selectedPlanet), this::show);
        routeView.name = "campaign-route-map";
        routePane = cont.pane(routeView).width(contentWidth()).growY().get();
        routePane.name = "campaign-route-pane";
        routePane.setScrollingDisabled(false, false);
        routePane.setFadeScrollBars(false);
        routePane.setOverscroll(false, false);
        ScrollPane pane = routePane;
        Planet planet = routePlanet;
        Core.app.post(() -> {
            validate();
            cont.validate();
            pane.layout();
            pane.setScrollX(scrolls.get(planet, 0f));
            pane.setScrollPercentY(0.5f);
            pane.updateVisualScroll();
        });
        cont.row();
        cont.table(actions -> {
            actions.button("@stars.route.planets", Icon.planet, () -> { selectedPlanet = null; rebuild(); }).width(160f).height(48f).pad(5f).get().name = "route-planets";
            actions.button("@stars.route.next", Icon.right, this::focusNext).width(180f).height(48f).pad(5f).get().name = "route-next";
            if(contentWidth() < 800f) actions.row();
            actions.button("@stars.tree", Icon.tree, this::showTree).width(180f).height(48f).pad(5f);
            actions.button("@stars.reward.title", Icon.info, () -> {
                BaseDialog rewards = new BaseDialog("@stars.reward.title");
                rewards.cont.pane(body -> body.add(CampaignStars.rewardSummary()).wrap().width(contentWidth() - 40f).left()).grow();
                rewards.addCloseButton(); rewards.show();
            }).width(220f).height(48f).pad(5f);
            actions.button("@stars.route.overview", Icon.map, this::showOverview).width(140f).height(48f).pad(5f).get().name = "route-overview";
        });
    }

    private void focusNext(){
        var next = routeView.route.nodes.find(node -> node.branch() >= 0 && CampaignStars.unlocked(node.preset()) && CampaignStars.stars(node.preset()) < 5);
        if(next == null) next = routeView.route.nodes.find(node -> CampaignStars.unlocked(node.preset()) && CampaignStars.stars(node.preset(), Mode.NORMAL) == 0);
        if(next == null) next = routeView.route.nodes.first();
        routePane.setScrollX(Scl.scl(next.x()) - routePane.getWidth() / 2f);
        routePane.setScrollY(routeView.getPrefHeight() - Scl.scl(next.y()) - routePane.getHeight() / 2f);
        routePane.updateVisualScroll();
    }

    private void showOverview(){
        BaseDialog overview = new BaseDialog(selectedPlanet.localizedName + " · " + text("route.overview"));
        overview.name = "route-overview-dialog";
        overview.cont.image(map(selectedPlanet)).width(contentWidth()).height(contentWidth() / 3f).row();
        overview.cont.add(text("route.overview.hint")).wrap().width(contentWidth()).color(Color.lightGray).pad(12f);
        overview.addCloseButton(); overview.show();
    }

    private void showTree(){
        BaseDialog tree = new BaseDialog("@stars.tree");
        tree.shouldPause = true;
        tree.addCloseButton();
        tree.hidden(this::rebuild);
        tree.onResize(() -> rebuildTree(tree));
        rebuildTree(tree);
        tree.show();
    }

    private void rebuildTree(BaseDialog tree){
        tree.cont.clear();
        tree.cont.add(text("tree.hint")).wrap().width(contentWidth()).color(Color.lightGray).pad(12f).row();
        tree.cont.table(planets -> {
            for(var planet : new Planet[]{Planets.serpulo, Planets.erekir}){
                planets.button(planet.localizedName, Styles.flatTogglet, () -> {
                    selectedPlanet = planet;
                    rebuildTree(tree);
                }).width(210f).height(52f).pad(4f).checked(button -> selectedPlanet == planet);
            }
        }).row();
        Seq<SectorPreset> levels = CampaignStars.mainLevels(selectedPlanet);
        for(var branch : WatchCampaign.sectors){
            if(branch.planet == selectedPlanet) levels.add(branch);
        }
        Table nodes = new Table();
        nodes.top();
        for(var preset : levels){
            if(CampaignProgression.primaryParent(preset) == null){
                addTreeNode(nodes, levels, preset, 0, tree);
            }
        }
        tree.cont.pane(nodes).width(contentWidth()).growY().scrollX(false);
    }

    private void addTreeNode(Table nodes, Seq<SectorPreset> levels, SectorPreset preset, int depth, BaseDialog tree){
        int branch = branchIndex(preset);
        nodes.table(Styles.black6, card -> {
            card.margin(10f);
            String label = (branch >= 0 ? text("branch") + " · " : "") + preset.localizedName;
            card.button(label, CampaignStars.unlocked(preset) ? Icon.terrain : Icon.lock, Styles.cleart, () -> {
                tree.hide();
                show(preset);
            }).growX().height(48f).row();
            card.table(stars -> addStars(stars, preset)).left().pad(6f).row();
            card.add(Core.bundle.format("stars.requirements", CampaignProgression.requirements(preset, Mode.NORMAL)))
                .wrap().color(Color.lightGray).growX().left().row();
            if(CampaignProgression.requiredReward(preset, Mode.HEROIC) != CampaignProgression.requiredReward(preset, Mode.NORMAL)){
                card.add(Core.bundle.format("stars.tree.challenge", CampaignProgression.requirements(preset, Mode.HEROIC)))
                    .wrap().color(Pal.accent).growX().left().padTop(7f).row();
            }
            if(branch >= 0){
                card.add(Core.bundle.format(CampaignStars.completedReward(branch) ? "stars.reward.earned" : "stars.reward", CampaignStars.rewardDescription(branch)))
                    .wrap().color(Pal.accent).growX().left().padTop(7f).row();
                card.add(Core.bundle.format("stars.tree.following", following(branch)))
                    .wrap().color(Pal.accent).growX().left().padTop(7f).row();
            }
        }).growX().padLeft(depth * 22f).padTop(5f).padBottom(branch >= 0 ? 9f : 2f).row();
        Seq<SectorPreset> children = levels.select(level -> CampaignProgression.primaryParent(level) == preset);
        children.sort((a, b) -> {
            boolean aBranch = branchIndex(a) >= 0, bBranch = branchIndex(b) >= 0;
            return aBranch == bBranch ? Integer.compare(a.id, b.id) : aBranch ? -1 : 1;
        });
        for(var child : children) addTreeNode(nodes, levels, child, depth + 1, tree);
    }

    private String following(int branch){
        Seq<String> missions = new Seq<>();
        for(var planet : new Planet[]{Planets.serpulo, Planets.erekir}){
            Seq<SectorPreset> targets = CampaignStars.mainLevels(planet);
            for(var preset : WatchCampaign.sectors) if(preset.planet == planet) targets.add(preset);
            for(var preset : targets){
                Seq<String> modes = new Seq<>();
                for(var mode : Mode.values()){
                    if(CampaignProgression.requiredReward(preset, mode) == branch) modes.add(modeName(mode));
                }
                if(!modes.isEmpty()) missions.add(preset.localizedName + " (" + modes.toString(" / ") + ")");
            }
        }
        return missions.toString("; ");
    }

    private void addStars(Table table, SectorPreset preset){
        for(var mode : Mode.values()){
            table.add(modeName(mode)).color(Color.lightGray).padLeft(mode == Mode.NORMAL ? 0f : 18f).padRight(5f);
            int best = CampaignStars.stars(preset, mode);
            for(int i = 0; i < (mode == Mode.NORMAL ? 3 : 1); i++){
                table.image(Icon.star).size(22f).color(i < best ? Pal.accent : Pal.gray).padRight(3f);
            }
        }
    }

    public void show(SectorPreset preset){
        selectedMode = Mode.NORMAL;
        detail = new BaseDialog(preset.localizedName);
        detail.name = "route-detail-" + preset.name;
        detail.shouldPause = true;
        detail.hidden(this::rebuild);
        detail.addCloseButton();
        detail.buttons.button("@stars.launch", Icon.play, () -> launch(preset)).size(210f, 64f)
            .disabled(button -> net.active() || !available(preset, selectedMode)).get().name = "route-launch";
        rebuildDetail(preset);
        detail.show();
    }

    private boolean available(SectorPreset preset, Mode mode){
        return CampaignStars.unlocked(preset) && CampaignProgression.allowed(preset, mode)
            && (mode == Mode.NORMAL || CampaignStars.stars(preset, Mode.NORMAL) > 0);
    }

    private void rebuildDetail(SectorPreset preset){
        detail.cont.clear();
        Table body = new Table();
        body.top().margin(16f);
        body.image(scenery(preset)).size(256f, 160f).padBottom(5f).row();
        body.add(text("route.terrain")).fontScale(0.8f).color(Color.lightGray).padBottom(12f).row();
        body.table(stars -> addStars(stars, preset)).left().padBottom(15f).row();
        body.table(modes -> {
            for(var mode : Mode.values()){
                modes.button(modeName(mode), Styles.flatTogglet, () -> {
                    selectedMode = mode;
                    rebuildDetail(preset);
                }).width(180f).height(54f).pad(4f).checked(button -> mode == selectedMode).get().name = "route-mode-" + mode.name();
                if(contentWidth() < 650f) modes.row();
            }
        }).left().padBottom(12f).row();
        body.add(Core.bundle.format("stars.requirements", CampaignProgression.requirements(preset, selectedMode)))
            .wrap().color(Pal.accent).growX().left().padBottom(12f).row();
        body.add(CampaignStars.describe(preset, selectedMode)).wrap().growX().left().row();
        body.add(text("replay")).wrap().color(Color.lightGray).growX().left().padTop(16f).row();
        int branch = branchIndex(preset);
        if(branch >= 0){
            if(!CampaignStars.branchUnlocked(branch)){
                body.add(Core.bundle.format("stars.requirement", WatchCampaign.starRequirements[branch], WatchCampaign.anchors[branch].localizedName))
                    .wrap().color(Pal.accent).growX().left().padTop(12f).row();
            }
            body.add(Core.bundle.format(CampaignStars.completedReward(branch) ? "stars.reward.earned" : "stars.reward", CampaignStars.rewardDescription(branch)))
                .wrap().color(Pal.accent).growX().left().padTop(12f).row();
        }
        if(selectedMode != Mode.NORMAL && CampaignStars.stars(preset, Mode.NORMAL) == 0){
            body.add(text("challenge.locked")).wrap().color(Pal.accent).growX().left().padTop(12f).row();
        }
        if(net.active()){
            body.add(text("network")).wrap().color(Pal.accent).growX().left().padTop(12f).row();
        }
        if(CampaignStars.active() && CampaignStars.currentPreset() == preset){
            body.add(Core.bundle.format("stars.current", preset.localizedName, modeName(CampaignStars.currentMode())))
                .wrap().growX().left().padTop(16f).row();
            body.add(CampaignStars.status()).wrap().growX().left().row();
        }
        detail.cont.pane(body).width(contentWidth()).growY().scrollX(false);
    }

    private int branchIndex(SectorPreset preset){
        for(int i = 0; i < WatchCampaign.sectors.length; i++){
            if(WatchCampaign.sectors[i] == preset) return i;
        }
        return -1;
    }

    private void launch(SectorPreset preset){
        if(!available(preset, selectedMode) || net.active()) return;
        Mode mode = selectedMode;
        detail.hide();
        hide();
        ui.planet.hide();
        ui.paused.hide();
        ui.loadAnd(() -> {
            if(control.saves.getCurrent() != null && state.isGame()){
                control.saves.getCurrent().save();
            }
            control.saves.resetSave();
            logic.reset();
            CampaignStars.loadMission(preset, mode);
            logic.play();
            control.saves.addSave(text("title") + " · " + preset.localizedName + " · " + modeName(mode)).setAutosave(true);
            Events.fire(Trigger.newGame);
            state.set(State.playing);
        });
    }
}
