import arc.*;
import arc.files.*;
import arc.scene.ui.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.desktop.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.*;
import static mindustry.Vars.*;

/** Isolated SDL presentation fixture; supplied units/materials are not gameplay results. */
public class UnitsDesktop060 extends DesktopLauncher{
    int step;
    long time;
    Unit carrier;
    boolean gate=Boolean.getBoolean("trial.freshGate");
    public UnitsDesktop060(){super(new String[0]);}
    public static void main(String[] args){
        System.setProperty("nodiscord","true");Vars.loadLogger();
        var prefs=new Settings();prefs.setDataDirectory(new Fi(System.getenv("MINDUSTRY_DATA_DIR")));prefs.load();prefs.put("locale","zh_CN");prefs.put("uiscale",100);prefs.manualSave();
        new arc.backend.sdl.SdlApplication(new UnitsDesktop060(),new arc.backend.sdl.SdlConfig(){{title="Watch 0.6.0 new content";width=1280;height=900;maximized=false;}});
    }
    void need(boolean yes,String why){if(!yes)throw new IllegalStateException(why);}
    void shot(String name){ScreenUtils.saveScreenshot(new Fi("C:/Users/Lenovo/watch060/"+name+".png"));}
    public void update(){
        super.update();if(!clientLoaded)return;
        if(time==0){time=System.nanoTime();return;}
        if((System.nanoTime()-time)/1e9f<2f)return;
        switch(step){
            case 0->{
                need(WatchCampaign.version.equals("0.6.0"),"new runtime version");
                Core.scene.root.getChildren().copy().each(e->{if(e instanceof Dialog d)d.hide();});
                if(gate){
                    need(CampaignProgression.serpuloStars()==0,"fresh profile");
                    need(!CampaignProgression.planetUnlocked(Planets.erekir),"fresh planet locked");
                }else{
                    need(CampaignStars.completedReward(1),"actual historical branch reward required");
                    need(CampaignProgression.planetUnlocked(Planets.erekir),"existing Erekir access preserved");
                }
                new CampaignStarsDialog().show();
            }
            case 1->{
                var button=(Button)Core.scene.find("route-planet-erekir");
                need(button!=null&&button.isDisabled()==gate,"actual Erekir button gate");
                shot(gate?"01-fresh-planet-locked":"02-existing-planet-access");
                if(gate){Log.info("NEW PLANET UI PASS: fresh profile, actual Erekir button disabled");Core.app.exit();return;}
                Core.scene.root.getChildren().copy().each(e->{if(e instanceof Dialog d)d.hide();});
                CampaignStars.loadMission(WatchCampaign.sectors[1],CampaignStars.Mode.NORMAL);logic.play();
                state.rules.fog=false;state.rules.staticFog=false;
                Team.sharded.core().items.set(Items.graphite,100);Team.sharded.core().items.set(Items.silicon,100);
                world.tile(68,33).setBlock(WatchHeroes.post,Team.sharded);
                var post=world.tile(68,33).build;
                player.team(Team.sharded);player.unit(UnitTypes.alpha.spawn(Team.sharded,68*tilesize,33*tilesize));
                renderer.setScale(6f);control.input.panCamera(Tmp.v1.set(68*tilesize,34*tilesize));
                control.input.config.showConfig(post);
            }
            case 2->{
                var button=(Button)Core.scene.find("watch-sentinel-deploy");need(button!=null&&!button.isDisabled(),"deploy available");button.fireClick();
                control.input.config.forceHide();
                carrier=WatchEnemies.carrier.spawn(Team.crux,72*tilesize,38*tilesize);
                state.set(mindustry.core.GameState.State.paused);
                ui.hudfrag.setHudText("[accent]v0.6.0 新内容 · 独立展示夹具[]\n青绿：可控哨兵；橙色：裂殖运兵车\n本场景人工放置单位与材料，仅验证画面，不计通关");
            }
            case 3->{
                need(player.unit().type==WatchHeroes.sentinel,"controlled independent hero");
                need(WatchHeroes.sentinel.region.texture!=UnitTypes.alpha.region.texture,"independent hero texture");
                need(WatchEnemies.carrier.region.texture!=UnitTypes.dagger.region.texture,"independent enemy texture");
                control.input.panCamera(Tmp.v1.set((player.unit().x+carrier.x)/2f,(player.unit().y+carrier.y)/2f));
                shot("03-independent-units");
            }
            case 4->{
                carrier.kill();
                need(Groups.unit.count(u->u.team==Team.crux&&u.type==UnitTypes.crawler)==2,"two native passengers");
                ui.hudfrag.setHudText("[accent]死亡释放：两只普通爬虫[]\n无递归分裂；先破甲，再用火焰／电弧清理\n独立画面验收，不计实战通关");
            }
            case 5->{shot("04-native-passengers");Log.info("NEW CONTENT SDL PASS: original textures, deployment control, native two-passenger death, existing planet access");Core.app.exit();}
        }
        step++;time=System.nanoTime();
    }
}
