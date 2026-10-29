package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;

/** Actual preceding APK upgrade, including a custom radius outside the three presets. */
public final class LayoutUpgradeProbe extends Instrumentation {
    Bundle args;int checks;
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    void main(Runnable r){Throwable[] failure={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    @Override public void onStart(){Bundle result=new Bundle();try{Context c=getTargetContext();Store s=new Store(c);
        if("seed".equals(args.getString("mode"))){
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==Integer.parseInt(args.getString("seedCode")),"seed actual previous APK");
            s.prefs.edit().clear().putBoolean("attendance_only_v1",true).putBoolean("attendance_compact_v1",true).putBoolean("baidu_consent_v1",true).putLong("holiday_attempt",System.currentTimeMillis()).commit();
            boolean unifiedSeed=Integer.parseInt(args.getString("seedCode"))>=16;
            if(unifiedSeed)s=new Store(c); // Modern APKs already use events; initialize their actual migration markers before writing history.
            s.prefs.edit().putBoolean("monitorLoud",true).putBoolean("remindLoud",false).commit();
            FlowTests.fixture(s);JSONObject cfg=s.config();Store.put(cfg,"radius",175);Store.put(cfg,"coordType","bd09ll");Store.put(cfg,"address","升级测试办公楼");Store.put(cfg,"addressLat",cfg.optDouble("lat",39.9));Store.put(cfg,"addressLon",cfg.optDouble("lon",116.4));Store.put(cfg,"addressType","bd09ll");s.saveConfig(cfg);
            JSONObject day=s.day();Store.put(day,"pmDone",true);if(!unifiedSeed)Store.put(day,"amTriggered",true);s.day(day);s.log("手动确认下班已打卡");
            Planner.create(c,"am","attendance","快到公司了，记得打上班卡",System.currentTimeMillis(),s.windowEnd("am"));
            s.prefs.edit().putString("expected_config",s.prefs.getString("config","")).putString("expected_day",s.prefs.getString("day","")).putString("expected_pending",s.prefs.getString("pending","")).putBoolean("enabled",false).commit();result.putString("stream","PASS layout upgrade seed on versionCode "+args.getString("seedCode"));
        }else{
            check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==Integer.parseInt(args.getString("expectedCode")),"actual new version");
            Store current=new Store(c);JSONObject cfg=current.config();
            check(current.prefs.getBoolean("wifi_only_v1",false),"Wi-Fi migration committed");check(!cfg.has("lat")&&!cfg.has("radius")&&!cfg.has("bssids")&&!cfg.has("weakRssi")&&!cfg.has("address"),"obsolete configuration removed");
            check(Alerts.monitorChannel(c).equals(Alerts.MONITOR)&&Alerts.remindChannel(c).equals(Alerts.REMIND),"upgrade ignores legacy notification profiles and uses fixed defaults");
            JSONObject previous=Store.json(current.prefs.getString("expected_config","{}"));check(cfg.optString("ssid").equals(previous.optString("ssid")),"company Wi-Fi preserved");for(String key:new String[]{"amStart","amEnd","pmStart","pmEnd","calendar"})check(cfg.optString(key).equals(previous.optString(key)),"preserved "+key);
            check(current.day().optBoolean("pmDone")&&!current.day().has("amTriggered")&&!current.day().has("visited"),"confirmation preserved, old triggers cleared");check(current.pending().length()==0,"old pending reminders cleared");check(current.prefs.getString("events","").contains("打卡"),"attendance history preserved");check(!current.prefs.contains("attendance_ring_am")&&!current.prefs.contains("baidu_consent_v1"),"old sound state and SDK consent retired");check(!current.prefs.contains("meds"),"generic reminders stay retired");check(!current.enabled(),"upgrade preserves disabled total switch");check(current.prefs.getBoolean("unified_logs_v1",false)&&!current.prefs.contains("log")&&!current.prefs.contains("debugLog"),"old histories migrated to one stream");check(!Authorizations.autostart(c)&&Planner.activeKey(current).isEmpty(),"upgrade waits for explicit autostart confirmation");
            Planner.onAlarm(c,"repeat_am");new AlarmReceiver().onReceive(c,new Intent("SNOOZE").putExtra("key","am"));check(current.pending().length()==0,"queued legacy intents cannot restore old loops");
            JSONObject oldBackup=new JSONObject().put("schema",3).put("config",previous);MainActivity.restoreBackup(c,oldBackup);check(current.config().optString("ssid").equals(previous.optString("ssid"))&&!current.config().has("lat"),"old backup imports only active fields");check(current.day().optBoolean("pmDone"),"backup import preserves actual confirmed record");
            result.putString("stream","PASS versionCode "+args.getString("seedCode")+" -> "+args.getString("expectedCode")+" layout upgrade: "+checks+" checks; Wi-Fi/windows/calendar/confirmed history preserved; obsolete timers/config removed");
        }finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
