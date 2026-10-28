package cn.qingxing.reminder;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;

/** Strict, deterministic parsing of annual State Council notices. Never executes fetched content. */
public final class HolidayParser {
    public static final String[] NAMES={"元旦","春节","清明节","劳动节","端午节","中秋节","国庆节"};
    public static final class Data {
        public final int year;public final Map<String,Boolean> work=new TreeMap<>();public final Map<String,String> names=new TreeMap<>();
        public Data(int y){year=y;}
        public void add(LocalDate date,boolean isWork,String name){
            if(!(date.getYear()==year||date.getYear()==year-1&&date.getMonthValue()==12))throw new IllegalArgumentException("日期不属于公告年份");
            String key=date.toString();if(work.containsKey(key)&&work.get(key)!=isWork)throw new IllegalArgumentException("休息与补班日期冲突");
            work.put(key,isWork);names.put(key,name);
        }
        public void validate(){
            if(work.size()<15||work.size()>100)throw new IllegalArgumentException("年度日期数量异常");
            Set<String> covered=new HashSet<>();int off=0,on=0;
            for(String day:work.keySet()){if(work.get(day))on++;else{off++;for(String n:NAMES)if(names.get(day).contains(n))covered.add(n);}}
            if(covered.size()!=7||off<15||off>70||on>20)throw new IllegalArgumentException("年度安排不完整");
        }
    }
    public static String plain(String html){
        String t=html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>"," ").replaceAll("(?s)<[^>]+>"," ")
            .replace("&nbsp;"," ").replace("&#160;"," ").replace("&ldquo;","“").replace("&rdquo;","”").replace("&amp;","&").replaceAll("\\s+"," ");
        String from="國務辦廳關於節勞動慶調連與週號間",to="国务办厅关于节劳动庆调连与周号间";
        for(int i=0;i<from.length();i++)t=t.replace(from.charAt(i),to.charAt(i));return t;
    }
    public static Data official(String html,int year){
        String text=plain(html);String title="国务院办公厅关于"+year+"年部分节假日安排的通知";
        if(!text.replace(" ","").contains(title))throw new IllegalArgumentException("公告标题或年份不匹配");
        Pattern heading=Pattern.compile("[一二三四五六七八九十]+[、．.]\\s*((?:(?:元旦|春节|清明节|劳动节|端午节|中秋节|国庆节)[、，和及\\s]*)+)[:：]");
        Matcher m=heading.matcher(text);List<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();List<String> names=new ArrayList<>();
        while(m.find()){starts.add(m.start());ends.add(m.end());names.add(m.group(1).trim());}
        if(starts.size()<6||starts.size()>7)throw new IllegalArgumentException("公告段落不完整");
        Data d=new Data(year);
        for(int i=0;i<starts.size();i++){
            String part=text.substring(ends.get(i),i+1<starts.size()?starts.get(i+1):text.length());int offEnd=part.indexOf("放假");
            if(offEnd<0)throw new IllegalArgumentException("未识别放假日期");
            dates(part.substring(0,offEnd),year,d,false,names.get(i));
            String tail=part.substring(offEnd+2);int onEnd=tail.indexOf("上班");
            if(onEnd>=0)dates(tail.substring(0,onEnd),year,d,true,names.get(i));
        }
        d.validate();return d;
    }
    private static void dates(String text,int year,Data data,boolean work,String name){
        Matcher m=Pattern.compile("(?:(\\d{4})年)?(?:(\\d{1,2})月)?(\\d{1,2})日(?:[（(][^）)]*[）)])?").matcher(text);
        LocalDate previous=null;int end=0,month=0,activeYear=year,found=0;
        while(m.find()){
            if(m.group(1)!=null)activeYear=Integer.parseInt(m.group(1));
            if(m.group(2)!=null){int newMonth=Integer.parseInt(m.group(2));if(previous!=null&&newMonth<month&&m.group(1)==null)activeYear++;month=newMonth;}
            if(month==0)throw new IllegalArgumentException("缺少月份");LocalDate current=LocalDate.of(activeYear,month,Integer.parseInt(m.group(3)));
            if(previous!=null&&text.substring(end,m.start()).matches("\\s*(?:至|到|—|－|-)\\s*")){
                long gap=ChronoUnit.DAYS.between(previous,current);if(gap<0||gap>16)throw new IllegalArgumentException("日期范围异常");
                for(LocalDate p=previous.plusDays(1);!p.isAfter(current);p=p.plusDays(1))data.add(p,work,name);
            }else data.add(current,work,name);
            previous=current;end=m.end();found++;
        }
        if(found==0)throw new IllegalArgumentException("没有可识别日期");
    }
}
