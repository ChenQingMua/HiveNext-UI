package hive.next;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationHook implements IXposedHookLoadPackage {

    private static List<String> lastFeatures = new ArrayList<String>();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Context appContext;
    private static WindowManager windowManager;
    private static LinearLayout notifyItem;
    private static TextView notifyText;
    private static WindowManager.LayoutParams notifyParams;
    private static Runnable hideRunnable;
    private static ValueAnimator currentAnimator;
    private static int notifyHeight = 0;
    private static WeakReference<Activity> currentActivityRef;

    private static final String[] MAIN_FEATURES = new String[] {
        "功能列表",
        "开关通知",
        "侧边水印",
        "屏幕水印"
    };

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
                appContext = activity;
                windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

                startCheckLoop();
            }
        });
    }

    private void cleanup() {
        if (notifyItem != null && windowManager != null) {
            try {
                windowManager.removeView(notifyItem);
            } catch (Exception e) {
            }
        }
        notifyItem = null;
        notifyText = null;
        notifyParams = null;
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
        }
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
    }

    private void startCheckLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (FeatureState.activeFeatures) {
                    List<String> currentFeatures = new ArrayList<String>();
                    for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                        currentFeatures.add(FeatureState.activeFeatures.get(i));
                    }

                    boolean hasNotify = false;
                    for (int i = 0; i < currentFeatures.size(); i++) {
                        if ("开关通知".equals(currentFeatures.get(i))) {
                            hasNotify = true;
                            break;
                        }
                    }

                    if (hasNotify) {
                        for (int i = 0; i < currentFeatures.size(); i++) {
                            String feature = currentFeatures.get(i);
                            if (!isMainFeature(feature) && !lastFeatures.contains(feature)) {
                                showOrUpdateNotify(feature + " 已开启", true);
                            }
                        }

                        for (int i = 0; i < lastFeatures.size(); i++) {
                            String feature = lastFeatures.get(i);
                            if (!isMainFeature(feature) && !currentFeatures.contains(feature)) {
                                showOrUpdateNotify(feature + " 已关闭", false);
                            }
                        }
                    }

                    lastFeatures.clear();
                    for (int i = 0; i < currentFeatures.size(); i++) {
                        lastFeatures.add(currentFeatures.get(i));
                    }
                }

                handler.postDelayed(this, 300);
            }
        }, 300);
    }

    private void showOrUpdateNotify(final String message, final boolean isOn) {
        if (appContext == null || windowManager == null) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (hideRunnable != null) {
                    handler.removeCallbacks(hideRunnable);
                }

                if (notifyItem == null) {
                    notifyItem = new LinearLayout(appContext);
                    notifyItem.setOrientation(LinearLayout.HORIZONTAL);
                    notifyItem.setGravity(Gravity.CENTER);
                    notifyItem.setPadding(dip2px(appContext, 16), dip2px(appContext, 10), dip2px(appContext, 16), dip2px(appContext, 10));

                    notifyText = new TextView(appContext);
                    notifyText.setTextSize(14);
                    notifyText.setTextColor(Color.parseColor("#FFFFFFFF"));
                    notifyText.setGravity(Gravity.CENTER);
                    notifyItem.addView(notifyText);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#E6008A2E"));
                    bg.setCornerRadius((float) dip2px(appContext, 4));
                    notifyItem.setBackgroundDrawable(bg);

                    notifyParams = new WindowManager.LayoutParams();
                    notifyParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                    notifyParams.format = PixelFormat.TRANSLUCENT;
                    notifyParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    notifyParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    notifyParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
                    notifyParams.height = 0;
                    notifyParams.y = dip2px(appContext, 20);

                    try {
                        windowManager.addView(notifyItem, notifyParams);
                    } catch (Exception e) {
                    }

                    notifyItem.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyHeight = notifyItem.getMeasuredHeight();
                            if (notifyHeight <= 0) {
                                notifyHeight = dip2px(appContext, 40);
                            }
                            animateSlideIn();
                        }
                    });
                }

                GradientDrawable bg = new GradientDrawable();
                if (isOn) {
                    bg.setColor(Color.parseColor("#E6008A2E"));
                } else {
                    bg.setColor(Color.parseColor("#E6CC0000"));
                }
                bg.setCornerRadius((float) dip2px(appContext, 4));
                notifyItem.setBackgroundDrawable(bg);

                notifyText.setText(message);

                hideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        animateSlideOut();
                    }
                };
                handler.postDelayed(hideRunnable, 1500);
            }
        });
    }

    private void animateSlideIn() {
        if (notifyItem == null || notifyParams == null) return;

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        final int startHeight = 0;
        final int endHeight = notifyHeight;

        currentAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        currentAnimator.setDuration(300);
        currentAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (notifyItem == null || notifyParams == null) return;
                notifyParams.height = (Integer) animation.getAnimatedValue();
                try {
                    windowManager.updateViewLayout(notifyItem, notifyParams);
                } catch (Exception e) {
                }
            }
        });
        currentAnimator.start();
    }

    private void animateSlideOut() {
        if (notifyItem == null || notifyParams == null) return;

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        final int startHeight = notifyHeight;
        final int endHeight = 0;

        currentAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        currentAnimator.setDuration(300);
        currentAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (notifyItem == null || notifyParams == null) return;
                notifyParams.height = (Integer) animation.getAnimatedValue();
                try {
                    windowManager.updateViewLayout(notifyItem, notifyParams);
                } catch (Exception e) {
                }
            }
        });
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (notifyItem != null && windowManager != null) {
                    try {
                        windowManager.removeView(notifyItem);
                    } catch (Exception e) {
                    }
                    notifyItem = null;
                    notifyText = null;
                    notifyParams = null;
                }
            }
        });
        currentAnimator.start();
    }

    private boolean isMainFeature(String feature) {
        for (int i = 0; i < MAIN_FEATURES.length; i++) {
            if (MAIN_FEATURES[i].equals(feature)) {
                return true;
            }
        }
        return false;
    }

    private int dip2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
