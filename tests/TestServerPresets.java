import org.dhakaflexplus.player.DefaultServerCatalog;
import java.util.ArrayList;
import java.util.HashSet;

/** Headless tests: no Android SDK or live ISP network needed. */
public final class TestServerPresets {
    private static int assertions=0;
    private static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        ArrayList<DefaultServerCatalog.Preset> p=DefaultServerCatalog.presets();
        check(p.size()==4,"factory preset count");
        HashSet<String> ids=new HashSet<>();
        for(DefaultServerCatalog.Preset entry:p){
            check(ids.add(entry.id),"unique ID "+entry.id);
            check(DefaultServerCatalog.isPreset(entry.id),"recognized preset "+entry.id);
            check(entry.url.equals(DefaultServerCatalog.factoryUrl(entry.id)),"factory URL lookup "+entry.id);
            check(entry.url.endsWith("/"),"trailing slash "+entry.id);
        }
        check("http://172.16.50.7/DHAKA-FLIX-7/".equals(DefaultServerCatalog.factoryUrl("df7")),"7 factory");
        check("http://172.16.50.10/DHAKA-FLIX-10/".equals(DefaultServerCatalog.factoryUrl("df10")),"10 factory");
        check("http://172.16.50.12/DHAKA-FLIX-12/".equals(DefaultServerCatalog.factoryUrl("df12")),"12 factory");
        check("http://172.16.50.14/DHAKA-FLIX-14/".equals(DefaultServerCatalog.factoryUrl("df14")),"14 factory");
        check(DefaultServerCatalog.factoryUrl("my-other-host")==null,"custom host has no preset");
        String initial=DefaultServerCatalog.factoryUrl("df7");
        check("http://10.0.0.20/DHAKA-FLIX-7/".equals(DefaultServerCatalog.resolveAddress(initial,"10.0.0.20")),"bare IP preserves path");
        check("https://10.0.0.20:9090/DHAKA-FLIX-7/".equals(DefaultServerCatalog.resolveAddress(initial,"https://10.0.0.20:9090")),"host port change keeps path");
        check("http://10.0.0.20/".equals(DefaultServerCatalog.resolveAddress(initial,"10.0.0.20/")),"explicit root path");
        check("http://10.0.0.20/New%20Videos/".equals(DefaultServerCatalog.resolveAddress(initial,"10.0.0.20/New Videos/")),"explicit folder path");
        check("https://example.com/Shows/".equals(DefaultServerCatalog.resolveAddress(initial,"https://example.com/Shows")),"explicit host path");
        check("http://10.0.0.20/".equals(DefaultServerCatalog.resolveAddress("http://1.1.1.1/","10.0.0.20")),"custom root remains root");
        boolean bad=false;try{DefaultServerCatalog.resolveAddress(initial,"ftp://10.0.0.20");}catch(IllegalArgumentException e){bad=true;}check(bad,"reject ftp");
        bad=false;try{DefaultServerCatalog.resolveAddress(initial,"http://user:pass@10.0.0.20/");}catch(IllegalArgumentException e){bad=true;}check(bad,"reject credentials");
        bad=false;try{DefaultServerCatalog.resolveAddress(initial,"http://10.0.0.20/Movies/?token=secret");}catch(IllegalArgumentException e){bad=true;}check(bad,"reject query");
        bad=false;try{DefaultServerCatalog.resolveAddress(initial," ");}catch(IllegalArgumentException e){bad=true;}check(bad,"reject blank");
        System.out.println("PASS "+assertions+" factory preset, URL validation, editing and path tests");
    }
}
