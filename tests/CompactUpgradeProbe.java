package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import org.json.*;import java.time.*;

/** Actual versionCode 5 -> 8 upgrade. No synthetic migration-only substitute. */
public final class CompactUpgradeProbe extends Instrumentation {
    Bundle args;int checks;
    void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    boolean retainedHistory(Store s)throws Exception{JSONArray expected=new JSONArray(s.prefs.getString("expected_log","[]")),actual=new JSONArray(s.prefs.getString("log","[]"));for(int i=0;i<expected.length();i++){JSONObject e=expected.getJSONObject(i);boolean found=false;for(int j=0;j<actual.length();j++){JSONObject a=actual.getJSONObject(j);if(a.optLong("at")==e.optLong("at")&&a.optString("text").equals(e.optString("text")))found=true;}if(!found)return false;}return true;}
    PendingIntent existing(Context c,String token){return PendingIntent.getBroadcast(c,0,new Intent(c,AlarmReceiver.class).setAction("ALARM").setData(Uri.parse("qingxing://alarm/"+token)),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);}
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    @Override public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();
        if("seed".equals(args.getString("mode"))){
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==5,"seed on actual 0.4.0");
            Store s=new Store(c);s.prefs.edit().clear().putBoolean("attendance_only_v1",true).putLong("holiday_attempt",System.currentTimeMillis()).commit();
            FlowTests.fixture(s);JSONObject cfg=s.config();Store.put(cfg,"coordType","bd09ll");Store.put(cfg,"weakRssi",-81);Store.put(cfg,"bssids","aa:bb:cc:dd:ee:ff");s.saveConfig(cfg);
            JSONObject day=s.day();Store.put(day,"amTriggered",true);Store.put(day,"pmDone",true);s.day(day);
            Alerts.channels(c);long now=System.currentTimeMillis();Planner.create(c,"am","attendance","快到公司了，记得打上班卡",now,s.windowEnd("am"));Planner.create(c,"test","test","轻醒 · 打卡提醒测试",now,now+600_000);
            s.log("手动确认下班已打卡");s.prefs.edit().putString("expected_config",s.prefs.getString("config","")).putString("expected_day",s.prefs.getString("day","")).putString("expected_log",s.prefs.getString("log","")).putBoolean("baidu_consent_v1",true).commit();
            check(s.pending().has("test")&&existing(c,"repeat_test")!=null,"old diagnostic has a real scheduled alarm");out.putString("stream","PASS compact upgrade seed on versionCode 5");
        }else{
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==Integer.parseInt(args.getString("expectedCode")),"actual new version");
            Store current=new Store(c);JSONObject cfg=current.config();
            check(current.prefs.getBoolean("wifi_only_v1",false),"Wi-Fi migration committed");check(!cfg.has("lat")&&!cfg.has("radius")&&!cfg.has("bssids")&&!cfg.has("weakRssi")&&!cfg.has("address"),"obsolete configuration removed");
            JSONObject previous=Store.json(current.prefs.getString("expected_config","{}"));check(cfg.optString("ssid").equals(previous.optString("ssid")),"company Wi-Fi preserved");for(String key:new String[]{"amStart","amEnd","pmStart","pmEnd","calendar"})check(cfg.optString(key).equals(previous.optString(key)),"preserved "+key);
            check(current.day().optBoolean("pmDone")&&!current.day().has("amTriggered")&&!current.day().has("visited"),"confirmation preserved, old triggers cleared");check(current.pending().length()==0,"old pending reminders cleared");check(current.prefs.getString("events","").contains("打卡"),"attendance history preserved");check(!current.prefs.contains("attendance_ring_am")&&!current.prefs.contains("baidu_consent_v1"),"old sound state and SDK consent retired");check(!current.prefs.contains("meds"),"generic reminders stay retired");
            Planner.onAlarm(c,"repeat_am");new AlarmReceiver().onReceive(c,new Intent("SNOOZE").putExtra("key","am"));check(current.pending().length()==0,"queued legacy intents cannot restore old loops");
            JSONObject oldBackup=new JSONObject().put("schema",3).put("config",previous);MainActivity.restoreBackup(c,oldBackup);check(current.config().optString("ssid").equals(previous.optString("ssid"))&&!current.config().has("lat"),"old backup imports only active fields");check(current.day().optBoolean("pmDone"),"backup import preserves actual confirmed record");
            out.putString("stream","PASS versionCode 5 -> "+args.getString("expectedCode")+" compact upgrade: "+checks+" checks; active data preserved and retired features removed");
        }finish(Activity.RESULT_OK,out);
    }catch(Throwable t){out.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,out);}}
}
