package mindustry.type;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.gen.*;

/** Standalone unit artwork drawn at a fixed world size, without changing the game's atlas. */
public class WatchSpriteUnitType extends UnitType{
    private final String sprite;
    private final float spriteSize;

    public WatchSpriteUnitType(String name, String sprite, float spriteSize){
        super(name);
        this.sprite = sprite;
        this.spriteSize = spriteSize;
        drawCell = false;
        createWreck = false;
    }

    @Override
    public void load(){
        super.load();
        var texture = new Texture(Core.files.internal("watch-units/" + sprite + ".png"));
        region = previewRegion = fullIcon = uiIcon = Core.atlas.addRegion(name, texture, 0, 0, texture.width, texture.height);
        shadowRegion = Core.atlas.find("particle");
    }

    @Override
    public void drawOutline(Unit unit){}

    @Override
    public void drawBody(Unit unit){
        applyColor(unit);
        Draw.rect(region, unit.x, unit.y, spriteSize, spriteSize, unit.rotation - 90f);
        Draw.reset();
    }
}
