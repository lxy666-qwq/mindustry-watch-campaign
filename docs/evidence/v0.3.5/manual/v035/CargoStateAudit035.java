import arc.struct.*;
import mindustry.gen.*;
import static mindustry.Vars.*;

/** Includes sleeping native buildings by observing the map, rather than the active update group. */
public class CargoStateAudit035{
    public static StringMap world(){
        var map=OnsetStateAudit.world();
        map.keys().toSeq().each(key->{if(key.startsWith("build.")||key.equals("buildings"))map.remove(key);});
        int buildings=0;
        for(var tile:world.tiles){
            var building=tile.build;
            if(building==null||building.tile!=tile)continue;
            buildings++;
            String id="build."+building.pos()+".";
            map.put(id+"identity",building.block.name+":"+building.team.id);
            map.put(id+"health",Float.toString(building.health));
            if(building.items!=null)for(var item:content.items()){
                int amount=building.items.get(item);if(amount!=0)map.put(id+"item."+item.name,Integer.toString(amount));
            }
            if(building.liquids!=null)for(var liquid:content.liquids()){
                float amount=building.liquids.get(liquid);if(amount!=0f)map.put(id+"liquid."+liquid.name,Float.toString(amount));
            }
        }
        map.put("buildings",Integer.toString(buildings));
        return map;
    }
}
