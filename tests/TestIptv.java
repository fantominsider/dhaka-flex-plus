import org.dhakaflexplus.player.IptvParser;
import java.util.*;
public class TestIptv {
static void ok(boolean x,String name){if(!x)throw new AssertionError(name);System.out.println("PASS "+name);}
public static void main(String[] a){
String playlist="#EXTM3U\n#EXTINF:-1 tvg-id=\"x\" tvg-name=\"Bangla News\" group-title=\"News\",Bangla 24\nhttp://192.0.2.55/live/index.m3u8\n#EXTINF:-1 group-title=\"Sports\",Sports Channel\n/streams/sport.ts\n#EXTINF:-1,NoCredential\nhttp://username:password@example.org/a\n#EXTINF:-1,Duplicate\nhttp://192.0.2.55/live/index.m3u8\n#EXTINF:-1,RTSP Camera\nrtsp://192.0.2.56:554/live\n#EXTINF:-1,IgnoreUnsafe\nfile:///sdcard/secret.mp4\n";
ArrayList<IptvParser.Channel> r=IptvParser.parse(playlist,"http://192.0.2.55/iptv/list.m3u");
ok(r.size()==3,"Supported, unique, noncredential playlist entries");
ok(r.get(0).name.equals("Bangla 24"),"EXTINF title parsed");
ok(r.get(0).group.equals("News"),"Group parsed");
ok(r.get(1).url.equals("http://192.0.2.55/streams/sport.ts"),"Relative stream resolved");
ok(r.get(2).url.equals("rtsp://192.0.2.56:554/live"),"RTSP stream retained");
ok(!IptvParser.isStreamUrl("javascript:alert(1)"),"Unsafe scheme rejected");
ok(!IptvParser.isStreamUrl("file:///data/user/secret.mp4"),"File scheme not allowed as remote URL");
ok(!IptvParser.isStreamUrl("https://user:pw@example.com/"),"Credentials in URL rejected");
ok(IptvParser.parse("#EXTM3U\n#EXTINF:-1,Hi\nrelative.ts\n",null).isEmpty(),"Relative URL without base rejected");
}
}
