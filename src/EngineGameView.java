import city.cs.engine.UserView;
import org.jbox2d.common.Vec2;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class EngineGameView extends UserView {
    private static final long serialVersionUID = 1L;
    private static final Map<String, BufferedImage> BACKGROUND_CACHE = new HashMap<>();
    private static final String MENU_BACKGROUND_PATH = "assets/menu_inicio.png";
    private static final Font HUD_FONT = new Font("Arial", Font.BOLD, 14);
    private static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 42);
    private static final Font SUBTITLE_BOLD_FONT = new Font("Serif", Font.BOLD, 19);
    private static final Font SUBTITLE_FONT = new Font("Serif", Font.PLAIN, 18);
    private static final Font ANNOUNCEMENT_TITLE_FONT = new Font("Serif", Font.BOLD, 34);
    private static final Font ANNOUNCEMENT_SUBTITLE_FONT = new Font("Serif", Font.PLAIN, 20);
    private static final Font PANEL_SUBTITLE_FONT = new Font("Serif", Font.PLAIN, 22);

    private transient final EngineGameWorld gameWorld;
    private transient final InputState input;

    // Crea la vista y conecta teclado y raton.
    public EngineGameView(EngineGameWorld world, InputState input, int width, int height) {
        super(world, width, height);
        this.gameWorld = world;
        this.input = input;

        setPreferredSize(new Dimension(width, height));
        setFocusable(true);

        float zoom = Math.min(width / EngineGameWorld.WORLD_WIDTH, height / EngineGameWorld.WORLD_HEIGHT);
        setView(new Vec2(EngineGameWorld.WORLD_WIDTH / 2f, EngineGameWorld.WORLD_HEIGHT / 2f), zoom);

        addKeyListener(new KeyHandler());
        addMouseListener(new MouseHandler());
        addMouseMotionListener(new MouseMotionHandler());
    }

    @Override
    protected void paintBackground(Graphics2D g) {
        // Dibuja el fondo del nivel y la decoracion fija.
        applyDamageShake(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (gameWorld.getGameState() == GameState.MENU) {
            g.drawImage(getBackgroundImage(MENU_BACKGROUND_PATH), 0, 0, getWidth(), getHeight(), null);
            return;
        }

        g.drawImage(getBackgroundImage(gameWorld.getCurrentBackgroundPath()), 0, 0, getWidth(), getHeight(), null);
        drawDecorations(g);
    }

    @Override
    protected void paintForeground(Graphics2D g) {
        // Dibuja la interfaz, barras de vida, portal y mensajes en pantalla.
        applyDamageShake(g);
        drawBloodEffects(g);
        drawExitPortal(g);
        drawZombieHealthBars(g);
        drawHud(g);
        drawOverlay(g);
    }

    private BufferedImage getBackgroundImage(String path) {
        return BACKGROUND_CACHE.computeIfAbsent(path, SpriteLoader::loadImage);
    }

    private void drawHud(Graphics2D g) {
        if (gameWorld.getGameState() == GameState.MENU) {
            return;
        }

        // El HUD solo sale cuando estamos jugando.
        EnginePlayer player = gameWorld.getPlayer();

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(14, 14, 280, 118, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(HUD_FONT);
        g.drawString("Level: " + (gameWorld.getCurrentLevelIndex() + 1) + "/3", 26, 34);
        g.drawString("Wave: " + gameWorld.getCurrentWave() + "/" + gameWorld.getTotalWaves(), 26, 54);
        g.drawString("Weapon: " + player.getWeapon().getName(), 26, 74);
        g.drawString("Ammo: " + player.getWeapon().getAmmoInMagazine() + "/" + player.getWeapon().getReserveAmmo(), 26, 94);

        g.drawString("Health", 170, 34);
        g.setColor(Color.RED);
        g.fillRoundRect(170, 44, 110, 10, 10, 10);
        g.setColor(Color.GREEN);
        int healthWidth = (int) (110 * (player.getHealth() / (double) player.getMaxHealth()));
        g.fillRoundRect(170, 44, Math.max(0, healthWidth), 10, 10, 10);
        g.setColor(Color.WHITE);
        g.drawRoundRect(170, 44, 110, 10, 10, 10);

        g.setColor(player.isReloading() ? Color.ORANGE : Color.LIGHT_GRAY);
        g.drawString(player.isReloading() ? "Reloading..." : "Press R to reload", 170, 94);

        EngineZombie boss = gameWorld.getActiveBoss();
        if (boss != null) {
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(320, 22, 540, 28, 18, 18);
            g.setColor(new Color(90, 16, 16));
            g.fillRoundRect(326, 28, 528, 16, 12, 12);
            int bossBarWidth = (int) (528 * (boss.getHealth() / (double) boss.getMaxHealth()));
            g.setColor(new Color(224, 58, 40));
            g.fillRoundRect(326, 28, Math.max(0, bossBarWidth), 16, 12, 12);
            g.setColor(new Color(255, 226, 190));
            g.drawRoundRect(326, 28, 528, 16, 12, 12);
            g.drawString("Final Boss", 560, 20);
        }
    }

    private void drawDecorations(Graphics2D g) {
        // Dibuja objetos del escenario que no se mueven.
        for (GameLevels.DecorationData decoration : gameWorld.getCurrentDecorations()) {
            g.drawImage(getBackgroundImage(decoration.imagePath), decoration.x, decoration.y, decoration.width, decoration.height, null);
        }
    }

    private void drawZombieHealthBars(Graphics2D g) {
        // Dibuja la vida de zombies normales.
        if (gameWorld.getGameState() != GameState.PLAYING && gameWorld.getGameState() != GameState.PAUSED) {
            return;
        }

        for (EngineZombie zombie : gameWorld.getZombies()) {
            if (zombie == null || !zombie.isAlive() || zombie.isBoss()) {
                continue;
            }

            Point2D.Float screenPoint = worldToView(zombie.getPosition());
            int barWidth = 34;
            int barHeight = 5;
            int barX = Math.round(screenPoint.x) - barWidth / 2;
            int barY = Math.round(screenPoint.y) - 34;

            g.setColor(new Color(70, 0, 0, 220));
            g.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

            int fillWidth = (int) (barWidth * (zombie.getHealth() / (double) zombie.getMaxHealth()));
            g.setColor(new Color(46, 198, 68, 230));
            g.fillRoundRect(barX, barY, Math.max(0, fillWidth), barHeight, 6, 6);

            g.setColor(new Color(255, 235, 220, 220));
            g.drawRoundRect(barX, barY, barWidth, barHeight, 6, 6);
        }
    }

    private void drawExitPortal(Graphics2D g) {
        if (!gameWorld.isExitActive()) {
            return;
        }

        // La salida ahora es mas visual y no lleva texto encima.
        EngineExit exit = gameWorld.getExit();
        Vec2 exitPos = exit.getPosition();
        Point2D.Float center = worldToView(exitPos);
        Point2D.Float edge = worldToView(new Vec2(exitPos.x + exit.getHalfWidth(), exitPos.y + exit.getHalfHeight()));
        int width = Math.max(72, Math.abs(Math.round((edge.x - center.x) * 2)));
        int height = Math.max(92, Math.abs(Math.round((center.y - edge.y) * 2)));
        int x = Math.round(center.x) - width / 2;
        int y = Math.round(center.y) - height / 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 420.0);
        int outerAlpha = 85 + Math.round(pulse * 40);
        int innerAlpha = 130 + Math.round(pulse * 45);

        g.setColor(new Color(245, 230, 195, outerAlpha));
        g.fillOval(x - 24, y + height - 18, width + 48, 42);
        g.setColor(new Color(255, 246, 224, innerAlpha));
        g.fillRoundRect(x + 10, y - 10, width - 20, height + 20, 26, 26);
        g.setColor(new Color(255, 250, 240, 205));
        g.fillRoundRect(x + 22, y + 8, width - 44, height - 16, 20, 20);
        g.setColor(new Color(199, 177, 130, 210));
        g.drawRoundRect(x + 10, y - 10, width - 20, height + 20, 26, 26);
        g.drawOval(x - 8, y + height - 8, width + 16, 16);
    }

    private void drawBloodEffects(Graphics2D g) {
        for (EngineGameWorld.BloodEffect blood : gameWorld.getBloodEffects()) {
            Point2D.Float center = worldToView(blood.position);
            Point2D.Float edge = worldToView(new Vec2(blood.position.x + blood.radius, blood.position.y));
            int radius = Math.max(10, Math.abs(Math.round(edge.x - center.x)));
            float fade = blood.getFramesLeft() / (float) blood.maxFrames;
            int alpha = Math.max(0, Math.min(150, Math.round(fade * 140)));

            g.setColor(new Color(110, 10, 10, alpha));
            g.fillOval(Math.round(center.x) - radius, Math.round(center.y) - radius / 2, radius * 2, radius);
            g.setColor(new Color(145, 18, 18, Math.max(0, alpha - 25)));
            g.fillOval(Math.round(center.x) - radius / 2, Math.round(center.y) - radius / 3, radius, Math.max(8, radius / 2));
        }
    }

    private void drawOverlay(Graphics2D g) {
        // Estos mensajes salen encima de todo: pausa, muerte, victoria y avisos.
        if (gameWorld.getGameState() == GameState.MENU) {
            drawMenuOverlay(g);
            return;
        }

        drawDamageOverlay(g);

        if (gameWorld.getAnnouncementFrames() > 0) {
            g.setColor(new Color(0, 0, 0, 125));
            g.fillRoundRect(350, 96, 480, 94, 24, 24);
            g.setColor(new Color(245, 233, 206));
            g.setFont(ANNOUNCEMENT_TITLE_FONT);
            drawCenteredString(g, gameWorld.getAnnouncementTitle(), getWidth(), 138);
            g.setFont(ANNOUNCEMENT_SUBTITLE_FONT);
            drawCenteredString(g, gameWorld.getAnnouncementSubtitle(), getWidth(), 170);
        }

        if (gameWorld.getGameState() == GameState.PAUSED) {
            drawCenteredPanel(g, "PAUSED", "Press ESC to continue");
            return;
        }

        if (gameWorld.getGameState() == GameState.GAME_OVER) {
            drawCenteredPanel(g, "GAME OVER", "Press R to restart");
            return;
        }

        if (gameWorld.getGameState() == GameState.VICTORY) {
            drawCenteredPanel(g, "NIGHT SURVIVED", "You escaped. Press R to play again");
        }
    }

    private void drawMenuOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 82));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(70, 10, 10, 132));
        g.fillRoundRect(290, 175, 600, 255, 24, 24);
        g.setColor(new Color(218, 201, 171, 185));
        g.drawRoundRect(290, 175, 600, 255, 24, 24);

        g.setFont(TITLE_FONT);
        drawCenteredString(g, "ZOMBIE SURVIVAL", getWidth(), 242);
        g.setFont(SUBTITLE_BOLD_FONT);
        drawCenteredString(g, "A dark road. Three levels. Survive the swarm.", getWidth(), 286);
        g.setFont(SUBTITLE_FONT);
        drawCenteredString(g, "WASD to move", getWidth(), 334);
        drawCenteredString(g, "Mouse to aim and hold click to shoot", getWidth(), 365);
        drawCenteredString(g, "Press R to reload and reach the exit when it opens", getWidth(), 396);
        g.setFont(ANNOUNCEMENT_SUBTITLE_FONT);
        g.setColor(new Color(255, 213, 136));
        drawCenteredString(g, "PRESS ENTER TO START", getWidth(), 457);
    }

    private void drawCenteredPanel(Graphics2D g, String title, String subtitle) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(245, 233, 206));
        g.setFont(TITLE_FONT);
        drawCenteredString(g, title, getWidth(), getHeight() / 2 - 12);
        g.setFont(PANEL_SUBTITLE_FONT);
        drawCenteredString(g, subtitle, getWidth(), getHeight() / 2 + 28);
    }

    private void drawDamageOverlay(Graphics2D g) {
        EnginePlayer player = gameWorld.getPlayer();
        if (player.getDamageFeedbackFrames() <= 0) {
            return;
        }

        int alpha = Math.min(120, player.getDamageFeedbackFrames() * 7);
        g.setColor(new Color(160, 20, 20, alpha));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void applyDamageShake(Graphics2D g) {
        EnginePlayer player = gameWorld.getPlayer();
        if (player.getDamageFeedbackFrames() <= 0 || gameWorld.getGameState() == GameState.MENU) {
            return;
        }

        int frames = player.getDamageFeedbackFrames();
        int amplitude = Math.max(1, (frames + 3) / 4);
        int offsetX = ((frames * 37) % (amplitude * 2 + 1)) - amplitude;
        int offsetY = ((frames * 19) % (amplitude * 2 + 1)) - amplitude;
        g.translate(offsetX, offsetY);
    }

    private void drawCenteredString(Graphics2D g, String text, int width, int y) {
        int x = (width - g.getFontMetrics().stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void updateMouseAim(MouseEvent event) {
        Point viewPoint = event.getPoint();
        input.aimWorld = viewToWorld(viewPoint);
    }

    private class KeyHandler extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W:
                    input.up = true;
                    break;
                case KeyEvent.VK_S:
                    input.down = true;
                    break;
                case KeyEvent.VK_A:
                    input.left = true;
                    break;
                case KeyEvent.VK_D:
                    input.right = true;
                    break;
                case KeyEvent.VK_R:
                    if (gameWorld.getGameState() == GameState.GAME_OVER
                            || gameWorld.getGameState() == GameState.VICTORY) {
                        input.restartPressed = true;
                    } else {
                        input.reloadPressed = true;
                    }
                    break;
                case KeyEvent.VK_ESCAPE:
                    input.escapePressed = true;
                    break;
                case KeyEvent.VK_ENTER:
                    input.enterPressed = true;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W:
                    input.up = false;
                    break;
                case KeyEvent.VK_S:
                    input.down = false;
                    break;
                case KeyEvent.VK_A:
                    input.left = false;
                    break;
                case KeyEvent.VK_D:
                    input.right = false;
                    break;
                default:
                    break;
            }
        }
    }

    private class MouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            updateMouseAim(e);
            if (e.getButton() == MouseEvent.BUTTON1) {
                input.firing = true;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                input.firing = false;
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            input.firing = false;
        }
    }

    private class MouseMotionHandler extends MouseMotionAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            updateMouseAim(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            updateMouseAim(e);
        }
    }
}
