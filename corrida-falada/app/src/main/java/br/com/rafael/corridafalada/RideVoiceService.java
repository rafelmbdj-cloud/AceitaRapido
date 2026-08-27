package br.com.rafael.corridafalada;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.SystemClock;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;
import android.view.accessibility.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;
import java.util.concurrent.Executor;

public final class RideVoiceService extends AccessibilityService implements TextToSpeech.OnInitListener {
    private AppPrefs prefs;
    private TextToSpeech tts;
    private long lastScan;
    private boolean screenshotBusy;
    private String lastFingerprint = "";
    private WindowManager windowManager;
    private TextView banner;

    @Override public void onServiceConnected() {
        prefs = new AppPrefs(this); tts = new TextToSpeech(this, this);
        createBanner();
        prefs.status("Acessibilidade conectada");
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("pt", "BR")); tts.setSpeechRate(1.18f);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (prefs == null) prefs = new AppPrefs(this);
        String pkg = event.getPackageName().toString();
        if (!pkg.equals(getPackageName())) prefs.lastPackage(pkg);
        if (!prefs.enabled() || pkg.equals(getPackageName()) || isSystemPackage(pkg)) return;
        long now = SystemClock.elapsedRealtime(); if (now - lastScan < 300) return; lastScan = now;

        String accessible = collectAllText();
        RecognizedOffer offer = OfferParser.parse(accessible);
        if (offer.isComplete()) { speakOnce(offer, "Acessibilidade"); return; }
        if (looksLikeRide(accessible) && prefs.ocr() && Build.VERSION.SDK_INT >= 30) takeOcrScreenshot();
    }

    private boolean looksLikeRide(String text) {
        String upper = text == null ? "" : text.toUpperCase(Locale.ROOT);
        return upper.contains("R$") && upper.contains("KM") &&
                (upper.contains("ACEITAR") || upper.contains("MOTORISTA") || upper.contains("COLETA"));
    }

    private boolean isSystemPackage(String pkg) {
        return pkg.startsWith("com.android.") || pkg.startsWith("android") ||
                pkg.startsWith("com.google.android") || pkg.startsWith("com.samsung.android");
    }

    private String collectAllText() {
        StringBuilder out = new StringBuilder();
        List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo window : windows) collect(window.getRoot(), out);
        if (out.length() == 0) collect(getRootInActiveWindow(), out);
        return out.toString();
    }

    private void collect(AccessibilityNodeInfo node, StringBuilder out) {
        if (node == null) return;
        if (node.getText() != null) out.append(node.getText()).append('\n');
        if (node.getContentDescription() != null) out.append(node.getContentDescription()).append('\n');
        for (int i=0;i<node.getChildCount();i++) collect(node.getChild(i),out);
    }

    private void takeOcrScreenshot() {
        if (screenshotBusy || Build.VERSION.SDK_INT < 30) return;
        screenshotBusy = true;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                HardwareBuffer buffer = result.getHardwareBuffer(); ColorSpace color = result.getColorSpace();
                Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, color);
                Bitmap bitmap = hardware == null ? null : hardware.copy(Bitmap.Config.ARGB_8888, false);
                buffer.close(); if (bitmap == null) { screenshotBusy=false; prefs.status("OCR: captura indisponível"); return; }
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener(text -> { screenshotBusy=false; bitmap.recycle(); RecognizedOffer o=OfferParser.parse(text.getText()); if(o.isComplete())speakOnce(o,"OCR");else prefs.status("OCR leu a tela, mas faltaram dados"); })
                    .addOnFailureListener(e -> { screenshotBusy=false; bitmap.recycle(); prefs.status("Falha no OCR: "+e.getClass().getSimpleName()); });
            }
            @Override public void onFailure(int errorCode) { screenshotBusy=false; prefs.status("Captura não autorizada: "+errorCode); }
        });
    }

    private void speakOnce(RecognizedOffer offer, String source) {
        String fingerprint = offer.fingerprint(); if (fingerprint.equals(lastFingerprint)) return;
        lastFingerprint = fingerprint; prefs.status(source+": "+offer.speech());
        showBanner(offer);
        playRatingSound(offer.calculatedClassification());
        if (tts != null) tts.speak(offer.speech(), TextToSpeech.QUEUE_FLUSH, null, fingerprint);
    }

    private void createBanner() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        banner = new TextView(this);
        banner.setTextColor(Color.WHITE); banner.setTextSize(24); banner.setGravity(Gravity.CENTER);
        banner.setPadding(18, 14, 18, 14); banner.setVisibility(TextView.GONE);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP;
        windowManager.addView(banner, p);
    }

    private void showBanner(RecognizedOffer offer) {
        String rating = offer.calculatedClassification();
        int color = rating.equals("EXCELENTE") ? Color.rgb(0,135,55) :
                rating.equals("BOA") ? Color.rgb(238,177,0) : Color.rgb(198,35,35);
        String pickup = OfferParser.placeForSpeech(offer.pickupAddress);
        banner.setBackgroundColor(color);
        banner.setText(String.format(Locale.forLanguageTag("pt-BR"),
                "%s — R$ %.2f/km | Coleta: %.1f km | %s", rating, offer.pricePerKm, offer.pickupKm, pickup));
        banner.setVisibility(TextView.VISIBLE);
        banner.removeCallbacks(hideBanner);
        banner.postDelayed(hideBanner, 12000);
    }

    private final Runnable hideBanner = () -> { if (banner != null) banner.setVisibility(TextView.GONE); };

    private void playRatingSound(String rating) {
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        int type = rating.equals("RUIM") ? ToneGenerator.TONE_PROP_NACK :
                rating.equals("EXCELENTE") ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_BEEP;
        tone.startTone(type, rating.equals("RUIM") ? 550 : 260);
        banner.postDelayed(tone::release, 800);
    }

    @Override public void onInterrupt() { if(prefs!=null)prefs.status("Serviço interrompido"); }
    @Override public void onDestroy() {
        if(tts!=null){tts.stop();tts.shutdown();}
        if(windowManager!=null && banner!=null) { try { windowManager.removeView(banner); } catch(Exception ignored){} }
        super.onDestroy();
    }
}
