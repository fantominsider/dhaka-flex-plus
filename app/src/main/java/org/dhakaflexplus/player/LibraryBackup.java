package org.dhakaflexplus.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Portable user-selected JSON backup; works with the Android document picker, without storage permissions. */
public final class LibraryBackup {
    public static final int MAX_FILE_BYTES=2*1024*1024;
    private static final String APP="DhakaFlexPlus";
    private static final String LEGACY_APP="UniversalMediaBrowserXR";
    private LibraryBackup(){}
    public static boolean safeMediaUrl(String address){
        if(address==null||address.length()>4096)return false;
        try{
            URI u=new URI(address);
            String s=u.getScheme();
            return u.getHost()!=null&&u.getUserInfo()==null&&
                ("http".equalsIgnoreCase(s)||"https".equalsIgnoreCase(s))&&
                u.getPort()<=65535;
        }catch(Exception e){return false;}
    }
    public static String exportJson(Context ctx){
        ServerStore store=new ServerStore(ctx);SharedPreferences p=store.preferences();
        JSONObject root=new JSONObject();JSONArray servers=new JSONArray();
        try{
            root.put("app",APP);root.put("format_version",2);
            root.put("note","Contains server addresses and viewing history; do not share publicly.");
            for(ServerStore.Server s:store.load()){
                JSONObject o=new JSONObject();o.put("id",s.id);o.put("name",s.name);o.put("url",s.url);servers.put(o);
            }
            root.put("servers",servers);
            root.put("default_server_id",store.defaultServerId(store.load()));
            JSONArray favs=new JSONArray();for(String s:store.favorites())favs.put(s);
            root.put("favorites",favs);
            root.put("recent",WatchHistory.toJson(WatchHistory.load(ctx)));
            root.put("sort_mode",p.getInt("sort_mode",0));
            JSONArray iptv=new JSONArray();
            for(IptvParser.Channel channel:new IptvStore(ctx).channels()){
                JSONObject item=new JSONObject();
                item.put("name",channel.name);item.put("url",channel.url);item.put("group",channel.group);
                iptv.put(item);
            }
            root.put("iptv_channels",iptv);
            JSONArray sources=new JSONArray();
            for(String source:new IptvStore(ctx).playlistSources())sources.put(source);
            root.put("iptv_sources",sources);
        }catch(Exception e){throw new IllegalStateException("Backup serialization failed",e);}
        return root.toString(2);
    }
    public static final class ImportPlan {
        public final ArrayList<ServerStore.Server> servers;
        public final LinkedHashSet<String> favorites;
        public final ArrayList<WatchHistory.Item> history;
        public final int sortMode;
        public final ArrayList<IptvParser.Channel> iptv;
        public final ArrayList<String> playlistSources;
        public final boolean includesIptv;
        public final String defaultServerId;
        ImportPlan(ArrayList<ServerStore.Server> s,LinkedHashSet<String> f,ArrayList<WatchHistory.Item> h,int sort,
                ArrayList<IptvParser.Channel> channels,ArrayList<String> sources,boolean include,String defaultId){
            servers=s;favorites=f;history=h;sortMode=sort;
            iptv=channels;playlistSources=sources;includesIptv=include;defaultServerId=defaultId;
        }
    }
    public static ImportPlan parse(String raw)throws Exception{
        if(raw==null||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Backup exceeds the 2 MB safety limit.");
        JSONObject root=new JSONObject(raw);
        int version=root.optInt("format_version",-1);
        if(!(APP.equals(root.optString("app"))||LEGACY_APP.equals(root.optString("app")))||(version!=1&&version!=2))
            throw new IllegalArgumentException("Not a supported Dhaka Flex Plus or previous Dhaka Flex Plus backup.");
        JSONArray serverArray=root.getJSONArray("servers");
        JSONArray favArray=root.getJSONArray("favorites");
        JSONArray recentArray=root.getJSONArray("recent");
        if(serverArray.length()>100||favArray.length()>1000||recentArray.length()>WatchHistory.MAX_ITEMS)
            throw new IllegalArgumentException("Backup contains too many records.");
        ArrayList<ServerStore.Server> servers=new ArrayList<>();
        HashSet<String> seenIds=new HashSet<>(),seenServerUrls=new HashSet<>();
        for(int i=0;i<serverArray.length();i++){
            JSONObject o=serverArray.getJSONObject(i);
            String name=o.optString("name","").trim();String url=o.optString("url","").trim();
            String id=o.optString("id","").trim();
            if(name.isEmpty()||name.length()>100||id.length()>100||!safeMediaUrl(url))
                throw new IllegalArgumentException("Invalid server record #"+(i+1));
            url=ServerStore.canonicalUrl(url);
            if(id.isEmpty()||!seenIds.add(id))id=UUID.randomUUID().toString();
            if(seenServerUrls.add(url))servers.add(new ServerStore.Server(id,name,url));
        }
        LinkedHashSet<String> favorites=new LinkedHashSet<>();
        for(int i=0;i<favArray.length();i++){
            String url=favArray.getString(i);if(!safeMediaUrl(url))
                throw new IllegalArgumentException("Invalid favorite #"+(i+1));
            favorites.add(url);
        }
        ArrayList<WatchHistory.Item> history=WatchHistory.fromJson(recentArray.toString());
        if(history.size()!=recentArray.length())throw new IllegalArgumentException("Invalid viewing history entries.");
        int sort=root.optInt("sort_mode",0);if(sort<0||sort>3)sort=0;
        ArrayList<IptvParser.Channel> channels=new ArrayList<>();
        ArrayList<String> playlistUrls=new ArrayList<>();
        if(version==2){
            JSONArray tv=root.optJSONArray("iptv_channels");
            if(tv!=null){
                if(tv.length()>IptvParser.MAX_CHANNELS)throw new IllegalArgumentException("Too many IPTV channels in backup.");
                for(int i=0;i<tv.length();i++){
                    JSONObject j=tv.optJSONObject(i);if(j==null)throw new IllegalArgumentException("Invalid channel record");
                    String url=j.optString("url",""),name=j.optString("name",""),group=j.optString("group","");
                    if(!IptvParser.isStreamUrl(url)||name.length()>200||group.length()>100)
                        throw new IllegalArgumentException("Invalid IPTV entry #"+(i+1));
                    channels.add(new IptvParser.Channel(name,url,group));
                }
            }
            JSONArray src=root.optJSONArray("iptv_sources");
            if(src!=null){
                if(src.length()>30)throw new IllegalArgumentException("Too many IPTV source addresses.");
                for(int i=0;i<src.length();i++){
                    String url=src.getString(i);
                    if(!IptvParser.isStreamUrl(url)||!(url.startsWith("https://")||url.startsWith("http://")))
                        throw new IllegalArgumentException("Invalid IPTV source #"+(i+1));
                    playlistUrls.add(url);
                }
            }
        }
        String defaultId=root.optString("default_server_id","");
        // Older backups have no selected default. Invalid IDs fall back to a saved server.
        boolean hasDefault=false;
        for(ServerStore.Server s:servers)if(s.id.equals(defaultId)){hasDefault=true;break;}
        return new ImportPlan(servers,favorites,history,sort,channels,playlistUrls,version==2,hasDefault?defaultId:"");
    }
    public static void apply(Context ctx,ImportPlan incoming,boolean replace){
        ServerStore store=new ServerStore(ctx);SharedPreferences p=store.preferences();
        ArrayList<ServerStore.Server> servers=new ArrayList<>();
        LinkedHashSet<String> favs=new LinkedHashSet<>();
        ArrayList<WatchHistory.Item> history=new ArrayList<>();
        if(!replace){
            servers.addAll(store.load());favs.addAll(store.favorites());history.addAll(WatchHistory.load(ctx));
        }
        for(ServerStore.Server s:incoming.servers){
            int match=-1;
            for(int i=0;i<servers.size();i++){
                if(servers.get(i).id.equals(s.id)||servers.get(i).url.equals(s.url)){match=i;break;}
            }
            if(match>=0){
                // Keep an existing preset ID when an imported duplicate URL has a different ID.
                ServerStore.Server original=servers.get(match);
                String stableId=original.url.equals(s.url)?original.id:s.id;
                servers.set(match,new ServerStore.Server(stableId,s.name,s.url));
            }else if(servers.size()<100)servers.add(s);
        }
        favs.addAll(incoming.favorites);
        for(WatchHistory.Item item:incoming.history){
            boolean found=false;for(WatchHistory.Item existing:history)if(existing.url.equals(item.url)){found=true;break;}
            if(!found&&history.size()<WatchHistory.MAX_ITEMS)history.add(item);
        }
        java.util.Collections.sort(history,(a,b)->Long.compare(b.updated,a.updated));
        JSONArray favArray=new JSONArray();int count=0;
        for(String s:favs){if(count++>=1000)break;favArray.put(s);}
        store.save(servers);
        if(!incoming.defaultServerId.isEmpty()){
            boolean exists=false;
            for(ServerStore.Server s:servers)if(s.id.equals(incoming.defaultServerId)){exists=true;break;}
            if(exists)store.setDefaultServerId(incoming.defaultServerId,servers);
        }
        if(incoming.includesIptv){
            IptvStore iptv=new IptvStore(ctx);
            iptv.merge(incoming.iptv,replace);
            if(replace)iptv.clearSources();
            for(String source:incoming.playlistSources)iptv.addSource(source);
        }
        p.edit().putString("favs",favArray.toString())
            .putString(WatchHistory.KEY,WatchHistory.toJson(history).toString())
            .putInt("sort_mode",incoming.sortMode).apply();
    }
}
