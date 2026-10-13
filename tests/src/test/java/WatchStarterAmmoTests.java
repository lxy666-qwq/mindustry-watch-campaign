import arc.util.*;
import mindustry.content.*;
import mindustry.maps.planet.*;
import mindustry.world.blocks.power.PowerNode.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class WatchStarterAmmoTests{
    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void initialDuoAmmunitionUsesNativeAmmoStorage(int level){
        logic.reset();
        Time.setDeltaProvider(() -> 1f);
        CampaignStars.loadMission(WatchCampaign.sectors[level], CampaignStars.Mode.NORMAL);
        logic.play();
        int turrets = 0;
        for(var building : mindustry.gen.Groups.build){
            if(building.team == state.rules.defaultTeam && building.block == Blocks.duo){
                var turret = (ItemTurretBuild)building;
                assertTrue(turret.totalAmmo > 0, "prebuilt turrets must have usable firing ammunition");
                assertTrue(turret.totalAmmo <= ((mindustry.world.blocks.defense.turrets.ItemTurret)Blocks.duo).maxAmmo);
                assertEquals(0, turret.items.get(Items.copper), "item module copper does not load a turret");
                assertNotNull(turret.peekAmmo());
                turrets++;
            }
        }
        assertEquals(2, turrets);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    void prebuiltGridActuallyConnectsAndChargesBattery(int level){
        logic.reset();
        Time.setDeltaProvider(() -> 1f);
        CampaignStars.loadMission(WatchCampaign.sectors[level], CampaignStars.Mode.NORMAL);
        logic.play();
        var base = WatchMapGenerator.core(world.width());
        var generator = world.tile(base.x - 8, base.y - 7).build;
        var battery = world.tile(base.x + 3, base.y - 7).build;
        var node = (PowerNodeBuild)world.tile(base.x - 2, base.y - 7).build;
        assertTrue(node.power.links.contains(generator.pos()));
        assertTrue(node.power.links.contains(battery.pos()), "the starter battery must be within native laser range");
        assertSame(generator.power.graph, battery.power.graph);
        for(int tick = 0; tick < 120; tick++){
            generator.update();
            node.power.graph.update();
        }
        assertTrue(battery.power.status > 0f, "real coal generation must charge the starter battery");
    }
}
