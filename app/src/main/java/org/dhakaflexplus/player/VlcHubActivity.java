package org.dhakaflexplus.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-owned VLC / IPTV interface; never launches or requires the standalone VLC Android app. */
public final class VlcHubActivity extends Activity {
    private static final int REQ_PLAYLIST=401, REQ_VIDEO=402;
    private static final int BG=Color.rgb(15,20,32),FG=Color.WHITE,DIM=Color.rgb(175,188,209);
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private LinearLayout root,list;
    private EditText search;
    private IptvStore library;
    private String filter="";
    private int d(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        library=new IptvStore(this);
        draw();
        if(VlcUpdateCenter.due(this))checkUpdates(false);
    }
    @Override protected void onDestroy(){work.shutdownNow();super.onDestroy();}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(FG);v.setTextSize(size);return v;}
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());
        b.setFocusable(true);return b;
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(d(14),d(12),d(14),d(24));root.setBackgroundColor(BG);
        scroll.addView(root);setContentView(scroll);
        root.addView(button("‹ Back to Home",this::finish));
        TextView heading=text("VLC  /  LIVE TV",24);heading.setPadding(d(4),d(14),0,d(5));root.addView(heading);
        TextView hint=text("Built-in LibVLC player • direct streams • IPTV playlists • local media",13);hint.setTextColor(DIM);root.addView(hint);
        root.addView(button("▶ Play a stream URL",this::promptStream));
        root.addView(button("+ Import M3U / IPTV playlist from URL",this::promptPlaylistUrl));
        root.addView(button("↻ Re-import a saved playlist URL",this::chooseSavedPlaylist));
        root.addView(button("▤ Import M3U file from device",this::choosePlaylistFile));
        root.addView(button("▣ Open local video or audio file",this::chooseMediaFile));
        root.addView(button("⚙ VLC settings and engine updates",this::settings));
        TextView heading2=text("Saved IPTV channels",19);heading2.setPadding(0,d(17),0,d(6));root.addView(heading2);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(FG);search.setHintTextColor(DIM);
        search.setHint("Filter channels or groups");root.addView(search);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){filter=s.toString().toLowerCase(Locale.ROOT);drawChannels();}
            public void afterTextChanged(Editable s){}
        });
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        drawChannels();
    }
    private void drawChannels(){
        if(list==null)return;
        list.removeAllViews();ArrayList<IptvParser.Channel> channels=library.channels();int matched=0;
        for(IptvParser.Channel c:channels){
            if(!filter.isEmpty()&&!c.name.toLowerCase(Locale.ROOT).contains(filter)&&!c.group.toLowerCase(Locale.ROOT).contains(filter))continue;
            matched++;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            Button play=button("▶  "+c.name+(c.group.isEmpty()?"":"  ["+c.group+"]"),()->play(c.url,c.name));
            row.addView(play,new LinearLayout.LayoutParams(0,d(58),1));
            Button more=button("⋮",()->new AlertDialog.Builder(this).setTitle(c.name)
                .setItems(new String[]{"Play", "Remove from saved channels", "Stream details"},(dialog,which)->{
                    if(which==0)play(c.url,c.name);
                    else if(which==1)new AlertDialog.Builder(this).setMessage("Remove only this saved channel? This will not delete anything on a media server.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{library.remove(c.url);drawChannels();}).show();
                    else new AlertDialog.Builder(this).setTitle("Stream information").setMessage(c.name+"\n"+c.url+"\nGroup: "+c.group).setPositiveButton("OK",null).show();
                }).show());
            row.addView(more,new LinearLayout.LayoutParams(d(55),d(58)));list.addView(row);
        }
        if(matched==0){TextView empty=text(channels.isEmpty()?"No saved IPTV channels yet. Import an M3U playlist to populate this area.":"No matching channels.",14);
            empty.setTextColor(DIM);empty.setPadding(0,d(16),0,d(16));list.addView(empty);}
    }
    private void promptStream(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(FG);
        input.setHint("https://server.example/live/stream.m3u8");
        new AlertDialog.Builder(this).setTitle("Open network stream").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Play",(dialog,which)->{
                String u=input.getText().toString().trim();
                if(!IptvParser.isStreamUrl(u)){message("Use HTTP(S), RTSP, RTMP, RTP or UDP URL without embedded credentials");return;}
                play(u,Uri.parse(u).getHost());
            }).setNeutralButton("Save & play",(dialog,which)->{
                String u=input.getText().toString().trim();
                if(!IptvParser.isStreamUrl(u)){message("Enter a valid stream URL");return;}
                String host=Uri.parse(u).getHost();
                ArrayList<IptvParser.Channel> one=new ArrayList<>();
                one.add(new IptvParser.Channel(host==null?"Direct stream":host,u,"Direct streams"));
                library.merge(one,false);drawChannels();play(u,host);
            }).show();
    }
    private void play(String url,String title){
        Intent intent=new Intent(this,PlayerActivity.class);
        intent.putExtra("media_url",url);intent.putExtra("media_title",title);
        startActivity(intent);
    }
    private void promptPlaylistUrl(){
        EditText entry=new EditText(this);entry.setTextColor(FG);entry.setSingleLine(true);
        entry.setHint("http://server.example/playlist.m3u");
        new AlertDialog.Builder(this).setTitle("Import IPTV playlist URL")
            .setMessage("Only use a playlist you are authorized to access. The app stores the parsed channel URLs locally.")
            .setView(entry).setNegativeButton("Cancel",null)
            .setPositiveButton("Import",(dialog,w)->{
                String url=entry.getText().toString().trim();
                if(!(url.startsWith("https://")||url.startsWith("http://"))||!IptvParser.isStreamUrl(url)){message("Enter a valid HTTP(S) M3U address");return;}
                importRemotePlaylist(url);
            }).show();
    }
    private void chooseSavedPlaylist(){
        ArrayList<String> urls=library.playlistSources();
        if(urls.isEmpty()){message("No saved playlist URLs yet");return;}
        new AlertDialog.Builder(this).setTitle("Re-import saved playlist")
            .setItems(urls.toArray(new String[0]),(d,index)->importRemotePlaylist(urls.get(index)))
            .setNegativeButton("Cancel",null).show();
    }
    private void importRemotePlaylist(String url){
        work.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(8000);c.setReadTimeout(15000);
                c.setRequestProperty("Accept","audio/x-mpegurl,application/vnd.apple.mpegurl,text/plain,*/*");
                String body;
                try{
                    if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
                    try(InputStream in=c.getInputStream()){body=readLimited(in);}
                }finally{c.disconnect();}
                ArrayList<IptvParser.Channel> parsed=IptvParser.parse(body,url);
                runOnUiThread(()->confirmImport(parsed,url));
            }catch(Exception e){runOnUiThread(()->message("Playlist import failed: "+e.getMessage()));}
        });
    }
    private static String readLimited(InputStream in)throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(output.size()+n>2*1024*1024)throw new IllegalArgumentException("Playlist exceeds 2 MB");output.write(b,0,n);}
        return new String(output.toByteArray(),StandardCharsets.UTF_8);
    }
    private void confirmImport(ArrayList<IptvParser.Channel> channels,String source){
        if(channels.isEmpty()){message("No supported channel addresses were found in the playlist");return;}
        new AlertDialog.Builder(this).setTitle("Import "+channels.size()+" IPTV channels?")
            .setMessage("Merge keeps saved channels; Replace removes the existing channel library. This only changes local app data.")
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Merge",(d,w)->finishImport(channels,source,false))
            .setPositiveButton("Replace",(d,w)->finishImport(channels,source,true)).show();
    }
    private void finishImport(ArrayList<IptvParser.Channel> channels,String source,boolean replace){
        library.merge(channels,replace);
        if(source!=null)library.addSource(source);
        drawChannels();message(channels.size()+" playlist entries imported");
    }
    private void choosePlaylistFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,REQ_PLAYLIST);}catch(Exception e){message("File picker not available");}
    }
    private void chooseMediaFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,REQ_VIDEO);}catch(Exception e){message("File picker not available");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==REQ_VIDEO){
            Intent play=new Intent(this,PlayerActivity.class);
            play.setData(uri);play.putExtra("media_url",uri.toString());
            play.putExtra("media_title","Local media");play.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(play);
        }else if(request==REQ_PLAYLIST){
            work.execute(()->{
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IllegalStateException("Cannot read selected file");
                    ArrayList<IptvParser.Channel> entries=IptvParser.parse(readLimited(in),null);
                    runOnUiThread(()->confirmImport(entries,null));
                }catch(Exception e){runOnUiThread(()->message("Import failed: "+e.getMessage()));}
            });
        }
    }
    private void settings(){
        boolean auto=VlcUpdateCenter.enabled(this);
        String[] options={"Engine bundled: LibVLC "+VlcUpdateCenter.ENGINE_VERSION,
            auto?"Automatic version checks: ON (tap to disable)":"Automatic version checks: OFF (tap to enable)",
            "Check for newer stable LibVLC now",
            "Network buffering / caching",
            "Hardware decoding",
            "How VLC updates work"};
        new AlertDialog.Builder(this).setTitle("VLC engine · Settings")
            .setItems(options,(dialog,i)->{
                if(i==1){VlcUpdateCenter.setEnabled(this,!auto);settings();}
                else if(i==2)checkUpdates(true);
                else if(i==3)chooseCaching();
                else if(i==4)chooseDecoder();
                else if(i==5)new AlertDialog.Builder(this).setTitle("About engine updates")
                    .setMessage("The VLC engine is included in Dhaka Flex Plus, so no separate VLC app is needed. Updating the standalone VLC app cannot update this embedded engine.\n\nVersion checks only compare the bundled engine with VideoLAN's stable 3.x Maven releases. A newer engine is delivered inside a NEW SIGNED Dhaka Flex Plus APK or through its official app-store release. This build does not install code updates or download executable libraries.")
                    .setPositiveButton("OK",null).show();
                else if(i==0)message("LibVLC "+VlcUpdateCenter.ENGINE_VERSION+" is bundled as an Android build dependency");
            }).setNegativeButton("Close",null).show();
    }
    private void chooseCaching(){
        int[] times={750,1500,3000,5000};String[] labels={"750 ms · fast LAN", "1500 ms · balanced", "3000 ms · unstable ISP", "5000 ms · slow IPTV"};
        int saved=new ServerStore(this).preferences().getInt("vlc_cache_ms",1500);int selected=1;
        for(int i=0;i<times.length;i++)if(times[i]==saved)selected=i;
        new AlertDialog.Builder(this).setTitle("Network buffer size").setSingleChoiceItems(labels,selected,(dialog,i)->{
            new ServerStore(this).preferences().edit().putInt("vlc_cache_ms",times[i]).apply();dialog.dismiss();
        }).setNegativeButton("Cancel",null).show();
    }
    private void chooseDecoder(){
        String[] names={"Automatic / hardware preferred","Software decoding"};
        int selected=new ServerStore(this).preferences().getBoolean("vlc_software_decoder",false)?1:0;
        new AlertDialog.Builder(this).setTitle("Decoder selection")
            .setSingleChoiceItems(names,selected,(dialog,which)->{
                new ServerStore(this).preferences().edit().putBoolean("vlc_software_decoder",which==1).apply();dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void checkUpdates(boolean interactive){
        if(interactive)message("Checking stable VideoLAN LibVLC versions …");
        work.execute(()->{
            try{
                String latest=VlcUpdateCenter.fetchStableVersion(this);
                runOnUiThread(()->{
                    if(VlcUpdateCenter.compare(latest,VlcUpdateCenter.ENGINE_VERSION)>0){
                        new AlertDialog.Builder(this).setTitle("Newer LibVLC available: "+latest)
                            .setMessage("Bundled: "+VlcUpdateCenter.ENGINE_VERSION+"\nUpstream: "+latest+"\n\nUpdating the engine requires a new signed release of this media app. This is a release notification, not an in-place engine installer.")
                            .setPositiveButton("OK",null).show();
                    }else if(interactive)message("LibVLC "+VlcUpdateCenter.ENGINE_VERSION+" is current among stable 3.x releases");
                });
            }catch(Exception e){if(interactive)runOnUiThread(()->message("Could not check upstream versions: "+e.getMessage()));}
        });
    }
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
