package tv.samurai.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppLoader {
    private static final String PREFS_NAME = "samurai_prefs";
    private static final String KEY_DOCK_PKGS = "dock_packages_list";
    private static final String KEY_SECONDARY_PKGS = "secondary_packages_list";

    public static final String[] COLOR_NAMES = new String[]{
            "🟡 CYBER YELLOW (#FFE600)",
            "🔴 NEON RED (#FF1744)",
            "🔵 CYBER CYAN (#00E5FF)",
            "🟢 MATRIX GREEN (#00E676)",
            "🟣 NETRUNNER PURPLE (#A855F7)",
            "🟠 NIGHT CITY ORANGE (#FF9100)",
            "🌸 NEON PINK (#FF007F)",
            "💎 ELECTRIC BLUE (#3B82F6)",
            "⚪ MILITECH WHITE (#F8FAFC)",
            "🔄 RESET TO DEFAULT"
    };

    public static final int[] COLOR_VALUES = new int[]{
            Color.parseColor("#FFE600"),
            Color.parseColor("#FF1744"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#00E676"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#FF9100"),
            Color.parseColor("#FF007F"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#F8FAFC"),
            0
    };

    public static int getAppCustomColor(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt("custom_color_" + pkg, 0);
    }

    public static void setAppCustomColor(Context context, String pkg, int color) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (color == 0) {
            prefs.edit().remove("custom_color_" + pkg).apply();
        } else {
            prefs.edit().putInt("custom_color_" + pkg, color).apply();
        }
    }

    private static final int[] ACCENT_COLORS = new int[]{
            Color.parseColor("#FFE600"),
            Color.parseColor("#FF1744"),
            Color.parseColor("#00E676"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#FF9100"),
            Color.parseColor("#00E5FF")
    };

    private static final int[] DEFAULT_ICONS = new int[]{
            0,
            R.drawable.ic_youtube,
            R.drawable.ic_spotify,
            R.drawable.ic_twitch,
            R.drawable.ic_plex,
            R.drawable.ic_cybernet
    };

    public static List<String> getDockPackageList(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_DOCK_PKGS, null);
        if (saved != null && !saved.trim().isEmpty()) {
            return new ArrayList<>(Arrays.asList(saved.split(",")));
        }
        String[] defaults = context.getResources().getStringArray(R.array.dock_packages);
        return new ArrayList<>(Arrays.asList(defaults));
    }

    public static void saveDockPackageList(Context context, List<String> pkgs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DOCK_PKGS, joinList(pkgs)).apply();
    }

    public static List<String> getSecondaryPackageList(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_SECONDARY_PKGS, null);
        List<String> dock = getDockPackageList(context);
        Map<String, ResolveInfo> allInstalled = queryAllLaunchableApps(context);

        Set<String> result = new LinkedHashSet<>();
        if (saved != null && !saved.trim().isEmpty()) {
            for (String p : saved.split(",")) {
                if (!p.isEmpty() && !dock.contains(p) && allInstalled.containsKey(p)) {
                    result.add(p);
                }
            }
        }
        // Automatically include any other installed launchable TV apps so nothing is hidden unless in primary dock
        for (String p : allInstalled.keySet()) {
            if (!dock.contains(p)) {
                result.add(p);
            }
        }
        return new ArrayList<>(result);
    }

    public static void saveSecondaryPackageList(Context context, List<String> pkgs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SECONDARY_PKGS, joinList(pkgs)).apply();
    }

    private static String joinList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public static Map<String, ResolveInfo> queryAllLaunchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> map = new LinkedHashMap<>();

        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(leanback, 0)) {
            if (ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(context.getPackageName()) && !pkg.equals("tv.widgetv.home")) {
                    map.put(pkg, ri);
                }
            }
        }

        Intent regular = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(regular, 0)) {
            if (ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(context.getPackageName()) && !pkg.equals("tv.widgetv.home") && !map.containsKey(pkg)) {
                    map.put(pkg, ri);
                }
            }
        }
        return map;
    }

    public static List<AppEntry> loadDockApps(Context context) {
        List<AppEntry> apps = new ArrayList<>();
        List<String> dockPkgs = getDockPackageList(context);
        for (int i = 0; i < dockPkgs.size(); i++) {
            apps.add(buildEntry(context, dockPkgs.get(i), i, true));
        }
        return apps;
    }

    public static List<AppEntry> loadSecondaryApps(Context context) {
        List<AppEntry> apps = new ArrayList<>();
        List<String> secPkgs = getSecondaryPackageList(context);
        for (int i = 0; i < secPkgs.size(); i++) {
            apps.add(buildEntry(context, secPkgs.get(i), i, false));
        }
        // Add "+ ADD / MANAGE APPS" card at the end of secondary row
        AppEntry addBtn = new AppEntry("__ADD_APPS__", "+ ADD APPS", "APP MATRIX // MANAGE", "CONFIGURE",
                Color.parseColor("#FFE600"), R.drawable.ic_cybernet, null);
        addBtn.isAddButton = true;
        apps.add(addBtn);
        return apps;
    }

    public static AppEntry buildEntry(Context context, String pkg, int index, boolean isPrimaryDock) {
        PackageManager pm = context.getPackageManager();
        Resources res = context.getResources();

        String[] defaultPkgs = res.getStringArray(R.array.dock_packages);
        String[] defaultLabels = res.getStringArray(R.array.dock_labels);
        String[] defaultActions = res.getStringArray(R.array.dock_actions);

        String subtitle = null;
        String action = isPrimaryDock ? "LOAD DATA" : "OPEN SHARD";

        for (int i = 0; i < defaultPkgs.length; i++) {
            if (defaultPkgs[i].equals(pkg)) {
                if (i < defaultLabels.length) subtitle = defaultLabels[i];
                if (i < defaultActions.length) action = defaultActions[i];
                break;
            }
        }

        String appLabel = pkg;
        try {
            CharSequence cs = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (cs != null) appLabel = cs.toString().toUpperCase();
        } catch (Exception ignored) {
        }

        String title = appLabel;
        if (subtitle != null) {
            int slashIdx = subtitle.indexOf("//");
            if (slashIdx > 0) {
                title = subtitle.substring(0, slashIdx).trim();
            }
            if (isPrimaryDock && index == 0 && !subtitle.contains("DATA_SHARD")) {
                subtitle = title + " // DATA_SHARD";
            }
        } else {
            subtitle = isPrimaryDock && index == 0 ? (title + " // DATA_SHARD") : (title + " //");
        }

        Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launchIntent == null) {
            launchIntent = pm.getLaunchIntentForPackage(pkg);
        }

        int accentColor = ACCENT_COLORS[index % ACCENT_COLORS.length];
        if (!isPrimaryDock && index == 0) {
            accentColor = Color.parseColor("#00E5FF");
        }
        int customColor = getAppCustomColor(context, pkg);
        if (customColor != 0) {
            accentColor = customColor;
        }
        int iconResId = resolveIcon(pkg, isPrimaryDock ? index : index + 1);

        AppEntry entry = new AppEntry(pkg, title, subtitle, action, accentColor, iconResId, launchIntent);
        try {
            entry.appIcon = pm.getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        return entry;
    }

    private static int resolveIcon(String pkg, int index) {
        String lower = pkg.toLowerCase();
        if (lower.contains("youtube") || lower.contains("smarttube")) {
            return index == 0 ? 0 : R.drawable.ic_youtube;
        } else if (lower.contains("spotify") || lower.contains("wave") || lower.contains("music") || lower.contains("media")) {
            return R.drawable.ic_spotify;
        } else if (lower.contains("twitch") || lower.contains("stremio") || lower.contains("gallery")) {
            return R.drawable.ic_twitch;
        } else if (lower.contains("plex") || lower.contains("explorer") || lower.contains("file")) {
            return R.drawable.ic_plex;
        } else if (lower.contains("browser") || lower.contains("chrome") || lower.contains("vpn") || lower.contains("vending") || lower.contains("settings")) {
            return R.drawable.ic_cybernet;
        }
        return DEFAULT_ICONS[index % DEFAULT_ICONS.length];
    }
}
