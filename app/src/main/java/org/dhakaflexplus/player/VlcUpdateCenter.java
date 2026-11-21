package org.dhakaflexplus.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe, read-only upstream version information. Does NOT sideload/replace executable VLC code. */
public final class VlcUpdateCenter {
    public static final String ENGINE_VERSION="3.7.7";
    public static final String METADATA="https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/maven-metadata.xml";
    public static final String RELEASES="https://central.sonatype.com/artifact/org.videolan.android/libvlc-all/versions";
    private static final String KEY_AUTO="vlc_auto_check_on_open";
    private static final String KEY_CHECK="vlc_last_check_millis";
    private static final String KEY_FOUND="vlc_latest_stable";
    private static final long INTERVAL=24L*60*60*1000;
    private VlcUpdateCenter(){}
    private static SharedPreferences prefs(Context c){return new ServerStore(c).preferences();}
    public static boolean enabled(Context c){return prefs(c).getBoolean(KEY_AUTO,false);}
    public static void setEnabled(Context c,boolean enabled){prefs(c).edit().putBoolean(KEY_AUTO,enabled).apply();}
    public static boolean due(Context c){
        return enabled(c)&&(System.currentTimeMillis()-prefs(c).getLong(KEY_CHECK,0)>INTERVAL);
    }
    public static String previouslyFound(Context c){return prefs(c).getString(KEY_FOUND,ENGINE_VERSION);}
    /** Network call; call only on a background thread. */
    public static String fetchStableVersion(Context context)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(METADATA).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);
        c.setRequestProperty("Accept","application/xml,text/xml");
        String raw;
        try{
            if(c.getResponseCode()!=200)throw new IllegalStateException("Version server returned HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){if(bytes.size()+n>128*1024)throw new IllegalStateException("Version response too large");bytes.write(buf,0,n);}
                raw=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
        String newest=ENGINE_VERSION;
        Matcher m=Pattern.compile("<version>\\s*(3\\.\\d+\\.\\d+)\\s*</version>").matcher(raw);
        while(m.find())if(compare(m.group(1),newest)>0)newest=m.group(1);
        prefs(context).edit().putLong(KEY_CHECK,System.currentTimeMillis()).putString(KEY_FOUND,newest).apply();
        return newest;
    }
    /** Returns positive when a is newer than b, for stable numeric x.y.z only. */
    public static int compare(String a,String b){
        if(!a.matches("\\d+\\.\\d+\\.\\d+")||!b.matches("\\d+\\.\\d+\\.\\d+"))return 0;
        String[] x=a.split("\\."),y=b.split("\\.");
        for(int i=0;i<3;i++){
            int diff=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));
            if(diff!=0)return diff;
        }
        return 0;
    }
}
