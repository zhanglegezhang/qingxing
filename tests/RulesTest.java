package cn.qingxing.reminder;
import java.time.*;import java.util.*;
public final class RulesTest {
 static int n;static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){Map<String,Boolean> days=new HashMap<>();check(Rules.workday(LocalDate.of(2026,10,10),days),"makeup day");check(!Rules.workday(LocalDate.of(2026,10,5),days),"official holiday");days.put("2026-10-10",false);check(!Rules.workday(LocalDate.of(2026,10,10),days),"manual override");
 check(Rules.validWindows("07:00","10:00","17:30","20:30"),"windows");check(!Rules.validWindows("07:00","18:00","17:30","20:30"),"overlap");check(!Rules.validWindows("","","",""),"empty disabled");check(Rules.inWindow(420,"07:00","10:00")&&!Rules.inWindow(600,"07:00","10:00"),"half open boundary");
 check(Rules.matches("Office","Office"),"SSID equality");check(!Rules.matches("Office","office"),"case sensitive");check(!Rules.matches("Office","Home"),"other network");check(!Rules.matches("",""),"empty not connected");check(!Rules.matches("Office",null),"read failure");
 check(Rules.eligible("am",true)&&!Rules.eligible("am",false),"AM connected only");check(Rules.eligible("pm",false)&&!Rules.eligible("pm",true),"PM disconnected only");check(!Rules.eligible("",false),"outside window");
 check(!Rules.due(0,60000,59999),"no startup or early alert");check(Rules.due(0,60000,60000),"first minute");check(Rules.nextNode(0,60000)==120000,"second node");check(Rules.nextNode(0,181000)==240000,"late delivery skips missed nodes");check(!Rules.due(60000,120000,0),"clock backwards");check(Rules.WIFI_POLL==10000,"ten second poll");
 for(int minute=1;minute<=60;minute++){long node=Rules.nextNode(1234,1234+minute*60000);check(node==1234+(minute+1)*60000,"fixed cadence "+minute);check(!Rules.due(1234,node,node-1),"no duplicate before node "+minute);}
 System.out.println("PASS "+n+" decision-rule checks");}
}
