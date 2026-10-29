package cn.qingxing.reminder;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.time.*;
import java.io.*;
import java.util.concurrent.*;

public final class HolidayTests extends Instrumentation {
    int checks;Context c;HolidaySync.Transport original;
    void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    String fixture(String name)throws Exception{try(InputStream in=getContext().getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    void sync(boolean force)throws Exception{CountDownLatch latch=new CountDownLatch(1);HolidaySync.start(c,force,latch::countDown);check(latch.await(10,TimeUnit.SECONDS),"async sync completed without blocking UI");}
    @Override public void onStart(){Bundle out=new Bundle();c=getTargetContext();original=HolidaySync.transport;try{
        Store s=new Store(c);s.enabled(false);s.pending(new JSONObject());Planner.rebuild(c);s.prefs.edit().clear().commit();
        String json=fixture("holiday-2026.json"),official=fixture("official-2026.txt");
        JSONObject community=HolidayCalendar.community(json,2026,"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2026.json");
        HolidayParser.Data reference=HolidayParser.official(official,2026);boolean same=community.getJSONObject("work").length()==reference.work.size();for(String day:reference.work.keySet())same&=community.getJSONObject("work").getBoolean(day)==reference.work.get(day);check(same,"entire downloaded 2026 calendar matches independently parsed official notice");
        check("community".equals(community.optString("kind")),"fallback explicitly labelled");check(community.getJSONObject("work").getBoolean("2026-10-10"),"weekend make-up work");check(!community.getJSONObject("work").getBoolean("2026-10-05"),"holiday weekday off");
        check(HolidayCalendar.governmentUrl("https://www.gov.cn/notice"),"government URL accepted");check(!HolidayCalendar.governmentUrl("https://gov.cn.evil.example/notice"),"spoof URL rejected");check(!HolidayCalendar.governmentUrl("http://www.gov.cn/notice"),"cleartext URL rejected");
        JSONObject invalid=new JSONObject(json);invalid.put("year",2027);try{HolidayCalendar.community(invalid.toString(),2026,"test");throw new AssertionError("wrong year accepted");}catch(IllegalArgumentException e){checks++;}
        invalid=new JSONObject(json);invalid.put("papers",new JSONArray().put("https://example.org/fake"));try{HolidayCalendar.community(invalid.toString(),2026,"test");throw new AssertionError("fake source accepted");}catch(IllegalArgumentException e){checks++;}
        invalid=new JSONObject(json);invalid.put("days",new JSONArray().put(invalid.getJSONArray("days").getJSONObject(0)));try{HolidayCalendar.community(invalid.toString(),2026,"test");throw new AssertionError("partial data accepted");}catch(IllegalArgumentException e){checks++;}
        invalid=new JSONObject(json);JSONObject opposite=new JSONObject(invalid.getJSONArray("days").getJSONObject(0).toString());opposite.put("isOffDay",false);invalid.getJSONArray("days").put(opposite);try{HolidayCalendar.community(invalid.toString(),2026,"test");throw new AssertionError("conflict accepted");}catch(IllegalArgumentException e){checks++;}
        HolidayCalendar cal=new HolidayCalendar(c);LocalDate future=LocalDate.of(2031,1,6);check(!cal.ready(future)&&!cal.workday(future),"unknown year pauses automatic attendance");
        JSONObject cfg=s.config();cfg.getJSONObject("calendar").put(future.toString(),true);cfg.getJSONObject("calendar").put("2026-10-10",false);s.saveConfig(cfg);check(cal.ready(future)&&cal.workday(future),"explicit custom date can be used");
        cal.commit(2026,community);check(!cal.workday(LocalDate.of(2026,10,10)),"manual override wins over synchronized calendar");check(s.config().getJSONObject("calendar").has(future.toString()),"sync preserves other manual dates");
        HolidaySync.transport=url->{if(url.contains("search-gov"))return "{}";if(HolidayCalendar.governmentUrl(url))return official;throw new IOException("unexpected fallback");};
        check("official".equals(HolidaySync.download(2026,new JSONObject()).optString("kind")),"official source wins when usable");
        HolidaySync.transport=url->{if(HolidayCalendar.governmentUrl(url))throw new IOException("offline government");if(url.endsWith("2026.json"))return json;throw new FileNotFoundException();};
        check("community".equals(HolidaySync.download(2026,new JSONObject()).optString("kind")),"fallback used when government unavailable");
        s.prefs.edit().putLong("holiday_attempt",0).commit();sync(true);check(cal.cached(2026).optInt("year")==2026,"real async flow commits valid current year");check(!cal.known(2027),"unavailable next year not marked synchronized");check(s.prefs.getString("holiday_status","").contains("等待可靠安排"),"missing year status is honest");
        String saved=cal.cached(2026).toString();HolidaySync.transport=url->{throw new IOException("network offline");};s.prefs.edit().putLong("holiday_attempt",0).commit();sync(true);
        check(saved.equals(cal.cached(2026).toString()),"offline failure keeps cached data byte-for-byte");check(!cal.workday(LocalDate.of(2026,10,10)),"offline failure keeps manual override");
        cfg=s.config();cfg.getJSONObject("calendar").put(LocalDate.now().toString(),true);s.saveConfig(cfg);JSONObject stale=new JSONObject().put("type","attendance").put("title","昨日打卡").put("due",System.currentTimeMillis()-86_400_000L).put("nextAt",System.currentTimeMillis()).put("stopAt",System.currentTimeMillis()+600_000);s.pending(new JSONObject().put("am",stale));Planner.rebuild(c);check(!s.pending().has("am"),"next-day recovery cannot revive yesterday attendance reminder");
        final int[] requests={0};HolidaySync.transport=url->{requests[0]++;throw new IOException();};sync(false);check(requests[0]==0,"opening again within a day does not refetch");
        HolidaySync.schedule(c);JobInfo job=c.getSystemService(JobScheduler.class).getPendingJob(HolidaySync.JOB);check(job!=null&&job.isPersisted(),"daily sync survives reboot");check(job.isPeriodic()&&job.getIntervalMillis()==86_400_000L,"daily periodic job configured");check(job.getNetworkType()==JobInfo.NETWORK_TYPE_ANY,"job requires network");
        out.putString("stream","PASS "+checks+" holiday sync/cache checks\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable t){out.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,out);}finally{HolidaySync.transport=original;}}
}
