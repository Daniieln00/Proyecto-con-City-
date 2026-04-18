import city.cs.engine.BodyImage;
import city.cs.engine.BoxShape;
import city.cs.engine.DynamicBody;
import city.cs.engine.GhostlyFixture;
import org.jbox2d.common.Vec2;

import java.awt.Color;
import java.util.Random;

public class EngineZombie extends DynamicBody {
    public enum Kind {
        BASIC,
        FAST,
        TANK,
        BOSS
    }

    private static final Random RANDOM = new Random();
    private static final String[][] STANDARD_VARIANT_PATHS = {
            createFramePaths("assets/sprites/zombies/big_head_walk/walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/zombie1_female_walk/Walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/zombie2_female_walk/Walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/zombie3_male_walk/walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/zombie4_male_walk/Walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/cop_walk/walk_%03d.png", 9),
            createFramePaths("assets/sprites/zombies/army_walk/walk_%03d.png", 9)
    };
    private static final String[] TANK_PATHS = createFramePaths("assets/sprites/zombies/megaboss_walk/Walk_%03d.png", 8);
    private static final String[] BOSS_WALK_PATHS = createFramePaths("assets/sprites/zombies/final_boss_walk/Walk_%03d.png", 8);
    private static final String[] BOSS_ATTACK_PATHS = createFramePaths("assets/sprites/zombies/final_boss_attack/Attack4_%03d.png", 9);

    private final Kind kind;
    private final int maxHealth;
    private int health;
    private final float baseSpeed;
    private final int contactDamage;
    private final boolean boss;
    private final String[] walkSpritePaths;
    private int attackCooldownFrames = 0;
    private int fireCooldownFrames = 100;
    private int rushCooldownFrames = 180;
    private int rushFrames = 0;
    private int animationTick;
    private int attackAnimationFrames;
    private String currentSpritePath;

    // Crea un zombie de un tipo concreto.
    public EngineZombie(EngineGameWorld world, Kind kind, Vec2 position) {
        super(world);
        // Esto hace que los zombies no se empujen entre ellos ni tapen pasillos.
        new GhostlyFixture(this, createShape(kind));
        this.kind = kind;
        this.maxHealth = maxHealthFor(kind);
        this.health = maxHealth;
        this.baseSpeed = speedFor(kind);
        this.contactDamage = damageFor(kind);
        this.boss = kind == Kind.BOSS;
        this.walkSpritePaths = spritePathsFor(kind);

        setGravityScale(0f);
        setFillColor(new Color(0, 0, 0, 0));
        setLineColor(new Color(0, 0, 0, 0));
        setPosition(position);
        setAngularVelocity(0f);
        refreshImage();
    }

    public void update(EngineGameWorld world, EnginePlayer player) {
        // Logica basica del zombie: seguir al jugador y atacar si esta cerca.
        if (attackCooldownFrames > 0) {
            attackCooldownFrames--;
        }
        if (boss) {
            if (fireCooldownFrames > 0) {
                fireCooldownFrames--;
            }
            if (rushCooldownFrames > 0) {
                rushCooldownFrames--;
            }
            if (rushFrames > 0) {
                rushFrames--;
            }
        }

        Vec2 playerPos = player.getPosition();
        Vec2 myPos = getPosition();
        float dx = playerPos.x - myPos.x;
        float dy = playerPos.y - myPos.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001f) {
            setLinearVelocity(new Vec2(0f, 0f));
            return;
        }

        dx /= length;
        dy /= length;
        float effectiveSpeed = getEffectiveSpeed();
        Vec2 desiredVelocity = world.resolveZombieVelocity(this, dx, dy, effectiveSpeed);
        setLinearVelocity(desiredVelocity);
        setAngularVelocity(0f);
        setAngle((float) (Math.atan2(dy, dx) + Math.PI / 2.0));
        animationTick++;

        if (boss && fireCooldownFrames == 0) {
            fireCooldownFrames = health < maxHealth / 2 ? 45 : 80;
            if (rushCooldownFrames <= 0) {
                rushFrames = 42;
                rushCooldownFrames = 180;
            }
            attackAnimationFrames = Math.max(attackAnimationFrames, 18);
            Vec2 velocity = new Vec2(dx * 7.5f, dy * 7.5f);
            world.spawnProjectile(new EngineProjectile(world, new Vec2(myPos.x + dx * 2.1f, myPos.y + dy * 2.1f),
                    velocity, 14, "boss_fire", false));
        }

        if (length <= getAttackRange() && canAttack()) {
            player.takeDamage(contactDamage);
            attackCooldownFrames = boss ? 34 : 36;
            attackAnimationFrames = Math.max(attackAnimationFrames, boss ? 18 : 10);
        }

