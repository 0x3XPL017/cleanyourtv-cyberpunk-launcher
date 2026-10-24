package tv.samurai.home;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private static final int PICK_AUDIO_REQUEST = 1001;
    private static final int COLS_PER_ROW = 6;

    // Built-in Cyberpunk Samurai Radio animation demo track (visual animation only)
    private static final String[][] SAMURAI_SYNTH_PLAYLIST = new String[][]{
            {"CHIPPIN' IN", "SAMURAI // NIGHT CITY FM 107.3", "204"}
    };

    private TextView clockText;
    private TextView bannerTitle;
    private View btnSettings;
    private View btnWifi;
    private WifiSignalView wifiSignalView;
    private TextView wifiSsidText;

    private View widgetSystemStatus;
    private View widgetWeather;
    private View widgetNowPlaying;

    private TextView weatherCityTemp;
    private TextView weatherCondition;
    private TextView weatherCyberSub;
    private TextView weatherDetails;
    private TextView sysTelemetry;
    private TextView sysBoostHint;

    // Now Playing controls & progress bar
    private TextView btnAudioBrowse;
    private TextView trackTitle;
    private TextView trackArtist;
    private AudioSpectrumView audioSpectrum;
    private View audioSeekContainer;
    private TextView audioTimeCurrent;
    private ProgressBar audioProgressBar;
    private TextView audioTimeTotal;
    private TextView btnMediaPrev;
    private TextView btnMediaPlay;
    private TextView btnMediaNext;
    private TextView audioStatusFooter;

    private CircularGaugeView gaugeCpu;
    private CircularGaugeView gaugeRam;

    private TextView widgetsTitle;
    private LinearLayout widgetsContainer;
    private View bottomFooter;

    private TextView appsManageHint;
    private LinearLayout appsRow;
    private LinearLayout secondarySection;
    private ScrollView secondaryScroll;
    private LinearLayout secondaryRowsContainer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SystemMonitor systemMonitor;
    private Config config;
    private AudioMediaManager audioManager;
    private Weather weather;

    private final List<AppEntry> appList = new ArrayList<>();
    private final List<View> cardViews = new ArrayList<>();
    private final List<AppEntry> secondaryAppList = new ArrayList<>();
    private final List<View> secondaryCardViews = new ArrayList<>();

    private int synthTrackIdx = 0;
    private int demoProgressSec = 0;
    private int demoTotalSec = 204;
    private boolean isSynthPlaying = true;

    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss:SSS", Locale.US);
    private final SimpleDateFormat footerDateFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US);
    private TextView footerDateText;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setupAppCards();
        }
    };

    private boolean isNowPlayingActive() {
        if (audioManager != null && audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
            return true;
        }
        return isSynthPlaying;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        config = new Config(this);
        systemMonitor = new SystemMonitor(this);
        weather = new Weather();
        audioManager = new AudioMediaManager(this, (track, playing) -> {
            if (track != null && playing) {
                trackTitle.setText(track.title);
                trackArtist.setText(track.subtitle);
                isSynthPlaying = false;
            } else if (!playing) {
                applySynthTrack(0, true, true);
            }
            updatePlaybackStateUI();
        });

        initViews();
        setupTopBar();
        setupWidgets();
        setupAppCards();
        startClockTicker();
        startAudioProgressLoop();
        startSystemTelemetryLoop();
        fetchWeather();

        // Listen for newly installed or removed packages so new apps automatically appear in secondary rows
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, pkgFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, pkgFilter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWifiStatus();
        setupAppCards();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        if (audioManager != null) {
            audioManager.stop();
        }
        bgExecutor.shutdown();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void initViews() {
        clockText = findViewById(R.id.clock_text);
        bannerTitle = findViewById(R.id.banner_title);
        btnSettings = findViewById(R.id.btn_settings);
        btnWifi = findViewById(R.id.btn_wifi);
        wifiSignalView = findViewById(R.id.wifi_signal_view);
        wifiSsidText = findViewById(R.id.wifi_ssid_text);

        widgetSystemStatus = findViewById(R.id.widget_system_status);
        widgetWeather = findViewById(R.id.widget_weather);
        widgetNowPlaying = findViewById(R.id.widget_now_playing);

        weatherCityTemp = findViewById(R.id.weather_city_temp);
        weatherCondition = findViewById(R.id.weather_condition);
        weatherCyberSub = findViewById(R.id.weather_cyber_sub);
        weatherDetails = findViewById(R.id.weather_details);
        sysTelemetry = findViewById(R.id.sys_telemetry);
        sysBoostHint = findViewById(R.id.sys_boost_hint);

        btnAudioBrowse = findViewById(R.id.btn_audio_browse);
        trackTitle = findViewById(R.id.track_title);
        trackArtist = findViewById(R.id.track_artist);
        audioSpectrum = findViewById(R.id.audio_spectrum);
        audioSeekContainer = findViewById(R.id.audio_seek_container);
        audioTimeCurrent = findViewById(R.id.audio_time_current);
        audioProgressBar = findViewById(R.id.audio_progress_bar);
        audioTimeTotal = findViewById(R.id.audio_time_total);
        btnMediaPrev = findViewById(R.id.btn_media_prev);
        btnMediaPlay = findViewById(R.id.btn_media_play);
        btnMediaNext = findViewById(R.id.btn_media_next);
        audioStatusFooter = findViewById(R.id.audio_status_footer);

        gaugeCpu = findViewById(R.id.gauge_cpu);
        gaugeRam = findViewById(R.id.gauge_ram);

        widgetsTitle = findViewById(R.id.widgets_title);
        widgetsContainer = findViewById(R.id.widgets_container);
        bottomFooter = findViewById(R.id.bottom_footer);
        footerDateText = findViewById(R.id.footer_date_text);

        appsManageHint = findViewById(R.id.apps_manage_hint);
        appsRow = findViewById(R.id.apps_row);
        secondarySection = findViewById(R.id.secondary_section);
        secondaryScroll = findViewById(R.id.secondary_scroll);
        secondaryRowsContainer = findViewById(R.id.secondary_rows_container);
    }

    private void setupTopBar() {
        styleInteractiveHeaderButton(btnSettings, false);
        styleInteractiveHeaderButton(btnWifi, false);

        btnSettings.setOnFocusChangeListener((v, hasFocus) -> styleInteractiveHeaderButton(v, hasFocus));
        btnWifi.setOnFocusChangeListener((v, hasFocus) -> styleInteractiveHeaderButton(v, hasFocus));

        btnSettings.setOnClickListener(v -> openSystemSettings());
        btnWifi.setOnClickListener(v -> openWifiSettings());

        updateWifiStatus();
    }

    private void styleInteractiveHeaderButton(View v, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6f);
        if (focused) {
            bg.setColor(Color.parseColor("#44FFE600"));
            bg.setStroke(2, Color.parseColor("#FFE600"));
            animateScale(v, 1.08f);
        } else {
            bg.setColor(Color.parseColor("#181C24"));
            bg.setStroke(1, Color.parseColor("#33FFE600"));
            animateScale(v, 1.0f);
        }
        v.setBackground(bg);
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "UNABLE TO OPEN SETTINGS", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWifiSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                openSystemSettings();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void updateWifiStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        boolean connected = false;
        int level = 0; // 0..4
        String label = "OFFLINE";

        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    connected = true;
                    level = 4;
                    label = "ETH";
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    connected = true;
                    int rssi = -55;
                    String ssid = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && caps.getTransportInfo() instanceof WifiInfo) {
                        WifiInfo wi = (WifiInfo) caps.getTransportInfo();
                        rssi = wi.getRssi();
                        ssid = wi.getSSID();
                    } else if (wm != null && wm.getConnectionInfo() != null) {
                        WifiInfo wi = wm.getConnectionInfo();
                        rssi = wi.getRssi();
                        ssid = wi.getSSID();
                    }
                    if (wm != null) {
                        level = WifiManager.calculateSignalLevel(rssi, 5); // returns 0..4
                    } else {
                        level = 3;
                    }
                    if (level == 0 && connected) level = 1;
                    if (ssid != null && !ssid.equals(WifiManager.UNKNOWN_SSID)) {
                        ssid = ssid.replace("\"", "").trim();
                        if (!ssid.isEmpty()) label = ssid.toUpperCase();
                        else label = "WIFI";
                    } else {
                        label = "WIFI";
                    }
                }
            }
        }

        wifiSignalView.setSignal(connected, level);
        wifiSsidText.setText(label);
        wifiSsidText.setTextColor(connected ? Color.parseColor("#FFE600") : Color.parseColor("#FF1744"));
    }

    private void setupWidgets() {
        gaugeCpu.setTitle("CPU");
        gaugeCpu.setSubtitle("USAGE");
        gaugeCpu.setAccentColor(Color.parseColor("#FFE600"));
        gaugeCpu.setProgress(0.42f);

        gaugeRam.setTitle("RAM");
        gaugeRam.setSubtitle("USAGE");
        gaugeRam.setAccentColor(Color.parseColor("#FF1744"));
        gaugeRam.setProgress(0.60f);

        applySynthTrack(synthTrackIdx, false, true);

        styleWidgetPanel(widgetSystemStatus, false, Color.parseColor("#FFE600"));
        styleWidgetPanel(widgetWeather, false, Color.parseColor("#00E5FF"));
        styleWidgetPanel(widgetNowPlaying, false, Color.parseColor("#FF1744"));

        widgetSystemStatus.setOnFocusChangeListener((v, hasFocus) ->
                styleWidgetPanel(v, hasFocus, Color.parseColor("#FFE600")));
        widgetWeather.setOnFocusChangeListener((v, hasFocus) ->
                styleWidgetPanel(v, hasFocus, Color.parseColor("#00E5FF")));

        // 1. Click System Status -> Purge background processes & free RAM
        widgetSystemStatus.setOnClickListener(v -> performRamPurge());

        // 2. Click Weather -> Choose or search city
        widgetWeather.setOnClickListener(v -> showCityPickerDialog());

        // 3. Now Playing interactive media buttons & progress bar
        setupMediaControls();
    }

    private void applySynthTrack(int idx, boolean resetTime, boolean play) {
        synthTrackIdx = (idx + SAMURAI_SYNTH_PLAYLIST.length) % SAMURAI_SYNTH_PLAYLIST.length;
        String[] item = SAMURAI_SYNTH_PLAYLIST[synthTrackIdx];
        trackTitle.setText(item[0]);
        trackArtist.setText(item[1]);
        demoTotalSec = Integer.parseInt(item[2]);
        if (resetTime) demoProgressSec = 0;
        isSynthPlaying = play;
        updatePlaybackStateUI();
    }

    private void updatePlaybackStateUI() {
        boolean playing = isNowPlayingActive();
        if (playing) {
            btnMediaPlay.setText("⏸");
            audioSpectrum.setPlaying(true);
            if (audioManager != null && audioManager.getCurrentTrack() != null) {
                audioStatusFooter.setText("PLAYING // AUDIO ACTIVE");
            } else {
                audioStatusFooter.setText("PLAYING // SYNTH STREAM");
            }
            audioStatusFooter.setTextColor(Color.parseColor("#00E676"));
        } else {
            btnMediaPlay.setText("▶");
            audioSpectrum.setPlaying(false);
            if (audioManager != null && audioManager.getCurrentTrack() != null) {
                audioStatusFooter.setText("PAUSED // AUDIO ACTIVE");
            } else {
                audioStatusFooter.setText("PAUSED // READY");
            }
            audioStatusFooter.setTextColor(Color.parseColor("#FFE600"));
        }
        updateAudioProgressUI();
    }

    private void setupMediaControls() {
        styleMediaButton(btnAudioBrowse, false, Color.parseColor("#FF1744"));
        styleMediaButton(btnMediaPrev, false, Color.parseColor("#00E5FF"));
        styleMediaButton(btnMediaPlay, false, Color.parseColor("#FFE600"));
        styleMediaButton(btnMediaNext, false, Color.parseColor("#00E5FF"));
        styleSeekBarContainer(audioSeekContainer, false);

        View.OnFocusChangeListener mediaFocusListener = (v, hasFocus) -> {
            if (v == btnAudioBrowse) styleMediaButton(v, hasFocus, Color.parseColor("#FF1744"));
            else if (v == btnMediaPlay) styleMediaButton(v, hasFocus, Color.parseColor("#FFE600"));
            else if (v == btnMediaPrev || v == btnMediaNext) styleMediaButton(v, hasFocus, Color.parseColor("#00E5FF"));
            else if (v == audioSeekContainer) styleSeekBarContainer(v, hasFocus);

            boolean anyNowPlayingFocus = btnAudioBrowse.hasFocus() || btnMediaPrev.hasFocus()
                    || btnMediaPlay.hasFocus() || btnMediaNext.hasFocus() || audioSeekContainer.hasFocus();
            styleWidgetPanel(widgetNowPlaying, anyNowPlayingFocus, Color.parseColor("#FF1744"));
        };

        btnAudioBrowse.setOnFocusChangeListener(mediaFocusListener);
        btnMediaPrev.setOnFocusChangeListener(mediaFocusListener);
        btnMediaPlay.setOnFocusChangeListener(mediaFocusListener);
        btnMediaNext.setOnFocusChangeListener(mediaFocusListener);
        audioSeekContainer.setOnFocusChangeListener(mediaFocusListener);

        btnAudioBrowse.setOnClickListener(v -> showAudioMediaDialog());

        btnMediaPlay.setOnClickListener(v -> {
            if (audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
                audioManager.togglePlayPause();
            } else {
                isSynthPlaying = !isSynthPlaying;
                updatePlaybackStateUI();
            }
        });

        btnMediaPrev.setOnClickListener(v -> {
            if (audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
                audioManager.playPrev();
            } else {
                applySynthTrack(synthTrackIdx, true, true);
            }
        });

        btnMediaNext.setOnClickListener(v -> {
            if (audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
                audioManager.playNext();
            } else {
                applySynthTrack(synthTrackIdx, true, true);
            }
        });

        audioSeekContainer.setOnClickListener(v -> showAudioMediaDialog());
        audioSeekContainer.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (audioManager.getCurrentTrack() != null) {
                        audioManager.seekByMs(-10000);
                    } else {
                        demoProgressSec = Math.max(0, demoProgressSec - 10);
                    }
                    updateAudioProgressUI();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (audioManager.getCurrentTrack() != null) {
                        audioManager.seekByMs(10000);
                    } else {
                        demoProgressSec = Math.min(demoTotalSec, demoProgressSec + 10);
                    }
                    updateAudioProgressUI();
                    return true;
                }
            }
            return false;
        });
    }

    private void styleMediaButton(View v, boolean focused, int accent) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(4f);
        if (focused) {
            bg.setColor(Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)));
            bg.setStroke(2, accent);
            animateScale(v, 1.08f);
        } else {
            bg.setColor(Color.parseColor("#1D222D"));
            bg.setStroke(1, Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
            animateScale(v, 1.0f);
        }
        v.setBackground(bg);
    }

    private void styleSeekBarContainer(View v, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(4f);
        if (focused) {
            bg.setColor(Color.parseColor("#26FFE600"));
            bg.setStroke(1, Color.parseColor("#FFE600"));
            audioStatusFooter.setText("◀ ▶ SEEK ±10S // OK TO PICK TRACK");
            audioStatusFooter.setTextColor(Color.parseColor("#FFE600"));
        } else {
            bg.setColor(Color.TRANSPARENT);
            boolean playing = isNowPlayingActive();
            if (playing) {
                audioStatusFooter.setText(audioManager.getCurrentTrack() != null ? "PLAYING // AUDIO ACTIVE" : "PLAYING // SYNTH STREAM");
                audioStatusFooter.setTextColor(Color.parseColor("#00E676"));
            } else {
                audioStatusFooter.setText(audioManager.getCurrentTrack() != null ? "PAUSED // AUDIO ACTIVE" : "PAUSED // READY");
                audioStatusFooter.setTextColor(Color.parseColor("#FFE600"));
            }
        }
        v.setBackground(bg);
    }

    private void startAudioProgressLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (audioManager != null && audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
                    updateAudioProgressUI();
                } else if (isSynthPlaying) {
                    demoProgressSec++;
                    if (demoProgressSec >= demoTotalSec) {
                        demoProgressSec = 0; // Continuous seamless looping animation
                    }
                    updateAudioProgressUI();
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void updateAudioProgressUI() {
        if (audioManager.getCurrentTrack() != null && audioManager.isPlaying()) {
            int posMs = audioManager.getCurrentPositionMs();
            int durMs = audioManager.getDurationMs();
            audioTimeCurrent.setText(formatMs(posMs));
            audioTimeTotal.setText(formatMs(durMs));
            if (durMs > 0) {
                int p = (int) ((posMs * 1000L) / durMs);
                audioProgressBar.setProgress(Math.max(0, Math.min(1000, p)));
            } else {
                audioProgressBar.setProgress(350);
            }
        } else {
            audioTimeCurrent.setText(formatMs(demoProgressSec * 1000));
            audioTimeTotal.setText(formatMs(demoTotalSec * 1000));
            int p = (int) ((demoProgressSec * 1000L) / Math.max(1, demoTotalSec));
            audioProgressBar.setProgress(Math.max(0, Math.min(1000, p)));
        }
    }

    private String formatMs(int ms) {
        int totalSec = Math.max(0, ms / 1000);
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void styleWidgetPanel(View panel, boolean focused, int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6f);
        if (focused) {
            bg.setColor(Color.parseColor("#1D222D"));
            bg.setStroke(2, accentColor);
            animateScale(panel, 1.02f);
        } else {
            bg.setColor(Color.parseColor("#14171E"));
            bg.setStroke(1, Color.parseColor("#2B323D"));
            animateScale(panel, 1.0f);
        }
        panel.setBackground(bg);
    }

    private void performRamPurge() {
        sysBoostHint.setText("[ PURGING... ]");
        sysBoostHint.setTextColor(Color.parseColor("#00E676"));

        bgExecutor.execute(() -> {
            SystemMonitor.RamCleanResult res = systemMonitor.freeRam();
            runOnUiThread(() -> {
                SystemMonitor.HardwareStats stats = systemMonitor.getStats();
                gaugeCpu.setProgress(stats.cpuPercent);
                gaugeRam.setProgress(stats.ramPercent);
                sysTelemetry.setText(stats.ramTelemetry + " · " + stats.cpuTelemetry);

                double mb = res.freedBytes / (1024.0 * 1024.0);
                String msg;
                if (res.isDaemonClean && res.killedPackages > 0) {
                    msg = String.format(Locale.US, "[SAMURAI_OS]: PURGED %d TASKS // FREED %.0f MB RAM", res.killedPackages, mb);
                } else if (res.isDaemonClean && mb >= 1.0) {
                    msg = String.format(Locale.US, "[SAMURAI_OS]: CACHE SWEPT // FREED %.0f MB RAM", mb);
                } else if (res.isDaemonClean) {
                    msg = "[SAMURAI_OS]: SYSTEM PURGED // RAM ALREADY OPTIMIZED";
                } else if (mb >= 1.0) {
                    msg = String.format(Locale.US, "[SAMURAI_OS]: APP HEAP CLEARED // FREED %.0f MB RAM", mb);
                } else {
                    msg = "[SAMURAI_OS]: MEMORY OPTIMIZED // CACHES FLUSHED";
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                handler.postDelayed(() -> {
                    sysBoostHint.setText("[ PURGE RAM ]");
                    sysBoostHint.setTextColor(Color.parseColor("#FFE600"));
                }, 2500);
            });
        });
    }

    private void showCityPickerDialog() {
        final String[] presets = new String[]{
                "🔍 SEARCH ANY CITY BY NAME...",
                "TOKYO // JAPAN (35.6762, 139.6503)",
                "BERGEN // NORWAY (60.3913, 5.3221)",
                "LONDON // UK (51.5074, -0.1278)",
                "NEW YORK // USA (40.7128, -74.0060)",
                "CAIRO // EGYPT (30.0444, 31.2357)",
                "DUBAI // UAE (25.2048, 55.2708)",
                "OSLO // NORWAY (59.9139, 10.7522)"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("WEATHER TELEMETRY // SELECT CITY")
                .setItems(presets, (dialog, which) -> {
                    if (which == 0) {
                        showCustomCitySearchDialog();
                    } else if (which == 1) {
                        applyCity("TOKYO", "35.6762", "139.6503");
                    } else if (which == 2) {
                        applyCity("BERGEN", "60.3913", "5.3221");
                    } else if (which == 3) {
                        applyCity("LONDON", "51.5074", "-0.1278");
                    } else if (which == 4) {
                        applyCity("NEW YORK", "40.7128", "-74.0060");
                    } else if (which == 5) {
                        applyCity("CAIRO", "30.0444", "31.2357");
                    } else if (which == 6) {
                        applyCity("DUBAI", "25.2048", "55.2708");
                    } else if (which == 7) {
                        applyCity("OSLO", "59.9139", "10.7522");
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showCustomCitySearchDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter city name (e.g. Oslo, Stockholm, Tokyo)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("SEARCH CITY // SATELLITE GEOCODER")
                .setView(input)
                .setPositiveButton("SEARCH", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) {
                        Toast.makeText(this, "SCANNING SATELLITE FOR " + q.toUpperCase() + "...", Toast.LENGTH_SHORT).show();
                        weather.searchCity(q, results -> {
                            if (results.isEmpty()) {
                                Toast.makeText(this, "NO COORDINATES FOUND FOR: " + q, Toast.LENGTH_SHORT).show();
                            } else if (results.size() == 1) {
                                Weather.CitySearchResult r = results.get(0);
                                applyCity(r.name, r.lat, r.lng);
                            } else {
                                String[] labels = new String[results.size()];
                                for (int i = 0; i < results.size(); i++) {
                                    Weather.CitySearchResult r = results.get(i);
                                    labels[i] = r.name + (r.country.isEmpty() ? "" : " (" + r.country + ")")
                                            + " [" + r.lat + ", " + r.lng + "]";
                                }
                                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle("SELECT MATCHING LOCATION")
                                        .setItems(labels, (d2, idx) -> {
                                            Weather.CitySearchResult chosen = results.get(idx);
                                            applyCity(chosen.name, chosen.lat, chosen.lng);
                                        })
                                        .show();
                            }
                        });
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void applyCity(String label, String lat, String lng) {
        config.setLocation(label, lat, lng);
        weatherCityTemp.setText(label + " / ...");
        fetchWeather();
        Toast.makeText(this, "WEATHER LINKED TO: " + label, Toast.LENGTH_SHORT).show();
    }

    private void showAudioMediaDialog() {
        List<AudioMediaManager.AudioTrack> tracks = audioManager.scanAudioFiles();
        List<String> options = new ArrayList<>();

        options.add("BROWSE SYSTEM / USB FILE PICKER...");
        options.add("[SAMURAI FM] CHIPPIN' IN (SAMURAI // NIGHT CITY FM 107.3) [ANIMATION DEMO ONLY]");

        for (AudioMediaManager.AudioTrack t : tracks) {
            options.add("[LOCAL/USB] " + t.title + "  [" + t.subtitle + "]");
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("SAMURAI AUDIO DECK // SELECT TRACK OR USB MEDIA")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        openSystemAudioFilePicker();
                        return;
                    }
                    if (which == 1) {
                        audioManager.stop();
                        applySynthTrack(0, true, true);
                        return;
                    }
                    int trackIdx = which - 2;
                    if (trackIdx >= 0 && trackIdx < tracks.size()) {
                        AudioMediaManager.AudioTrack chosen = tracks.get(trackIdx);
                        isSynthPlaying = false;
                        audioManager.playTrack(chosen);
                        updatePlaybackStateUI();
                        Toast.makeText(this, "PLAYING: " + chosen.title, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void openSystemAudioFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "SELECT AUDIO FILE"), PICK_AUDIO_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "NO FILE PICKER APP FOUND — INSERT USB WITH MP3/WAV/FLAC", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String seg = uri.getLastPathSegment();
            String name = seg != null ? seg.toUpperCase() : "EXTERNAL AUDIO SHARD";
            AudioMediaManager.AudioTrack track = new AudioMediaManager.AudioTrack(name, "EXTERNAL / USB STREAM", uri, null);
            isSynthPlaying = false;
            audioManager.playTrack(track);
            updatePlaybackStateUI();
        }
    }

    private void showSecondaryDrawer(boolean show) {
        if (show) {
            if (secondarySection.getVisibility() != View.VISIBLE) {
                if (widgetsTitle != null) widgetsTitle.setVisibility(View.GONE);
                if (widgetsContainer != null) widgetsContainer.setVisibility(View.GONE);
                if (appsRow != null) appsRow.setVisibility(View.GONE);
                secondarySection.setVisibility(View.VISIBLE);
                appsManageHint.setText("▲ PRESS UP TO RETURN TO MAIN LIST  ·  HOLD OK TO REORDER");
            }
        } else {
            if (secondarySection.getVisibility() == View.VISIBLE) {
                secondarySection.setVisibility(View.GONE);
                if (widgetsTitle != null) widgetsTitle.setVisibility(View.VISIBLE);
                if (widgetsContainer != null) widgetsContainer.setVisibility(View.VISIBLE);
                if (appsRow != null) {
                    appsRow.setVisibility(View.VISIBLE);
                    ViewGroup.LayoutParams lp = appsRow.getLayoutParams();
                    lp.height = dpToPx(176);
                    appsRow.setLayoutParams(lp);
                    for (View c : cardViews) {
                        View t = c.findViewById(R.id.card_telemetry);
                        if (t != null) t.setVisibility(View.VISIBLE);
                    }
                }
                appsManageHint.setText("▼ SCROLL DOWN FOR MORE APPS  ·  HOLD OK TO REORDER");
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupAppCards() {
        appList.clear();
        appList.addAll(AppLoader.loadDockApps(this));

        secondaryAppList.clear();
        secondaryAppList.addAll(AppLoader.loadSecondaryApps(this));

        appsRow.removeAllViews();
        cardViews.clear();

        secondaryRowsContainer.removeAllViews();
        secondaryCardViews.clear();

        LayoutInflater inflater = LayoutInflater.from(this);

        // 1. Primary Focused Data Shards Row (Max 6 per row)
        int primaryCount = Math.min(COLS_PER_ROW, appList.size());
        for (int i = 0; i < primaryCount; i++) {
            final int index = i;
            final AppEntry app = appList.get(i);
            View card = inflater.inflate(R.layout.item_app_card, appsRow, false);

            TextView subtitle = card.findViewById(R.id.card_subtitle);
            TextView title = card.findViewById(R.id.card_title);
            TextView action = card.findViewById(R.id.card_action);
            TextView telemetry = card.findViewById(R.id.card_telemetry);
            ImageView icon = card.findViewById(R.id.card_icon);

            subtitle.setText(app.subtitle);
            title.setText(app.title);
            action.setText(app.actionLabel);

            if (app.iconResId != 0) {
                icon.setImageResource(app.iconResId);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            if (index == 0) {
                telemetry.setText("[DATA_SHARD]:: ENCRYPTED\nNET_LINK: SYNCHRONIZED");
            } else if (index == 1) {
                telemetry.setText("[STREAM]:: 4K HDR MATRIX\nBANDWIDTH: 45.2 MBPS");
            } else if (index == 2) {
                telemetry.setText("[AUDIO]:: LOSSLESS FLAC\nBUFFER: 100% OK");
            } else if (index == 3) {
                telemetry.setText("[FEED]:: BROADCAST LIVE\nLATENCY: 8 MS");
            } else if (index == 4) {
                telemetry.setText("[MEDIA_SERVER]:: CONNECTED\nPORT 32400 SECURED");
            } else {
                telemetry.setText("[CYBER_NET]:: GATEWAY ONLINE\nPROXY: NIGHT_CITY_VPN");
            }

            card.setFocusable(true);
            card.setClickable(true);
            card.setLongClickable(true);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                applyCardFocus(v, app, hasFocus);
                if (hasFocus) {
                    showSecondaryDrawer(false);
                }
            });
            card.setOnClickListener(v -> launchApp(app));
            card.setOnLongClickListener(v -> {
                showAppMoveMenu(app, index, true);
                return true;
            });

            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (!secondaryCardViews.isEmpty()) {
                            final int targetIdx = Math.min(index, Math.min(COLS_PER_ROW - 1, secondaryCardViews.size() - 1));
                            showSecondaryDrawer(true);
                            secondaryCardViews.get(targetIdx).post(() -> secondaryCardViews.get(targetIdx).requestFocus());
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                        cardViews.get(index - 1).requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index < cardViews.size() - 1) {
                        cardViews.get(index + 1).requestFocus();
                        return true;
                    }
                }
                return false;
            });

            appsRow.addView(card);
            cardViews.add(card);
            applyCardFocus(card, app, false);
        }

        // 2. Secondary Multi-Row Grid (Max 6 apps per row; new rows added automatically)
        int totalSec = secondaryAppList.size();
        int numRows = (totalSec + COLS_PER_ROW - 1) / COLS_PER_ROW;

        for (int r = 0; r < numRows; r++) {
            LinearLayout rowLayout = new LinearLayout(this);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(68));
            rowLp.bottomMargin = dpToPx(4);
            rowLayout.setLayoutParams(rowLp);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setClipChildren(false);
            rowLayout.setClipToPadding(false);
            rowLayout.setWeightSum(COLS_PER_ROW);

            int startIdx = r * COLS_PER_ROW;
            int endIdx = Math.min(startIdx + COLS_PER_ROW, totalSec);

            for (int i = startIdx; i < endIdx; i++) {
                final int index = i;
                final int rowIdx = r;
                final int colIdx = i - startIdx;
                final AppEntry app = secondaryAppList.get(i);
                View card = inflater.inflate(R.layout.item_secondary_card, rowLayout, false);

                TextView subtitle = card.findViewById(R.id.sec_card_subtitle);
                TextView title = card.findViewById(R.id.sec_card_title);
                TextView action = card.findViewById(R.id.sec_card_action);
                ImageView icon = card.findViewById(R.id.sec_card_icon);

                subtitle.setText(app.subtitle);
                title.setText(app.title);
                action.setText(app.isAddButton ? "MANAGE SHARDS" : "OPEN // HOLD: ▲ TOP");

                if (app.iconResId != 0) {
                    icon.setImageResource(app.iconResId);
                    icon.setVisibility(View.VISIBLE);
                } else {
                    icon.setVisibility(View.GONE);
                }

                card.setFocusable(true);
                card.setClickable(true);
                card.setLongClickable(true);

                card.setOnFocusChangeListener((v, hasFocus) -> {
                    applySecondaryCardFocus(v, app, hasFocus);
                    if (hasFocus) {
                        showSecondaryDrawer(true);
                        v.post(() -> {
                            Rect rect = new Rect();
                            v.getDrawingRect(rect);
                            secondaryScroll.offsetDescendantRectToMyCoords(v, rect);
                            rect.top -= dpToPx(8);
                            rect.bottom += dpToPx(28);
                            secondaryScroll.requestChildRectangleOnScreen(v, rect, false);
                        });
                    }
                });
                card.setOnClickListener(v -> {
                    if (app.isAddButton) {
                        showManageAllAppsDialog();
                    } else {
                        launchApp(app);
                    }
                });
                card.setOnLongClickListener(v -> {
                    if (app.isAddButton) {
                        showManageAllAppsDialog();
                    } else {
                        showAppMoveMenu(app, index, false);
                    }
                    return true;
                });

                card.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (rowIdx > 0) {
                                int upIdx = (rowIdx - 1) * COLS_PER_ROW + colIdx;
                                if (upIdx < secondaryCardViews.size()) {
                                    secondaryCardViews.get(upIdx).requestFocus();
                                    return true;
                                }
                            } else if (!cardViews.isEmpty()) {
                                final int targetIdx = Math.min(colIdx, cardViews.size() - 1);
                                showSecondaryDrawer(false);
                                cardViews.get(targetIdx).post(() -> cardViews.get(targetIdx).requestFocus());
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            int downRowStart = (rowIdx + 1) * COLS_PER_ROW;
                            if (downRowStart < secondaryCardViews.size()) {
                                int downIdx = Math.min(downRowStart + colIdx, secondaryCardViews.size() - 1);
                                secondaryCardViews.get(downIdx).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                            secondaryCardViews.get(index - 1).requestFocus();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index < secondaryCardViews.size() - 1) {
                            secondaryCardViews.get(index + 1).requestFocus();
                            return true;
                        }
                    }
                    return false;
                });

                rowLayout.addView(card);
                secondaryCardViews.add(card);
                applySecondaryCardFocus(card, app, false);
            }

            // Fill remaining empty slots in last row with invisible spacers so 6-column widths stay uniform
            int itemsInRow = endIdx - startIdx;
            for (int s = itemsInRow; s < COLS_PER_ROW; s++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                spLp.setMargins(dpToPx(5), dpToPx(4), dpToPx(5), dpToPx(4));
                spacer.setLayoutParams(spLp);
                spacer.setVisibility(View.INVISIBLE);
                rowLayout.addView(spacer);
            }

            secondaryRowsContainer.addView(rowLayout);
        }

        if (getCurrentFocus() == null && !cardViews.isEmpty()) {
            cardViews.get(0).requestFocus();
        }
    }

    private void showAppMoveMenu(AppEntry app, int index, boolean isPrimaryRow) {
        List<String> actions = new ArrayList<>();
        actions.add("🚀 LAUNCH " + app.title);
        if (isPrimaryRow) {
            actions.add("⭐ MAKE FIRST (YELLOW HERO DATA SHARD)");
            actions.add("◀ MOVE LEFT IN FOCUSED ROW");
            actions.add("▶ MOVE RIGHT IN FOCUSED ROW");
            actions.add("▼ MOVE DOWN TO SECONDARY ROW");
        } else {
            actions.add("▲ MOVE UP TO FOCUSED DATA SHARDS ROW (MAX 6)");
            actions.add("⭐ PROMOTE TO #1 YELLOW HERO DATA SHARD");
            actions.add("◀ MOVE LEFT IN SECONDARY MATRIX");
            actions.add("▶ MOVE RIGHT IN SECONDARY MATRIX");
        }
        actions.add("🎨 CHANGE SHARD ACCENT COLOR");
        actions.add("➕ MANAGE / ADD INSTALLED APPS...");

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("DATA SHARD CONTROL // " + app.title)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    List<String> dock = AppLoader.getDockPackageList(this);
                    List<String> sec = AppLoader.getSecondaryPackageList(this);

                    if (which == 0) {
                        launchApp(app);
                        return;
                    }

                    boolean stayInSecondary = !isPrimaryRow;
                    int targetDockIdx = 0;
                    int targetSecIdx = index;

                    if (isPrimaryRow) {
                        if (which == 1) {
                            dock.remove(app.packageName);
                            dock.add(0, app.packageName);
                            targetDockIdx = 0;
                            stayInSecondary = false;
                        } else if (which == 2 && index > 0) {
                            dock.remove(index);
                            dock.add(index - 1, app.packageName);
                            targetDockIdx = index - 1;
                            stayInSecondary = false;
                        } else if (which == 3 && index < dock.size() - 1) {
                            dock.remove(index);
                            dock.add(index + 1, app.packageName);
                            targetDockIdx = index + 1;
                            stayInSecondary = false;
                        } else if (which == 4) {
                            if (dock.size() <= 1) {
                                Toast.makeText(this, "KEEP AT LEAST 1 SHARD IN FOCUSED ROW", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            dock.remove(app.packageName);
                            if (!sec.contains(app.packageName)) sec.add(0, app.packageName);
                            stayInSecondary = true;
                            targetSecIdx = 0;
                        } else if (which == 5) {
                            showColorPickerDialog(app, index, true);
                            return;
                        } else if (which == 6) {
                            showManageAllAppsDialog();
                            return;
                        }
                    } else {
                        if (which == 1) {
                            sec.remove(app.packageName);
                            if (!dock.contains(app.packageName)) dock.add(app.packageName);
                            while (dock.size() > COLS_PER_ROW) {
                                String overflow = dock.remove(dock.size() - 1);
                                if (!sec.contains(overflow)) sec.add(0, overflow);
                            }
                            targetDockIdx = dock.indexOf(app.packageName);
                            stayInSecondary = false;
                        } else if (which == 2) {
                            sec.remove(app.packageName);
                            dock.remove(app.packageName);
                            dock.add(0, app.packageName);
                            while (dock.size() > COLS_PER_ROW) {
                                String overflow = dock.remove(dock.size() - 1);
                                if (!sec.contains(overflow)) sec.add(0, overflow);
                            }
                            targetDockIdx = 0;
                            stayInSecondary = false;
                        } else if (which == 3) {
                            int target = Math.max(0, index - 1);
                            if (index > 0 && index < sec.size()) {
                                sec.remove(index);
                                sec.add(target, app.packageName);
                            }
                            stayInSecondary = true;
                            targetSecIdx = target;
                        } else if (which == 4) {
                            int target = Math.min(sec.size() - 1, index + 1);
                            if (index < sec.size() - 1) {
                                sec.remove(index);
                                sec.add(target, app.packageName);
                            }
                            stayInSecondary = true;
                            targetSecIdx = target;
                        } else if (which == 5) {
                            showColorPickerDialog(app, index, false);
                            return;
                        } else if (which == 6) {
                            showManageAllAppsDialog();
                            return;
                        }
                    }

                    AppLoader.saveDockPackageList(this, dock);
                    AppLoader.saveSecondaryPackageList(this, sec);
                    setupAppCards();

                    if (stayInSecondary) {
                        showSecondaryDrawer(true);
                        final int fSec = Math.max(0, Math.min(targetSecIdx, secondaryCardViews.size() - 1));
                        if (!secondaryCardViews.isEmpty()) {
                            secondaryCardViews.get(fSec).post(() -> secondaryCardViews.get(fSec).requestFocus());
                        }
                    } else {
                        showSecondaryDrawer(false);
                        final int fDock = Math.max(0, Math.min(targetDockIdx, cardViews.size() - 1));
                        if (!cardViews.isEmpty()) {
                            cardViews.get(fDock).post(() -> cardViews.get(fDock).requestFocus());
                        }
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showColorPickerDialog(AppEntry app, int index, boolean isPrimaryRow) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CHOOSE ACCENT COLOR // " + app.title)
                .setItems(AppLoader.COLOR_NAMES, (d, which) -> {
                    int chosenColor = AppLoader.COLOR_VALUES[which];
                    AppLoader.setAppCustomColor(this, app.packageName, chosenColor);
                    setupAppCards();
                    if (isPrimaryRow) {
                        showSecondaryDrawer(false);
                        final int fIdx = Math.max(0, Math.min(index, cardViews.size() - 1));
                        if (!cardViews.isEmpty()) {
                            cardViews.get(fIdx).post(() -> cardViews.get(fIdx).requestFocus());
                        }
                    } else {
                        showSecondaryDrawer(true);
                        final int fIdx = Math.max(0, Math.min(index, secondaryCardViews.size() - 1));
                        if (!secondaryCardViews.isEmpty()) {
                            secondaryCardViews.get(fIdx).post(() -> secondaryCardViews.get(fIdx).requestFocus());
                        }
                    }
                    Toast.makeText(this, "ACCENT COLOR UPDATED FOR " + app.title, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showManageAllAppsDialog() {
        Map<String, ResolveInfo> allApps = AppLoader.queryAllLaunchableApps(this);
        List<String> dock = AppLoader.getDockPackageList(this);
        PackageManager pm = getPackageManager();

        final List<String> pkgKeys = new ArrayList<>(allApps.keySet());
        String[] labels = new String[pkgKeys.size()];
        boolean[] checked = new boolean[pkgKeys.size()];

        for (int i = 0; i < pkgKeys.size(); i++) {
            String pkg = pkgKeys.get(i);
            ResolveInfo ri = allApps.get(pkg);
            CharSequence labelCs = ri != null ? ri.loadLabel(pm) : pkg;
            String label = labelCs != null ? labelCs.toString().toUpperCase() : pkg;
            checked[i] = dock.contains(pkg);
            labels[i] = label + (checked[i] ? "  [FOCUSED ROW]" : "  [SECONDARY ROW]");
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CHOOSE UP TO 6 APPS FOR FOCUSED ROW (OTHERS GO TO 6-PER-ROW SECONDARY MATRIX)")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("APPLY MATRIX", (dialog, which) -> {
                    List<String> newDock = new ArrayList<>();
                    List<String> newSec = new ArrayList<>();

                    for (String p : dock) {
                        int idx = pkgKeys.indexOf(p);
                        if (idx >= 0 && checked[idx] && newDock.size() < COLS_PER_ROW) {
                            newDock.add(p);
                        }
                    }
                    for (int i = 0; i < pkgKeys.size(); i++) {
                        String p = pkgKeys.get(i);
                        if (checked[i] && !newDock.contains(p) && newDock.size() < COLS_PER_ROW) {
                            newDock.add(p);
                        } else if (!newDock.contains(p)) {
                            newSec.add(p);
                        }
                    }
                    if (newDock.isEmpty() && !pkgKeys.isEmpty()) {
                        newDock.add(pkgKeys.get(0));
                        newSec.remove(pkgKeys.get(0));
                    }
                    AppLoader.saveDockPackageList(this, newDock);
                    AppLoader.saveSecondaryPackageList(this, newSec);
                    setupAppCards();
                    if (!cardViews.isEmpty()) cardViews.get(0).requestFocus();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void applyCardFocus(View card, AppEntry app, boolean isFocused) {
        View inner = card.findViewById(R.id.card_inner);
        TextView subtitle = card.findViewById(R.id.card_subtitle);
        TextView title = card.findViewById(R.id.card_title);
        TextView action = card.findViewById(R.id.card_action);
        TextView telemetry = card.findViewById(R.id.card_telemetry);
        FrameLayout glitch = card.findViewById(R.id.card_glitch_block);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6f);

        if (isFocused) {
            card.setTranslationZ(10f);
            if (app.accentColor == Color.parseColor("#FFE600")) {
                bg.setColor(Color.parseColor("#FFE600"));
                bg.setStroke(2, Color.WHITE);

                subtitle.setTextColor(Color.parseColor("#090B0E"));
                title.setTextColor(Color.parseColor("#090B0E"));
                action.setTextColor(Color.parseColor("#090B0E"));
                telemetry.setTextColor(Color.parseColor("#222222"));
                glitch.setVisibility(View.VISIBLE);
            } else {
                bg.setColor(Color.argb(65, Color.red(app.accentColor), Color.green(app.accentColor), Color.blue(app.accentColor)));
                bg.setStroke(2, app.accentColor);

                subtitle.setTextColor(Color.WHITE);
                title.setTextColor(app.accentColor);
                action.setTextColor(app.accentColor);
                telemetry.setTextColor(Color.parseColor("#CCCCCC"));
                glitch.setVisibility(View.GONE);
            }
            animateScale(card, 1.03f);
        } else {
            card.setTranslationZ(0f);
            bg.setColor(Color.parseColor("#11141A"));
            bg.setStroke(1, Color.argb(120, Color.red(app.accentColor), Color.green(app.accentColor), Color.blue(app.accentColor)));

            subtitle.setTextColor(Color.parseColor("#94A3B8"));
            title.setTextColor(app.accentColor);
            action.setTextColor(Color.parseColor("#94A3B8"));
            telemetry.setTextColor(Color.parseColor("#475569"));
            glitch.setVisibility(View.GONE);

            animateScale(card, 1.0f);
        }

        inner.setBackground(bg);
    }

    private void applySecondaryCardFocus(View card, AppEntry app, boolean isFocused) {
        View inner = card.findViewById(R.id.sec_card_inner);
        TextView subtitle = card.findViewById(R.id.sec_card_subtitle);
        TextView title = card.findViewById(R.id.sec_card_title);
        TextView action = card.findViewById(R.id.sec_card_action);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(5f);

        if (isFocused) {
            card.setTranslationZ(10f);
            bg.setColor(Color.argb(60, Color.red(app.accentColor), Color.green(app.accentColor), Color.blue(app.accentColor)));
            bg.setStroke(2, app.accentColor);
            subtitle.setTextColor(Color.WHITE);
            title.setTextColor(app.accentColor);
            action.setTextColor(Color.parseColor("#FFE600"));
            animateScale(card, 1.03f);
        } else {
            card.setTranslationZ(0f);
            bg.setColor(Color.parseColor("#12151C"));
            bg.setStroke(1, Color.argb(95, Color.red(app.accentColor), Color.green(app.accentColor), Color.blue(app.accentColor)));
            subtitle.setTextColor(Color.parseColor("#64748B"));
            title.setTextColor(app.accentColor);
            action.setTextColor(Color.parseColor("#475569"));
            animateScale(card, 1.0f);
        }

        inner.setBackground(bg);
    }

    private void animateScale(View view, float scale) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", scale);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", scale);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(150);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void launchApp(AppEntry app) {
        if (app.launchIntent != null) {
            try {
                app.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(app.launchIntent);
            } catch (Exception e) {
                Toast.makeText(this, "LAUNCH FAILED // " + app.title, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "[SAMURAI_OS]: MOUNTING " + app.title + " // DATA_SHARD", Toast.LENGTH_SHORT).show();
        }
    }

    private void startClockTicker() {
        handler.post(new Runnable() {
            private int counter = 0;

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                String formatted = clockFormat.format(new Date(now));
                clockText.setText(formatted);

                if (footerDateText != null && (counter++ % 20 == 0)) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(now);
                    int week = cal.get(Calendar.WEEK_OF_YEAR);
                    String dateFormatted = footerDateFormat.format(new Date(now)).toUpperCase(Locale.US) + " // WEEK " + week;
                    footerDateText.setText(dateFormatted);
                }

                handler.postDelayed(this, 50);
            }
        });
    }

    private void startSystemTelemetryLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                SystemMonitor.HardwareStats stats = systemMonitor.getStats();
                gaugeCpu.setProgress(stats.cpuPercent);
                gaugeRam.setProgress(stats.ramPercent);
                sysTelemetry.setText(stats.ramTelemetry + " · " + stats.cpuTelemetry);
                updateWifiStatus();

                handler.postDelayed(this, 2500);
            }
        });
    }

    private void fetchWeather() {
        weather.fetch(config.cityLabel, config.lat, config.lng, config.units, data -> {
            weatherCityTemp.setText(data.cityLabel + " / " + data.temperature + "°C");
            weatherCondition.setText(data.condition);
            if (data.cyberTelemetry != null) {
                weatherCyberSub.setText(data.cyberTelemetry);
            }
            weatherDetails.setText(data.hazardDetails);
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (secondarySection.getVisibility() == View.VISIBLE) {
                showSecondaryDrawer(false);
                if (!cardViews.isEmpty()) cardViews.get(0).requestFocus();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null) {
                int idx1 = cardViews.indexOf(focused);
                if (idx1 >= 0) {
                    showAppMoveMenu(appList.get(idx1), idx1, true);
                    return true;
                }
                int idx2 = secondaryCardViews.indexOf(focused);
                if (idx2 >= 0) {
                    AppEntry e = secondaryAppList.get(idx2);
                    if (e.isAddButton) showManageAllAppsDialog();
                    else showAppMoveMenu(e, idx2, false);
                    return true;
                }
            }
            showManageAllAppsDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
