package mindustry.maps.planet;

import arc.math.*;
import arc.files.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.maps.generators.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.MessageBlock.*;
import mindustry.world.blocks.power.PowerNode.*;

import static mindustry.Vars.*;
import static mindustry.content.Blocks.*;

/** Fixed strategic routes, with seeded scenery and ten distinct terrain layouts. */
public class WatchMapGenerator extends FileMapGenerator{
    private final int level;
    private Tiles tiles;
    private int size;
    private Block ground, rock;

    public WatchMapGenerator(SectorPreset preset, int level){
        super(new Map(new Fi("watch-" + (level + 1) + ".msav"), WatchCampaign.stages[level].size(), WatchCampaign.stages[level].size(),
            StringMap.of("name", preset.localizedName), false), preset);
        this.level = level;
    }

    public static Point2 core(int size){ return new Point2(size / 2, 26); }
    public static Point2 primary(int size){ return new Point2(size / 2, size - 23); }
    public static Point2 flank(int size){ return new Point2(size - 25, size * 3 / 5); }
    public static Point2 naval(int size){ return new Point2(25, size * 3 / 5); }
    public static Point2 gate(int size){
        var side = flank(size);
        return new Point2(Math.round(Mathf.lerp(side.x, size / 2 + 20, 0.35f)), Math.round(Mathf.lerp(side.y, size / 3, 0.35f)));
    }
    public static Point2 cache(int size){ return new Point2(size / 2 - 36, size * 2 / 5); }

