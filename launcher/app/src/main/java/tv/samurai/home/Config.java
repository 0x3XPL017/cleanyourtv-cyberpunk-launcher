package tv.samurai.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

public class Config {
    private static final String PREFS_NAME = "samurai_prefs";
    private final SharedPreferences prefs;

    public String cityLabel;
    public String lat;
    public String lng;
    public final String units;
    public final boolean cyberFlavor;

    public Config(Context context) {
        Resources res = context.getResources();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cityLabel = prefs.getString("weather_city_label", res.getString(R.string.weather_city_label));
        lat = prefs.getString("weather_lat", res.getString(R.string.weather_lat));
        lng = prefs.getString("weather_lng", res.getString(R.string.weather_lng));
        if (cityLabel != null && (cityLabel.contains("SOGNDAL") || cityLabel.contains("NIGHT CITY"))) {
            cityLabel = "OSLO";
            lat = "59.9139";
            lng = "10.7522";
            prefs.edit()
                    .putString("weather_city_label", cityLabel)
                    .putString("weather_lat", lat)
                    .putString("weather_lng", lng)
                    .apply();
        }
        units = res.getString(R.string.weather_units);
        cyberFlavor = res.getBoolean(R.bool.weather_cyber_flavor);
    }

    public void setLocation(String cityLabel, String lat, String lng) {
        this.cityLabel = cityLabel;
        this.lat = lat;
        this.lng = lng;
        prefs.edit()
                .putString("weather_city_label", cityLabel)
                .putString("weather_lat", lat)
                .putString("weather_lng", lng)
                .apply();
    }
}
