import org.dhakaflexplus.player.IptvParser;
import java.util.*;
public class TestIptvExtra {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
public static void main(String[] args){
 boolean rejected=false;
 try{IptvParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\nseg001.ts\n","https://example.com/live.m3u8");}
 catch(IllegalArgumentException e){rejected=e.getMessage().contains("HLS");}
 check(rejected,"HLS segment manifest rejected as channel list");
 check(IptvParser.parse("#EXTM3U\n#EXTINF:-1,Music\nhttp://192.0.2.57/audio/live.mp3\n",null).size()==1,"Audio IPTV stream allowed");
 check(IptvParser.isStreamUrl("udp://@239.42.1.3:1234"),"VLC UDP multicast convention supported");
 boolean tooBig=false;
 try{IptvParser.parse("x".repeat(2*1024*1024+1),null);}catch(IllegalArgumentException e){tooBig=true;}
 check(tooBig,"Playlist size limit enforced");
}
}