    @Override
    public void generate(Tiles tiles, WorldParams params){
        this.tiles = tiles;
        size = tiles.width;
        tiles.fill();
        ground = switch(level){
            case 0, 3 -> grass;
            case 1, 7 -> basalt;
            case 2 -> snow;
            case 4, 5 -> sand;
            case 6 -> moss;
            case 8 -> darksand;
            default -> stone;
        };
        rock = switch(level){
            case 2 -> snowWall;
            case 4, 5 -> sandWall;
            case 6 -> sporePine;
            case 8 -> duneWall;
            default -> stoneWall;
        };

        for(Tile tile : tiles){
            int x = tile.x, y = tile.y;
            float noise = Simplex.noise2d(410 + level, 3, 0.55, 0.065, x, y);
            tile.setFloor(ground.asFloor());
            boolean border = x < 7 || y < 7 || x >= size - 7 || y >= size - 7;
            tile.setBlock(border || noise > (level == 4 ? 0.65 : 0.46) ? rock : air);
            if(!border){
                if(level == 2 && Mathf.dst(x, y, size / 2f, size * 0.55f) < 27f) tile.setFloor(ice.asFloor());
                if(level == 3 && Math.abs(y - size / 2) < 9) tile.setFloor(deepwater.asFloor());
                if(level == 5 && x < size * 0.42f){ tile.setFloor(deepwater.asFloor()); tile.setBlock(air); }
                if(level == 8 && Math.abs(y - size * 0.56f - Math.sin(x / 13f) * 7) < 6){ tile.setFloor(slag.asFloor()); tile.setBlock(air); }
                if(level == 9 && x < size * 0.3f){ tile.setFloor(moss.asFloor()); }
                if(level == 9 && x > size * 0.72f){ tile.setFloor(hotrock.asFloor()); }
                if(level == 9 && y > size * 0.75f){ tile.setFloor(snow.asFloor()); }
            }
        }

        var base = core(size);
        var entry = primary(size);
        int bend = level == 1 ? 28 : level == 4 ? 25 : level == 6 ? -28 : 16;
        //The main route deliberately bends: artillery corners and flanking spaces differ between stages.
        road(entry.x, entry.y, size / 2 + bend, size * 3 / 4, 6);
        road(size / 2 + bend, size * 3 / 4, size / 2 - bend, size / 2, 6);
        road(size / 2 - bend, size / 2, size / 2 + bend / 2, size / 3, 6);
        road(size / 2 + bend / 2, size / 3, base.x, base.y, 7);
        disk(base.x, base.y, 24, stone, true);
        disk(entry.x, entry.y, 10, ground, true);

        //A permanent eastern crossing remains reachable when the central ice/ford changes.
        if(level == 2 || level == 3 || level == 8){
            road(size / 2 + bend, size * 3 / 4, size / 2 + 33, size / 2, 5);
            road(size / 2 + 33, size / 2, base.x, base.y + 18, 5);
            paintFord(level == 2 ? ice : level == 8 ? basalt : sand, false);
        }

        var side = flank(size);
        road(side.x, side.y, size / 2 + 20, size / 3, 5);
        road(size / 2 + 20, size / 3, base.x, base.y + 18, 5);
        disk(side.x, side.y, 9, ground, true);
        var gate = gate(size);
        disk(gate.x, gate.y, 6, ground, true);
        for(int x = gate.x - 2; x <= gate.x + 2; x++){
            for(int y = gate.y - 6; y <= gate.y + 6; y++) tiles.getn(x, y).setBlock(rock);
        }

        if(level == 5){
            var sea = naval(size);
            waterRoad(sea.x, sea.y, base.x - 18, base.y + 15, 9);
            disk(sea.x, sea.y, 10, deepwater, true);
            //A firing quay and a dock beside the core make water defence and naval production useful.
            road(base.x - 26, base.y + 22, base.x - 26, size / 2, 4);
            disk(base.x - 18, base.y + 15, 7, water, true);
            tiles.getn(sea.x, sea.y).setOverlay(spawn);
        }
        if(level == 4){
            disk(size / 2 - 31, size / 3, 8, water, true);
            disk(size / 2 - 31, size / 3, 4, deepwater, true);
        }
        if(level == 7){
            disk(size / 2 - 26, size / 3 + 12, 7, hotrock, true);
        }

        //Optional forward mine and a repairable emplacement reward expansion.
        var cache = cache(size);
        road(cache.x, cache.y, base.x - 12, base.y + 16, 4);
        disk(cache.x, cache.y, 9, ground, true);
        orePatch(cache.x, cache.y + 3, level >= 2 ? oreTitanium : oreCoal, 5);
        tiles.getn(cache.x - 5, cache.y).setBlock(level >= 4 ? salvo : duo, Team.derelict);
        tiles.getn(cache.x + 5, cache.y).setBlock(battery, Team.derelict);
        for(int x = cache.x - 7; x <= cache.x + 7; x++) tiles.getn(x, cache.y + 9).setBlock(rock);

        orePatch(base.x - 14, base.y + 10, oreCopper, 5);
        orePatch(base.x + 14, base.y + 10, oreLead, 5);
        orePatch(base.x - 17, base.y + 27, oreCoal, 5);
        orePatch(base.x + 23, base.y + 29, oreCopper, 5);
        if(level >= 2) orePatch(base.x + 26, base.y + 41, oreTitanium, 5);
        if(level >= 7) orePatch(size / 2 - 33, size / 2 + 15, oreThorium, 6);
        //Sand for glass and a pumping pool are available without clearing the whole map.
        disk(base.x + 22, base.y, 6, sand, true);
        if(level != 5) disk(base.x - 23, base.y + 2, 5, water, true);
        if(level == 5){
            //A southern dock reaches the core's firing range without washing out the copper mine above it.
            waterRoad(25, size * 3 / 5, base.x - 23, base.y - 10, 7);
            waterRoad(base.x - 23, base.y - 10, base.x - 12, base.y - 11, 4);
        }

        tiles.getn(base.x, base.y).setBlock(coreShard, Team.sharded);
        starterMine(base.x - 14, base.y + 10, base.x - 2, base.y, false);
        starterMine(base.x + 14, base.y + 10, base.x + 2, base.y, true);
        for(int offset : new int[]{-7, 7}){
            tiles.getn(base.x + offset, base.y + 20).setBlock(duo, Team.sharded);
            tiles.getn(base.x + offset, base.y + 20).build.items.add(Items.copper, 100);
        }
        for(int offset = -12; offset <= 12; offset++){
            if(Math.abs(offset) > 2) tiles.getn(base.x + offset, base.y + 25).setBlock(copperWall, Team.sharded);
        }
        if(level >= 1){
            tiles.getn(base.x - 8, base.y - 7).setBlock(combustionGenerator, Team.sharded);
            tiles.getn(base.x - 8, base.y - 7).build.items.add(Items.coal, 20);
            tiles.getn(base.x - 4, base.y - 7).setBlock(powerNode, Team.sharded);
            tiles.getn(base.x + 3, base.y - 7).setBlock(battery, Team.sharded);
            var node = (PowerNodeBuild)tiles.getn(base.x - 4, base.y - 7).build;
            node.configureAny(tiles.getn(base.x - 8, base.y - 7).pos());
            node.configureAny(tiles.getn(base.x + 3, base.y - 7).pos());
        }

        tiles.getn(entry.x, entry.y).setOverlay(spawn);
        tiles.getn(side.x, side.y).setOverlay(spawn); //Future drop zone is visible from the beginning.
        tiles.getn(base.x + 5, base.y - 4).setBlock(message, Team.sharded);
        ((MessageBuild)tiles.getn(base.x + 5, base.y - 4).build).message.append(WatchCampaign.text("stage." + (level + 1) + ".briefing"));
        state.map = map;
    }

