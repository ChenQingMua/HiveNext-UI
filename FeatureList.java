package hive.next;

public class FeatureList {

    public static final String[][] ALL_MENUS = {
        // Combat Menu
        {"Combat Menu", "杀戮光环", "碰撞箱", "自动瞄准", "刀刀暴击", "锁定背部", "环绕", "无限光环", "上帝模式"},
        // Move Menu
        {"Move Menu", "踏空", "移速", "兔子跳", "飞行", "飞船", "喷气背包", "穿墙", "自动行走", "自动跳跃", "安全行走", "虚空回弹", "反击退", "强制游泳", "水面行走", "反减速", "爬墙"},
        // World Menu
        {"World Menu", "秒挖", "范围破坏", "自动挖床", "自动搭路", "防踢出", "禁用器", "玩家传送", "建筑导入", "建筑导出", "自定义刷物", "自动吃食", "自动钓鱼", "刷屏", "发言绕过"},
        // Vision Menu
        {"Vision Menu", "玩家绘制", "床绘制", "容器绘制", "追踪线", "动画", "低火", "无火", "导入材质", "3D掉落物", "锁定相机", "自由相机", "运动相机", "X-Ray", "全亮", "渲染手"},
        // Main Menu
        {"Hive Next", "功能列表", "开关通知", "水印", "信息"}
    };

    public static final String[] DEFAULT_ENABLED = {
        "功能列表",
        "开关通知"
    };

    public static final float GOLDEN_ANGLE = 222f;

    public static int getTotalFeaturesCount() {
        int count = 0;
        for (String[] menu : ALL_MENUS) {
            count += menu.length - 1;
        }
        return count;
    }

    public static int getFeatureIndex(String featureName) {
        int index = 0;
        for (String[] menu : ALL_MENUS) {
            for (int i = 1; i < menu.length; i++) {
                if (menu[i].equals(featureName)) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    public static float getFeatureOffset(String featureName) {
        int index = getFeatureIndex(featureName);
        if (index < 0) return 0f;
        int total = getTotalFeaturesCount();
        float progress = total > 0 ? (float) index / total : 0f;
        float offset = -progress * GOLDEN_ANGLE;
        return (offset % 360f + 360f) % 360f;
    }

    public static float[] getAllOffsets() {
        int total = getTotalFeaturesCount();
        float[] offsets = new float[total];
        for (int i = 0; i < total; i++) {
            float progress = total > 0 ? (float) i / total : 0f;
            float offset = -progress * GOLDEN_ANGLE;
            offsets[i] = (offset % 360f + 360f) % 360f;
        }
        return offsets;
    }

    public static String[] getAllFeatureNames() {
        int total = getTotalFeaturesCount();
        String[] names = new String[total];
        int index = 0;
        for (String[] menu : ALL_MENUS) {
            for (int i = 1; i < menu.length; i++) {
                names[index++] = menu[i];
            }
        }
        return names;
    }
}