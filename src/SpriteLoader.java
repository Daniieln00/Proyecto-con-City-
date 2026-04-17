import javax.imageio.ImageIO;
import city.cs.engine.BodyImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SpriteLoader {
    private static final Map<String, BufferedImage[]> FRAME_CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, BodyImage> BODY_IMAGE_CACHE = new HashMap<>();
    private static final ExecutorService PRELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sprite-preload");
        thread.setDaemon(true);
        return thread;
    });

    private SpriteLoader() {
    }

    public static BufferedImage[] loadFrames(String directoryPath) {
        BufferedImage[] cachedFrames = FRAME_CACHE.get(directoryPath);
        if (cachedFrames != null) {
            return cachedFrames;
        }

        File directory = new File(directoryPath);
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("No sprite frames found in " + directoryPath);
        }

        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        BufferedImage[] frames = new BufferedImage[files.length];

        for (int i = 0; i < files.length; i++) {
            try {
                frames[i] = ImageIO.read(files[i]);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load sprite frame " + files[i].getPath(), e);
            }
        }

        FRAME_CACHE.put(directoryPath, frames);
        return frames;
    }

    public static BufferedImage loadImage(String imagePath) {
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
        PRELOAD_EXECUTOR.execute(() -> {
            for (String imagePath : imagePaths) {
                loadImage(imagePath);
            }
        });
    }

    public static void preloadBodyImagesAsync(float height, String... imagePaths) {
        PRELOAD_EXECUTOR.execute(() -> {
            for (String imagePath : imagePaths) {
                loadBodyImage(imagePath, height);
            }
        });
    }
}
