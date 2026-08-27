package br.com.rafael.corridafalada;

import java.util.Locale;

public final class RecognizedOffer {
    public String classification = "";
    public double value = -1;
    public double pickupKm = -1;
    public String pickupAddress = "";
    public String destinationAddress = "";
    public double pricePerKm = -1;

    public String calculatedClassification() {
        if (pricePerKm > 3.0) return "EXCELENTE";
        if (pricePerKm >= 2.0) return "BOA";
        return "RUIM";
    }

    public boolean isComplete() {
        return value > 0 && pricePerKm > 0 && pickupKm >= 0 && !pickupAddress.isBlank() && !destinationAddress.isBlank();
    }

    public String fingerprint() {
        return String.format(Locale.US, "%.2f|%.2f|%s|%s", value, pickupKm, pickupAddress, destinationAddress);
    }

    public String speech() {
        String rating = title(calculatedClassification()) + ".";
        String pickup = OfferParser.placeForSpeech(pickupAddress);
        String destination = OfferParser.placeForSpeech(destinationAddress);
        return String.format(Locale.forLanguageTag("pt-BR"),
                "%s Embarque em %s, a %.1f quilômetro%s. Destino %s. Valor %.0f reais. %.2f por quilômetro.",
                rating, pickup, pickupKm, pickupKm == 1.0 ? "" : "s", destination, value, pricePerKm);
    }

    private static String title(String text) {
        String lower = text.toLowerCase(Locale.forLanguageTag("pt-BR"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
