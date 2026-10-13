import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.net.*;
import mindustry.type.*;
import java.util.*;
import static mindustry.Vars.*;

/** Render terrain excerpts from the actual initial mission worlds; this is not a playthrough. */
public class ExportRouteScenes040{
    public static void main(String[] args) throws Exception{
        Fi assets = new Fi(args[0]), output = new Fi(args[1]);
        output.mkdirs();
        ArcNativesLoader.load();
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics();
        Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings(); Core.settings.setDataDirectory(output.child("profile"));
        Core.settings.setJson(JsonIO.json);
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.ROOT);
        content.createBaseContent(); logic = new Logic(); content.init(); content.loadColors();
        StringBuilder index = new StringBuilder("preset\tplanet\tsource\twidth\theight\tdominantFloors\n");
        int count = 0;
        for(SectorPreset preset : content.sectors()){
            if(!CampaignStars.eligible(preset)) continue;
            logic.reset(); CampaignStars.loadMission(preset, CampaignStars.Mode.NORMAL);
            Pixmap source = MapIO.generatePreview(world.tiles), scene = new Pixmap(256, 160);
            for(int y = 0; y < scene.height; y++) for(int x = 0; x < scene.width; x++){
                float dx = (x - 127.5f) / 123f, dy = (y - 79.5f) / 76f;
                float rim = 1f + 0.035f * (float)Math.sin(x * 0.17 + y * 0.11);
                float alpha = Math.max(0f, Math.min(1f, (rim - dx * dx - dy * dy) * 8f));
                int color = source.get(x * source.width / scene.width, y * source.height / scene.height);
                scene.set(x, y, (color & 0xffffff00) | (int)(alpha * 255));
            }
            output.child("scenes/" + preset.name + ".png").writePng(scene);
            TreeMap<String, Integer> floors = new TreeMap<>();
            for(var tile : world.tiles) floors.merge(tile.floor().name, 1, Integer::sum);
            var dominant = floors.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(5)
                .map(e -> e.getKey() + ":" + e.getValue()).reduce((a, b) -> a + "," + b).orElseThrow();
            String origin = CampaignStars.branchIndex(preset) >= 0 ? "WatchMapGenerator:" + CampaignStars.branchIndex(preset) : preset.generator.map.file.name();
            index.append(preset.name).append('\t').append(preset.planet.name).append('\t').append(origin).append('\t')
                .append(world.width()).append('\t').append(world.height()).append('\t').append(dominant).append('\n');
            System.out.println(preset.name + " " + world.width() + "x" + world.height() + " " + dominant);
            source.dispose(); scene.dispose(); count++;
        }
        output.child("scenes/index.tsv").writeString(index.toString());
        output.child("result.txt").writeString("mode=terrain-extraction-only\nbaseVersion=" + WatchCampaign.version + "\nscenes=" + count + "\nlogicTicks=0\npermanentStars=" + CampaignStars.totalStars() + "\n");
        if(count != 56 || CampaignStars.totalStars() != 0) throw new IllegalStateException("Unexpected extraction coverage or progress");
        System.exit(0);
    }
}
