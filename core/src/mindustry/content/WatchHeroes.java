package mindustry.content;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import mindustry.ai.types.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;

/** One optional, finite deployment per star mission; native units handle control and save data. */
public class WatchHeroes{
    public static UnitType sentinel;
    public static SentinelPost post;
    public static final String deployedKey = "stars.sentinel.deployed";

    public static void load(){
        sentinel = new WatchSpriteUnitType("watch-sentinel", "sentinel", 18f);
        sentinel.constructor = UnitEntity::create;
        sentinel.controller = u -> new CommandAI();
        sentinel.flying = sentinel.lowAltitude = true;
        sentinel.isEnemy = false;
        sentinel.health = 180f;
        sentinel.speed = 2.2f;
        sentinel.accel = 0.1f;
        sentinel.drag = 0.05f;
        sentinel.rotateSpeed = 12f;
        sentinel.hitSize = 10f;
        sentinel.itemCapacity = 20;
        sentinel.mineTier = 1;
        sentinel.mineSpeed = 4f;
        sentinel.buildSpeed = 0.35f;
        sentinel.engineOffset = 6f;
        sentinel.weapons.add(new Weapon("small-basic-weapon"){{
            reload = 150f;
            mirror = false;
            x = 0f;
            y = 2f;
            bullet = new LaserBoltBulletType(3f, 6f){{
                lifetime = 28f;
                status = StatusEffects.slow;
                statusDuration = 60f;
                collidesAir = false;
                buildingDamageMultiplier = 0f;
                backColor = trailColor = hitColor = Color.valueOf("77dbc4");
                frontColor = Color.white;
            }};
        }});
        post = new SentinelPost("watch-sentinel-post");
    }

    public static boolean available(){
        return CampaignStars.active() && !state.rules.tags.getBool("stars.native") &&
            CampaignStars.completedReward(1) && !net.active() && !state.rules.editor && !state.rules.pvp;
    }

    public static class SentinelPost extends Block{
        public SentinelPost(String name){
            super(name);
            requirements(Category.units, with(Items.lead, 60, Items.silicon, 40));
            size = 2;
            health = 260;
            solid = configurable = destructible = true;
            config(Integer.class, (SentinelPostBuild build, Integer action) -> {
                if(action != 1 || player.team() != build.team) return;
                Unit unit = build.deploy();
                if(unit != null) InputHandler.unitControl(player, unit);
            });
        }

        @Override
        public void load(){
            super.load();
            region = Core.atlas.find("repair-point");
            fullIcon = uiIcon = region;
        }

        public class SentinelPostBuild extends Building{
            public boolean canDeploy(){
                var core = team.core();
                return available() && team == state.rules.defaultTeam &&
                    !state.rules.tags.getBool(deployedKey) && core != null &&
                    core.items.get(Items.graphite) >= 40 && core.items.get(Items.silicon) >= 30;
            }

            public Unit deploy(){
                if(!canDeploy()) return null;
                var core = team.core();
                core.items.remove(Items.graphite, 40);
                core.items.remove(Items.silicon, 30);
                state.rules.tags.put(deployedKey, "true");
                return sentinel.spawn(team, x, y);
            }

            @Override
            public void buildConfiguration(Table table){
                table.button(Core.bundle.get("watch.sentinel.deploy"), () -> configure(1))
                    .size(330f, 70f).disabled(button -> !canDeploy()).get().name = "watch-sentinel-deploy";
            }
        }
    }
}
