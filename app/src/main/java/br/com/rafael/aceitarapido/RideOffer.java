package br.com.rafael.aceitarapido;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RideOffer {
    private static final Pattern MONEY = Pattern.compile("R\\$\\s*(\\d+(?:[.,]\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KM = Pattern.compile("(?<![/\\d])(\\d+(?:[.,]\\d+)?)\\s*km\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*min(?:uto)?s?\\b", Pattern.CASE_INSENSITIVE);

    public final double fare;
    public final double pickupKm;
    public final double tripKm;
    public final double pickupMinutes;
    public final double tripMinutes;

    private RideOffer(double fare, double pickupKm, double tripKm,
                      double pickupMinutes, double tripMinutes) {
        this.fare = fare;
        this.pickupKm = pickupKm;
        this.tripKm = tripKm;
        this.pickupMinutes = pickupMinutes;
        this.tripMinutes = tripMinutes;
    }

    public static RideOffer parse(List<String> texts) {
        String joined = String.join(" | ", texts).replace('\u00a0', ' ');
        Matcher moneyMatcher = MONEY.matcher(joined);
        List<Double> money = new ArrayList<>();
        while (moneyMatcher.find()) money.add(number(moneyMatcher.group(1)));

        List<Double> kms = matches(KM, joined);
        List<Double> mins = matches(MIN, joined);
        if (money.isEmpty() || kms.size() < 2) return null;

        double fare = money.stream().filter(v -> v >= 4 && v <= 1000).findFirst().orElse(-1.0);
        if (fare < 0) return null;
        double pickupMin = mins.size() >= 2 ? mins.get(0) : -1;
        double tripMin = mins.size() >= 2 ? mins.get(1) : (mins.isEmpty() ? -1 : mins.get(0));
        return new RideOffer(fare, kms.get(0), kms.get(1), pickupMin, tripMin);
    }

    public boolean isGood(AppPrefs p) {
        double totalKm = pickupKm + tripKm;
        if (fare < p.minFare() || totalKm <= 0 || fare / totalKm < p.minPerKm()) return false;
        if (pickupKm > p.maxPickupKm()) return false;
        if (pickupMinutes >= 0 && pickupMinutes > p.maxPickupMinutes()) return false;
        double totalMinutes = Math.max(0, pickupMinutes) + Math.max(0, tripMinutes);
        return totalMinutes <= 0 || fare / totalMinutes >= p.minPerMinute();
    }

    public String fingerprint() {
        return String.format(Locale.US, "%.2f-%.2f-%.2f-%.1f", fare, pickupKm, tripKm, tripMinutes);
    }

    public String summary() {
        double rate = fare / Math.max(0.01, pickupKm + tripKm);
        return String.format(Locale.getDefault(), "R$ %.2f • %.2f km total • R$ %.2f/km", fare, pickupKm + tripKm, rate);
    }

    private static List<Double> matches(Pattern pattern, String input) {
        List<Double> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) result.add(number(matcher.group(1)));
        return result;
    }

    private static double number(String value) {
        try { return Double.parseDouble(value.replace(',', '.')); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
