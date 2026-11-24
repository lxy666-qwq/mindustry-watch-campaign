import arc.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.type.*;
import org.junit.jupiter.api.*;
import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class CampaignPlanetGateTests{
    record Score(String key, boolean present, int value){}
    Seq<Score> saved;
    ObjectMap<Sector,Boolean> captures;
    @BeforeAll static void start(){ ApplicationTests.launchApplication(); }
    @BeforeEach void clear(){
        saved = new Seq<>();captures = new ObjectMap<>();
        for(var preset : content.sectors()) if(CampaignStars.eligible(preset)){
            captures.put(preset.sector, preset.sector.info.wasCaptured);preset.sector.info.wasCaptured=false;
            for(var mode : Mode.values()){
                String key=CampaignStars.progressKey(preset,mode);
                saved.add(new Score(key,Core.settings.has(key),Core.settings.getInt(key,0)));Core.settings.put(key,0);
            }
        }
    }
    @AfterEach void restore(){
        for(var s : saved) if(s.present())Core.settings.put(s.key(),s.value());else Core.settings.remove(s.key());
        for(var c : captures) c.key.info.wasCaptured=c.value;
    }
    void full(SectorPreset p){for(var mode:Mode.values())Core.settings.put(CampaignStars.progressKey(p,mode),mode.maximum);}
    @Test void freshErekirRequires15SerpuloMainStars(){
        assertTrue(CampaignProgression.planetUnlocked(Planets.serpulo));
        assertFalse(CampaignProgression.planetUnlocked(Planets.erekir));
        full(SectorPresets.groundZero);full(SectorPresets.frozenForest);
        full(SectorPresets.crateredBattleground);
        assertEquals(15,CampaignProgression.serpuloStars());
        assertTrue(CampaignProgression.planetUnlocked(Planets.erekir));
    }
    @Test void branchesCannotPayPlanetThreshold(){
        for(var p:WatchCampaign.sectors)if(p.planet==Planets.serpulo)full(p);
        assertEquals(0,CampaignProgression.serpuloStars());
        assertFalse(CampaignProgression.planetUnlocked(Planets.erekir));
    }
    @Test void existingEarnedErekirProgressKeepsAccess(){
        Core.settings.put(CampaignStars.progressKey(SectorPresets.onset,Mode.NORMAL),1);
        assertTrue(CampaignProgression.planetUnlocked(Planets.erekir));
    }
    @Test void newGateGraphCanReachAll280Stars(){
        boolean changed;
        int passes=0;
        do{
            changed=false;
            for(var preset:content.sectors()) if(CampaignStars.eligible(preset)&&CampaignStars.unlocked(preset)){
                for(var mode:Mode.values()){
                    if(!CampaignProgression.allowed(preset,mode)||(mode!=Mode.NORMAL&&!CampaignStars.normalComplete(preset)))continue;
                    if(CampaignStars.stars(preset,mode)<mode.maximum){Core.settings.put(CampaignStars.progressKey(preset,mode),mode.maximum);changed=true;}
                }
            }
            assertTrue(++passes<=168);
        }while(changed);
        assertEquals(280,CampaignStars.totalStars());
    }
}
