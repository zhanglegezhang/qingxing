package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.os.*;import android.media.*;import org.json.*;
/** SSID-only foreground lifetime and wake lock, limited to the active window. */
public final class MonitorService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());private PowerManager.WakeLock wake;
    static final long REMINDER_MS=5000;
    static MonitorService current;
    private static Intent pendingReminder;
    private static final Handler lifecycle=new Handler(Looper.getMainLooper());private static Runnable launchRequest;
    Ringtone ringtone;Vibrator vibrator;long reminderStartedAt,reminderStoppedAt;String reminderKey="",reminderNode="";
    private PowerManager.WakeLock reminderWake;
    private final Runnable endReminder=()->stopReminder("已满 5 秒",true);
    static void remind(Context c,String key,JSONObject p){
        // AlarmReceiver reconciles the service after polling; never start a second service lifetime for a cue.
        Intent request=new Intent().putExtra("key",key).putExtra("due",p.optLong("due")).putExtra("node",p.optLong("nextAt")).putExtra("round",p.optString("round"));
        if(current!=null)current.startReminder(request);else pendingReminder=request;
    }
    static void cancelReminder(String key){if(pendingReminder!=null&&key.equals(pendingReminder.getStringExtra("key")))pendingReminder=null;MonitorService service=current;if(service==null)return;if(Looper.myLooper()!=Looper.getMainLooper()){service.handler.post(()->cancelReminder(key));return;}if(key.equals(service.reminderKey))service.stopReminder("提醒已取消");}
    private void startReminder(Intent i){
        Store s=new Store(this);String key=i.getStringExtra("key");JSONObject p=s.pending().optJSONObject(key);
        if(!Alerts.REMIND.equals(Alerts.remindChannel(this))||!Alerts.allowed(this,Alerts.REMIND)||!key.equals(Planner.activeKey(s))||!AttendanceData.validPending(key,p)||p.optLong("due")!=i.getLongExtra("due",0)||p.optLong("nextAt")!=i.getLongExtra("node",0)||!p.optString("round").equals(i.getStringExtra("round"))||p.optLong("stopAt")<=System.currentTimeMillis())return;
        String node=p.optString("round")+"|"+p.optLong("nextAt");if(node.equals(reminderNode))return;
        stopReminder("新的提醒");reminderNode=node;reminderKey=key;
        try{reminderWake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"qingxing:five-second-reminder");reminderWake.acquire(REMINDER_MS+1000);}catch(RuntimeException e){s.debug("声震","保持唤醒失败",e.getClass().getSimpleName(),true);}
        NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel channel=nm.getNotificationChannel(Alerts.REMIND);AudioManager audio=getSystemService(AudioManager.class);
        boolean permitted=nm.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL||channel.canBypassDnd();
        boolean sound=false,vibration=false;
        try{if(permitted&&channel.getImportance()>=NotificationManager.IMPORTANCE_DEFAULT&&channel.getSound()!=null&&audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL&&audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)>0){
            ringtone=RingtoneManager.getRingtone(this,channel.getSound());if(ringtone!=null){ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());ringtone.setLooping(true);}
        }}catch(RuntimeException e){s.debug("声震","响铃失败",e.getClass().getSimpleName(),true);}
        // Prepare the selected tone before timing: preparation must not shorten either cue.
        reminderStartedAt=SystemClock.elapsedRealtime();reminderStoppedAt=0;
        try{if(ringtone!=null){ringtone.play();sound=ringtone.isPlaying();}}catch(RuntimeException e){s.debug("声震","播放失败",e.getClass().getSimpleName(),true);}
        try{if(permitted&&channel.getImportance()>=NotificationManager.IMPORTANCE_DEFAULT&&channel.shouldVibrate()&&audio.getRingerMode()!=AudioManager.RINGER_MODE_SILENT){
            vibrator=getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator()){vibrator.vibrate(VibrationEffect.createOneShot(REMINDER_MS,VibrationEffect.DEFAULT_AMPLITUDE),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());vibration=true;}
        }}catch(RuntimeException e){s.debug("声震","振动失败",e.getClass().getSimpleName(),true);}
        handler.postDelayed(endReminder,Math.max(0,REMINDER_MS-(SystemClock.elapsedRealtime()-reminderStartedAt)));
        s.debug("声震","开始 5 秒提醒","响铃="+sound+"；振动请求="+vibration+"；受通知设置、静音与勿扰控制",!sound&&!vibration);
    }
    private void stopReminder(String reason){stopReminder(reason,false);}
    private void stopReminder(String reason,boolean timedOut){
        handler.removeCallbacks(endReminder);boolean active=!reminderKey.isEmpty();
        try{if(ringtone!=null)ringtone.stop();}catch(RuntimeException ignored){}finally{ringtone=null;}
        // A one-shot effect ends itself after the full 5000 ms; early cancellation still stops immediately.
        try{if(vibrator!=null&&!timedOut)vibrator.cancel();}catch(RuntimeException ignored){}finally{vibrator=null;}
        if(reminderWake!=null&&reminderWake.isHeld())reminderWake.release();reminderWake=null;
        if(active){reminderStoppedAt=SystemClock.elapsedRealtime();new Store(this).debug("声震","已停止",reason+"；持续 "+(reminderStoppedAt-reminderStartedAt)+" 毫秒",false);}reminderKey="";
    }
    public static boolean permitted(Context c,boolean background){return WifiConnection.permitted(c,background);}
    public static String clean(String s){return WifiConnection.clean(s);}
    public static void reconcile(Context c,boolean visible){
        if(launchRequest!=null)lifecycle.removeCallbacks(launchRequest);
        if(Planner.activeKey(new Store(c)).isEmpty()){if(current!=null)c.stopService(new Intent(c,MonitorService.class));Planner.cleanup(c);return;}
        if(current!=null){current.consumeReminder();return;}
        Context app=c.getApplicationContext();launchRequest=()->{
            if(current!=null||Planner.activeKey(new Store(app)).isEmpty())return;
            try{app.startForegroundService(new Intent(app,MonitorService.class));}
            catch(RuntimeException e){new Store(app).debugLimited("监测","启动失败",e.getClass().getSimpleName()+"；请打开应用恢复",true);}
        };lifecycle.post(launchRequest);
    }
    @Override public void onCreate(){super.onCreate();current=this;Alerts.channels(this);
        if(Build.VERSION.SDK_INT>=34)startForeground(Alerts.MONITOR_ID,Alerts.monitoring(this,"准备监控公司 Wi-Fi",false),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(Alerts.MONITOR_ID,Alerts.monitoring(this,"准备监控公司 Wi-Fi",false));
        handler.post(tick);new Store(this).debug("监测","服务已启动","每 10 秒读取公司 Wi-Fi；不获取定位或步数",false);
    }
    private void consumeReminder(){if(!Alerts.REMIND.equals(Alerts.remindChannel(this)))stopReminder("已切换安静提醒");Intent request=pendingReminder;pendingReminder=null;if(request!=null)startReminder(request);}
    @Override public int onStartCommand(Intent i,int f,int id){consumeReminder();return START_STICKY;}
    private final Runnable tick=new Runnable(){public void run(){
        Store s=new Store(MonitorService.this);String key=Planner.activeKey(s);
        if(key.isEmpty()){Planner.cleanup(MonitorService.this);stopSelf();return;}
        try{
            if(wake==null||!wake.isHeld()){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"qingxing:wifi-monitor");wake.acquire(Math.min(6*60*60_000L,Math.max(1000,s.windowEnd(key)-System.currentTimeMillis()+1000)));}
            Planner.checkNow(MonitorService.this);
            if(Planner.activeKey(s).isEmpty()){stopSelf();return;}
            getSystemService(NotificationManager.class).notify(Alerts.MONITOR_ID,Alerts.monitoring(MonitorService.this,s.prefs.getString("status","监控中")));
            String channel=Alerts.monitorChannel(MonitorService.this);s.debug("监控通知","检查后已提交",Alerts.settingsSummary(MonitorService.this,channel)+"；声震请求="+Alerts.loud(channel),!Alerts.mismatch(MonitorService.this,channel).isEmpty());
        }catch(RuntimeException e){s.debugLimited("监测","检查失败",e.getClass().getSimpleName(),true);}
        handler.postDelayed(this,Rules.WIFI_POLL);
    }};
    @Override public void onDestroy(){stopReminder("监控服务停止");if(current==this)current=null;handler.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);new Store(this).debug("监测","服务已停止","Wi-Fi 轮询已停止",false);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
