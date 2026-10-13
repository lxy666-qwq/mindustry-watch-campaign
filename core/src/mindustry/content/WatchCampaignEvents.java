package mindustry.content;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.planet.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;

import static mindustry.Vars.*;
import static mindustry.content.WatchCampaign.*;

/** State is stored in Rules.tags, alongside the map, entities and wave counter in normal saves. */
public class WatchCampaignEvents{
    private static final String prefix = "watch.event.", powerPrefix = "watch.power.";
    private static int shownWave = -1;
    private static Rules shownRules;

    public static void register(){
        Events.on(WorldLoadEvent.class, e -> {
            shownWave = -1;
            shownRules = null;
        });
        Events.on(ResetEvent.class, e -> {
            shownWave = -1;
            shownRules = null;
        });
        Events.run(Trigger.update, () -> {
            if(!state.isGame() || level() < 0) return;
            if(!net.client() && blackoutActive()) enforceBlackout();
            if(!headless && (shownRules != state.rules || shownWave != state.wave)) refreshHud();
        });

    }

    public static String key(Encounter event){
        return prefix + event.wave() + "." + event.change().name();
    }

    public static boolean done(Encounter event){
        return state.rules.tags.getBool(key(event));
    }

    /** Called before spawning, so a route opened on wave 7 is already usable by wave 7's enemies. */
    public static void beforeWave(){
        int level = level();
        if(level < 0 || net.client() || !state.rules.waves) return;
        for(var event : stages[level].encounters()){
            if(state.wave >= event.wave() && !done(event)){
                apply(level, event.change());
                state.rules.tags.put(key(event), "true");
                if(!headless || net.server()) Call.announce(Core.bundle.format("watch.event", event.wave(), text("event." + event.change().name())));
            }
        }
        if(!headless || net.server()) Call.setRules(state.rules);
    }

    public static boolean blackoutActive(){
        return state.rules.tags.getBool("watch.blackout");
    }

    private static void apply(int level, Change change){
        int size = world.width();
        switch(change){
            case supply -> {
                var core = state.rules.defaultTeam.core();
                if(core != null){
                    grant(core, Items.copper, 350);
                    grant(core, Items.graphite, 120);
                    if(level >= 2) grant(core, Items.titanium, 150);
                }
            }
            case flank, fast -> openFlank();
            case thaw -> WatchMapGenerator.paintFord(world.tiles, Blocks.water, true);
            case flood -> {
                WatchMapGenerator.paintFord(world.tiles, Blocks.deepwater, true);
                Call.createWeather(Weathers.rain, 0.6f, state.rules.waveSpacing * 3f, 0.3f, -0.5f);
            }
            case snow -> Call.createWeather(Weathers.snow, 0.7f, state.rules.waveSpacing * 3f, 0.4f, -0.5f);
            case sandstorm -> {
                state.rules.tags.put("watch.solarBeforeStorm", Float.toString(state.rules.solarMultiplier));
                state.rules.solarMultiplier = 0.15f;
                Call.createWeather(Weathers.sandstorm, 0.5f, state.rules.waveSpacing * 4f, 0.7f, -0.2f);
            }
            case calm -> {
                state.rules.solarMultiplier = state.rules.tags.getFloat("watch.solarBeforeStorm", 1f);
                Weathers.sandstorm.remove();
            }
            case lowTide -> {
                openFlank();
                var base = WatchMapGenerator.core(size);
                //A fresh beach expands the quay but preserves the naval approach.
                for(int x = base.x - 32; x <= base.x - 24; x++){
                    for(int y = size / 2; y <= size * 3 / 5; y++){
                        var tile = world.tile(x, y);
                        if(tile.build == null && tile.floor().isLiquid) tile.setFloorNet(Blocks.sand);
                    }
                }
            }
            case spores -> Call.createWeather(Weathers.sporestorm, 0.55f, state.rules.waveSpacing * 4f, 0.4f, -0.3f);
            case salvage -> {
                var cache = WatchMapGenerator.cache(size);
                for(int x = cache.x - 7; x <= cache.x + 7; x++) clearNatural(world.tile(x, cache.y + 9));
                var core = state.rules.defaultTeam.core();
                if(core != null){
                    grant(core, Items.coal, 180);
                    grant(core, Items.silicon, 120);
                }
            }
            case blackout -> {
                state.rules.tags.put("watch.blackout", "true");
                enforceBlackout();
            }
            case powerRestored -> {
                state.rules.tags.put("watch.blackout", "false");
                restorePower();
            }
            case eruption -> {
                WatchMapGenerator.paintFord(world.tiles, Blocks.slag, true);
                openFlank();
            }
            //These transitions use the scheduled SpawnGroups, rather than spawning untracked extra enemies.
            case air, navy, armor, swarm, boss -> {}
        }
    }

    private static void grant(Building core, mindustry.type.Item item, int amount){
        Call.setItem(core, item, Math.min(core.getMaximumAccepted(item), core.items.get(item) + amount));
    }

    private static void clearNatural(Tile tile){
        if(tile.block().isStatic()) tile.setNet(Blocks.air, mindustry.game.Team.derelict, 0);
    }

    private static void openFlank(){
        var gate = WatchMapGenerator.gate(world.width());
        for(int x = gate.x - 3; x <= gate.x + 3; x++){
            for(int y = gate.y - 7; y <= gate.y + 7; y++) clearNatural(world.tile(x, y));
        }
    }

    /** Batteries and item turrets keep working; every fuel/thermal/solar generator is temporarily disabled. */
    public static void enforceBlackout(){
        Groups.build.each(build -> {
            if(build.team == state.rules.defaultTeam && build.block instanceof PowerGenerator && build.enabled){
                state.rules.tags.put(powerPrefix + build.pos(), build.block.name);
                build.enabled = false;
            }
        });
    }

    private static void restorePower(){
        Seq<String> remove = new Seq<>();
        for(var entry : state.rules.tags.entries()){
            if(entry.key.startsWith(powerPrefix)){
                var build = world.build(Integer.parseInt(entry.key.substring(powerPrefix.length())));
                if(build != null && build.team == state.rules.defaultTeam && build.block.name.equals(entry.value)) build.enabled = true;
                remove.add(entry.key);
            }
        }
        remove.each(state.rules.tags::remove);
    }

    public static void refreshHud(){
        int level = level();
        if(level < 0 || headless) return;
        var stage = stages[level];
        StringBuilder message = new StringBuilder("[accent]").append(name(level)).append("[]\n")
            .append(Core.bundle.format("watch.goal", stage.waves())).append("\n");
        for(var event : stage.encounters()){
            if(!done(event)){
                message.append("[yellow]").append(Core.bundle.format("watch.next", event.wave(), text("event." + event.change().name())));
                break;
            }
        }
        if(CampaignStars.active()) message.append("\n").append(CampaignStars.status());
        if(blackoutActive()) message.append("\n[scarlet]").append(text("power.tip"));
        ui.hudfrag.setHudText(message.toString());
        shownWave = state.wave;
        shownRules = state.rules;
    }
}
