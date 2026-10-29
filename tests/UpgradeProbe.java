package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import org.json.*;import java.time.*;import java.util.*;

/** Real 0.3.0 -> attendance-only APK migration with both OS alarms and notifications. */
public final class UpgradeProbe extends Instrumentation {
    Bundle args;int checks;
    void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    PendingIntent existing(Context c,String token){Intent i=new Intent(c,AlarmReceiver.class).setAction("ALARM").setData(Uri.parse("qingxing://alarm/"+token));return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);}
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    @Override public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();SharedPreferences p=c.getSharedPreferences("qingxing_v1",0);
        if("seed".equals(args.getString("mode"))){
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==4,"seed requires actual 0.3.0 APK");
            JSONObject cfg=new JSONObject("{\"lat\":39.9077395,\"lon\":116.412635,\"coordType\":\"bd09ll\",\"ssid\":\"OldOffice\",\"radius\":150,\"weakRssi\":-80,\"amStart\":\"00:00\",\"amEnd\":\"23:58\",\"pmStart\":\"23:58\",\"pmEnd\":\"23:59\",\"bssids\":\"aa:bb:cc:dd:ee:ff\",\"calendar\":{\"2026-10-10\":false}}");
            long now=System.currentTimeMillis();JSONObject med=new JSONObject().put("type","med").put("title","旧事项必须删除").put("due",now).put("nextAt",now+300_000).put("stopAt",now+1_800_000);
            cfg.getJSONObject("calendar").put(LocalDate.now().toString(),true);
            JSONObject am=new JSONObject(med.toString()).put("type","attendance").put("title","快到公司了，记得打上班卡");JSONObject pending=new JSONObject().put("med_upgrade-a",med).put("am",am);
            Set<String> tokens=new HashSet<>(Arrays.asList("med_upgrade-a","repeat_med_upgrade-a","expire_med_upgrade-a","repeat_am"));JSONObject schedule=new JSONObject();for(String token:tokens)schedule.put(token,now+300_000);
            JSONArray logs=new JSONArray().put(new JSONObject().put("at",now).put("text","已确认：旧事项必须删除")).put(new JSONObject().put("at",now-86_400_000).put("text","手动确认上班已打卡"));
            p.edit().clear().putString("config",cfg.toString()).putString("expected_config",cfg.toString()).putString("meds",new JSONArray().put(new JSONObject().put("id","upgrade-a").put("name","旧事项必须删除").put("times","08:00,20:00").put("enabled",true)).toString()).putString("pending",pending.toString()).putString("scheduledTimes",schedule.toString()).putStringSet("alarmTokens",tokens).putString("log",logs.toString()).putString("status","已确认：旧事项必须删除").putLong("handled_med_upgrade-a",now).putLong("expected_due",now).putLong("expected_next",now+300_000).putString("day",new JSONObject().put("date",LocalDate.now().toString()).put("pmDone",true).put("amTriggered",true).toString()).putBoolean("enabled",false).putBoolean("baidu_consent_v1",true).putLong("holiday_attempt",now).commit();
            Alerts.channels(c);Alerts.show(c,"med_upgrade-a",med);Alerts.show(c,"am",am);for(String token:tokens)Planner.set(c,token,now+300_000,true);
            check(existing(c,"repeat_med_upgrade-a")!=null,"legacy repeat PendingIntent actually exists");out.putString("stream","PASS upgrade seed on versionCode 4 with actual legacy alarms/notifications");
        }else{
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==Integer.parseInt(args.getString("expectedCode")),"actual new version");
            Store current=new Store(c);JSONObject cfg=current.config();
            check(current.prefs.getBoolean("wifi_only_v1",false),"Wi-Fi migration committed");check(!cfg.has("lat")&&!cfg.has("radius")&&!cfg.has("bssids")&&!cfg.has("weakRssi")&&!cfg.has("address"),"obsolete configuration removed");
            JSONObject previous=Store.json(current.prefs.getString("expected_config","{}"));check(cfg.optString("ssid").equals(previous.optString("ssid")),"company Wi-Fi preserved");for(String key:new String[]{"amStart","amEnd","pmStart","pmEnd","calendar"})check(cfg.optString(key).equals(previous.optString(key)),"preserved "+key);
            check(current.day().optBoolean("pmDone")&&!current.day().has("amTriggered")&&!current.day().has("visited"),"confirmation preserved, old triggers cleared");check(current.pending().length()==0,"old pending reminders cleared");check(current.prefs.getString("events","").contains("打卡"),"attendance history preserved");check(!current.prefs.contains("attendance_ring_am")&&!current.prefs.contains("baidu_consent_v1"),"old sound state and SDK consent retired");check(!current.prefs.contains("meds"),"generic reminders stay retired");
            Planner.onAlarm(c,"repeat_am");new AlarmReceiver().onReceive(c,new Intent("SNOOZE").putExtra("key","am"));check(current.pending().length()==0,"queued legacy intents cannot restore old loops");
            JSONObject oldBackup=new JSONObject().put("schema",3).put("config",previous);MainActivity.restoreBackup(c,oldBackup);check(current.config().optString("ssid").equals(previous.optString("ssid"))&&!current.config().has("lat"),"old backup imports only active fields");check(current.day().optBoolean("pmDone"),"backup import preserves actual confirmed record");
            out.putString("stream","PASS versionCode 4 -> "+args.getString("expectedCode")+" upgrade: "+checks+" checks; active data preserved and retired features removed");
        }finish(Activity.RESULT_OK,out);
    }catch(Throwable t){out.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,out);}}
}
