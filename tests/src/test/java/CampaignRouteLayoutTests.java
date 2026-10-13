import arc.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.type.*;
import mindustry.ui.layout.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import java.util.stream.*;
import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class CampaignRouteLayoutTests{
    @BeforeAll static void init(){ ApplicationTests.launchApplication(); }
    static Stream<Planet> planets(){ return Stream.of(Planets.serpulo, Planets.erekir); }

    @ParameterizedTest @MethodSource("planets")
    void everyEligibleMissionHasOneNodeAndActualPrerequisites(Planet planet){
        var route = new CampaignRouteLayout(planet);
        var expected = content.sectors().select(preset -> preset.planet == planet && CampaignStars.eligible(preset));
        assertEquals(expected.size, route.nodes.size);
        assertEquals(expected.size, route.byPreset.size);
        for(var preset : expected){
            assertNotNull(route.byPreset.get(preset), preset.name);
            var node = route.byPreset.get(preset);
            for(var parent : CampaignProgression.parents(preset)){
                if(parent.planet != planet) continue;
                assertTrue(route.edges.contains(edge -> !edge.reward() && edge.from().preset() == parent && edge.to() == node), preset.name);
                if(node.branch() < 0) assertTrue(route.byPreset.get(parent).x() < node.x(), preset.name + " main route direction");
            }
            int reward = CampaignProgression.requiredReward(preset, Mode.NORMAL);
            if(reward >= 0 && WatchCampaign.sectors[reward].planet == planet){
                assertTrue(route.edges.contains(edge -> edge.reward() && edge.from().branch() == reward && edge.to() == node), preset.name);
            }
        }
    }

    @ParameterizedTest @MethodSource("planets")
    void clickTargetsFitAndNeverOverlapAndBranchesStayNearAnchors(Planet planet){
        var route = new CampaignRouteLayout(planet);
        for(var node : route.nodes){
            assertTrue(node.x() >= 62f && node.x() + 62f <= route.width, node.preset().name);
            assertTrue(node.y() >= 52f && node.y() + 40f <= route.height, node.preset().name);
            if(node.branch() >= 0){
                var anchor = route.byPreset.get(WatchCampaign.anchors[node.branch()]);
                assertTrue(Math.abs(node.x() - anchor.x()) <= 260f && Math.abs(node.y() - anchor.y()) <= 280f, node.preset().name);
            }
            for(var other : route.nodes){
                if(other == node) continue;
                assertTrue(Math.abs(other.x() - node.x()) >= CampaignRouteLayout.nodeWidth || Math.abs(other.y() - node.y()) >= CampaignRouteLayout.nodeHeight,
                    node.preset().name + " overlaps " + other.preset().name);
            }
        }
    }

    @ParameterizedTest @MethodSource("planets")
    void openingRoutesDoesNotChangeWorldOrScores(Planet planet){
        var tiles = world.tiles;
        var rules = state.rules;
        ObjectIntMap<String> scores = new ObjectIntMap<>();
        for(var preset : content.sectors()) if(CampaignStars.eligible(preset)) for(var mode : Mode.values()){
            scores.put(CampaignStars.progressKey(preset, mode), CampaignStars.stars(preset, mode));
        }
        new CampaignRouteLayout(planet);
        assertSame(tiles, world.tiles);
        assertSame(rules, state.rules);
        for(var preset : content.sectors()) if(CampaignStars.eligible(preset)) for(var mode : Mode.values()){
            assertEquals(scores.get(CampaignStars.progressKey(preset, mode), -1), CampaignStars.stars(preset, mode));
        }
    }

    @ParameterizedTest @MethodSource("planets")
    void everyNodeHasAnActualTerrainSource(Planet planet){
        assertTrue(Core.files.internal("route-maps/" + planet.name + "-v0.4.0.png").exists());
        String index = Core.files.internal("route-scenes/index.tsv").readString();
        for(var node : new CampaignRouteLayout(planet).nodes){
            assertTrue(Core.files.internal("route-scenes/" + node.preset().name + ".png").exists(), node.preset().name);
            assertTrue(index.lines().anyMatch(line -> line.startsWith(node.preset().name + "\t" + planet.name + "\t")), node.preset().name);
        }
    }
}
