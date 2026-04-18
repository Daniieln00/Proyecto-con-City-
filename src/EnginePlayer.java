import city.cs.engine.BoxShape;
import city.cs.engine.DynamicBody;
import org.jbox2d.common.Vec2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class EnginePlayer extends DynamicBody {
    private static final BoxShape SHAPE = new BoxShape(0.39f, 0.50f);
    private static final String PLAYER_HURT_SOUND = "player_pain.wav";
    private static final String[] PISTOL_WALK_PATHS = createFramePaths("assets/sprites/player/pistol_walk/Walk_gun_%03d.png", 6);
    private static final String[] PISTOL_SHOOT_PATHS = createFramePaths("assets/sprites/player/pistol_shoot/Gun_Shot_%03d.png", 5);
    private static final String[] RIFLE_WALK_PATHS = createFramePaths("assets/sprites/player/rifle_walk_v2/Walk_riffle_%03d.png", 6);
    private static final String[] RIFLE_SHOOT_PATHS = createFramePaths("assets/sprites/player/rifle_shoot/Riffle_%03d.png", 9);
    private static final String[] SHOTGUN_WALK_PATHS = createFramePaths("assets/sprites/player/flamethrower_walk/Walk_firethrower_%03d.png", 6);
    private static final String[] SHOTGUN_SHOOT_PATHS = createFramePaths("assets/sprites/player/flamethrower_shoot/FlameThrower_%03d.png", 9);

    private int maxHealth = 140;
    private int health = 140;
    private Weapon weapon = Weapon.pistol();
    private boolean reloading;
    private int reloadFramesLeft;
    private float aimX = 1f;
    private float aimY = 0f;
    private final float speed = 8f;
    private int animationTick;
    private int shotAnimationFrames;
    private int damageFeedbackFrames;
    private String currentSpritePath;
    private boolean visible = true;

    // Crea al jugador y coloca su imagen inicial.
    public EnginePlayer(EngineGameWorld world, Vec2 startPosition) {
        super(world, SHAPE);
        setGravityScale(0f);
        setFillColor(new Color(0, 0, 0, 0));
        setLineColor(new Color(0, 0, 0, 0));
        setPosition(startPosition);
        setAngularVelocity(0f);
        refreshImage();
    }

    public void update(InputState input) {
        // Aqui se actualiza el movimiento, el disparo y la recarga del jugador.
        float dx = 0f;
        float dy = 0f;

        if (input.up) {
            dy += 1f;
        }
        if (input.down) {
            dy -= 1f;
        }
        if (input.left) {
            dx -= 1f;
        }
        if (input.right) {
            dx += 1f;
        }

        Vec2 desiredVelocity = new Vec2(dx, dy);
        if (desiredVelocity.lengthSquared() > 0f) {
            desiredVelocity.normalize();
            desiredVelocity.mulLocal(speed);
            animationTick++;
        } else {
            animationTick = 0;
        }
        setLinearVelocity(desiredVelocity);

        setAimTowards(input.aimWorld);
        weapon.updateCooldown();

        if (reloading) {
            reloadFramesLeft--;
            if (reloadFramesLeft <= 0) {
                weapon.reloadMagazine();
                reloading = false;
                reloadFramesLeft = 0;
            }
        }

        if (shotAnimationFrames > 0) {
            shotAnimationFrames--;
        }

        if (damageFeedbackFrames > 0) {
            damageFeedbackFrames--;
        }

        refreshImage();
    }

    public void stopMotion() {
        setLinearVelocity(new Vec2(0f, 0f));
        setAngularVelocity(0f);
    }

    // Cambia la direccion a la que mira el jugador.
    public void setAimTowards(Vec2 target) {
        if (target == null) {
            return;
        }
        Vec2 position = getPosition();
        float dx = target.x - position.x;
        float dy = target.y - position.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001f) {
            return;
        }
        aimX = dx / length;
        aimY = dy / length;
        // El sprite original mira hacia abajo, por eso se gira 90 grados.
        setAngle((float) (Math.atan2(aimY, aimX) + Math.PI / 2.0));
    }

    public List<EngineProjectile> tryShoot(EngineGameWorld world) {
        // Crea una bala si el arma dispara una sola vez o varias si es escopeta.
        List<EngineProjectile> projectiles = new ArrayList<>();
        if (reloading || !weapon.canShoot()) {
            return projectiles;
        }

        if (!weapon.hasAmmoInMagazine()) {
            startReload();
            return projectiles;
        }

        weapon.markShot();
        weapon.useAmmo();
        SoundManager.play(weapon.getSoundFile());
        shotAnimationFrames = Math.max(shotAnimationFrames, weapon.getPellets() > 1 ? 7 : 5);

        Vec2 position = getPosition();
        Vec2 spawnPoint = new Vec2(position.x + aimX * 0.75f, position.y + aimY * 0.75f);

        if (weapon.getPellets() == 1) {
            projectiles.add(new EngineProjectile(world, spawnPoint, new Vec2(aimX * weapon.getBulletSpeed(), aimY * weapon.getBulletSpeed()),
                    weapon.getDamage(), weapon.getProjectileStyle(), true));
            return projectiles;
        }

        for (int i = 0; i < weapon.getPellets(); i++) {
            double offset = ((double) i / (weapon.getPellets() - 1) - 0.5) * weapon.getSpread();
            double angle = Math.atan2(aimY, aimX) + offset;
            float vx = (float) (Math.cos(angle) * weapon.getBulletSpeed());
            float vy = (float) (Math.sin(angle) * weapon.getBulletSpeed());
            projectiles.add(new EngineProjectile(world, spawnPoint, new Vec2(vx, vy),
                    weapon.getDamage(), weapon.getProjectileStyle(), true));
        }

        return projectiles;
    }

    public void takeDamage(int amount) {
        // Resta vida y activa el efecto visual de dano.
        health -= amount;
        if (health < 0) {
            health = 0;
        }
        damageFeedbackFrames = 16;
        SoundManager.play(PLAYER_HURT_SOUND, 160);
    }

    public void healFull() {
        health = maxHealth;
    }

    public void heal(int amount) {
        if (amount <= 0) {
            return;
        }
        health = Math.min(maxHealth, health + amount);
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getDamageFeedbackFrames() {
        return damageFeedbackFrames;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public void setWeapon(Weapon weapon) {
        // Cambia el arma y reinicia la recarga.
        this.weapon = weapon;
        this.reloading = false;
        this.reloadFramesLeft = 0;
        refreshImage();
    }

    public void startReload() {
        // Empieza la recarga si hace falta y hay municion en reserva.
        if (reloading || !weapon.canReload()) {
            return;
        }
        reloading = true;
        reloadFramesLeft = weapon.getReloadFrames();
        SoundManager.play(weapon.getReloadSoundFile());
    }

    public boolean isReloading() {
        return reloading;
    }

    public void refillAmmo() {
        weapon.refillReserveAmmo();
    }

    public void addAmmo(int amount) {
        weapon.addReserveAmmo(amount);
    }

    public void resetForNewGame() {
        reloading = false;
        reloadFramesLeft = 0;
        aimX = 1f;
        aimY = 0f;
        setAngle(0f);
        stopMotion();
        weapon.resetAmmo();
        animationTick = 0;
        shotAnimationFrames = 0;
        damageFeedbackFrames = 0;
        currentSpritePath = null;
        refreshImage();
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }

        this.visible = visible;
        if (!visible) {
            removeAllImages();
            return;
        }

        currentSpritePath = null;
        refreshImage();
    }

    private void refreshImage() {
        if (!visible) {
            return;
        }

        String nextSpritePath = getCurrentSpritePath();
        if (nextSpritePath == null || nextSpritePath.equals(currentSpritePath)) {
            return;
        }

        currentSpritePath = nextSpritePath;
        removeAllImages();
        addImage(SpriteLoader.loadBodyImage(currentSpritePath, getCurrentSpriteHeight()));
    }

    private String getCurrentSpritePath() {
        // Quieto usa la primera imagen. Al moverse o disparar usa animacion.
        if (shotAnimationFrames <= 0 && getLinearVelocity().lengthSquared() <= 0.01f) {
            return getDefaultSpritePath();
        }

        String[] frames = shotAnimationFrames > 0 ? getShootPathsForWeapon() : getWalkPathsForWeapon();
        int frameIndex = (animationTick / 6) % frames.length;
        return frames[frameIndex];
    }

    private String[] getWalkPathsForWeapon() {
        if ("Shotgun".equalsIgnoreCase(weapon.getName())) {
            return SHOTGUN_WALK_PATHS;
        }
        if ("Rifle".equalsIgnoreCase(weapon.getName())) {
            return RIFLE_WALK_PATHS;
        }
        return PISTOL_WALK_PATHS;
    }

    private String[] getShootPathsForWeapon() {
        if ("Shotgun".equalsIgnoreCase(weapon.getName())) {
            return SHOTGUN_SHOOT_PATHS;
        }
        if ("Rifle".equalsIgnoreCase(weapon.getName())) {
            return RIFLE_SHOOT_PATHS;
        }
        return PISTOL_SHOOT_PATHS;
    }

    private String getDefaultSpritePath() {
        return getWalkPathsForWeapon()[0];
    }

    private float getCurrentSpriteHeight() {
        if ("Rifle".equalsIgnoreCase(weapon.getName()) || "Shotgun".equalsIgnoreCase(weapon.getName())) {
            return 2.45f;
        }
        return 2.05f;
    }

    public static void preloadAssets() {
        SpriteLoader.preloadBodyImagesAsync(2.05f, concat(PISTOL_WALK_PATHS, PISTOL_SHOOT_PATHS));
        SpriteLoader.preloadBodyImagesAsync(2.45f, concat(RIFLE_WALK_PATHS, RIFLE_SHOOT_PATHS, SHOTGUN_WALK_PATHS, SHOTGUN_SHOOT_PATHS));
    }

    private static String[] createFramePaths(String format, int frameCount) {
        String[] paths = new String[frameCount];
        for (int i = 0; i < frameCount; i++) {
            paths[i] = String.format(format, i);
        }
        return paths;
    }

    private static String[] concat(String[]... arrays) {
        int length = 0;
        for (String[] array : arrays) {
            length += array.length;
        }

        String[] result = new String[length];
        int index = 0;
        for (String[] array : arrays) {
            for (String value : array) {
                result[index++] = value;
            }
        }
        return result;
    }
}
