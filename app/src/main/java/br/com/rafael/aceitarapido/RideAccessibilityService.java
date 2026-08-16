package br.com.rafael.aceitarapido;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RideAccessibilityService extends AccessibilityService {
    private AppPrefs prefs;
    private long lastClickAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanning;
    private final Runnable scanner = new Runnable() {
        @Override public void run() {
            scanAndAccept();
            handler.postDelayed(this, 350);
        }
    };

    @Override public void onServiceConnected() {
        prefs = new AppPrefs(this);
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        prefs.setLastStatus("Acessibilidade conectada");
        if (!scanning) {
            scanning = true;
            handler.post(scanner);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        scanAndAccept();
    }

    private void scanAndAccept() {
        if (prefs == null) prefs = new AppPrefs(this);
        if (!prefs.enabled()) return;
        long now = SystemClock.elapsedRealtime();
        // Evita dois toques na mesma oferta, mas libera rapidamente a próxima.
        if (now - lastClickAt < 2500) return;

        List<AccessibilityNodeInfo> roots = activeRoots();
        List<String> texts = new ArrayList<>();
        for (AccessibilityNodeInfo root : roots) collectTexts(root, texts);
        String decision = findDecision(texts);
        AccessibilityNodeInfo accept = null;
        for (AccessibilityNodeInfo root : roots) {
            accept = findAcceptButton(root);
            if (accept != null) break;
        }

        if (decision.isEmpty() || accept == null) {
            if (!decision.isEmpty()) {
                prefs.setLastStatus(decision + " detectada; aguardando botão ACEITAR");
            }
            return;
        }
        if (decision.equals("RUIM")) {
            prefs.setLastStatus("RUIM: deixada tocando para decisão manual");
            return;
        }
        if (decision.equals("EXCELENTE") && !prefs.acceptExcellent()) return;
        if (decision.equals("NORMAL") && !prefs.acceptNormal()) return;
        if (clickNodeOrParent(accept)) {
            lastClickAt = now;
            prefs.setLastStatus(decision + ": botão ACEITAR acionado");
        }
    }

    @Override public void onInterrupt() {
        if (prefs != null) prefs.setLastStatus("Serviço interrompido pelo Android");
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(scanner);
        scanning = false;
        super.onDestroy();
    }

    private List<AccessibilityNodeInfo> activeRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo window : getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) roots.add(root);
        }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) roots.add(active);
        return roots;
    }

    private void collectTexts(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        if (node.getText() != null) out.add(node.getText().toString());
        if (node.getContentDescription() != null) out.add(node.getContentDescription().toString());
        for (int i = 0; i < node.getChildCount(); i++) collectTexts(node.getChild(i), out);
    }

    private String findDecision(List<String> texts) {
        String all = String.join(" | ", texts).toUpperCase(Locale.ROOT);
        if (all.contains("EXCELENTE")) return "EXCELENTE";
        if (all.contains("NORMAL")) return "NORMAL";
        if (all.contains("RUIM")) return "RUIM";
        return "";
    }

    private AccessibilityNodeInfo findAcceptButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String text = node.getText() == null ? "" : node.getText().toString();
        String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        String combined = (text + " " + desc).trim().toUpperCase(Locale.ROOT);
        if (combined.matches("^ACEITAR(?:\\s*\\(\\d+\\))?.*$")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findAcceptButton(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
