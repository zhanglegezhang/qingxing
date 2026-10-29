package cn.qingxing.reminder;

import android.app.*;
import android.content.*;
import org.json.*;
import java.util.*;

/** Destructive migration explicitly requested by the user; runs before any planner or UI reads. */
final class AttendanceData {
    static final String MARKER="attendance_only_v1";
    static boolean attendanceKey(String key){return "am".equals(key)||"pm".equals(key);}
    static boolean validPending(String key,JSONObject p){return p!=null&&attendanceKey(key)&&"attendance".equals(p.optString("type"));}
    static boolean validToken(String token){return token!=null&&(token.equals("boundary")||token.matches("(?:repeat|expire)_(?:am|pm)"));}
    static synchronized void retireDiagnostics(Context c,SharedPreferences p){
        if(p.getBoolean("attendance_compact_v1",false))return;
        JSONObject pending=Store.json(p.getString("pending","{}")),scheduled=Store.json(p.getString("scheduledTimes","{}"));pending.remove("test");
        Set<String> tokens=new HashSet<>(p.getStringSet("alarmTokens",Collections.emptySet()));
        for(String token:new String[]{"test","repeat_test","expire_test"}){PendingIntent old=Planner.alarm(c,token);c.getSystemService(AlarmManager.class).cancel(old);old.cancel();tokens.remove(token);scheduled.remove(token);}
        c.getSystemService(NotificationManager.class).cancel(Alerts.id("test"));
        p.edit().putString("pending",pending.toString()).putString("scheduledTimes",scheduled.toString()).putStringSet("alarmTokens",tokens).putBoolean("attendance_compact_v1",true).commit();
    }
    static boolean oldAttendanceToken(String token){return token.equals("boundary")||token.matches("(?:repeat|expire)_(?:am|pm)");}
    static boolean attendanceLog(String text){
        if(text.equals("手动确认上班已打卡")||text.equals("手动确认下班已打卡"))return true;
        for(String prefix:new String[]{"触发提醒：","再次提醒：","已确认："})for(String title:new String[]{"快到公司了，记得打上班卡","检测到正在离开，记得打下班卡"})if(text.equals(prefix+title))return true;
        return text.equals("提醒到期，保留未确认记录：am")||text.equals("提醒到期，保留未确认记录：pm");
    }
    static synchronized void migrate(Context c,SharedPreferences p){
        if(p.getBoolean(MARKER,false))return;
        JSONObject pending=Store.json(p.getString("pending","{}")),scheduled=Store.json(p.getString("scheduledTimes","{}"));
        JSONArray items=Store.array(p.getString("meds","[]"));Set<String> keys=new HashSet<>();
        for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null)keys.add("med_"+item.optString("id"));}
        JSONObject keepPending=new JSONObject();Iterator<String> it=pending.keys();while(it.hasNext()){
            String key=it.next();JSONObject value=pending.optJSONObject(key);
            if(attendanceKey(key)&&validPending(key,value))Store.put(keepPending,key,value);else keys.add(key);
        }
        Set<String> tokens=new HashSet<>(p.getStringSet("alarmTokens",Collections.emptySet()));it=scheduled.keys();while(it.hasNext())tokens.add(it.next());
        for(String key:keys){tokens.add(key);tokens.add("repeat_"+key);tokens.add("expire_"+key);}
        Set<String> keepTokens=new HashSet<>();JSONObject keepScheduled=new JSONObject();
        AlarmManager alarms=c.getSystemService(AlarmManager.class);NotificationManager notifications=c.getSystemService(NotificationManager.class);
        for(String token:tokens){if(oldAttendanceToken(token)){keepTokens.add(token);if(scheduled.has(token))Store.put(keepScheduled,token,scheduled.optLong(token));}else{
            PendingIntent intent=Planner.alarm(c,token);alarms.cancel(intent);intent.cancel();
        }}
        for(String key:keys)notifications.cancel(Alerts.id(key));
        // Also remove orphaned posted notifications whose task data was already lost.
        for(android.service.notification.StatusBarNotification n:notifications.getActiveNotifications())if(Alerts.REMIND.equals(n.getNotification().getChannelId())&&!((keepPending.has("am")&&n.getId()==Alerts.id("am"))||(keepPending.has("pm")&&n.getId()==Alerts.id("pm"))))notifications.cancel(n.getId());
        JSONArray oldLogs=Store.array(p.getString("log","[]")),logs=new JSONArray();
        for(int i=0;i<oldLogs.length();i++){JSONObject line=oldLogs.optJSONObject(i);if(line!=null&&attendanceLog(line.optString("text"))){Store.put(line,"kind","attendance");logs.put(line);}}
        SharedPreferences.Editor edit=p.edit().remove("meds").remove("status").remove("statusAt").putString("pending",keepPending.toString()).putString("scheduledTimes",keepScheduled.toString()).putStringSet("alarmTokens",keepTokens).putString("log",logs.toString());
        for(String key:p.getAll().keySet())if(key.startsWith("handled_med_"))edit.remove(key);
        // Rewriting this preference file also replaces its previous-generation .bak, without touching exported files.
        if(!edit.commit())throw new IllegalStateException("旧事项清理未保存，请重新打开应用");
        if(!p.edit().putBoolean(MARKER,true).commit())throw new IllegalStateException("升级标记未保存，请重新打开应用");
    }
    static synchronized void wifiOnly(Context c,SharedPreferences prefs){
        if(prefs.getBoolean("wifi_only_v1",false))return;
        android.app.AlarmManager alarms=c.getSystemService(android.app.AlarmManager.class);
        for(String token:new HashSet<>(prefs.getStringSet("alarmTokens",Collections.emptySet()))){android.app.PendingIntent pi=Planner.alarm(c,token);alarms.cancel(pi);pi.cancel();}
        for(String w:new String[]{"am","pm"}){JSONObject ring=Store.json(prefs.getString("attendance_ring_"+w,"{}"));String token=ring.optString("token");if(!token.isEmpty()){
            Intent intent=new Intent(c,AlarmReceiver.class).setAction("QUIET").setData(android.net.Uri.parse("qingxing://quiet/"+w+"/"+token));
            android.app.PendingIntent pi=android.app.PendingIntent.getBroadcast(c,0,intent,android.app.PendingIntent.FLAG_NO_CREATE|android.app.PendingIntent.FLAG_IMMUTABLE);if(pi!=null){alarms.cancel(pi);pi.cancel();}
        }}
        c.getSystemService(android.app.NotificationManager.class).cancelAll();
        JSONObject day=Store.json(prefs.getString("day","{}"));for(String key:new String[]{"amTriggered","pmTriggered","visited","wifiSeen"})day.remove(key);
        SharedPreferences.Editor edit=prefs.edit().putString("config",Store.cleanConfig(Store.json(prefs.getString("config","{}"))).toString()).putString("day",day.toString()).putString("pending","{}").putString("wifiRound","{}").putString("scheduledTimes","{}").putStringSet("alarmTokens",Collections.emptySet()).remove("status").remove("statusAt");
        for(String key:prefs.getAll().keySet())if(key.startsWith("attendance_ring_")||key.startsWith("locationRecovery")||key.startsWith("baidu_"))edit.remove(key);
        edit.putBoolean("wifi_only_v1",true);if(!edit.commit())throw new IllegalStateException("Wi-Fi 监测升级未保存，请重试");
    }
}
