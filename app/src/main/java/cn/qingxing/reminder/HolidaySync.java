package cn.qingxing.reminder;

import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class HolidaySync {
    static final long DAY=86_400_000L;static final int JOB=2026001;
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"holiday-sync");t.setDaemon(true);return t;});
    private static final List<Runnable> listeners=new ArrayList<>();private static volatile boolean running=false;
    public static boolean running(){return running;}
    public static void schedule(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs.getPendingJob(JOB)==null)jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,HolidaySyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(DAY,4*60*60_000L).setPersisted(true).setBackoffCriteria(30*60_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    public static synchronized void start(Context context,boolean force,Runnable done){
        Context c=context.getApplicationContext();Store s=new Store(c);long now=System.currentTimeMillis(),last=s.prefs.getLong("holiday_attempt",0);Handler main=new Handler(Looper.getMainLooper());
        if(running){if(done!=null)listeners.add(done);return;}
        if(last<=now&&now-last<(force?15_000:DAY)){if(done!=null)main.post(done);return;}
        if(done!=null)listeners.add(done);running=true;s.prefs.edit().putLong("holiday_attempt",now).apply();
        WORK.execute(()->{try{refresh(c);}catch(Exception e){s.prefs.edit().putString("holiday_status","同步未完成，已保留原有安排").putString("holiday_error",e.getClass().getSimpleName()).commit();}
            finally{main.post(()->{Planner.rebuild(c);MonitorService.reconcile(c,false);List<Runnable> callbacks;synchronized(HolidaySync.class){running=false;callbacks=new ArrayList<>(listeners);listeners.clear();}for(Runnable r:callbacks)r.run();});}
        });
    }
    private static void refresh(Context c){
        Store s=new Store(c);HolidayCalendar calendar=new HolidayCalendar(c);int current=LocalDate.now().getYear();List<String> report=new ArrayList<>();boolean changed=false;
        for(int year:new int[]{current,current+1}){try{
            JSONObject data=download(year,calendar.cached(year));calendar.commit(year,data);report.add(year+" 已更新（"+(data.optString("kind").equals("official")?"政府公告":"开源备用")+"）");changed=true;
        }catch(Exception e){report.add(year+" "+(calendar.known(year)?"沿用已保存安排":"等待可靠安排"));}}
        s.prefs.edit().putString("holiday_status",String.join("；",report)).putLong("holiday_checked",System.currentTimeMillis()).commit();s.log("调休检查："+String.join("；",report));
        if(changed)s.prefs.edit().putLong("holiday_success",System.currentTimeMillis()).apply();
    }
    /** Package-visible transport seam used only by the isolated tests; production always uses HTTPS. */
    interface Transport{String get(String url)throws Exception;}
    static Transport transport=HolidaySync::https;
    static JSONObject download(int year,JSONObject cache)throws Exception{
        LinkedHashSet<String> candidates=new LinkedHashSet<>();JSONArray old=cache.optJSONArray("papers");if(old!=null)for(int i=0;i<old.length();i++)candidates.add(old.optString(i));
        if(year==2026){candidates.add("https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm");candidates.add(Rules.HOLIDAY_SOURCE);}
        try{
            String query="国务院办公厅关于"+year+"年部分节假日安排的通知";
            String result=transport.get("https://sousuo.www.gov.cn/search-gov/data?t=zhengcelibrary&q="+URLEncoder.encode(query,"UTF-8")+"&searchfield=title&p=0&n=5&type=gwyzcwjk").replace("\\/","/");
            Matcher urls=Pattern.compile("https://[A-Za-z0-9._-]+\\.gov\\.cn/[^\\s\"<>\\\\]+").matcher(result);while(urls.find()&&candidates.size()<8)candidates.add(urls.group());
        }catch(Exception ignored){}
        Set<String> tried=new HashSet<>();for(String url:candidates)if(HolidayCalendar.governmentUrl(url)&&tried.size()<4){tried.add(url);try{return official(transport.get(url),year,url);}catch(Exception ignored){}}
        // Authorized fallback. It remains explicitly labelled community data even when it cites a government notice.
        Exception last=null;for(String source:new String[]{"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/"+year+".json","https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/"+year+".json"})try{
            JSONObject data=HolidayCalendar.community(transport.get(source),year,source);JSONArray papers=data.getJSONArray("papers");
            for(int i=0;i<papers.length();i++){String url=papers.getString(i);if(!tried.add(url))continue;try{return official(transport.get(url),year,url);}catch(Exception ignored){}}
            return data;
        }catch(Exception e){last=e;}throw last==null?new IOException("没有可靠年度数据"):last;
    }
    private static JSONObject official(String html,int year,String url){return HolidayCalendar.encode(HolidayParser.official(html,year),"official",url,new JSONArray().put(url));}
    private static String https(String raw)throws Exception{
        URI uri=new URI(raw);String host=uri.getHost();if(!HolidayCalendar.governmentUrl(raw)&&!("https".equals(uri.getScheme())&&("raw.githubusercontent.com".equals(host)||"cdn.jsdelivr.net".equals(host))))throw new IOException("不支持的数据源");
        HttpURLConnection conn=(HttpURLConnection)new URL(raw).openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(4000);conn.setReadTimeout(6000);conn.setRequestProperty("User-Agent","Qingxing/0.2 personal holiday sync");
        try{int status=conn.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2*1024*1024)throw new IOException("数据过大");bytes.write(b,0,n);}String type=conn.getContentType();Charset charset=type!=null&&type.toLowerCase(Locale.ROOT).matches(".*charset=.?gb.*")?Charset.forName("GB18030"):StandardCharsets.UTF_8;return new String(bytes.toByteArray(),charset);}}
        finally{conn.disconnect();}
    }
}
