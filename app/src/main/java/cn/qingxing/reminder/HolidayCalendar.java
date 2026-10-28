package cn.qingxing.reminder;

import android.content.*;
import org.json.*;
import java.time.*;
import java.util.*;

public final class HolidayCalendar {
    private final Store s;
    public HolidayCalendar(Context c){s=new Store(c);}
    public JSONObject cached(int year){return Store.json(s.prefs.getString("holiday_year_"+year,"{}"));}
    public boolean known(int year){return Rules.knownYear(year)||cached(year).optInt("year")==year;}
    public boolean ready(LocalDate date){return overrides().has(date.toString())||known(date.getYear());}
    public JSONObject overrides(){return s.config().optJSONObject("calendar");}
    public boolean workday(LocalDate date){
        JSONObject custom=overrides();String key=date.toString();if(custom.has(key))return custom.optBoolean(key);
        if(!known(date.getYear()))return false;
        // Next year's New Year notice may also change dates in December of this year.
        for(int year:new int[]{date.getYear()+1,date.getYear()}){JSONObject changes=cached(year).optJSONObject("work");if(changes!=null&&changes.has(key))return changes.optBoolean(key);}
        if(Rules.knownYear(date.getYear()))return Rules.workday(date,Collections.emptyMap());
        return date.getDayOfWeek().getValue()<=5;
    }
    public String name(LocalDate date){
        for(int year:new int[]{date.getYear()+1,date.getYear()}){JSONObject names=cached(year).optJSONObject("names");if(names!=null&&names.has(date.toString()))return names.optString(date.toString());}
        if(date.getYear()==2026){String k=date.toString();String[][] spans={{"01-01","01-04","元旦"},{"02-14","02-28","春节"},{"04-04","04-06","清明节"},{"05-01","05-09","劳动节"},{"06-19","06-21","端午节"},{"09-20","09-27","中秋节"},{"10-01","10-10","国庆节"}};
            for(String[] span:spans)if(k.compareTo("2026-"+span[0])>=0&&k.compareTo("2026-"+span[1])<=0&&(!Rules.workday(date,Collections.emptyMap())&&date.getDayOfWeek().getValue()<=5||Rules.workday(date,Collections.emptyMap())&&date.getDayOfWeek().getValue()>5))return span[2];
        }return "";
    }
    public String sourceLabel(int year){String kind=cached(year).optString("kind");return kind.equals("official")?"政府公告":kind.equals("community")?"开源备用 · 附官方公告":Rules.knownYear(year)?"内置已核对安排":"待取得可靠安排";}
    public static boolean governmentUrl(String raw){try{java.net.URI u=new java.net.URI(raw);String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.equals("gov.cn")||h.endsWith(".gov.cn"))&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}}
    public static JSONObject encode(HolidayParser.Data data,String kind,String source,JSONArray papers){
        data.validate();JSONObject out=new JSONObject(),work=new JSONObject(),names=new JSONObject();
        for(String day:data.work.keySet()){Store.put(work,day,data.work.get(day));Store.put(names,day,data.names.get(day));}
        Store.put(out,"year",data.year);Store.put(out,"work",work);Store.put(out,"names",names);Store.put(out,"kind",kind);Store.put(out,"source",source);Store.put(out,"papers",papers);Store.put(out,"updatedAt",System.currentTimeMillis());return out;
    }
    public static JSONObject community(String raw,int year,String source)throws JSONException{
        JSONObject root=new JSONObject(raw);if(!(root.get("year") instanceof Number)||root.getDouble("year")!=year)throw new IllegalArgumentException("年份不匹配");
        JSONArray papers=root.getJSONArray("papers");if(papers.length()<1||papers.length()>8)throw new IllegalArgumentException("缺少官方公告来源");for(int i=0;i<papers.length();i++)if(!governmentUrl(papers.getString(i)))throw new IllegalArgumentException("公告链接不是政府网站");
        JSONArray days=root.getJSONArray("days");if(days.length()>100)throw new IllegalArgumentException("日期过多");HolidayParser.Data data=new HolidayParser.Data(year);
        for(int i=0;i<days.length();i++){JSONObject day=days.getJSONObject(i);if(!(day.get("isOffDay") instanceof Boolean))throw new IllegalArgumentException("日期类型无效");String name=HolidayParser.plain(day.getString("name"));if(name.length()>40)throw new IllegalArgumentException("节日名称无效");data.add(LocalDate.parse(day.getString("date")),!day.getBoolean("isOffDay"),name);}
        return encode(data,"community",source,papers);
    }
    public void commit(int year,JSONObject data){if(data.optInt("year")!=year)throw new IllegalArgumentException("年份不匹配");s.prefs.edit().putString("holiday_year_"+year,data.toString()).commit();}
}
