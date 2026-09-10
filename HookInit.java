package hive.next;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        new LogoHook().handleLoadPackage(lpparam);
        new MenuHook().handleLoadPackage(lpparam);
        new WaterHook().handleLoadPackage(lpparam);  
        new ListHook().handleLoadPackage(lpparam);
        new NotificationHook().handleLoadPackage(lpparam);
        new ShortcutHook().handleLoadPackage(lpparam);
        
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Toast.makeText((Activity) param.thisObject, "Hive Next", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
