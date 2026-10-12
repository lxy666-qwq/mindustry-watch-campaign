import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import java.util.*;
import static mindustry.Vars.*;

/** Read the archived original game's own maps and content, using a separate data directory. */
public class OriginalCampaignExport{
    public static class Entry{
        public String name, key, planet, map, description, parent;
        public int sector, originalPosition, width, height, captureWave, winWave, difficulty;
        public boolean assigned, hidden, alwaysUnlocked, requireUnlock, showHidden, waves, attackMode, attackAfterWaves, fog;
        public float waveSpacingSeconds, initialWaveSpacingSeconds;
        public String[] resources, terrain, objectives, weather, shieldSectors;
        public int enemyCores, spawnPoints, waveGroups, mapObjectives, worldProcessors;
    }
    public static void main(String[] args){
        Log.useColors = false;
        Core.app = new MockApplication();
        Core.files = new MockFiles();
        Core.graphics = new MockGraphics();
        Core.audio = new MockAudio();
        Core.input = new MockInput();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(args[0]).child("runtime"));
        headless = true;
        net = new Net(null);
        Vars.init();
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.SIMPLIFIED_CHINESE);
        Core.settings.setJson(JsonIO.json);
        JsonIO.json.setOutputType(JsonWriter.OutputType.json);
        content.createBaseContent();
        logic = new Logic();
        netServer = new NetServer();
        content.init();
        var output = new Fi(args[0]);
        var entries = new ArrayList<Entry>();
        for(var preset : content.sectors()){
            logic.reset();
            Entry e = new Entry();
            e.key=preset.name; e.name=preset.localizedName; e.planet=preset.planet.name;
            e.sector=preset.sector.id; e.originalPosition=preset.originalPosition;
            e.assigned=preset.sector.preset==preset; e.hidden=preset.isHidden();
            e.alwaysUnlocked=preset.alwaysUnlocked;
            e.requireUnlock=preset.requireUnlock; e.showHidden=preset.showHidden;
            e.shieldSectors=preset.shieldSectors.map(s->s.planet.name+"/"+s.id).toArray(String.class);
            e.map=preset.generator.map.file.path();
            e.description=preset.description;
            e.difficulty=(int)preset.difficulty; e.captureWave=preset.captureWave;
            e.attackAfterWaves=preset.attackAfterWaves;
            var objectiveNames=new ArrayList<String>();
            if(preset.techNode!=null){
                e.parent=preset.techNode.parent==null?null:preset.techNode.parent.content.localizedName;
                for(var objective:preset.techNode.objectives){
                    if(objective instanceof Objectives.SectorComplete o) objectiveNames.add("占领："+o.preset.localizedName);
                    else if(objective instanceof Objectives.Research o) objectiveNames.add("研究："+o.content.localizedName);
                    else objectiveNames.add(objective.toString());
                }
            }
            e.objectives=objectiveNames.toArray(String[]::new);
            var params = new WorldParams(); params.saveInfo=false;
            if(e.assigned){
                world.loadSector(preset.sector,params);
            }else{
                state.rules.sector=preset.sector;
                world.loadGenerator(preset.generator.map.width,preset.generator.map.height,tiles->preset.generator.generate(tiles,params));
                preset.rules.get(state.rules);
            }
            e.width=world.width(); e.height=world.height();
            Rules r=state.rules;
            e.waves=r.waves; e.attackMode=r.attackMode; e.winWave=r.winWave; e.fog=r.fog;
            e.waveSpacingSeconds=r.waveSpacing/60f;
            e.initialWaveSpacingSeconds=r.initialWaveSpacing/60f;
            e.waveGroups=r.spawns.size;
            e.mapObjectives=r.objectives.all.size;
            var items=new TreeSet<String>();
            var floors=new TreeMap<String,Integer>();
            for(Tile tile:world.tiles){
                floors.merge(tile.floor().localizedName,1,Integer::sum);
                if(world.getDarkness(tile.x,tile.y)<3){
                    if(tile.floor().itemDrop!=null && !tile.block().isStatic()) items.add(tile.floor().itemDrop.localizedName);
                    if(tile.overlay().itemDrop!=null && !tile.block().isStatic()) items.add(tile.overlay().itemDrop.localizedName);
                    if(tile.wallDrop()!=null) items.add(tile.wallDrop().localizedName);
                    if(tile.floor().liquidDrop!=null && !tile.block().isStatic()) items.add(tile.floor().liquidDrop.localizedName);
                }
                if(tile.overlay()==mindustry.content.Blocks.spawn) e.spawnPoints++;
                if(tile.isCenter() && tile.build!=null){
                    if(tile.build.block instanceof mindustry.world.blocks.storage.CoreBlock && tile.team()==r.waveTeam) e.enemyCores++;
                    if(tile.build.block==mindustry.content.Blocks.worldProcessor){
                        e.worldProcessors++;
                        var processor=(mindustry.world.blocks.logic.LogicBlock.LogicBuild)tile.build;
                        output.child("scripts/"+preset.name+"/"+tile.x+"_"+tile.y+".mlog").writeString(processor.code);
                    }
                }
            }
            e.resources=items.toArray(String[]::new);
            e.terrain=floors.entrySet().stream().sorted(java.util.Map.Entry.<String,Integer>comparingByValue().reversed()).limit(6).map(x->x.getKey()+"("+x.getValue()+")").toArray(String[]::new);
            e.weather=r.weather.map(w->w.weather.localizedName).toArray(String.class);
            output.child("rules/"+preset.name+".json").writeString(JsonIO.write(r));
            entries.add(e);
            System.out.println(e.planet+" / "+e.name+" / "+e.width+"x"+e.height);
        }
        Json json=new Json(); json.setUsePrototypes(false); json.setOutputType(JsonWriter.OutputType.json);
        output.child("campaign.json").writeString(json.toJson(entries.toArray(Entry[]::new)));
        System.out.println("Exported "+entries.size()+" original presets.");
        System.exit(0);
    }
}
