package mindustry.content;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static mindustry.Vars.*;

/** Mission-local incidents. Before an incident, only ambient visual changes are shown. */
public class CampaignIncidents{
    private static final String prefix = "incident.";
    private static JsonValue definitions;

    private static JsonValue definition(SectorPreset preset){
        if(definitions == null) definitions = new JsonReader().parse(Core.files.internal("campaign-incidents.json"));
        return definitions.get(preset.name);
    }

    public static void begin(){
        if(WatchCampaign.level() < 0){
            definition(CampaignStars.currentPreset()).getString("kind");
            state.rules.tags.put(prefix + "version", "1");
        }
    }

    private static boolean enabled(){
        return state.isGame() && CampaignStars.active() && WatchCampaign.level() < 0 && !net.active() &&
            !state.rules.tags.getBool(CampaignStars.prefix + "native") && state.rules.tags.containsKey(prefix + "version");
    }

    private static boolean active(){
        return enabled() && state.rules.tags.getBool(prefix + "started") && !state.rules.tags.getBool(prefix + "done");
    }

    private static boolean chinese(){
        return Core.bundle.getLocale().getLanguage().equals("zh");
    }

    private static String text(String key){
        return Core.bundle.get("incident." + key);
    }

    private static String title(JsonValue event){
        return event.getString(chinese() ? "title" : "titleEn");
    }

    private static String story(JsonValue event){
        return event.getString(chinese() ? "story" : "storyEn");
    }

    public static void register(){
        Events.run(Trigger.draw, CampaignIncidents::draw);
        Events.on(GameOverEvent.class, e -> { if(active()) finish(false); });
        Events.on(ClientLoadEvent.class, e -> ui.hudGroup.fill(table -> {
            table.top().right().marginTop(155f).marginRight(8f);
            table.visible(() -> state.isGame() && active() && !state.rules.tags.getBool(CampaignStars.prefix + "finished"));
            table.button("@incident.action", Icon.warning, Styles.cleart, CampaignIncidents::show)
                .width(170f).height(42f).get().name = "campaign-incident-action";
        }));
    }

    public static void update(){
        if(!enabled() || state.rules.defaultTeam.core() == null) return;
        var tags = state.rules.tags;
        var event = definition(CampaignStars.currentPreset());
        if(!tags.getBool(prefix + "started")){
            boolean due = event.has("wave") ? state.wave > event.getInt("wave") : CampaignStars.elapsed() >= event.getFloat("after");
            if(!due) return;
            start(event);
        }
        if(!active()) return;
        if(CampaignStars.elapsed() >= tags.getFloat(prefix + "end")){
            finish();
            return;
        }
        String kind = event.getString("kind");
        if(!kind.equals("salvage") && !kind.equals("relay")) return;
        if(tags.getBool(prefix + "claimed")) return;
        int point = tags.getInt(prefix + "point");
        float x = Point2.x(point) * tilesize, y = Point2.y(point) * tilesize;
        boolean present = player != null && !player.dead() && player.team() == state.rules.defaultTeam &&
            player.unit().within(x, y, 2.5f * tilesize);
        float held = present ? tags.getFloat(prefix + "held") + Time.delta / 60f : 0f;
        tags.put(prefix + "held", Float.toString(held));
        if(held < (kind.equals("relay") ? 10f : 5f)) return;
        if(kind.equals("salvage")){
            int collected = tags.getInt(prefix + "collected") + 1;
            tags.put(prefix + "collected", Integer.toString(collected));
            grant(20, 8);
            if(collected == 3){
                tags.put(prefix + "claimed", "true");
                finish();
            }else{
                tags.put(prefix + "held", "0");
                tags.put(prefix + "point", Integer.toString(findPoint(collected)));
            }
        }else{
            tags.put(prefix + "claimed", "true");
            boost(1.25f, 1f);
            tags.put(prefix + "end", Float.toString(CampaignStars.elapsed() + 45f));
            announce(text("relay.claimed"));
        }
    }

    private static void start(JsonValue event){
        var tags = state.rules.tags;
        tags.put(prefix + "started", "true");
        tags.put(prefix + "end", Float.toString(CampaignStars.elapsed() + event.getFloat("duration")));
        switch(event.getString("kind")){
            case "salvage", "relay" -> tags.put(prefix + "point", Integer.toString(findPoint(0)));
            case "surge" -> boost(1.15f, 1.2f);
            case "jam" -> boost(0.9f, 0.85f);
            case "dispatch" -> {}
            default -> throw new IllegalArgumentException("Unknown mission incident: " + event.getString("kind"));
        }
        announce(title(event) + "\n" + story(event) + "\n" + text("kind." + event.getString("kind")));
    }

