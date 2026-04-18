import org.jbox2d.common.Vec2;

// Guarda el estado actual del teclado y del raton.
public class InputState {
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean firing;
    public boolean reloadPressed;
    public boolean escapePressed;
    public boolean enterPressed;
    public boolean restartPressed;
    public Vec2 aimWorld = new Vec2(EngineGameWorld.WORLD_WIDTH / 2f, EngineGameWorld.WORLD_HEIGHT / 2f);

    public boolean consumeReloadPressed() {
        // "Consumir" significa leer la tecla una vez y apagarla.
        boolean pressed = reloadPressed;
        reloadPressed = false;
        return pressed;
    }

    public boolean consumeEscapePressed() {
        boolean pressed = escapePressed;
        escapePressed = false;
        return pressed;
    }

    public boolean consumeEnterPressed() {
        boolean pressed = enterPressed;
        enterPressed = false;
        return pressed;
    }

    public boolean consumeRestartPressed() {
        boolean pressed = restartPressed;
        restartPressed = false;
        return pressed;
    }

    public void resetForStateChange() {
        // Limpia teclas viejas al pausar, reiniciar o volver al menu.
        up = false;
        down = false;
        left = false;
        right = false;
        firing = false;
        reloadPressed = false;
        escapePressed = false;
        enterPressed = false;
        restartPressed = false;
    }
}
