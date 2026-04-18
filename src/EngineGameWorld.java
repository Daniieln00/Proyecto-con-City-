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
    // Final boss uses its own background track and should stop with the boss.
    private static final String BOSS_MUSIC = "jefe_final.wav";
    private static final String AMBIENT_LOOP = "susurros.wav";
    private static final String ZOMBIE_GROAN_SOUND = "zombie_grito_1.wav";
    private static final String RELAX_SOUND = "respirar.wav";
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
    private int exitDelayFrames;
    private boolean relaxSoundPlayed;
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
        // Main game loop: menu/pause transitions, player input, AI and wave progress.
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
            SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
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
        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        SoundManager.stopSound(RELAX_SOUND);
        SoundManager.playBackgroundMusic(MENU_MUSIC);
        SoundManager.warmUp(GAME_MUSIC, MENU_MUSIC, BOSS_MUSIC, AMBIENT_LOOP, "pistol.wav", "rifle.wav",
                "shotgun.wav", "pistol_reload.wav", "rifle_reload.wav", "recarga_escopeta.wav", ZOMBIE_GROAN_SOUND, RELAX_SOUND);
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
        exitDelayFrames = 0;
        nextLevelIndex = -1;
        announcementFrames = 0;
        gameState = GameState.MENU;
        loadLevel(0, true);
        input.resetForStateChange();
    }

    private void loadLevel(int index, boolean fullReset) {
        // Rebuild all level bodies every time we enter a new level.
        clearCurrentLevelBodies();

        GameLevels.LevelData level = levels[index];
        currentWave = 1;
        exitActive = false;
        exitDelayFrames = 0;
        relaxSoundPlayed = false;
        spawnCooldown = 0;
        bossMusicPlaying = false;
        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        SoundManager.stopSound(RELAX_SOUND);
        input.resetForStateChange();
        activeWallRects.clear();

        for (GameLevels.RectData wall : level.walls) {
            StaticBody wallBody = new StaticBody(this, new BoxShape(wall.halfWidth, wall.halfHeight));
            wallBody.setPosition(wall.position);
            wallBody.setFillColor(new Color(0, 0, 0, 0));
            wallBody.setLineColor(new Color(0, 0, 0, 0));
            wallBody.setName("wall");
            levelBodies.add(wallBody);
            if (isVisibleObstacle(wall)) {
                // Only visible props should affect zombie pathfinding.
                activeWallRects.add(wall);
            }
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
        // Enemies should enter from the screen edge instead of appearing in the middle.
        EngineZombie zombie = new EngineZombie(this, spawn.kind, spawnPositionAtEdge(spawn.kind, spawn.position));
        zombies.add(zombie);
        levelBodies.add(zombie);
        spawnCooldown = 18;
    }

    private void updateZombies() {
        if (!zombies.isEmpty()) {
            SoundManager.play(ZOMBIE_GROAN_SOUND, 2500);
        } else {
            SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        }

        for (EngineZombie zombie : new ArrayList<>(zombies)) {
            zombie.update(this, player);
            if (!zombie.isAlive()) {
                queueDestroy(zombie);
                zombies.remove(zombie);
            }
        }

        if (!bossMusicPlaying && getActiveBoss() != null) {
            SoundManager.play(ZOMBIE_GROAN_SOUND);
            SoundManager.stopAllLoops();
            SoundManager.playBackgroundMusic(BOSS_MUSIC);
            bossMusicPlaying = true;
            showAnnouncement("FINAL BOSS", "Infernal Abomination");
            return;
        }

        // If the boss is dead, switch back to the normal level mix immediately.
        if (bossMusicPlaying && getActiveBoss() == null) {
            bossMusicPlaying = false;
            SoundManager.stopAllLoops();
            if (!zombies.isEmpty() || !pendingSpawns.isEmpty()) {
                playGameplayAudio();
            }
        }
    }

    private void updateProjectiles() {
        for (EngineProjectile projectile : new ArrayList<>(projectiles)) {
            // Zombies use ghostly fixtures, so damage is resolved manually here.
            if (applyManualProjectileHit(projectile)) {
                continue;
            }
            Vec2 position = projectile.getPosition();
            if (!projectile.isAlive()
                    || projectile.tickLifetime()
                    || position.x < -1f || position.x > WORLD_WIDTH + 1f
                    || position.y < -1f || position.y > WORLD_HEIGHT + 1f) {
                destroyProjectile(projectile);
            }
        }
    }

    private boolean applyManualProjectileHit(EngineProjectile projectile) {
        if (!projectile.isAlive()) {
            return false;
        }

        Vec2 projectilePosition = projectile.getPosition();
        float projectileRadius = 0.16f;

        if (projectile.isFromPlayer()) {
            for (EngineZombie zombie : new ArrayList<>(zombies)) {
                if (!zombie.isAlive()) {
                    continue;
                }

                float hitRadius = projectileRadius + zombie.getCollisionHalfWidth();
                if (!isWithinHitRadius(projectilePosition, zombie.getPosition(), hitRadius)) {
                    continue;
                }

                zombie.takeDamage(projectile.getDamage());
                SoundManager.play(getImpactSoundForCurrentWeapon(), 50);
                if (!zombie.isAlive()) {
                    queueDestroy(zombie);
                    zombies.remove(zombie);
                }
                destroyProjectile(projectile);
                return true;
            }
            return false;
        }

        float playerHitRadius = projectileRadius + 0.60f;
        if (projectile.canHitPlayer()
                && isWithinHitRadius(projectilePosition, player.getPosition(), playerHitRadius)) {
            player.takeDamage(projectile.getDamage());
            projectile.markPlayerHit();
            return true;
        }
        return false;
    }

    private boolean isWithinHitRadius(Vec2 first, Vec2 second, float hitRadius) {
        float dx = first.x - second.x;
        float dy = first.y - second.y;
        return dx * dx + dy * dy <= hitRadius * hitRadius;
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

        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        if (!relaxSoundPlayed) {
            SoundManager.play(RELAX_SOUND, 0);
            relaxSoundPlayed = true;
        }
        if (exitDelayFrames == 0) {
            exitDelayFrames = 180;
            return;
        }

        exitDelayFrames--;
        if (exitDelayFrames <= 0) {
            exitActive = true;
        }
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
        if (player.getWeapon().getPellets() > 1 || "Shotgun".equalsIgnoreCase(player.getWeapon().getName())) {
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
        // Try the direct path first, then slide on one axis, then try side steps.
        Vec2 currentPosition = zombie.getPosition();
        float moveX = dx * speed;
        float moveY = dy * speed;
        float halfWidth = zombie.getCollisionHalfWidth();
        float halfHeight = zombie.getCollisionHalfHeight();
        Vec2 directVelocity = velocityTowards(currentPosition, moveX, moveY, halfWidth, halfHeight);
        if (directVelocity != null) {
            return directVelocity;
        }

        Vec2 horizontalVelocity = velocityTowards(currentPosition, moveX, 0f, halfWidth, halfHeight);
        if (horizontalVelocity != null) {
            return horizontalVelocity;
        }

        Vec2 verticalVelocity = velocityTowards(currentPosition, 0f, moveY, halfWidth, halfHeight);
        if (verticalVelocity != null) {
            return verticalVelocity;
        }

        Vec2 leftSlide = velocityTowards(currentPosition, -dy * speed * 0.7f, dx * speed * 0.7f, halfWidth, halfHeight);
        if (leftSlide != null) {
            return leftSlide;
        }

        Vec2 rightSlide = velocityTowards(currentPosition, dy * speed * 0.7f, -dx * speed * 0.7f, halfWidth, halfHeight);
        if (rightSlide != null) {
            return rightSlide;
        }

        return nudgeInsideWorld(currentPosition, halfWidth, halfHeight, speed);
    }

    private Vec2 velocityTowards(Vec2 currentPosition, float deltaX, float deltaY, float halfWidth, float halfHeight) {
        // Clamp the candidate move to the playable area so corners do not freeze enemies.
        float targetX = clampToWorld(currentPosition.x + deltaX, halfWidth, WORLD_WIDTH);
        float targetY = clampToWorld(currentPosition.y + deltaY, halfHeight, WORLD_HEIGHT);
        if (collidesAt(targetX, targetY, halfWidth, halfHeight)) {
            return null;
        }
        return new Vec2(targetX - currentPosition.x, targetY - currentPosition.y);
    }

    private Vec2 nudgeInsideWorld(Vec2 position, float halfWidth, float halfHeight, float speed) {
        // Final fallback when the zombie has drifted too close to the world boundary.
        float nudgedX = clampToWorld(position.x, halfWidth, WORLD_WIDTH);
        float nudgedY = clampToWorld(position.y, halfHeight, WORLD_HEIGHT);
        Vec2 nudge = new Vec2(nudgedX - position.x, nudgedY - position.y);
        if (nudge.lengthSquared() == 0f) {
            return new Vec2(0f, 0f);
        }
        if (nudge.lengthSquared() > speed * speed) {
            nudge.normalize();
            nudge.mulLocal(speed);
        }
        return nudge;
    }

    private float clampToWorld(float value, float halfSize, float worldSize) {
        return Math.max(halfSize, Math.min(worldSize - halfSize, value));
    }

    private boolean isVisibleObstacle(GameLevels.RectData wall) {
        return wall.position.x >= 0f
                && wall.position.x <= WORLD_WIDTH
                && wall.position.y >= 0f
                && wall.position.y <= WORLD_HEIGHT;
    }

    private boolean collidesAt(float centerX, float centerY, float halfWidth, float halfHeight) {
        for (GameLevels.RectData wall : activeWallRects) {
            boolean overlapsX = Math.abs(centerX - wall.position.x) < (halfWidth + wall.halfWidth);
            boolean overlapsY = Math.abs(centerY - wall.position.y) < (halfHeight + wall.halfHeight);
            if (overlapsX && overlapsY) {
                return true;
            }
        }
        return false;
    }

    private Vec2 spawnPositionAtEdge(EngineZombie.Kind kind, Vec2 position) {
        float halfWidth = collisionHalfWidthFor(kind);
        float halfHeight = collisionHalfHeightFor(kind);
        float edgePadding = 0.95f;
        float clampedX = Math.max(halfWidth + edgePadding, Math.min(WORLD_WIDTH - halfWidth - edgePadding, position.x));
        float clampedY = Math.max(halfHeight + edgePadding, Math.min(WORLD_HEIGHT - halfHeight - edgePadding, position.y));

        if (position.x <= 0f) {
            clampedX = halfWidth + edgePadding;
        } else if (position.x >= WORLD_WIDTH) {
            clampedX = WORLD_WIDTH - halfWidth - edgePadding;
        }

        if (position.y <= 0f) {
            clampedY = halfHeight + edgePadding;
        } else if (position.y >= WORLD_HEIGHT) {
            clampedY = WORLD_HEIGHT - halfHeight - edgePadding;
        }

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