    private static int findPoint(int index){
        var core = state.rules.defaultTeam.core();
        float angle = 35f + index * 115f;
        Tile best = null;
        float score = Float.MAX_VALUE;
        for(var tile : world.tiles){
            float distance = core.dst(tile.worldx(), tile.worldy());
            if(distance < 4f * tilesize || distance > 13f * tilesize || tile.solid() || tile.build != null ||
                tile.floor().isLiquid || !tile.floor().hasSurface() || world.getDarkness(tile.x, tile.y) >= 3 ||
                state.rules.fog && !fogControl.isDiscovered(state.rules.defaultTeam, tile.x, tile.y)) continue;
            if(state.rules.waveTeam.cores().contains(enemy -> enemy.within(tile.worldx(), tile.worldy(), 35f * tilesize))) continue;
            float value = Angles.angleDist(angle, Angles.angle(core.x, core.y, tile.worldx(), tile.worldy())) + Math.abs(distance / tilesize - 8f) * 3f;
            if(value < score){ best = tile; score = value; }
        }
        //Some original tutorial bases have no empty discovered ground nearby: service the core instead.
        return best == null ? core.pos() : best.pos();
    }

    private static void boost(float build, float units){
        var team = state.rules.teams.get(state.rules.defaultTeam);
        var tags = state.rules.tags;
        tags.put(prefix + "buildBase", Float.toString(team.buildSpeedMultiplier));
        tags.put(prefix + "unitBase", Float.toString(team.unitBuildSpeedMultiplier));
        team.buildSpeedMultiplier *= build;
        team.unitBuildSpeedMultiplier *= units;
        tags.put(prefix + "buildApplied", Float.toString(team.buildSpeedMultiplier));
        tags.put(prefix + "unitApplied", Float.toString(team.unitBuildSpeedMultiplier));
        tags.put(prefix + "boost", "true");
    }

    private static void finish(){
        finish(true);
    }

    private static void finish(boolean notify){
        var tags = state.rules.tags;
        if(tags.getBool(prefix + "boost")){
            var team = state.rules.teams.get(state.rules.defaultTeam);
            //A native world processor may have changed its own rules during the interval.
            if(Mathf.equal(team.buildSpeedMultiplier, tags.getFloat(prefix + "buildApplied"))) team.buildSpeedMultiplier = tags.getFloat(prefix + "buildBase");
            if(Mathf.equal(team.unitBuildSpeedMultiplier, tags.getFloat(prefix + "unitApplied"))) team.unitBuildSpeedMultiplier = tags.getFloat(prefix + "unitBase");
            tags.put(prefix + "boost", "false");
        }
        tags.put(prefix + "done", "true");
        if(notify) announce(text("ended"));
    }

    private static void grant(int basic, int graphite){
        var core = state.rules.defaultTeam.core();
        var item = state.rules.planet == Planets.erekir ? Items.beryllium : Items.copper;
        Call.setItem(core, item, Math.min(core.getMaximumAccepted(item), core.items.get(item) + basic));
        Call.setItem(core, Items.graphite, Math.min(core.getMaximumAccepted(Items.graphite), core.items.get(Items.graphite) + graphite));
    }

    private static void announce(String message){
        if(!headless) Call.announce(message);
    }

    public static String hud(){
        if(!active()) return "";
        var event = definition(CampaignStars.currentPreset());
        var tags = state.rules.tags;
        String kind = event.getString("kind");
        var result = new StringBuilder("\n[accent]").append(title(event)).append("[] · ")
            .append(Math.max(0, Mathf.ceil(tags.getFloat(prefix + "end") - CampaignStars.elapsed()))).append(chinese() ? "秒" : "s");
        if((kind.equals("salvage") || kind.equals("relay")) && !tags.getBool(prefix + "claimed")){
            int point = tags.getInt(prefix + "point");
            result.append("\n").append(chinese() ? "标记位置 " : "Marker ").append(Point2.x(point)).append(", ").append(Point2.y(point))
                .append(" · ").append(Mathf.floor(tags.getFloat(prefix + "held"))).append(kind.equals("relay") ? "/10s" : "/5s")
                .append(kind.equals("salvage") ? " · " + tags.getInt(prefix + "collected") + "/3" : "");
        }else result.append("\n").append(text("kind." + kind));
        return result.toString();
    }

    private static void choose(int choice){
        if(!active() || state.rules.tags.getBool(prefix + "claimed")) return;
        var event = definition(CampaignStars.currentPreset());
        var core = state.rules.defaultTeam.core();
        if(core == null) return;
        var item = state.rules.planet == Planets.erekir ? Items.beryllium : Items.copper;
        int cost = event.getString("kind").equals("jam") ? 20 : 30;
        if(choice == 0){
            if(core.items.get(item) < cost) return;
            core.items.remove(item, cost);
            state.rules.tags.put(prefix + "claimed", "true");
            if(event.getString("kind").equals("jam")){ finish(); return; }
            boost(1.25f, 1f);
            state.rules.tags.put(prefix + "end", Float.toString(CampaignStars.elapsed() + 45f));
            announce(text("crew.claimed"));
        }else{
            state.rules.tags.put(prefix + "claimed", "true");
            grant(25, 10);
            finish();
        }
    }

