import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SoundManager {
    private static final String SOUND_FOLDER = "assets/sounds";
    private static final String MUSIC_FOLDER = "assets/music";
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(".wav", ".aif", ".aiff", ".au", ".mp3");
    private static final Map<String, File> AUDIO_FILES = new ConcurrentHashMap<>();
    private static final Map<String, Float> VOLUME_ADJUSTMENTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_PLAYED_AT = new ConcurrentHashMap<>();
    private static final Map<String, List<Clip>> ACTIVE_CLIPS = new ConcurrentHashMap<>();
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "audio-worker");
        thread.setDaemon(true);
        return thread;
    });
    private static Clip backgroundClip;
    private static Clip ambientClip;
    private static String currentBackgroundTrack;
    private static String currentAmbientTrack;

    static {
        VOLUME_ADJUSTMENTS.put("pistol.wav", -27.0f);
        VOLUME_ADJUSTMENTS.put("rifle.wav", -16.0f);
        VOLUME_ADJUSTMENTS.put("shotgun.wav", -18.0f);

        VOLUME_ADJUSTMENTS.put("pistol_reload.wav", -5.0f);
        VOLUME_ADJUSTMENTS.put("rifle_reload.wav", -4.0f);
        VOLUME_ADJUSTMENTS.put("shotgun_reload.wav", -4.5f);

        VOLUME_ADJUSTMENTS.put("Terror.wav", -20.0f);
        VOLUME_ADJUSTMENTS.put("Terror.mp3", -18.0f);
        VOLUME_ADJUSTMENTS.put("miedo.wav", -19.0f);
        VOLUME_ADJUSTMENTS.put("jefe_final.mp3", -16.0f);
        VOLUME_ADJUSTMENTS.put("final_boss.wav", -15.0f);
        VOLUME_ADJUSTMENTS.put("jefe_final.wav", -13.0f);
        VOLUME_ADJUSTMENTS.put("susurros.wav", -28.0f);
        VOLUME_ADJUSTMENTS.put("zombie atak.wav", -12.0f);
        VOLUME_ADJUSTMENTS.put("player_pain.wav", -10.0f);
        VOLUME_ADJUSTMENTS.put("respirar.wav", -6.0f);
        VOLUME_ADJUSTMENTS.put("pistola zombie .wav", -10.5f);
        VOLUME_ADJUSTMENTS.put("escopeta zombie hit .wav", -9.0f);
        VOLUME_ADJUSTMENTS.put("zombie_grito_1.wav", -16.0f);
    }

    private SoundManager() {
    }

    public static void play(String fileName) {
        play(fileName, 0);
    }

    public static void play(String fileName, long minIntervalMs) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }

        if (!isReadyToPlay(fileName, minIntervalMs)) {
            return;
        }

        AUDIO_EXECUTOR.execute(() -> {
            try {
                File soundFile = AUDIO_FILES.computeIfAbsent(fileName, SoundManager::resolveAudioFile);
                if (!soundFile.exists()) {
                    return;
                }

                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                applyVolume(clip, VOLUME_ADJUSTMENTS.getOrDefault(fileName, 0.0f));
                ACTIVE_CLIPS.computeIfAbsent(fileName, key -> new CopyOnWriteArrayList<>()).add(clip);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        List<Clip> clips = ACTIVE_CLIPS.get(fileName);
                        if (clips != null) {
                            clips.remove(clip);
                        }
                        clip.close();
                    }
                });
                clip.start();
            } catch (Exception ignored) {
            }
        });
    }

    public static void playBackgroundMusic(String fileName) {
        AUDIO_EXECUTOR.execute(() -> {
            backgroundClip = startLoop(fileName, backgroundClip, currentBackgroundTrack);
            if (backgroundClip != null) {
                currentBackgroundTrack = fileName;
            } else {
                currentBackgroundTrack = null;
            }
        });
    }

    public static void playAmbientLoop(String fileName) {
        AUDIO_EXECUTOR.execute(() -> {
            ambientClip = startLoop(fileName, ambientClip, currentAmbientTrack);
            if (ambientClip != null) {
                currentAmbientTrack = fileName;
            } else {
                currentAmbientTrack = null;
            }
        });
    }

    public static void warmUp(String... fileNames) {
        if (fileNames == null || fileNames.length == 0) {
            return;
        }

        AUDIO_EXECUTOR.execute(() -> {
            for (String fileName : fileNames) {
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }

                try {
                    File audioFile = AUDIO_FILES.computeIfAbsent(fileName, SoundManager::resolveAudioFile);
                    if (!audioFile.exists()) {
                        continue;
                    }

                    try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile)) {
                        Clip clip = AudioSystem.getClip();
                        clip.open(audioInputStream);
                        clip.close();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void stopAllLoops() {
        AUDIO_EXECUTOR.execute(() -> {
            stopClip(backgroundClip);
            stopClip(ambientClip);
            backgroundClip = null;
            ambientClip = null;
            currentBackgroundTrack = null;
            currentAmbientTrack = null;
        });
    }

    private static Clip startLoop(String fileName, Clip existingClip, String currentTrack) {
        if (fileName == null || fileName.isEmpty()) {
            return existingClip;
        }

        if (fileName.equals(currentTrack) && existingClip != null && existingClip.isRunning()) {
            return existingClip;
        }

        stopClip(existingClip);

        try {
            File musicFile = AUDIO_FILES.computeIfAbsent(fileName, SoundManager::resolveAudioFile);
            if (!musicFile.exists()) {
                return null;
            }

            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(musicFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            applyVolume(clip, VOLUME_ADJUSTMENTS.getOrDefault(musicFile.getName(), -12.0f));
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            return clip;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void stopClip(Clip clip) {
        if (clip == null) {
            return;
        }

        clip.stop();
        clip.close();
    }

    private static boolean isReadyToPlay(String fileName, long minIntervalMs) {
        if (minIntervalMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long lastPlayed = LAST_PLAYED_AT.getOrDefault(fileName, 0L);
        if (now - lastPlayed < minIntervalMs) {
            return false;
        }

        LAST_PLAYED_AT.put(fileName, now);
        return true;
    }

    private static File resolveAudioFile(String name) {
        if (name.contains(".")) {
            File soundFile = new File(SOUND_FOLDER, name);
            if (soundFile.exists()) {
                return soundFile;
            }

            File musicFile = new File(MUSIC_FOLDER, name);
            if (musicFile.exists()) {
                return musicFile;
            }

            return soundFile;
        }

        for (String extension : SUPPORTED_EXTENSIONS) {
            File musicFile = new File(MUSIC_FOLDER, name + extension);
            if (musicFile.exists()) {
                return musicFile;
            }

            File soundFile = new File(SOUND_FOLDER, name + extension);
            if (soundFile.exists()) {
                return soundFile;
            }
        }

        return new File(SOUND_FOLDER, name + ".wav");
    }

    private static void applyVolume(Clip clip, float gainDb) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float safeGain = Math.max(gainControl.getMinimum(), Math.min(gainDb, gainControl.getMaximum()));
        gainControl.setValue(safeGain);
    }
}
