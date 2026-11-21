package org.dhakaflexplus.player;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone M3U playlist parser. No Android dependencies; no remote fetch during parsing. */
public final class IptvParser {
    private IptvParser() {}
    public static final int MAX_CHANNELS = 2000;
    private static final Pattern GROUP = Pattern.compile("(?i)group-title\\s*=\\s*\"([^\"]{0,100})\"");
    private static final Pattern NAME = Pattern.compile("(?i)tvg-name\\s*=\\s*\"([^\"]{0,200})\"");
    private static final Pattern TITLE = Pattern.compile("(?i)^(.*?)(?:,(.*))?$");

    public static final class Channel {
        public final String name, url, group;
        public Channel(String name, String url, String group) {
            this.name = name; this.url = url; this.group = group;
        }
    }

    public static boolean isStreamUrl(String value) {
        if(value==null || value.length()==0 || value.length()>4096) return false;
        try {
            URI u = new URI(value.trim());
            String scheme=u.getScheme();
            if(scheme==null || u.getHost()==null || u.getPort()>65535) return false;
            // VLC multicast convention permits a single empty userinfo marker in udp://@host:port.
            // Non-empty user:password authentication still requires separate secure handling.
            String user=u.getUserInfo();
            boolean multicast=("udp".equalsIgnoreCase(scheme)||"rtp".equalsIgnoreCase(scheme));
            if(user!=null && !(multicast&&user.isEmpty()))return false;
            switch(scheme.toLowerCase(Locale.ROOT)) {
                case "http": case "https": case "rtsp": case "rtmp":
                case "rtp": case "udp": return true;
                default: return false;
            }
        } catch(Exception e) { return false; }
    }

    /** Resolve relative entries only against a public/network playlist base URL. */
    public static ArrayList<Channel> parse(String raw, String baseUrl) {
        if(raw==null || raw.length()>2*1024*1024) throw new IllegalArgumentException("Playlist exceeds 2 MB");
        // An HLS .m3u8 media manifest is not an IPTV channel list.
        // Importing it would incorrectly create a channel for each media segment.
        if(raw.contains("#EXT-X-"))
            throw new IllegalArgumentException("HLS video manifest detected. Use Play a stream URL instead of playlist import.");
        ArrayList<Channel> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        String name="", group="", extgrp="";
        URI base=null;
        try { if(baseUrl!=null && isStreamUrl(baseUrl)) base=new URI(baseUrl); } catch(Exception ignored) {}
        for(String line:raw.replace("\r", "").split("\n")) {
            line=line.trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF:")) {
                String meta=line.substring(8);
                Matcher gr=GROUP.matcher(meta);
                Matcher nm=NAME.matcher(meta);
                group=gr.find()?gr.group(1).trim():extgrp;
                Matcher title=TITLE.matcher(meta);
                String display=title.matches()&&title.group(2)!=null?title.group(2).trim():"";
                name=!display.isEmpty()?display:(nm.find()?nm.group(1).trim():"");
            } else if(line.startsWith("#EXTGRP:")) {
                extgrp=line.substring(8).trim();
                if(extgrp.length()>100)extgrp=extgrp.substring(0,100);
            } else if(!line.startsWith("#")) {
                String absolute=line;
                try {
                    URI candidate=new URI(line.replace(" ","%20"));
                    if(!candidate.isAbsolute() && base!=null) absolute=base.resolve(candidate).toString();
                } catch(Exception ignored) { absolute=""; }
                if(isStreamUrl(absolute) && seen.add(absolute)) {
                    String display=name.isEmpty()?guessName(absolute):name;
                    if(display.length()>200)display=display.substring(0,200);
                    result.add(new Channel(display,absolute,group));
                    if(result.size()>=MAX_CHANNELS)break;
                }
                name=""; group=extgrp;
            }
        }
        return result;
    }

    private static String guessName(String value) {
        try {
            URI uri=new URI(value);String path=uri.getPath();
            if(path!=null&&!path.isEmpty()&&!path.equals("/")) {
                String last=path.substring(path.lastIndexOf('/')+1);
                if(!last.isEmpty())return last;
            }
            return uri.getHost();
        } catch(Exception e) { return "Untitled stream"; }
    }
}
