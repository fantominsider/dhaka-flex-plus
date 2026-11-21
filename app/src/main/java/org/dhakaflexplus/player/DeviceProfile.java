package org.dhakaflexplus.player;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import java.util.Locale;

/** Best-effort automatic *host* classification, with explicit user override.
 *  USB-C XREAL glasses usually identify as an external display, not a reliable brand ID.
 *  Playback capability is checked at runtime; a model name is not proof of OpenXR support.
 */
public final class DeviceProfile {
    public enum Kind { HANDHELD, ANDROID_TV, PICO, QUEST, OTHER_XR, EXTERNAL_DISPLAY }
    public static final String KEY="display_profile";
    public static final String[] OPTIONS={"Automatic", "Phone / tablet", "Android TV", "VR headset", "XREAL / external display"};
    public static final String[] VALUES={"auto", "handheld", "tv", "vr", "external"};
    private DeviceProfile() {}
    public static Kind detected(Context context) {
        String vendor=(Build.MANUFACTURER+" "+Build.BRAND+" "+Build.MODEL+" "+Build.DEVICE).toLowerCase(Locale.ROOT);
        if(vendor.contains("pico")||vendor.contains("bytedance"))return Kind.PICO;
        if(vendor.contains("quest")||vendor.contains("oculus")||vendor.contains("facebook")||vendor.contains("meta horizon"))return Kind.QUEST;
        PackageManager pm=context.getPackageManager();
        if(pm.hasSystemFeature("android.hardware.vr.high_performance"))return Kind.OTHER_XR;
        UiModeManager mode=(UiModeManager)context.getSystemService(Context.UI_MODE_SERVICE);
        if(mode!=null&&mode.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION)return Kind.ANDROID_TV;
        if(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK))return Kind.ANDROID_TV;
        DisplayManager displays=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        if(displays!=null){
            for(Display display:displays.getDisplays()){
                if(display.getDisplayId()!=Display.DEFAULT_DISPLAY)return Kind.EXTERNAL_DISPLAY;
            }
        }
        return Kind.HANDHELD;
    }
    public static Kind active(Context ctx) {
        String override=new ServerStore(ctx).preferences().getString(KEY,"auto");
        if("handheld".equals(override))return Kind.HANDHELD;
        if("tv".equals(override))return Kind.ANDROID_TV;
        if("vr".equals(override))return Kind.OTHER_XR;
        if("external".equals(override))return Kind.EXTERNAL_DISPLAY;
        return detected(ctx);
    }
    public static boolean isVr(Kind k){return k==Kind.PICO||k==Kind.QUEST||k==Kind.OTHER_XR;}
    public static String label(Kind k){
        switch(k){
            case PICO:return "PICO XR headset";
            case QUEST:return "Meta Quest headset";
            case OTHER_XR:return "VR/XR headset (manual or generic)";
            case ANDROID_TV:return "Android TV";
            case EXTERNAL_DISPLAY:return "External display (XREAL-compatible mode)";
            default:return "Android phone or tablet";
        }
    }
}
