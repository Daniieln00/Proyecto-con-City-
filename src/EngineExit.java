import city.cs.engine.BoxShape;
import city.cs.engine.Sensor;
import city.cs.engine.SensorEvent;
import city.cs.engine.SensorListener;
import city.cs.engine.StaticBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

// Esta clase marca la zona que activa el siguiente nivel.
public class EngineExit extends StaticBody implements SensorListener {
    private final float halfWidth;
    private final float halfHeight;

    public EngineExit(EngineGameWorld world, Vec2 position, float halfWidth, float halfHeight) {
        super(world);
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        setPosition(position);
        setFillColor(new Color(0, 0, 0, 0));
        setLineColor(new Color(0, 0, 0, 0));

        // Detecta al jugador pero no le corta el paso.
        Sensor sensor = new Sensor(this, new BoxShape(halfWidth, halfHeight));
        sensor.addSensorListener(this);
    }

    @Override
    public void beginContact(SensorEvent e) {
        // Solo el jugador puede activar esta zona.
        if (e.getContactBody() instanceof EnginePlayer) {
            ((EngineGameWorld) getWorld()).onExitReached();
        }
    }

    @Override
    public void endContact(SensorEvent e) {
    }

    public float getHalfWidth() {
        return halfWidth;
    }

    public float getHalfHeight() {
        return halfHeight;
    }
}
