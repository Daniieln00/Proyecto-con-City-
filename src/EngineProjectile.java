import city.cs.engine.CircleShape;
import city.cs.engine.CollisionEvent;
import city.cs.engine.CollisionListener;
import city.cs.engine.DynamicBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

public class EngineProjectile extends DynamicBody implements CollisionListener {
    private static final CircleShape SHAPE = new CircleShape(0.16f);

    private final int damage;
    private final String style;
    private final boolean fromPlayer;
    private int remainingFrames;
    private boolean alive = true;

    public EngineProjectile(EngineGameWorld world, Vec2 position, Vec2 velocity, int damage, String style, boolean fromPlayer) {
        super(world, SHAPE);
        this.damage = damage;
        this.style = style;
        this.fromPlayer = fromPlayer;

        setGravityScale(0f);
        setBullet(true);
        setPosition(position);
        setLinearVelocity(velocity);
        setFillColor(colorFor(style));
        setLineColor(colorFor(style));
        remainingFrames = lifetimeFor(style, fromPlayer);
        addCollisionListener(this);
    }

    @Override
    public void collide(CollisionEvent event) {
        ((EngineGameWorld) getWorld()).handleProjectileCollision(this, event.getOtherBody());
    }

    public int getDamage() {
        return damage;
    }

    public boolean isFromPlayer() {
        return fromPlayer;
    }

    public boolean isAlive() {
        return alive;
    }

    public void markDestroyed() {
        alive = false;
        setLinearVelocity(new Vec2(0f, 0f));
        setFillColor(new Color(0, 0, 0, 0));
        setLineColor(new Color(0, 0, 0, 0));
        setPosition(new Vec2(-100f, -100f));
    }

    public boolean tickLifetime() {
        if (--remainingFrames <= 0) {
            alive = false;
            return true;
        }
        return false;
    }

    private static int lifetimeFor(String style, boolean fromPlayer) {
        if ("fire".equalsIgnoreCase(style)) {
            return fromPlayer ? 14 : 28;
        }
        if ("rifle".equalsIgnoreCase(style)) {
            return 28;
        }
        return 18;
    }

    private static Color colorFor(String style) {
        if ("fire".equalsIgnoreCase(style)) {
            return new Color(255, 96, 24);
        }
        if ("rifle".equalsIgnoreCase(style)) {
            return new Color(0, 245, 255);
        }
        return new Color(255, 240, 0);
    }
}
