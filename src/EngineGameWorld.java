import city.cs.engine.Body;
import city.cs.engine.BoxShape;
import city.cs.engine.StaticBody;
import city.cs.engine.StepEvent;
import city.cs.engine.StepListener;
import org.jbox2d.common.Vec2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class EngineGameWorld extends city.cs.engine.World implements StepListener {
    public static final int VIEW_WIDTH = 1180;
    public static final int VIEW_HEIGHT = 820;
    public static final float WORLD_WIDTH = 40f;
    public static final float WORLD_HEIGHT = 27.8f;

    private static final String MENU_MUSIC = "miedo.wav";
    private static final String GAME_MUSIC = "Terror.wav";
    private static final String BOSS_MUSIC = "final_boss.wav";
    private static final String AMBIENT_LOOP = "susurros.wav";
    private static final String ZOMBIE_GROAN_SOUND = "zombie_grito_1.wav";
    private static final String MENU_BACKGROUND_PATH = "assets/menu_inicio.png";

    private final InputState input;
    private final GameLevels.LevelData[] levels;
    private final Queue<GameLevels.SpawnData> pendingSpawns = new LinkedList<>();
    private final List<EngineZombie> zombies = new ArrayList<>();
    private final List<EngineProjectile> projectiles = new ArrayList<>();
    private final List<EnginePickup> pickups = new ArrayList<>();
    private final List<Body> levelBodies = new ArrayList<>();
    private final List<Body> destroyQueue = new ArrayList<>();
    private final List<GameLevels.RectData> activeWallRects = new ArrayList<>();

    private EnginePlayer player;
    private EngineExit exit;
    private GameState gameState = GameState.MENU;
    private int currentLevelIndex;
    private int currentWave;
    private int spawnCooldown;
    private boolean exitActive;
    private boolean bossMusicPlaying;
    private boolean levelTransitioning;
    private int transitionFrames;
    private int nextLevelIndex = -1;
    private int announcementFrames;
    private String announcementTitle = "";
    private String announcementSubtitle = "";
    private boolean assetsPreloaded;

    public EngineGameWorld(InputState input) {
        super(60);
        this.input = input;
        this.levels = GameLevels.buildLevels();
        setGravity(0f);
        addStepListener(this);
        startNewGame();
    }

    @Override
    public void preStep(StepEvent stepEvent) {
        if (player == null) {
            return;
        }

        if (announcementFrames > 0) {
            announcementFrames--;
        }

        if (levelTransitioning) {
            player.stopMotion();
            transitionFrames--;
            if (transitionFrames <= 0) {
                levelTransitioning = false;
                if (nextLevelIndex >= levels.length) {
                    gameState = GameState.VICTORY;
                    nextLevelIndex = -1;
                } else {
                    currentLevelIndex = nextLevelIndex;
                    nextLevelIndex = -1;
                    loadLevel(currentLevelIndex, false);
                }
            }
            return;
        }

        if (gameState == GameState.MENU) {
            player.stopMotion();
            setMenuEntitiesVisible(false);
            if (input.consumeEnterPressed()) {
                gameState = GameState.PLAYING;
                setMenuEntitiesVisible(true);
                playGameplayAudio();
                showAnnouncement(getCurrentLevel().title, "Wave 1");
            }
            return;
        }

        if (gameState == GameState.PAUSED) {
            player.stopMotion();
            if (input.consumeEscapePressed()) {
                input.resetForStateChange();
                gameState = GameState.PLAYING;
            }
            return;
        }

        if (gameState == GameState.GAME_OVER || gameState == GameState.VICTORY) {
            player.stopMotion();
            if (input.consumeRestartPressed()) {
                startNewGame();
            }
            return;
        }

        if (input.consumeEscapePressed()) {
            player.stopMotion();
            input.resetForStateChange();
            gameState = GameState.PAUSED;
            return;
        }

        player.update(input);

        if (input.consumeReloadPressed()) {
            player.startReload();
        }

        if (input.firing) {
            for (EngineProjectile projectile : player.tryShoot(this)) {
                spawnProjectile(projectile);
            }
        }

        updateSpawning();
        updateZombies();
        updateProjectiles();
        updateWaveProgress();

        if (player.getHealth() <= 0) {
            SoundManager.stopAllLoops();
            gameState = GameState.GAME_OVER;
            input.firing = false;
        }
    }

    @Override
    public void postStep(StepEvent stepEvent) {
        if (destroyQueue.isEmpty()) {
            return;
        }
        for (Body body : new ArrayList<>(destroyQueue)) {
            body.destroy();
        }
        destroyQueue.clear();
    }

    public void handleProjectileCollision(EngineProjectile projectile, Body other) {
        if (other == null || !projectile.isAlive()) {
            return;
        }
        if (other instanceof EngineProjectile) {
            return;
        }

        if (projectile.isFromPlayer()) {
            if (other instanceof EngineZombie) {
                EngineZombie zombie = (EngineZombie) other;
                zombie.takeDamage(projectile.getDamage());
                SoundManager.play(getImpactSoundForCurrentWeapon(), 50);
                if (!zombie.isAlive()) {
                    queueDestroy(zombie);
                    zombies.remove(zombie);
                }
                destroyProjectile(projectile);
                return;
            }

            if (other instanceof EnginePlayer || other instanceof EnginePickup || other instanceof EngineExit) {
                return;
            }

            destroyProjectile(projectile);
            return;
        }

        if (other instanceof EnginePlayer) {
            ((EnginePlayer) other).takeDamage(projectile.getDamage());
            destroyProjectile(projectile);
            return;
        }

        if (other instanceof EngineZombie || other instanceof EnginePickup || other instanceof EngineExit) {
            return;
        }

        destroyProjectile(projectile);
    }

    public void collectPickup(EnginePickup pickup, EnginePlayer player) {
        if (pickup.isCollected()) {
            return;
        }

        if (pickup.getKind() == EnginePickup.Kind.AMMO) {
            player.addAmmo(pickup.getAmount());
        } else {
            player.heal(pickup.getAmount());
        }

        pickup.markCollected();
        pickups.remove(pickup);
        queueDestroy(pickup);
    }

    public void onExitReached() {
        if (!exitActive || levelTransitioning || gameState != GameState.PLAYING) {
            return;
        }
        levelTransitioning = true;
        transitionFrames = 55;
        nextLevelIndex = currentLevelIndex + 1;
        input.firing = false;
    }

    public void spawnProjectile(EngineProjectile projectile) {
        projectiles.add(projectile);
        levelBodies.add(projectile);
    }

    public EnginePlayer getPlayer() {
        return player;
    }

    public EngineZombie getActiveBoss() {
        for (EngineZombie zombie : zombies) {
            if (zombie.isBoss() && zombie.isAlive()) {
                return zombie;
            }
        }
        return null;
    }

    public GameState getGameState() {
        return gameState;
    }

    public int getCurrentLevelIndex() {
        return currentLevelIndex;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalWaves() {
        return getCurrentLevel().waves.size();
    }

    public boolean isExitActive() {
        return exitActive;
    }

    public EngineExit getExit() {
        return exit;
    }

    public int getAnnouncementFrames() {
        return announcementFrames;
    }

    public String getAnnouncementTitle() {
        return announcementTitle;
    }

    public String getAnnouncementSubtitle() {
        return announcementSubtitle;
    }

    public String getCurrentBackgroundPath() {
        return getCurrentLevel().backgroundPath;
    }

    public List<GameLevels.DecorationData> getCurrentDecorations() {
        return getCurrentLevel().decorations;
    }

    public List<EngineZombie> getZombies() {
        return zombies;
    }

    private void startNewGame() {
        SoundManager.stopAllLoops();
        SoundManager.playBackgroundMusic(MENU_MUSIC);
        SoundManager.warmUp(GAME_MUSIC, MENU_MUSIC, BOSS_MUSIC, AMBIENT_LOOP, "pistol.wav", "rifle.wav",
                "shotgun.wav", "pistol_reload.wav", "rifle_reload.wav", "recarga_escopeta.wav");
        preloadVisualAssets();

        if (player == null) {
            player = new EnginePlayer(this, levels[0].playerSpawn);
        }
        player.healFull();
        player.resetForNewGame();
        setMenuEntitiesVisible(false);

        currentLevelIndex = 0;
        levelTransitioning = false;
        transitionFrames = 0;
        nextLevelIndex = -1;
        announcementFrames = 0;
        gameState = GameState.MENU;
        loadLevel(0, true);
        input.resetForStateChange();
    }

    private void loadLevel(int index, boolean fullReset) {
        clearCurrentLevelBodies();

        GameLevels.LevelData level = levels[index];
        currentWave = 1;
        exitActive = false;
        spawnCooldown = 0;
        bossMusicPlaying = false;
        input.resetForStateChange();
        activeWallRects.clear();

        for (GameLevels.RectData wall : level.walls) {
            StaticBody wallBody = new StaticBody(this, new BoxShape(wall.halfWidth, wall.halfHeight));
            wallBody.setPosition(wall.position);
            wallBody.setFillColor(new Color(0, 0, 0, 0));
            wallBody.setLineColor(new Color(0, 0, 0, 0));
            wallBody.setName("wall");
            levelBodies.add(wallBody);
            activeWallRects.add(wall);
        }

        for (GameLevels.PickupData pickup : level.pickups) {
            EnginePickup pickupBody = new EnginePickup(this, pickup.kind, pickup.position, pickup.amount);
            pickups.add(pickupBody);
            levelBodies.add(pickupBody);
        }

        exit = new EngineExit(this, level.exitPosition, level.exitHalfWidth, level.exitHalfHeight);
        levelBodies.add(exit);

        player.setPosition(level.playerSpawn);
        player.stopMotion();
        if (fullReset) {
            player.healFull();
        }
        player.setWeapon(level.createWeapon());
        if (gameState == GameState.PLAYING) {
            playGameplayAudio();
        }

        queueWave(level.waves.get(0));
        showAnnouncement(level.title, "Wave 1");
    }

    private void clearCurrentLevelBodies() {
        for (Body body : new ArrayList<>(levelBodies)) {
            body.destroy();
        }
        levelBodies.clear();
        zombies.clear();
        pickups.clear();
        projectiles.clear();
        pendingSpawns.clear();
        activeWallRects.clear();
    }

    private void updateSpawning() {
        if (pendingSpawns.isEmpty()) {
            return;
        }

        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }

        GameLevels.SpawnData spawn = pendingSpawns.poll();
        EngineZombie zombie = new EngineZombie(this, spawn.kind, clampSpawnPosition(spawn.kind, spawn.position));
        zombies.add(zombie);
        levelBodies.add(zombie);
        spawnCooldown = 18;
    }

    private void updateZombies() {
        if (!zombies.isEmpty()) {
            SoundManager.play(ZOMBIE_GROAN_SOUND, 2500);
        }

        for (EngineZombie zombie : new ArrayList<>(zombies)) {
            zombie.update(this, player);
            if (!zombie.isAlive()) {
                queueDestroy(zombie);
                zombies.remove(zombie);
            }
        }

        if (!bossMusicPlaying && getActiveBoss() != null) {
            SoundManager.stopAllLoops();
            SoundManager.playBackgroundMusic(BOSS_MUSIC);
            bossMusicPlaying = true;
            showAnnouncement("FINAL BOSS", "Infernal Abomination");
        }
    }

    private void updateProjectiles() {
        for (EngineProjectile projectile : new ArrayList<>(projectiles)) {
            Vec2 position = projectile.getPosition();
            if (!projectile.isAlive()
                    || projectile.tickLifetime()
                    || position.x < -1f || position.x > WORLD_WIDTH + 1f
                    || position.y < -1f || position.y > WORLD_HEIGHT + 1f) {
                destroyProjectile(projectile);
            }
        }
    }

    private void updateWaveProgress() {
        if (!zombies.isEmpty() || !pendingSpawns.isEmpty()) {
            return;
        }

        GameLevels.LevelData level = getCurrentLevel();
        if (currentWave < level.waves.size()) {
            currentWave++;
            queueWave(level.waves.get(currentWave - 1));
            showAnnouncement(getWaveTitle(), "Stay alive");
            return;
        }

        exitActive = true;
    }

    private void queueWave(List<GameLevels.SpawnData> wave) {
        pendingSpawns.clear();
        pendingSpawns.addAll(wave);
        spawnCooldown = 0;
    }

    private void destroyProjectile(EngineProjectile projectile) {
        projectiles.remove(projectile);
        if (projectile.isAlive()) {
            projectile.markDestroyed();
        }
        queueDestroy(projectile);
    }

    private void queueDestroy(Body body) {
        if (!destroyQueue.contains(body)) {
            destroyQueue.add(body);
        }
    }

    private String getWaveTitle() {
        if (currentLevelIndex == 2 && currentWave == getCurrentLevel().waves.size()) {
            return "Final Wave";
        }
        return "Wave " + currentWave;
    }

    private String getImpactSoundForCurrentWeapon() {
        if (player.getWeapon().getPellets() > 1 || "Flamethrower".equalsIgnoreCase(player.getWeapon().getName())) {
            return "escopeta zombie hit .wav";
        }
        return "pistola zombie .wav";
    }

    private void showAnnouncement(String title, String subtitle) {
        announcementTitle = title;
        announcementSubtitle = subtitle;
        announcementFrames = 120;
    }

    private void setMenuEntitiesVisible(boolean visible) {
        player.setVisible(visible);
        for (EnginePickup pickup : pickups) {
            pickup.setVisible(visible);
        }
    }

    public Vec2 resolveZombieVelocity(EngineZombie zombie, float dx, float dy, float speed) {
        Vec2 currentPosition = zombie.getPosition();
        float moveX = dx * speed;
        float moveY = dy * speed;
        float halfWidth = zombie.getCollisionHalfWidth();
        float halfHeight = zombie.getCollisionHalfHeight();

        if (!collidesAt(currentPosition.x + moveX, currentPosition.y + moveY,
                halfWidth, halfHeight)) {
            return new Vec2(moveX, moveY);
        }

        float[] steerAngles = {22f, -22f, 45f, -45f, 68f, -68f, 90f, -90f};
        for (float steerAngle : steerAngles) {
            Vec2 steeredVelocity = steeredVelocity(dx, dy, speed, steerAngle);
            if (!collidesAt(currentPosition.x + steeredVelocity.x, currentPosition.y + steeredVelocity.y,
                    halfWidth, halfHeight)) {
                return steeredVelocity;
            }
        }

        boolean preferHorizontal = Math.abs(moveX) >= Math.abs(moveY);
        if (preferHorizontal) {
            if (!collidesAt(currentPosition.x + moveX, currentPosition.y, halfWidth, halfHeight)) {
                return new Vec2(moveX, 0f);
            }
            if (!collidesAt(currentPosition.x, currentPosition.y + moveY, halfWidth, halfHeight)) {
                return new Vec2(0f, moveY);
            }
        } else {
            if (!collidesAt(currentPosition.x, currentPosition.y + moveY, halfWidth, halfHeight)) {
                return new Vec2(0f, moveY);
            }
            if (!collidesAt(currentPosition.x + moveX, currentPosition.y, halfWidth, halfHeight)) {
                return new Vec2(moveX, 0f);
            }
        }

        float leftX = -dy * speed * 0.7f;
        float leftY = dx * speed * 0.7f;
        if (!collidesAt(currentPosition.x + leftX, currentPosition.y + leftY, halfWidth, halfHeight)) {
            return new Vec2(leftX, leftY);
        }

        float rightX = dy * speed * 0.7f;
        float rightY = -dx * speed * 0.7f;
        if (!collidesAt(currentPosition.x + rightX, currentPosition.y + rightY, halfWidth, halfHeight)) {
            return new Vec2(rightX, rightY);
        }

        return new Vec2(0f, 0f);
    }

    private Vec2 steeredVelocity(float dx, float dy, float speed, float angleDegrees) {
        float radians = (float) Math.toRadians(angleDegrees);
        float steeredX = (float) (dx * Math.cos(radians) - dy * Math.sin(radians));
        float steeredY = (float) (dx * Math.sin(radians) + dy * Math.cos(radians));
        return new Vec2(steeredX * speed, steeredY * speed);
    }

    private boolean collidesAt(float centerX, float centerY, float halfWidth, float halfHeight) {
        for (GameLevels.RectData wall : activeWallRects) {
            boolean overlapsX = Math.abs(centerX - wall.position.x) < (halfWidth + wall.halfWidth);
            boolean overlapsY = Math.abs(centerY - wall.position.y) < (halfHeight + wall.halfHeight);
            if (overlapsX && overlapsY) {
                return true;
            }
        }
        return centerX - halfWidth < 0f
                || centerX + halfWidth > WORLD_WIDTH
                || centerY - halfHeight < 0f
                || centerY + halfHeight > WORLD_HEIGHT;
    }

    private Vec2 clampSpawnPosition(EngineZombie.Kind kind, Vec2 position) {
        float halfWidth = collisionHalfWidthFor(kind);
        float halfHeight = collisionHalfHeightFor(kind);
        float clampedX = Math.max(halfWidth, Math.min(WORLD_WIDTH - halfWidth, position.x));
        float clampedY = Math.max(halfHeight, Math.min(WORLD_HEIGHT - halfHeight, position.y));
        return new Vec2(clampedX, clampedY);
    }

    private float collisionHalfWidthFor(EngineZombie.Kind kind) {
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

    private float collisionHalfHeightFor(EngineZombie.Kind kind) {
        return collisionHalfWidthFor(kind);
    }

    private void playGameplayAudio() {
        SoundManager.stopAllLoops();
        SoundManager.playBackgroundMusic(GAME_MUSIC);
        SoundManager.playAmbientLoop(AMBIENT_LOOP);
    }

    private void preloadVisualAssets() {
        if (assetsPreloaded) {
            return;
        }
        assetsPreloaded = true;

        List<String> imagePaths = new ArrayList<>();
        imagePaths.add(MENU_BACKGROUND_PATH);
        for (GameLevels.LevelData level : levels) {
            imagePaths.add(level.backgroundPath);
            for (GameLevels.DecorationData decoration : level.decorations) {
                imagePaths.add(decoration.imagePath);
            }
        }

        SpriteLoader.preloadImagesAsync(imagePaths.toArray(new String[0]));
        EnginePlayer.preloadAssets();
        EngineZombie.preloadAssets();
        EnginePickup.preloadAssets();
    }

    private GameLevels.LevelData getCurrentLevel() {
        return levels[currentLevelIndex];
    }
}
