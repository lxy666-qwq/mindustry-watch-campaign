package mindustry.ui.layout;

import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.type.*;

/** Schematic routes use actual prerequisites; positions do not claim planetary geography. */
public class CampaignRouteLayout{
    public final Seq<Node> nodes = new Seq<>();
    public final Seq<Edge> edges = new Seq<>();
    public final ObjectMap<SectorPreset, Node> byPreset = new ObjectMap<>();
    public static final float nodeWidth = 124f, nodeHeight = 92f;
    public final float width = 2172f, height = 724f;
    private final ObjectIntMap<SectorPreset> ranks = new ObjectIntMap<>();

    public record Node(SectorPreset preset, float x, float y, int number, int branch){}
    public record Edge(Node from, Node to, boolean reward){}

    public CampaignRouteLayout(Planet planet){
        Seq<SectorPreset> mains = CampaignStars.mainLevels(planet);
        mains.sort((a, b) -> {
            int order = Integer.compare(rank(a), rank(b));
            return order == 0 ? Integer.compare(a.id, b.id) : order;
        });
        int[] xs = planet == Planets.serpulo ? new int[]{80,160,245,335,490,575,660,760,840,910,980,1045,1110,1175,1240,1305,1370,1450,1510,1570,1630,1690,1750,1810,1870,1930,1990,2050,2110} : new int[]{100,220,340,460,580,700,820,940,1060,1180,1300,1420,1540,1660,1780,1900,2020};
        int[] ys = planet == Planets.serpulo ? new int[]{570,450,590,360,320,620,490,220,580,430,270,640,490,620,440,300,570,390,665,120,240,460,660,300,530,410,130,280,650} : new int[]{500,630,480,600,380,540,660,300,640,490,610,400,610,440,560,660,460};
        for(int i = 0; i < mains.size; i++){
            add(new Node(mains.get(i), xs[i], ys[i], i + 1, -1));
        }
        int[][] branches = {{150,330},{430,660},{400,510},{630,280},{1550,550},{900,140},{1075,390},{620,650},{1280,360},{2000,230}};
        for(int i = 0; i < WatchCampaign.sectors.length; i++){
            var preset = WatchCampaign.sectors[i];
            if(preset.planet != planet) continue;
            add(new Node(preset, branches[i][0], branches[i][1], 0, i));
        }
        for(var node : nodes){
            for(var parent : CampaignProgression.parents(node.preset)){
                if(parent.planet == planet) edges.add(new Edge(byPreset.get(parent), node, false));
            }
            int reward = CampaignProgression.requiredReward(node.preset, Mode.NORMAL);
            if(reward >= 0 && WatchCampaign.sectors[reward].planet == planet){
                edges.add(new Edge(byPreset.get(WatchCampaign.sectors[reward]), node, true));
            }
        }
    }

    private void add(Node node){
        nodes.add(node);
        byPreset.put(node.preset, node);
    }

    private int rank(SectorPreset preset){
        if(ranks.containsKey(preset)) return ranks.get(preset, 0);
        int result = 0;
        for(var parent : CampaignProgression.parents(preset)){
            if(parent.planet == preset.planet) result = Math.max(result, rank(parent) + 1);
        }
        int reward = CampaignProgression.requiredReward(preset, Mode.NORMAL);
        if(reward >= 0 && WatchCampaign.sectors[reward].planet == preset.planet){
            result = Math.max(result, rank(WatchCampaign.sectors[reward]) + 1);
        }
        ranks.put(preset, result);
        return result;
    }
}