        if (attackAnimationFrames > 0) {
            attackAnimationFrames--;
        }

        refreshImage();
    }

    public boolean canAttack() {
        return attackCooldownFrames == 0;
    }

    // Resta vida al zombie.
    public void takeDamage(int amount) {
        health -= amount;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isBoss() {
        return boss;
    }

    public void stopMotion() {
        // Se usa al pausar o cambiar de nivel para que no sigan moviendose.
        setLinearVelocity(new Vec2(0f, 0f));
        setAngularVelocity(0f);
    }

    private float getEffectiveSpeed() {
        // El jefe cambia de velocidad segun su fase.
        if (!boss) {
            return baseSpeed;
        }
        float phaseMultiplier = health < maxHealth / 2 ? 1.2f : 1f;
        float rushMultiplier = rushFrames > 0 ? 1.6f : 1f;
        return baseSpeed * phaseMultiplier * rushMultiplier;
    }

    private float getAttackRange() {
        // Cada tipo de zombie pega a una distancia distinta.
        switch (kind) {
            case FAST:
                return 0.95f;
            case TANK:
                return 1.45f;
            case BOSS:
                return 1.9f;
            default:
                return 1.12f;
        }
    }

    public float getCollisionHalfWidth() {
        switch (kind) {
            case FAST:
                return 0.24f;
            case TANK:
                return 0.42f;
            case BOSS:
                return 0.72f;
            default:
                return 0.28f;
        }
    }

    public float getCollisionHalfHeight() {
        return getCollisionHalfWidth();
    }

    private static BoxShape createShape(Kind kind) {
        switch (kind) {
            case FAST:
                return new BoxShape(0.24f, 0.24f);
            case TANK:
                return new BoxShape(0.42f, 0.42f);
            case BOSS:
                return new BoxShape(0.72f, 0.72f);
            default:
                return new BoxShape(0.28f, 0.28f);
        }
    }

    private static int maxHealthFor(Kind kind) {
        switch (kind) {
            case FAST:
                return 28;
            case TANK:
                return 160;
            case BOSS:
                return 680;
            default:
                return 45;
        }
    }

    private static float speedFor(Kind kind) {
        switch (kind) {
            case FAST:
                return 6.8f;
            case TANK:
                return 3.1f;
            case BOSS:
                return 4.5f;
            default:
                return 5.2f;
        }
    }

    private static int damageFor(Kind kind) {
        switch (kind) {
            case FAST:
                return 5;
            case TANK:
                return 10;
            case BOSS:
                return 18;
            default:
                return 6;
        }
    }

    private static float imageHeightFor(Kind kind) {
        switch (kind) {
            case TANK:
                return 3.2f;
            case BOSS:
                return 6.0f;
            default:
                return 2.1f;
        }
    }

    private void refreshImage() {
        String nextSpritePath = getCurrentSpritePath();
        if (nextSpritePath == null || nextSpritePath.equals(currentSpritePath)) {
            return;
        }

        currentSpritePath = nextSpritePath;
        removeAllImages();
        addImage(SpriteLoader.loadBodyImage(currentSpritePath, imageHeightFor(kind)));
    }

    private String getCurrentSpritePath() {
        if (boss && attackAnimationFrames > 0) {
            return BOSS_ATTACK_PATHS[(animationTick / 4) % BOSS_ATTACK_PATHS.length];
        }
        return walkSpritePaths[(animationTick / 6) % walkSpritePaths.length];
    }

    private static String[] spritePathsFor(Kind kind) {
        switch (kind) {
            case TANK:
                return TANK_PATHS;
            case BOSS:
                return BOSS_WALK_PATHS;
            default:
                return STANDARD_VARIANT_PATHS[RANDOM.nextInt(STANDARD_VARIANT_PATHS.length)];
        }
    }

    public static void preloadAssets() {
        for (String[] variant : STANDARD_VARIANT_PATHS) {
            SpriteLoader.preloadBodyImagesAsync(2.1f, variant);
        }
        SpriteLoader.preloadBodyImagesAsync(3.2f, TANK_PATHS);
        SpriteLoader.preloadBodyImagesAsync(6.0f, BOSS_WALK_PATHS);
        SpriteLoader.preloadBodyImagesAsync(6.0f, BOSS_ATTACK_PATHS);
    }

    private static String[] createFramePaths(String format, int frameCount) {
        String[] paths = new String[frameCount];
        for (int i = 0; i < frameCount; i++) {
            paths[i] = String.format(format, i);
        }
        return paths;
    }
}
