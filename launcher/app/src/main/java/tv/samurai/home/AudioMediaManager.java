package tv.samurai.home;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers audio files on internal storage and connected USB drives, and manages MediaPlayer playlist & progress.
 */
public class AudioMediaManager {
    public static class AudioTrack {
        public String title;
        public String subtitle;
        public Uri uri;
        public String path;

        public AudioTrack(String title, String subtitle, Uri uri, String path) {
            this.title = title;
            this.subtitle = subtitle;
            this.uri = uri;
            this.path = path;
        }
    }

    private final Context context;
    private MediaPlayer mediaPlayer;
    private final List<AudioTrack> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private AudioTrack currentTrack;
    private boolean isPlaying = false;

    public interface PlaybackListener {
        void onStateChanged(AudioTrack track, boolean playing);
    }

    private final PlaybackListener listener;

    public AudioMediaManager(Context context, PlaybackListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public List<AudioTrack> scanAudioFiles() {
        List<AudioTrack> tracks = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        // 1. Query MediaStore (covers indexed internal & external/USB volumes)
        try {
            ContentResolver cr = context.getContentResolver();
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA
            };
            try (Cursor cursor = cr.query(collection, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC")) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                    int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        String title = cursor.getString(titleCol);
                        String artist = cursor.getString(artistCol);
                        String name = cursor.getString(nameCol);
                        String path = dataCol >= 0 ? cursor.getString(dataCol) : null;

                        if (title == null || title.trim().isEmpty()) title = name;
                        if (title == null) title = "AUDIO_SHARD_" + id;
                        if (artist == null || artist.contains("<unknown>")) {
                            artist = (path != null && path.startsWith("/storage/") && !path.startsWith("/storage/emulated"))
                                    ? "USB DRIVE // AUDIO" : "INTERNAL DRIVE // AUDIO";
                        }

                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        if (path != null) seenPaths.add(path);
                        tracks.add(new AudioTrack(title.toUpperCase(), artist.toUpperCase(), contentUri, path));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Direct filesystem scan on internal storage & USB mount points (/storage, /mnt/media_rw)
        List<File> roots = new ArrayList<>();
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null && ext.exists()) roots.add(ext);
        } catch (Exception ignored) {
        }
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] children = storageDir.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!c.getName().equalsIgnoreCase("self") && !c.getName().equalsIgnoreCase("emulated")) {
                        roots.add(c);
                    }
                }
            }
        }
        File mntUsb = new File("/mnt/media_rw");
        if (mntUsb.exists() && mntUsb.isDirectory()) {
            File[] children = mntUsb.listFiles();
            if (children != null) {
                for (File c : children) roots.add(c);
            }
        }

        for (File root : roots) {
            scanDirectory(root, 0, tracks, seenPaths);
        }

        playlist.clear();
        playlist.addAll(tracks);
        return tracks;
    }

    private void scanDirectory(File dir, int depth, List<AudioTrack> out, Set<String> seen) {
        if (dir == null || depth > 4 || out.size() > 150) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                if (f.getName().equalsIgnoreCase("Android")) continue;
                scanDirectory(f, depth + 1, out, seen);
            } else if (isAudioFile(f.getName())) {
                String abs = f.getAbsolutePath();
                if (!seen.contains(abs)) {
                    seen.add(abs);
                    String name = f.getName();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) name = name.substring(0, dot);
                    boolean isUsb = !abs.startsWith("/storage/emulated") && !abs.startsWith("/sdcard");
                    String source = isUsb ? "USB DRIVE // " + dir.getName().toUpperCase() : "INTERNAL // " + dir.getName().toUpperCase();
                    out.add(new AudioTrack(name.toUpperCase(), source, Uri.fromFile(f), abs));
                }
            }
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")
                || lower.endsWith(".ogg") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                || lower.endsWith(".opus");
    }

    public void playTrack(AudioTrack track) {
        stop();
        try {
            if (playlist.isEmpty()) {
                scanAudioFiles();
            }
            for (int i = 0; i < playlist.size(); i++) {
                AudioTrack t = playlist.get(i);
                if ((track.path != null && track.path.equals(t.path)) || track.title.equals(t.title)) {
                    currentIndex = i;
                    break;
                }
            }

            mediaPlayer = new MediaPlayer();
            if (track.path != null && new File(track.path).exists()) {
                mediaPlayer.setDataSource(track.path);
            } else if (track.uri != null) {
                mediaPlayer.setDataSource(context, track.uri);
            }
            mediaPlayer.setOnCompletionListener(mp -> playNext());
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentTrack = track;
            isPlaying = true;
            if (listener != null) listener.onStateChanged(currentTrack, true);
        } catch (Exception e) {
            isPlaying = false;
            if (listener != null) listener.onStateChanged(track, false);
        }
    }

    public boolean playNext() {
        if (playlist.isEmpty()) scanAudioFiles();
        if (playlist.isEmpty()) return false;
        currentIndex = (currentIndex + 1) % playlist.size();
        playTrack(playlist.get(currentIndex));
        return true;
    }

    public boolean playPrev() {
        if (playlist.isEmpty()) scanAudioFiles();
        if (playlist.isEmpty()) return false;
        if (mediaPlayer != null && getCurrentPositionMs() > 4000) {
            seekToMs(0);
            return true;
        }
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playTrack(playlist.get(currentIndex));
        return true;
    }

    public void seekByMs(int deltaMs) {
        if (mediaPlayer != null) {
            int dur = getDurationMs();
            if (dur > 0) {
                int target = Math.max(0, Math.min(dur, getCurrentPositionMs() + deltaMs));
                seekToMs(target);
            }
        }
    }

    public void seekToMs(int posMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(posMs);
            } catch (Exception ignored) {
            }
        }
    }

    public int getCurrentPositionMs() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public int getDurationMs() {
        if (mediaPlayer != null) {
            try {
                return Math.max(0, mediaPlayer.getDuration());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPlaying = false;
            } else {
                mediaPlayer.start();
                isPlaying = true;
            }
            if (listener != null) listener.onStateChanged(currentTrack, isPlaying);
        } else {
            if (playlist.isEmpty()) scanAudioFiles();
            if (!playlist.isEmpty()) {
                currentIndex = 0;
                playTrack(playlist.get(0));
            }
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        isPlaying = false;
        if (listener != null && currentTrack != null) listener.onStateChanged(currentTrack, false);
    }

    public boolean isPlaying() {
        return mediaPlayer != null && isPlaying;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }
}
