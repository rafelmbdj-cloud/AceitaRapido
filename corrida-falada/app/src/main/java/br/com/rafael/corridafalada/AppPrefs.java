package br.com.rafael.corridafalada;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {
    private final SharedPreferences p;
    AppPrefs(Context context) { p = context.getSharedPreferences("corrida_falada", Context.MODE_PRIVATE); }
    boolean enabled() { return p.getBoolean("enabled", false); }
    void enabled(boolean value) { p.edit().putBoolean("enabled", value).apply(); }
    boolean ocr() { return p.getBoolean("ocr", true); }
    void ocr(boolean value) { p.edit().putBoolean("ocr", value).apply(); }
    String target() { return p.getString("target", ""); }
    void target(String value) { p.edit().putString("target", value).apply(); }
    String lastPackage() { return p.getString("last_package", ""); }
    void lastPackage(String value) { p.edit().putString("last_package", value).apply(); }
    String status() { return p.getString("status", "Aguardando configuração"); }
    void status(String value) { p.edit().putString("status", value).apply(); }
    boolean clickTarget() { return p.getBoolean("click_target", false); }
    void clickTarget(boolean value) { p.edit().putBoolean("click_target", value).apply(); }
    int clickX() { return p.getInt("click_x", 300); }
    int clickY() { return p.getInt("click_y", 700); }
    void clickPosition(int x, int y) { p.edit().putInt("click_x", x).putInt("click_y", y).apply(); }
}
