package hive.next;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogoHook implements IXposedHookLoadPackage {

    private static WindowManager windowManager;
    private static LinearLayout container;
    private static WindowManager.LayoutParams logoParams;
    private static TextView tvTime;
    private static TextView tvPkg;
    private static TextView tvLogo;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> currentActivityRef;
    private static GradientManager gradientManager;

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

                windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

                gradientManager = GradientManager.getInstance();
                gradientManager.start();

                container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);

                tvTime = new TextView(activity);
                tvTime.setText(getCurrentTime());
                tvTime.setTextSize(14);
                tvTime.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));
                tvTime.setBackgroundColor(Color.parseColor("#8A000000"));
                LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                container.addView(tvTime, lpTime);

                tvPkg = new TextView(activity);
                tvPkg.setText(getActivityInfo(activity));
                tvPkg.setTextSize(14);
                tvPkg.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));
                tvPkg.setBackgroundColor(Color.parseColor("#8A000000"));
                LinearLayout.LayoutParams lpPkg = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                container.addView(tvPkg, lpPkg);

                tvLogo = new TextView(activity);
                tvLogo.setText("Hive Next");
                tvLogo.setTextSize(14);
                tvLogo.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));
                tvLogo.setBackgroundColor(Color.parseColor("#8A000000"));
                container.addView(tvLogo, new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT
                                  ));

                logoParams = new WindowManager.LayoutParams();
                logoParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                logoParams.format = PixelFormat.TRANSLUCENT;
                logoParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                logoParams.gravity = Gravity.BOTTOM | Gravity.START;
                logoParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                logoParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

                try {
                    windowManager.addView(container, logoParams);
                } catch (Exception e) {
                    XposedBridge.log("[HiveNext] Logo add error: " + e.getMessage());
                }

                startUpdateLoop();
            }
        });
    }

    private void cleanup() {
        if (container != null && windowManager != null) {
            try {
                windowManager.removeView(container);
            } catch (Exception e) {
            }
        }
        if (gradientManager != null) {
            gradientManager.stop();
        }
        container = null;
        tvTime = null;
        tvPkg = null;
        tvLogo = null;
        logoParams = null;
        windowManager = null;
    }

    private void startUpdateLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (tvTime != null && tvPkg != null && tvLogo != null && container != null) {
                    updateVisibility();
                    tvTime.setText(getCurrentTime());

                    tvTime.setTextColor(GradientManager.getRainbowColorWithOffset(0f));
                    tvPkg.setTextColor(GradientManager.getRainbowColorWithOffset(40f));
                    tvLogo.setTextColor(GradientManager.getRainbowColorWithOffset(80f));

                    handler.postDelayed(this, 50);
                }
            }
        }, 20);
    }

    private void updateVisibility() {
        synchronized (FeatureState.activeFeatures) {
            boolean hasSideWatermark = false;
            for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                if ("信息".equals(FeatureState.activeFeatures.get(i))) {
                    hasSideWatermark = true;
                    break;
                }
            }
            if (container != null) {
                if (hasSideWatermark) {
                    container.setVisibility(View.VISIBLE);
                } else {
                    container.setVisibility(View.GONE);
                }
            }
        }
    }

    private int dip2px(Activity activity, float dp) {
        float scale = activity.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    private GradientDrawable createRoundRectBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius((float) radius);
        return drawable;
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String getActivityInfo(Activity activity) {
        return activity.getPackageName() + "." + activity.getClass().getSimpleName();
    }
}