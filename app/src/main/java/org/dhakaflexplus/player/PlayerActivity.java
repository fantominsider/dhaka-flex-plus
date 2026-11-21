package org.dhakaflexplus.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Built-in libVLC video/audio player, custom UI, and existing WebXR browser handoff. */
public final class PlayerActivity extends Activity {
    private String url,title,mode;
    private ServerStore store;
    private LibVLC vlc;
    private MediaPlayer player;
    private VLCVideoLayout surface;
    private TextView status,playButton;
    private boolean prepared=false,finished=false,completed=false,seekable=false;
    private long lastCheckpoint=0;
    private int sleepMinutes=0;
    private Runnable sleepTask;
    private float rate=1f;
    private String aspect="Default";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkpointTask=new Runnable(){
        @Override public void run(){checkpoint();if(!finished)handler.postDelayed(this,4000);}
    };
    private int d(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        url=getIntent().getStringExtra("media_url");title=getIntent().getStringExtra("media_title");
        if(url==null || (!IptvParser.isStreamUrl(url) && !url.startsWith("content://"))){finish();return;}
        if(title==null||title.isEmpty())title="Media";
        store=new ServerStore(this);
        if(LibraryBackup.safeMediaUrl(url))WatchHistory.visit(this,url,title);
        mode=getIntent().getStringExtra("view_mode");
        DeviceProfile.Kind device=DeviceProfile.active(this);
        if(mode==null||mode.isEmpty()){
            if(DeviceProfile.isVr(device))mode=PlaybackModes.detectFromTitle(title);
            else if(device==DeviceProfile.Kind.EXTERNAL_DISPLAY && title.toLowerCase(Locale.ROOT).contains("sbs"))mode="xreal-sbs";
            else mode="flat";
        }
        getWindow().setStatusBarColor(Color.BLACK);
        boolean fullscreen="xreal-sbs".equals(mode);
        if(fullscreen){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        if(!fullscreen){
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(button("‹",this::finish),new LinearLayout.LayoutParams(d(50),d(48)));
            TextView name=new TextView(this);name.setText(title);name.setTextColor(Color.WHITE);
            name.setTextSize(16);name.setMaxLines(2);head.addView(name,new LinearLayout.LayoutParams(0,d(54),1));
            head.addView(button("Display",this::chooseMode),new LinearLayout.LayoutParams(d(104),d(48)));
            root.addView(head);
        }
        surface=new VLCVideoLayout(this);surface.setKeepScreenOn(true);
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        if(!fullscreen){
            HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
            playButton=button("Pause",this::togglePlayback);controls.addView(playButton);
            controls.addView(button("−15s",()->seekBy(-15000)));
            controls.addView(button("+30s",()->seekBy(30000)));
            controls.addView(button("Audio",()->chooseTracks(true)));
            controls.addView(button("Subtitles",()->chooseTracks(false)));
            controls.addView(button("Speed",this::chooseSpeed));
            controls.addView(button("Aspect",this::chooseAspect));
            controls.addView(button("Volume",this::chooseVolume));
            controls.addView(button("Sleep",this::chooseSleepTimer));
            controls.addView(button("Other player",()->openExternal(this,url)));
            scroller.addView(controls);root.addView(scroller);
            status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(12);
            status.setGravity(Gravity.CENTER);status.setPadding(d(6),d(4),d(6),d(6));
            status.setText("Loading with embedded VLC · swipe the control strip for more options");root.addView(status);
        }
        if(PlaybackModes.isXr(mode)){
            // LibVLC is Android's normal/XREAL player. WebXR still uses the headset browser's video decoding.
            startXr(mode);
        }else startVlc();
    }
    private Button button(String label,Runnable click){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);
        b.setOnClickListener(v->click.run());b.setFocusable(true);return b;
    }
    private void startVlc(){
        try{
            vlc=new LibVLC(this,new ArrayList<String>());
            player=new MediaPlayer(vlc);
            player.attachViews(surface,null,false,false);
            player.setEventListener(event->runOnUiThread(()->handleEvent(event.type)));
            Media media=new Media(vlc,Uri.parse(url));
            boolean software=store.preferences().getBoolean("vlc_software_decoder",false);
            media.setHWDecoderEnabled(!software,false);
            int cache=store.preferences().getInt("vlc_cache_ms",1500);
            cache=Math.max(500,Math.min(10000,cache));
            media.addOption(":network-caching="+cache);
            player.setMedia(media);media.release();
            prepared=true;
            player.play();
            handler.postDelayed(checkpointTask,4000);
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("VLC initialization failed")
                .setMessage("The embedded media engine could not start: "+e.getMessage())
                .setPositiveButton("Close",(dialog,which)->finish()).show();
        }
    }
    private void handleEvent(int type){
        if(finished||player==null)return;
        if(type==MediaPlayer.Event.Playing){
            if(status!=null)status.setText("Playing with embedded LibVLC");
            if(playButton!=null)playButton.setText("Pause");
            long saved=getResumePosition();
            if(saved>0&&!completed && lastCheckpoint==0){
                // Avoid repeatedly returning to the bookmark on pause/resume.
                lastCheckpoint=1;
                handler.postDelayed(()->{
                    if(player!=null && player.isSeekable())player.setTime(saved);
                },350);
            }
        }else if(type==MediaPlayer.Event.Paused){
            if(playButton!=null)playButton.setText("Play");
        }else if(type==MediaPlayer.Event.EndReached){
            completed=true;
            if(LibraryBackup.safeMediaUrl(url)){
                store.preferences().edit().remove("position_"+url.hashCode())
                    .putBoolean("watched_"+url.hashCode(),true).apply();
                WatchHistory.mark(this,url,title,0,clamp(player.getLength()),true);
            }
            if(sleepTask!=null)handler.removeCallbacks(sleepTask);
            if(playButton!=null)playButton.setText("Play");
            if(status!=null)status.setText("Playback finished");
        }else if(type==MediaPlayer.Event.EncounteredError){
            new AlertDialog.Builder(this).setTitle("Media playback failed")
                .setMessage("LibVLC could not open or decode this media. Verify the address, server access, format, and device capabilities.")
                .setNegativeButton("Close",(d,w)->finish())
                .setPositiveButton("External player",(d,w)->openExternal(this,url)).show();
        }
    }
    private int clamp(long value){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,value));}
    private long getResumePosition(){
        int legacy=store.preferences().getInt("position_"+url.hashCode(),0);
        for(WatchHistory.Item item:WatchHistory.load(this))if(item.url.equals(url))
            return item.completed?0:Math.max(legacy,item.positionMs);
        return legacy;
    }
    private void checkpoint(){
        if(!prepared||completed||player==null||!LibraryBackup.safeMediaUrl(url))return;
        long pos=player.getTime(),length=player.getLength();
        if(pos>0){
            int safe=clamp(pos);
            store.preferences().edit().putInt("position_"+url.hashCode(),safe).apply();
            WatchHistory.mark(this,url,title,safe,clamp(length),false);
        }
    }
    private void togglePlayback(){
        if(player==null)return;
        if(player.isPlaying()){checkpoint();player.pause();if(playButton!=null)playButton.setText("Play");}
        else{player.play();if(playButton!=null)playButton.setText("Pause");}
    }
    private void seekBy(long ms){
        if(player==null||!player.isSeekable()){toast("This stream cannot be seeked (common for live TV)");return;}
        long next=Math.max(0,player.getTime()+ms),length=player.getLength();
        if(length>0)next=Math.min(next,length-1);
        player.setTime(next);
    }
    private void chooseTracks(boolean audio){
        if(player==null)return;
        MediaPlayer.TrackDescription[] options=audio?player.getAudioTracks():player.getSpuTracks();
        if(options==null||options.length==0){toast("No "+(audio?"audio":"subtitle")+" tracks reported yet");return;}
        String[] labels=new String[options.length];int selected=-1;
        int active=audio?player.getAudioTrack():player.getSpuTrack();
        for(int i=0;i<options.length;i++){
            labels[i]=options[i].name==null?"Track "+options[i].id:options[i].name;
            if(options[i].id==active)selected=i;
        }
        new AlertDialog.Builder(this).setTitle(audio?"Choose audio language":"Choose embedded subtitles")
            .setSingleChoiceItems(labels,selected,(dialog,index)->{
                if(audio)player.setAudioTrack(options[index].id);
                else player.setSpuTrack(options[index].id);
                dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void chooseSpeed(){
        float[] rates={0.5f,0.75f,1f,1.25f,1.5f,2f};String[] names={"0.5×","0.75×","1× · normal","1.25×","1.5×","2×"};
        int current=2;for(int i=0;i<rates.length;i++)if(rate==rates[i])current=i;
        new AlertDialog.Builder(this).setTitle("Playback speed")
            .setSingleChoiceItems(names,current,(dialog,i)->{
                if(player!=null){rate=rates[i];player.setRate(rate);}dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void chooseAspect(){
        String[] choices={"Default (source aspect)","16:9","4:3","21:9","1:1"};
        String[] ratios={null,"16:9","4:3","21:9","1:1"};
        int current=0;for(int i=1;i<ratios.length;i++)if(ratios[i].equals(aspect))current=i;
        new AlertDialog.Builder(this).setTitle("Video aspect ratio")
            .setSingleChoiceItems(choices,current,(dialog,index)->{
                if(player!=null)player.setAspectRatio(ratios[index]);
                aspect=ratios[index]==null?"Default":ratios[index];dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void chooseVolume(){
        if(player==null)return;
        String[] names={"0% (mute)","25%","50%","75%","100%"};int[] levels={0,25,50,75,100};
        int current=Math.min(4,Math.max(0,player.getVolume()/25));
        new AlertDialog.Builder(this).setTitle("Playback volume")
            .setSingleChoiceItems(names,current,(dialog,index)->{player.setVolume(levels[index]);dialog.dismiss();})
            .setNegativeButton("Cancel",null).show();
    }
    private void chooseSleepTimer(){
        String[] labels={"Off","15 minutes","30 minutes","60 minutes","90 minutes"};int[] times={0,15,30,60,90};
        int current=0;for(int i=0;i<times.length;i++)if(times[i]==sleepMinutes)current=i;
        new AlertDialog.Builder(this).setTitle("Pause after")
            .setSingleChoiceItems(labels,current,(dialog,index)->{
                if(sleepTask!=null)handler.removeCallbacks(sleepTask);
                sleepMinutes=times[index];sleepTask=null;
                if(sleepMinutes>0){
                    sleepTask=()->{
                        if(player!=null&&player.isPlaying()){checkpoint();player.pause();}
                        toast("Sleep timer reached · paused");sleepMinutes=0;sleepTask=null;
                    };
                    handler.postDelayed(sleepTask,sleepMinutes*60L*1000L);
                }
                dialog.dismiss();toast(sleepMinutes==0?"Timer off":"Sleep timer: "+sleepMinutes+" min");
            }).setNegativeButton("Cancel",null).show();
    }
    private void chooseMode(){
        int current=0;for(int i=0;i<PlaybackModes.MODES.length;i++)if(PlaybackModes.MODES[i].equals(mode))current=i;
        new AlertDialog.Builder(this).setTitle("Display mode")
            .setSingleChoiceItems(PlaybackModes.LABELS,current,(dialog,index)->{
                String selection=PlaybackModes.MODES[index];dialog.dismiss();
                if(PlaybackModes.isXr(selection)){
                    if(!LibraryBackup.safeMediaUrl(url)){toast("WebXR relay currently requires an HTTP(S) stream");return;}
                    mode=selection;startXr(selection);
                }else{
                    if(selection.equals(mode))return;
                    checkpoint();Intent next=new Intent(this,PlayerActivity.class)
                        .putExtra("media_url",url).putExtra("media_title",title).putExtra("view_mode",selection);
                    if(url.startsWith("content://"))next.setData(Uri.parse(url)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(next);finish();
                }
            }).setNegativeButton("Cancel",null).show();
    }
    private void startXr(String xrMode){
        if(!LibraryBackup.safeMediaUrl(url)){
            toast("XR browser relay only supports HTTP(S) video URLs");return;
        }
        if(player!=null&&player.isPlaying()){checkpoint();player.pause();}
        String token=LocalXrService.randomToken();
        Intent service=new Intent(this,LocalXrService.class).setAction(LocalXrService.ACTION_START)
            .putExtra(LocalXrService.EXTRA_URL,url).putExtra(LocalXrService.EXTRA_TITLE,title)
            .putExtra(LocalXrService.EXTRA_MODE,xrMode).putExtra(LocalXrService.EXTRA_TOKEN,token);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);}
        catch(Exception e){toast("XR relay failed: "+e.getMessage());return;}
        toast("Open in headset browser and tap Enter VR");
        handler.postDelayed(()->{
            if(isFinishing())return;
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(LocalXrService.viewerUrl(token)))
                .addCategory(Intent.CATEGORY_BROWSABLE);
            try{startActivity(i);}catch(Exception e){
                new AlertDialog.Builder(this).setTitle("Headset browser unavailable")
                    .setMessage("Open this URL in the headset browser:\n"+LocalXrService.viewerUrl(token))
                    .setPositiveButton("OK",null).show();
            }
        },900);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onPause(){checkpoint();if(player!=null&&player.isPlaying())player.pause();super.onPause();}
    @Override protected void onDestroy(){
        finished=true;handler.removeCallbacks(checkpointTask);
        if(sleepTask!=null)handler.removeCallbacks(sleepTask);
        checkpoint();
        if(player!=null){try{player.stop();player.detachViews();}catch(Exception ignored){}player.release();player=null;}
        if(vlc!=null){vlc.release();vlc=null;}
        super.onDestroy();
    }
    public static void openExternal(Context context,String url){
        try{
            Intent i=new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(i,"Other available players"));
        }catch(Exception e){
            new AlertDialog.Builder(context).setMessage("No compatible external player installed. The in-app player uses LibVLC.")
                .setPositiveButton("OK",null).show();
        }
    }
}