    private void starterMine(int x, int y, int endX, int endY, boolean left){
        tiles.getn(x, y).setBlock(mechanicalDrill, Team.sharded);
        //Size-two drills occupy x..x+1, y..y+1, so take items from their lower face.
        for(int cy = y - 1; cy >= endY; cy--) tiles.getn(x, cy).setBlock(conveyor, Team.sharded, 3);
        for(int cx = x; left ? cx >= endX : cx <= endX; cx += left ? -1 : 1){
            tiles.getn(cx, endY).setBlock(conveyor, Team.sharded, left ? 2 : 0);
        }
    }

    private void orePatch(int x, int y, Block ore, int radius){
        disk(x, y, radius + 1, ground, true);
        Geometry.circle(x, y, size, size, radius, (tx, ty) -> tiles.getn(tx, ty).setOverlay(ore));
    }

    private void road(int ax, int ay, int bx, int by, int radius){
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for(int step = 0; step <= steps; step++){
            float f = steps == 0 ? 0f : step / (float)steps;
            disk(Math.round(Mathf.lerp(ax, bx, f)), Math.round(Mathf.lerp(ay, by, f)), radius, ground, true);
        }
    }

    private void waterRoad(int ax, int ay, int bx, int by, int radius){
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for(int step = 0; step <= steps; step++){
            float f = step / (float)steps;
            disk(Math.round(Mathf.lerp(ax, bx, f)), Math.round(Mathf.lerp(ay, by, f)), radius, deepwater, true);
        }
    }

    private void disk(int x, int y, int radius, Block floor, boolean clear){
        Geometry.circle(x, y, size, size, radius, (tx, ty) -> {
            var tile = tiles.getn(tx, ty);
            tile.setFloor(floor.asFloor());
            if(clear) tile.setBlock(air);
        });
    }

    private void paintFord(Block floor, boolean network){
        paintFord(tiles, floor, network);
    }

    /** Marked western crossing; permanent east bridge is outside this rectangle. */
    public static void paintFord(Tiles tiles, Block floor, boolean network){
        int size = tiles.width;
        for(int x = size / 2 - 25; x <= size / 2 + 7; x++){
            for(int y = size / 2 - 8; y <= size / 2 + 8; y++){
                var tile = tiles.getn(x, y);
                if(tile.build == null && !tile.block().isStatic()){
                    if(network) tile.setFloorNet(floor); else tile.setFloor(floor.asFloor());
                }
            }
        }
    }
}
