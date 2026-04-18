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
    // Aqui se separan los efectos cortos y la musica larga.
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
        // Mezcla general: ningun sonido debe pegar un salto raro sobre el resto.
        VOLUME_ADJUSTMENTS.put("pistol.wav", -24.0f);
        VOLUME_ADJUSTMENTS.put("rifle.wav", -22.5f);
        VOLUME_ADJUSTMENTS.put("shotgun.wav", -23.0f);

        VOLUME_ADJUSTMENTS.put("pistol_reload.wav", -18.0f);
        VOLUME_ADJUSTMENTS.put("rifle_reload.wav", -17.5f);
        VOLUME_ADJUSTMENTS.put("shotgun_reload.wav", -18.5f);

        VOLUME_ADJUSTMENTS.put("Terror.wav", -19.0f);
        VOLUME_ADJUSTMENTS.put("Terror.mp3", -19.0f);
        VOLUME_ADJUSTMENTS.put("miedo.wav", -19.5f);
        VOLUME_ADJUSTMENTS.put("final_boss.wav", -22.0f);
        VOLUME_ADJUSTMENTS.put("jefe_final.wav", -10.0f);
        VOLUME_ADJUSTMENTS.put("susurros.wav", -23.5f);
        VOLUME_ADJUSTMENTS.put("player_pain.wav", -20.5f);
        VOLUME_ADJUSTMENTS.put("pistola zombie .wav", -20.5f);
        VOLUME_ADJUSTMENTS.put("escopeta zombie hit .wav", -20.0f);
        VOLUME_ADJUSTMENTS.put("zombie_grito_1.wav", -14.0f);
    }

    private SoundManager() {
    }

    // Reproduce un sonido sin limite extra.
    public static void play(String fileName) {
        play(fileName, 0);
    }

    // Reproduce un sonido y opcionalmente evita repetirlo demasiado rapido.
    public static void play(String fileName, long minIntervalMs) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }

        // Evita que el mismo sonido se repita demasiado rapido.
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
        // La musica principal suena en bucle y cambia la anterior.
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
        // El ambiente va aparte para sonar al mismo tiempo que la musica.
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

        // Carga sonidos en el menu para que luego no den tirones.
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
        // Esto solo para sonidos en bucle, no disparos sueltos.
        AUDIO_EXECUTOR.execute(() -> {
            stopClip(backgroundClip);
            stopClip(ambientClip);
            backgroundClip = null;
            ambientClip = null;
            currentBackgroundTrack = null;
            currentAmbientTrack = null;
        });
    }

    public static void stopSound(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }

        // Sirve para cortar un sonido al instante.
        AUDIO_EXECUTOR.execute(() -> {
            List<Clip> clips = ACTIVE_CLIPS.remove(fileName);
            if (clips == null) {
                return;
            }
            for (Clip clip : clips) {
                stopClip(clip);
            }
        });
    }

    private static Clip startLoop(String fileName, Clip existingClip, String currentTrack) {
        // Inicia un audio en bucle si no estaba sonando ya.
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
