package br.com.rafael.corridafalada;

import static org.junit.Assert.*;
import org.junit.Test;

public class OfferParserTest {
    private static final String PRINT = "EXCELENTE - Nota 100: R$ 3,48/km | Coleta: 1,6 km\n" +
            "R$ 23,08\n(Motorista) R$ 19,93\n5,7 km (R$ 3,48/km)\n" +
            "12 min (R$ 1,62/min)\n1,6 km (4 min)\nRua Dois - Irati, PR\n" +
            "4,1 km (8 min)\nRua Nossa Senhora de Fátima - Irati, PR\nACEITAR (6)";

    @Test public void parsesReferenceScreenshotWithoutConfusingRates() {
        RecognizedOffer offer = OfferParser.parse(PRINT);
        assertTrue(offer.isComplete());
        assertEquals("EXCELENTE", offer.classification);
        assertEquals(23.08, offer.value, 0.001);
        assertEquals(3.48, offer.pricePerKm, 0.001);
        assertEquals(1.6, offer.pickupKm, 0.001);
        assertEquals("Rua Dois - Irati, PR", offer.pickupAddress);
        assertEquals("Rua Nossa Senhora de Fátima - Irati, PR", offer.destinationAddress);
        assertTrue(offer.speech().startsWith("Excelente."));
    }

    @Test public void appliesRequestedPriceRanges() {
        RecognizedOffer offer = new RecognizedOffer();
        offer.pricePerKm = 3.01; assertEquals("EXCELENTE", offer.calculatedClassification());
        offer.pricePerKm = 3.00; assertEquals("BOA", offer.calculatedClassification());
        offer.pricePerKm = 2.00; assertEquals("BOA", offer.calculatedClassification());
        offer.pricePerKm = 1.99; assertEquals("RUIM", offer.calculatedClassification());
    }
}
