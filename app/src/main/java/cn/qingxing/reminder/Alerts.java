package cn.qingxing.reminder;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import org.json.*;

public final class Alerts {
    public static final String MONITOR="monitor_quiet_v2", MONITOR_LOUD="monitor_loud_v2", REMIND_QUIET="reminder_quiet_v2", REMIND="reminder_loud_v2", HEALTH="health_v1";
    public static final int MONITOR_ID=1;
    public static void channels(Context c) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        create(nm,MONITOR,"安静监控",false,true);create(nm,MONITOR_LOUD,"吵闹监控",true,true);
        create(nm,REMIND_QUIET,"安静提醒",false,false);create(nm,REMIND,"吵闹提醒",true,false);
        NotificationChannel health=new NotificationChannel(HEALTH,"运行状态",NotificationManager.IMPORTANCE_DEFAULT);
        health.setLockscreenVisibility(Notification.VISIBILITY_SECRET);health.setSound(null,null);health.enableVibration(false);
        nm.createNotificationChannel(health);
    }
    private static void create(NotificationManager nm,String id,String name,boolean loud,boolean monitor){
        String description=monitor?"后台查看 Wi-Fi，轻醒固定使用安静监控":"打卡提醒固定使用响铃和振动，每次持续 5 秒；实际效果受手机通知设置影响";
        NotificationChannel existing=nm.getNotificationChannel(id);if(existing!=null){if(!description.equals(existing.getDescription())){existing.setDescription(description);nm.createNotificationChannel(existing);}return;}
        NotificationChannel channel=new NotificationChannel(id,name,loud?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        channel.setLockscreenVisibility(monitor?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PUBLIC);channel.setShowBadge(!monitor);
        channel.enableVibration(loud);if(loud)channel.setVibrationPattern(new long[]{0,400});
        channel.setSound(loud?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):null,loud?new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build():null);
        nm.createNotificationChannel(channel);
    }
    /** Monitoring is always quiet; attendance reminders always request sound and vibration. Legacy choices are ignored. */
    public static String monitorChannel(Context c){return MONITOR;}
    public static String remindChannel(Context c){return REMIND;}
    static boolean loud(String id){return MONITOR_LOUD.equals(id)||REMIND.equals(id);}
    static boolean allowed(Context c,String id){NotificationManager nm=c.getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled())return false;NotificationChannel channel=nm.getNotificationChannel(id);if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;if(channel.getGroup()!=null){NotificationChannelGroup group=nm.getNotificationChannelGroup(channel.getGroup());if(group!=null&&group.isBlocked())return false;}return true;}
    static String mismatch(Context c,String id){
        NotificationChannel ch=c.getSystemService(NotificationManager.class).getNotificationChannel(id);if(ch==null)return "通知类别不存在";
        boolean sound=ch.getSound()!=null;if(sound&&ch.getSound().equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)))sound=RingtoneManager.getActualDefaultRingtoneUri(c,RingtoneManager.TYPE_NOTIFICATION)!=null;
        return mismatch(ch,allowed(c,id),loud(id),sound);
    }
    static String mismatch(NotificationChannel ch,boolean allowed,boolean loud,boolean sound){
        java.util.List<String> reasons=new java.util.ArrayList<>();if(!allowed)reasons.add("通知被禁用");
        if(loud){if(!sound)reasons.add("声音未开启");if(!ch.shouldVibrate())reasons.add("振动未开启");if(ch.getImportance()<NotificationManager.IMPORTANCE_DEFAULT)reasons.add("通知重要程度过低，无法声震");}
        else {if(sound)reasons.add("声音应关闭");if(ch.shouldVibrate())reasons.add("振动应关闭");}
        return String.join("；",reasons);
    }
    static String settingsSummary(Context c,String id){
        NotificationChannel ch=c.getSystemService(NotificationManager.class).getNotificationChannel(id);
        android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);
        return id+"；显示="+allowed(c,id)+"；声音="+(ch!=null&&ch.getSound()!=null)+"；振动="+(ch!=null&&ch.shouldVibrate())+"；重要程度="+(ch==null?0:ch.getImportance())+"；响铃模式="+audio.getRingerMode()+"；通知音量="+audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)+"；勿扰="+c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()+"；不匹配="+mismatch(c,id);
    }
    public static PendingIntent open(Context c) {return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent action(Context c,String action,String key,long due) {
        Intent i=new Intent(c,AlarmReceiver.class).setAction(action).setData(android.net.Uri.parse("qingxing://action/"+action+"/"+key+"/"+due)).putExtra("key",key).putExtra("due",due);
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static Notification monitoring(Context c,String text) {
        return monitoring(c,text,true);
    }
    static Notification monitoring(Context c,String text,boolean check) {
        String channel=check?monitorChannel(c):MONITOR;
        return new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notify).setContentTitle("轻醒 · 打卡监测中").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(!loud(channel)).setShowWhen(false).setContentIntent(open(c))
            .addAction(new Notification.Action.Builder(null,"暂停监测",action(c,"PAUSE","monitor",0)).build()).build();
    }
    public static int id(String key) {return 1000+(key.hashCode()&0x3fffffff);}
    public static void show(Context c,String key,JSONObject p) {
        show(c,key,p,false);
    }
    private static void show(Context c,String key,JSONObject p,boolean silentUpdate) {
        if(!AttendanceData.validPending(key,p))return;
        boolean posted=post(c,key,notification(c,key,p,silentUpdate,""));
        if(posted&&!silentUpdate&&REMIND.equals(remindChannel(c)))MonitorService.remind(c,key,p);
    }
    static Notification notification(Context c,String key,JSONObject p,boolean silentUpdate,String ringToken) {
        String label=p.optString("type").equals("attendance")?"已打卡":"结束测试";
        String channel=remindChannel(c);
        Notification n=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notify).setContentTitle(new Store(c).displayTitle(key,p)).setOnlyAlertOnce(silentUpdate)
            .setContentText("每分钟查看 Wi-Fi · 响铃和振动 5 秒").setStyle(new Notification.BigTextStyle().bigText("记得去企业微信打卡。首页打卡按钮会打开企微，并停止对应提醒；已完成打卡也可点击下方“已打卡”。本次响铃和振动持续 5 秒。"))
            .setGroup("qingxing.attendance."+key).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_ALARM).setAutoCancel(false).setContentIntent(open(c))
            .addAction(new Notification.Action.Builder(null,label,action(c,"DONE",key,p.optLong("due"))).build())
            .build();
        n.extras.putString("qingxing.ringToken",ringToken);
        return n;
    }
    static boolean post(Context c,String key,Notification n) {
        Store s=new Store(c);String channel=n.getChannelId();if(!allowed(c,channel)){s.debug("提醒","提交被阻止",settingsSummary(c,channel),true);return false;}
        try{c.getSystemService(NotificationManager.class).notify(id(key),n);s.debug("提醒","已向系统提交通知",settingsSummary(c,channel)+"；仅表示提交请求，声震由后台服务按 5 秒控制并受系统设置约束",!mismatch(c,channel).isEmpty());return true;}catch(SecurityException e){s.debug("提醒","通知提交失败",e.getClass().getSimpleName(),true);return false;}
    }
    public static void refreshExisting(Context c) {
        JSONObject pending=new Store(c).pending();NotificationManager manager=c.getSystemService(NotificationManager.class);
        java.util.Set<Integer> active=new java.util.HashSet<>();for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications())active.add(n.getId());
        java.util.Iterator<String> keys=pending.keys();while(keys.hasNext()){String key=keys.next();JSONObject p=pending.optJSONObject(key);if(AttendanceData.validPending(key,p)&&active.contains(id(key)))show(c,key,p,true);}
    }
    public static void cancel(Context c,String key) {MonitorService.cancelReminder(key);c.getSystemService(NotificationManager.class).cancel(id(key));}
    public static void issue(Context c,String msg) {
        Store s=new Store(c);s.status(msg);s.log(msg);
        try{c.getSystemService(NotificationManager.class).notify(2,new Notification.Builder(c,HEALTH).setSmallIcon(R.drawable.ic_notify).setContentTitle("轻醒需要你的处理").setContentText(msg).setStyle(new Notification.BigTextStyle().bigText(msg)).setVisibility(Notification.VISIBILITY_SECRET).setContentIntent(open(c)).build());}catch(SecurityException ignored){}
    }
}
