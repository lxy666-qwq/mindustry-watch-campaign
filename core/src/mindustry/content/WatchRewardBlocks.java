package mindustry.content;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;

/** Optional emergency support earned by completing the first branch with all five stars. */
public class WatchRewardBlocks{
    public static FieldBandage fieldBandage;

    public static void load(){
        fieldBandage = new FieldBandage("field-bandage");
    }

    public static void applyResearch(Rules rules){
        if(!rules.tags.containsKey(CampaignStars.prefix + "preset") || !CampaignStars.completedReward(0)) return;
        rules.researched.add(fieldBandage);
        if(rules.blockWhitelist) rules.bannedBlocks.add(fieldBandage);
    }

    public static class FieldBandage extends Block{
        public final float range = 8f * tilesize;
        public final float wallCooldown = 60f;

        public FieldBandage(String name){
            super(name);
            requirements(Category.effect, with(Items.graphite, 50, Items.silicon, 40));
            health = 280;
            size = 2;
            update = solid = hasPower = hasItems = true;
            itemCapacity = 20;
            canOverdrive = false;
            group = BlockGroup.projectors;
            consumePower(0.7f);
            consumeItems(with(Items.graphite, 2));
        }

        @Override
        public void load(){
            super.load();
            region = Core.atlas.find("mend-projector");
            fullIcon = uiIcon = region;
        }

        @Override
        public TextureRegion[] icons(){
            return new TextureRegion[]{region};
        }

        public String cooldownKey(Building wall){
            return CampaignStars.prefix + "bandage.wall." + wall.pos();
        }

        public String identityCooldownKey(Building wall){
            return CampaignStars.prefix + "bandage.asset." + CampaignStars.identity(wall);
        }

        public class FieldBandageBuild extends Building{
            private Building target;

            private boolean eligible(Building wall){
                return wall.team == team && wall.block instanceof Wall && wall.health > 0f &&
                    wall.health <= wall.maxHealth * 0.3f && !wall.isHealSuppressed() && dst2(wall) <= range * range &&
                    CampaignStars.elapsed() >= Math.max(state.rules.tags.getFloat(cooldownKey(wall), 0f),
                        state.rules.tags.getFloat(identityCooldownKey(wall), 0f));
            }

            private Building findWall(){
                Building nearest = null;
                float distance = Float.POSITIVE_INFINITY;
                for(var wall : team.data().buildings){
                    if(eligible(wall) && dst2(wall) < distance){
                        nearest = wall;
                        distance = dst2(wall);
                    }
                }
                return nearest;
            }

            @Override
            public boolean shouldConsume(){
                target = CampaignStars.active() && !state.rules.tags.getBool(CampaignStars.prefix + "native") &&
                    !net.active() && CampaignStars.completedReward(0) && enabled ? findWall() : null;
                return target != null;
            }

            @Override
            public void updateTile(){
                //Native consumers guarantee real power and graphite. Recheck the shared wall cooldown after another tower updates.
                if(efficiency < 0.999f || target == null || !eligible(target)) return;
                consume();
                target.heal(target.maxHealth * 0.15f);
                target.recentlyHealed();
                state.rules.tags.put(cooldownKey(target), Float.toString(CampaignStars.elapsed() + wallCooldown));
                state.rules.tags.put(identityCooldownKey(target), Float.toString(CampaignStars.elapsed() + wallCooldown));
                Fx.healBlockFull.at(target.x, target.y, target.block.size, Color.valueOf("84f491"), target.block);
            }

            @Override
            public void drawSelect(){
                Drawf.dashCircle(x, y, range, Pal.heal);
            }
        }
    }
}
