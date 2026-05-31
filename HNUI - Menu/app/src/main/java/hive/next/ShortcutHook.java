package hive.next;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShortcutHook implements IXposedHookLoadPackage {

    private static WindowManager windowManager;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> currentActivityRef;
    private static float hue = 0f;

    private static final Map<String, ShortcutView> shortcuts = new HashMap<String, ShortcutView>();

    private static final int COLOR_BLUE = Color.parseColor("#FF0034FF");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFFFF");
    private static final int COLOR_TRANSPARENT = Color.parseColor("#00000000");

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;

                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    return;
                }

                cleanup();
                currentActivityRef = new WeakReference<Activity>(activity);
                windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

                startGradientLoop();
            }
        });
    }

    private void cleanup() {
        if (windowManager != null) {
            for (ShortcutView shortcut : shortcuts.values()) {
                try {
                    windowManager.removeView(shortcut.view);
                } catch (Exception e) {
                }
            }
        }
        shortcuts.clear();
        windowManager = null;
    }

    public static void toggleShortcut(Context context, String featureName) {
        if (shortcuts.containsKey(featureName)) {
            ShortcutView shortcut = shortcuts.get(featureName);
            try {
                windowManager.removeView(shortcut.view);
            } catch (Exception e) {
            }

            synchronized (FeatureState.activeFeatures) {
                FeatureState.activeFeatures.remove(featureName);
            }
            MenuHook.syncButtonState(featureName, false);

            shortcuts.remove(featureName);
        } else {
            createShortcut(context, featureName);
        }
    }

    public static void syncShortcutState(String featureName, boolean isOn) {
        ShortcutView shortcut = shortcuts.get(featureName);
        if (shortcut != null) {
            updateShortcutStyle(shortcut);
        }
    }

    private static void createShortcut(Context context, String featureName) {
        if (windowManager == null) return;

        final LinearLayout shortcutView = new LinearLayout(context);
        shortcutView.setOrientation(LinearLayout.HORIZONTAL);
        shortcutView.setGravity(Gravity.CENTER);
        shortcutView.setPadding(dip2px(context, 10), dip2px(context, 8), dip2px(context, 10), dip2px(context, 8));

        final TextView textView = new TextView(context);
        textView.setText(featureName);
        textView.setTextSize(14);
        textView.setGravity(Gravity.CENTER);
        shortcutView.addView(textView);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius((float) dip2px(context, 4));
        shortcutView.setBackgroundDrawable(bg);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.CENTER;  
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(shortcutView, params);
        } catch (Exception e) {
            return;
        }

        final ShortcutView shortcut = new ShortcutView();
        shortcut.view = shortcutView;
        shortcut.textView = textView;
        shortcut.params = params;
        shortcut.featureName = featureName;
        shortcuts.put(featureName, shortcut);

        synchronized (FeatureState.activeFeatures) {
            if (!FeatureState.activeFeatures.contains(featureName)) {
                FeatureState.activeFeatures.add(featureName);
            }
        }
        MenuHook.syncButtonState(featureName, true);

        updateShortcutStyle(shortcut);

        setupDragAndClick(shortcutView, shortcut, context);
    }

    private static void setupDragAndClick(final View view, final ShortcutView shortcut, final Context context) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downX = 0;
            private float downY = 0;
            private int startX = 0;
            private int startY = 0;
            private long downTime = 0;
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 10; 
            private static final long LONG_PRESS_TIME = 600; 

            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isDragging) {
                        toggleShortcut(context, shortcut.featureName);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = shortcut.params.x;
                        startY = shortcut.params.y;
                        downTime = System.currentTimeMillis();
                        isDragging = false;
                        handler.postDelayed(longPressRunnable, LONG_PRESS_TIME);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float moveX = Math.abs(event.getRawX() - downX);
                        float moveY = Math.abs(event.getRawY() - downY);

                        if (moveX > CLICK_THRESHOLD || moveY > CLICK_THRESHOLD) {
                            isDragging = true;
                            handler.removeCallbacks(longPressRunnable);
                        }

                        if (isDragging) {
                            float dx = event.getRawX() - downX;
                            float dy = event.getRawY() - downY;
                            shortcut.params.x = startX + (int) dx;
                            shortcut.params.y = startY + (int) dy;
                            if (windowManager != null) {
                                try {
                                    windowManager.updateViewLayout(view, shortcut.params);
                                } catch (Exception e) {
                                }
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPressRunnable);
                        float totalMoveX = Math.abs(event.getRawX() - downX);
                        float totalMoveY = Math.abs(event.getRawY() - downY);
                        long duration = System.currentTimeMillis() - downTime;

                        if (!isDragging && totalMoveX < CLICK_THRESHOLD && totalMoveY < CLICK_THRESHOLD
                                && duration < LONG_PRESS_TIME) {
                            synchronized (FeatureState.activeFeatures) {
                                boolean isOn = FeatureState.activeFeatures.contains(shortcut.featureName);
                                isOn = !isOn;

                                if (isOn) {
                                    if (!FeatureState.activeFeatures.contains(shortcut.featureName)) {
                                        FeatureState.activeFeatures.add(shortcut.featureName);
                                    }
                                } else {
                                    FeatureState.activeFeatures.remove(shortcut.featureName);
                                }

                                MenuHook.syncButtonState(shortcut.featureName, isOn);
                                updateShortcutStyle(shortcut);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        return true;
                }
                return false;
            }
        });
    }

    private void startGradientLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hue = (hue + 2f) % 360f;
                for (ShortcutView shortcut : shortcuts.values()) {
                    if (shortcut != null && shortcut.view != null) {
                        updateShortcutStyle(shortcut);
                    }
                }
                handler.postDelayed(this, 50);
            }
        }, 50);
    }

    private static void updateShortcutStyle(ShortcutView shortcut) {
        if (shortcut == null || shortcut.view == null) return;

        boolean isOn = FeatureState.activeFeatures.contains(shortcut.featureName);
        GradientDrawable bg = new GradientDrawable();

        if (isOn) {
            bg.setColor(getRainbowColor(hue));
            shortcut.textView.setTextColor(COLOR_WHITE);
        } else {
            bg.setColor(COLOR_TRANSPARENT);
            shortcut.textView.setTextColor(COLOR_BLUE);
        }
        bg.setCornerRadius((float) dip2px(shortcut.view.getContext(), 4));
        shortcut.view.setBackgroundDrawable(bg);
    }

    private static int getRainbowColor(float hue) {
        float[] hsv = new float[] { hue, 1.0f, 1.0f };
        return Color.HSVToColor(hsv);
    }

    private static int dip2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    private static class ShortcutView {
        LinearLayout view;
        TextView textView;
        WindowManager.LayoutParams params;
        String featureName;
    }
}
