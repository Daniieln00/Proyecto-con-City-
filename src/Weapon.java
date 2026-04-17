public class Weapon {
    private final String name;
    private final int damage;
    private final int cooldownFrames;
    private final int bulletSpeed;
    private final int pellets;
    private final double spread;
    private final String soundFile;
    private final int magazineSize;
    private final int maxReserveAmmo;
    private final int reloadFrames;
    private final String reloadSoundFile;
    private final String projectileStyle;
    private int ammoInMagazine;
    private int reserveAmmo;
    private int currentCooldown = 0;

    public Weapon(String name, int damage, int cooldownFrames, int bulletSpeed, int pellets, double spread,
                  String soundFile, int magazineSize, int maxReserveAmmo, int reloadFrames,
                  String reloadSoundFile, String projectileStyle) {
        this.name = name;
        this.damage = damage;
        this.cooldownFrames = cooldownFrames;
        this.bulletSpeed = bulletSpeed;
        this.pellets = pellets;
        this.spread = spread;
        this.soundFile = soundFile;
        this.magazineSize = magazineSize;
        this.maxReserveAmmo = maxReserveAmmo;
        this.reloadFrames = reloadFrames;
        this.reloadSoundFile = reloadSoundFile;
        this.projectileStyle = projectileStyle;
        this.ammoInMagazine = magazineSize;
        this.reserveAmmo = maxReserveAmmo;
    }

    public void updateCooldown() {
        if (currentCooldown > 0) {
            currentCooldown--;
        }
    }

    public boolean canShoot() {
        return currentCooldown == 0;
    }

    public void markShot() {
        currentCooldown = cooldownFrames;
    }

    public String getName() {
        return name;
    }

    public int getDamage() {
        return damage;
    }

    public int getBulletSpeed() {
        return bulletSpeed;
    }

    public int getPellets() {
        return pellets;
    }

    public double getSpread() {
        return spread;
    }

    public String getSoundFile() {
        return soundFile;
    }

    public int getMagazineSize() {
        return magazineSize;
    }

    public int getAmmoInMagazine() {
        return ammoInMagazine;
    }

    public int getReserveAmmo() {
        return reserveAmmo;
    }

    public int getMaxReserveAmmo() {
        return maxReserveAmmo;
    }

    public int getReloadFrames() {
        return reloadFrames;
    }

    public String getReloadSoundFile() {
        return reloadSoundFile;
    }

    public String getProjectileStyle() {
        return projectileStyle;
    }

    public boolean hasAmmoInMagazine() {
        return ammoInMagazine > 0;
    }

    public boolean canReload() {
        return ammoInMagazine < magazineSize && reserveAmmo > 0;
    }

    public void useAmmo() {
        if (ammoInMagazine > 0) {
            ammoInMagazine--;
        }
    }

    public void reloadMagazine() {
        int missingAmmo = magazineSize - ammoInMagazine;
        int ammoToLoad = Math.min(missingAmmo, reserveAmmo);
        ammoInMagazine += ammoToLoad;
        reserveAmmo -= ammoToLoad;
    }

    public void addReserveAmmo(int amount) {
        reserveAmmo = Math.min(maxReserveAmmo, reserveAmmo + amount);
    }

    public void refillReserveAmmo() {
        reserveAmmo = maxReserveAmmo;
    }

    public void resetAmmo() {
        ammoInMagazine = magazineSize;
        reserveAmmo = maxReserveAmmo;
        currentCooldown = 0;
    }

    public static Weapon pistol() {
        return new Weapon("Pistol", 20, 14, 14, 1, 0, "pistol.wav", 12, 72, 55, "pistol_reload.wav", "pistol");
    }

    public static Weapon rifle() {
        return new Weapon("Rifle", 24, 8, 18, 1, 0, "rifle.wav", 24, 144, 70, "rifle_reload.wav", "rifle");
    }

    public static Weapon shotgun() {
        return new Weapon("Flamethrower", 18, 20, 15, 6, 0.24, "shotgun.wav", 6, 42, 120, "recarga_escopeta.wav", "fire");
    }
}
