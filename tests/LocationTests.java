package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;
/** Former location suite now verifies the GPS-free monitor and real polling lifetime. */
public final class LocationTests extends Instrumentation {
 Context c;int n;void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}void main(Runnable r){runOnMainSync(r);}
 boolean running(){for(ActivityManager.RunningServiceInfo s:c.getSystemService(ActivityManager.class).getRunningServices(100))if(s.service.getClassName().equals(MonitorService.class.getName())&&s.foreground)return true;return false;}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();c=getTargetContext();try{
 main(()->{Store s=new Store(c);s.enabled(false);c.stopService(new Intent(c,MonitorService.class));Planner.reset(c);FlowTests.window(s,"pm");Planner.rebuild(c);MonitorService.reconcile(c,true);});Thread.sleep(800);
 Store s=new Store(c);check(running(),"real Wi-Fi foreground service started");long observed=s.round().optLong("observedAt");check(observed>0,"first real Wi-Fi observation recorded");check(s.prefs.getString("status","").contains("Wi-Fi"),"status describes Wi-Fi not distance");Thread.sleep(10_700);check(s.round().optLong("observedAt")>=observed+9000,"real ten second polling advances while no UI is required");check(s.pending().length()==0,"no reminder before first minute");
 long origin=s.round().optLong("startedAt");getUiAutomation().executeShellCommand("input keyevent 223").close();
 Thread.sleep(Math.max(0,origin+55_000-System.currentTimeMillis()));check(s.pending().length()==0,"real screen-off round remains silent at 55 seconds");
 long deadline=System.currentTimeMillis()+12_000;while(!s.pending().has("pm")&&System.currentTimeMillis()<deadline)Thread.sleep(100);
 check(s.pending().has("pm"),"real alarm/foreground poll delivers first reminder after a minute while screen off");check(s.pending().optJSONObject("pm").optLong("due")-origin>=60_000,"actual reminder not early");
 getUiAutomation().executeShellCommand("input keyevent 224").close();getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
 main(()->{JSONObject cfg=s.config();Store.put(cfg.optJSONObject("calendar"),s.today(),false);s.saveConfig(cfg);Planner.rebuild(c);MonitorService.reconcile(c,true);});Thread.sleep(500);check(!running()&&s.round().length()==0,"nonwork day stops service and round");check(s.pending().length()==0,"nonwork day no reminders");
 main(()->{FlowTests.window(s,"am");Planner.attendanceDone(c,"am");});Thread.sleep(300);check(!running(),"confirmed current window stops monitor");check(s.prefs.getString("status","").contains("已确认"),"confirmed status displayed");
 out.putString("stream","PASS "+n+" location callback/status checks: location removed; SSID polling and lifecycle verified");finish(-1,out);
 }catch(Throwable t){out.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(0,out);}finally{main(()->{new Store(c).enabled(false);c.stopService(new Intent(c,MonitorService.class));Planner.reset(c);Planner.rebuild(c);});}}
}
