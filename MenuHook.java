package hive.next;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MenuHook implements IXposedHookLoadPackage {

    private static WindowManager windowManager;
    private static List<View> menuViews = new ArrayList<View>();
    private static List<WindowManager.LayoutParams> menuParamsList = new ArrayList<WindowManager.LayoutParams>();
    private static List<TextView> activeButtons = new ArrayList<TextView>();
    private static Map<String, TextView> featureButtons = new HashMap<String, TextView>();
    private static Map<TextView, Boolean> isLastItemMap = new HashMap<TextView, Boolean>();
    private static Map<TextView, Float> buttonOffsets = new HashMap<TextView, Float>();
    private static float lastX;
    private static float lastY;
    private static int paramX;
    private static int paramY;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> currentActivityRef;
    private static Context appContext;
    private static GradientManager gradientManager;

    private static final int COLOR_BLUE = Color.parseColor("#FF0034FF");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFFFF");
    private static final int COLOR_TRANSPARENT = Color.parseColor("#00000000");

    private static final String[][] ALL_MENUS = FeatureList.ALL_MENUS;
    private static final String[] DEFAULT_ENABLED = FeatureList.DEFAULT_ENABLED;

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
                appContext = activity.getApplicationContext();

                windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

                gradientManager = GradientManager.getInstance();
                gradientManager.start();

                for (String[] menu : ALL_MENUS) {
                    createMenuWindow(activity, menu, 0, 0);
                }

                syncActiveFeaturesToMenu();

                startGradientLoop();
                startLogLoop();
            }
        });
    }

    private void cleanup() {
        if (windowManager != null) {
            for (int i = 0; i < menuViews.size(); i++) {
                try {
                    windowManager.removeView(menuViews.get(i));
                } catch (Exception e) {
                }
            }
        }
        if (gradientManager != null) {
            gradientManager.stop();
        }
        menuViews.clear();
        menuParamsList.clear();
        activeButtons.clear();
        featureButtons.clear();
        isLastItemMap.clear();
        buttonOffsets.clear();
        windowManager = null;
    }

    private void syncActiveFeaturesToMenu() {
        synchronized (FeatureState.activeFeatures) {
            for (String activeFeature : FeatureState.activeFeatures) {
                TextView btn = featureButtons.get(activeFeature);
                if (btn != null) {
                    btn.setTag(true);
                    updateButtonStyleStatic(btn, true);
                    if (!activeButtons.contains(btn)) {
                        activeButtons.add(btn);
                    }
                }
            }
        }
    }

    private void createMenuWindow(final Activity activity, final String[] features, int offsetX, int offsetY) {
        final View menuView = createMenuView(activity, features);
        menuViews.add(menuView);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.CENTER;
        params.width = dip2px(activity, 140);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.x = offsetX;
        params.y = offsetY;
        menuParamsList.add(params);

        try {
            windowManager.addView(menuView, params);
        } catch (Exception e) {
            XposedBridge.log("[HiveNext] addView error: " + e.getMessage());
        }
    }

    private View createMenuView(final Context context, final String[] features) {
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
            dip2px(context, 140),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#54FFFFFF"));
        bg.setCornerRadius(dip2px(context, 6));
        container.setBackgroundDrawable(bg);

        final String titleText = features[0];

        final TextView title = new TextView(context);
        title.setText(titleText);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(COLOR_BLUE);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
            dip2px(context, 140),
            dip2px(context, 36)
        ));

        GradientDrawable titleBg = new GradientDrawable();
        titleBg.setColor(COLOR_WHITE);
        titleBg.setCornerRadii(new float[]{
            dip2px(context, 6), dip2px(context, 6),
            dip2px(context, 6), dip2px(context, 6),
            dip2px(context, 6), dip2px(context, 6),
            dip2px(context, 6), dip2px(context, 6)
        });
        
        title.setBackgroundDrawable(titleBg);
        container.addView(title);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            dip2px(context, 140),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scrollView.setVisibility(View.GONE);

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
            dip2px(context, 140),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        for (int i = 1; i < features.length; i++) {
            final String featureName = features[i];
            final TextView btn = new TextView(context);
            btn.setText(featureName);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dip2px(context, 2), dip2px(context, 2), dip2px(context, 2), dip2px(context, 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dip2px(context, 140),
                dip2px(context, 36)
            );
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            btn.setLayoutParams(lp);

            boolean isAlreadyActive = false;
            synchronized (FeatureState.activeFeatures) {
                isAlreadyActive = FeatureState.activeFeatures.contains(featureName);
            }

            boolean isDefaultOn = false;
            for (String enabled : DEFAULT_ENABLED) {
                if (featureName.equals(enabled)) {
                    isDefaultOn = true;
                    break;
                }
            }
            
            if (isAlreadyActive) {
                isDefaultOn = true;
            }
            
            btn.setTag(isDefaultOn);
            
            boolean isLastItem = (i == features.length - 1);
            isLastItemMap.put(btn, isLastItem);
            
            float offset = FeatureList.getFeatureOffset(featureName);
            buttonOffsets.put(btn, offset);
            
            updateButtonStyle(btn, isDefaultOn, isLastItem);

            if (isDefaultOn) {
                activeButtons.add(btn);
                synchronized (FeatureState.activeFeatures) {
                    if (!FeatureState.activeFeatures.contains(featureName)) {
                        FeatureState.activeFeatures.add(featureName);
                    }
                }
            }

            content.addView(btn);
            featureButtons.put(featureName, btn);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isOn = btn.getTag() != null && (Boolean) btn.getTag();
                    isOn = !isOn;
                    btn.setTag(isOn);
                    boolean isLast = isLastItemMap.containsKey(btn) && isLastItemMap.get(btn);
                    updateButtonStyle(btn, isOn, isLast);

                    synchronized (FeatureState.activeFeatures) {
                        if (isOn) {
                            activeButtons.add(btn);
                            if (!FeatureState.activeFeatures.contains(featureName)) {
                                FeatureState.activeFeatures.add(featureName);
                            }
                        } else {
                            activeButtons.remove(btn);
                            FeatureState.activeFeatures.remove(featureName);
                            GradientDrawable resetBg = new GradientDrawable();
                            resetBg.setColor(COLOR_TRANSPARENT);
                            if (isLast) {
                                resetBg.setCornerRadii(new float[]{
                                    0, 0,
                                    0, 0,
                                    dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6),
                                    dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6)
                                });
                            }
                            btn.setBackgroundDrawable(resetBg);
                        }
                    }

                    ShortcutHook.syncShortcutState(featureName, isOn);
                }
            });

            setupLongPress(btn, featureName, context);
        }

        scrollView.addView(content);
        container.addView(scrollView);

        title.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int windowIndex = menuViews.indexOf(container);
                if (windowIndex < 0) return false;

                final WindowManager.LayoutParams params = menuParamsList.get(windowIndex);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        paramX = params.x;
                        paramY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX;
                        float dy = event.getRawY() - lastY;
                        params.x = paramX + (int) dx;
                        params.y = paramY + (int) dy;
                        if (windowManager != null && menuViews.get(windowIndex) != null) {
                            windowManager.updateViewLayout(menuViews.get(windowIndex), params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        float moveX = Math.abs(event.getRawX() - lastX);
                        float moveY = Math.abs(event.getRawY() - lastY);
                        if (moveX < 10 && moveY < 10) {
                            boolean isExpanded = scrollView.getVisibility() == View.VISIBLE;
                            if (isExpanded) {
                                scrollView.setVisibility(View.GONE);
                                GradientDrawable titleBgCollapsed = new GradientDrawable();
                                titleBgCollapsed.setColor(COLOR_WHITE);
                                titleBgCollapsed.setCornerRadii(new float[]{
                                    dip2px(context, 6), dip2px(context, 6),
                                    dip2px(context, 6), dip2px(context, 6),
                                    dip2px(context, 6), dip2px(context, 6),
                                    dip2px(context, 6), dip2px(context, 6)
                                });
                                title.setBackgroundDrawable(titleBgCollapsed);
                                
                                for (int i = 0; i < content.getChildCount(); i++) {
                                    TextView child = (TextView) content.getChildAt(i);
                                    boolean isOn = child.getTag() != null && (Boolean) child.getTag();
                                    boolean isLast = isLastItemMap.containsKey(child) && isLastItemMap.get(child);
                                    updateButtonStyle(child, isOn, isLast);
                                }
                            } else {
                                scrollView.setVisibility(View.VISIBLE);
                                GradientDrawable titleBgExpanded = new GradientDrawable();
                                titleBgExpanded.setColor(COLOR_WHITE);
                                titleBgExpanded.setCornerRadii(new float[]{
                                    dip2px(context, 6), dip2px(context, 6),
                                    dip2px(context, 6), dip2px(context, 6),
                                    0, 0,
                                    0, 0
                                });
                                title.setBackgroundDrawable(titleBgExpanded);
                                
                                for (int i = 0; i < content.getChildCount(); i++) {
                                    TextView child = (TextView) content.getChildAt(i);
                                    boolean isOn = child.getTag() != null && (Boolean) child.getTag();
                                    boolean isLast = isLastItemMap.containsKey(child) && isLastItemMap.get(child);
                                    updateButtonStyle(child, isOn, isLast);
                                }
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        return container;
    }

    private void setupLongPress(final TextView button, final String featureName, final Context context) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private long downTime = 0;
            private float downX = 0;
            private float downY = 0;
            private boolean isLongPress = false;
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (SystemClock.elapsedRealtime() - downTime >= 600) {
                        isLongPress = true;
                        ShortcutHook.toggleShortcut(context, featureName);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = SystemClock.elapsedRealtime();
                        downX = event.getRawX();
                        downY = event.getRawY();
                        isLongPress = false;
                        handler.postDelayed(longPressRunnable, 600);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = SystemClock.elapsedRealtime() - downTime;
                        float moveX = Math.abs(event.getRawX() - downX);
                        float moveY = Math.abs(event.getRawY() - downY);

                        handler.removeCallbacks(longPressRunnable);

                        if (!isLongPress && duration < 600 && moveX < 10 && moveY < 10) {
                            button.performClick();
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

    private void updateButtonStyle(TextView button, boolean isOn, boolean isLastItem) {
        if (isOn) {
            button.setTextColor(COLOR_WHITE);
            float offset = buttonOffsets.containsKey(button) ? buttonOffsets.get(button) : 0f;
            int color = GradientManager.getRainbowColorWithOffset(offset);
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(color);
            if (isLastItem) {
                activeBg.setCornerRadii(new float[]{
                    0, 0,
                    0, 0,
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6),
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6)
                });
            } else {
                activeBg.setCornerRadius(0);
            }
            button.setBackgroundDrawable(activeBg);
        } else {
            button.setTextColor(COLOR_BLUE);
            GradientDrawable resetBg = new GradientDrawable();
            resetBg.setColor(COLOR_TRANSPARENT);
            if (isLastItem) {
                resetBg.setCornerRadii(new float[]{
                    0, 0,
                    0, 0,
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6),
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6)
                });
            } else {
                resetBg.setCornerRadius(0);
            }
            button.setBackgroundDrawable(resetBg);
            button.getPaint().setShader(null);
            button.invalidate();
        }
    }

    public static void syncButtonState(String featureName, boolean isOn) {
        TextView btn = featureButtons.get(featureName);
        if (btn != null) {
            btn.setTag(isOn);
            boolean isLast = isLastItemMap.containsKey(btn) && isLastItemMap.get(btn);
            updateButtonStyleStaticWithLast(btn, isOn, isLast);
            if (isOn) {
                if (!activeButtons.contains(btn)) {
                    activeButtons.add(btn);
                }
                synchronized (FeatureState.activeFeatures) {
                    if (!FeatureState.activeFeatures.contains(featureName)) {
                        FeatureState.activeFeatures.add(featureName);
                    }
                }
            } else {
                activeButtons.remove(btn);
                synchronized (FeatureState.activeFeatures) {
                    FeatureState.activeFeatures.remove(featureName);
                }
                GradientDrawable resetBg = new GradientDrawable();
                resetBg.setColor(COLOR_TRANSPARENT);
                if (isLast) {
                    resetBg.setCornerRadii(new float[]{
                        0, 0,
                        0, 0,
                        dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6),
                        dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6)
                    });
                }
                btn.setBackgroundDrawable(resetBg);
            }
        }
    }

    private static void updateButtonStyleStatic(TextView button, boolean isOn) {
        if (isOn) {
            button.setTextColor(COLOR_WHITE);
            float offset = buttonOffsets.containsKey(button) ? buttonOffsets.get(button) : 0f;
            int color = GradientManager.getRainbowColorWithOffset(offset);
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(color);
            button.setBackgroundDrawable(activeBg);
        } else {
            button.setTextColor(COLOR_BLUE);
            GradientDrawable resetBg = new GradientDrawable();
            resetBg.setColor(COLOR_TRANSPARENT);
            button.setBackgroundDrawable(resetBg);
            button.getPaint().setShader(null);
            button.invalidate();
        }
    }

    private static void updateButtonStyleStaticWithLast(TextView button, boolean isOn, boolean isLastItem) {
        if (isOn) {
            button.setTextColor(COLOR_WHITE);
            float offset = buttonOffsets.containsKey(button) ? buttonOffsets.get(button) : 0f;
            int color = GradientManager.getRainbowColorWithOffset(offset);
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(color);
            if (isLastItem) {
                activeBg.setCornerRadii(new float[]{
                    0, 0,
                    0, 0,
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6),
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6)
                });
            } else {
                activeBg.setCornerRadius(0);
            }
            button.setBackgroundDrawable(activeBg);
        } else {
            button.setTextColor(COLOR_BLUE);
            GradientDrawable resetBg = new GradientDrawable();
            resetBg.setColor(COLOR_TRANSPARENT);
            if (isLastItem) {
                resetBg.setCornerRadii(new float[]{
                    0, 0,
                    0, 0,
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6),
                    dip2px(button.getContext(), 6), dip2px(button.getContext(), 6)
                });
            } else {
                resetBg.setCornerRadius(0);
            }
            button.setBackgroundDrawable(resetBg);
            button.getPaint().setShader(null);
            button.invalidate();
        }
    }

    private void startGradientLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gradientManager == null) return;
                for (int i = 0; i < activeButtons.size(); i++) {
                    TextView btn = activeButtons.get(i);
                    if (btn != null) {
                        float offset = buttonOffsets.containsKey(btn) ? buttonOffsets.get(btn) : 0f;
                        int color = GradientManager.getRainbowColorWithOffset(offset);
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(color);
                        boolean isLast = isLastItemMap.containsKey(btn) && isLastItemMap.get(btn);
                        if (isLast) {
                            bg.setCornerRadii(new float[]{
                                0, 0,
                                0, 0,
                                dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6),
                                dip2px(btn.getContext(), 6), dip2px(btn.getContext(), 6)
                            });
                        } else {
                            bg.setCornerRadius(0);
                        }
                        btn.setBackgroundDrawable(bg);
                    }
                }
                handler.postDelayed(this, 50);
            }
        }, 50);
    }

    private void startLogLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (FeatureState.activeFeatures) {
                    if (FeatureState.activeFeatures.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("[HiveNext] Active: ");
                        for (int i = 0; i < FeatureState.activeFeatures.size(); i++) {
                            sb.append(FeatureState.activeFeatures.get(i));
                            if (i < FeatureState.activeFeatures.size() - 1) {
                                sb.append(", ");
                            }
                        }
                        XposedBridge.log(sb.toString());
                    }
                }
                handler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private static int dip2px(Context context, float dp) {
        if (context == null) {
            context = appContext;
        }
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}