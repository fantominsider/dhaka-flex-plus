package org.dhakaflexplus.player;

import java.util.Locale;

/** Display presets; no speculative automatic 2D->3D conversion. */
public final class PlaybackModes {
    public static final String[] LABELS={
        "Normal 2D (built-in VLC player)",
        "XREAL 3D: fullscreen raw SBS (set glasses to SBS)",
        "VR theater: 2D cinema screen",
        "VR theater: 3D side-by-side (SBS)",
        "VR theater: 3D top / bottom (OU)",
        "VR 180°: monoscopic",
        "VR 180°: stereo SBS",
        "VR 360°: monoscopic",
        "VR 360°: stereo SBS",
        "VR 360°: stereo top / bottom"
    };
    public static final String[] MODES={
        "flat","xreal-sbs","theater","theater-sbs","theater-tb",
        "vr180","vr180-sbs","vr360","vr360-sbs","vr360-tb"
    };
    private PlaybackModes() {}
    public static String detectFromTitle(String title) {
        String s=title==null?"":title.toLowerCase(Locale.ROOT);
        boolean tb=s.contains("top.bottom")||s.contains("top-bottom")||s.contains("over.under")||s.contains("-ou-");
        boolean stereo=s.contains("sbs")||s.contains("side-by-side")||s.contains("side_by_side");
        if(s.contains("vr360")||s.contains("360°")||s.contains("360deg")||s.contains("360-video")||s.contains("360 video")||s.contains("_360_")||s.contains("-360-"))return tb?"vr360-tb":stereo?"vr360-sbs":"vr360";
        if(s.contains("vr180")||s.contains("180°")||s.contains("_180_"))return stereo?"vr180-sbs":"vr180";
        if(tb)return "theater-tb";
        if(stereo)return "theater-sbs";
        return "theater";
    }
    public static boolean isXr(String mode){return mode!=null&&(mode.startsWith("theater")||mode.startsWith("vr180")||mode.startsWith("vr360"));}
    public static String name(String mode){for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return LABELS[i];return LABELS[0];}
}
