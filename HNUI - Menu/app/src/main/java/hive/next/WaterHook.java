package hive.next;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WaterHook implements IXposedHookLoadPackage {

    private static TextView watermark;
    private static WindowManager.LayoutParams waterParams;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> currentActivityRef;
    private static WindowManager windowManager;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;

                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    updateVisibility();
                    return;
                }

                cleanup();
                currentActivityRef = new WeakReference<Activity>(activity);

                watermark = new TextView(activity);
                watermark.setText("Hive Next");
                watermark.setTextSize(91);
                watermark.setTextColor(Color.parseColor("#80D500F9"));
                watermark.setGravity(Gravity.CENTER);

                waterParams = new WindowManager.LayoutParams();
                waterParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                waterParams.format = PixelFormat.TRANSLUCENT;
                waterParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                waterParams.gravity = Gravity.CENTER;
                waterParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
                waterParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

                try {
                    windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                    windowManager.addView(watermark, waterParams);
                    XposedBridge.log("[HiveNext] Watermark added");
                } catch (Exception e) {
                    XposedBridge.log("[HiveNext] Watermark add error: " + e.getMessage());
                }

                startCheckLoop();
            }
        });
    }

    private void cleanup() {
        if (watermark != null && windowManager != null) {
            try {
                windowManager.removeView(watermark);
            } catch (Exception e) {
            }
        }
        watermark = null;
        waterParams = null;
        windowManager = null;
    }

    private void startCheckLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateVisibility();
                handler.postDelayed(this, 20);
            }
        }, 20);
    }

    private void updateVisibility() {
        if (watermark == null) return;

        synchronized (FeatureState.activeFeatures) {
            boolean hasScreenWatermark = false;
            for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                if ("屏幕水印".equals(FeatureState.activeFeatures.get(i))) {
                    hasScreenWatermark = true;
                    break;
                }
            }

            if (hasScreenWatermark) {
                watermark.setVisibility(View.VISIBLE);
            } else {
                watermark.setVisibility(View.GONE);
            }
        }
    }
}
