package cn.qingxing.reminder;

import android.app.*;
import android.content.*;
import java.util.*;

/** Public Android grants plus a separately labelled user attestation for vendor autostart. */
final class Authorizations {
    static final String ENTRY_LABEL="完成以下必要授权：";
    static final String NOTIFICATION_PATH="1️⃣通知：选择“始终允许”";
    static final String PRECISE_PATH="2️⃣权限管理-位置：勾选“精确位置”";
    static final String BACKGROUND_PATH="3️⃣权限管理-位置：选择“始终允许”";
    static final String ALARM_PATH="4️⃣允许闹钟和提醒";
    static final String AUTOSTART_PATH="5️⃣自启动：勾选";
    static boolean autostart(Context c){return c.getSharedPreferences("qingxing_v1",0).getBoolean("autostartConfirmed",false);}
    static String missing(Context c){
        List<String> missing=new ArrayList<>();
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!nm.areNotificationsEnabled())missing.add(NOTIFICATION_PATH);
        if(!WifiConnection.permitted(c,false))missing.add(PRECISE_PATH);
        if(!WifiConnection.permitted(c,true))missing.add(BACKGROUND_PATH);
        if(!Planner.exact(c))missing.add(ALARM_PATH);
        if(!autostart(c))missing.add(AUTOSTART_PATH+"（待手动确认）");
        if(nm.areNotificationsEnabled()){
            if(!Alerts.allowed(c,Alerts.monitorChannel(c)))missing.add("选中监控通知被禁用");
            if(!Alerts.allowed(c,Alerts.remindChannel(c)))missing.add("选中提醒通知被禁用");
        }
        return String.join("、",missing);
    }
    static boolean ready(Context c){return missing(c).isEmpty();}
}
