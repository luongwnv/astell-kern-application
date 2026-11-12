package com.example.akplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.session.MediaSession;
import android.media.MediaDescription;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG=0xff111112, PANEL=0xff1c1c1e, LINE=0xff303033, RED=0xffb32d39, WHITE=0xffeeeeef, MUTED=0xff8e8e93;
    private final ArrayList<Song> songs=new ArrayList<>(), queue=new ArrayList<>();
    private final Set<String> favorites=new HashSet<>();
    private final Handler handler=new Handler();
    private LinearLayout root, content, drawerPanel; private FrameLayout screen, drawerOverlay; private MediaPlayer player; private boolean playerReady=false; private int current=-1; private String category="SONGS", query=""; private boolean full=false, shuffle=false, repeat=false, dragging=false; private SeekBar seek; private TextView elapsed, duration;
    private final Random random=new Random();
    private final SeekPositionTracker seekPosition=new SeekPositionTracker();
    private final ExecutorService libraryWorker=Executors.newSingleThreadExecutor();
    private int libraryGeneration;
    private boolean resumedOnce;
    private final Runnable drawerTimeout=()->closeDrawer();
    private void resetDrawerTimeout(){handler.removeCallbacks(drawerTimeout);if(drawerOverlay!=null)handler.postDelayed(drawerTimeout,8000);}
    private boolean externalScreen=false, ampScreen=false, eqScreen=false, homeScreen=false, externalQueueOpen=false, drawerClosing=false;
    private DeviceEqualizer equalizer;
    private SystemEqualizerService eqService;
    private boolean eqBound;
    private final ServiceConnection eqConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            eqService=((SystemEqualizerService.LocalBinder)binder).getService();
            try{eqService.restoreIfEnabled();}catch(RuntimeException e){android.util.Log.w("CrimsonEQ","Could not start system EQ",e);}
            equalizer=eqService.getEqualizer();
            if(eqScreen)showEqualizer();
        }
        @Override public void onServiceDisconnected(ComponentName name){eqService=null;equalizer=null;if(eqScreen)showEqualizer();}
    };
    private MediaController externalController, miniController;
    private TextView miniTitle; private PlayerIcon miniToggle;
    private final MediaController.Callback externalCallback=new MediaController.Callback(){
        @Override public void onMetadataChanged(MediaMetadata metadata){if(externalScreen&&!dragging&&!seekPosition.isPending())renderExternal();}
        @Override public void onPlaybackStateChanged(PlaybackState state){if(externalScreen){updatePlaybackProgress();if(!dragging&&!seekPosition.isPending())renderExternal();}}
        @Override public void onQueueChanged(List<MediaSession.QueueItem> items){if(externalScreen&&externalQueueOpen)showExternalQueue();}
        @Override public void onSessionDestroyed(){externalController=null;if(externalScreen)showExternal();}
    };
    static class Song { long id, albumId, duration; String title, artist, album; Uri uri; }
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); favorites.addAll(getPreferences(0).getStringSet("favorites",new HashSet<>())); eqBound=bindService(new Intent(this,SystemEqualizerService.class),eqConnection,BIND_AUTO_CREATE); showLibrary(); requestLibrary(); if(SystemEqualizerService.SHOW_EQ.equals(getIntent().getAction()))showEqualizer(); handler.post(tick); }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private TextView button(String s,int size,Runnable action){TextView t=text(s,size,WHITE); t.setGravity(Gravity.CENTER); t.setOnClickListener(v->action.run()); return t;}
    private GradientDrawable shape(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));}
    private void weighted(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(0,-1,1));}
    private void divider(LinearLayout p){View v=new View(this);v.setBackgroundColor(LINE);p.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void base(){handler.removeCallbacks(drawerTimeout);homeScreen=false;drawerClosing=false;miniController=null;miniTitle=null;miniToggle=null;drawerOverlay=null;drawerPanel=null;screen=new FrameLayout(this);screen.setFitsSystemWindows(true);LinearLayout shell=column();shell.setBackgroundColor(BG);root=column();root.setBackgroundColor(BG);shell.addView(root,new LinearLayout.LayoutParams(-1,0,1));LinearLayout navigation=row();navigation.setBackgroundColor(0xff202326);weighted(navigation,icon("home",true,this::showLibrary,"Library"));weighted(navigation,icon("playing",true,()->{if(externalScreen&&externalController!=null)renderExternal();else if(current>=0)showPlayer();else showExternal();},"Now playing"));weighted(navigation,icon("menu",true,this::showMenu,"Menu"));weighted(navigation,icon("back",true,this::onBackPressed,"Back"));add(shell,navigation,-1,48);screen.addView(shell,new FrameLayout.LayoutParams(-1,-1));setContentView(screen);}
    private void requestLibrary(){String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE; if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{permission},7); else loadSongs();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)loadSongs();else showLibrary();}}
    private void loadSongs(){
        final int generation=++libraryGeneration;
        libraryWorker.execute(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            ArrayList<Song> loaded=new ArrayList<>();boolean failed=false;
            String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.DURATION};
            try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")){
                if(c!=null)while(c.moveToNext()){
                    Song song=new Song();song.id=c.getLong(0);song.title=c.getString(1);song.artist=clean(c.getString(2));song.album=clean(c.getString(3));song.albumId=c.getLong(4);song.duration=c.getLong(5);song.uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,song.id);loaded.add(song);
                }
            }catch(Exception e){failed=true;android.util.Log.w("CrimsonLibrary","Could not read music library",e);}
            final boolean error=failed;
            handler.post(()->{
                if(isDestroyed()||generation!=libraryGeneration)return;
                if(error){Toast.makeText(this,"Could not read the music library",Toast.LENGTH_LONG).show();return;}
                songs.clear();songs.addAll(loaded);
                if(homeScreen)showLibrary();
            });
        });
    }
    private String clean(String s){return s==null||s.equals("<unknown>")?"Unknown":s;}
    private void showLibrary(){externalQueueOpen=false;eqScreen=false;ampScreen=false;externalScreen=false;releaseExternal();full=false;base();homeScreen=true; LinearLayout header=row();header.setPadding(dp(16),dp(8),dp(16),0);add(header,icon("hamburger",true,this::showMenu,"Menu"),40,48);ImageView brand=new ImageView(this);brand.setImageResource(com.example.akplayer.R.drawable.astell_wordmark);brand.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.setPadding(dp(8),dp(5),dp(8),dp(5));brand.setColorFilter(new android.graphics.ColorMatrixColorFilter(new android.graphics.ColorMatrix(new float[]{0,0,0,0,238, 0,0,0,0,238, 0,0,0,0,239, -3,0,0,0,735})));brand.setContentDescription("Astell and Kern");weighted(header,brand);add(header,icon("search",true,this::showSearch,"Search"),40,48);add(root,header,-1,56);
        LinearLayout title=row();title.setPadding(dp(22),0,dp(22),0);TextView lib=text("LIBRARY",15,MUTED);lib.setLetterSpacing(.16f);weighted(title,lib);add(title,button("↻",32,this::requestLibrary),40,40);add(root,title,-1,48);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout tabrow=row();tabrow.setPadding(dp(14),0,dp(14),0);for(String name:new String[]{"SONGS","ALBUMS","ARTISTS","FAVORITES"}){TextView t=button(name,14,()->{category=name;showLibrary();});t.setTextColor(category.equals(name)?WHITE:MUTED);t.setTypeface(null,Typeface.BOLD);t.setLetterSpacing(.08f);t.setPadding(dp(14),0,dp(14),0);add(tabrow,t,-2,44);}tabs.addView(tabrow);add(root,tabs,-1,44);divider(root);
        LinearLayout actions=row();actions.setPadding(dp(22),0,dp(18),0);TextView count=text(countLabel(),14,MUTED);weighted(actions,count);LinearLayout playAll=row();playAll.setGravity(Gravity.CENTER);PlayerIcon playAllIcon=compactIcon("play",true,()->{},"Play all");playAllIcon.setClickable(false);add(playAll,playAllIcon,32,48);TextView playAllLabel=text("PLAY ALL",13,WHITE);add(playAll,playAllLabel,-2,48);playAll.setContentDescription("Play all");playAll.setOnClickListener(v->{ArrayList<Song> list=filtered();if(!list.isEmpty())playList(list,0);});add(actions,playAll,125,48);add(root,actions,-1,48);
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));renderList(); showHomeMiniPlayer(); }
    private String countLabel(){return filtered().size()+" TRACKS"+(query.isEmpty()?"":" · "+query);}
    private ArrayList<Song> filtered(){ArrayList<Song> out=new ArrayList<>();for(Song s:songs){if(category.equals("FAVORITES")&&!favorites.contains(""+s.id))continue;String all=(s.title+" "+s.artist+" "+s.album).toLowerCase(Locale.ROOT);if(all.contains(query.toLowerCase(Locale.ROOT)))out.add(s);}return out;}
    private void renderList(){ArrayList<Song> list=filtered();if(list.isEmpty()){TextView empty=text(songs.isEmpty()?"No music found. Grant access and add music to your device.":"No results.",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(32),dp(50),dp(32),0);add(content,empty,-1,130);return;}
        if(category.equals("ALBUMS")||category.equals("ARTISTS")){LinkedHashMap<String,ArrayList<Song>> groups=new LinkedHashMap<>();for(Song s:list){String key=category.equals("ALBUMS")?s.album:s.artist;if(!groups.containsKey(key))groups.put(key,new ArrayList<>());groups.get(key).add(s);}for(Map.Entry<String,ArrayList<Song>> e:groups.entrySet()){Song s=e.getValue().get(0);addItem(e.getKey(),category.equals("ALBUMS")?s.artist:e.getValue().size()+" tracks",s,()->showGroup(e.getKey(),e.getValue()));}}else for(int i=0;i<list.size();i++){final int index=i;Song s=list.get(i);addItem(s.title,s.artist+"  ·  "+s.album,s,()->playList(list,index));}}
    private void addItem(String title,String sub,Song s,Runnable click){LinearLayout r=row();r.setPadding(dp(20),dp(7),dp(14),dp(7));ImageView art=art(s,48);add(r,art,48,48);LinearLayout labels=column();labels.setPadding(dp(14),0,0,0);TextView a=text(title,17,WHITE);a.setSingleLine();a.setEllipsize(android.text.TextUtils.TruncateAt.END);TextView b=text(sub,14,MUTED);b.setSingleLine();b.setEllipsize(android.text.TextUtils.TruncateAt.END);add(labels,a,-1,29);add(labels,b,-1,24);r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));add(r,button("⋮",22,()->songOptions(s)),36,48);r.setOnClickListener(v->click.run());add(content,r,-1,76);}
    private ImageView art(Song s,int size){ImageView v=new ImageView(this);v.setBackground(shape(0xff29282a,2));v.setScaleType(ImageView.ScaleType.CENTER_CROP);try{Uri u=ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),s.albumId);v.setImageURI(u);}catch(Exception ignored){}return v;}
    private void showGroup(String name,ArrayList<Song> list){full=false;base();LinearLayout h=row();add(h,button("‹",32,this::showLibrary),55,55);TextView label=text(name,18,WHITE);label.setSingleLine();weighted(h,label);add(h,button("▶",22,()->playList(list,0)),55,55);add(root,h,-1,56);divider(root);ScrollView sc=new ScrollView(this);content=column();sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));for(int i=0;i<list.size();i++){final int n=i;Song s=list.get(i);addItem(s.title,s.artist,s,()->playList(list,n));}if(current>=0)miniPlayer();}
    private void songOptions(Song s){String fav=favorites.contains(""+s.id)?"Remove from favorites":"Add to favorites";LinearLayout drawer=drawer(s.title);drawerItem(drawer,fav,()->{toggleFavorite(s);showLibrary();});drawerItem(drawer,"Add to queue",()->{queue.add(s);closeDrawer();Toast.makeText(this,"Added to queue",Toast.LENGTH_SHORT).show();});}
    private void toggleFavorite(Song s){String id=""+s.id;if(!favorites.add(id))favorites.remove(id);getPreferences(0).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();}
    private void playList(List<Song> list,int index){queue.clear();queue.addAll(list);play(index);showPlayer();}
    private void initializePlayer(){if(player!=null)return;try{player=new MediaPlayer();player.setOnSeekCompleteListener(p->{seekPosition.displayedPosition(p.getCurrentPosition(),SystemClock.elapsedRealtime(),SystemClock.elapsedRealtime());updatePlaybackProgress();});}catch(RuntimeException e){android.util.Log.e("CrimsonEQ","Player initialization failed",e);if(player!=null){player.release();player=null;}}}
    private void play(int index){
        seekPosition.clear();if(queue.isEmpty())return;current=(index+queue.size())%queue.size();initializePlayer();if(player==null){Toast.makeText(this,"Unable to initialize player",Toast.LENGTH_SHORT).show();return;}
        try{
            playerReady=false;player.reset();player.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this,queue.get(current).uri);
            player.setOnPreparedListener(p->{playerReady=true;p.start();if(full)showPlayer();else if(homeScreen)showLibrary();});
            player.setOnCompletionListener(p->{if(repeat)play(current);else if(current+1<queue.size()||shuffle)next();else{p.seekTo(0);if(full)showPlayer();}});
            player.setOnErrorListener((p,what,extra)->{playerReady=false;Toast.makeText(this,"Could not play this file",Toast.LENGTH_SHORT).show();return true;});player.prepareAsync();
        }catch(Exception e){android.util.Log.e("CrimsonEQ","Playback failed",e);Toast.makeText(this,"Could not play this file",Toast.LENGTH_SHORT).show();}
    }
    private void next(){if(queue.isEmpty())return;play(shuffle?random.nextInt(queue.size()):current+1);if(full)showPlayer();else showLibrary();}
    private void previous(){if(queue.isEmpty())return;play(current-1);if(full)showPlayer();else showLibrary();}
    private void toggle(){try{if(player!=null&&playerReady){if(player.isPlaying())player.pause();else player.start();}if(full)showPlayer();else showLibrary();}catch(IllegalStateException ignored){}}
    private boolean playing(){try{return player!=null&&playerReady&&player.isPlaying();}catch(IllegalStateException e){return false;}}
    private MediaController findExternalPlayback(){
        ComponentName listener=new ComponentName(this,MediaNotificationListener.class);
        try{MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);for(MediaController c:manager.getActiveSessions(listener)){PlaybackState state=c.getPlaybackState();if(!getPackageName().equals(c.getPackageName())&&state!=null&&state.getState()==PlaybackState.STATE_PLAYING)return c;}}catch(SecurityException ignored){}return null;
    }
    private void showHomeMiniPlayer(){MediaController external=findExternalPlayback();if(external!=null&&!playing()){miniController=external;miniPlayer(true);}else if(current>=0&&current<queue.size())miniPlayer(false);}
    private void miniPlayer(){miniPlayer(false);}
    private void miniPlayer(boolean external){
        if(external&&miniController==null)return;if(!external&&(current<0||current>=queue.size()))return;
        divider(root);LinearLayout bar=row();bar.setBackgroundColor(0xff24272a);bar.setPadding(dp(12),0,dp(8),0);
        ImageView artwork;if(external){artwork=new ImageView(this);artwork.setBackground(shape(0xff343639,0));artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);MediaMetadata m=miniController.getMetadata();if(m!=null){android.graphics.Bitmap bitmap=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(bitmap==null)bitmap=m.getBitmap(MediaMetadata.METADATA_KEY_ART);if(bitmap!=null)artwork.setImageBitmap(bitmap);}}else artwork=art(queue.get(current),40);
        add(bar,artwork,40,40);miniTitle=text(external?externalTitle():queue.get(current).title,16,WHITE);miniTitle.setSingleLine();miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);miniTitle.setPadding(dp(12),0,dp(5),0);bar.addView(miniTitle,new LinearLayout.LayoutParams(0,-1,1));
        add(bar,compactIcon("previous",true,()->{if(external){if(miniController!=null)miniController.getTransportControls().skipToPrevious();}else previous();},"Previous track"),40,52);
        miniToggle=compactIcon(external?"pause":(playing()?"pause":"play"),true,()->{if(external){if(miniController!=null){PlaybackState state=miniController.getPlaybackState();if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING)miniController.getTransportControls().pause();else miniController.getTransportControls().play();}}else toggle();},"Play or pause");add(bar,miniToggle,40,52);
        add(bar,compactIcon("next",true,()->{if(external){if(miniController!=null)miniController.getTransportControls().skipToNext();}else next();},"Next track"),40,52);
        bar.setOnClickListener(v->{if(external)showExternal();else showPlayer();});add(root,bar,-1,58);
    }
    private String externalTitle(){if(miniController==null)return "No track";MediaMetadata metadata=miniController.getMetadata();String title=metadata==null?null:metadata.getString(MediaMetadata.METADATA_KEY_TITLE);return title==null?"No track information":title;}
    private static final int PLAYER_BG=0xff191c1f, PLAYER_TEXT=0xffc8bda7, PLAYER_DIM=0xff747475;
    private PlayerIcon icon(String name,boolean active,Runnable click,String label){return new PlayerIcon(this,name,active?PLAYER_TEXT:PLAYER_DIM,click,label);}
    private PlayerIcon compactIcon(String name,boolean active,Runnable click,String label){PlayerIcon result=icon(name,active,click,label);result.setIconScale(0.85f);return result;}
    private void showPlayer(){
        externalScreen=false;releaseExternal();if(current<0||current>=queue.size()){showLibrary();return;}full=true;Song s=queue.get(current);
        playerLayout(art(s,300),s.title,s.artist,"LOCAL AUDIO",playing(),false,s.duration,
            ()->previous(),()->toggle(),()->next(),()->showQueue());
    }
    private void playerLayout(ImageView cover,String title,String artist,String source,boolean isPlaying,boolean external,long length,Runnable back,Runnable togglePlayback,Runnable forward,Runnable queueAction){
        base();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff35393c,PLAYER_BG,0xff101215}));
        LinearLayout header=row();header.setPadding(dp(12),0,dp(12),0);
        add(header,icon("logo",true,this::showLibrary,"Back to library"),48,48);
        TextView format=text(source,10,PLAYER_DIM);format.setGravity(Gravity.CENTER);weighted(header,format);
        add(header,icon("queue",true,queueAction,"Queue"),48,48);add(root,header,-1,48);
        LinearLayout album=column();album.setGravity(Gravity.CENTER_HORIZONTAL);album.setPadding(dp(36),0,dp(36),0);
        int screenHeight=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
        int side=Math.min(getResources().getDisplayMetrics().widthPixels-dp(72),dp(Math.max(150,Math.min(340,screenHeight-388))));
        cover.setBackground(shape(0xff303337,0));cover.setScaleType(ImageView.ScaleType.CENTER_CROP);FrameLayout progress=new FrameLayout(this);progress.addView(cover,new FrameLayout.LayoutParams(side,side));
        seek=new SeekBar(this);seek.setContentDescription("Playback position");seek.setPadding(0,0,0,0);seek.setThumb(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));seek.setThumbOffset(0);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(PLAYER_TEXT));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff555859));
        long actions=external&&externalController!=null&&externalController.getPlaybackState()!=null?externalController.getPlaybackState().getActions():0;
        seek.setEnabled(!external||(actions&PlaybackState.ACTION_SEEK_TO)!=0);seek.setMax((int)Math.min(Integer.MAX_VALUE,Math.max(0,length)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){seekPosition.begin(b.getProgress(),SystemClock.elapsedRealtime());try{if(external){if(externalController!=null)externalController.getTransportControls().seekTo(b.getProgress());else seekPosition.clear();}else if(player!=null&&playerReady)player.seekTo(b.getProgress());else seekPosition.clear();}catch(RuntimeException e){seekPosition.clear();}dragging=false;}public void onProgressChanged(SeekBar b,int p,boolean u){if(u&&elapsed!=null)elapsed.setText(time(p));}});
        FrameLayout.LayoutParams seekBounds=new FrameLayout.LayoutParams(-1,dp(48));seekBounds.topMargin=side-dp(20);progress.addView(seek,seekBounds);LinearLayout times=row();elapsed=text("0:00",13,0xffb2afa8);duration=text(length>0?time(length):"--:--",13,0xffb2afa8);duration.setGravity(Gravity.RIGHT);weighted(times,elapsed);TextView index=text(external?"":(current+1)+" / "+queue.size(),12,0xffaaa7a0);index.setGravity(Gravity.CENTER);weighted(times,index);weighted(times,duration);FrameLayout.LayoutParams timeBounds=new FrameLayout.LayoutParams(-1,dp(28));timeBounds.topMargin=side+dp(8);progress.addView(times,timeBounds);album.addView(progress,new LinearLayout.LayoutParams(side,side+dp(36)));add(root,album,-1,-2);
        View space=new View(this);add(root,space,-1,18);
        LinearLayout tools=row();tools.setPadding(dp(25),0,dp(25),0);
        weighted(tools,icon("expand",!external,()->{if(!external)showLibrary();else openExternalApp();},"Library or source app"));
        weighted(tools,icon("plus",!external,()->{if(!external){queue.add(queue.get(current));Toast.makeText(this,"Added to queue",Toast.LENGTH_SHORT).show();}else unavailable();},"Add to queue"));
        weighted(tools,icon("star",!external&&favorites.contains(""+queue.get(current).id),()->{if(!external){toggleFavorite(queue.get(current));showPlayer();}else unavailable();},"Favorites"));
        weighted(tools,icon("repeat",!external&&repeat,()->{if(!external){repeat=!repeat;showPlayer();}else unavailable();},"Repeat"));
        weighted(tools,icon("shuffle",!external&&shuffle,()->{if(!external){shuffle=!shuffle;showPlayer();}else unavailable();},"Shuffle"));
        weighted(tools,icon("more",true,()->{if(external)openExternalApp();else songOptions(queue.get(current));},"Options"));add(root,tools,-1,48);
        TextView name=text(title==null?"No track information":title,18,PLAYER_TEXT);name.setGravity(Gravity.CENTER);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(20),0,dp(20),0);add(root,name,-1,38);
        TextView by=text(artist==null?"Unknown artist":artist,13,PLAYER_TEXT);by.setTypeface(null,Typeface.BOLD);by.setGravity(Gravity.CENTER);by.setSingleLine();add(root,by,-1,28);
        LinearLayout controls=row();controls.setPadding(dp(48),0,dp(48),0);weighted(controls,icon("previous",true,back,"Previous track"));weighted(controls,icon(isPlaying?"pause":"play",true,togglePlayback,isPlaying?"Pause":"Play"));weighted(controls,icon("next",true,forward,"Next track"));add(root,controls,-1,90);updatePlaybackProgress();
    }
    private void unavailable(){Toast.makeText(this,"This action is managed by the source app",Toast.LENGTH_SHORT).show();}
    private void openExternalApp(){if(externalController==null)return;android.app.PendingIntent intent=externalController.getSessionActivity();try{if(intent!=null)intent.send();else {Intent launch=getPackageManager().getLaunchIntentForPackage(externalController.getPackageName());if(launch!=null)startActivity(launch);else unavailable();}}catch(Exception e){unavailable();}}
    private void updatePlaybackProgress(){
        if(dragging||seek==null)return;
        long now=SystemClock.elapsedRealtime();
        if(full&&player!=null&&playerReady){
            try{seek.setMax(player.getDuration());long pos=seekPosition.displayedPosition(player.getCurrentPosition(),now,0);seek.setProgress((int)Math.max(0,Math.min(pos,seek.getMax())));elapsed.setText(time(pos));duration.setText(time(player.getDuration()));}catch(IllegalStateException ignored){}
        }else if(externalScreen&&externalController!=null){
            PlaybackState state=externalController.getPlaybackState();
            if(state!=null&&state.getPosition()>=0){
                long pos=state.getPosition();if(state.getState()==PlaybackState.STATE_PLAYING)pos+=(long)((now-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
                pos=seekPosition.displayedPosition(pos,now,state.getLastPositionUpdateTime());pos=Math.max(0,Math.min(pos,seek.getMax()));seek.setProgress((int)pos);elapsed.setText(time(pos));
            }
        }
    }
    private final Runnable tick=new Runnable(){public void run(){if(miniController!=null&&miniTitle!=null&&miniToggle!=null){miniTitle.setText(externalTitle());PlaybackState miniState=miniController.getPlaybackState();miniToggle.setIcon(miniState!=null&&miniState.getState()==PlaybackState.STATE_PLAYING?"pause":"play");}updatePlaybackProgress();handler.postDelayed(this,500);}};
    private String time(long ms){long sec=ms/1000;return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void removeDrawerImmediately(){drawerClosing=false;handler.removeCallbacks(drawerTimeout);externalQueueOpen=false;if(drawerOverlay!=null){drawerOverlay.animate().cancel();if(drawerPanel!=null)drawerPanel.animate().cancel();screen.removeView(drawerOverlay);drawerOverlay=null;drawerPanel=null;}}
    private void closeDrawer(){closeDrawer(null);}
    private void closeDrawer(Runnable after){
        handler.removeCallbacks(drawerTimeout);externalQueueOpen=false;
        if(drawerClosing)return;
        if(drawerOverlay==null){if(after!=null)after.run();return;}
        drawerClosing=true;final FrameLayout overlay=drawerOverlay;final LinearLayout panel=drawerPanel;final FrameLayout parent=screen;
        overlay.setClickable(false);if(panel!=null)panel.setEnabled(false);
        overlay.animate().cancel();overlay.animate().alpha(0f).setDuration(190).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{parent.removeView(overlay);if(drawerOverlay==overlay){drawerOverlay=null;drawerPanel=null;drawerClosing=false;}if(after!=null)after.run();}).start();
        if(panel!=null){panel.animate().cancel();panel.animate().translationX(-panel.getWidth()).setDuration(190).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();}
    }
    private LinearLayout drawer(String title){
        removeDrawerImmediately();drawerOverlay=new FrameLayout(this){@Override public boolean dispatchTouchEvent(android.view.MotionEvent event){if(drawerClosing)return true;if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)resetDrawerTimeout();return super.dispatchTouchEvent(event);}};drawerOverlay.setBackgroundColor(0x99000000);drawerOverlay.setOnClickListener(v->closeDrawer());screen.addView(drawerOverlay,new FrameLayout.LayoutParams(-1,-1));resetDrawerTimeout();
        LinearLayout panel=column();panel.setBackgroundColor(0xff202326);panel.setOnClickListener(v->{});FrameLayout.LayoutParams panelParams=new FrameLayout.LayoutParams(Math.min(dp(320),getResources().getDisplayMetrics().widthPixels-dp(48)),-1,Gravity.START);drawerOverlay.addView(panel,panelParams);drawerPanel=panel;panel.setTranslationX(-panelParams.width);drawerOverlay.setAlpha(0f);drawerOverlay.animate().alpha(1f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();panel.animate().translationX(0f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        LinearLayout head=row();head.setPadding(dp(20),0,dp(12),0);add(head,icon("logo",true,this::closeDrawer,"Astell and Kern logo"),42,48);TextView heading=text("Astell&Kern".equals(title)?"":title,19,WHITE);heading.setSingleLine();heading.setEllipsize(android.text.TextUtils.TruncateAt.END);weighted(head,heading);add(head,button("×",27,this::closeDrawer),45,56);add(panel,head,-1,64);divider(panel);
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return list;
    }
    private void drawerItem(LinearLayout list,String label,Runnable action){TextView item=text(label,15,WHITE);item.setPadding(dp(24),0,dp(15),0);item.setOnClickListener(v->closeDrawer(action));add(list,item,-1,54);divider(list);}
    private void showQueue(){if(queue.isEmpty()){Toast.makeText(this,"The queue is empty",Toast.LENGTH_SHORT).show();return;}LinearLayout list=drawer("NOW PLAYING");for(int i=0;i<queue.size();i++){final int index=i;drawerItem(list,(i==current?"▶   ":"")+queue.get(i).title,()->{play(index);showPlayer();});}}
    private void showExternalQueue(){
        if(externalController==null)return;List<MediaSession.QueueItem> entries=externalController.getQueue();LinearLayout list=drawer("NOW PLAYING");externalQueueOpen=true;
        if(entries==null||entries.isEmpty()){
            TextView info=text("The current app does not share its queue with Android. Open the source app to view it.",14,MUTED);info.setPadding(dp(24),dp(20),dp(24),dp(20));add(list,info,-1,-2);drawerItem(list,"Open source app",this::openExternalApp);return;
        }
        PlaybackState state=externalController.getPlaybackState();long currentId=state==null?MediaSession.QueueItem.UNKNOWN_ID:state.getActiveQueueItemId();long actions=state==null?0:state.getActions();boolean canSkip=(actions&PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)!=0;
        for(MediaSession.QueueItem item:entries){MediaDescription desc=item.getDescription();CharSequence title=desc.getTitle(), subtitle=desc.getSubtitle();String label=(item.getQueueId()==currentId?"▶   ":"")+(title==null?"Untitled":title.toString())+(subtitle==null?"":"  ·  "+subtitle);drawerItem(list,label,()->{if(externalController!=null&&canSkip)externalController.getTransportControls().skipToQueueItem(item.getQueueId());else Toast.makeText(this,"The source app does not support selecting tracks from the queue",Toast.LENGTH_SHORT).show();});}
    }
    private void showSearch(){LinearLayout list=drawer("SEARCH");EditText input=new EditText(this);input.setSingleLine();input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("Song, artist, album");input.setText(query);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){resetDrawerTimeout();}public void afterTextChanged(Editable text){}});list.setPadding(dp(20),0,dp(20),0);add(list,input,-1,56);drawerItem(list,"SEARCH",()->{query=input.getText().toString().trim();showLibrary();});drawerItem(list,"CLEAR SEARCH",()->{query="";showLibrary();});}
    private void showMenu(){LinearLayout list=drawer("Astell&Kern");drawerItem(list,"Library",()->{category="SONGS";showLibrary();});drawerItem(list,"Favorites",()->{category="FAVORITES";showLibrary();});drawerItem(list,"Queue",this::showQueue);drawerItem(list,"Playing from other apps",this::showExternal);drawerItem(list,"Equalizer",this::showEqualizer);drawerItem(list,"AMP",this::showAmp);drawerItem(list,"Refresh library",this::requestLibrary);drawerItem(list,"About",()->{LinearLayout about=drawer("ABOUT");TextView info=text("Crimson Player is an independent project inspired by Astell&Kern.",14,MUTED);info.setPadding(dp(24),dp(16),dp(24),0);add(about,info,-1,-2);});}
    private String profileId(){return getPreferences(0).getString("eq_profile_selected","default");}
    private String profileName(String id){return EqPreferences.profileName(getPreferences(0),id);}
    private String bandKey(DeviceEqualizer eq,short band){return EqPreferences.draftKey(getPreferences(0),eq.getCenterFreq(band));}
    private void applyProfile(){if(equalizer!=null)EqPreferences.apply(getPreferences(0),equalizer);}
    private void saveCurrentEqProfile(){
        if(equalizer==null)return;
        try{EqPreferences.save(getPreferences(0),equalizer);showEqualizer();Toast.makeText(this,"Saved to "+profileName(profileId()),Toast.LENGTH_SHORT).show();}
        catch(RuntimeException e){Toast.makeText(this,"Could not save EQ profile",Toast.LENGTH_SHORT).show();}
    }
    private void revertEqProfile(){
        if(equalizer==null)return;
        try{EqPreferences.revert(getPreferences(0),equalizer);showEqualizer();Toast.makeText(this,"Restored last saved profile",Toast.LENGTH_SHORT).show();}
        catch(RuntimeException e){Toast.makeText(this,"Could not restore EQ profile",Toast.LENGTH_SHORT).show();}
    }
    private void showEqProfiles(){LinearLayout list=drawer("EQ PROFILES");Set<String> stored=EqPreferences.profileIds(getPreferences(0));ArrayList<String> ids=new ArrayList<>(stored);Collections.sort(ids,(a,b)->profileName(a).compareToIgnoreCase(profileName(b)));for(String id:ids){LinearLayout item=row();item.setPadding(dp(24),0,dp(18),0);TextView label=text(profileName(id),15,WHITE);weighted(item,label);if(id.equals(profileId())){PlayerIcon check=icon("check",true,()->{},"Selected profile");check.setClickable(false);add(item,check,36,44);}item.setOnClickListener(v->closeDrawer(()->{getPreferences(0).edit().putString("eq_profile_selected",id).apply();try{applyProfile();showEqualizer();}catch(RuntimeException e){Toast.makeText(this,"Unable to load EQ profile",Toast.LENGTH_SHORT).show();}}));add(list,item,-1,54);divider(list);}drawerItem(list,"+  Save as new profile",this::saveEqProfile);}
    private void saveEqProfile(){LinearLayout list=drawer("SAVE EQ PROFILE");list.setPadding(dp(20),0,dp(20),0);EditText name=new EditText(this);name.setSingleLine();name.setTextColor(WHITE);name.setHintTextColor(MUTED);name.setHint("Profile name");name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){resetDrawerTimeout();}public void afterTextChanged(Editable e){}});add(list,name,-1,56);drawerItem(list,"SAVE",()->{String label=name.getText().toString().trim();if(label.isEmpty()){Toast.makeText(this,"Enter a profile name",Toast.LENGTH_SHORT).show();saveEqProfile();return;}if(equalizer==null){Toast.makeText(this,"Device EQ is unavailable",Toast.LENGTH_SHORT).show();return;}try{String id=UUID.randomUUID().toString();Set<String> ids=new HashSet<>(getPreferences(0).getStringSet("eq_profile_ids",Collections.singleton("default")));ids.add(id);android.content.SharedPreferences.Editor edit=getPreferences(0).edit().putStringSet("eq_profile_ids",ids).putString("eq_profile_name_"+id,label).putString("eq_profile_selected",id);for(short i=0;i<equalizer.getNumberOfBands();i++)edit.putInt("eq_profile_"+id+"_gain_"+equalizer.getCenterFreq(i),equalizer.getBandLevel(i));edit.apply();showEqualizer();Toast.makeText(this,"EQ profile saved",Toast.LENGTH_SHORT).show();}catch(RuntimeException e){Toast.makeText(this,"Could not save EQ profile",Toast.LENGTH_SHORT).show();}});}

    private void sectionHeader(String title,Runnable action){
        LinearLayout header=row();header.setPadding(dp(16),dp(8),dp(16),0);
        add(header,icon("back",true,this::showLibrary,"Back to library"),40,48);
        TextView heading=text(title,28,WHITE);heading.setGravity(Gravity.CENTER);heading.setSingleLine();heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heading.setLetterSpacing(-0.025f);weighted(header,heading);
        if(action!=null)add(header,button("↻",32,action),40,48);else add(header,new View(this),40,48);
        add(root,header,-1,56);
    }
    private String eqStatus(){return equalizer.getNumberOfBands()+" BANDS · "+(!equalizer.hasControl()?"CONTROL LOST":equalizer.getEnabled()?"SYSTEM ON":"OFF")+(EqPreferences.isDirty(getPreferences(0),equalizer)?" · UNSAVED":"");}
    private void resetEqualizer(){if(equalizer==null)return;try{android.content.SharedPreferences.Editor editor=getPreferences(0).edit();for(short b=0;b<equalizer.getNumberOfBands();b++){equalizer.setBandLevel(b,(short)0);editor.putInt(bandKey(equalizer,b),0);}editor.apply();showEqualizer();}catch(RuntimeException e){Toast.makeText(this,"Unable to reset device EQ",Toast.LENGTH_SHORT).show();}}
    private void showEqualizer(){
        if(eqService!=null&&equalizer==null&&eqService.getEqualizer()==null){eqService.retryEqualizer();equalizer=eqService.getEqualizer();}
        eqScreen=true;ampScreen=false;externalScreen=false;releaseExternal();full=false;base();root.setBackgroundColor(0xff14191d);
        sectionHeader("Equalizer",this::resetEqualizer);
        if(equalizer==null){LinearLayout message=column();message.setPadding(dp(24),dp(25),dp(24),0);TextView info=text("System EQ is unavailable or still connecting. This device must support EQ on the Android output mix. Direct/bit-perfect output may bypass it.",15,WHITE);add(message,info,-1,-2);add(message,button("RETRY SYSTEM EQ",13,()->{if(eqService!=null){eqService.retryEqualizer();equalizer=eqService.getEqualizer();}showEqualizer();}),-1,48);TextView open=button("OPEN FiiO SYSTEM EQ",13,this::openFiioEq);open.setBackground(shape(0xff334c57,4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(22);message.addView(open,p);add(root,message,-1,-2);return;}
        try{
            LinearLayout selection=row();selection.setPadding(dp(20),0,dp(18),0);LinearLayout profileArea=column();LinearLayout profileRow=row();TextView profile=text(profileName(profileId()),16,WHITE);weighted(profileRow,profile);add(profileRow,icon("down",true,this::showEqProfiles,"Choose EQ profile"),36,42);add(profileArea,profileRow,-1,44);View underline=new View(this);underline.setBackgroundColor(0xff6195a2);add(profileArea,underline,-1,1);profileArea.setOnClickListener(v->showEqProfiles());weighted(selection,profileArea);Switch enabled=new Switch(this);enabled.setContentDescription("Enable equalizer");enabled.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffd4eff4,0xffc5cacf}));enabled.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffff315f,0xff657079}));enabled.setChecked(equalizer.getEnabled());enabled.setEnabled(equalizer.hasControl());enabled.setOnCheckedChangeListener((button,on)->{try{if(eqService==null)throw new IllegalStateException("System EQ is disconnected");eqService.setEqEnabled(on);if(equalizer.getEnabled()!=on){Toast.makeText(this,"Device EQ could not be changed",Toast.LENGTH_SHORT).show();button.setOnCheckedChangeListener(null);button.setChecked(equalizer.getEnabled());return;}showEqualizer();}catch(RuntimeException e){Toast.makeText(this,"System EQ could not be changed",Toast.LENGTH_SHORT).show();showEqualizer();}});add(selection,enabled,60,48);add(root,selection,-1,58);
            LinearLayout tabs=row();tabs.setPadding(dp(4),dp(3),dp(4),dp(3));tabs.setBackground(shape(0xff31424a,5));TextView main=button("Main",14,()->{});main.setTypeface(null,Typeface.BOLD);main.setBackground(shape(0xff568496,4));weighted(tabs,main);TextView advanced=button("Advanced",14,()->Toast.makeText(this,"Q and parametric controls are not available on this EQ",Toast.LENGTH_LONG).show());advanced.setTextColor(0xffa8c2cc);weighted(tabs,advanced);LinearLayout wrap=column();wrap.setPadding(dp(20),0,dp(20),0);add(wrap,tabs,-1,40);add(root,wrap,-1,40);
            short[] range=equalizer.getBandLevelRange();boolean adjustable=equalizer.hasControl();
            TextView note=text(eqStatus(),11,MUTED);note.setGravity(Gravity.CENTER);
            TextView selectedGain=text("GAIN    0.0 dB",15,WHITE);TextView selectedFreq=text("FREQ    — Hz",15,WHITE);
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(false);LinearLayout bands=row();bands.setGravity(Gravity.CENTER);bands.setPadding(dp(18),0,dp(18),0);
            for(short i=0;i<equalizer.getNumberOfBands();i++){final short band=i;int freq=equalizer.getCenterFreq(i)/1000;short gain=equalizer.getBandLevel(i);if(i==0){selectedGain.setText(String.format(Locale.US,"GAIN    %.1f dB",gain/100f));selectedFreq.setText("FREQ    "+freq+" Hz");}EqBandView v=new EqBandView(this,freq,range[0],range[1],gain,adjustable,value->{if(equalizer==null)return value;try{equalizer.setBandLevel(band,value);short actual=equalizer.getBandLevel(band);getPreferences(0).edit().putInt(bandKey(equalizer,band),actual).apply();selectedGain.setText(String.format(Locale.US,"GAIN    %.1f dB",actual/100f));selectedFreq.setText("FREQ    "+freq+" Hz");note.setText(eqStatus());return actual;}catch(RuntimeException e){Toast.makeText(this,"Unable to adjust EQ band",Toast.LENGTH_SHORT).show();return value;}});int width=Math.max(36,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-36)/equalizer.getNumberOfBands());add(bands,v,width,-1);}
            scroll.addView(bands);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));divider(root);
            LinearLayout details=column();details.setPadding(dp(24),0,dp(24),0);add(details,selectedGain,-1,38);divider(details);add(details,selectedFreq,-1,38);add(root,details,-1,77);
            add(root,note,-1,30);
            LinearLayout footer=row();weighted(footer,button("SAVE",12,this::saveCurrentEqProfile));weighted(footer,button("REVERT",12,this::revertEqProfile));weighted(footer,button("SAVE AS",12,this::saveEqProfile));add(root,footer,-1,42);
        }catch(RuntimeException e){Toast.makeText(this,"Device EQ unavailable",Toast.LENGTH_LONG).show();equalizer=null;showEqualizer();}
    }
    private void openFiioEq(){Intent intent=getPackageManager().getLaunchIntentForPackage("com.fiio.fiioeq");try{if(intent!=null)startActivity(intent);else startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}catch(Exception e){Toast.makeText(this,"FiiO EQ is unavailable",Toast.LENGTH_LONG).show();}}
    private void showAmp(){
        ampScreen=true;eqScreen=false;externalScreen=false;releaseExternal();full=false;base();root.setBackgroundColor(0xff14191d);
        sectionHeader("AMP",null);
        LinearLayout content=column();content.setPadding(dp(20),dp(18),dp(20),0);root.addView(content,new LinearLayout.LayoutParams(-1,-1));
        TextView section=text("OUTPUT GAIN",12,0xff9cb3bb);section.setLetterSpacing(.12f);add(content,section,-1,40);
        TextView description=text("Adjust hardware gain in FiiO audio settings.",14,0xffb6c3c8);add(content,description,-1,36);
        for(String level:new String[]{"LOW","MID","HIGH","SUPER"}){
            boolean supported=!level.equals("SUPER");LinearLayout line=row();line.setPadding(dp(18),0,dp(14),0);line.setBackground(shape(supported?0xff26353c:0xff20282c,5));
            TextView label=text(level,16,supported?WHITE:PLAYER_DIM);label.setTypeface(null,Typeface.BOLD);weighted(line,label);
            TextView status=text(supported?"OPEN SETTINGS":"NOT AVAILABLE",11,supported?0xffa8c2cc:PLAYER_DIM);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);add(line,status,120,48);
            if(supported){add(line,icon("next_small",true,this::openAmpSettings,"Open FiiO audio settings"),30,44);line.setOnClickListener(v->openAmpSettings());}
            else {add(line,new View(this),30,44);line.setOnClickListener(v->Toast.makeText(this,"FiiO M11S does not support Super gain",Toast.LENGTH_SHORT).show());}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(62));lp.topMargin=dp(9);content.addView(line,lp);
        }
        TextView note=text("The current gain level is shown in FiiO settings. Super and tube modes are not available on M11S.",12,0xff91a6ae);note.setPadding(dp(4),dp(14),dp(4),0);add(content,note,-1,-2);
    }
    private void openAmpSettings(){try{startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}catch(Exception e){Toast.makeText(this,"Could not open device audio settings",Toast.LENGTH_LONG).show();}}
    private void releaseExternal(){if(externalController!=null){seekPosition.clear();externalController.unregisterCallback(externalCallback);externalController=null;}}
    private void showExternal(){
        externalScreen=true;full=false;releaseExternal();
        ComponentName listener=new ComponentName(this,MediaNotificationListener.class);
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        boolean allowed=false;if(enabled!=null)for(String part:enabled.split(":")){ComponentName c=ComponentName.unflattenFromString(part);if(listener.equals(c)){allowed=true;break;}}
        if(!allowed){base();externalHeader();externalMessage("Allow notification access to view and control other music apps.","OPEN SETTINGS",()->{try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Could not open notification settings",Toast.LENGTH_LONG).show();}});return;}
        try{
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> sessions=manager.getActiveSessions(listener);
            ArrayList<MediaController> others=new ArrayList<>();for(MediaController c:sessions)if(!getPackageName().equals(c.getPackageName()))others.add(c);
            if(others.isEmpty()){base();externalHeader();externalMessage("No active music app found. Play a song in Spotify, Tidal, or another app.","REFRESH",this::showExternal);return;}
            externalController=others.get(0);externalController.registerCallback(externalCallback,handler);renderExternal();
        }catch(SecurityException e){base();externalHeader();externalMessage("Notification access has not been granted.","OPEN SETTINGS",()->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));}
    }
    private void externalHeader(){LinearLayout h=row();add(h,button("‹",32,this::showLibrary),55,55);TextView title=text("OTHER APPS",13,WHITE);title.setLetterSpacing(.08f);weighted(h,title);add(h,button("↻",24,this::showExternal),55,55);add(root,h,-1,56);divider(root);}
    private void externalMessage(String message,String action,Runnable click){LinearLayout box=column();box.setGravity(Gravity.CENTER);box.setPadding(dp(30),0,dp(30),0);TextView t=text(message,15,MUTED);t.setGravity(Gravity.CENTER);add(box,t,-1,120);TextView b=button(action,12,click);b.setTextColor(WHITE);b.setBackground(shape(RED,3));add(box,b,-1,48);root.addView(box,new LinearLayout.LayoutParams(-1,0,1));}
    private void renderExternal(){
        if(!externalScreen||externalController==null)return;
        MediaMetadata m=externalController.getMetadata();PlaybackState state=externalController.getPlaybackState();
        String app=externalController.getPackageName();try{app=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception ignored){}
        ImageView cover=new ImageView(this);if(m!=null){android.graphics.Bitmap bitmap=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(bitmap==null)bitmap=m.getBitmap(MediaMetadata.METADATA_KEY_ART);if(bitmap!=null)cover.setImageBitmap(bitmap);}
        String title=m==null?null:m.getString(MediaMetadata.METADATA_KEY_TITLE), artist=m==null?null:m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        long length=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);boolean isPlaying=state!=null&&state.getState()==PlaybackState.STATE_PLAYING;
        playerLayout(cover,title,artist,app.toUpperCase(Locale.ROOT),isPlaying,true,length,
            ()->{if(externalController!=null)externalController.getTransportControls().skipToPrevious();},
            ()->{if(externalController!=null){PlaybackState st=externalController.getPlaybackState();if(st!=null&&st.getState()==PlaybackState.STATE_PLAYING)externalController.getTransportControls().pause();else externalController.getTransportControls().play();}},
            ()->{if(externalController!=null)externalController.getTransportControls().skipToNext();},this::showExternalQueue);
        if(externalQueueOpen)showExternalQueue();
    }
    @Override protected void onResume(){super.onResume();if(eqService!=null)equalizer=eqService.getEqualizer();if(eqScreen)showEqualizer();else if(externalScreen)showExternal();else if(homeScreen&&resumedOnce)showLibrary();resumedOnce=true;}
    @Override public void onBackPressed(){if(drawerOverlay!=null)closeDrawer();else if(full||externalScreen||ampScreen||eqScreen)showLibrary();else super.onBackPressed();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(SystemEqualizerService.SHOW_EQ.equals(intent.getAction()))showEqualizer();}
    @Override protected void onDestroy(){libraryGeneration++;libraryWorker.shutdownNow();handler.removeCallbacks(drawerTimeout);handler.removeCallbacks(tick);releaseExternal();if(eqBound){unbindService(eqConnection);eqBound=false;}equalizer=null;eqService=null;if(player!=null)player.release();super.onDestroy();}
}
