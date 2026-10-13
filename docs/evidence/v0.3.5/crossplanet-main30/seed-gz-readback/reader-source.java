import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.net.*;
import java.util.Locale;
import static mindustry.Vars.*;

/** Fresh JVM reads actual saved progress and world; performs no ticks or score writes. */
public class CrossPlanetRead034{
    static StringMap scores(){
        StringMap values=new StringMap();
        for(var planet:new mindustry.type.Planet[]{Planets.serpulo,Planets.erekir})
            for(var preset:CampaignStars.mainLevels(planet))for(var mode:Mode.values())values.put(preset.name+"."+mode,CampaignStars.stars(preset,mode)+"");
        for(var preset:WatchCampaign.sectors)for(var mode:Mode.values())values.put(preset.name+"."+mode,CampaignStars.stars(preset,mode)+"");
        return values;
    }
    public static void main(String[] args)throws Exception{
        Fi sourceProfile=new Fi(args[0]),save=new Fi(args[1]),out=new Fi(args[2]);
        if(out.exists())throw new IllegalArgumentException("New readback output required");
        out.mkdirs();Fi profile=out.child("profile");profile.mkdirs();sourceProfile.child("settings.bin").copyTo(profile.child("settings.bin"));
        new Fi("local-work/mindustry-star-work/CrossPlanetRead034.java").copyTo(out.child("reader-source.java"));
        Fi assets=new Fi("local-work/mindustry-star-work/star034-runtime/assets");
        Core.app=new MockApplication();Core.files=new MockFiles(){@Override public Fi internal(String path){return assets.child(path);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();Core.settings=new Settings();
        Core.settings.setDataDirectory(profile);Core.settings.setJson(JsonIO.json);headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        StringMap before=scores();int main=CampaignStars.mainStars(),total=CampaignStars.totalStars();
        if(main!=Integer.parseInt(args[3]))throw new IllegalStateException("Expected actual main total "+args[3]+", got "+main);
        if(CampaignStars.stars(SectorPresets.onset)!=5||CampaignStars.stars(SectorPresets.aegis)!=5||CampaignStars.stars(SectorPresets.lake)!=5||CampaignStars.stars(SectorPresets.intersect)!=3)throw new IllegalStateException("Genuine Erekir chain mismatch");
        SaveIO.load(save);SerpuloNativeState033.equal("best scores after save load",before,scores());
        if(main!=CampaignStars.mainStars()||total!=CampaignStars.totalStars())throw new IllegalStateException("Load changed total scores");
        Fi tags=save.parent().child("final-tags.json"),fields=save.parent().child("final-world.json");int tagCount=0,fieldCount=0;
        if(tags.exists()){
            StringMap expected=JsonIO.read(StringMap.class,tags.readString());SerpuloNativeState033.equal("rule tags",expected,state.rules.tags);tagCount=expected.size;
            StringMap worldExpected=JsonIO.read(StringMap.class,fields.readString());SerpuloNativeState033.equal("terrain and buildings",worldExpected,SerpuloNativeState033.world());fieldCount=worldExpected.size;
        }
        Fi jar=new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        String result="actualVersion="+WatchCampaign.version+"\njar="+jar+"\njarModified="+jar.lastModified()+"\nassets="+assets+"\nsourceProfile="+sourceProfile+"\nendSave="+save+"\nmainStars="+main+"\ntotalStars="+total+"\nere kirEarnedMain=18\n";
        result=result.replace("ere kir", "erekir");
        for(var preset:new mindustry.type.SectorPreset[]{SectorPresets.onset,SectorPresets.aegis,SectorPresets.lake,SectorPresets.intersect,SectorPresets.groundZero,SectorPresets.frozenForest,SectorPresets.crateredBattleground})
            result+=preset.name+"="+CampaignStars.stars(preset,Mode.NORMAL)+"/"+CampaignStars.stars(preset,Mode.HEROIC)+"/"+CampaignStars.stars(preset,Mode.TACTICAL)+"\n";
        for(int i=0;i<4;i++)result+="watch"+(i+1)+"="+CampaignStars.stars(WatchCampaign.sectors[i],Mode.NORMAL)+"/"+CampaignStars.stars(WatchCampaign.sectors[i],Mode.HEROIC)+"/"+CampaignStars.stars(WatchCampaign.sectors[i],Mode.TACTICAL)+" reward="+CampaignStars.completedReward(i)+" unlocked="+CampaignStars.branchUnlocked(i)+"\n";
        result+="loadedMission="+CampaignStars.currentPreset().name+"\nloadedMode="+CampaignStars.currentMode()+"\nfinished="+state.rules.tags.getBool("stars.finished")+"\nearned="+state.rules.tags.getInt("stars.earned")+"\ncoreMinimum="+CampaignStars.minimumCoreHealth()+"\nobjectivesCompleted="+state.rules.objectives.all.count(o->o.isCompleted())+"\nobjectivesTotal="+state.rules.objectives.all.size+"\ndetachedSector="+(state.rules.sector==null)+"\nallBestScoresEqual=true\nworldBaselineCompared="+tags.exists()+"\nruleTagsCompared="+tagCount+"\nterrainAndBuildingFieldsCompared="+fieldCount+"\nnoTicks=true\nnoScoreMerging=true\noriginalProfileUntouched=true\nreadback=passed\n";
        out.child("result.txt").writeString(result);out.child("best-scores.json").writeString(JsonIO.write(before));System.out.print(result);System.exit(0);
    }
}