    private static void show(){
        if(!active()) return;
        var event = definition(CampaignStars.currentPreset());
        var dialog = new BaseDialog(title(event));
        Table body = new Table();
        float width = Math.min(600f, Core.graphics.getWidth() / Scl.scl(1f) - 80f);
        body.add(story(event)).wrap().width(width).left().row();
        body.add(text("kind." + event.getString("kind"))).wrap().width(width).left().padTop(15f).row();
        if(!state.rules.tags.getBool(prefix + "claimed")){
            String kind = event.getString("kind");
            if(kind.equals("dispatch") || kind.equals("jam")){
                body.button(kind.equals("jam") ? "@incident.recalibrate" : "@incident.crew", () -> { choose(0); dialog.hide(); })
                    .width(width).height(55f).padTop(15f).disabled(button -> {
                        var core = state.rules.defaultTeam.core();
                        return !active() || core == null || core.items.get(state.rules.planet == Planets.erekir ? Items.beryllium : Items.copper) < (kind.equals("jam") ? 20 : 30);
                    }).row();
                if(kind.equals("dispatch")) body.button("@incident.stock", () -> { choose(1); dialog.hide(); }).width(width).height(55f).padTop(8f).row();
            }else if(kind.equals("salvage") || kind.equals("relay")){
                body.button("@incident.locate", () -> {
                    if(active()){
                        int point = state.rules.tags.getInt(prefix + "point");
                        control.input.panCamera(Tmp.v1.set(Point2.x(point) * tilesize, Point2.y(point) * tilesize));
                    }
                    dialog.hide();
                }).width(width).height(50f).padTop(15f).row();
            }
        }
        dialog.cont.pane(body).scrollX(false).width(width + 16f);
        dialog.addCloseButton();dialog.show();
    }

    public static float omen(){
        if(!enabled() || state.rules.tags.getBool(prefix + "started") || state.rules.tags.getBool(CampaignStars.prefix + "finished")) return 0f;
        var event = definition(CampaignStars.currentPreset());
        if(event.has("after")) return Mathf.clamp((CampaignStars.elapsed() - event.getFloat("after") + 30f) / 30f);
        if(state.wave < event.getInt("wave")) return 0f;
        return Mathf.clamp(1f - state.wavetime / Math.max(state.rules.waveSpacing, 1f));
    }

    private static void draw(){
        if(headless || !state.isGame() || !CampaignStars.active() || state.rules.tags.getBool(CampaignStars.prefix + "native")) return;
        float strength = WatchCampaign.level() >= 0 ? WatchCampaignEvents.omen() : omen();
        if(strength > 0f){
            var tint = switch(CampaignStars.terrain(CampaignStars.currentPreset())){
                case FROZEN -> Color.valueOf("bddeee");
                case NAVAL -> Color.valueOf("6bacc7");
                case SPORE -> Color.valueOf("a48db8");
                case VOLCANIC -> Color.valueOf("d77a4d");
                default -> Color.valueOf("c7af75");
            };
            Draw.z(Layer.weather);
            Draw.color(tint, 0.065f * strength);
            Fill.rect(Core.camera.position.x, Core.camera.position.y, Core.camera.width, Core.camera.height);
            for(int i = 0; i < 4; i++){
                Draw.color(tint, strength * 0.035f);
                Fill.circle(Core.camera.position.x + Mathf.sin(Time.time * 0.0015f + i * 1.7f) * Core.camera.width * 0.6f,
                    Core.camera.position.y + Mathf.cos(Time.time * 0.002f + i * 2f) * Core.camera.height * 0.6f,
                    Core.camera.width * (0.08f + strength * 0.06f));
            }
            for(int i = 0; i < 22; i++){
                float x = Core.camera.position.x + Mathf.sin(i * 13f + Time.time * 0.004f) * Core.camera.width * 0.55f;
                float y = Core.camera.position.y + Mathf.cos(i * 7f + Time.time * 0.006f) * Core.camera.height * 0.55f;
                Draw.color(tint, strength * (0.07f + Mathf.absin(Time.time + i * 11f, 11f, 0.12f)));
                Fill.circle(x, y, 1.5f + i % 4);
            }
            Draw.reset();
        }
        if(active() && !state.rules.tags.getBool(prefix + "claimed") && state.rules.tags.containsKey(prefix + "point")){
            int point = state.rules.tags.getInt(prefix + "point");
            Draw.z(Layer.blockOver);
            Draw.color();
            Draw.rect(definition(CampaignStars.currentPreset()).getString("kind").equals("salvage") ? Blocks.container.region : Blocks.powerNode.region,
                Point2.x(point) * tilesize, Point2.y(point) * tilesize, 12f, 12f);
            Draw.z(Layer.overlayUI);
            Draw.color(Pal.accent);
            Lines.stroke(1.3f);
            Lines.circle(Point2.x(point) * tilesize, Point2.y(point) * tilesize, (2.5f + Mathf.absin(8f, 0.25f)) * tilesize);
            Draw.reset();
        }
    }
}
