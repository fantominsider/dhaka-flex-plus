package org.dhakaflexplus.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Persists named HTTP directory-listing servers. No secret credentials are stored. */
public final class ServerStore {
    private static final String PREFS = "universal_media_v1";
    private final SharedPreferences prefs;

    public static class Server {
        public final String id;
        public String name;
        public String url;
        public Server(String id, String name, String url) {this.id=id;this.name=name;this.url=url;}
    }

    public ServerStore(Context ctx) {prefs=ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);}
    public SharedPreferences preferences(){return prefs;}

    private static final String PRESET_MIGRATED="factory_servers_added_v06";
    private static final String DEFAULT_SERVER_ID="default_server_id";
    public ArrayList<Server> load() {
        ArrayList<Server> output=new ArrayList<>();
        String raw=prefs.getString("servers",null);
        if(raw!=null){
            try{
                JSONArray arr=new JSONArray(raw);
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i);
                    if(o==null)continue;
                    String id=o.optString("id", "");
                    String name=o.optString("name", "");
                    String url=o.optString("url", "");
                    if(!id.isEmpty()&&!name.isEmpty()&&!url.isEmpty()) output.add(new Server(id,name,url));
                }
            }catch(Exception e){
                // Do not overwrite a malformed existing library with a fresh preset library.
                return output;
            }
        }
        // One-time migration: preserve old custom addresses, fill missing factory entries.
        // Never re-add a preset the user later deliberately deletes.
        if(!prefs.getBoolean(PRESET_MIGRATED,false)){
            addMissingFactoryPresets(output);
            save(output);
            prefs.edit().putBoolean(PRESET_MIGRATED,true).apply();
        }
        return output;
    }
    /** Re-add deliberately deleted factory entries only when the user requests it. */
    public static int addMissingFactoryPresets(ArrayList<Server> servers){
        int inserted=0;
        for(DefaultServerCatalog.Preset preset:DefaultServerCatalog.presets()){
            boolean exists=false;
            for(Server current:servers){
                if(current.id.equals(preset.id)||current.url.equals(preset.url)){exists=true;break;}
            }
            if(!exists){servers.add(new Server(preset.id,preset.name,preset.url));inserted++;}
        }
        return inserted;
    }
    public String defaultServerId(ArrayList<Server> servers){
        String id=prefs.getString(DEFAULT_SERVER_ID,"");
        for(Server s:servers)if(s.id.equals(id))return id;
        return servers.isEmpty()?null:servers.get(0).id;
    }
    public void setDefaultServerId(String id,ArrayList<Server> servers){
        if(id!=null){
            boolean found=false;
            for(Server s:servers)if(s.id.equals(id)){found=true;break;}
            if(!found)throw new IllegalArgumentException("Choose a saved server.");
        }
        prefs.edit().putString(DEFAULT_SERVER_ID,id==null?"":id).apply();
    }
    public void save(ArrayList<Server> servers){
        JSONArray arr=new JSONArray();
        for(Server s:servers){
            try{JSONObject o=new JSONObject();o.put("id",s.id);o.put("name",s.name);o.put("url",s.url);arr.put(o);}catch(Exception ignored){}
        }
        prefs.edit().putString("servers",arr.toString()).apply();
        String id=prefs.getString(DEFAULT_SERVER_ID,"");
        boolean exists=false;
        for(Server s:servers)if(s.id.equals(id)){exists=true;break;}
        if(!exists) setDefaultServerId(servers.isEmpty()?null:servers.get(0).id,servers);
    }
    public static String canonicalUrl(String value){
        return DefaultServerCatalog.canonicalUrl(value);
    }
    public LinkedHashSet<String> favorites(){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        try{JSONArray arr=new JSONArray(prefs.getString("favs","[]"));for(int i=0;i<arr.length();i++)out.add(arr.getString(i));}catch(Exception ignored){}
        return out;
    }
    public void toggleFavorite(String url){
        LinkedHashSet<String> fav=favorites();
        if(!fav.remove(url)) fav.add(url);
        JSONArray arr=new JSONArray();for(String s:fav)arr.put(s);
        prefs.edit().putString("favs",arr.toString()).apply();
    }
}
