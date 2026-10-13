package mindustry.content;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.game.MapObjectives.*;
import mindustry.game.Objectives.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.power.PowerGenerator.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.production.BeamDrill.*;
import mindustry.world.blocks.production.Pump.*;
import mindustry.world.blocks.production.GenericCrafter.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.defense.turrets.LiquidTurret.*;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.consumers.ConsumeLiquidBase;

import java.io.*;

import static mindustry.Vars.*;

/** Five-star missions keep progress in settings and run state in ordinary, isolated saves. */
public class CampaignStars{
    public static final String prefix = "stars.";
    private static final String progressPrefix = "watch-stars-v1.";
    private static boolean loadingMission;
    private static JsonValue missionMetadata;
    private static float hudTime;
    private static Rules hudRules;
    private static final ObjectMap<Building, Integer> carriedAssets = new ObjectMap<>();
    private static Rules flowRules;
    private static final ObjectMap<String, ObjectSet<String>> incomingFlows = new ObjectMap<>();
    private static final IntMap<ObjectSet<String>> receivedInputs = new IntMap<>();
    private static final ObjectSet<String> itemOrigins = new ObjectSet<>();
    private static final IntSet coreAssets = new IntSet();

    public record MissionLimits(float deadline, int buildBudget, int lossBudget){}

    public enum Mode{
        NORMAL(3), HEROIC(1), TACTICAL(1);

        public final int maximum;

        Mode(int maximum){
            this.maximum = maximum;
        }
    }

    public enum Terrain{
        VALLEY, FROZEN, NAVAL, SPORE, INDUSTRIAL, VOLCANIC, EREKIR;
    }

