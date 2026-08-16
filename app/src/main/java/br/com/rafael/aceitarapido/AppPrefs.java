package br.com.rafael.aceitarapido;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "aceita_rapido";
    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    public double minFare() { return getDouble("min_fare", 8.0); }
    public double minPerKm() { return getDouble("min_per_km", 2.0); }
    public double minPerMinute() { return getDouble("min_per_min", 1.0); }
    public double maxPickupKm() { return getDouble("max_pickup_km", 3.5); }
    public double maxPickupMinutes() { return getDouble("max_pickup_min", 5.0); }
    public String targetPackage() { return prefs.getString("target_package", ""); }
    public void setTargetPackage(String value) { prefs.edit().putString("target_package", value).apply(); }
    public boolean captureTarget() { return prefs.getBoolean("capture_target", false); }
    public void setCaptureTarget(boolean value) { prefs.edit().putBoolean("capture_target", value).apply(); }
    public String lastStatus() { return prefs.getString("last_status", "Aguardando configuração"); }
    public void setLastStatus(String value) { prefs.edit().putString("last_status", value).apply(); }
    public boolean acceptExcellent() { return prefs.getBoolean("accept_excellent", true); }
    public void setAcceptExcellent(boolean value) { prefs.edit().putBoolean("accept_excellent", value).apply(); }
    public boolean acceptNormal() { return prefs.getBoolean("accept_normal", true); }
    public void setAcceptNormal(boolean value) { prefs.edit().putBoolean("accept_normal", value).apply(); }

    public void saveRules(double minFare, double minPerKm, double minPerMinute,
                          double maxPickupKm, double maxPickupMinutes) {
        prefs.edit()
                .putLong("min_fare", Double.doubleToRawLongBits(minFare))
                .putLong("min_per_km", Double.doubleToRawLongBits(minPerKm))
                .putLong("min_per_min", Double.doubleToRawLongBits(minPerMinute))
                .putLong("max_pickup_km", Double.doubleToRawLongBits(maxPickupKm))
                .putLong("max_pickup_min", Double.doubleToRawLongBits(maxPickupMinutes))
                .apply();
    }

    private double getDouble(String key, double fallback) {
        if (!prefs.contains(key)) return fallback;
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToRawLongBits(fallback)));
    }
}
