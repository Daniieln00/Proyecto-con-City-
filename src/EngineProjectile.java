import city.cs.engine.CircleShape;
import city.cs.engine.CollisionEvent;
import city.cs.engine.CollisionListener;
import city.cs.engine.DynamicBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;

public class EngineProjectile extends DynamicBody implements CollisionListener {
    private static final CircleShape SHAPE = new CircleShape(0.16f);
    private static final String[] BOSS_FIRE_PATHS = {
            "assets/sprites/effects/boss_fire/fire_0000_Layer-4.png",
            "assets/sprites/effects/boss_fire/fire_0001_Layer-3.png",
            "assets/sprites/effects/boss_fire/fire_0002_Layer-2.png",
            "assets/sprites/effects/boss_fire/fire_0003_Layer-1.png",
            "assets/sprites/effects/boss_fire/fire_0004_Layer-6.png",
            "assets/sprites/effects/boss_fire/fire_0005_Layer-5.png"
    };

    private final int damage;
    private final String style;
    private final boolean fromPlayer;
    private int remainingFrames;
    private boolean alive = true;
    private int animationTick;
    private String currentSpritePath;
    private int playerHitCooldownFrames;

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
        refreshImage();
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
        // Projectile animation and lifetime are updated together once per frame.
        animationTick++;
        if (playerHitCooldownFrames > 0) {
            playerHitCooldownFrames--;
        }
        refreshImage();
        if (--remainingFrames <= 0) {
            alive = false;
            return true;
        }
        return false;
    }

    private static int lifetimeFor(String style, boolean fromPlayer) {
        if ("boss_fire".equalsIgnoreCase(style)) {
            return 90;
        }
        if ("shotgun".equalsIgnoreCase(style)) {
            return 15;
        }
        if ("rifle".equalsIgnoreCase(style)) {
            return 44;
        }
        return 36;
    }

    private static Color colorFor(String style) {
        if ("boss_fire".equalsIgnoreCase(style)) {
            return new Color(255, 96, 24);
        }
        if ("shotgun".equalsIgnoreCase(style)) {
            return new Color(255, 224, 92);
        }
        if ("rifle".equalsIgnoreCase(style)) {
            return new Color(0, 245, 255);
        }
        return new Color(255, 240, 0);
    }

    private void refreshImage() {
        if (!"boss_fire".equalsIgnoreCase(style)) {
            return;
        }

        String nextSpritePath = BOSS_FIRE_PATHS[(animationTick / 2) % BOSS_FIRE_PATHS.length];
        if (nextSpritePath.equals(currentSpritePath)) {
            return;
        }

        currentSpritePath = nextSpritePath;
        removeAllImages();
        addImage(SpriteLoader.loadBodyImage(nextSpritePath, 1.2f));
    }

    public boolean canHitPlayer() {
        return playerHitCooldownFrames == 0;
    }

    public void markPlayerHit() {
        playerHitCooldownFrames = 12;
    }
}
