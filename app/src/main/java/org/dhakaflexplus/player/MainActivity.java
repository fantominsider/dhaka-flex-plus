package org.dhakaflexplus.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A new Android HTTP media-directory browser, not a modification of the original signed APK. */
public final class MainActivity extends Activity {
    static final int REQ_EXPORT=3101, REQ_IMPORT=3102;
    static final String[] SORT_OPTIONS={"Folders first · A to Z","Folders first · Z to A","Videos first · A to Z","All entries · A to Z"};
    static final int BG=Color.rgb(15,20,32);
    static final int CARD=Color.rgb(26,35,54);
    static final int CARD_EDGE=Color.rgb(50,65,88);
    static final int TEXT=Color.rgb(236,242,255);
    static final int MUTED=Color.rgb(158,174,200);
    static final int BLUE=Color.rgb(114,169,255);

    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicInteger requestVersion=new AtomicInteger();
    private ServerStore store;
    private ArrayList<ServerStore.Server> servers;
    private ServerStore.Server activeServer;
    private final ArrayList<DirectoryClient.Entry> allEntries=new ArrayList<>();
    private final ArrayList<DirectoryClient.Entry> shownEntries=new ArrayList<>();
    private final ArrayList<String> navigation=new ArrayList<>();
    private String currentUrl=null, currentScreen="home";
    private String currentTitle="Home";
    private LinearLayout root, mainContent, topBar;
    private TextView status,crumb,title;
    private EditText filter;
    private GridView grid;
    private EntryAdapter adapter;
    private String lastMessage="Select a server to browse its media folders";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new ServerStore(this);
        servers=store.load();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(b!=null){
            currentUrl=b.getString("current_url");
            currentTitle=b.getString("current_title","Home");
            String serverId=b.getString("server_id");
            for(ServerStore.Server s:servers)if(s.id.equals(serverId))activeServer=s;
            ArrayList<String> history=b.getStringArrayList("history");
            if(history!=null)navigation.addAll(history);
            currentScreen=b.getString("screen","home");
        }
        buildShell();
        if("browse".equals(currentScreen)&&currentUrl!=null)browse(currentUrl,currentTitle,false);
        else if("servers".equals(currentScreen))showServerManager();
        else if("favorites".equals(currentScreen))showFavorites();
        else if("recent".equals(currentScreen))showRecent();
        else if("collections".equals(currentScreen))showKnownCollections();
        else showHome();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putString("current_url",currentUrl);
        out.putString("current_title",currentTitle);
        out.putString("server_id",activeServer==null?"":activeServer.id);
        out.putString("screen",currentScreen);
        out.putStringArrayList("history",new ArrayList<>(navigation));
    }
    @Override protected void onDestroy(){requestVersion.incrementAndGet();executor.shutdownNow();super.onDestroy();}

    private int d(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(d(radius));return gd;
    }
    private GradientDrawable border(int color,int radius){
        GradientDrawable gd=bg(color,radius);gd.setStroke(d(1),CARD_EDGE);return gd;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
    private View spacer(int w){View v=new View(this);v.setLayoutParams(lp(w,1));return v;}
    private TextView action(String label,Runnable runnable){
        TextView v=text(label,13,TEXT,true);v.setPadding(d(12),0,d(12),0);v.setGravity(Gravity.CENTER);
        v.setBackground(border(CARD,12));v.setFocusable(true);v.setClickable(true);
        v.setOnClickListener(w->runnable.run());
        return v;
    }
    private void buildShell(){
        root=vertical();root.setBackgroundColor(BG);
        setContentView(root);
        topBar=horizontal();topBar.setPadding(d(14),d(12),d(14),d(8));
        TextView brand=text("▶  DHAKA FLEX PLUS",18,TEXT,true);
        brand.setSingleLine(true); brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
        brand.setOnClickListener(v->showHome());brand.setFocusable(true);
        topBar.addView(brand,new LinearLayout.LayoutParams(0,d(48),1));
        topBar.addView(action("⌂",this::showHome),lp(46,44));topBar.addView(spacer(6));
        topBar.addView(action("☰",this::showMenu),lp(46,44));
        root.addView(topBar);
        mainContent=vertical();root.addView(mainContent,new LinearLayout.LayoutParams(-1,0,1));
        status=text(lastMessage,12,MUTED,false);
        status.setPadding(d(16),d(10),d(16),d(12));
        root.addView(status,lp(-1,-2));
    }
    private void updateStatus(String m){lastMessage=m;if(status!=null)status.setText(m);}
    private void clearScreen(String screen){
        currentScreen=screen;
        mainContent.removeAllViews();
    }
    private void addHeader(String heading,String subtitle){
        LinearLayout header=vertical();header.setPadding(d(18),d(10),d(18),d(10));
        title=text(heading,24,TEXT,true);header.addView(title);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView sub=text(subtitle,12,MUTED,false);sub.setPadding(0,d(4),0,0);header.addView(sub);
        }
        mainContent.addView(header,lp(-1,-2));
    }
    private void showHome(){
        requestVersion.incrementAndGet();activeServer=null;currentUrl=null;currentTitle="Home";navigation.clear();
        clearScreen("home");
        addHeader("Your media servers",""+DeviceProfile.label(DeviceProfile.active(this))+" · Browse HTTP(S) sources or add an IP address.");
        LinearLayout buttons=horizontal();buttons.setPadding(d(16),d(4),d(16),d(12));
        TextView manage=action("+ Add / manage servers",this::showServerManager);
        buttons.addView(manage,new LinearLayout.LayoutParams(0,d(48),1));
        buttons.addView(spacer(8));buttons.addView(action("★ Favorites",this::showFavorites),lp(-2,48));
        mainContent.addView(buttons);
        TextView vlc=action("▶ VLC Player / IPTV / Live TV  ›",this::showVlc);
        LinearLayout.LayoutParams vp=lp(-1,52);vp.setMargins(d(16),0,d(16),d(12));
        mainContent.addView(vlc,vp);
        TextView categories=action("Browse 17 original collections  ›",this::showKnownCollections);
        LinearLayout.LayoutParams cp=lp(-1,46);cp.setMargins(d(16),0,d(16),d(12));
        mainContent.addView(categories,cp);
        TextView recent=action("▶ Continue watching / Recent media  ›",this::showRecent);
        LinearLayout.LayoutParams rp=lp(-1,46);rp.setMargins(d(16),0,d(16),d(12));
        mainContent.addView(recent,rp);
        if(servers.isEmpty()){
            TextView empty=text("No servers configured. Tap Add / manage servers to enter a server IP address.",15,MUTED,false);
            empty.setPadding(d(20),d(30),d(20),d(30));mainContent.addView(empty);return;
        }
        ServerStore.Server selected=defaultServer();
        if(selected!=null){
            TextView quick=action("★ Open default server · "+selected.name+"  ›",()->openServer(selected));
            LinearLayout.LayoutParams qp=lp(-1,48);qp.setMargins(d(16),0,d(16),d(10));
            mainContent.addView(quick,qp);
        }
        addServerGrid();
        updateStatus("Saved sources: "+servers.size()+" · Reachability depends on your network");
    }
    private int columnCount(){
        int width=getResources().getConfiguration().screenWidthDp;
        boolean vr=DeviceProfile.isVr(DeviceProfile.active(this));
        return Math.max(1,Math.min(5,vr? (width<600?2:width<1000?3:4) : (width<400?2:width<650?3:width<950?4:5)));
    }
    private ServerStore.Server defaultServer(){
        String id=store.defaultServerId(servers);
        for(ServerStore.Server item:servers)if(item.id.equals(id))return item;
        return null;
    }
    private void addServerGrid(){
        final ArrayList<ServerStore.Server> displayServers=new ArrayList<>(servers);
        String defaultId=store.defaultServerId(servers);
        for(int i=0;i<displayServers.size();i++)if(displayServers.get(i).id.equals(defaultId)){
            if(i!=0)java.util.Collections.swap(displayServers,0,i);
            break;
        }
        GridView g=new GridView(this);g.setNumColumns(columnCount());g.setVerticalSpacing(d(10));g.setHorizontalSpacing(d(10));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);g.setClipToPadding(false);
        g.setPadding(d(12),d(6),d(12),d(12));
        g.setAdapter(new BaseAdapter(){
            @Override public int getCount(){return displayServers.size();}
            @Override public Object getItem(int p){return displayServers.get(p);}
            @Override public long getItemId(int p){return p;}
            @Override public View getView(int p,View old,ViewGroup parent){
                ServerStore.Server server=displayServers.get(p);
                LinearLayout card=vertical();card.setBackground(border(CARD,16));
                card.setPadding(d(12),d(14),d(10),d(10));card.setMinimumHeight(d(126));
                TextView icon=text("◉",28,BLUE,true);card.addView(icon,lp(-1,38));
                TextView label=text((server.id.equals(defaultId)?"★ DEFAULT · ":"")+server.name,14,TEXT,true);
                label.setMaxLines(2);card.addView(label,lp(-1,-2));
                TextView ip=text(server.url,11,MUTED,false);ip.setMaxLines(2);ip.setPadding(0,d(6),0,0);
                ip.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(ip,lp(-1,-2));
                card.setFocusable(true);return card;
            }
        });
        g.setOnItemClickListener((p,v,position,id)->openServer(displayServers.get(position)));
        mainContent.addView(g,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void openServer(ServerStore.Server s){
        activeServer=s;navigation.clear();browse(s.url,s.name,false);
    }
    private void buildBrowser(){
        clearScreen("browse");
        LinearLayout nav=horizontal();nav.setPadding(d(14),d(2),d(14),d(4));
        nav.addView(action("‹ Back",this::navigateBack),lp(-2,44));nav.addView(spacer(8));
        crumb=text(currentTitle,18,TEXT,true);crumb.setSingleLine(true);
        crumb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nav.addView(crumb,new LinearLayout.LayoutParams(0,d(44),1));
        nav.addView(action("Sort",this::chooseSort),lp(62,44));nav.addView(spacer(5));
        nav.addView(action("↗",()->openExternal(currentUrl)),lp(44,44));
        mainContent.addView(nav);
        filter=new EditText(this);filter.setSingleLine(true);filter.setTextColor(TEXT);filter.setHintTextColor(MUTED);
        filter.setTextSize(15);filter.setHint("Search this folder");filter.setPadding(d(14),0,d(14),0);
        filter.setBackground(border(CARD,12));
        LinearLayout.LayoutParams fp=lp(-1,48);fp.setMargins(d(14),d(4),d(14),d(12));
        mainContent.addView(filter,fp);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){applyFilter(s.toString());}
            @Override public void afterTextChanged(Editable s){}
        });
        grid=new GridView(this);grid.setNumColumns(columnCount());
        grid.setHorizontalSpacing(d(10));grid.setVerticalSpacing(d(10));grid.setPadding(d(12),d(4),d(12),d(12));
        grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter=new EntryAdapter();grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,v,pos,id)->{
            DirectoryClient.Entry entry=shownEntries.get(pos);
            if(entry.directory){navigation.add(currentUrl);browse(entry.url,entry.name,false);} else play(entry);
        });
        grid.setOnItemLongClickListener((parent,v,pos,id)->{showItemActions(shownEntries.get(pos));return true;});
        mainContent.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void browse(String url,String name,boolean fromHistory){
        currentUrl=url;currentTitle=name;buildBrowser();
        final int version=requestVersion.incrementAndGet();
        allEntries.clear();shownEntries.clear();adapter.notifyDataSetChanged();
        updateStatus("Connecting to "+Uri.parse(url).getHost()+" …");
        executor.execute(()->{
            try{
                ArrayList<DirectoryClient.Entry> list=DirectoryClient.browse(url);
                runOnUiThread(()->{
                    if(version!=requestVersion.get()||isFinishing())return;
                    allEntries.clear();allEntries.addAll(list);applyFilter(filter.getText().toString());
                    if(list.isEmpty())updateStatus("No playable files/folders found · Does this URL show an HTTP directory index? Use ↗ to view the page.");
                    else updateStatus(list.size()+" folders/files · "+Uri.parse(url).getHost());
                });
            }catch(Exception e){
                final String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                runOnUiThread(()->{
                    if(version!=requestVersion.get()||isFinishing())return;
                    updateStatus("Connection failed: "+msg);
                    showError("Could not browse this server",msg+"\n\nCheck Wi-Fi/ISP access and that the address points to an HTTP directory listing.");
                });
            }
        });
    }
    private void applyFilter(String term){
        shownEntries.clear();String query=term.trim().toLowerCase(Locale.ROOT);
        for(DirectoryClient.Entry e:allEntries)if(query.isEmpty()||e.name.toLowerCase(Locale.ROOT).contains(query))shownEntries.add(e);
        int mode=store.preferences().getInt("sort_mode",0);
        Collections.sort(shownEntries,(a,b)->{
            if(mode!=3&&a.directory!=b.directory)return (mode==2?(a.directory?1:-1):(a.directory?-1:1));
            return mode==1?b.name.compareToIgnoreCase(a.name):a.name.compareToIgnoreCase(b.name);
        });
        if(adapter!=null)adapter.notifyDataSetChanged();
    }
    private void chooseSort(){
        int current=store.preferences().getInt("sort_mode",0);
        if(current<0||current>=SORT_OPTIONS.length)current=0;
        new AlertDialog.Builder(this).setTitle("Sort this folder")
            .setSingleChoiceItems(SORT_OPTIONS,current,(dialog,which)->{
                store.preferences().edit().putInt("sort_mode",which).apply();
                dialog.dismiss();if(filter!=null)applyFilter(filter.getText().toString());
            }).setNegativeButton("Cancel",null).show();
    }
    private class EntryAdapter extends BaseAdapter {
        @Override public int getCount(){return shownEntries.size();}
        @Override public Object getItem(int p){return shownEntries.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View old,ViewGroup parent){
            DirectoryClient.Entry entry=shownEntries.get(p);
            LinearLayout card=vertical();card.setPadding(d(12),d(12),d(10),d(10));
            card.setMinimumHeight(d(112));card.setBackground(border(CARD,14));
            TextView symbol=text(entry.directory?"▣":"▶",27,BLUE,true);card.addView(symbol,lp(-1,38));
            TextView name=text(entry.name,14,TEXT,true);name.setMaxLines(3);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,lp(-1,-2));
            TextView kind=text(entry.directory?"FOLDER":"MEDIA",10,MUTED,false);kind.setPadding(0,d(8),0,0);
            card.addView(kind,lp(-1,-2));card.setFocusable(true);return card;
        }
    }
    private void play(DirectoryClient.Entry e){
        Intent i=new Intent(this,PlayerActivity.class);
        i.putExtra("media_url",e.url);i.putExtra("media_title",e.name);startActivity(i);
    }
    private void showItemActions(DirectoryClient.Entry e){
        LinkedHashSet<String> favorites=store.favorites();
        String favText=favorites.contains(e.url)?"Remove favorite":"Add favorite";
        String[] options=e.directory?new String[]{"Open folder",favText,"Copy URL","Open in browser"}:
                new String[]{"Play", "Open in external player",favText,"Copy URL"};
        new AlertDialog.Builder(this).setTitle(e.name).setItems(options,(dialog,which)->{
            String choice=options[which];
            if(choice.equals("Open folder")){navigation.add(currentUrl);browse(e.url,e.name,false);}
            else if(choice.equals("Play"))play(e);
            else if(choice.equals("Open in external player"))PlayerActivity.openExternal(this,e.url);
            else if(choice.equals("Add favorite")||choice.equals("Remove favorite")){store.toggleFavorite(e.url);toast("Favorites updated");}
            else if(choice.equals("Copy URL"))copy(e.url);
            else if(choice.equals("Open in browser"))openExternal(e.url);
        }).show();
    }
    private void copy(String s){
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Media URL",s));toast("Link copied");
    }
    private void openExternal(String s){
        if(s==null)return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(s)));}
        catch(Exception e){toast("No browser available");}
    }
    private void showVlc(){startActivity(new Intent(this,VlcHubActivity.class));}
    private void showMenu(){
        PopupMenu menu=new PopupMenu(this,topBar.getChildAt(topBar.getChildCount()-1));
        menu.getMenu().add("Manage servers");menu.getMenu().add("Original collections");menu.getMenu().add("Favorites");
        menu.getMenu().add("Continue watching");menu.getMenu().add("VLC Player / Live TV");
        menu.getMenu().add("Test all servers");
        menu.getMenu().add("Backup / export");menu.getMenu().add("Restore / import");
        menu.getMenu().add("Open HTTP address");menu.getMenu().add("Device / display profile");
        menu.getMenu().add("Device & network info");menu.getMenu().add("About / compatibility");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getTitle().toString()){
                case "Manage servers":showServerManager();break;
                case "Favorites":showFavorites();break;
                case "Continue watching":showRecent();break;
                case "VLC Player / Live TV":showVlc();break;
                case "Test all servers":testAllServers();break;
                case "Backup / export":exportBackup();break;
                case "Restore / import":importBackup();break;
                case "Device & network info":showDiagnostics();break;
                case "Original collections":showKnownCollections();break;
                case "Open HTTP address":promptQuickUrl();break;
                case "Device / display profile":showDisplayProfile();break;
                case "About / compatibility":showAbout();break;
            }return true;
        });
        menu.show();
    }
    private void showServerManager(){
        requestVersion.incrementAndGet();clearScreen("servers");
        addHeader("Server manager","The original Dhaka Flex hosts are prefilled and editable. Restore loads the factory IP into the field; Save IP applies it. Choose one server as your home default.");
        LinearLayout row=horizontal();row.setPadding(d(14),d(4),d(14),d(12));
        row.addView(action("+ Add server",()->editServer(null)),new LinearLayout.LayoutParams(0,d(48),1));
        row.addView(spacer(8));row.addView(action("Test all",this::testAllServers),lp(-2,48));
        mainContent.addView(row);
        TextView addPresets=action("↺ Re-add missing factory presets",()->{
            int added=ServerStore.addMissingFactoryPresets(servers);
            if(added>0)store.save(servers);
            toast(added==0?"All four factory presets are already available":added+" factory preset(s) restored");
            showServerManager();
        });
        LinearLayout.LayoutParams pres=lp(-1,42);pres.setMargins(d(14),0,d(14),d(10));
        mainContent.addView(addPresets,pres);
        LinearLayout backups=horizontal();backups.setPadding(d(14),0,d(14),d(12));
        backups.addView(action("Export backup",this::exportBackup),new LinearLayout.LayoutParams(0,d(42),1));
        backups.addView(spacer(8));
        backups.addView(action("Import backup",this::importBackup),new LinearLayout.LayoutParams(0,d(42),1));
        mainContent.addView(backups);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=vertical();list.setPadding(d(14),0,d(14),d(16));
        String defaultId=store.defaultServerId(servers);
        for(ServerStore.Server s:new ArrayList<>(servers)){
            LinearLayout card=vertical();card.setPadding(d(12),d(12),d(12),d(10));
            card.setBackground(border(CARD,14));
            String badge=s.id.equals(defaultId)?" ★ DEFAULT":"";
            TextView name=text(s.name+badge,16,s.id.equals(defaultId)?BLUE:TEXT,true);card.addView(name);
            if(DefaultServerCatalog.isPreset(s.id)){
                TextView hint=text("Factory preset: "+DefaultServerCatalog.factoryUrl(s.id),11,MUTED,false);
                hint.setPadding(0,d(3),0,d(5));card.addView(hint);
            }
            LinearLayout ipRow=horizontal();
            EditText address=field("IP or complete HTTP(S) directory URL",s.url);
            address.setTextSize(13);address.setPadding(d(8),0,d(6),0);
            address.setBackground(border(BG,10));
            ipRow.addView(address,new LinearLayout.LayoutParams(0,d(50),1));
            if(DefaultServerCatalog.isPreset(s.id)){
                ipRow.addView(spacer(6));
                TextView restore=action("↺ Restore\nDefault IP",()->{
                    address.setText(DefaultServerCatalog.factoryUrl(s.id));
                    address.setSelection(address.length());
                    updateStatus("Factory IP loaded for "+s.name+". Tap Save IP to keep the change.");
                });
                restore.setTextSize(11);ipRow.addView(restore,lp(120,50));
            }
            card.addView(ipRow);
            LinearLayout primary=horizontal();primary.setPadding(0,d(8),0,0);
            primary.addView(action("Save IP",()->saveIpField(s,address,false)),new LinearLayout.LayoutParams(0,d(44),1));
            primary.addView(spacer(7));
            primary.addView(action(s.id.equals(defaultId)?"★ Default Server":"☆ Make Default",
                    ()->saveIpField(s,address,true)),new LinearLayout.LayoutParams(0,d(44),1));
            card.addView(primary);
            LinearLayout secondary=horizontal();secondary.setPadding(0,d(7),0,0);
            secondary.addView(action("Browse",()->openServer(s)),new LinearLayout.LayoutParams(0,d(42),1));
            secondary.addView(spacer(5));secondary.addView(action("Test",()->testServer(s)),new LinearLayout.LayoutParams(0,d(42),1));
            secondary.addView(spacer(5));secondary.addView(action("Rename",()->editServer(s)),new LinearLayout.LayoutParams(0,d(42),1));
            secondary.addView(spacer(5));secondary.addView(action("✕",()->deleteServer(s)),lp(42,42));
            card.addView(secondary);
            LinearLayout.LayoutParams c=lp(-1,-2);c.setMargins(0,0,0,d(10));list.addView(card,c);
        }
        scroll.addView(list);mainContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        updateStatus("Saved sources: "+servers.size()+" · Default: "+(defaultServer()==null?"None":defaultServer().name));
    }
    private void saveIpField(ServerStore.Server server,EditText field,boolean makeDefault){
        String updated;
        try{updated=DefaultServerCatalog.resolveAddress(server.url,field.getText().toString());}
        catch(IllegalArgumentException ex){field.setError(ex.getMessage());return;}
        for(ServerStore.Server other:servers){
            if(other!=server&&other.url.equalsIgnoreCase(updated)){
                field.setError("This address is already saved under "+other.name);return;
            }
        }
        server.url=updated;
        store.save(servers);
        if(makeDefault)store.setDefaultServerId(server.id,servers);
        toast(makeDefault?"Saved and set as default server":"Server IP saved");
        showServerManager();
    }
    private EditText field(String placeholder,String value){
        EditText e=new EditText(this);e.setText(value);e.setSingleLine(true);e.setTextSize(16);
        e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(placeholder);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        e.setSelectAllOnFocus(true);return e;
    }
    private void editServer(ServerStore.Server current){
        LinearLayout panel=vertical();panel.setPadding(d(18),0,d(18),0);
        TextView label=text("Display name",13,MUTED,true);panel.addView(label);
        EditText name=field("My media server",current==null?"":current.name);panel.addView(name);
        TextView urlLabel=text("IP address, port and optional directory path",13,MUTED,true);
        urlLabel.setPadding(0,d(12),0,0);panel.addView(urlLabel);
        EditText address=field("192.168.1.50:8080/Movies/",current==null?"":current.url);panel.addView(address);
        TextView tip=text("Example: http://192.168.1.50:8080/Movies/\nHTTP(S) directory listings only. Use a server you are authorized to access.",12,MUTED,false);
        tip.setPadding(0,d(12),0,0);panel.addView(tip);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(current==null?"Add server":"Edit server")
                .setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim();String u;
            try{u=current==null?ServerStore.canonicalUrl(address.getText().toString())
                    :DefaultServerCatalog.resolveAddress(current.url,address.getText().toString());}
            catch(IllegalArgumentException e){address.setError(e.getMessage());return;}
            if(n.isEmpty())n=Uri.parse(u).getHost();
            for(ServerStore.Server other:servers){
                if(other!=current&&other.url.equalsIgnoreCase(u)){
                    address.setError("Already saved under "+other.name);return;
                }
            }
            if(current==null)servers.add(new ServerStore.Server(UUID.randomUUID().toString(),n,u));
            else{current.name=n;current.url=u;}
            store.save(servers);dialog.dismiss();showServerManager();
        }));
        dialog.show();
    }
    private void deleteServer(ServerStore.Server s){
        new AlertDialog.Builder(this).setTitle("Remove "+s.name+"?")
                .setMessage("This removes the saved server entry, not any files on the server.")
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,which)->{
                    servers.remove(s);store.save(servers);showServerManager();
                }).show();
    }
    private void testServer(ServerStore.Server s){
        int version=requestVersion.incrementAndGet();
        updateStatus("Testing "+s.url+" …");
        executor.execute(()->{
            String message;
            try{
                ArrayList<DirectoryClient.Entry> entries=DirectoryClient.browse(s.url);
                message="Connected · "+entries.size()+" folders/videos detected";
            }catch(Exception e){message="Test failed · "+e.getMessage();}
            final String result=message;
            runOnUiThread(()->{if(version==requestVersion.get()&&!isFinishing()){
                updateStatus(s.name+": "+result);
                new AlertDialog.Builder(this).setTitle(s.name).setMessage(result).setPositiveButton("OK",null).show();
            }});
        });
    }
    private void testAllServers(){
        if(servers.isEmpty()){toast("No servers configured");return;}
        final ArrayList<ServerStore.Server> toTest=new ArrayList<>(servers);
        final int version=requestVersion.incrementAndGet();
        final int count=Math.min(30,toTest.size());
        updateStatus("Testing " + count + " servers · HTTP response only, not video decode …");
        executor.execute(()->{
            StringBuilder results=new StringBuilder();int pass=0,fail=0;
            for(int i=0;i<count;i++){
                if(version!=requestVersion.get())return;
                ServerStore.Server sv=toTest.get(i);
                ServerProbe.Result result=ServerProbe.probe(sv.url);
                if(result.reachable)pass++;else fail++;
                results.append(result.reachable?"✓  ":"✕  ").append(sv.name).append(" — ")
                    .append(result.description).append("\n");
                final int completed=i+1,passed=pass;
                runOnUiThread(()->{if(version==requestVersion.get()&&!isFinishing())
                    updateStatus("Checked "+completed+"/"+count+" · reachable "+passed);});
            }
            final int healthy=pass,failed=fail;
            final String summary=results.toString()+(toTest.size()>count?"\nOnly the first 30 sources were checked.":"");
            runOnUiThread(()->{if(version!=requestVersion.get()||isFinishing())return;
                updateStatus("Server check: "+healthy+" reachable · "+failed+" failed");
                new AlertDialog.Builder(this).setTitle("Server reachability: "+healthy+" / "+count)
                    .setMessage(summary+"\nReachable means an HTTP response was received, not that media decoding works.")
                    .setPositiveButton("OK",null).show();
            });
        });
    }
    private void showKnownCollections(){
        requestVersion.incrementAndGet();clearScreen("collections");
        addHeader("Dhaka Flex collections","17 original category shortcuts use your saved (editable) server IPs.");
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=vertical();list.setPadding(d(14),d(2),d(14),d(14));
        int count=0;
        for(ServerStore.Server server:servers){
            final String[] paths;
            if("df7".equals(server.id))paths=new String[]{"3D Movies","English Movies","Foreign Language Movies","Kolkata Bangla Movies"};
            else if("df10".equals(server.id))paths=new String[]{"Anime & Cartoon TV Series","Awards & TV Shows","Documentary","WWE & AEW Wrestling"};
            else if("df12".equals(server.id))paths=new String[]{"TV-WEB-Series"};
            else if("df14".equals(server.id))paths=new String[]{"Animation Movies (1080p)","Animation Movies","English Movies (1080p)","Hindi Movies","IMDb Top-250 Movies","KOREAN TV & WEB Series","SOUTH INDIAN MOVIES/Hindi Dubbed","SOUTH INDIAN MOVIES/South Movies"};
            else continue;
            TextView host=text(server.name,16,BLUE,true);host.setPadding(d(4),d(10),0,d(10));list.addView(host);
            for(String path:paths){
                String url=joinEncodedPath(server.url,path);
                TextView item=action("▣  "+path,()->{
                    activeServer=server;navigation.clear();browse(url,path,false);
                });
                item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                LinearLayout.LayoutParams params=lp(-1,52);params.setMargins(0,0,0,d(6));list.addView(item,params);
                count++;
            }
        }
        if(count==0){
            TextView empty=text("No matching preset servers found. Restore a preset in Server Manager to get the original shortcuts.",14,MUTED,false);
            list.addView(empty);
        }
        scroll.addView(list);mainContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        updateStatus(count+" optional collection shortcuts · Access requires permission and network reachability");
    }
    private String joinEncodedPath(String base,String path){
        StringBuilder b=new StringBuilder(base.endsWith("/")?base:base+"/");
        String[] parts=path.split("/");
        for(String part:parts){
            try{b.append(java.net.URLEncoder.encode(part,"UTF-8").replace("+","%20"));}
            catch(Exception e){b.append(part.replace(" ","%20"));}
            b.append('/');
        }
        return b.toString();
    }

    private void showFavorites(){
        requestVersion.incrementAndGet();clearScreen("favorites");
        addHeader("Favorites","Saved folder and video links from any configured server");
        LinkedHashSet<String> fav=store.favorites();
        if(fav.isEmpty()){
            TextView tip=text("To save an item, open a server and long-press a folder or video.",15,MUTED,false);
            tip.setPadding(d(18),d(18),d(18),d(18));mainContent.addView(tip);return;
        }
        ScrollView scroll=new ScrollView(this);LinearLayout list=vertical();list.setPadding(d(14),0,d(14),d(12));
        for(String u:fav){
            boolean folder=u.endsWith("/");
            String label=DirectoryClient.nameFromUrl(u);
            LinearLayout card=horizontal();card.setPadding(d(10),d(8),d(8),d(8));card.setBackground(border(CARD,12));
            TextView name=text((folder?"▣  ":"▶  ")+label,14,TEXT,true);
            name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setOnClickListener(v->{
                if(folder){activeServer=null;navigation.clear();browse(u,label,false);}
                else play(new DirectoryClient.Entry(label,u,false));
            });name.setFocusable(true);
            card.addView(name,new LinearLayout.LayoutParams(0,d(48),1));
            card.addView(action("✕",()->{store.toggleFavorite(u);showFavorites();}),lp(40,40));
            LinearLayout.LayoutParams p=lp(-1,-2);p.setMargins(0,0,0,d(8));list.addView(card,p);
        }
        scroll.addView(list);mainContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void showRecent(){
        requestVersion.incrementAndGet();clearScreen("recent");
        addHeader("Continue watching","Your recent media across all configured HTTP servers · saved on this device");
        ArrayList<WatchHistory.Item> items=WatchHistory.load(this);
        LinearLayout controls=horizontal();controls.setPadding(d(14),d(4),d(14),d(12));
        controls.addView(action("‹ Home",this::showHome),new LinearLayout.LayoutParams(0,d(44),1));
        controls.addView(spacer(8));
        controls.addView(action("Clear history",()->{
            new AlertDialog.Builder(this).setTitle("Clear viewing history?")
                .setMessage("This removes the recent list and its stored resume points. Media files are not changed.")
                .setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{
                    for(WatchHistory.Item item:WatchHistory.load(this))
                        store.preferences().edit().remove("position_"+item.url.hashCode()).apply();
                    WatchHistory.clear(this);showRecent();
                }).show();
        }),lp(-2,44));
        mainContent.addView(controls);
        if(items.isEmpty()){
            TextView hint=text("No media played yet. Open a server and select a movie to start.",14,MUTED,false);
            hint.setPadding(d(18),d(20),d(18),d(20));mainContent.addView(hint);return;
        }
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=vertical();list.setPadding(d(14),0,d(14),d(14));
        for(WatchHistory.Item item:items){
            LinearLayout card=horizontal();card.setPadding(d(10),d(9),d(8),d(9));card.setBackground(border(CARD,12));
            LinearLayout detail=vertical();
            TextView name=text("▶  "+item.title,14,TEXT,true);name.setMaxLines(2);detail.addView(name);
            String second=(item.completed?"Watched":item.positionMs>0?"Resume at "+WatchHistory.time(item.positionMs):"Recently opened")
                +" · "+Uri.parse(item.url).getHost();
            TextView meta=text(second,12,MUTED,false);meta.setPadding(0,d(5),0,0);detail.addView(meta);
            detail.setClickable(true);detail.setFocusable(true);
            detail.setOnClickListener(v->play(new DirectoryClient.Entry(item.title,item.url,false)));
            card.addView(detail,new LinearLayout.LayoutParams(0,d(58),1));
            card.addView(action("✕",()->{WatchHistory.remove(this,item.url);store.preferences().edit().remove("position_"+item.url.hashCode()).apply();showRecent();}),lp(42,42));
            LinearLayout.LayoutParams lp=lp(-1,-2);lp.setMargins(0,0,0,d(8));list.addView(card,lp);
        }
        scroll.addView(list);mainContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        updateStatus(items.size()+" recent entries · XR-browser progress is not synchronized");
    }
    private void exportBackup(){
        Intent choose=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);choose.setType("application/json");
        choose.putExtra(Intent.EXTRA_TITLE,"DhakaFlexPlus-backup.json");
        try{startActivityForResult(choose,REQ_EXPORT);}
        catch(Exception e){showError("Backup unavailable","No Android document provider: "+e.getMessage());}
    }
    private void importBackup(){
        Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);choose.setType("*/*");
        try{startActivityForResult(choose,REQ_IMPORT);}
        catch(Exception e){showError("Import unavailable","No Android document provider: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==REQ_EXPORT){
            try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
                if(output==null)throw new java.io.IOException("Could not open destination");
                output.write(LibraryBackup.exportJson(this).getBytes(StandardCharsets.UTF_8));
                output.flush();
                new AlertDialog.Builder(this).setTitle("Backup saved")
                    .setMessage("Servers, IPTV channels, favorites, recent history and sorting preferences were exported. Your device profile stays local. The file contains private network URLs; share it carefully.")
                    .setPositiveButton("OK",null).show();
            }catch(Exception e){showError("Could not export backup",e.getMessage());}
        }else if(request==REQ_IMPORT){
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new java.io.IOException("Could not read file");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
                while((n=input.read(chunk))!=-1){
                    if(bytes.size()+n>LibraryBackup.MAX_FILE_BYTES)throw new IllegalArgumentException("Backup exceeds 2 MB");
                    bytes.write(chunk,0,n);
                }
                final LibraryBackup.ImportPlan plan=LibraryBackup.parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                new AlertDialog.Builder(this).setTitle("Restore media library?")
                    .setMessage(plan.servers.size()+" servers, "+plan.iptv.size()+" IPTV channels, "+plan.favorites.size()+" favorites and "+plan.history.size()+" recent entries.\n\nMerge keeps existing records. Replace overwrites saved servers, favorites, history and sorting preferences. Your device profile stays unchanged. No remote server files are touched.")
                    .setNegativeButton("Cancel",null)
                    .setNeutralButton("Merge",(d,w)->applyImport(plan,false))
                    .setPositiveButton("Replace",(d,w)->applyImport(plan,true)).show();
            }catch(Exception e){showError("Could not import backup",e.getMessage());}
        }
    }
    private void applyImport(LibraryBackup.ImportPlan plan,boolean replace){
        LibraryBackup.apply(this,plan,replace);
        servers=store.load();activeServer=null;navigation.clear();
        updateStatus("Library restored from backup");showHome();
        toast(replace?"Backup replaced saved library":"Backup merged into saved library");
    }
    private void showDiagnostics(){
        DeviceProfile.Kind found=DeviceProfile.detected(this),selected=DeviceProfile.active(this);
        String network="Unidentified / unavailable";
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(cm!=null){
                if(Build.VERSION.SDK_INT>=23){
                    NetworkCapabilities c=cm.getNetworkCapabilities(cm.getActiveNetwork());
                    if(c!=null){
                        if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))network="Wi-Fi";
                        else if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))network="Ethernet";
                        else if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))network="Mobile data";
                        else network="Other active network";
                    }else network="No active network";
                }else{
                    NetworkInfo n=cm.getActiveNetworkInfo();network=n==null?"No active network":n.getTypeName();
                }
            }
        }catch(Exception ignored){}
        int screens=1;
        try{DisplayManager dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);if(dm!=null)screens=dm.getDisplays().length;}catch(Exception ignored){}
        String report="Manufacturer: "+Build.MANUFACTURER+"\nModel: "+Build.MODEL+
            "\nAndroid SDK: "+Build.VERSION.SDK_INT+
            "\nDetected profile: "+DeviceProfile.label(found)+
            "\nActive profile: "+DeviceProfile.label(selected)+
            "\nDisplay count: "+screens+
            "\nNetwork: "+network+
            "\nSaved servers: "+servers.size()+
            "\n\nExternal display detection cannot prove XREAL is connected. Server accessibility must be checked separately.";
        new AlertDialog.Builder(this).setTitle("Device & network info").setMessage(report)
            .setNeutralButton("Copy report",(d,w)->copy(report))
            .setNegativeButton("Close",null).show();
    }
    private void promptQuickUrl(){
        EditText e=field("http://192.168.1.50:8080/Movies/","");e.setPadding(d(18),d(8),d(18),d(8));
        new AlertDialog.Builder(this).setTitle("Open an HTTP directory")
                .setView(e).setNegativeButton("Cancel",null).setPositiveButton("Open",(dialog,which)->{
                    try{String url=ServerStore.canonicalUrl(e.getText().toString());activeServer=null;navigation.clear();browse(url,DirectoryClient.nameFromUrl(url),false);}
                    catch(Exception ex){toast(ex.getMessage());}
                }).show();
    }
    private void showDisplayProfile(){
        String saved=store.preferences().getString(DeviceProfile.KEY,"auto");
        int selected=0;
        for(int i=0;i<DeviceProfile.VALUES.length;i++)if(DeviceProfile.VALUES[i].equals(saved))selected=i;
        new AlertDialog.Builder(this).setTitle("Display: "+DeviceProfile.label(DeviceProfile.detected(this)))
                .setSingleChoiceItems(DeviceProfile.OPTIONS,selected,(dialog,which)->{
                    store.preferences().edit().putString(DeviceProfile.KEY,DeviceProfile.VALUES[which]).apply();
                    dialog.dismiss();showHome();
                }).setNegativeButton("Cancel",null).show();
    }
    private void showAbout(){
        new AlertDialog.Builder(this).setTitle("Dhaka Flex Plus · v0.6")
                .setMessage("A separate, editable Android app for browsing HTTP(S) directory listings on accessible local/ISP servers.\n\n"+
                        "Four optional factory IP presets (.7, .10, .12, .14) are prefilled. Restore Default IP does not save until you choose Save IP; Make Default selects the preferred home server. A bare IP preserves the current directory prefix. No automatic connection to factory servers is made.\n\n"+
                        "Supports Android phone portrait/landscape, tablets, Android TV (D-pad), saved hosts, favorites, recent history, resume positions, sorting, backup/restore, and batch reachability checks. Device profile is auto-detected with manual override.\n\n"+
                        "VR theater and 180/360 use the headset browser WebXR engine when available. Built-in Android 2D panels alone are not immersive. XREAL One is a connected display, not an APK-installable headset.\n\n"+
                        "Built-in LibVLC powers Android streaming playback and a separate VLC / IPTV home section. It supports network stream URLs, M3U imports and standard playback controls. Native WebXR uses the headset browser decoder, not LibVLC.\n\n"+
                        "Does not yet implement the complete VLC Android application feature set (SMB/FTP media browser, network discovery, equalizer, casting, EPG, DRM or full hardware XR).\n\n"+
                        "Only browse media for which you have access and viewing rights.")
                .setPositiveButton("OK",null).show();
    }
    private void showError(String title,String msg){
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("OK",null)
                .setNeutralButton("Open in browser",(dialog,which)->openExternal(currentUrl)).show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void navigateBack(){
        if("browse".equals(currentScreen)){
            if(navigation.size()>0){String previous=navigation.remove(navigation.size()-1);browse(previous,DirectoryClient.nameFromUrl(previous),true);}
            else showHome();
        }else showHome();
    }
    @Override public void onBackPressed(){navigateBack();}
}
