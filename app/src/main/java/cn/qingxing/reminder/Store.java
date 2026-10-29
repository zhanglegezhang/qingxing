package cn.qingxing.reminder;

import android.content.*;
import org.json.*;
import java.time.*;
import java.util.*;

public final class Store {
    final SharedPreferences prefs;
    private final Context context;
    Context context(){return context;}
    public Store(Context c) { context=c.getApplicationContext();prefs=context.getSharedPreferences("qingxing_v1",Context.MODE_PRIVATE);boolean fresh=prefs.getAll().isEmpty();if(fresh)prefs.edit().putBoolean("enabled",true).putBoolean("firstGuidePending",true).commit();AttendanceData.migrate(context,prefs);AttendanceData.retireDiagnostics(context,prefs);AttendanceData.wifiOnly(context,prefs);mergeLogs(); }
    public JSONObject config() {
        JSONObject c=json(prefs.getString("config","{}"));
        defaults(c,"amStart","08:30");defaults(c,"amEnd","10:00");defaults(c,"pmStart","17:30");defaults(c,"pmEnd","19:00");
        defaults(c,"ssid","");defaults(c,"calendar",new JSONObject());
        return c;
    }
    private void defaults(JSONObject j,String k,Object v) {if(!j.has(k)) put(j,k,v);}
    public void saveConfig(JSONObject j) {prefs.edit().putString("config",cleanConfig(j).toString()).commit();}
    static JSONObject cleanConfig(JSONObject source){JSONObject clean=new JSONObject();for(String k:new String[]{"ssid","amStart","amEnd","pmStart","pmEnd","calendar"})if(source.has(k))try{put(clean,k,source.get(k));}catch(JSONException e){throw new IllegalArgumentException(e);}return clean;}
    public JSONObject round(){return json(prefs.getString("wifiRound","{}"));}
    public void round(JSONObject r){prefs.edit().putString("wifiRound",r.toString()).commit();}
    public boolean enabled() {return prefs.getBoolean("enabled",false);}
    public void enabled(boolean e) {prefs.edit().putBoolean("enabled",e).commit();}
    public String displayTitle(String key,JSONObject pending) {
        return pending.optString("title");
    }
    public JSONObject pending() {return json(prefs.getString("pending","{}"));}
    public void pending(JSONObject p) {prefs.edit().putString("pending",p.toString()).commit();}
    public String today() {return LocalDate.now().toString();}
    public JSONObject day() {
        JSONObject j=json(prefs.getString("day","{}"));
        if(!today().equals(j.optString("date"))) { j=new JSONObject();put(j,"date",today()); }
        return j;
    }
    public void day(JSONObject j) {prefs.edit().putString("day",j.toString()).commit();}
    public void status(String value) {prefs.edit().putString("status",value).putLong("statusAt",System.currentTimeMillis()).apply();}
    private void mergeLogs(){synchronized(Store.class){
        if(prefs.getBoolean("unified_logs_v1",false))return;
        List<JSONObject> all=new ArrayList<>();for(String key:new String[]{"log","debugLog"}){JSONArray old=array(prefs.getString(key,"[]"));for(int i=0;i<old.length();i++){JSONObject line=old.optJSONObject(i);if(line!=null){if(!line.has("module")){put(line,"module","动态");put(line,"event",line.optString("text"));put(line,"detail","");}all.add(line);}}}
        Collections.sort(all,(a,b)->Long.compare(b.optLong("at"),a.optLong("at")));JSONArray merged=new JSONArray();for(int i=0;i<Math.min(100,all.size());i++)merged.put(all.get(i));
        prefs.edit().putString("events",merged.toString()).remove("log").remove("debugLog").putBoolean("unified_logs_v1",true).commit();
    }}
    public void log(String msg) {debug("动态",msg,"",false);}
    /** One chronological, bounded stream. Identical events remain separate entries. */
    public void debug(String module,String event,String detail,boolean error){
        synchronized(Store.class){
            JSONArray old=array(prefs.getString("events","[]")),lines=new JSONArray();JSONObject line=new JSONObject();
            put(line,"at",System.currentTimeMillis());put(line,"module",brief(module,40));put(line,"event",brief(event,160));put(line,"detail",brief(detail,1200));put(line,"error",error);lines.put(line);
            for(int i=0;i<Math.min(99,old.length());i++)lines.put(old.opt(i));
            prefs.edit().putString("events",lines.toString()).commit();
        }
    }
    public void debugLimited(String module,String event,String detail,boolean error){
        debug(module,event,detail,error);
    }
    private static String brief(String value,int length){if(value==null)return "";String text=value.replace('\n',' ').replace('\r',' ');return text.length()>length?text.substring(0,length)+"…":text;}
    public boolean workday() {
        return new HolidayCalendar(context).workday(LocalDate.now());
    }
    public String window() {
        if(!workday()||!hasTimes()) return "";
        JSONObject c=config(); int m=LocalTime.now().getHour()*60+LocalTime.now().getMinute();
        if(Rules.inWindow(m,c.optString("amStart"),c.optString("amEnd"))) return "am";
        if(Rules.inWindow(m,c.optString("pmStart"),c.optString("pmEnd"))) return "pm";
        return "";
    }
    public boolean hasTimes(){JSONObject c=config();return Rules.validWindows(c.optString("amStart"),c.optString("amEnd"),c.optString("pmStart"),c.optString("pmEnd"));}
    public long windowEnd(String w) {return hasTimes()?Rules.at(LocalDate.now(),config().optString(w+"End"),ZoneId.systemDefault()):0;}
    public static JSONObject json(String s) {try{return new JSONObject(s);}catch(JSONException e){return new JSONObject();}}
    public static JSONArray array(String s) {try{return new JSONArray(s);}catch(JSONException e){return new JSONArray();}}
    public static void put(JSONObject j,String k,Object v) {try{j.put(k,v);}catch(JSONException e){throw new IllegalArgumentException(e);}}
}
