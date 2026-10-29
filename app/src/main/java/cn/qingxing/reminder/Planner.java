package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.Build;import org.json.*;import java.time.*;import java.util.*;
/** One persisted round, fixed minute nodes, and fresh SSID evaluation at every node. */
public final class Planner {
    static PendingIntent alarm(Context c,String token){return PendingIntent.getBroadcast(c,0,new Intent(c,AlarmReceiver.class).setAction("ALARM").setData(Uri.parse("qingxing://alarm/"+token)).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void set(Context c,String token,long at,boolean reminder){if(!exact(c))return;AlarmManager m=c.getSystemService(AlarmManager.class);if(reminder)m.setAlarmClock(new AlarmManager.AlarmClockInfo(at,Alerts.open(c)),alarm(c,token));else m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c,token));}
    static String activeKey(Store s){String w=s.window();return s.enabled()&&!s.config().optString("ssid").trim().isEmpty()&&!w.isEmpty()&&!s.day().optBoolean(w+"Done")&&Authorizations.ready(s.context())?w:"";}
    static String identity(Store s,String w){JSONObject cfg=s.config();return s.today()+"|"+w+"|"+cfg.optString("ssid")+"|"+cfg.optString(w+"Start")+"|"+cfg.optString(w+"End");}
    static JSONObject ensureRound(Store s,long now){String key=activeKey(s);if(key.isEmpty())return new JSONObject();JSONObject r=s.round();String identity=identity(s,key);
        if(!identity.equals(r.optString("identity"))||r.optLong("startedAt")>now||r.optLong("nextAt")<=0){clearPending(s.context(),s);r=new JSONObject();Store.put(r,"identity",identity);Store.put(r,"id",java.util.UUID.randomUUID().toString());Store.put(r,"key",key);Store.put(r,"startedAt",now);Store.put(r,"nextAt",now+Rules.MINUTE);s.round(r);s.debug("监测","开始新一轮",(key.equals("am")?"上班":"下班")+"；满一分钟首次检查提醒",false);}return r;
    }
    static void clearPending(Context c,Store s){JSONObject p=s.pending();if(p.length()>0){s.pending(new JSONObject());s.debug("提醒","已停止","当前条件不满足、时段切换或监控结束；取消通知及声震",false);}Alerts.cancel(c,"am");Alerts.cancel(c,"pm");}
    public static void reset(Context c){Store s=new Store(c);clearPending(c,s);s.round(new JSONObject());}
    static String inactiveStatus(Store s){String w=s.window();if(!s.enabled())return "打卡提醒已关闭";if(!s.workday())return "非工作日，不监控、不提醒";if(!w.isEmpty()&&s.day().optBoolean(w+"Done"))return "本时段已确认打卡，等待下个时段";if(!Authorizations.ready(s.context()))return "监控已暂停，请完成必要授权："+Authorizations.missing(s.context());if(s.config().optString("ssid").trim().isEmpty())return "请先配置公司 Wi-Fi";
        if(s.hasTimes()){int minute=LocalTime.now().getHour()*60+LocalTime.now().getMinute();JSONObject cfg=s.config();for(String k:new String[]{"pm","am"})if(minute>=Rules.minutes(cfg.optString(k+"End"))&&!s.day().optBoolean(k+"Done"))return (k.equals("am")?"上班":"下班")+"提醒时段已结束，尚未确认";}return "等待下一个提醒时段";}
    public static void cleanup(Context c){Store s=new Store(c);if(activeKey(s).isEmpty()){clearPending(c,s);String reason=inactiveStatus(s);if(s.round().length()>0){s.round(new JSONObject());s.debug("监控","本轮停止",reason,false);}s.status(reason);}}
    public static void checkNow(Context c){Store s=new Store(c);if(activeKey(s).isEmpty()){cleanup(c);return;}WifiConnection.State wifi=WifiConnection.read(c,s.config().optString("ssid"));poll(c,System.currentTimeMillis(),wifi.connected,wifi.detail+"；绑定名称="+s.config().optString("ssid")+"；读取名称="+wifi.name);}
    static void poll(Context c,long now,boolean connected,String detail){Store s=new Store(c);String key=activeKey(s);if(key.isEmpty()){cleanup(c);return;}JSONObject r=ensureRound(s,now);
        boolean eligible=Rules.eligible(key,connected);Store.put(r,"connected",connected);Store.put(r,"observedAt",now);
        s.debugLimited("Wi-Fi",connected?"已连接":"未连接",detail,false);
        s.debug("检查","Wi-Fi 条件",(key.equals("am")?"上班":"下班")+"；符合提醒条件="+eligible+"；本轮开始="+r.optLong("startedAt")+"；下次分钟节点="+r.optLong("nextAt"),false);
        if(!eligible)clearPending(c,s);
        if(Rules.due(r.optLong("startedAt"),r.optLong("nextAt"),now)){
            Store.put(r,"nextAt",Rules.nextNode(r.optLong("startedAt"),now));s.round(r);
            if(eligible){JSONObject pending=s.pending(),p=pending.optJSONObject(key);if(!AttendanceData.validPending(key,p)||!r.optString("id").equals(p.optString("round"))){p=new JSONObject();Store.put(p,"type","attendance");Store.put(p,"due",now);Store.put(p,"round",r.optString("id"));}
                Store.put(p,"title",key.equals("am")?"上班卡尚未确认，请记得打卡":"下班卡尚未确认，请记得打卡");Store.put(p,"stopAt",s.windowEnd(key));Store.put(p,"nextAt",r.optLong("nextAt"));Store.put(pending,key,p);s.pending(pending);Alerts.show(c,key,p);s.log("每分钟提醒："+(key.equals("am")?"上班":"下班")+"卡尚未确认");
            }
        }
        s.round(r);String status=(key.equals("am")?"上班":"下班")+"监控 · "+(connected?"公司 Wi-Fi 已连接":"公司 Wi-Fi 未连接")+" · "+(eligible?"每分钟提醒":"条件不满足，不提醒")+" · 最近检查 "+Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0);s.status(status);rebuild(c);
    }
    public static void rebuild(Context c){Store s=new Store(c);Alerts.channels(c);long now=System.currentTimeMillis();String w=activeKey(s);if(w.isEmpty())cleanup(c);JSONObject r=w.isEmpty()?new JSONObject():ensureRound(s,now);
        for(String t:new HashSet<>(s.prefs.getStringSet("alarmTokens",Collections.emptySet())))c.getSystemService(AlarmManager.class).cancel(alarm(c,t));
        Map<String,Long> tasks=new LinkedHashMap<>();if(s.enabled()&&s.hasTimes()&&Authorizations.ready(c)){
            long next=Rules.at(LocalDate.now().plusDays(1),"00:00",ZoneId.systemDefault());JSONObject cfg=s.config();for(String k:new String[]{"amStart","amEnd","pmStart","pmEnd"}){long t=Rules.at(LocalDate.now(),cfg.optString(k),ZoneId.systemDefault());if(t>now)next=Math.min(next,t);}tasks.put("boundary",next);
        }
        if(!w.isEmpty())tasks.put("wifi_tick",Math.max(now+1000,r.optLong("nextAt")));
        JSONObject scheduled=new JSONObject();for(Map.Entry<String,Long> e:tasks.entrySet())Store.put(scheduled,e.getKey(),e.getValue());s.prefs.edit().putStringSet("alarmTokens",new HashSet<>(tasks.keySet())).putString("scheduledTimes",scheduled.toString()).commit();
        try{for(Map.Entry<String,Long> e:tasks.entrySet())set(c,e.getKey(),e.getValue(),e.getKey().equals("wifi_tick"));}catch(SecurityException e){s.debugLimited("计划","闹钟权限不足","请允许闹钟和提醒以恢复锁屏定时",true);}
    }
    public static void complete(Context c,String key,long due){Store s=new Store(c);JSONObject p=s.pending().optJSONObject(key);
        if(!AttendanceData.validPending(key,p)||p.optLong("due")!=due||!key.equals(activeKey(s))||p.optLong("stopAt")<=System.currentTimeMillis()||!s.round().optString("id").equals(p.optString("round"))){s.debug("确认","校验拒绝","提醒已取消、过期或不匹配",true);return;}
        s.debug("确认","校验通过",key,false);attendanceDone(c,key);
    }
    public static void attendanceDone(Context c,String key){if(!AttendanceData.attendanceKey(key))return;Store s=new Store(c);JSONObject day=s.day();Store.put(day,key+"Done",true);s.day(day);s.log("手动确认"+(key.equals("am")?"上班":"下班")+"已打卡");s.debug("状态","完成状态已更新",key+"=已确认",false);if(key.equals(s.round().optString("key")))reset(c);else Alerts.cancel(c,key);rebuild(c);MonitorService.reconcile(c,true);}
    public static void attendanceUndo(Context c,String key){if(!AttendanceData.attendanceKey(key))return;Store s=new Store(c);JSONObject day=s.day();if(!day.optBoolean(key+"Done"))return;Store.put(day,key+"Done",false);s.day(day);s.log("已取消"+(key.equals("am")?"上班":"下班")+"打卡确认");if(key.equals(s.window()))reset(c);rebuild(c);MonitorService.reconcile(c,true);}
    public static void onAlarm(Context c,String token){if(!"wifi_tick".equals(token)&&!"boundary".equals(token))return;checkNow(c);rebuild(c);MonitorService.reconcile(c,false);}
    /** Only retained for instrumentation that seeds historical APKs; retired callers cannot create alerts. */
    @Deprecated public static void create(Context c,String key,String type,String title,long due,long stop){}
}
