package dev.lelonio.square.visualsmoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Framework-only instrumentation: no production dependencies or Gradle changes.
 * Run exclusively on a disposable emulator with the documented synthetic fixtures. */
public class VisualSmoke extends Instrumentation {
    private int checks;
    private boolean thor;
    private boolean layout;
    private boolean noMini;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        thor = "thor".equals(args.getString("mode"));
        layout = args.getString("mode", "").startsWith("layout");
        noMini = "layout-nomini".equals(args.getString("mode"));
        start();
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (label.contentEquals(node.getText() == null ? "" : node.getText()) ||
            label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = find(node.getChild(i), label);
            if (child != null) return child;
        }
        return null;
    }
    protected AccessibilityNodeInfo node(String label) { return find(getUiAutomation().getRootInActiveWindow(), label); }
    protected void await(String label, boolean present) {
        long until = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < until) {
            if ((node(label) != null) == present) return;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Expected " + label + " present=" + present);
    }
    protected void touch(String label) {
        await(label, true);
        Rect bounds = new Rect(); node(label).getBoundsInScreen(bounds);
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.centerX(), bounds.centerY(), 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getUiAutomation().injectInputEvent(event, true); event.recycle();
        }
        SystemClock.sleep(400);
    }
    private void clearSyntheticQueue() throws Exception {
        // Test-only connection to the existing MediaSession, using runtime app
        // classes so the runner still compiles against the Android SDK alone.
        ClassLoader loader = getTargetContext().getClassLoader();
        Class<?> tokenType = Class.forName("androidx.media3.session.SessionToken", true, loader);
        Object token = tokenType.getConstructor(android.content.Context.class, android.content.ComponentName.class)
            .newInstance(getTargetContext(), new android.content.ComponentName("dev.lelonio.square", "dev.lelonio.square.playback.PlaybackService"));
        Class<?> builderType = Class.forName("androidx.media3.session.MediaController$Builder", true, loader);
        Object builder = builderType.getConstructor(android.content.Context.class, tokenType).newInstance(getTargetContext(), token);
        Object controller = ((java.util.concurrent.Future<?>) builderType.getMethod("buildAsync").invoke(builder)).get(15, java.util.concurrent.TimeUnit.SECONDS);
        runOnMainSync(() -> {
            try { controller.getClass().getMethod("clearMediaItems").invoke(controller); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        SystemClock.sleep(1000);
        runOnMainSync(() -> {
            try { controller.getClass().getMethod("release").invoke(controller); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    protected void swipe(float from, float to) {
        Rect screen = new Rect(); getUiAutomation().getRootInActiveWindow().getBoundsInScreen(screen);
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 20; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 20 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float y = screen.top + screen.height() * (from + (to - from) * i / 20f);
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, screen.left + screen.width() * 0.85f, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getUiAutomation().injectInputEvent(event, true); event.recycle(); SystemClock.sleep(15);
        }
        SystemClock.sleep(500);
    }
    protected void key(int code) {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(now, SystemClock.uptimeMillis(), action, code, 0);
            event.setSource(InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_DPAD);
            getUiAutomation().injectInputEvent(event, true);
        }
        SystemClock.sleep(500);
    }
    private void stick(float y) {
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties(); property.id = 0;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); coords.setAxisValue(MotionEvent.AXIS_Y, y);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1,
            new MotionEvent.PointerProperties[]{property}, new MotionEvent.PointerCoords[]{coords},
            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_JOYSTICK, 0);
        getUiAutomation().injectInputEvent(event, true); event.recycle(); SystemClock.sleep(180);
    }
    protected void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Missing screenshot");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(), "visual-smoke-" + name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        }
    }
    protected void passed(String name) {
        checks++;
        Bundle status = new Bundle(); status.putString("stream", "PASS " + (thor ? "thor " : "conventional ") + name + "\n");
        sendStatus(0, status);
    }
    @Override public void onStart() {
        try {
            if (!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE))
                throw new AssertionError("Disposable emulator required");
            getTargetContext().getSharedPreferences("square_preferences", 0).edit().putBoolean("player_open", false).commit();
            Intent intent = new Intent().setClassName("dev.lelonio.square", thor ?
                "dev.lelonio.square.thor.ui.ThorActivity" : "dev.lelonio.square.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            runOnMainSync(() -> getTargetContext().startActivity(intent));
            SystemClock.sleep(2500);
            if (node("Close") != null) touch("Close");
            await("Artists", true);
            if (layout) {
                if (noMini) clearSyntheticQueue();
                touch("Albums"); await("Album 01", true);
                if (noMini && (node("Play") != null || node("Pause") != null || node("Next") != null))
                    throw new AssertionError("Mini-player still present after clearing synthetic queue");
                screenshot(noMini ? "grid-start-no-mini" : "grid-start");
                for (int i = 0; i < 10; i++) swipe(0.76f, 0.30f);
                await("Midnights", true);
                Rect last = new Rect(); node("Midnights").getBoundsInScreen(last);
                Rect bar = new Rect(); node("Home").getBoundsInScreen(bar);
                if (last.bottom >= bar.top || last.top < 0) throw new AssertionError("Last card hidden by floating controls: " + last + " / " + bar);
                screenshot(noMini ? "grid-end-no-mini" : "grid-end");
                passed(noMini ? "last album scrolls above controls without mini-player" : "last album scrolls fully above floating mini-player");
                Bundle result = new Bundle(); result.putString("stream", "PASS total=" + checks + "\n"); finish(Activity.RESULT_OK, result); return;
            }
            touch("Artists"); await("Billie Eilish", true);
            if (node("Change artist association") != null || node("Associate artist image") != null)
                throw new AssertionError("Association controls leaked into grid");
            passed("clean artist grid");
            touch("Billie Eilish"); touch("About"); await("Change artist association", true);
            screenshot((thor ? "thor" : "conventional") + "-menu");
            passed("original Info opens contextual menu");
            key(thor ? KeyEvent.KEYCODE_BUTTON_B : KeyEvent.KEYCODE_BACK);
            await("Change artist association", false); await("About", true);
            passed("Back closes menu and retains profile");
            if (thor) {
                touch("About"); await("Change artist association", true);
                // Reset to the first item by reaching the group's boundary.
                // Focus is observed through the action it activates, not through
                // accessibility focus (Compose exposes those as separate states).
                for (int i = 0; i < 8; i++) key(KeyEvent.KEYCODE_DPAD_UP);
                key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN);
                key(KeyEvent.KEYCODE_BUTTON_A); await("Cancel", true);
                passed("D-pad and A activate profile input");
                key(KeyEvent.KEYCODE_BUTTON_B); await("Cancel", false);
                touch("About"); await("Change artist association", true);
                for (int i = 0; i < 8; i++) key(KeyEvent.KEYCODE_DPAD_UP);
                stick(0.85f); stick(0f); stick(0.85f); stick(0f);
                key(KeyEvent.KEYCODE_BUTTON_A); await("Cancel", true);
                key(KeyEvent.KEYCODE_BUTTON_B); await("Cancel", false);
                touch("About"); await("Change artist association", true);
                passed("left stick moves menu focus");
                key(KeyEvent.KEYCODE_BUTTON_B); await("Change artist association", false);
                passed("B dismisses menu after stick input");
            }
            Bundle result = new Bundle(); result.putString("stream", "PASS total=" + checks + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Bundle result = new Bundle(); result.putString("stream", "FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
