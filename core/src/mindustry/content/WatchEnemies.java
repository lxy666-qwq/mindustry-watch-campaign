package mindustry.content;

import mindustry.ai.types.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;

/** A single, finite armored transport: its passengers never inherit the split ability. */
public class WatchEnemies{
    public static UnitType carrier;

    public static void load(){
        carrier = new WatchSpriteUnitType("watch-brood-carrier", "carrier", 25f);
        carrier.constructor = UnitEntity::create;
        carrier.controller = u -> new GroundAI();
        carrier.speed = 0.65f;
        carrier.drag = 0.1f;
        carrier.accel = 0.08f;
        carrier.rotateSpeed = 4f;
        carrier.health = 420f;
        carrier.armor = 4f;
        carrier.hitSize = 14f;
        carrier.targetAir = false;
        carrier.abilities.add(new SpawnDeathAbility(UnitTypes.crawler, 2, 6f));
        carrier.weapons.add(new Weapon("small-basic-weapon"){{
            x = 0f;
            y = 5f;
            mirror = false;
            reload = 60f;
            bullet = new BasicBulletType(2.5f, 12f){{
                lifetime = 30f;
                collidesAir = false;
            }};
        }});
    }
}
