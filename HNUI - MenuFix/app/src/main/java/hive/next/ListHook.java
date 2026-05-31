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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ListHook implements IXposedHookLoadPackage {

    private static WindowManager windowManager;
    private static LinearLayout container;
    private static WindowManager.LayoutParams listParams;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static float hue = 0f;
    private static WeakReference<Activity> currentActivityRef;

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

                container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));

                listParams = new WindowManager.LayoutParams();
                listParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                listParams.format = PixelFormat.TRANSLUCENT;
                listParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                listParams.gravity = Gravity.TOP | Gravity.END;
                listParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
                listParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                listParams.x = dip2px(activity, 8);
                listParams.y = dip2px(activity, 8);

                try {
                    windowManager.addView(container, listParams);
                } catch (Exception e) {
                    XposedBridge.log("[HiveNext] List add error: " + e.getMessage());
                }

                startUpdateLoop(activity);
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
        container = null;
        listParams = null;
        windowManager = null;
    }

    private void startUpdateLoop(final Activity activity) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (container == null) {
                    handler.postDelayed(this, 50);
                    return;
                }

                synchronized (FeatureState.activeFeatures) {
                    boolean hasFeatureList = false;
                    for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                        if ("功能列表".equals(FeatureState.activeFeatures.get(i))) {
                            hasFeatureList = true;
                            break;
                        }
                    }

                    if (!hasFeatureList) {
                        container.setVisibility(View.GONE);
                        handler.postDelayed(this, 50);
                        return;
                    }

                    container.setVisibility(View.VISIBLE);
                    container.removeAllViews();

                    if (FeatureState.activeFeatures.size() == 0) {
                        handler.postDelayed(this, 50);
                        return;
                    }

                    List<String> sortedFeatures = new ArrayList<String>();
                    for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                        sortedFeatures.add(FeatureState.activeFeatures.get(i));
                    }

                    Collections.sort(sortedFeatures, new Comparator<String>() {
                        @Override
                        public int compare(String a, String b) {
                            return b.length() - a.length();
                        }
                    });

                    int total = sortedFeatures.size();

                    for (int i = 0; i < total; i++) {
                        String feature = sortedFeatures.get(i);

                        TextView tv = new TextView(activity);
                        tv.setText(feature);
                        tv.setTextSize(14);
                        tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                        tv.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));
                        tv.setBackgroundDrawable(createRoundRectBg(Color.parseColor("#8A000000"), dip2px(activity, 4)));

                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        lp.gravity = Gravity.END;
                        lp.bottomMargin = dip2px(activity, 4);
                        container.addView(tv, lp);

                        float itemHue = (hue - (i * 30f) + 360f) % 360f;
                       
                        int color = getRainbowColor(itemHue);
                        tv.setTextColor(color);
                    }
                }

                hue = (hue + 5f) % 360f;
                handler.postDelayed(this, 20);
            }
        }, 20);
    }

    private int getRainbowColor(float hue) {
        float[] hsv = new float[] { hue, 1.0f, 1.0f };
        return Color.HSVToColor(hsv);
    }

    private GradientDrawable createRoundRectBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius((float) radius);
        return drawable;
    }

    private int dip2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
