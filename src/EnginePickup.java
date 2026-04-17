import city.cs.engine.BodyImage;
import city.cs.engine.CircleShape;
import city.cs.engine.Sensor;
import city.cs.engine.SensorEvent;
import city.cs.engine.SensorListener;
import city.cs.engine.StaticBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

public class EnginePickup extends StaticBody implements SensorListener {
    public enum Kind {
        AMMO,
        HEALTH
    }

    private final Kind kind;
    private final int amount;
    private boolean collected;
    private boolean visible = true;

    public EnginePickup(EngineGameWorld world, Kind kind, Vec2 position, int amount) {
        super(world);
        this.kind = kind;
        this.amount = amount;

        setPosition(position);
        setFillColor(new Color(0, 0, 0, 0));
        setLineColor(new Color(0, 0, 0, 0));
        addImage(SpriteLoader.loadBodyImage(imageFor(kind), 0.9f));

        Sensor sensor = new Sensor(this, new CircleShape(0.45f));
        sensor.addSensorListener(this);
    }

    @Override
    public void beginContact(SensorEvent e) {
        if (collected) {
            return;
        }
        if (e.getContactBody() instanceof EnginePlayer) {
            ((EngineGameWorld) getWorld()).collectPickup(this, (EnginePlayer) e.getContactBody());
        }
    }

    @Override
    public void endContact(SensorEvent e) {
    }

    public Kind getKind() {
        return kind;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isCollected() {
        return collected;
    }

    public void markCollected() {
        collected = true;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }

        this.visible = visible;
        removeAllImages();
        if (visible) {
            addImage(SpriteLoader.loadBodyImage(imageFor(kind), 0.9f));
        }
    }

    private static String imageFor(Kind kind) {
        if (kind == Kind.AMMO) {
            return "assets/craftpix/maps/level3/props/objects_house_0048_Layer-49.png";
        }
        return "assets/craftpix/maps/level3/props/objects_house_0022_Layer-23.png";
    }

    public static void preloadAssets() {
        SpriteLoader.preloadBodyImagesAsync(0.9f,
                imageFor(Kind.AMMO),
                imageFor(Kind.HEALTH));
    }
}
