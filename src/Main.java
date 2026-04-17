import city.cs.engine.UserView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InputState input = new InputState();
            EngineGameWorld world = new EngineGameWorld(input);
            UserView view = new EngineGameView(world, input, EngineGameWorld.VIEW_WIDTH, EngineGameWorld.VIEW_HEIGHT);

            JFrame frame = new JFrame("Zombie Survival");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            view.requestFocusInWindow();
            world.start();
        });
    }
}
