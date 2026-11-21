package org.dhakaflexplus.player;

import android.text.Html;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles typical Apache/nginx/lighttpd HTTP auto-index pages (not SMB, FTP, Plex, etc.). */
public final class DirectoryClient {
    private DirectoryClient(){}
    public static final class Entry {
        public final String name, url;
        public final boolean directory;
        public Entry(String name,String url,boolean directory){this.name=name;this.url=url;this.directory=directory;}
    }
    private static final Pattern LINKS=Pattern.compile("(?is)<a\\s+[^>]*?href\\s*=\\s*(?:[\"']([^\"']+)[\"']|([^\\s>]+))[^>]*>(.*?)</a>");
    private static final Pattern TAGS=Pattern.compile("(?s)<[^>]+>");
    private static final int LIMIT=4*1024*1024;

    public static boolean isMedia(String url){
        String s=url.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        return s.matches(".*\\.(mp4|m4v|mkv|avi|mov|webm|ts|m2ts|mpeg|mpg|m3u8|m3u|mpd|3gp|flv|wmv|ogv|mp3|flac|m4a|aac|wav|ogg|opus)$");
    }
    public static String nameFromUrl(String url){
        try{
            URI u=new URI(url);
            String path=u.getRawPath();
            if(path==null||path.isEmpty()||path.equals("/"))return u.getHost();
            while(path.endsWith("/")&&path.length()>1)path=path.substring(0,path.length()-1);
            String last=path.substring(path.lastIndexOf('/')+1);
            return decode(last);
        }catch(Exception e){return url;}
    }
    private static String decode(String s){
        try{return URLDecoder.decode(s.replace("+","%2B"),"UTF-8");}catch(Exception e){return s;}
    }
    private static String unescape(String s){
        if(Build.VERSION.SDK_INT>=24)return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return Html.fromHtml(s).toString().trim();
    }
    public static ArrayList<Entry> browse(String inputUrl)throws Exception{
        URL url=new URL(inputUrl);
        if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new Exception("Unsupported protocol");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(9000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Android; DhakaFlexPlus/0.5)");
        c.setRequestProperty("Accept","text/html,application/xhtml+xml,*/*;q=0.5");
        int code;
        byte[] data;
        String type;
        try{
            code=c.getResponseCode();
            if(code<200||code>=300)throw new Exception("Server responded with HTTP "+code);
            type=c.getContentType();
            if(type!=null&&!(type.toLowerCase(Locale.ROOT).contains("text/html")||type.toLowerCase(Locale.ROOT).contains("text/plain")))
                throw new Exception("Not an HTML directory listing ("+type+"). Try Open in browser or a compatible server.");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int count;
                while((count=in.read(buf))!=-1){if(out.size()+count>LIMIT)throw new Exception("Directory listing exceeds 4 MB.");out.write(buf,0,count);}
                data=out.toByteArray();
            }
        }finally{c.disconnect();}
        String html=new String(data,StandardCharsets.UTF_8);
        Matcher matcher=LINKS.matcher(html);
        ArrayList<Entry> entries=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        URI base=new URI(inputUrl.replace(" ","%20"));
        while(matcher.find()){
            String raw=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
            if(raw==null||raw.isEmpty())continue;
            raw=unescape(raw);
            if(raw.startsWith("#")||raw.startsWith("?")||raw.startsWith("javascript:")||raw.startsWith("mailto:"))continue;
            URI resolved;
            try{resolved=base.resolve(raw.replace(" ","%20")).normalize();}catch(Exception ignored){continue;}
            if(!resolved.getScheme().equalsIgnoreCase("http")&&!resolved.getScheme().equalsIgnoreCase("https"))continue;
            // Don't follow links to unrelated external websites from a directory listing.
            if(!base.getHost().equalsIgnoreCase(resolved.getHost())||base.getPort()!=resolved.getPort())continue;
            String link=resolved.toString();
            if(link.equals(inputUrl)||!seen.add(link))continue;
            String label=unescape(TAGS.matcher(matcher.group(3)).replaceAll(" ")).replace('\u00a0',' ').trim();
            if(label.equalsIgnoreCase("parent directory")||label.equals("../")||raw.equals("../")||raw.equals(".."))continue;
            boolean isDirectory=resolved.getRawPath()!=null&&resolved.getRawPath().endsWith("/");
            if(label.isEmpty()||label.equals(".")||label.equals(".."))label=nameFromUrl(link);
            if(label.endsWith("/"))label=label.substring(0,label.length()-1);
            if(isDirectory||isMedia(link))entries.add(new Entry(label,link,isDirectory));
        }
        Collections.sort(entries,new Comparator<Entry>(){
            @Override public int compare(Entry a,Entry b){
                if(a.directory!=b.directory)return a.directory?-1:1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return entries;
    }
}
