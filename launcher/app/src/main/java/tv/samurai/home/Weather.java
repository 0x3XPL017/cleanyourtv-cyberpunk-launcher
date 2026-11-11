package tv.samurai.home;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live Open-Meteo weather fetcher with Cyberpunk HUD telemetry translation.
 */
public class Weather {
    public static class WeatherData {
        public String cityLabel;
        public int temperature;
        public String condition;
        public String cyberTelemetry;
        public String hazardLevel;
        public String hazardDetails;
        public boolean isAcidRain;
    }

    public interface WeatherCallback {
        void onWeatherLoaded(WeatherData data);
    }

    public static class CitySearchResult {
        public String name;
        public String country;
        public String lat;
        public String lng;
    }

    public interface GeocodeCallback {
        void onResults(java.util.List<CitySearchResult> results);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void searchCity(String query, GeocodeCallback callback) {
        executor.execute(() -> {
            java.util.List<CitySearchResult> list = new java.util.ArrayList<>();
            try {
                String encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8");
                String urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=5&language=en&format=json";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("results")) {
                        org.json.JSONArray arr = json.getJSONArray("results");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            CitySearchResult r = new CitySearchResult();
                            r.name = obj.optString("name", "UNKNOWN").toUpperCase();
                            r.country = obj.optString("country", "").toUpperCase();
                            r.lat = String.format(java.util.Locale.US, "%.4f", obj.optDouble("latitude", 0.0));
                            r.lng = String.format(java.util.Locale.US, "%.4f", obj.optDouble("longitude", 0.0));
                            list.add(r);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            mainHandler.post(() -> callback.onResults(list));
        });
    }

    public void fetch(String city, String lat, String lng, String units, WeatherCallback callback) {
        executor.execute(() -> {
            WeatherData data = new WeatherData();
            data.cityLabel = city != null ? city : "NIGHT CITY";
            data.temperature = 28;
            data.condition = "DIGITAL ACID RAIN";
            data.cyberTelemetry = "ATMOS // WIND 14 KM/H · RAD 0.14 mSv · BIO-FILTER: ON";
            data.hazardLevel = "⚠️ HAZARDS";
            data.hazardDetails = "AIR TOXICITY: MODERATE - PH 3.4";
            data.isAcidRain = true;

            try {
                String tempParam = "fahrenheit".equalsIgnoreCase(units) ? "temperature_unit=fahrenheit" : "temperature_unit=celsius";
                String urlStr = String.format("https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true&%s",
                        lat, lng, tempParam);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("current_weather")) {
                        JSONObject current = json.getJSONObject("current_weather");
                        data.temperature = (int) Math.round(current.getDouble("temperature"));
                        int wind = (int) Math.round(current.optDouble("windspeed", 12.0));
                        int code = current.getInt("weathercode");

                        // Translate WMO code into Cyberpunk environmental telemetry
                        if (code >= 51 && code <= 67) {
                            data.condition = "DIGITAL ACID RAIN";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · ACID PH 3.4 · FILTER: ACTIVE";
                            data.hazardDetails = "PRECIPITATION: ACIDIC (PH 3.4) · HAZARD ALERT";
                            data.isAcidRain = true;
                        } else if (code >= 80 && code <= 82) {
                            data.condition = "HEAVY ACID DOWNPOUR";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · RAD 0.28 mSv · SHIELD: MAX";
                            data.hazardDetails = "TOXIC FLUSH: EXTREME · SEEK SHELTER";
                            data.isAcidRain = true;
                        } else if (code >= 95) {
                            data.condition = "ELECTRICAL ION SQUALL";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · EMP SURGE · GRID SHIELD ON";
                            data.hazardDetails = "LIGHTNING ARCS · GRID INTERFERENCE HIGH";
                            data.isAcidRain = true;
                        } else if (code >= 45 && code <= 48) {
                            data.condition = "INDUSTRIAL TOXIC SMOG";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · PM2.5 280µg · MASK REQ";
                            data.hazardDetails = "PARTICULATES PM2.5: SEVERE (280 µg/m³)";
                            data.isAcidRain = false;
                        } else if (code >= 1 && code <= 3) {
                            data.condition = "SYNTHETIC CLOUD MATRIX";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · RAD 0.08 mSv · AIRFLOW OPTIMAL";
                            data.hazardDetails = "UV INDEX: 1.2 · OZONE PURITY 88%";
                            data.isAcidRain = false;
                        } else {
                            data.condition = "NEON SKY / CLEAR VIS";
                            data.cyberTelemetry = "WIND " + wind + " KM/H · RAD 0.04 mSv · VISIBILITY 100%";
                            data.hazardDetails = "HAZARD STATUS: NOMINAL · AIRFLOW NORMAL";
                            data.isAcidRain = false;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep default cyberpunk telemetry on network offline
            }

            mainHandler.post(() -> callback.onWeatherLoaded(data));
        });
    }
}
