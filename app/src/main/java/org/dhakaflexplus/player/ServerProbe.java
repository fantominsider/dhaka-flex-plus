package org.dhakaflexplus.player;

import java.net.HttpURLConnection;
import java.net.URL;

/** Lightweight HTTP reachability test. Does not imply that a video or XR format will play. */
public final class ServerProbe {
    public static final class Result {
        public final boolean reachable;
        public final int status;
        public final String description;
        Result(boolean ok,int code,String message){reachable=ok;status=code;description=message;}
    }
    private ServerProbe(){}
    public static Result probe(String url){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(3500);c.setReadTimeout(3500);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent","DhakaFlexPlus/0.5");
            // GET fetches just response headers. Do not download the index for health checks.
            int code=c.getResponseCode();
            return new Result(code>=200&&code<400,code,"HTTP "+code);
        }catch(Exception e){
            String detail=e.getMessage();
            if(detail==null||detail.isEmpty())detail=e.getClass().getSimpleName();
            if(detail.length()>110)detail=detail.substring(0,110);
            return new Result(false,-1,detail);
        }finally{if(c!=null)c.disconnect();}
    }
}
