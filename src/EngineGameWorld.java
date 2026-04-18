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
    // Musica del jefe final. Tiene que parar cuando el jefe muere.
    private static final String BOSS_MUSIC = "final_boss.wav";
    private static final String BOSS_SPAWN_SOUND = "jefe_final.wav";
    private static final String AMBIENT_LOOP = "susurros.wav";
    private static final String ZOMBIE_GROAN_SOUND = "zombie_grito_1.wav";
    private static final String MENU_BACKGROUND_PATH = "assets/menu_inicio.png";

    private final InputState input;
    private final GameLevels.LevelData[] levels;
    private final Queue<GameLevels.SpawnData> pendingSpawns = new LinkedList<>();
    private final List<EngineZombie> zombies = new ArrayList<>();
    private final List<EngineProjectile> projectiles = new ArrayList<>();
    private final List<EnginePickup> pickups = new ArrayList<>();
    private final List<BloodEffect> bloodEffects = new ArrayList<>();
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
    private int nextLevelIndex = -1;
    private int announcementFrames;
    private String announcementTitle = "";
    private String announcementSubtitle = "";
    private boolean assetsPreloaded;

    // Crea el mundo del juego y prepara el primer estado.
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
        // Bucle principal del juego. Aqui se controla casi todo en cada frame.
        if (player == null) {
            return;
        }

        if (announcementFrames > 0) {
            announcementFrames--;
        }

        if (levelTransitioning) {
            freezeDynamicBodies();
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
            freezeDynamicBodies();
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
            freezeDynamicBodies();
            player.stopMotion();
            if (input.consumeEscapePressed()) {
                input.resetForStateChange();
                gameState = GameState.PLAYING;
            }
            return;
        }

        if (gameState == GameState.GAME_OVER || gameState == GameState.VICTORY) {
            freezeDynamicBodies();
            player.stopMotion();
            if (input.consumeRestartPressed()) {
                startNewGame();
            }
            return;
        }

        if (input.consumeEscapePressed()) {
            freezeDynamicBodies();
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
        updateBloodEffects();
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
        for (int i = 0; i < destroyQueue.size(); i++) {
            destroyQueue.get(i).destroy();
        }
        destroyQueue.clear();
    }

    // Decide que pasa cuando una bala toca algo.
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
                    markZombieDead(zombie);
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

    // Aplica el efecto de un pickup cuando el jugador lo toca.
    public void collectPickup(EnginePickup pickup, EnginePlayer player) {
        if (pickup.isCollected()) {
            return;
        }

        if (pickup.getKind() == EnginePickup.Kind.AMMO) {
            player.refillAmmo();
        } else {
            player.healFull();
        }

        pickup.markCollected();
        pickups.remove(pickup);
        queueDestroy(pickup);
    }

    // Se llama cuando el jugador entra en el portal.
    public void onExitReached() {
        if (!exitActive || levelTransitioning || gameState != GameState.PLAYING) {
            return;
        }
        levelTransitioning = true;
        transitionFrames = 55;
        nextLevelIndex = currentLevelIndex + 1;
        input.firing = false;
    }

    // Mete una bala nueva en el mundo y en la lista de control.
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

    public List<BloodEffect> getBloodEffects() {
        return bloodEffects;
    }

    private void startNewGame() {
        // Reinicia musica, jugador y nivel inicial.
        SoundManager.stopAllLoops();
        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        SoundManager.playBackgroundMusic(MENU_MUSIC);
        SoundManager.warmUp(GAME_MUSIC, MENU_MUSIC, BOSS_MUSIC, AMBIENT_LOOP, "pistol.wav", "rifle.wav",
                "shotgun.wav", "pistol_reload.wav", "rifle_reload.wav", "recarga_escopeta.wav",
                "player_pain.wav", "pistola zombie .wav", "escopeta zombie hit .wav", ZOMBIE_GROAN_SOUND, BOSS_SPAWN_SOUND);
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
        spawnCooldown = 0;
        bossMusicPlaying = false;
        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
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
                // Solo los obstaculos que se ven deben frenar a los zombies.
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
        // Borra el nivel anterior antes de crear el nuevo.
        for (Body body : new ArrayList<>(levelBodies)) {
            body.destroy();
        }
        levelBodies.clear();
        zombies.clear();
        pickups.clear();
        projectiles.clear();
        bloodEffects.clear();
        pendingSpawns.clear();
        activeWallRects.clear();
    }

    private void updateSpawning() {
        // Saca zombies poco a poco para que no salgan todos a la vez.
        if (pendingSpawns.isEmpty()) {
            return;
        }

        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }

        GameLevels.SpawnData spawn = pendingSpawns.poll();
        // Los zombies entran por los bordes para que no aparezcan de golpe en mitad del mapa.
        EngineZombie zombie = new EngineZombie(this, spawn.kind, spawnPositionAtEdge(spawn.kind, spawn.position));
        zombies.add(zombie);
        levelBodies.add(zombie);
        spawnCooldown = 18;

        if (spawn.kind == EngineZombie.Kind.BOSS && !bossMusicPlaying) {
            SoundManager.stopAllLoops();
            SoundManager.playBackgroundMusic(BOSS_MUSIC);
            SoundManager.play(BOSS_SPAWN_SOUND, 0);
            bossMusicPlaying = true;
            showAnnouncement("FINAL BOSS",  "Infernal Abomination");
        }

    }
    private void updateZombies() {
        // Mueve zombies, activa sonidos y controla la musica del jefe.
        if (!zombies.isEmpty()) {
            SoundManager.play(ZOMBIE_GROAN_SOUND, 2500);
        } else {
            SoundManager.stopSound(ZOMBIE_GROAN_SOUND);
        }

        for (int i = zombies.size() - 1; i >= 0; i--) {
            EngineZombie zombie = zombies.get(i);
            zombie.update(this, player);
            if (!zombie.isAlive()) {
                markZombieDead(zombie);
            }
        }

        if (bossMusicPlaying && getActiveBoss() == null) {
            bossMusicPlaying = false;
        }
    }

    private void updateProjectiles() {
        // Revisa balas, golpes y cuando una bala debe borrarse.
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            EngineProjectile projectile = projectiles.get(i);
            // Como los zombies no bloquean fisicamente, el dano se calcula aqui a mano.
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
        // Comprueba dano sin depender solo de la colision fisica del motor.
        if (!projectile.isAlive()) {
            return false;
        }

        Vec2 projectilePosition = projectile.getPosition();
        float projectileRadius = 0.16f;

        if (projectile.isFromPlayer()) {
            for (int i = zombies.size() - 1; i >= 0; i--) {
                EngineZombie zombie = zombies.get(i);
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
                    markZombieDead(zombie);
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
        // Si ya no quedan zombies, pasa a la siguiente wave o activa el portal.
        if (!zombies.isEmpty() || !pendingSpawns.isEmpty()) {
            return;
        }

        SoundManager.stopSound(ZOMBIE_GROAN_SOUND);

        GameLevels.LevelData level = getCurrentLevel();
        if (currentWave < level.waves.size()) {
            currentWave++;
            queueWave(level.waves.get(currentWave - 1));
            showAnnouncement(getWaveTitle(), "Stay alive");
            return;
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
        // Guarda los spawns pendientes de la wave actual.
        pendingSpawns.clear();
        pendingSpawns.addAll(wave);
        spawnCooldown = 0;
    }

    private void destroyProjectile(EngineProjectile projectile) {
        // Borra una bala de forma segura.
        projectiles.remove(projectile);
        if (projectile.isAlive()) {
            projectile.markDestroyed();
        }
        queueDestroy(projectile);
    }

    private void queueDestroy(Body body) {
        // Se destruyen al final del frame para no romper la fisica.
        if (!destroyQueue.contains(body)) {
            destroyQueue.add(body);
        }
    }

    private void markZombieDead(EngineZombie zombie) {
        if (!zombies.remove(zombie)) {
            return;
        }
        bloodEffects.add(new BloodEffect(zombie.getPosition(), zombie.isBoss() ? 1.8f : zombie.getCollisionHalfWidth() * 3.4f));
        queueDestroy(zombie);
    }

    private String getWaveTitle() {
        if (currentLevelIndex == 2 && currentWave == getCurrentLevel().waves.size()) {
            return "Final Wave";
        }
        return "Wave " + currentWave;
    }

    private String getImpactSoundForCurrentWeapon() {
        // Devuelve el sonido de impacto segun el arma actual.
        if (player.getWeapon().getPellets() > 1 || "Shotgun".equalsIgnoreCase(player.getWeapon().getName())) {
            return "escopeta zombie hit .wav";
        }
        return "pistola zombie .wav";
    }

    private void showAnnouncement(String title, String subtitle) {
        // Muestra un texto grande unos segundos.
        announcementTitle = title;
        announcementSubtitle = subtitle;
        announcementFrames = 120;
    }

    private void setMenuEntitiesVisible(boolean visible) {
        // Oculta jugador y pickups cuando estamos en el menu.
        player.setVisible(visible);
        for (EnginePickup pickup : pickups) {
            pickup.setVisible(visible);
        }
    }

    public Vec2 resolveZombieVelocity(EngineZombie zombie, float dx, float dy, float speed) {
        // Primero intenta ir recto. Si no puede, prueba a deslizarse y luego a rodear.
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
        // Limita el movimiento al mapa para que no se queden pillados en las esquinas.
        float targetX = clampToWorld(currentPosition.x + deltaX, halfWidth, WORLD_WIDTH);
        float targetY = clampToWorld(currentPosition.y + deltaY, halfHeight, WORLD_HEIGHT);
        if (collidesAt(targetX, targetY, halfWidth, halfHeight)) {
            return null;
        }
        return new Vec2(targetX - currentPosition.x, targetY - currentPosition.y);
    }

    private Vec2 nudgeInsideWorld(Vec2 position, float halfWidth, float halfHeight, float speed) {
        // Ultimo ajuste por si el zombie se pego demasiado al borde.
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
        // Mira si el zombie pisaria un obstaculo visible.
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
        // Ajusta el spawn para que entre desde un borde sin salirse del mapa.
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
        // Vuelve a poner el sonido normal del nivel.
        SoundManager.stopAllLoops();
        SoundManager.playBackgroundMusic(GAME_MUSIC);
        SoundManager.playAmbientLoop(AMBIENT_LOOP);
    }

    private void freezeDynamicBodies() {
        // Al pausar hay que parar cuerpos que ya venian moviendose.
        for (EngineZombie zombie : zombies) {
            zombie.stopMotion();
        }
        for (EngineProjectile projectile : projectiles) {
            projectile.stopMotion();
        }
    }

    private void updateBloodEffects() {
        for (int i = bloodEffects.size() - 1; i >= 0; i--) {
            if (bloodEffects.get(i).tick()) {
                bloodEffects.remove(i);
            }
        }
    }

    private void preloadVisualAssets() {
        // Precarga imagenes para reducir tirones al empezar a jugar.
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

    public static final class BloodEffect {
        public final Vec2 position;
        public final float radius;
        public final int maxFrames;
        private int framesLeft;

        private BloodEffect(Vec2 position, float radius) {
            this.position = new Vec2(position.x, position.y);
            this.radius = radius;
            this.maxFrames = 220;
            this.framesLeft = maxFrames;
        }

        public boolean tick() {
            framesLeft--;
            return framesLeft <= 0;
        }

        public int getFramesLeft() {
            return framesLeft;
        }
    }
}
