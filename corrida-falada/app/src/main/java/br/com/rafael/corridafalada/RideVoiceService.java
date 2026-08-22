package br.com.rafael.corridafalada;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.Display;
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

    @Override public void onServiceConnected() {
        prefs = new AppPrefs(this); tts = new TextToSpeech(this, this);
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
        if (!prefs.enabled() || !pkg.equals(prefs.target())) return;
        long now = SystemClock.elapsedRealtime(); if (now - lastScan < 300) return; lastScan = now;

        String accessible = collectAllText();
        RecognizedOffer offer = OfferParser.parse(accessible);
        if (offer.isComplete()) { speakOnce(offer, "Acessibilidade"); return; }
        if (prefs.ocr() && Build.VERSION.SDK_INT >= 30 && now - lastScan >= 0) takeOcrScreenshot();
        else prefs.status("Oferta detectada; aguardando dados completos");
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
        if (tts != null) tts.speak(offer.speech(), TextToSpeech.QUEUE_FLUSH, null, fingerprint);
    }

    @Override public void onInterrupt() { if(prefs!=null)prefs.status("Serviço interrompido"); }
    @Override public void onDestroy() { if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy(); }
}
