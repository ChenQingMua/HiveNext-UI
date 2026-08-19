package hive.next;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    
    private static List<String> lastFeatures = new ArrayList<String>();
    private static Map<String, TextView> viewCache = new HashMap<String, TextView>();
    
    private static Paint measurePaint;

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

                listParams = new WindowManager.LayoutParams();
                listParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                listParams.format = PixelFormat.TRANSLUCENT;
                listParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                listParams.gravity = Gravity.TOP | Gravity.END;
                listParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                listParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

                try {
                    windowManager.addView(container, listParams);
                } catch (Exception e) {
                    XposedBridge.log("[HiveNext] List add error: " + e.getMessage());
                }

                measurePaint = new Paint();
                measurePaint.setTextSize(14f * activity.getResources().getDisplayMetrics().scaledDensity);

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
        lastFeatures.clear();
        viewCache.clear();
        measurePaint = null;
    }

    private void startUpdateLoop(final Activity activity) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (container == null || windowManager == null) {
                    handler.postDelayed(this, 20);
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
                        if (!viewCache.isEmpty()) {
                            container.removeAllViews();
                            viewCache.clear();
                            lastFeatures.clear();
                        }
                        container.setVisibility(View.GONE);
                        handler.postDelayed(this, 20);
                        return;
                    }

                    container.setVisibility(View.VISIBLE);

                    if (FeatureState.activeFeatures.size() == 0) {
                        if (!viewCache.isEmpty()) {
                            container.removeAllViews();
                            viewCache.clear();
                            lastFeatures.clear();
                        }
                        handler.postDelayed(this, 20);
                        return;
                    }

                    List<String> sortedFeatures = new ArrayList<String>();
                    for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                        sortedFeatures.add(FeatureState.activeFeatures.get(i));
                    }

                    Collections.sort(sortedFeatures, new Comparator<String>() {
                        @Override
                        public int compare(String a, String b) {
                            if (measurePaint == null) {
                                return b.length() - a.length();
                            }
                            float widthA = measurePaint.measureText(a);
                            float widthB = measurePaint.measureText(b);
                          
                            return Float.compare(widthB, widthA);
                        }
                    });

                    updateFeatureList(activity, sortedFeatures);
                }

                hue = (hue + 3.75f) % 360f;
                handler.postDelayed(this, 20);
            }
        }, 20);
    }

    private void updateFeatureList(final Activity activity, final List<String> newFeatures) {
        if (container == null) return;

        if (lastFeatures.equals(newFeatures)) {
            int total = newFeatures.size();
            for (int i = 0; i < total; i++) {
                String feature = newFeatures.get(i);
                TextView tv = viewCache.get(feature);
                if (tv != null) {
                    float itemHue = getItemHue(i, total);
                    tv.setTextColor(getRainbowColor(itemHue));
                }
            }
            return;
        }

        Iterator<Map.Entry<String, TextView>> it = viewCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TextView> entry = it.next();
            String feature = entry.getKey();
            if (!newFeatures.contains(feature)) {
                TextView tv = entry.getValue();
                if (tv.getParent() != null) {
                    container.removeView(tv);
                }
                it.remove();
            }
        }

        for (TextView tv : viewCache.values()) {
            if (tv.getParent() != null) {
                container.removeView(tv);
            }
        }

        int total = newFeatures.size();
        for (int i = 0; i < total; i++) {
            String feature = newFeatures.get(i);
            TextView tv = viewCache.get(feature);

            if (tv == null) {
                tv = new TextView(activity);
                tv.setText(feature);
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                tv.setPadding(dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4), dip2px(activity, 4));
                tv.setBackgroundColor(Color.parseColor("#8A000000"));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.gravity = Gravity.END;

                container.addView(tv, lp);
                viewCache.put(feature, tv);
            } else {
                container.addView(tv);
            }

            float itemHue = getItemHue(i, total);
            tv.setTextColor(getRainbowColor(itemHue));
        }

        lastFeatures = new ArrayList<String>(newFeatures);
    }

    private float getItemHue(int index, int total) {
        if (total <= 1) return hue;
        float step = total <= 12 ? 30f : 360f / total;
        return (hue - (index * step) + 720f) % 360f;
    }

    private int getRainbowColor(float hue) {
        float[] hsv = new float[] { hue, 1.0f, 1.0f };
        return Color.HSVToColor(hsv);
    }

    private int dip2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
