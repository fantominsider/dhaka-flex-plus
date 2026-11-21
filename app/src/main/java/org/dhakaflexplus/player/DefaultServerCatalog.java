package org.dhakaflexplus.player;

import java.net.URI;
import java.util.ArrayList;

/**
 * User-visible factory preset addresses from the reference configuration.
 * Addresses are private RFC1918 network hosts, not public Internet services.
 * This catalog NEVER initiates a network connection. Users must browse/test explicitly.
 * Editing a preset changes its saved address, not the immutable factory value.
 */
public final class DefaultServerCatalog {
    private DefaultServerCatalog(){}
    public static final class Preset {
        public final String id, name, url;
        private Preset(String id,String name,String url){this.id=id;this.name=name;this.url=url;}
    }
    private static final Preset[] FACTORY={
        new Preset("df7", "Dhaka Flex 7 · Movies / 3D", "http://172.16.50.7/DHAKA-FLIX-7/"),
        new Preset("df10", "Dhaka Flex 10 · TV / Anime", "http://172.16.50.10/DHAKA-FLIX-10/"),
        new Preset("df12", "Dhaka Flex 12 · Web Series", "http://172.16.50.12/DHAKA-FLIX-12/"),
        new Preset("df14", "Dhaka Flex 14 · Movies / Series", "http://172.16.50.14/DHAKA-FLIX-14/")
    };
    /** Return a new list so callers cannot mutate the catalog. */
    public static ArrayList<Preset> presets(){
        ArrayList<Preset> out=new ArrayList<>();
        for(Preset preset:FACTORY)out.add(preset);
        return out;
    }
    public static String factoryUrl(String id){
        if(id==null)return null;
        for(Preset p:FACTORY)if(p.id.equals(id))return p.url;
        return null;
    }
    public static boolean isPreset(String id){return factoryUrl(id)!=null;}

    /**
     * A bare IP/host (with optional scheme or port) changes the host but preserves
     * the saved directory prefix. A full URL containing a path replaces the prefix.
     * This makes typical ISP IP changes safe without losing collection paths.
     */
    public static String resolveAddress(String previousUrl, String input){
        if(input==null||input.trim().isEmpty())
            throw new IllegalArgumentException("Enter an IP address or HTTP(S) URL.");
        String raw=input.trim();
        String uriText=raw.contains("://")?raw:"http://"+raw;
        String normalized=canonicalUrl(uriText);
        URI requested=URI.create(normalized);
        String path=requested.getRawPath();
        String originalPath;
        try{originalPath=URI.create(canonicalUrl(previousUrl)).getRawPath();}
        catch(Exception ignored){originalPath="/";}
        // Preserve the prefix only when the user entered just a host (no trailing slash).
        String inputWithoutScheme=raw.contains("://")?raw.substring(raw.indexOf("://")+3):raw;
        boolean inputHasPath=inputWithoutScheme.indexOf('/')>=0;
        if(!inputHasPath&&"/".equals(path)&&originalPath!=null&&!"/".equals(originalPath)){
            String prefix=normalized.substring(0,normalized.length()-1);
            return prefix+originalPath;
        }
        return normalized;
    }
    public static String canonicalUrl(String raw){
        String value=raw.trim();
        if(value.isEmpty())throw new IllegalArgumentException("Enter an IP address or HTTP(S) URL.");
        if(!value.contains("://"))value="http://"+value;
        value=value.replace(" ","%20");
        URI parsed;
        try{parsed=new URI(value);}catch(Exception e){throw new IllegalArgumentException("Invalid address. Example: 192.168.1.50:8080/Movies/");}
        String scheme=parsed.getScheme();
        if(scheme==null||!("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))||parsed.getHost()==null)
            throw new IllegalArgumentException("Only HTTP(S) server addresses are supported.");
        if(parsed.getUserInfo()!=null)throw new IllegalArgumentException("Do not include credentials in a server address.");
        if(parsed.getPort()>65535)throw new IllegalArgumentException("Invalid port.");
        if(parsed.getRawQuery()!=null||parsed.getRawFragment()!=null)
            throw new IllegalArgumentException("Enter a directory URL without query or fragment.");
        if(!value.endsWith("/"))value+="/";
        return value;
    }
}
