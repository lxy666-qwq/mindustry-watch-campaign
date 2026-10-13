import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.net.*;
import static mindustry.Vars.*;

/** Persistence inspection of an existing world. No gameplay, no artificial star awards. */
public class StarSaveCompatibilityProbe{
    public static void main(String[] args){
        Fi assets = new Fi("Mindustry/core/assets");
        Core.app = new MockApplication();
        Core.files = new MockFiles(){ @Override public Fi internal(String path){ return assets.child(path); } };
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.input = new MockInput();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(args[1]));
        Core.settings.setJson(JsonIO.json);
        headless = true; net = new Net(null); Vars.init(); Vars.loadSettings();
        content.createBaseContent(); logic = new Logic(); netServer = new NetServer(); content.init();
        Fi oldSave = new Fi(args[0]), migrated = new Fi(args[2]);
        var expected = SaveIO.getMeta(oldSave).rules.tags;
        if(expected.size < 100 || !expected.containsKey("stars.assetNext")) throw new IllegalStateException("Existing legacy ledger missing");
        SaveIO.load(oldSave);
        if(!expected.equals(state.rules.tags)) throw new IllegalStateException("Legacy world changed its embedded ledger on load");
        String preset = CampaignStars.currentPreset().name;
        int placed = CampaignStars.placed();
        float minimum = CampaignStars.minimumCoreHealth();
        SaveIO.write(migrated);
        if(!expected.equals(state.rules.tags)) throw new IllegalStateException("Migration changed the live ledger");
        int newMetadataTags = SaveIO.getMeta(migrated).rules.tags.size;
        if(newMetadataTags >= expected.size) throw new IllegalStateException("Ledger was not removed from metadata");
        logic.reset();
        SaveIO.load(migrated);
        if(!expected.equals(state.rules.tags) || CampaignStars.placed() != placed || CampaignStars.minimumCoreHealth() != minimum){
            throw new IllegalStateException("Migrated world did not restore the same ledger and task state");
        }
        System.out.println("version=" + WatchCampaign.version);
        System.out.println("inspectionOnly=true; no gameplay or score awards");
        System.out.println("preset=" + preset);
        System.out.println("legacyEmbeddedTags=" + expected.size);
        System.out.println("newMetadataTags=" + newMetadataTags);
        System.out.println("placed=" + placed);
        System.out.println("minimumCore=" + minimum);
        System.out.println("allTagsEqualAfterMigration=true");
        System.out.println("settingsStars=" + CampaignStars.totalStars());
    }
}
