package mindustry.ui;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.CampaignStars.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.layout.*;
import mindustry.ui.layout.CampaignRouteLayout.*;

/** Mission markers embedded in a continuous illustrated campaign atlas. */
public class CampaignRouteView extends WidgetGroup{
    public final CampaignRouteLayout route;
    private final ObjectMap<Node, Button> buttons = new ObjectMap<>();
    private final TextureRegion map;
    private final Color road = Color.valueOf("f3d49c"), sideRoad = Color.valueOf("c7a7eb");

    public CampaignRouteView(Planet planet, TextureRegion map, Cons<SectorPreset> selected){
        this.map = map;
        route = new CampaignRouteLayout(planet);
        setTransform(false);
        for(var node : route.nodes){
            Button button = new Button(new Button.ButtonStyle(Styles.clearNonei));
            button.top().margin(0f);
            button.name = "route-" + node.preset().name;
            button.image(node.branch() >= 0 ? Icon.star : Icon.terrain).size(26f)
                .color(CampaignStars.unlocked(node.preset()) ? node.branch() >= 0 ? sideRoad : Pal.accent : Color.lightGray).row();
            button.add(node.branch() >= 0 ? Core.bundle.get("stars.branch") + " · " + node.preset().localizedName : node.number() + ". " + node.preset().localizedName)
                .wrap().width(116f).height(28f).fontScale(0.7f).color(Color.white).row();
            button.table(stars -> {
                for(var mode : Mode.values()){
                    for(int i = 0; i < mode.maximum; i++){
                        stars.image(Icon.star).size(11f).color(i < CampaignStars.stars(node.preset(), mode) ? Pal.accent : Color.gray).padRight(1f);
                    }
                    if(mode == Mode.NORMAL) stars.add().width(5f);
                }
                if(!CampaignStars.unlocked(node.preset())) stars.image(Icon.lock).size(12f).padLeft(3f);
            }).padTop(3f).row();
            if(node.branch() >= 0){
                button.add(Core.bundle.format("stars.route.threshold", WatchCampaign.starRequirements[node.branch()]))
                    .fontScale(0.55f).color(sideRoad);
            }
            button.clicked(() -> selected.get(node.preset()));
            button.addListener(new Tooltip(table -> table.background(Tex.button).margin(12f)
                .add(node.preset().localizedName + "\n" + CampaignProgression.requirements(node.preset(), Mode.NORMAL))
                .wrap().width(350f)));
            addChild(button);
            buttons.put(node, button);
        }
    }

    @Override
    public void layout(){
        for(var node : route.nodes){
            Button button = buttons.get(node);
            button.setBounds(Scl.scl(node.x() - 62f), Scl.scl(node.y() - 52f), Scl.scl(CampaignRouteLayout.nodeWidth), Scl.scl(CampaignRouteLayout.nodeHeight));
            button.validate();
        }
    }

    @Override public float getPrefWidth(){ return Scl.scl(route.width); }
    @Override public float getPrefHeight(){ return Scl.scl(route.height); }

    @Override
    public void draw(){
        validate();
        float ox = x, oy = y;
        Draw.color(Color.white, parentAlpha);
        Draw.rect(map, ox + getWidth() / 2f, oy + getHeight() / 2f, getWidth(), getHeight());
        Node hovered = route.nodes.find(node -> buttons.get(node).isOver());
        for(var edge : route.edges){
            boolean highlighted = hovered != null && (edge.from() == hovered || edge.to() == hovered);
            if(!highlighted) continue;
            float x1 = ox + Scl.scl(edge.from().x()), y1 = oy + Scl.scl(edge.from().y() + 27f);
            float x2 = ox + Scl.scl(edge.to().x()), y2 = oy + Scl.scl(edge.to().y() + 27f);
            Color color = edge.reward() || edge.to().branch() >= 0 ? sideRoad : road;
            Draw.color(Color.black, 0.5f * parentAlpha);
            Lines.stroke(Scl.scl(6f));
            Lines.curve(x1, y1, x1 + (x2 - x1) * 0.45f, y1, x1 + (x2 - x1) * 0.55f, y2, x2, y2, 30);
            Draw.color(color, parentAlpha);
            Lines.stroke(Scl.scl(3f));
            Lines.curve(x1, y1, x1 + (x2 - x1) * 0.45f, y1, x1 + (x2 - x1) * 0.55f, y2, x2, y2, 30);
        }
        for(var node : route.nodes){
            float cx = ox + Scl.scl(node.x()), cy = oy + Scl.scl(node.y() + 27f);
            Draw.color(Color.black, 0.85f * parentAlpha);
            Fill.circle(cx, cy, Scl.scl(18f));
            boolean unlocked = CampaignStars.unlocked(node.preset());
            Draw.color(unlocked ? CampaignStars.stars(node.preset()) == 5 ? Pal.heal : node.branch() >= 0 ? sideRoad : Pal.accent : Color.gray, parentAlpha);
            Lines.stroke(Scl.scl(hovered == node ? 4f : 2f));
            Lines.circle(cx, cy, Scl.scl(18f));
            Draw.color(Color.black, 0.52f * parentAlpha);
            Fill.rect(cx, oy + Scl.scl(node.y() - 20f), Scl.scl(124f), Scl.scl(node.branch() >= 0 ? 66f : 52f));
        }
        Draw.reset();
        super.draw();
    }
}
