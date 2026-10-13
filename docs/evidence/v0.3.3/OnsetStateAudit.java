import arc.struct.*;
import mindustry.content.*;
import mindustry.gen.*;
import java.util.Objects;
import static mindustry.Vars.*;

/** Observes native world state, never changes gameplay values. */
public class OnsetStateAudit{
    public static StringMap world(){
        StringMap map=new StringMap();
        map.put("tick",Double.toString(state.tick));
        map.put("wave",Integer.toString(state.wave));
        map.put("wavetime",Float.toString(state.wavetime));
        map.put("elapsed",Float.toString(CampaignStars.elapsed()));
        map.put("placed",Integer.toString(CampaignStars.placed()));
        map.put("minCore",Float.toString(CampaignStars.minimumCoreHealth()));
        map.put("coreLosses",Integer.toString(CampaignStars.coreLosses()));
        map.put("objectives",Integer.toString(state.rules.objectives.all.count(o->o.isCompleted())));
        map.put("limits",state.rules.limitX+","+state.rules.limitY+","+state.rules.limitWidth+","+state.rules.limitHeight);
        map.put("buildings",Integer.toString(Groups.build.size()));
        map.put("units",Integer.toString(Groups.unit.size()));
        map.put("stats.buildingsBuilt",Integer.toString(state.stats.buildingsBuilt));
        map.put("stats.buildingsDestroyed",Integer.toString(state.stats.buildingsDestroyed));
        map.put("stats.unitsCreated",Integer.toString(state.stats.unitsCreated));
        for(var item:content.items())map.put("stats.coreItemCount."+item.name,Integer.toString(state.stats.coreItemCount.get(item,0)));
        for(var building:Groups.build){
            String id="build."+building.pos()+".";
            map.put(id+"identity",building.block.name+":"+building.team.id);
            map.put(id+"health",Float.toString(building.health));
            if(building.items!=null)for(var item:content.items()){
                int amount=building.items.get(item);
                if(amount!=0)map.put(id+"item."+item.name,Integer.toString(amount));
            }
            if(building.liquids!=null)for(var liquid:content.liquids()){
                float amount=building.liquids.get(liquid);
                if(amount!=0f)map.put(id+"liquid."+liquid.name,Float.toString(amount));
            }
        }
        for(var unit:Groups.unit){
            String id="unit."+unit.id+".";
            map.put(id+"identity",unit.type.name+":"+unit.team.id);
            map.put(id+"position",unit.x+","+unit.y);
            map.put(id+"health",Float.toString(unit.health));
            map.put(id+"cargo",unit.item().name+":"+unit.stack.amount);
        }
        return map;
    }
    public static void equal(String name,StringMap before,StringMap after){
        for(var entry:before)if(!Objects.equals(entry.value,after.get(entry.key)))throw new IllegalStateException(name+" changed "+entry.key+": "+entry.value+" -> "+after.get(entry.key));
        if(before.size!=after.size)throw new IllegalStateException(name+" entry count changed "+before.size+" -> "+after.size);
    }
}
