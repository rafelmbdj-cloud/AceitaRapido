package br.com.rafael.corridafalada;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.widget.*;
import java.util.Locale;

public final class MainActivity extends Activity {
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

        Button test = button("Testar faixa, som e voz");
        test.setOnClickListener(v -> testVoice()); root.addView(test);

        statusText = text("", 15, false); statusText.setPadding(dp(14),dp(14),dp(14),dp(14));
        statusText.setBackgroundColor(Color.rgb(224, 242, 229)); root.addView(statusText);
        TextView safety = text("Privacidade: a captura, quando necessária, é processada na memória e não é salva. Faça a configuração e os testes com o aparelho parado e sob supervisão de um motorista adulto habilitado.", 13, false);
        safety.setPadding(0,dp(14),0,0); root.addView(safety);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll); refresh();
    }

    private void testVoice() {
        RecognizedOffer sample = OfferParser.parse("R$ 23,08\nR$ 3,20/km\n1,6 km (4 min)\nRua Dois - Centro - Irati, PR\n4,1 km (8 min)\nRua Nossa Senhora de Fátima - Rio Bonito - Irati, PR");
        final TextToSpeech[] voice = new TextToSpeech[1];
        voice[0] = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) { toast("A voz do Android não está disponível."); return; }
            voice[0].setLanguage(new Locale("pt", "BR"));
            voice[0].setSpeechRate(1.18f);
            voice[0].speak(sample.speech(), TextToSpeech.QUEUE_FLUSH, null, "teste");
        });
    }

    @Override protected void onResume() { super.onResume(); if (statusText != null) refresh(); }
    private void refresh() { statusText.setText("Status: " + prefs.status()); }
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setPadding(0,dp(7),0,dp(7));if(bold)v.setTypeface(null,1);return v;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
