package br.com.rafael.aceitarapido;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_SCREENSHOT = 42;
    private AppPrefs prefs;
    private Switch master, excellent, normal;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        setContentView(buildScreen());
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private ScrollView buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        scroll.addView(root);

        root.addView(text("ACEITA RÁPIDO", 28, true));
        TextView subtitle = text("Complemento para o seu Auto Clic", 16, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        master = toggle("AUTOACEITE LIGADO");
        master.setOnCheckedChangeListener((b, checked) -> {
            prefs.setEnabled(checked);
            prefs.setLastStatus(checked ? "Aguardando faixa do Auto Clic" : "Autoaceite desligado");
            refresh();
        });
        root.addView(master);
        status = text("", 15, true);
        status.setPadding(dp(12), dp(14), dp(12), dp(14));
        root.addView(status);

        TextView section = text("QUAIS FAIXAS DEVEM SER ACEITAS?", 15, true);
        section.setPadding(0, dp(22), 0, dp(8));
        root.addView(section);
        excellent = toggle("Verde — EXCELENTE");
        excellent.setOnCheckedChangeListener((b, checked) -> prefs.setAcceptExcellent(checked));
        root.addView(excellent);
        normal = toggle("Amarela — NORMAL");
        normal.setOnCheckedChangeListener((b, checked) -> prefs.setAcceptNormal(checked));
        root.addView(normal);
        TextView red = text("Vermelha — RUIM: nunca será clicada", 16, true);
        red.setTextColor(Color.rgb(180, 30, 30));
        red.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(red);

        Button access = new Button(this);
        access.setText("ATIVAR ACESSIBILIDADE");
        access.setTextColor(Color.WHITE);
        access.setBackgroundColor(Color.rgb(22, 163, 74));
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(access, new LinearLayout.LayoutParams(-1, dp(54)));

        Button analyze = new Button(this);
        analyze.setText("ANALISAR PRINT DA CORRIDA");
        analyze.setTextColor(Color.WHITE);
        analyze.setBackgroundColor(Color.rgb(37, 99, 235));
        analyze.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick, PICK_SCREENSHOT);
        });
        LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(-1, dp(54));
        analyzeParams.setMargins(0, dp(12), 0, 0);
        root.addView(analyze, analyzeParams);

        TextView help = text("1. Ative Aceita Rápido na acessibilidade.\n2. Deixe o Auto Clic das faixas ligado.\n3. Ligue o autoaceite acima.\n4. Abra o Rapidocar.\n\nEXCELENTE e NORMAL acionam ACEITAR uma única vez. RUIM continua tocando.", 15, false);
        help.setPadding(0, dp(18), 0, 0);
        root.addView(help);
        return scroll;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SCREENSHOT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        status.setText("Status: analisando o print...");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                    .addOnSuccessListener(result -> showScreenshotResult(result.getText()))
                    .addOnFailureListener(error -> status.setText("Status: não foi possível ler esse print"));
        } catch (IOException error) {
            status.setText("Status: não foi possível abrir esse print");
        }
    }

    private void showScreenshotResult(String textFound) {
        String text = textFound.toUpperCase(Locale.ROOT);
        if (text.contains("EXCELENTE")) {
            status.setText("Status: PRINT EXCELENTE detectado — teste aprovado");
            playTestBeep();
        } else if (text.contains("NORMAL") || text.contains("BOA")) {
            status.setText("Status: PRINT NORMAL/BOA detectado — teste aprovado");
            playTestBeep();
        } else if (text.contains("RUIM")) {
            status.setText("Status: PRINT RUIM detectado — sem bipe");
        } else {
            status.setText("Status: o print não contém EXCELENTE, NORMAL, BOA ou RUIM");
        }
    }

    private void playTestBeep() {
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 350);
        status.postDelayed(tone::release, 500);
        Toast.makeText(this, "Corrida boa detectada no print", Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        master.setChecked(prefs.enabled());
        excellent.setChecked(prefs.acceptExcellent());
        normal.setChecked(prefs.acceptNormal());
        status.setText("Status: " + prefs.lastStatus());
        status.setTextColor(prefs.enabled() ? Color.rgb(22, 130, 70) : Color.rgb(180, 70, 30));
    }

    private Switch toggle(String label) {
        Switch view = new Switch(this);
        view.setText(label); view.setTextSize(17);
        view.setPadding(dp(12), dp(13), dp(12), dp(13));
        view.setBackgroundColor(Color.WHITE);
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
