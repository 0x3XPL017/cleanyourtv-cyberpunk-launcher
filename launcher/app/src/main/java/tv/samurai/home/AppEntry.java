package tv.samurai.home;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public class AppEntry {
    public String packageName;
    public String title;
    public String subtitle;
    public String actionLabel;
    public int accentColor;
    public int iconResId;
    public Drawable appIcon;
    public Intent launchIntent;
    public boolean isAddButton;

    public AppEntry(String packageName, String title, String subtitle, String actionLabel, int accentColor, int iconResId, Intent launchIntent) {
        this.packageName = packageName;
        this.title = title;
        this.subtitle = subtitle;
        this.actionLabel = actionLabel;
        this.accentColor = accentColor;
        this.iconResId = iconResId;
        this.launchIntent = launchIntent;
        this.isAddButton = false;
    }
}
