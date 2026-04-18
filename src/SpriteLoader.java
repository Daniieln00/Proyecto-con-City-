import javax.imageio.ImageIO;
import city.cs.engine.BodyImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Esta clase carga imagenes y las guarda en memoria para no repetir trabajo.
public final class SpriteLoader {
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, BodyImage> BODY_IMAGE_CACHE = new HashMap<>();
    private static final ExecutorService PRELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sprite-preload");
        thread.setDaemon(true);
        return thread;
    });

    private SpriteLoader() {
    }

    public static BufferedImage loadImage(String imagePath) {
        // Las imagenes grandes del mapa se guardan aparte.
        BufferedImage cachedImage = IMAGE_CACHE.get(imagePath);
        if (cachedImage != null) {
            return cachedImage;
        }

        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            if (image == null) {
                throw new IllegalStateException("Could not decode image " + imagePath);
            }
            IMAGE_CACHE.put(imagePath, image);
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load image " + imagePath, e);
        }
    }

    public static BodyImage loadBodyImage(String imagePath, float height) {
        // La imagen del cuerpo depende de la ruta y del tamano en pantalla.
        String cacheKey = imagePath + "|" + height;
        BodyImage cachedImage = BODY_IMAGE_CACHE.get(cacheKey);
        if (cachedImage != null) {
            return cachedImage;
        }

        BodyImage bodyImage = new BodyImage(imagePath, height);
        BODY_IMAGE_CACHE.put(cacheKey, bodyImage);
        return bodyImage;
    }

    public static void preloadImagesAsync(String... imagePaths) {
        // Precarga fondos en el menu para evitar tirones.
        PRELOAD_EXECUTOR.execute(() -> {
            for (String imagePath : imagePaths) {
                loadImage(imagePath);
            }
        });
    }

    public static void preloadBodyImagesAsync(float height, String... imagePaths) {
        // Precarga personajes y efectos para que el juego vaya mas suave.
        PRELOAD_EXECUTOR.execute(() -> {
            for (String imagePath : imagePaths) {
                loadBodyImage(imagePath, height);
            }
        });
    }
}
