package br.com.rafael.corridafalada;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class OfferParser {
    private static final Pattern CLASSIFICATION = Pattern.compile("(?i)\\b(EXCELENTE|BOA|NORMAL|RUIM)\\b");
    private static final Pattern MONEY = Pattern.compile("(?i)R\\$\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)");
    private static final Pattern PER_KM = Pattern.compile("(?i)R\\$\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*/\\s*km");
    private static final Pattern LEG = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*km\\s*\\(\\s*\\d+\\s*min[^)]*\\)");
    private static final Pattern ADDRESS = Pattern.compile("(?i)^(?:RUA|R\\.|AVENIDA|AV\\.|RODOVIA|ESTRADA|TRAVESSA|ALAMEDA|PR-)[\\p{L}0-9 .ºª,'-]+$");

    public static RecognizedOffer parse(String raw) {
        RecognizedOffer offer = new RecognizedOffer();
        String text = raw == null ? "" : raw.replace('\u00a0', ' ');
        Matcher classification = CLASSIFICATION.matcher(text);
        if (classification.find()) offer.classification = classification.group(1).toUpperCase(Locale.ROOT);

        Matcher perKm = PER_KM.matcher(text);
        if (perKm.find()) offer.pricePerKm = number(perKm.group(1));

        Matcher money = MONEY.matcher(text);
        while (money.find()) {
            String after = text.substring(money.end(), Math.min(text.length(), money.end() + 12));
            if (after.matches("(?is)^\\s*/\\s*(?:km|min).*")) continue;
            double candidate = number(money.group(1));
            if (candidate >= 4) { offer.value = candidate; break; }
        }

        List<String> lines = cleanLines(text);
        for (int i = 0; i < lines.size(); i++) {
            Matcher leg = LEG.matcher(lines.get(i));
            if (!leg.find()) continue;
            String address = nextAddress(lines, i + 1);
            if (offer.pickupKm < 0) {
                offer.pickupKm = number(leg.group(1));
                offer.pickupAddress = address;
            } else if (offer.destinationAddress.isBlank()) {
                offer.destinationAddress = address;
                break;
            }
        }
        if (offer.pricePerKm < 0 && offer.value > 0) {
            Matcher tripKm = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*km\\s*\\(\\s*R\\$").matcher(text);
            if (tripKm.find()) {
                double km = number(tripKm.group(1));
                if (km > 0) offer.pricePerKm = offer.value / km;
            }
        }
        return offer;
    }

    private static List<String> cleanLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("[\\r\\n|]+")) {
            String clean = line.trim().replaceAll("\\s+", " ");
            if (!clean.isBlank()) result.add(clean);
        }
        return result;
    }

    private static String nextAddress(List<String> lines, int start) {
        for (int i = start; i < Math.min(lines.size(), start + 4); i++) {
            String line = lines.get(i).trim();
            if (ADDRESS.matcher(line).matches()) return line;
        }
        return "";
    }

    static String placeForSpeech(String address) {
        String cleaned = address.replaceAll("(?i)\\s*-\\s*Irati\\s*[-,]\\s*PR\\s*$", "").trim();
        String[] parts = cleaned.split("\\s+-\\s+");
        if (parts.length >= 2 && !parts[parts.length - 1].matches("(?i)PR|IRATI")) return "bairro " + parts[parts.length - 1];
        return cleaned;
    }

    private static double number(String value) {
        try { return Double.parseDouble(value.replace(',', '.')); }
        catch (NumberFormatException e) { return -1; }
    }

    private OfferParser() {}
}
