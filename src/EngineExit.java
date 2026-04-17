import city.cs.engine.BoxShape;
import city.cs.engine.Sensor;
import city.cs.engine.SensorEvent;
import city.cs.engine.SensorListener;
import city.cs.engine.StaticBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

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

        Sensor sensor = new Sensor(this, new BoxShape(halfWidth, halfHeight));
        sensor.addSensorListener(this);
    }

    @Override
    public void beginContact(SensorEvent e) {
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
