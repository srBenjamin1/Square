package dev.lelonio.square.visualsmoke;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/** Opens the real app's Library tab on a disposable emulator, including an empty index. */
public final class LibrarySmoke extends VisualSmoke {
    private String source;
    private boolean populated;
    private String switchFrom;
    @Override public void onCreate(Bundle args) {
        source = args.getString("source", "LOCAL");
        switchFrom = args.getString("switchFrom");
        populated = "true".equals(args.getString("populated", "false"));
        super.onCreate(args);
    }
    private void setRemoteLibraryFixture(Activity activity) throws Exception {
        ClassLoader loader = getTargetContext().getClassLoader();
        Class<?> ownerType = Class.forName("androidx.lifecycle.ViewModelStoreOwner", true, loader);
        Class<?> providerType = Class.forName("androidx.lifecycle.ViewModelProvider", true, loader);
        Object provider = providerType.getConstructor(ownerType).newInstance(activity);
        Class<?> modelType = Class.forName("dev.lelonio.square.ui.MainViewModel", true, loader);
        Object model = providerType.getMethod("get", Class.class).invoke(provider, modelType);
        java.util.List<Object> playlists = new java.util.ArrayList<>();
        if (populated) {
            Class<?> playlistType = Class.forName("dev.lelonio.square.data.CatalogPlaylist", true, loader);
            playlists.add(playlistType.getConstructor(String.class, String.class, String.class, String.class, boolean.class, Boolean.class)
                .newInstance("SPOTIFY".equals(source) ? "spotify:playlist:0000000000000000000000" : "ytmusic:playlist:PLregression",
                    "Library regression playlist", null, null, false, false));
        }
        Class<?> readyType = Class.forName("dev.lelonio.square.ui.MainViewModel$UiState$Ready", true, loader);
        Object ready = readyType.getConstructor(String.class, java.util.List.class, String.class).newInstance("Regression fixture", playlists, null);
        java.lang.reflect.Field state = modelType.getDeclaredField("_state"); state.setAccessible(true);
        Object flow = state.get(model);
        Class<?> mutableFlow = Class.forName("kotlinx.coroutines.flow.MutableStateFlow", true, loader);
        runOnMainSync(() -> {
            try { mutableFlow.getMethod("setValue", Object.class).invoke(flow, ready); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
    private void assertLibraryHeading() {
        await("Library", true);
        android.graphics.Rect title = new android.graphics.Rect(); node("Library").getBoundsInScreen(title);
        android.graphics.Rect window = new android.graphics.Rect(); getUiAutomation().getRootInActiveWindow().getBoundsInScreen(window);
        if (title.bottom >= window.height() / 2) throw new AssertionError("Only Library tab is present; Library heading did not open: " + title);
    }
    @Override public void onStart() {
        try {
            if (!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE))
                throw new AssertionError("Disposable emulator required");
            Object app = getTargetContext().getApplicationContext();
            Object preferences = app.getClass().getMethod("getPreferences").invoke(app);
            Class<?> sourceType = Class.forName("dev.lelonio.square.data.MusicSource", true, app.getClass().getClassLoader());
            Object selected = sourceType.getField(switchFrom == null ? source : switchFrom).get(null);
            preferences.getClass().getMethod("setSource", sourceType).invoke(preferences, selected);
            preferences.getClass().getMethod("setOnboarded", boolean.class).invoke(preferences, true);
            getTargetContext().getSharedPreferences("square_preferences", 0).edit().putBoolean("player_open", false).commit();
            Intent intent = new Intent().setClassName("dev.lelonio.square", "dev.lelonio.square.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ActivityMonitor monitor = addMonitor("dev.lelonio.square.ui.MainActivity", null, false);
            runOnMainSync(() -> getTargetContext().startActivity(intent));
            Activity activity = monitor.waitForActivityWithTimeout(30000);
            if (activity == null) throw new AssertionError("MainActivity did not start");
            await("Library", true);
            if (switchFrom != null) {
                // Keep the same activity and tab-widget composition while the
                // source changes its route count; fresh-start tests miss this.
                SystemClock.sleep(1000);
                Object target = sourceType.getField(source).get(null);
                runOnMainSync(() -> {
                    try { preferences.getClass().getMethod("setSource", sourceType).invoke(preferences, target); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
                SystemClock.sleep(1200); await("Library", true);
            }
            touch("Library");
            SystemClock.sleep(1800);
            if (!"LOCAL".equals(source)) setRemoteLibraryFixture(activity);
            await("Albums", true);
            assertLibraryHeading();
            passed(source + " opens Library");
            if ("LOCAL".equals(source)) {
                if (populated) {
                    await("Library regression album", true);
                    touch("Library regression album"); await("Library regression song", true);
                    key(android.view.KeyEvent.KEYCODE_BACK); await("Albums", true);
                    passed(source + " opens and returns from indexed album");
                } else {
                    int id = getTargetContext().getResources().getIdentifier("local_empty", "string", "dev.lelonio.square");
                    await(getTargetContext().getString(id), true);
                    passed(source + " empty library stays usable");
                }
                touch("Artists"); touch("Songs"); touch("Albums");
            } else {
                int totalId = getTargetContext().getResources().getIdentifier("item_count", "string", "dev.lelonio.square");
                await(getTargetContext().getString(totalId, populated ? 1 : 0), true);
                touch("Albums");
                int id = getTargetContext().getResources().getIdentifier("album_count", "string", "dev.lelonio.square");
                await(getTargetContext().getString(id, 0), true);
                touch("Playlists");
                if (populated) await("Library regression playlist", true);
                else {
                    int countId = getTargetContext().getResources().getIdentifier("playlist_count", "string", "dev.lelonio.square");
                    await(getTargetContext().getString(countId, 0), true);
                }
                touch("Change layout");
                if (populated) await("Library regression playlist", true);
                passed(source + " fixture filters and grid/list remain usable");
            }
            screenshot("library-" + source.toLowerCase() + (populated ? "-populated" : "-empty"));
            // Opening Search must not be necessary to create the Library route,
            // and composing it must not dynamically add a second destination.
            touch("Search"); SystemClock.sleep(600); key(android.view.KeyEvent.KEYCODE_BACK);
            // Search intentionally replaces the full tab strip with its field.
            touch("Home"); await("Library", true); touch("Library"); await("Albums", true);
            assertLibraryHeading();
            passed(source + " Library reopens after Search");
            Bundle result = new Bundle(); result.putString("stream", "PASS Library " + source + " populated=" + populated + (switchFrom == null ? "" : " switchFrom=" + switchFrom) + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            try { screenshot("library-" + source.toLowerCase() + "-failure"); } catch (Throwable ignored) { }
            Bundle result = new Bundle(); result.putString("stream", "FAIL Library " + source + " " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
