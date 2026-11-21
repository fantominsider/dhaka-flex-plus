package org.dhakaflexplus.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;

/** Device-local recent viewing history. Entries contain URLs, titles, resume points, not video files. */
public final class WatchHistory {
    static final String KEY="recent_media";
    static final int MAX_ITEMS=60;
    public static final class Item {
        public final String url,title;
        public final int positionMs, durationMs;
        public final boolean completed;
        public final long updated;
        public Item(String u,String t,int p,int d,boolean c,long stamp){url=u;title=t;positionMs=p;durationMs=d;completed=c;updated=stamp;}
    }
    private WatchHistory(){}
    private static SharedPreferences prefs(Context ctx){return new ServerStore(ctx).preferences();}
    public static ArrayList<Item> load(Context ctx){return fromJson(prefs(ctx).getString(KEY,"[]"));}
    static ArrayList<Item> fromJson(String raw){
        ArrayList<Item> out=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length()&&out.size()<MAX_ITEMS;i++){
                JSONObject o=a.optJSONObject(i);if(o==null)continue;
                String url=o.optString("url","");
                if(!LibraryBackup.safeMediaUrl(url)||!seen.add(url))continue;
                String title=o.optString("title",DirectoryClient.nameFromUrl(url));
                if(title.length()>220)title=title.substring(0,220);
                int p=Math.max(0,o.optInt("position",0)),d=Math.max(0,o.optInt("duration",0));
                boolean completed=o.optBoolean("completed",false);
                long updated=Math.max(0,o.optLong("updated",0));
                out.add(new Item(url,title,p,d,completed,updated));
            }
        }catch(Exception ignored){}
        return out;
    }
    static JSONArray toJson(ArrayList<Item> items){
        JSONArray a=new JSONArray();int count=0;
        for(Item x:items){if(count>=MAX_ITEMS)break;
            try{
                JSONObject o=new JSONObject();o.put("url",x.url);o.put("title",x.title);
                o.put("position",x.positionMs);o.put("duration",x.durationMs);
                o.put("completed",x.completed);o.put("updated",x.updated);a.put(o);count++;
            }catch(Exception ignored){}
        }
        return a;
    }
    private static void save(Context c,ArrayList<Item> items){prefs(c).edit().putString(KEY,toJson(items).toString()).apply();}
    public static void mark(Context ctx,String url,String title,int positionMs,int durationMs,boolean completed){
        if(!LibraryBackup.safeMediaUrl(url))return;
        ArrayList<Item> list=load(ctx);
        for(int i=list.size()-1;i>=0;i--)if(list.get(i).url.equals(url))list.remove(i);
        int position=Math.max(0,positionMs),duration=Math.max(0,durationMs);
        if(duration>30000&&position>=duration-15000)completed=true;
        if(title==null||title.trim().isEmpty())title=DirectoryClient.nameFromUrl(url);
        if(title.length()>220)title=title.substring(0,220);
        list.add(0,new Item(url,title,completed?0:position,duration,completed,System.currentTimeMillis()));
        while(list.size()>MAX_ITEMS)list.remove(list.size()-1);
        save(ctx,list);
    }
    public static void visit(Context ctx,String url,String title){
        if(!LibraryBackup.safeMediaUrl(url))return;
        ArrayList<Item> list=load(ctx);
        int pos=0,duration=0;boolean completed=false;
        for(Item x:list)if(x.url.equals(url)){pos=x.positionMs;duration=x.durationMs;completed=x.completed;break;}
        mark(ctx,url,title,pos,duration,completed);
    }
    public static void remove(Context ctx,String url){ArrayList<Item> list=load(ctx);for(int i=list.size()-1;i>=0;i--)if(list.get(i).url.equals(url))list.remove(i);save(ctx,list);}
    public static void clear(Context ctx){prefs(ctx).edit().remove(KEY).apply();}
    public static String time(int ms){int sec=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60);}
}
