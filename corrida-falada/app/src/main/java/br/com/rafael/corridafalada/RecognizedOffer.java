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

    public boolean canClassify() { return pricePerKm > 0; }

    public String fingerprint() {
        return String.format(Locale.US, "%.2f|%.2f|%.2f|%s|%s", value, pricePerKm, pickupKm, pickupAddress, destinationAddress);
    }

    public String speech() {
        String rating = title(calculatedClassification()) + ".";
        StringBuilder out = new StringBuilder(rating);
        if (!pickupAddress.isBlank()) out.append(" Embarque em ").append(OfferParser.placeForSpeech(pickupAddress)).append(".");
        if (pickupKm >= 0) out.append(String.format(Locale.forLanguageTag("pt-BR"), " Coleta a %.1f quilômetro%s.", pickupKm, pickupKm == 1.0 ? "" : "s"));
        if (!destinationAddress.isBlank()) out.append(" Destino ").append(OfferParser.placeForSpeech(destinationAddress)).append(".");
        if (value > 0) out.append(String.format(Locale.forLanguageTag("pt-BR"), " Valor %.0f reais.", value));
        out.append(String.format(Locale.forLanguageTag("pt-BR"), " %.2f por quilômetro.", pricePerKm));
        return out.toString();
    }

    private static String title(String text) {
        String lower = text.toLowerCase(Locale.forLanguageTag("pt-BR"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
