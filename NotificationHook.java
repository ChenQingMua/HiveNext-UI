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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationHook implements IXposedHookLoadPackage {

    private static List<String> lastFeatures = new ArrayList<String>();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Context appContext;
    private static WindowManager windowManager;
    private static WeakReference<Activity> currentActivityRef;
    private static boolean isInitShown = false;

    private static final int COLOR_BLUE = Color.parseColor("#2196F3");
    private static final int COLOR_RED = Color.parseColor("#F44336");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");

    private static LinearLayout parentContainer;
    private static WindowManager.LayoutParams parentParams;
    private static boolean parentAdded = false;

    private static Queue<NotifyItem> pendingQueue = new LinkedList<NotifyItem>();
    private static boolean isProcessing = false;
    private static final int MAX_DISPLAY = 16;

    private static List<NotifyView> activeViews = new ArrayList<NotifyView>();

    private static final String[] BLOCK_FEATURES = new String[] {
        "功能列表", "开关通知", "水印", "信息"
    };

    static class NotifyItem {
        String message;
        boolean isOn;
        NotifyItem(String msg, boolean on) {
            message = msg;
            isOn = on;
        }
    }

    static class NotifyView {
        LinearLayout container;
        LinearLayout whiteCard;
        String message;
        boolean isOn;
        int fullWidth;
        int whiteFullWidth;
        boolean isRemoving;
    }

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

                createParentContainer();

                if (!isInitShown) {
                    isInitShown = true;
                    showNotification("Hive Next 模块已加载", true);
                }

                startCheckLoop();
            }
        });
    }

    private void cleanup() {
        if (parentContainer != null && windowManager != null) {
            try {
                windowManager.removeView(parentContainer);
            } catch (Exception e) {
            }
        }
        parentContainer = null;
        parentParams = null;
        parentAdded = false;
        pendingQueue.clear();
        activeViews.clear();
        isProcessing = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void createParentContainer() {
        if (appContext == null || windowManager == null) return;

        parentContainer = new LinearLayout(appContext);
        parentContainer.setOrientation(LinearLayout.VERTICAL);
        parentContainer.setGravity(Gravity.TOP | Gravity.START);

        parentParams = new WindowManager.LayoutParams();
        parentParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        parentParams.format = PixelFormat.TRANSLUCENT;
        parentParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        parentParams.gravity = Gravity.TOP | Gravity.START;
        parentParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        parentParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        parentParams.x = 0;
        parentParams.y = dip2px(appContext, 0);

        try {
            windowManager.addView(parentContainer, parentParams);
            parentAdded = true;
        } catch (Exception e) {
            parentAdded = false;
        }
    }

    private void startCheckLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (appContext == null || windowManager == null || !parentAdded) {
                    handler.postDelayed(this, 300);
                    return;
                }

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
                                addToQueue(feature + " 已开启", true);
                            }
                        }

                        for (int i = 0; i < lastFeatures.size(); i++) {
                            String feature = lastFeatures.get(i);
                            if (!isMainFeature(feature) && !currentFeatures.contains(feature)) {
                                addToQueue(feature + " 已关闭", false);
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

    private synchronized void addToQueue(String message, boolean isOn) {
        for (NotifyItem item : pendingQueue) {
            if (item.message.equals(message)) {
                return;
            }
        }
        for (NotifyView view : activeViews) {
            if (view.message.equals(message) && !view.isRemoving) {
                return;
            }
        }
        pendingQueue.add(new NotifyItem(message, isOn));
        processQueue();
    }

    private synchronized void processQueue() {
        if (isProcessing) return;
        if (pendingQueue.isEmpty()) return;
        if (parentContainer == null || !parentAdded) return;
        if (activeViews.size() >= MAX_DISPLAY) return;

        isProcessing = true;
        NotifyItem item = pendingQueue.poll();
        if (item != null) {
            showNotify(item.message, item.isOn);
        }
        isProcessing = false;

        if (!pendingQueue.isEmpty() && activeViews.size() < MAX_DISPLAY) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    processQueue();
                }
            }, 150);
        }
    }

    private void showNotify(final String message, final boolean isOn) {
        if (appContext == null || parentContainer == null) return;

        final NotifyView nv = new NotifyView();
        nv.message = message;
        nv.isOn = isOn;
        nv.isRemoving = false;

        nv.container = createNotifyView(nv, message, isOn);
        nv.container.setTag(message);
        nv.container.setVisibility(View.INVISIBLE);
        parentContainer.addView(nv.container);
        activeViews.add(nv);

        nv.container.post(new Runnable() {
            @Override
            public void run() {
                nv.fullWidth = nv.container.getMeasuredWidth();
                nv.whiteFullWidth = nv.whiteCard.getMeasuredWidth();
                if (nv.fullWidth <= 0) {
                    nv.fullWidth = dip2px(appContext, 150);
                }
                if (nv.whiteFullWidth <= 0) {
                    nv.whiteFullWidth = dip2px(appContext, 120);
                }

                LinearLayout.LayoutParams containerParams = (LinearLayout.LayoutParams) nv.container.getLayoutParams();
                containerParams.width = 0;
                nv.container.setLayoutParams(containerParams);

                LinearLayout.LayoutParams whiteParams = (LinearLayout.LayoutParams) nv.whiteCard.getLayoutParams();
                whiteParams.width = 0;
                nv.whiteCard.setLayoutParams(whiteParams);
                nv.whiteCard.setVisibility(View.INVISIBLE);

                nv.container.setVisibility(View.VISIBLE);

                animateWidth(nv.container, 0, nv.fullWidth, 250, new Runnable() {
                    @Override
                    public void run() {
                        nv.whiteCard.setVisibility(View.VISIBLE);
                        animateWidth(nv.whiteCard, 0, nv.whiteFullWidth, 200, new Runnable() {
                            @Override
                            public void run() {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        removeNotify(nv);
                                    }
                                }, 1234);
                            }
                        });
                    }
                });
            }
        });
    }

    private void removeNotify(final NotifyView nv) {
        if (nv == null || nv.isRemoving) return;
        if (nv.container == null) {
            nv.isRemoving = true;
            activeViews.remove(nv);
            processQueue();
            return;
        }

        nv.isRemoving = true;

        nv.container.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (nv.container != null && nv.container.getParent() != null) {
                        parentContainer.removeView(nv.container);
                    }
                    activeViews.remove(nv);
                    processQueue();
                }
            })
            .start();
    }

    private LinearLayout createNotifyView(NotifyView nv, String message, boolean isOn) {
        int bgColor = isOn ? COLOR_BLUE : COLOR_RED;

        LinearLayout container = new LinearLayout(appContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(bgColor);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.topMargin = 0;
        containerParams.bottomMargin = 0;
        container.setLayoutParams(containerParams);

        LinearLayout whiteCard = new LinearLayout(appContext);
        whiteCard.setOrientation(LinearLayout.VERTICAL);
        whiteCard.setBackgroundColor(COLOR_WHITE);

        LinearLayout.LayoutParams whiteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        whiteParams.rightMargin = dip2px(appContext, 10);
        whiteCard.setLayoutParams(whiteParams);
        container.addView(whiteCard);

        TextView textView = new TextView(appContext);
        textView.setText(message);
        textView.setTextSize(16);
        textView.setTextColor(Color.BLACK);
        textView.setSingleLine(true);
        textView.setMaxLines(1);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(
            dip2px(appContext, 10),
            dip2px(appContext, 10),
            dip2px(appContext, 10),
            dip2px(appContext, 10)
        );
        textView.setLayoutParams(textParams);
        whiteCard.addView(textView);

        nv.whiteCard = whiteCard;

        return container;
    }

    private void animateWidth(final View view, int from, final int to, int duration, final Runnable onComplete) {
        if (view == null) return;

        final int diff = to - from;
        final int steps = Math.max(10, duration / 16);
        final int delay = duration / steps;
        final int stepSize = diff / steps;

        final Handler animHandler = new Handler(Looper.getMainLooper());
        final int[] current = {from};
        final int[] step = {0};

        animHandler.post(new Runnable() {
            @Override
            public void run() {
                if (view == null) {
                    return;
                }

                step[0]++;
                current[0] += stepSize;

                if (step[0] >= steps || (diff > 0 && current[0] >= to) || (diff < 0 && current[0] <= to)) {
                    current[0] = to;
                }

                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
                params.width = current[0];
                view.setLayoutParams(params);

                if (step[0] < steps && current[0] != to) {
                    animHandler.postDelayed(this, delay);
                } else {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });
    }

    private boolean isMainFeature(String feature) {
        for (int i = 0; i < BLOCK_FEATURES.length; i++) {
            if (BLOCK_FEATURES[i].equals(feature)) {
                return true;
            }
        }
        return false;
    }

    private void showNotification(String message, boolean isOn) {
        addToQueue(message, isOn);
    }

    private int dip2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}