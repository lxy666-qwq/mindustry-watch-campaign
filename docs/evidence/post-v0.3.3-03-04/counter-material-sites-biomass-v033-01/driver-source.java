import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.Mode;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import java.util.*;
import static mindustry.Vars.*;

/** Physical/new-map baseline audit only. No ticks, build commands, seeded scores, combat or victory. */
public class CounterMaterialAudit{
    static Fi out;
    static String costs(Block b){
        var result=new TreeMap<String,Integer>();
        for(var stack:b.requirements) result.put(stack.item.name,stack.amount);
        return result.toString();
    }
    static String inventory(){
        var result=new TreeMap<String,Integer>();
        for(var core:state.rules.defaultTeam.cores()) for(var item:content.items()){
            int amount=core.items.get(item);
            if(amount>0) result.merge(item.name,amount,Integer::sum);
        }
        return result.toString();
    }
    static String consumption(Block b){
        var result=new ArrayList<String>();
        for(var c:b.consumers){
            String desc=c.getClass().getSimpleName();
            if(c instanceof ConsumeItems items) desc=Arrays.toString(items.items);
            else if(c instanceof ConsumePower p) desc="power/s="+(p.usage*60)+" buffer="+p.capacity;
            else if(c instanceof ConsumeLiquid liquid) desc=liquid.liquid.name+"/s="+liquid.amount*60;
            else if(c instanceof ConsumeLiquids liquids) desc=Arrays.toString(liquids.liquids)+" per-tick";
            else if(c instanceof ConsumeLiquidFilter filter){
                var names=new ArrayList<String>();
                for(var liquid:content.liquids()) if(filter.filter.get(liquid)) names.add(liquid.name);
                desc=names+"/s="+filter.amount*60;
            }else if(c instanceof ConsumeItemFilter filter){
                var names=new ArrayList<String>();
                for(var item:content.items()) if(filter.filter.get(item)) names.add(item.name);
                desc=names.toString();
            }
            result.add(desc+(c.optional?" optional":" required")+(c.booster?" booster":""));
        }
        return result.toString();
    }
    static String output(Block b){
        var text=new ArrayList<String>();
        if(b instanceof GenericCrafter crafter){
            if(crafter.outputItems!=null) text.add("items="+Arrays.toString(crafter.outputItems));
            if(crafter.outputLiquids!=null) text.add("liquids/tick="+Arrays.toString(crafter.outputLiquids));
            text.add("craftSeconds="+crafter.craftTime/60);
        }
        if(b instanceof PowerGenerator power) text.add("power/s="+power.powerProduction*60);
        if(b instanceof HeatCrafter heat) text.add("heatRequired="+heat.heatRequirement);
        if(b instanceof AttributeCrafter crafter) text.add("floorAttribute="+crafter.attribute);
        if(b instanceof Drill drill) text.add("mineTier="+drill.tier);
        if(b instanceof BeamDrill drill) text.add("wallMineTier="+drill.tier);
        if(b instanceof WallCrafter wall) text.add("wallAttribute="+wall.attribute+" output="+wall.output.name);
        if(b instanceof ItemTurret turret) text.add("itemAmmo="+turret.ammoTypes.keys().toSeq().toString(",",item->item.name));
        if(b instanceof LiquidTurret turret) text.add("liquidAmmo="+turret.ammoTypes.keys().toSeq().toString(",",liquid->liquid.name));
        if(b instanceof Turret turret) text.add("heatRequired="+turret.heatRequirement+" rangeTiles="+turret.range/tilesize);
        return text.toString();
    }
    static void row(String file,String text){out.child(file).writeString(text+"\n",true);}
    public static void main(String[] args)throws Exception{
        out=new Fi(args[0]);
        if(out.exists()) throw new IllegalArgumentException("New audit output required: "+out);
        out.mkdirs();
        new Fi("local-work/mindustry-star-work/CounterMaterialAudit.java").copyTo(out.child("driver-source.java"));
        Fi assets=new Fi("local-work/mindustry-star-work/star033-runtime/assets");
        Log.useColors=false;
        Core.app=new MockApplication();
        Core.files=new MockFiles(){@Override public Fi internal(String path){return assets.child(path);}};
        Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.input=new MockInput();
        Core.settings=new Settings();Core.settings.setDataDirectory(out.child("profile"));Core.settings.setJson(JsonIO.json);
        headless=true;net=new Net(null);Vars.init();Vars.loadSettings();
        Core.bundle=I18NBundle.createBundle(Core.files.internal("bundles/bundle"),Locale.SIMPLIFIED_CHINESE);
        content.createBaseContent();logic=new Logic();netServer=new NetServer();content.init();
        var jar=new Fi(new java.io.File(CampaignStars.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        row("runtime.txt","watchVersion="+WatchCampaign.version+"\njar="+jar+"\njarModified="+jar.lastModified()+"\nassets="+assets+
            "\nnoTicks=true\nnoCombat=true\nnoScoreSeeding=true\nnoSharedProfile=true\nloadGatesBypassedForInspection=true\nloadoutBaseline=no earned-branch rewards");
        row("map-modes.tsv","map\tmode\trewardIndex\tplanet\twidth\theight\tcoreInventoryAfterPlay\tloadout\tplayerBuildingInventory\tplayerBuildings\tavailableEquipmentBaseline");
        row("resources.tsv","map\tsource\tresource\trawTiles\tclearAccessibleTiles\tnearestCoreTiles");
        row("player-buildings.tsv","map\tblock\tcount\titems\tliquids");
        row("all-team-buildings.tsv","map\tteam\tblock\tcount\titems\tliquids\tbreakableByPlayer\tbaselineRefundItems\tfillItems\tinfiniteResources");
        row("building-sites.tsv","map\tteam\tblock\tx\ty\tbreakableByPlayer\tcost\tdarkness\tdistanceCoreTiles");
        row("ore-sites.tsv","map\titem\tx\ty\tdistanceCoreTiles\tmechanicalDrillValid\twantedOre2x2\totherOre2x2");
        row("equipment.tsv","map\tmode\trewardIndex\tblock\tlocalizedName\tcost\tconsumption\toutputOrAmmo\tinitialMaterialShortfall");
        row("catalog.tsv","block\tlocalizedName\tcost\tconsumption\toutputOrAmmo");
        for(var block:content.blocks()) if(block.requirements.length>0)
            row("catalog.tsv",block.name+"\t"+block.localizedName+"\t"+costs(block)+"\t"+consumption(block)+"\t"+output(block));
        int maps=0,modeCount=0;
        boolean victory[]=new boolean[]{false};Events.on(EventType.GameOverEvent.class,e->victory[0]=true);
        for(var preset:content.sectors()){
            if(!CampaignStars.eligible(preset)) continue;
            if(args.length>1&&!preset.name.equals(args[1]))continue;
            boolean inspected=false;
            for(var mode:Mode.values()){
                int reward=CampaignProgression.requiredReward(preset,mode);
                if(reward<0) continue;
                logic.reset();
                CampaignStars.loadMission(preset,mode);logic.play();
                var core=state.rules.defaultTeam.core();
                var stock=new TreeMap<String,Integer>();
                var blocks=new TreeMap<String,Integer>();
                for(var b:state.rules.defaultTeam.data().buildings){
                    blocks.merge(b.block.name,1,Integer::sum);
                    if(b.items!=null)for(var item:content.items())if(b.items.get(item)>0)stock.merge(item.name,b.items.get(item),Integer::sum);
                }
                boolean erekir=preset.planet==Planets.erekir&&CampaignStars.branchIndex(preset)<0;
                var equipments=CampaignProgression.rewardSet(reward,erekir);
                row("map-modes.tsv",preset.name+"\t"+mode+"\t"+reward+"\t"+preset.planet.name+"\t"+world.width()+"\t"+world.height()+"\t"+inventory()+"\t"+state.rules.loadout+"\t"+stock+"\t"+blocks+"\t"+equipments.toString(",",b->b.name+":"+CampaignStars.available(b)));
                for(var b:equipments){
                    var missing=new TreeMap<String,Integer>();
                    for(var req:b.requirements)if(core.items.get(req.item)<req.amount)missing.put(req.item.name,req.amount-core.items.get(req.item));
                    row("equipment.tsv",preset.name+"\t"+mode+"\t"+reward+"\t"+b.name+"\t"+b.localizedName+"\t"+costs(b)+"\t"+consumption(b)+"\t"+output(b)+"\t"+missing);
                }
                modeCount++;
                if(!inspected){
                    maps++;inspected=true;
                    for(var item:content.items())for(boolean wall:new boolean[]{false,true}){
                        int raw=0,clear=0;float nearest=Float.POSITIVE_INFINITY;
                        for(Tile tile:world.tiles)if((wall?tile.wallDrop():tile.drop())==item){
                            raw++;if(world.getDarkness(tile.x,tile.y)<3&&(wall||tile.block()==Blocks.air)){clear++;nearest=Math.min(nearest,core.dst(tile.worldx(),tile.worldy())/tilesize);}
                        }
                        if(raw>0)row("resources.tsv",preset.name+"\t"+(wall?"wall-ore":"floor-ore")+"\t"+item.name+"\t"+raw+"\t"+clear+"\t"+nearest);
                    }
                    for(var liquid:content.liquids()){
                        int raw=0,clear=0;float nearest=Float.POSITIVE_INFINITY;
                        for(Tile tile:world.tiles)if(tile.floor().liquidDrop==liquid){
                            raw++;if(world.getDarkness(tile.x,tile.y)<3&&tile.block()==Blocks.air){clear++;nearest=Math.min(nearest,core.dst(tile.worldx(),tile.worldy())/tilesize);}
                        }
                        if(raw>0)row("resources.tsv",preset.name+"\tliquid-floor\t"+liquid.name+"\t"+raw+"\t"+clear+"\t"+nearest);
                    }
                    for(var attribute:new Attribute[]{Attribute.steam,Attribute.sand,Attribute.oil}){
                        int floors=0,walls=0;
                        for(Tile tile:world.tiles){if(tile.floor().attributes.get(attribute)>0)floors++;if(tile.block().attributes.get(attribute)>0)walls++;}
                        if(floors>0)row("resources.tsv",preset.name+"\tfloor-attribute\t"+attribute+"\t"+floors+"\t-1\t-1");
                        if(walls>0)row("resources.tsv",preset.name+"\twall-attribute\t"+attribute+"\t"+walls+"\t-1\t-1");
                    }
                    for(var item:new Item[]{Items.copper,Items.lead,Items.coal,Items.sand,Items.scrap}){
                        var sites=new ArrayList<Tile>();
                        for(Tile tile:world.tiles)if(tile.drop()==item&&tile.block()==Blocks.air&&world.getDarkness(tile.x,tile.y)<3)sites.add(tile);
                        sites.sort(Comparator.comparingDouble(t->core.dst(t.worldx(),t.worldy())));
                        for(int i=0;i<Math.min(20,sites.size());i++){
                            Tile tile=sites.get(i);int wanted=0,other=0;
                            for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++){
                                Tile part=world.tile(tile.x+dx,tile.y+dy);
                                if(part!=null&&part.drop()==item)wanted++;else if(part!=null&&part.drop()!=null)other++;
                            }
                            row("ore-sites.tsv",preset.name+"\t"+item.name+"\t"+tile.x+"\t"+tile.y+"\t"+core.dst(tile.worldx(),tile.worldy())/tilesize+"\t"+
                                Build.validPlaceIgnoreUnits(Blocks.mechanicalDrill,state.rules.defaultTeam,tile.x,tile.y,0,true,true)+"\t"+wanted+"\t"+other);
                        }
                    }
                    for(var entry:blocks.entrySet()){
                        var itemTotals=new TreeMap<String,Integer>();var liquidTotals=new TreeMap<String,Float>();
                        for(var b:state.rules.defaultTeam.data().buildings)if(b.block.name.equals(entry.getKey())){
                            if(b.items!=null)for(var item:content.items())if(b.items.get(item)>0)itemTotals.merge(item.name,b.items.get(item),Integer::sum);
                            if(b.liquids!=null)for(var liquid:content.liquids())if(b.liquids.get(liquid)>0)liquidTotals.merge(liquid.name,b.liquids.get(liquid),Float::sum);
                        }
                        row("player-buildings.tsv",preset.name+"\t"+entry.getKey()+"\t"+entry.getValue()+"\t"+itemTotals+"\t"+liquidTotals);
                    }
                    for(Team team:Team.all){
                        var teamBlocks=new TreeMap<String,Integer>();
                        for(var b:team.data().buildings){
                            teamBlocks.merge(b.block.name,1,Integer::sum);
                            row("building-sites.tsv",preset.name+"\t"+team.name+"\t"+b.block.name+"\t"+b.tileX()+"\t"+b.tileY()+"\t"+
                                Build.validBreak(state.rules.defaultTeam,b.tileX(),b.tileY())+"\t"+costs(b.block)+"\t"+world.getDarkness(b.tileX(),b.tileY())+"\t"+core.dst(b.x,b.y)/tilesize);
                            if(b instanceof LogicBuild processor){
                                var file=out.child("processors").child(preset.name+"-"+team.name+"-"+b.pos()+".txt");
                                file.parent().mkdirs();file.writeString(processor.code);
                            }
                        }
                        for(var entry:teamBlocks.entrySet()){
                            var itemTotals=new TreeMap<String,Integer>();var liquidTotals=new TreeMap<String,Float>();
                            var refundTotals=new TreeMap<String,Integer>();int breakable=0;
                            for(var b:team.data().buildings)if(b.block.name.equals(entry.getKey())){
                                if(b.items!=null)for(var item:content.items())if(b.items.get(item)>0)itemTotals.merge(item.name,b.items.get(item),Integer::sum);
                                if(b.liquids!=null)for(var liquid:content.liquids())if(b.liquids.get(liquid)>0)liquidTotals.merge(liquid.name,b.liquids.get(liquid),Float::sum);
                                if(Build.validBreak(state.rules.defaultTeam,b.tileX(),b.tileY())){
                                    breakable++;
                                    for(var req:b.block.requirements)refundTotals.merge(req.item.name,Math.round(req.amount*state.rules.buildCostMultiplier*state.rules.deconstructRefundMultiplier),Integer::sum);
                                }
                            }
                            row("all-team-buildings.tsv",preset.name+"\t"+team.name+"\t"+entry.getKey()+"\t"+entry.getValue()+"\t"+itemTotals+"\t"+liquidTotals+"\t"+breakable+"\t"+refundTotals+"\t"+team.rules().fillItems+"\t"+team.rules().infiniteResources);
                        }
                    }
                }
                if(CampaignStars.totalStars()!=0||victory[0])throw new IllegalStateException("Audit unexpectedly scored or ended a game");
                System.out.println("audited="+preset.name+" mode="+mode+" reward="+reward+" core="+inventory());
            }
        }
        row("runtime.txt","maps="+maps+"\nmodeVariants="+modeCount+"\nfinalStars="+CampaignStars.totalStars()+"\ngameOver=false");
        System.out.println("Audit completed maps="+maps+" modes="+modeCount+" stars=0; no ticks/combat/builds");
        System.exit(0);
    }
}
