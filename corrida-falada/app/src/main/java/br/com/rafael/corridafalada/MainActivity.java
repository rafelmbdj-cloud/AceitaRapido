package br.com.rafael.corridafalada;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_PRINT = 1001;
    private AppPrefs prefs;
    private TextView statusText;
    private Switch enabled, ocr;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new AppPrefs(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(247, 250, 248));

        TextView title = text("Corrida Falada", 29, true);
        title.setTextColor(Color.rgb(7, 105, 45)); root.addView(title);
        root.addView(text("Analisa a corrida, mostra a faixa colorida, toca um alerta e fala o bairro. Nunca aceita nem recusa.", 16, false));

        Button accessibility = button("ATIVAR PERMISSÃO DE ACESSIBILIDADE");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        root.addView(text("Não precisa escolher o Rapidocar. Quando uma oferta aparecer, o aplicativo reconhece automaticamente.", 15, true));
        ocr = new Switch(this); ocr.setText("Usar OCR quando os textos não estiverem acessíveis");
        ocr.setChecked(prefs.ocr()); ocr.setOnCheckedChangeListener((v,on) -> prefs.ocr(on)); root.addView(ocr);
        enabled = new Switch(this); enabled.setText("Ativar análise de corridas"); enabled.setTextSize(18);
        enabled.setChecked(prefs.enabled()); enabled.setOnCheckedChangeListener((v,on) -> {
            prefs.enabled(on); prefs.status(on ? "Ativado; aguardando oferta" : "Desativado"); refresh();
        }); root.addView(enabled);

        TextView rules = text("Excelente (verde): acima de R$ 3,00/km\nBoa (amarela): de R$ 2,00 a R$ 3,00/km\nRuim (vermelha): abaixo de R$ 2,00/km", 16, true);
        rules.setTextColor(Color.rgb(35, 35, 35)); root.addView(rules);

        Button test = button("ANALISAR PRINT DA CORRIDA");
        test.setOnClickListener(v -> choosePrint()); root.addView(test);

        statusText = text("", 15, false); statusText.setPadding(dp(14),dp(14),dp(14),dp(14));
        statusText.setBackgroundColor(Color.rgb(224, 242, 229)); root.addView(statusText);
        TextView safety = text("Privacidade: a captura, quando necessária, é processada na memória e não é salva. Faça a configuração e os testes com o aparelho parado e sob supervisão de um motorista adulto habilitado.", 13, false);
        safety.setPadding(0,dp(14),0,0); root.addView(safety);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll); refresh();
    }

    private void choosePrint() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_PRINT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PRINT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        analyzePrint(data.getData());
    }

    private void analyzePrint(Uri uri) {
        prefs.status("Lendo o print..."); refresh();
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                    .addOnSuccessListener(text -> showPrintResult(OfferParser.parse(text.getText())))
                    .addOnFailureListener(error -> { prefs.status("Não consegui ler esse print"); refresh(); });
        } catch (IOException error) {
            prefs.status("Não consegui abrir esse print"); refresh();
        }
    }

    private void showPrintResult(RecognizedOffer offer) {
        if (offer.pricePerKm <= 0) {
            prefs.status("Não encontrei o valor por quilômetro no print"); refresh(); return;
        }
        String rating = offer.calculatedClassification();
        int color = rating.equals("EXCELENTE") ? Color.rgb(0,135,55) :
                rating.equals("BOA") ? Color.rgb(238,177,0) : Color.rgb(198,35,35);
        String place = !offer.pickupAddress.isBlank() ? OfferParser.placeForSpeech(offer.pickupAddress) : "bairro não identificado";
        prefs.status(String.format(Locale.forLanguageTag("pt-BR"), "%s — R$ %.2f/km — %s", rating, offer.pricePerKm, place));
        refresh(); statusText.setBackgroundColor(color); statusText.setTextColor(Color.WHITE);
        playSound(rating); speak(rating + ". " + place + ".");
    }

    private void playSound(String rating) {
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        int type = rating.equals("RUIM") ? ToneGenerator.TONE_PROP_NACK :
                rating.equals("EXCELENTE") ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_BEEP;
        tone.startTone(type, rating.equals("RUIM") ? 550 : 260);
        statusText.postDelayed(tone::release, 800);
    }

    private void speak(String message) {
        final TextToSpeech[] voice = new TextToSpeech[1];
        voice[0] = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) { toast("A voz do Android não está disponível."); return; }
            voice[0].setLanguage(new Locale("pt", "BR"));
            voice[0].setSpeechRate(1.18f);
            voice[0].speak(message, TextToSpeech.QUEUE_FLUSH, null, "print");
        });
    }

    @Override protected void onResume() { super.onResume(); if (statusText != null) refresh(); }
    private void refresh() { statusText.setText("Status: " + prefs.status()); }
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setPadding(0,dp(7),0,dp(7));if(bold)v.setTypeface(null,1);return v;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