    public static void register(){
        SaveVersion.addCustomChunk("watch-stars-ledger", new CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return active();
            }

            @Override
            public boolean writeNet(){
                return false;
            }

            @Override
            public void write(DataOutput stream) throws IOException{
                var keys = state.rules.tags.keys().toSeq().select(CampaignStars::ledgerTag);
                stream.writeInt(1);
                stream.writeInt(keys.size);
                for(var key : keys){
                    stream.writeUTF(key);
                    stream.writeUTF(state.rules.tags.get(key));
                }
            }

            @Override
            public void read(DataInput stream) throws IOException{
                int version = stream.readInt();
                if(version != 1) throw new IOException("Unsupported five-star ledger version: " + version);
                int count = stream.readInt();
                for(int i = 0; i < count; i++) state.rules.tags.put(stream.readUTF(), stream.readUTF());
            }
        });
        Events.on(WorldLoadEvent.class, e -> {
            if(loadingMission || active() || !state.isCampaign()) return;
            var preset = state.rules.sector.preset;
            if(eligible(preset) && !preset.sector.info.wasCaptured){
                beginRun(preset, Mode.NORMAL);
                //An older uninstrumented save has no evidence of earlier damage or core losses.
                state.rules.tags.put(prefix + "partial", Boolean.toString(state.wave > 1 || state.tick > 1f));
                state.rules.tags.put(prefix + "native", "true");
            }
        });
        Events.run(Trigger.update, CampaignStars::update);
        Events.on(BlockDestroyEvent.class, e -> {
            if(running()) state.rules.tags.remove(prefix + "asset." + e.tile.pos());
            if(running() && e.tile.team() == state.rules.defaultTeam && e.tile.block() instanceof CoreBlock){
                state.rules.tags.put(prefix + "coreLosses", Integer.toString(coreLosses() + 1));
                recordCoreHealth(0f);
            }
        });
        Events.on(BlockBuildEndEvent.class, e -> {
            if(running() && e.team == state.rules.defaultTeam){
                if(!e.breaking){
                    countPlacement();
                    newAsset(e.tile.pos(), true);
                }else{
                    state.rules.tags.remove(prefix + "asset." + e.tile.pos());
                }
            }
        });
        Events.on(PickupEvent.class, e -> {
            if(running() && e.build != null && e.build.team == state.rules.defaultTeam){
                int token = asset(e.build);
                if(token != 0) carriedAssets.put(e.build, token);
                state.rules.tags.remove(prefix + "asset." + e.build.pos());
            }
        });
        Events.on(SaveWriteEvent.class, e -> {
            if(active() && !net.active()) writeCarriedAssets();
        });
        Events.on(SaveLoadEvent.class, e -> {
            carriedAssets.clear();
            if(active() && !net.active()){
                readCarriedAssets();
                if(currentMode() == Mode.TACTICAL) loadFlows();
            }
        });
        Events.on(GameOverEvent.class, e -> {
            if(!running()) return;
            if(e.winner == state.rules.defaultTeam){
                recordVictory();
            }else{
                state.rules.tags.put(prefix + "failed", "true");
                state.rules.tags.put(prefix + "finished", "true");
                state.rules.tags.put(prefix + "earned", "0");
            }
        });
        Events.on(SectorCaptureEvent.class, e -> {
            if(!net.client() && e.initialCapture && running() && currentPreset() == e.sector.preset){
                recordVictory();
            }
        });
    }

    /** Per-asset and resource-flow records are stored outside the 64 KiB rules metadata string. */
    private static boolean ledgerTag(String key){
        return key.startsWith(prefix) && key.indexOf('.', prefix.length()) >= 0;
    }

    public static String rulesForSave(Rules rules){
        if(!rules.tags.keys().toSeq().contains(CampaignStars::ledgerTag)) return JsonIO.write(rules);
        var copy = rules.copy();
        copy.retainContentFields(rules);
        copy.tags.keys().toSeq().each(key -> {
            if(ledgerTag(key)) copy.tags.remove(key);
        });
        return JsonIO.write(copy);
    }

    /** Only official technology-tree main levels and the ten relocated Watch branches participate. */
    public static boolean eligible(SectorPreset preset){
        if(preset == null || preset.minfo.mod != null) return false;
        if(branchIndex(preset) >= 0) return true;
        return (preset.planet == Planets.serpulo || preset.planet == Planets.erekir) &&
            preset.techNode != null && !preset.name.startsWith("sector-") &&
            !preset.name.equals("fallenVessel") && !preset.name.equals("geothermalStronghold") && !preset.name.equals("cruxscape");
    }

    public static Seq<SectorPreset> mainLevels(Planet planet){
        return content.sectors().select(p -> p.planet == planet && eligible(p) && branchIndex(p) < 0);
    }

    public static int branchIndex(SectorPreset preset){
        for(int i = 0; i < WatchCampaign.sectors.length; i++){
            if(WatchCampaign.sectors[i] == preset) return i;
        }
        return -1;
    }

    public static String progressKey(SectorPreset preset, Mode mode){
        return progressPrefix + preset.name + "." + mode.name();
    }

    public static int stars(SectorPreset preset, Mode mode){
        int score = Core.settings.getInt(progressKey(preset, mode), 0);
        if(mode == Mode.NORMAL && branchIndex(preset) < 0 && preset.sector.info.wasCaptured) score = Math.max(score, 1);
        return score;
    }

    public static int stars(SectorPreset preset){
        return stars(preset, Mode.NORMAL) + stars(preset, Mode.HEROIC) + stars(preset, Mode.TACTICAL);
    }

    /** Scores only improve; a worse replay cannot remove stars or unlock a reward twice. */
    public static int saveBest(SectorPreset preset, Mode mode, int earned){
        if(!eligible(preset) || earned < 0 || earned > mode.maximum) throw new IllegalArgumentException("Invalid mission score: " + earned);
        int best = Math.max(stars(preset, mode), earned);
        Core.settings.put(progressKey(preset, mode), best);
        Core.settings.manualSave();
        return best;
    }

    public static int mainStars(){
        int total = 0;
        for(var planet : new Planet[]{Planets.serpulo, Planets.erekir}){
            for(var preset : mainLevels(planet)) total += stars(preset);
        }
        return total;
    }

    public static int totalStars(){
        int total = mainStars();
        for(var branch : WatchCampaign.sectors) total += stars(branch);
        return total;
    }

    public static boolean normalComplete(SectorPreset preset){
        return stars(preset, Mode.NORMAL) >= 1;
    }

    public static boolean branchUnlocked(int index){
        return mainStars() >= WatchCampaign.starRequirements[index] && normalComplete(WatchCampaign.anchors[index]);
    }

    public static boolean completedReward(int index){
        return stars(WatchCampaign.sectors[index]) == 5;
    }

    /** The star board follows original capture dependencies, without requiring global research purchases. */
    public static boolean unlocked(SectorPreset preset){
        int branch = branchIndex(preset);
        if(branch >= 0) return branchUnlocked(branch) && CampaignProgression.allowed(preset, Mode.NORMAL);
        if(!eligible(preset)) return false;
        if(!CampaignProgression.allowed(preset, Mode.NORMAL)) return false;
        if(preset.alwaysUnlocked || normalComplete(preset)) return true;
        for(var parent : CampaignProgression.parents(preset)) if(!normalComplete(parent)) return false;
        return true;
    }

    /** Loads the original map into a detached sector, then removes campaign save/export ownership. */
    public static void loadMission(SectorPreset preset, Mode mode){
        if(!eligible(preset)) throw new IllegalArgumentException("Unsupported star mission: " + preset.name);
        var detached = new Sector(preset.planet, preset.sector.tile){
            @Override
            public boolean allowLaunchLoadout(){ return false; }

            @Override
            public boolean allowLaunchSchematics(){ return false; }
        };
        detached.preset = preset;
        var params = new WorldParams();
        params.saveInfo = false;
        loadingMission = true;
        try{
            world.loadSector(detached, params);
        }finally{
            loadingMission = false;
        }
        state.rules.sector = null;
        state.rules.planet = preset.planet;
        beginRun(preset, mode);
        addMissionResearch(preset);
        WatchRewardBlocks.applyResearch(state.rules);
        var enemy = state.rules.teams.get(state.rules.waveTeam);
        if(mode == Mode.HEROIC){
            enemy.unitHealthMultiplier *= 1.25f;
            enemy.unitDamageMultiplier *= 1.15f;
            enemy.blockDamageMultiplier *= 1.15f;
            enemy.blockHealthMultiplier *= 1.1f;
            state.rules.waveSpacing *= 0.85f;
        }else if(mode == Mode.TACTICAL){
            enemy.unitHealthMultiplier *= 1.12f;
            enemy.unitDamageMultiplier *= 1.08f;
            enemy.blockDamageMultiplier *= 1.08f;
            state.rules.waveSpacing *= 0.95f;
        }
        //Intersect must finish its wave phase and then its original enemy-base assault.
        if(preset.attackAfterWaves){
            state.rules.tags.put(prefix + "attackAfterWaves", "true");
            state.rules.tags.put(prefix + "phaseWave", Integer.toString(state.rules.winWave));
            state.rules.winWave = 0;
        }
        applyRewards(state.rules);
        CampaignProgression.applyRewards(state.rules);
    }

    private static void beginRun(SectorPreset preset, Mode mode){
        carriedAssets.clear();
        var tags = state.rules.tags;
        tags.put(prefix + "preset", preset.name);
        tags.put(prefix + "mode", mode.name());
        tags.put(prefix + "elapsed", "0");
        tags.put(prefix + "minCore", "1");
        tags.put(prefix + "coreLosses", "0");
        tags.put(prefix + "placed", "0");
        tags.put(prefix + "finished", "false");
        tags.put(prefix + "failed", "false");
        tags.put(prefix + "partial", "false");
        tags.put(prefix + "native", "false");
        tags.put(prefix + "earned", "0");
        tags.put(prefix + "lossBudget", Integer.toString(lossBudget(preset)));
        var limits = limits(preset);
        tags.put(prefix + "buildBudget", Integer.toString(limits.buildBudget));
        tags.put(prefix + "deadline", Float.toString(limits.deadline));
        tags.put(prefix + "assetNext", "0");
        tags.put(prefix + "taskAStart", "0");
        tags.put(prefix + "flowVersion", "2");
        for(var building : state.rules.defaultTeam.data().buildings) newAsset(building.pos(), false);
        tags.put(prefix + "taskBStart", "0");
    }

    private static void addMissionResearch(SectorPreset preset){
        if(branchIndex(preset) >= 0) return; //Watch retains its equipment whitelist.
        for(var unlockable : content.blocks()){
            if(unlockable.unlocked() && unlockable.isOnPlanet(preset.planet)) state.rules.researched.add(unlockable);
        }
        for(var unit : content.units()){
            if(unit.unlocked() && unit.isOnPlanet(preset.planet)) state.rules.researched.add(unit);
        }
        addResearchNode(preset.planet.techTree, preset);
        for(var objective : state.rules.objectives){
            if(objective instanceof ResearchObjective research) state.rules.researched.add(research.content);
            if(objective instanceof ProduceObjective produce) state.rules.researched.add(produce.content);
            if(objective instanceof BuildCountObjective build) state.rules.researched.add(build.block);
            if(objective instanceof UnitCountObjective units) state.rules.researched.add(units.unit);
        }
    }

    private static void addResearchNode(TechTree.TechNode node, SectorPreset mission){
        for(var objective : node.objectives){
            if(objective instanceof SectorComplete capture && capture.preset != mission && eligible(capture.preset) && !normalComplete(capture.preset)) return;
            if(objective instanceof OnSector on && on.preset != mission && eligible(on.preset) && !normalComplete(on.preset)) return;
        }
        state.rules.researched.add(node.content);
        for(var child : node.children) addResearchNode(child, mission);
    }

    public static boolean active(){
        return state.rules.tags.containsKey(prefix + "preset");
    }

    private static boolean running(){
        return active() && !net.active() && !state.rules.tags.getBool(prefix + "finished");
    }

    private static boolean tacticalRunning(){
        return running() && currentMode() == Mode.TACTICAL;
    }

    private static void countPlacement(){
        state.rules.tags.put(prefix + "placed", Integer.toString(placed() + 1));
    }

    private static int newAsset(int position, boolean constructed){
        int token = state.rules.tags.getInt(prefix + "assetNext", 0) + 1;
        state.rules.tags.put(prefix + "assetNext", Integer.toString(token));
        state.rules.tags.put(prefix + "asset." + position, Integer.toString(token));
        if(constructed) state.rules.tags.put(prefix + "constructed." + token, "true");
        return token;
    }

    private static int asset(Building building){
        return state.rules.tags.getInt(prefix + "asset." + building.pos(), 0);
    }

    /** Stable mission identity also follows non-updating walls through payload saves and relocation. */
    public static int identity(Building building){
        int token = asset(building);
        return token == 0 ? newAsset(building.pos(), false) : token;
    }

    /** The one native ground-placement entry for a building payload. */
    public static void recordPayloadPlacement(Building building){
        if(!running() || building.team != state.rules.defaultTeam) return;
        countPlacement();
        int token = carriedAssets.get(building, 0);
        carriedAssets.remove(building);
        //Factory-created or untracked payloads are not new player constructions.
        if(token == 0) newAsset(building.pos(), false);
        else state.rules.tags.put(prefix + "asset." + building.pos(), Integer.toString(token));
    }

    private static boolean newConstruction(Building building){
        return state.rules.tags.getBool(prefix + "constructed." + asset(building));
    }

    //Unit IDs and ordered payload lists are serialized by native SaveVersion/PayloadUnit.
    //Snapshot the actual live payload objects at save time; indices can change between saves.
    private static void writeCarriedAssets(){
        state.rules.tags.keys().toSeq().each(key -> {
            if(key.startsWith(prefix + "carried.")) state.rules.tags.remove(key);
        });
        for(var unit : Groups.unit){
            if(unit instanceof Payloadc carrier){
                for(int index = 0; index < carrier.payloads().size; index++){
                    writePayloadAsset(carrier.payloads().get(index), prefix + "carried.unit." + unit.id + "." + index);
                }
            }
        }
        for(var holder : state.rules.defaultTeam.data().buildings){
            writePayloadAsset(holder.getPayload(), prefix + "carried.block." + holder.pos());
        }
    }

    private static void writePayloadAsset(Payload payload, String key){
        if(payload instanceof BuildPayload building){
            if(carriedAssets.containsKey(building.build)) state.rules.tags.put(key, Integer.toString(carriedAssets.get(building.build)));
            writePayloadAsset(building.build.getPayload(), key + ".payload");
        }
    }

    private static void readCarriedAssets(){
        for(var unit : Groups.unit){
            if(unit instanceof Payloadc carrier){
                for(int index = 0; index < carrier.payloads().size; index++){
                    readPayloadAsset(carrier.payloads().get(index), prefix + "carried.unit." + unit.id + "." + index);
                }
            }
        }
        for(var holder : state.rules.defaultTeam.data().buildings){
            readPayloadAsset(holder.getPayload(), prefix + "carried.block." + holder.pos());
        }
    }

    private static void readPayloadAsset(Payload payload, String key){
        if(payload instanceof BuildPayload building){
            int token = state.rules.tags.getInt(key, 0);
            if(token != 0) carriedAssets.put(building.build, token);
            readPayloadAsset(building.build.getPayload(), key + ".payload");
        }
    }

    /** Called only after a native conveyor, duct or conduit successfully transfers a resource. */
    public static void recordTransfer(Building source){
        if(tacticalRunning() && source.team == state.rules.defaultTeam && newConstruction(source)){
            state.rules.tags.put(prefix + "transported." + asset(source), "true");
        }
    }

    //Every edge is an actual native resource handoff, not a guessed proximity connection.
    //Item junction directions are separate nodes; their crossing lanes do not mix.
    private static String flowNode(Building building, Building other, String resource, boolean outgoing){
        int lane = 4;
        if(building instanceof Junction.JunctionBuild || building instanceof DuctJunction.DuctJunctionBuild){
            int side = building.relativeToEdge(other.tile);
            lane = outgoing ? side : (side + 2) % 4;
        }
        return resource + ":" + identity(building) + ":" + lane;
    }

    private static int flowAsset(String node){
        int first = node.indexOf(':');
        return Integer.parseInt(node.substring(first + 1, node.indexOf(':', first + 1)));
    }

    private static void loadFlows(){
        if(flowRules == state.rules) return;
        flowRules = state.rules;
        incomingFlows.clear();
        receivedInputs.clear();
        itemOrigins.clear();
        coreAssets.clear();
        if(state.rules.tags.getInt(prefix + "flowVersion", 0) < 2){
            //Earlier runs could credit core-return branches without a real resource origin.
            state.rules.tags.keys().toSeq().each(key -> {
                if(key.startsWith(prefix + "flow.") || key.startsWith(prefix + "received.") ||
                    key.startsWith(prefix + "transferred.") || key.startsWith(prefix + "origin.") ||
                    key.startsWith(prefix + "core.")) state.rules.tags.remove(key);
            });
            state.rules.tags.put(prefix + "flowVersion", "2");
        }
        for(var key : state.rules.tags.keys()){
            if(key.startsWith(prefix + "flow.")){
                String edge = key.substring((prefix + "flow.").length());
                int split = edge.indexOf('>');
                String from = edge.substring(0, split), to = edge.substring(split + 1);
                incomingFlows.get(to, ObjectSet::new).add(from);
            }else if(key.startsWith(prefix + "received.")){
                String node = key.substring((prefix + "received.").length());
                receivedInputs.get(flowAsset(node), ObjectSet::new).add(node);
            }else if(key.startsWith(prefix + "origin.")){
                itemOrigins.add(key.substring((prefix + "origin.").length()));
            }else if(key.startsWith(prefix + "core.")){
                coreAssets.add(Integer.parseInt(key.substring((prefix + "core.").length())));
            }
        }
    }

    private static boolean recordFlow(Building source, Building recipient, String resource, boolean input){
        //Native hand-stack delivery can have no Building source; it is not a conveyor handoff.
        if(source == null || source.team != state.rules.defaultTeam || recipient.team != source.team) return false;
        loadFlows();
        if(coreInventory(source)) recordCoreAsset(source);
        if(coreInventory(recipient)) recordCoreAsset(recipient);
        String from = flowNode(source, recipient, resource, true), to = flowNode(recipient, source, resource, false);
        if(incomingFlows.get(to, ObjectSet::new).add(from)) state.rules.tags.put(prefix + "flow." + from + ">" + to, "true");
        if(input){
            if(receivedInputs.get(asset(recipient), ObjectSet::new).add(to)) state.rules.tags.put(prefix + "received." + to, "true");
        }
        return true;
    }

    /** Called after a real item enters the receiver's inventory. Full-core incineration does not call this. */
    public static void recordItemFlow(Building source, Building recipient, Item item){
        if(!tacticalRunning()) return;
        String resource = "i" + item.name;
        if(recordFlow(source, recipient, resource, recipient.block.consumesItem(item)) && recipient instanceof CoreBuild){
            //Only this actual incoming route is assessed; other historical core inputs do not participate.
            markSupply(flowNode(source, recipient, resource, true), true);
        }
    }

    private static void recordCoreAsset(Building core){
        loadFlows();
        int token = identity(core);
        if(coreAssets.add(token)) state.rules.tags.put(prefix + "core." + token, "true");
    }

    private static boolean coreInventory(Building building){
        return building instanceof CoreBuild || building instanceof StorageBlock.StorageBuild storage && storage.linkedCore != null;
    }

    private static void recordItemOrigin(Building source, Item item){
        loadFlows();
        String node = "i" + item.name + ":" + identity(source) + ":4";
        if(itemOrigins.add(node)) state.rules.tags.put(prefix + "origin." + node, "true");
    }

    /** Building.produced calls this after positive native production of this particular item. */
    public static void recordItemProduction(Building source, Item item){
        if(!tacticalRunning() || source.team != state.rules.defaultTeam) return;
        recordItemOrigin(source, item);
        recordProduction(source);
    }

    /** InputHandler calls this after a positive physical unit-to-inventory stack delivery. */
    public static void recordManualItemInput(Unit source, Building recipient, Item item){
        if(tacticalRunning() && source != null && source.team == state.rules.defaultTeam && recipient.team == source.team){
            //Linked storage shares the core's inventory; its historical token stays a boundary after unlinking.
            if(coreInventory(recipient)) recordCoreAsset(recipient);
            else recordItemOrigin(recipient, item);
        }
    }

    /** Called after positive liquid input; ammunition liquid is a legitimate supply terminal. */
    public static void recordLiquidFlow(Building source, Building recipient, Liquid liquid, float amount){
        if(!tacticalRunning()) return;
        String resource = "l" + liquid.name;
        if(amount > 0f && recordFlow(source, recipient, resource, recipient.block.consumesLiquid(liquid)) &&
            recipient instanceof LiquidTurretBuild && ((mindustry.world.blocks.defense.turrets.LiquidTurret)recipient.block).ammoTypes.containsKey(liquid)){
            markSupply(flowNode(recipient, source, resource, false));
        }
    }

    /** Confirms existing supply paths only after this turret's native coolant consumer removes liquid. */
    public static void recordLiquidConsumption(Building recipient, ConsumeLiquidBase consumer, Liquid liquid, float amount){
        if(tacticalRunning() && amount > 0f && recipient.team == state.rules.defaultTeam &&
            recipient.block instanceof BaseTurret turret && turret.coolant == consumer){
            loadFlows();
            markSupply("l" + liquid.name + ":" + identity(recipient) + ":4");
        }
    }

    /** ItemTurret calls this only after converting a valid physical item into ammunition. */
    public static void recordAmmoSupply(Building source, Building recipient, Item item){
        if(!tacticalRunning()) return;
        String resource = "i" + item.name;
        if(recordFlow(source, recipient, resource, false)) markSupply(flowNode(recipient, source, resource, false));
    }

    private static void markSupply(String terminal){
        markSupply(terminal, false);
    }

    private static void markSupply(String terminal, boolean requireOrigin){
        ObjectSet<String> seen = new ObjectSet<>();
        ObjectMap<String, ObjectSet<String>> downstream = new ObjectMap<>();
        Seq<String> pending = Seq.with(terminal);
        while(!pending.isEmpty()){
            String node = pending.pop();
            if(!seen.add(node)) continue;
            //A core is a valid source for real consumption, but not for returning stock to another core.
            if(coreAssets.contains(flowAsset(node))) continue;
            var sources = incomingFlows.get(node);
            if(sources != null) for(var source : sources){
                if(requireOrigin) downstream.get(source, ObjectSet::new).add(node);
                pending.add(source);
            }
        }
        if(requireOrigin){
            for(var node : seen) if(itemOrigins.contains(node) && !coreAssets.contains(flowAsset(node))) pending.add(node);
            seen.clear();
            while(!pending.isEmpty()){
                String node = pending.pop();
                if(!seen.add(node)) continue;
                var next = downstream.get(node);
                if(next != null) for(var target : next) pending.add(target);
            }
        }
        for(var node : seen) state.rules.tags.put(prefix + "transferred." + flowAsset(node), "true");
    }

    private static void recordSuppliedProduction(Building source){
        loadFlows();
        var inputs = receivedInputs.get(asset(source));
        if(inputs != null) for(var input : inputs) markSupply(input);
    }

    /** Called only after actual native item/liquid output, or positive generator output. */
    public static void recordProduction(Building source){
        if(tacticalRunning() && source.team == state.rules.defaultTeam){
            recordSuppliedProduction(source);
            if(newConstruction(source) && productionTask(source, currentPreset())){
                state.rules.tags.put(prefix + "produced." + asset(source), Float.toString(elapsed()));
            }
        }
    }

    public static SectorPreset currentPreset(){
        return active() ? content.sector(state.rules.tags.get(prefix + "preset")) : null;
    }

    public static Mode currentMode(){
        return Mode.valueOf(state.rules.tags.get(prefix + "mode"));
    }

    /** Scoped mission equipment never writes vanilla unlock settings. */
    public static boolean available(UnlockableContent unlockable){
        return active() && state.rules.researched.contains(unlockable);
    }

    public static float elapsed(){
        return state.rules.tags.getFloat(prefix + "elapsed", 0f);
    }

    public static int coreLosses(){
        return state.rules.tags.getInt(prefix + "coreLosses", 0);
    }

    public static float minimumCoreHealth(){
        return state.rules.tags.getFloat(prefix + "minCore", 1f);
    }

    public static int placed(){
        return state.rules.tags.getInt(prefix + "placed", 0);
    }

    public static void recordCoreHealth(Building building){
        if(running() && building.team == state.rules.defaultTeam && building.block instanceof CoreBlock){
            recordCoreHealth(building.healthf());
        }
    }

    public static void recordCoreHealth(float fraction){
        state.rules.tags.put(prefix + "minCore", Float.toString(Math.min(minimumCoreHealth(), fraction)));
    }

    public static void update(){
        if(!running() || !state.isPlaying()) return;
        state.rules.tags.put(prefix + "elapsed", Float.toString(elapsed() + Time.delta / 60f));
        for(var core : state.rules.defaultTeam.cores()) recordCoreHealth(core.healthf());
        if(currentMode() == Mode.TACTICAL){
            for(var building : state.rules.defaultTeam.data().buildings){
                if(building instanceof GeneratorBuild generator && generator.getPowerProduction() > 0f) recordProduction(building);
                if(building instanceof SolidPump.SolidPumpBuild pump && pump.enabled && pump.lastPump > 0f) recordProduction(building);
            }
        }
        if(state.rules.tags.getBool(prefix + "attackAfterWaves") && state.rules.waves &&
            state.wave >= state.rules.tags.getInt(prefix + "phaseWave") && state.enemies == 0 && !spawner.isSpawning()){
            state.rules.attackMode = true;
            state.rules.waves = false;
            state.rules.tags.put(prefix + "attackAfterWaves", "false");
            Call.setRules(state.rules);
        }
        if(!headless){
            hudTime += Time.delta;
            if(hudRules != state.rules || hudTime >= 60f){
                hudTime = 0f;
                hudRules = state.rules;
                if(WatchCampaign.level() >= 0){
                    WatchCampaignEvents.refreshHud();
                }else{
                    ui.hudfrag.setHudText("[accent]" + currentPreset().localizedName + " · " + modeName(currentMode()) + "[]\n" + status());
                }
            }
        }
    }

    public static Terrain terrain(SectorPreset preset){
        int branch = branchIndex(preset);
        if(branch >= 0){
            return new Terrain[]{Terrain.VALLEY, Terrain.INDUSTRIAL, Terrain.FROZEN, Terrain.NAVAL, Terrain.VOLCANIC,
                Terrain.NAVAL, Terrain.SPORE, Terrain.INDUSTRIAL, Terrain.VOLCANIC, Terrain.INDUSTRIAL}[branch];
        }
        if(preset.planet == Planets.erekir){
            return switch(preset.name){
                case "lake", "marsh" -> Terrain.NAVAL;
                case "caldera-erekir", "karst", "origin" -> Terrain.VOLCANIC;
                case "stronghold", "siege", "crossroads" -> Terrain.INDUSTRIAL;
                default -> Terrain.EREKIR;
            };
        }
        return switch(preset.name){
            case "frozenForest", "impact0078", "nuclearComplex" -> Terrain.FROZEN;
            case "ruinousShores", "windsweptIslands", "coastline", "weatheredChannels", "navalFortress", "perilousHarbor", "littoralShipyard", "atolls", "sunkenPier" -> Terrain.NAVAL;
            case "biomassFacility", "taintedWoods", "fungalPass", "infestedCanyons", "mycelialBastion", "overgrowth" -> Terrain.SPORE;
            case "tarFields", "desolateRift" -> Terrain.VOLCANIC;
            case "extractionOutpost", "testingGrounds", "facility32m", "planetaryTerminal" -> Terrain.INDUSTRIAL;
            default -> Terrain.VALLEY;
        };
    }

    private static int lossBudget(SectorPreset preset){
        return switch(terrain(preset)){
            case VALLEY, EREKIR -> 16;
            case FROZEN -> 12;
            case NAVAL -> 22;
            case SPORE -> 14;
            case INDUSTRIAL -> 24;
            case VOLCANIC -> 20;
        };
    }

    /** Preview and actual run use the same immutable original-map metadata. */
    public static MissionLimits limits(SectorPreset preset){
        int width, height, enemyCores, winWave;
        boolean waves;
        float spacing, initial;
        int branch = branchIndex(preset);
        if(branch >= 0){
            var stage = WatchCampaign.stages[branch];
            width = height = stage.size();
            enemyCores = 0;
            winWave = stage.waves() + 1;
            waves = true;
            spacing = branch == 0 ? 55f : branch < 4 ? 65f : 75f;
            initial = 180f;
        }else{
            if(missionMetadata == null) missionMetadata = new JsonReader().parse(Core.files.internal("star-missions.json"));
            var metadata = missionMetadata.get(preset.name);
            width = metadata.getInt("width");
            height = metadata.getInt("height");
            enemyCores = metadata.getInt("enemyCores");
            winWave = metadata.getInt("winWave");
            waves = metadata.getBoolean("waves");
            spacing = metadata.getFloat("waveSpacingSeconds");
            initial = metadata.getFloat("initialWaveSpacingSeconds");
        }
        int buildBudget = Math.max(160, Math.min(1800, 100 + width * height / 650 + enemyCores * 45));
        if(initial <= 0f) initial = spacing * 2f;
        float deadline = waves && winWave > 0 ?
            (initial + spacing * winWave) * 1.4f + (preset.attackAfterWaves ? 1200f : 240f) :
            60f * (20f + preset.difficulty * 5f + enemyCores * 3f);
        return new MissionLimits(deadline, buildBudget, lossBudget(preset));
    }

    private static int taskA(SectorPreset preset){
        loadFlows();
        return state.rules.defaultTeam.data().buildings.count(b -> newConstruction(b) &&
            state.rules.tags.getBool(prefix + "transported." + asset(b)) && state.rules.tags.getBool(prefix + "transferred." + asset(b)) && switch(terrain(preset)){
                case NAVAL, VOLCANIC -> b.block instanceof Conduit;
                default -> b.block instanceof Conveyor || b.block instanceof Duct;
            });
    }

    private static int taskB(SectorPreset preset){
        return state.rules.defaultTeam.data().buildings.count(b -> productionTask(b, preset) &&
            newConstruction(b) && state.rules.tags.containsKey(prefix + "produced." + asset(b)) &&
            elapsed() - state.rules.tags.getFloat(prefix + "produced." + asset(b)) <= 60f);
    }

    private static boolean productionTask(Building b, SectorPreset preset){
        return switch(terrain(preset)){
            case VALLEY, EREKIR -> b.block instanceof Drill || b.block instanceof BeamDrill;
            case FROZEN, VOLCANIC -> b.block instanceof PowerGenerator;
            case NAVAL -> b.block instanceof Pump || (branchIndex(preset) < 0 && preset.planet == Planets.erekir && b.block == Blocks.ventCondenser);
            case SPORE, INDUSTRIAL -> b.block instanceof GenericCrafter;
        };
    }

    private static int taskARequired(SectorPreset preset){
        return terrain(preset) == Terrain.INDUSTRIAL ? 24 : terrain(preset) == Terrain.NAVAL || terrain(preset) == Terrain.VOLCANIC ? 12 : 16;
    }

    public static boolean tacticalTaskComplete(){
        var preset = currentPreset();
        return taskA(preset) >= state.rules.tags.getInt(prefix + "taskAStart") + taskARequired(preset) &&
            taskB(preset) >= state.rules.tags.getInt(prefix + "taskBStart") + 2 &&
            placed() <= state.rules.tags.getInt(prefix + "buildBudget");
    }

    public static int scoreVictory(){
        if(state.rules.tags.getBool(prefix + "failed")) return 0;
        return switch(currentMode()){
            case NORMAL -> state.rules.tags.getBool(prefix + "partial") ? 1 :
                1 + (coreLosses() == 0 ? 1 : 0) + (minimumCoreHealth() >= 0.7f ? 1 : 0);
            case HEROIC -> coreLosses() == 0 && minimumCoreHealth() >= 0.85f &&
                elapsed() <= state.rules.tags.getFloat(prefix + "deadline") &&
                state.stats.buildingsDestroyed <= state.rules.tags.getInt(prefix + "lossBudget") ? 1 : 0;
            case TACTICAL -> coreLosses() == 0 && minimumCoreHealth() >= 0.6f && tacticalTaskComplete() ? 1 : 0;
        };
    }

    public static int recordVictory(){
        if(!running()) return state.rules.tags.getInt(prefix + "earned", 0);
        int earned = scoreVictory();
        saveBest(currentPreset(), currentMode(), earned);
        state.rules.tags.put(prefix + "finished", "true");
        state.rules.tags.put(prefix + "earned", Integer.toString(earned));
        if(!headless){
            ui.announce((chinese() ? "任务完成：本次获得 " : "Mission complete: earned ") + earned + " / " + currentMode().maximum + (chinese() ? " 星" : " stars"), 8f);
        }
        return earned;
    }

    /** Applies bounded conveniences to star missions, without changing combat or vanilla campaigns. */
    public static void applyRewards(Rules rules){
        if(!rules.tags.containsKey(prefix + "preset") || rules.pvp || rules.editor || net.active()) return;
        float build = 0f, refund = 0f;
        if(completedReward(0)) build += 0.02f;
        if(completedReward(1)) refund += 0.03f;
        if(completedReward(3)) build += 0.02f;
        if(completedReward(4)) refund += 0.03f;
        if(completedReward(6)) build += 0.02f;
        if(completedReward(7)) refund += 0.03f;
        if(completedReward(9)){ build += 0.02f; refund += 0.01f; }
        if(!rules.tags.containsKey(prefix + "rewardBuildBase")){
            rules.tags.put(prefix + "rewardBuildBase", Float.toString(rules.teams.get(rules.defaultTeam).buildSpeedMultiplier));
            rules.tags.put(prefix + "rewardRefundBase", Float.toString(rules.deconstructRefundMultiplier));
        }
        rules.teams.get(rules.defaultTeam).buildSpeedMultiplier = rules.tags.getFloat(prefix + "rewardBuildBase") * (1f + build);
        rules.deconstructRefundMultiplier = Math.min(1f, rules.tags.getFloat(prefix + "rewardRefundBase") + refund);
        if(Mode.valueOf(rules.tags.get(prefix + "mode")) == Mode.NORMAL && !rules.tags.getBool(prefix + "rewardSupplies")){
            boolean erekir = rules.planet == Planets.erekir && branchIndex(content.sector(rules.tags.get(prefix + "preset"))) < 0;
            if(completedReward(2)) addLoadout(rules, erekir ? Items.beryllium : Items.copper, 80);
            if(completedReward(5)) addLoadout(rules, erekir ? Items.tungsten : Items.lead, 50);
            if(completedReward(8)) addLoadout(rules, erekir ? Items.graphite : Items.coal, 50);
            rules.tags.put(prefix + "rewardSupplies", "true");
        }
    }

    private static void addLoadout(Rules rules, Item item, int amount){
        var stack = rules.loadout.find(s -> s.item == item);
        if(stack == null){
            rules.loadout.add(new ItemStack(item, amount));
        }else{
            //Map loadouts may reference preset data; replace instead of mutating its shared stack.
            int index = rules.loadout.indexOf(stack);
            rules.loadout.set(index, new ItemStack(item, stack.amount + amount));
        }
    }

    private static boolean chinese(){
        return Core.bundle.getLocale().getLanguage().equals("zh");
    }

    public static String modeName(Mode mode){
        return switch(mode){
            case NORMAL -> chinese() ? "普通" : "Normal";
            case HEROIC -> chinese() ? "英雄挑战" : "Heroic challenge";
            case TACTICAL -> chinese() ? "战术挑战" : "Tactical challenge";
        };
    }

    public static String describe(SectorPreset preset, Mode mode){
        if(mode == Mode.NORMAL) return chinese() ?
            "完成原地图目标得1星；全程不失去核心再得1星；核心最低生命不低于70%再得1星。重玩仅保留最好成绩。" :
            "1 star for the original objective, 1 for losing no cores, 1 for keeping every core at least 70% healthy throughout. Replays retain your best score.";
        if(mode == Mode.HEROIC) return (chinese() ?
            "敌军单位生命+25%、攻击+15%，敌方炮塔攻击+15%、建筑生命+10%，波次间隔缩短15%。完成原目标，同时全程核心≥85%、无核心损失，损失建筑不超过" :
            "Enemies: +25% unit health, +15% unit/turret damage, +10% building health; waves arrive 15% faster. Complete the original objective with cores always ≥85%, no core losses, and at most ") +
            lossBudget(preset) + (chinese() ? "座；时限" : " buildings lost; deadline ") + Math.round(limits(preset).deadline) + (chinese() ? "秒。成功得1星。" : " seconds. Awards 1 star.");
        return (chinese() ?
            "敌军单位生命+12%、攻击+8%，敌方炮塔攻击+8%，波次间隔缩短5%。完成原目标，全程核心≥60%、无核心损失，并在建造预算内保留新增的" :
            "Enemies: +12% unit health, +8% unit/turret damage; waves arrive 5% faster. Complete the original objective with cores always ≥60%, no core losses, and keep new ") +
            logisticsTask(preset) + (chinese() ? "，累计建造不超过" : ", with at most ") + limits(preset).buildBudget +
            (chinese() ? "座（空运落地也计一次）。原图设施搬移后仍不计新增；新增设施搬移保留记录。成功得1星。" : " total constructions (airlift placement also counts). Relocated initial facilities remain excluded; new facilities keep their records when moved. Awards 1 star.");
    }

    private static String logisticsTask(SectorPreset preset){
        boolean cn = chinese();
        String kind = switch(terrain(preset)){
            case VALLEY, EREKIR -> cn ? "采矿设施" : "drills";
            case FROZEN, VOLCANIC -> cn ? "发电设施" : "generators";
            case NAVAL -> cn ? "抽水/冷凝设施" : "pumps/condensers";
            case SPORE, INDUSTRIAL -> cn ? "加工设施" : "crafters";
        };
        String transport = terrain(preset) == Terrain.NAVAL || terrain(preset) == Terrain.VOLCANIC ?
            (cn ? "导管" : "conduits") : (cn ? "传送带/物流管" : "conveyors/ducts");
        return taskARequired(preset) + (cn ? "座曾实际供给核心、弹药塔、消耗冷却液的炮台或产出设施的" : " transports that actually supplied a core, ammunition turret, turret consuming coolant or producing facility: ") + transport +
            (cn ? "及2座胜利前60秒内曾实际产出/发电的" : " and 2 facilities with actual output/power in the final 60 seconds: ") + kind +
            (cn ? "。核心入库只计真实产出或玩家实际投放的来源，核心回流不计。" : ". Core input requires actual production or a physical player delivery as origin; core stock returns do not count.");
    }

    public static String status(){
        if(!active()) return "";
        if(state.rules.tags.getBool(prefix + "finished")){
            return (state.rules.tags.getInt(prefix + "earned") == 0 ? (chinese() ? "挑战条件未达成；" : "Challenge conditions unmet; ") : "") +
                (chinese() ? "本次获得 " : "Earned ") + state.rules.tags.getInt(prefix + "earned") + " / " + currentMode().maximum +
                (chinese() ? " 星；本关累计 " : " stars; level total ") + stars(currentPreset()) + " / 5";
        }
        String core = (chinese() ? "核心最低生命 " : "Lowest core health ") + Math.round(minimumCoreHealth() * 100f) +
            "% · " + (chinese() ? "核心损失 " : "Cores lost ") + coreLosses();
        return switch(currentMode()){
            case NORMAL -> core;
            case HEROIC -> core + "\n" + (chinese() ? "用时 " : "Time ") + Math.round(elapsed()) + " / " +
                Math.round(state.rules.tags.getFloat(prefix + "deadline")) + (chinese() ? " 秒 · 建筑损失 " : " s · Buildings lost ") +
                state.stats.buildingsDestroyed + " / " + state.rules.tags.getInt(prefix + "lossBudget");
            case TACTICAL -> core + "\n" + logisticsTask(currentPreset()) + "\n" +
                (chinese() ? "新增运输设施 " : "New transport ") + Math.max(0, taskA(currentPreset()) - state.rules.tags.getInt(prefix + "taskAStart")) + " / " + taskARequired(currentPreset()) +
                " · " + (chinese() ? "新增生产设施 " : "New production ") + Math.max(0, taskB(currentPreset()) - state.rules.tags.getInt(prefix + "taskBStart")) + " / 2" +
                "\n" + (chinese() ? "累计建造 " : "Total construction ") + placed() + " / " + state.rules.tags.getInt(prefix + "buildBudget");
        };
    }

    public static String rewardDescription(int index){
        return (chinese() ? new String[]{
            "前哨工兵：建造速度永久+2%，解锁战地绷带塔", "废墟回收：拆除返还率永久+3个百分点", "冰原储备：每次新普通任务额外铜/铍80",
            "河谷工兵：建造速度永久+2%", "沙海回收：拆除返还率永久+3个百分点", "港口储备：每次新普通任务额外铅/钨50",
            "密林工兵：建造速度永久+2%", "高地回收：拆除返还率永久+3个百分点", "断层储备：每次新普通任务额外煤/石墨50",
            "终局后勤：建造速度永久+2%，拆除返还率永久+1个百分点"
        } : new String[]{
            "Outpost engineers: permanently +2% build speed and unlock the Field Bandage tower", "Ruins salvage: permanently +3 percentage points dismantling refund", "Ice reserve: +80 copper/beryllium on each fresh normal mission",
            "River engineers: permanently +2% build speed", "Desert salvage: permanently +3 percentage points dismantling refund", "Harbor reserve: +50 lead/tungsten on each fresh normal mission",
            "Forest engineers: permanently +2% build speed", "Highland salvage: permanently +3 percentage points dismantling refund", "Fault reserve: +50 coal/graphite on each fresh normal mission",
            "Final logistics: permanently +2% build speed and +1 percentage point dismantling refund"
        })[index] + (chinese() ? "；永久装备：" : "; Permanent equipment: ") + CampaignProgression.rewardEquipment(index, false) +
            " (Serpulo) / " + CampaignProgression.rewardEquipment(index, true) + " (Erekir)";
    }

    public static String rewardSummary(){
        int count = 0;
        for(int i = 0; i < WatchCampaign.sectors.length; i++) if(completedReward(i)) count++;
        return (chinese() ? "仅支线满五星给予永久奖励；已获得 " : "Only five-star branches grant permanent rewards; earned ") + count + " / 10\n" +
            (chinese() ? "全部奖励上限：建造速度+8%、拆除返还率+10个百分点；每次新普通任务额外基础物资80/50/50。首支线满五星解锁绷带塔。仅用于五星任务，不提高最大生命或伤害；绷带修复消耗石墨并要求供电，同一墙体共用60秒冷却。" :
                "All rewards combined: +8% build speed, +10 percentage points refund, and finite +80/+50/+50 starting supplies on fresh normal missions. The first five-star branch unlocks Field Bandage. Star missions only; no maximum health or damage increase. Repairs cost graphite and require power, with a shared 60-second cooldown per wall.");
    }

    public static String result(){
        String result = status();
        if(!active() || !state.rules.tags.getBool(prefix + "finished") || state.rules.tags.getInt(prefix + "earned") > 0) return result;
        if(state.rules.tags.getBool(prefix + "failed")) return result + (chinese() ? "\n核心被摧毁，任务失败。" : "\nYour base was defeated.");
        StringBuilder unmet = new StringBuilder(result).append(chinese() ? "\n原地图目标完成，但挑战条件未全部达成：" : "\nOriginal objective completed, but challenge conditions were missed: ");
        if(coreLosses() > 0) unmet.append(chinese() ? "核心损失；" : "core loss; ");
        if(minimumCoreHealth() < (currentMode() == Mode.HEROIC ? 0.85f : 0.6f)) unmet.append(chinese() ? "核心最低生命；" : "minimum core health; ");
        if(currentMode() == Mode.HEROIC){
            if(elapsed() > state.rules.tags.getFloat(prefix + "deadline")) unmet.append(chinese() ? "超时；" : "deadline; ");
            if(state.stats.buildingsDestroyed > state.rules.tags.getInt(prefix + "lossBudget")) unmet.append(chinese() ? "建筑损失超限。" : "building losses.");
        }else if(currentMode() == Mode.TACTICAL){
            if(placed() > state.rules.tags.getInt(prefix + "buildBudget")) unmet.append(chinese() ? "建造超预算；" : "construction budget; ");
            if(!tacticalTaskComplete()) unmet.append(chinese() ? "新增物流/生产设施不足。" : "insufficient new logistics/production facilities.");
        }
        return unmet.toString();
    }
}
