package hive.next;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

public class GradientManager {

    private static GradientManager instance;
    private static float baseHue = 0f;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static float speed = 4f;
    private static long interval = 32;
    private static boolean isRunning = false;

    private GradientManager() {}

    public static synchronized GradientManager getInstance() {
        if (instance == null) {
            instance = new GradientManager();
        }
        return instance;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        loop();
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void loop() {
        if (!isRunning) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                baseHue = (baseHue + speed) % 360f;
                handler.postDelayed(this, interval);
            }
        }, interval);
    }

    public float getCurrentHue() {
        return baseHue;
    }

    public float getHueWithOffset(float offset) {
        return (baseHue + offset + 720f) % 360f;
    }

    public static void setSpeed(float s) {
        speed = s;
    }

    public static void setInterval(long i) {
        interval = i;
    }

    public static float getSpeed() {
        return speed;
    }

    public static long getInterval() {
        return interval;
    }

    public static void setSpeedFast() {
        speed = 8f;
        interval = 16;
    }

    public static void setSpeedNormal() {
        speed = 3.5f;
        interval = 16;
    }

    public static void setSpeedSlow() {
        speed = 1f;
        interval = 50;
    }

    public static void setSpeedCustom(float s, long i) {
        speed = s;
        interval = i;
    }

    public static int getRainbowColor(float hue) {
        float[] hsv = new float[] { hue, 1.0f, 1.0f };
        return Color.HSVToColor(hsv);
    }

    public static int getRainbowColorWithOffset(float offset) {
        float hue = (baseHue + offset + 720f) % 360f;
        float[] hsv = new float[] { hue, 1.0f, 1.0f };
        return Color.HSVToColor(hsv);
    }
}
