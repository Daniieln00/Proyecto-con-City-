import city.cs.engine.CircleShape;
import city.cs.engine.Sensor;
import city.cs.engine.SensorEvent;
import city.cs.engine.SensorListener;
import city.cs.engine.StaticBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

// Esta clase crea objetos que dan vida o municion al tocarlos.
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
        addImage(SpriteLoader.loadBodyImage(imageFor(kind), 1.0f));

        // Se puede recoger, pero no bloquea el camino.
        Sensor sensor = new Sensor(this, new CircleShape(0.45f));
        sensor.addSensorListener(this);
    }

    @Override
    public void beginContact(SensorEvent e) {
        // Si ya se recogio, no hace nada otra vez.
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
        // El mundo lo borra al final del frame.
        collected = true;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }

        this.visible = visible;
        removeAllImages();
        if (visible) {
            addImage(SpriteLoader.loadBodyImage(imageFor(kind), 1.0f));
        }
    }

    private static String imageFor(Kind kind) {
        // Cada tipo usa una imagen distinta para reconocerlo rapido.
        if (kind == Kind.AMMO) {
            return "assets/craftpix/maps/level3/props/objects_house_0021_Layer-22.png";
        }
        return "assets/craftpix/maps/level3/props/objects_house_0020_Layer-21.png";
    }

    public static void preloadAssets() {
        SpriteLoader.preloadBodyImagesAsync(1.0f,
                imageFor(Kind.AMMO),
                imageFor(Kind.HEALTH));
    }
}
