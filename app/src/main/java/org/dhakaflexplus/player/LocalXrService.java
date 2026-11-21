package org.dhakaflexplus.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local-only HTTP service for the headset's system WebXR browser.
 * The HTTP server binds ONLY to 127.0.0.1 and serves the active stream with a random token.
 * No remote LAN listener, library enumeration endpoint, or public streaming proxy.
 * Foreground notification keeps the service alive while the immersive browser is in front.
 */
public final class LocalXrService extends Service {
    public static final String ACTION_START="org.dhakaflexplus.player.XR_START";
    public static final String ACTION_STOP="org.dhakaflexplus.player.XR_STOP";
    public static final String EXTRA_URL="stream_url", EXTRA_TITLE="stream_title", EXTRA_MODE="stream_mode", EXTRA_TOKEN="stream_token";
    public static final int PORT=47831;
    private static final String CHANNEL="xr_local_playback";
    private final ExecutorService workers=Executors.newFixedThreadPool(6);
    private volatile ServerSocket server;
    private volatile boolean active;
    private volatile String token,streamUrl,title,mode;
    private Thread acceptThread;

    public static String randomToken(){
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);
        StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();
    }
    public static String viewerUrl(String token){return "http://127.0.0.1:"+PORT+"/xr?t="+Uri.encode(token);}
    @Override public void onCreate(){super.onCreate();ensureNotificationChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        String media=intent.getStringExtra(EXTRA_URL);
        try{
            URI u=new URI(media);
            if(u.getHost()==null||u.getUserInfo()!=null||(!"http".equalsIgnoreCase(u.getScheme())&&!"https".equalsIgnoreCase(u.getScheme())))
                throw new IllegalArgumentException("Expected an HTTP(S) media URL");
        }catch(Exception e){Log.w("MediaBrowserXR","Rejected stream URL",e);stopSelf();return START_NOT_STICKY;}
        streamUrl=media;
        title=intent.getStringExtra(EXTRA_TITLE);
        mode=intent.getStringExtra(EXTRA_MODE);
        token=intent.getStringExtra(EXTRA_TOKEN);
        if(token==null||!token.matches("[0-9a-f]{32}")){stopSelf();return START_NOT_STICKY;}
        startForeground(42,buildNotification());
        if(!active)startServer();
        return START_NOT_STICKY;
    }
    private void ensureNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"XR video streaming",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Local loopback relay while viewing video in the headset browser");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private Notification buildNotification(){
        Intent stop=new Intent(this,LocalXrService.class).setAction(ACTION_STOP);
        PendingIntent action=PendingIntent.getService(this,43,stop,PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setContentTitle("Media Browser XR viewer")
            .setContentText("Secure local video relay active · Tap Stop when finished")
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action).build();
    }
    private synchronized void startServer(){
        if(active)return;
        active=true;
        acceptThread=new Thread(()->{
            try(ServerSocket listener=new ServerSocket(PORT,20,InetAddress.getByName("127.0.0.1"))){
                server=listener;
                while(active){
                    Socket socket=listener.accept();
                    try{workers.execute(()->serve(socket));}catch(Exception e){try{socket.close();}catch(IOException ignored){}}
                }
            }catch(IOException e){if(active)Log.e("MediaBrowserXR","Local XR service failed to bind",e);}
            finally{active=false;server=null;}
        },"xr-loopback-accept");
        acceptThread.start();
    }
    private void serve(Socket socket){
        try(Socket client=socket){
            client.setSoTimeout(20000);
            InputStream input=new BufferedInputStream(client.getInputStream());
            OutputStream output=new BufferedOutputStream(client.getOutputStream());
            String line=readLine(input,8192);
            if(line==null)return;
            String[] first=line.split(" ");
            if(first.length!=3||!("GET".equals(first[0])||"HEAD".equals(first[0]))){respond(output,405,"text/plain","Method not allowed".getBytes(StandardCharsets.UTF_8),false);return;}
            boolean head="HEAD".equals(first[0]);
            if(first[1].length()>2048){respond(output,414,"text/plain",new byte[0],head);return;}
            String range=null;
            int headerBytes=0;
            while((line=readLine(input,8192))!=null&&!line.isEmpty()){
                headerBytes+=line.length();if(headerBytes>16384)throw new IOException("Headers too large");
                if(line.regionMatches(true,0,"Range:",0,6))range=line.substring(6).trim();
            }
            URI requested=new URI("http://localhost"+first[1]);
            String candidate=requested.getRawQuery()==null?"":requested.getRawQuery();
            String supplied="";
            for(String param:candidate.split("&"))if(param.startsWith("t="))supplied=param.substring(2);
            if("/xr.js".equals(requested.getPath())){asset(output,"xr.js","application/javascript; charset=utf-8",head);return;}
            if(token==null||!token.equals(supplied)){respond(output,403,"text/plain","Access denied".getBytes(StandardCharsets.UTF_8),head);return;}
            switch(requested.getPath()){
                case "/health":respond(output,200,"text/plain","OK".getBytes(StandardCharsets.UTF_8),head);break;
                case "/xr":asset(output,"xr.html","text/html; charset=utf-8",head);break;
                case "/xr.js":asset(output,"xr.js","application/javascript; charset=utf-8",head);break;
                case "/config":
                    JSONObject cfg=new JSONObject();cfg.put("title",title==null?"Video":title);cfg.put("mode",mode==null?"theater":mode);
                    respond(output,200,"application/json; charset=utf-8",cfg.toString().getBytes(StandardCharsets.UTF_8),head);break;
                case "/stream":relay(output,range,head);break;
                default:respond(output,404,"text/plain","Not found".getBytes(StandardCharsets.UTF_8),head);
            }
        }catch(Exception e){Log.w("MediaBrowserXR","Request failed: "+e.getMessage());}
    }
    private static String readLine(InputStream in,int maximum)throws IOException{
        ByteArrayOutputStream buf=new ByteArrayOutputStream();int next;
        while((next=in.read())!=-1){if(next=='\n')break;if(next!='\r')buf.write(next);if(buf.size()>maximum)throw new IOException("Line too long");}
        if(next==-1&&buf.size()==0)return null;
        return buf.toString("ISO-8859-1");
    }
    private void asset(OutputStream out,String name,String type,boolean head)throws IOException{
        try(InputStream in=getAssets().open(name)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
            while((n=in.read(chunk))!=-1)bytes.write(chunk,0,n);
            respond(out,200,type,bytes.toByteArray(),head);
        }
    }
    private static void headers(OutputStream out,int status,String contentType,long length,String more)throws IOException{
        String reason=status==200?"OK":status==206?"Partial Content":status==403?"Forbidden":status==404?"Not Found":status==414?"URI Too Long":status==502?"Bad Gateway":"Error";
        String response="HTTP/1.1 "+status+" "+reason+"\r\n"+
            "Content-Type: "+contentType+"\r\n"+
            (length>=0?"Content-Length: "+length+"\r\n":"")+
            "Connection: close\r\n"+
            "X-Content-Type-Options: nosniff\r\n"+
            "Referrer-Policy: no-referrer\r\n"+
            "Cache-Control: no-store\r\n"+
            "Permissions-Policy: xr-spatial-tracking=(self)\r\n"+
            "Content-Security-Policy: default-src 'self' blob:; script-src 'self'; style-src 'self' 'unsafe-inline'; media-src 'self' blob:; connect-src 'self'\r\n"+
            more+"\r\n";
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
    }
    private static void respond(OutputStream out,int status,String mime,byte[] data,boolean head)throws IOException{
        headers(out,status,mime,data.length,"");if(!head)out.write(data);out.flush();
    }
    private void relay(OutputStream out,String range,boolean head)throws IOException{
        HttpURLConnection remote=null;
        try{
            remote=(HttpURLConnection)new java.net.URL(streamUrl).openConnection();
            remote.setInstanceFollowRedirects(true);
            remote.setRequestMethod(head?"HEAD":"GET");
            remote.setConnectTimeout(10000);remote.setReadTimeout(30000);
            remote.setRequestProperty("User-Agent","DhakaFlexPlus/0.5");
            if(range!=null&&range.matches("bytes=[0-9]*-[0-9]*"))remote.setRequestProperty("Range",range);
            int code=remote.getResponseCode();
            if(code!=200&&code!=206){respond(out,502,"text/plain",("Upstream HTTP "+code).getBytes(StandardCharsets.UTF_8),head);return;}
            String mime=remote.getContentType();if(mime==null)mime="application/octet-stream";
            // Use HTTP/1.1 close-delimited transfer when upstream length is unknown.
            String contentRange=remote.getHeaderField("Content-Range");
            String extras="Accept-Ranges: bytes\r\n"+(code==206&&contentRange!=null?"Content-Range: "+contentRange+"\r\n":"");
            // URLConnection.getContentLengthLong() starts at API 24. Parse the
            // header on every version so Android 6 also supports videos >2 GiB.
            long contentLength=-1;
            String lengthHeader=remote.getHeaderField("Content-Length");
            if(lengthHeader!=null){
                try{contentLength=Long.parseLong(lengthHeader.trim());}
                catch(NumberFormatException ignored){}
            }
            headers(out,code,mime,contentLength,extras);
            if(!head){
                try(InputStream input=remote.getInputStream()){
                    byte[] bytes=new byte[64*1024];int count;
                    while((count=input.read(bytes))!=-1){out.write(bytes,0,count);out.flush();}
                }
            }
            out.flush();
        }finally{if(remote!=null)remote.disconnect();}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        active=false;
        try{if(server!=null)server.close();}catch(IOException ignored){}
        workers.shutdownNow();super.onDestroy();
    }
}
