package org.dhakaflexplus.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local IPTV channel library. Does not download or cache media files. */
public final class IptvStore {
    private static final String CHANNELS="vlc_iptv_channels_v1";
    private static final String PLAYLIST_URLS="vlc_playlist_sources_v1";
    private final SharedPreferences prefs;
    public IptvStore(Context ctx){ prefs=new ServerStore(ctx).preferences(); }
    public ArrayList<IptvParser.Channel> channels(){
        ArrayList<IptvParser.Channel> list=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(prefs.getString(CHANNELS,"[]"));
            for(int i=0;i<a.length()&&list.size()<IptvParser.MAX_CHANNELS;i++){
                JSONObject j=a.optJSONObject(i);if(j==null)continue;
                String name=j.optString("name",""),url=j.optString("url",""),group=j.optString("group","");
                if(IptvParser.isStreamUrl(url))list.add(new IptvParser.Channel(name,url,group));
            }
        }catch(Exception ignored){}
        return list;
    }
    public void merge(ArrayList<IptvParser.Channel> incoming,boolean replace){
        LinkedHashMap<String,IptvParser.Channel> all=new LinkedHashMap<>();
        if(!replace)for(IptvParser.Channel c:channels())all.put(c.url,c);
        for(IptvParser.Channel c:incoming){
            if(all.size()>=IptvParser.MAX_CHANNELS&&!all.containsKey(c.url))break;
            if(IptvParser.isStreamUrl(c.url))all.put(c.url,c);
        }
        save(new ArrayList<>(all.values()));
    }
    public void remove(String url){
        ArrayList<IptvParser.Channel> all=channels();
        for(int i=all.size()-1;i>=0;i--)if(all.get(i).url.equals(url))all.remove(i);
        save(all);
    }
    public void save(ArrayList<IptvParser.Channel> entries){
        JSONArray array=new JSONArray();
        int count=0;
        for(IptvParser.Channel c:entries){
            if(count++>=IptvParser.MAX_CHANNELS)break;
            JSONObject j=new JSONObject();
            try{j.put("name",c.name);j.put("url",c.url);j.put("group",c.group);array.put(j);}catch(Exception ignored){}
        }
        prefs.edit().putString(CHANNELS,array.toString()).apply();
    }
    public ArrayList<String> playlistSources(){
        ArrayList<String> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(prefs.getString(PLAYLIST_URLS,"[]"));
            for(int i=0;i<a.length()&&out.size()<30;i++){
                String u=a.optString(i,"");if(IptvParser.isStreamUrl(u))out.add(u);
            }
        }catch(Exception ignored){}
        return out;
    }
    public void clearSources(){prefs.edit().remove(PLAYLIST_URLS).apply();}
    public void addSource(String url){
        ArrayList<String> urls=playlistSources();
        if(!urls.contains(url)){urls.add(url);JSONArray a=new JSONArray();
            for(String u:urls)if(a.length()<30)a.put(u);
            prefs.edit().putString(PLAYLIST_URLS,a.toString()).apply();}
    }
}
