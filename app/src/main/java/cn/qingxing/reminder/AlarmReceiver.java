package cn.qingxing.reminder;
import android.content.*;import org.json.*;
public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){try{
        String a=i.getAction();Store s=new Store(c);
        if("ALARM".equals(a)){Planner.onAlarm(c,i.getStringExtra("token"));return;}
        if("DONE".equals(a)){s.debug("通知","收到打卡确认",AttendanceData.attendanceKey(i.getStringExtra("key"))?i.getStringExtra("key"):"未知提醒",false);Planner.complete(c,i.getStringExtra("key"),i.getLongExtra("due",0));return;}
        if("QUIET".equals(a)||"SNOOZE".equals(a))return;
        if("PAUSE".equals(a)){s.enabled(false);Planner.reset(c);c.stopService(new Intent(c,MonitorService.class));Planner.rebuild(c);s.log("已暂停 Wi-Fi 监控和打卡提醒");return;}
        if(Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a))Planner.reset(c);
        s.log("系统事件："+a);HolidaySync.schedule(c);Planner.rebuild(c);MonitorService.reconcile(c,false);
    }catch(Exception e){new Store(c).debug("计划","恢复失败",e.getClass().getSimpleName(),true);}}
}
