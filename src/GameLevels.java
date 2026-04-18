import org.jbox2d.common.Vec2;

import java.util.ArrayList;
import java.util.List;

public final class GameLevels {
    private GameLevels() {
    }

    public static LevelData[] buildLevels() {
        // All level content is defined here so world logic stays smaller.
        return new LevelData[] {
                buildLevel1(),
                buildLevel2(),
                buildLevel3()
        };
    }

    private static LevelData buildLevel1() {
        LevelData level = new LevelData("Green Fields",
                "assets/craftpix/maps/level1/field/campo_1.png", pixelCenterToWorld(100 + 18, 110 + 18), 0);

        level.walls.add(obstacleRectData(192, 150, 54, 42));
        level.walls.add(obstacleRectData(820, 186, 52, 40));
        level.walls.add(obstacleRectData(496, 330, 58, 48));
        level.walls.add(obstacleRectData(156, 528, 58, 46));
        addWorldBounds(level);
        addDecorations(level,
                decoration("assets/craftpix/maps/level1/glades/Stone1_ground_shadow.png", 202, 154, 42, 42),
                decoration("assets/craftpix/maps/level1/glades/Stone1_ground_shadow.png", 842, 188, 42, 42),
                decoration("assets/craftpix/maps/level1/glades/Ruin1_ground_shadow.png", 492, 328, 76, 76),
                decoration("assets/craftpix/maps/level1/glades/Stone1_ground_shadow.png", 162, 530, 46, 46),
                decoration("assets/craftpix/maps/level1/glades/Tree1.png", 944, 548, 74, 74),
                decoration("assets/craftpix/maps/level1/props/objects_outside_0006_Layer-7.png", 330, 110, 54, 46),
                decoration("assets/craftpix/maps/level1/props/objects_outside_0008_Layer-9.png", 636, 104, 42, 42),
                decoration("assets/craftpix/maps/level1/props/objects_outside_0005_Layer-6.png", 884, 456, 54, 54),
                decoration("assets/craftpix/maps/level1/props/objects_outside_0000_Layer-1.png", 102, 242, 34, 34)
        );

        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(286 + 11, 248 + 11), 18));
        level.pickups.add(new PickupData(EnginePickup.Kind.HEALTH, pixelCenterToWorld(620 + 11, 420 + 11), 25));
        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(766 + 11, 606 + 11), 12));

        RectData exitRect = rectData(930, 274, 62, 88);
        level.exitPosition = exitRect.position;
        level.exitHalfWidth = exitRect.halfWidth;
        level.exitHalfHeight = exitRect.halfHeight;

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, -60, 140),
                spawn(EngineZombie.Kind.BASIC, 1035, 100),
                spawn(EngineZombie.Kind.BASIC, 760, -60),
                spawn(EngineZombie.Kind.FAST, -60, 560),
                spawn(EngineZombie.Kind.TANK, 1035, 500)
        ));

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, -60, 160),
                spawn(EngineZombie.Kind.BASIC, 1240, 180),
                spawn(EngineZombie.Kind.BASIC, 1035, 200),
                spawn(EngineZombie.Kind.FAST, 760, -60),
                spawn(EngineZombie.Kind.FAST, 1035, 240),
                spawn(EngineZombie.Kind.TANK, 540, 880)
        ));
        return level;
    }

    private static LevelData buildLevel2() {
        LevelData level = new LevelData("Burning Grounds",
                "assets/craftpix/maps/level2/cursed/fuego.png", pixelCenterToWorld(78 + 18, 628 + 18), 1);

        level.walls.add(obstacleRectData(312, 214, 82, 82));
        level.walls.add(obstacleRectData(666, 338, 96, 96));
        addWorldBounds(level);
        addDecorations(level,
                decoration("assets/craftpix/maps/level2/cursed/Bones_shadow1_1.png", 124, 588, 62, 40),
                decoration("assets/craftpix/maps/level2/cursed/Bones_shadow1_1.png", 818, 126, 58, 38),
                decoration("assets/craftpix/maps/level2/cursed/Ruins_shadow1_3.png", 278, 180, 88, 88),
                decoration("assets/craftpix/maps/level2/cursed/Ruins_shadow1_3.png", 648, 330, 96, 96),
                decoration("assets/craftpix/maps/level2/cursed/Eye_plant_shadow1_1.png", 520, 188, 44, 44),
                decoration("assets/craftpix/maps/level2/cursed/Spike_plant_shadow1_1.png", 886, 540, 66, 66),
                decoration("assets/craftpix/maps/level2/cursed/Many_eyes_plant_shadow1_1.png", 930, 226, 48, 48),
                decoration("assets/craftpix/maps/level2/cursed/Tentacle_plant_shadow1_1.png", 184, 256, 54, 54),
                decoration("assets/craftpix/maps/level2/cursed/Tubular_plant_shadow1_1.png", 760, 528, 50, 50),
                decoration("assets/craftpix/maps/level2/cursed/Pustules_shadow1_1.png", 408, 620, 34, 34),
                decoration("assets/craftpix/maps/level2/cursed/Rock_eyes_shadow1_1.png", 994, 604, 44, 44),
                decoration("assets/craftpix/maps/level2/cursed/Pustules_shadow1_1.png", 564, 120, 30, 30),
                decoration("assets/craftpix/maps/level2/cursed/Eye_plant_shadow1_1.png", 210, 468, 34, 34)
        );

        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(144 + 11, 546 + 11), 30));
        level.pickups.add(new PickupData(EnginePickup.Kind.HEALTH, pixelCenterToWorld(464 + 11, 250 + 11), 30));
        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(846 + 11, 606 + 11), 20));

        RectData exitRect = rectData(906, 592, 64, 86);
        level.exitPosition = exitRect.position;
        level.exitHalfWidth = exitRect.halfWidth;
        level.exitHalfHeight = exitRect.halfHeight;

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, -60, 180),
                spawn(EngineZombie.Kind.BASIC, 860, -60),
                spawn(EngineZombie.Kind.BASIC, -60, 320),
                spawn(EngineZombie.Kind.FAST, 1035, 520),
                spawn(EngineZombie.Kind.FAST, 1035, 260),
                spawn(EngineZombie.Kind.FAST, 860, -60),
                spawn(EngineZombie.Kind.BASIC, 520, 880),
                spawn(EngineZombie.Kind.TANK, 520, -60),
                spawn(EngineZombie.Kind.BASIC, 1240, 520)
        ));

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, -60, 160),
                spawn(EngineZombie.Kind.BASIC, 840, -60),
                spawn(EngineZombie.Kind.FAST, 1035, 520),
                spawn(EngineZombie.Kind.FAST, 1035, 480),
                spawn(EngineZombie.Kind.FAST, 1035, 260),
                spawn(EngineZombie.Kind.FAST, 900, -60),
                spawn(EngineZombie.Kind.BASIC, -60, 560),
                spawn(EngineZombie.Kind.TANK, 850, -60),
                spawn(EngineZombie.Kind.TANK, 1035, 440),
                spawn(EngineZombie.Kind.FAST, 1240, 600),
                spawn(EngineZombie.Kind.BASIC, -60, 660)
        ));
        return level;
    }

    private static LevelData buildLevel3() {
        LevelData level = new LevelData("Final Arena",
                "assets/craftpix/maps/level3/nivel3_background.png", pixelCenterToWorld(566 + 18, 610 + 18), 2);

        level.walls.add(obstacleRectData(54, 120, 110, 120));
        level.walls.add(obstacleRectData(988, 120, 110, 120));
        level.walls.add(obstacleRectData(72, 606, 128, 124));
        level.walls.add(obstacleRectData(962, 606, 132, 124));
        level.walls.add(obstacleRectData(238, 70, 130, 96));
        level.walls.add(obstacleRectData(802, 72, 132, 94));
        level.walls.add(obstacleRectData(210, 632, 128, 92));
        level.walls.add(obstacleRectData(840, 632, 128, 92));
        addWorldBounds(level);
        addDecorations(level,
                decoration("assets/craftpix/maps/level2/forestnight/14.png", 26, 82, 128, 146),
                decoration("assets/craftpix/maps/level2/forestnight/14.png", 1024, 82, 128, 146),
                decoration("assets/craftpix/maps/level2/forestnight/14.png", 40, 574, 138, 158),
                decoration("assets/craftpix/maps/level2/forestnight/14.png", 992, 572, 138, 158),
                decoration("assets/craftpix/maps/level2/forestnight/16.png", 220, 60, 166, 112),
                decoration("assets/craftpix/maps/level2/forestnight/16.png", 794, 58, 168, 114),
                decoration("assets/craftpix/maps/level2/forestnight/16.png", 188, 616, 168, 110),
                decoration("assets/craftpix/maps/level2/forestnight/16.png", 824, 614, 164, 110),
                decoration("assets/craftpix/maps/level2/forestnight/04.png", 114, 128, 92, 92),
                decoration("assets/craftpix/maps/level2/forestnight/04.png", 972, 128, 92, 92),
                decoration("assets/craftpix/maps/level2/forestnight/04.png", 110, 642, 96, 96),
                decoration("assets/craftpix/maps/level2/forestnight/04.png", 970, 640, 96, 96)
        );

        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(262 + 11, 394 + 11), 16));
        level.pickups.add(new PickupData(EnginePickup.Kind.HEALTH, pixelCenterToWorld(564 + 11, 478 + 11), 35));
        level.pickups.add(new PickupData(EnginePickup.Kind.HEALTH, pixelCenterToWorld(564 + 11, 256 + 11), 25));
        level.pickups.add(new PickupData(EnginePickup.Kind.AMMO, pixelCenterToWorld(876 + 11, 394 + 11), 24));

        RectData exitRect = rectData(520, 684, 140, 76);
        level.exitPosition = exitRect.position;
        level.exitHalfWidth = exitRect.halfWidth;
        level.exitHalfHeight = exitRect.halfHeight;

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, 560, -60),
                spawn(EngineZombie.Kind.BASIC, -60, 410),
                spawn(EngineZombie.Kind.FAST, 560, 880),
                spawn(EngineZombie.Kind.FAST, -60, 410),
                spawn(EngineZombie.Kind.BASIC, 1240, 260),
                spawn(EngineZombie.Kind.TANK, 560, -60),
                spawn(EngineZombie.Kind.TANK, 560, 880),
                spawn(EngineZombie.Kind.TANK, 1240, 410),
                spawn(EngineZombie.Kind.FAST, 1240, 580)
        ));

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BASIC, 560, -60),
                spawn(EngineZombie.Kind.BASIC, 1240, 410),
                spawn(EngineZombie.Kind.FAST, 560, -60),
                spawn(EngineZombie.Kind.FAST, 560, 880),
                spawn(EngineZombie.Kind.FAST, -60, 410),
                spawn(EngineZombie.Kind.FAST, 1240, 410),
                spawn(EngineZombie.Kind.BASIC, -60, 220),
                spawn(EngineZombie.Kind.TANK, 560, -60),
                spawn(EngineZombie.Kind.TANK, 560, 880),
                spawn(EngineZombie.Kind.TANK, 1240, 410),
                spawn(EngineZombie.Kind.FAST, -60, 620)
        ));

        level.waves.add(wave(
                spawn(EngineZombie.Kind.BOSS, 560, -120),
                spawn(EngineZombie.Kind.FAST, -60, 410),
                spawn(EngineZombie.Kind.FAST, 1240, 410),
                spawn(EngineZombie.Kind.BASIC, 560, 880),
                spawn(EngineZombie.Kind.TANK, 560, 880),
                spawn(EngineZombie.Kind.TANK, -60, 410),
                spawn(EngineZombie.Kind.FAST, 1240, 620)
        ));
        return level;
    }

    private static List<SpawnData> wave(SpawnData... spawns) {
        List<SpawnData> wave = new ArrayList<>(spawns.length);
        for (SpawnData spawn : spawns) {
            wave.add(spawn);
        }
        return wave;
    }

    private static void addWorldBounds(LevelData level) {
        // Invisible border that keeps the player and projectiles inside the map.
        int thickness = 48;
        level.walls.add(rectData(-thickness, -thickness, thickness, EngineGameWorld.VIEW_HEIGHT + thickness * 2));
        level.walls.add(rectData(EngineGameWorld.VIEW_WIDTH, -thickness, thickness, EngineGameWorld.VIEW_HEIGHT + thickness * 2));
        level.walls.add(rectData(0, -thickness, EngineGameWorld.VIEW_WIDTH, thickness));
        level.walls.add(rectData(0, EngineGameWorld.VIEW_HEIGHT, EngineGameWorld.VIEW_WIDTH, thickness));
    }

    private static void addDecorations(LevelData level, DecorationData... decorations) {
        for (DecorationData decoration : decorations) {
            level.decorations.add(decoration);
        }
    }

    private static DecorationData decoration(String imagePath, int x, int y, int width, int height) {
        return new DecorationData(imagePath, x, y, width, height);
    }

    private static SpawnData spawn(EngineZombie.Kind kind, int pixelX, int pixelY) {
        return new SpawnData(kind, pixelCenterToWorld(pixelX, pixelY));
    }

    private static RectData rectData(int pixelX, int pixelY, int pixelWidth, int pixelHeight) {
        float worldWidth = pixelWidth / (float) EngineGameWorld.VIEW_WIDTH * EngineGameWorld.WORLD_WIDTH;
        float worldHeight = pixelHeight / (float) EngineGameWorld.VIEW_HEIGHT * EngineGameWorld.WORLD_HEIGHT;
        float centerPixelX = pixelX + pixelWidth / 2f;
        float centerPixelY = pixelY + pixelHeight / 2f;
        return new RectData(pixelCenterToWorld(centerPixelX, centerPixelY), worldWidth / 2f, worldHeight / 2f);
    }

    private static RectData obstacleRectData(int pixelX, int pixelY, int pixelWidth, int pixelHeight) {
        // Visible props use a slightly smaller collision box than the sprite bounds.
        int shrunkWidth = Math.max(16, Math.round(pixelWidth * 0.68f));
        int shrunkHeight = Math.max(16, Math.round(pixelHeight * 0.68f));
        int offsetX = pixelX + (pixelWidth - shrunkWidth) / 2;
        int offsetY = pixelY + (pixelHeight - shrunkHeight) / 2;
        return rectData(offsetX, offsetY, shrunkWidth, shrunkHeight);
    }

    private static Vec2 pixelCenterToWorld(float pixelX, float pixelY) {
        float x = pixelX / EngineGameWorld.VIEW_WIDTH * EngineGameWorld.WORLD_WIDTH;
        float y = EngineGameWorld.WORLD_HEIGHT - (pixelY / EngineGameWorld.VIEW_HEIGHT * EngineGameWorld.WORLD_HEIGHT);
        return new Vec2(x, y);
    }

    public static final class LevelData {
        public final String title;
        public final String backgroundPath;
        public final Vec2 playerSpawn;
        public final int weaponIndex;
        public final List<RectData> walls = new ArrayList<>();
        public final List<DecorationData> decorations = new ArrayList<>();
        public final List<PickupData> pickups = new ArrayList<>();
        public final List<List<SpawnData>> waves = new ArrayList<>();
        public Vec2 exitPosition;
        public float exitHalfWidth;
        public float exitHalfHeight;

        private LevelData(String title, String backgroundPath, Vec2 playerSpawn, int weaponIndex) {
            this.title = title;
            this.backgroundPath = backgroundPath;
            this.playerSpawn = playerSpawn;
            this.weaponIndex = weaponIndex;
        }

        public Weapon createWeapon() {
            switch (weaponIndex) {
                case 1:
                    return Weapon.rifle();
                case 2:
                    return Weapon.shotgun();
                default:
                    return Weapon.pistol();
            }
        }
    }

    public static final class SpawnData {
        public final EngineZombie.Kind kind;
        public final Vec2 position;

        private SpawnData(EngineZombie.Kind kind, Vec2 position) {
            this.kind = kind;
            this.position = position;
        }
    }

    public static final class PickupData {
        public final EnginePickup.Kind kind;
        public final Vec2 position;
        public final int amount;

        private PickupData(EnginePickup.Kind kind, Vec2 position, int amount) {
            this.kind = kind;
            this.position = position;
            this.amount = amount;
        }
    }

    public static final class RectData {
        public final Vec2 position;
        public final float halfWidth;
        public final float halfHeight;

        private RectData(Vec2 position, float halfWidth, float halfHeight) {
            this.position = position;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
        }
    }

    public static final class DecorationData {
        public final String imagePath;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private DecorationData(String imagePath, int x, int y, int width, int height) {
            this.imagePath = imagePath;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
