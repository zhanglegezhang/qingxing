package cn.qingxing.reminder;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class HolidayParserTest {
    static int count;
    static void check(boolean b,String m){count++;if(!b)throw new AssertionError(m);}
    static void reject(String text,int year){try{HolidayParser.official(text,year);throw new AssertionError("invalid notice accepted");}catch(IllegalArgumentException e){count++;}}
    public static void main(String[] args)throws Exception{
        String source=new String(Files.readAllBytes(Paths.get("tests/fixtures/official-2026.txt")),java.nio.charset.StandardCharsets.UTF_8);
        HolidayParser.Data data=HolidayParser.official(source,2026);
        check(data.work.size()==39,"complete 2026 changes");
        for(LocalDate d=LocalDate.of(2026,1,1);d.getYear()==2026;d=d.plusDays(1)){
            boolean expected=Rules.workday(d,Collections.emptyMap());Boolean changed=data.work.get(d.toString());check((changed!=null?changed:d.getDayOfWeek().getValue()<=5)==expected,"2026 date "+d);
        }
        check(data.work.get("2026-02-14"),"two work dates parsed");check(data.work.get("2026-02-28"),"second work date parsed");
        reject(source,2027);reject(source.replace("三、清明节：4月4日（周六）至6日（周一）放假，共3天。",""),2026);
        reject(source.replace("1月4日（周日）上班","1月2日（周五）上班"),2026);
        reject(source.replace("至3日（周六）","至30日（周六）"),2026);
        String html="<script>fake</script><h1>"+source.replace("\n","</p><p>")+"</h1>";
        check(HolidayParser.official(html,2026).work.equals(data.work),"HTML parsing");
        String traditional=source.replace("国务院办公厅关于","國務院辦公廳關於").replace("节","節").replace("劳动","勞動").replace("国庆","國慶").replace("调","調");
        check(HolidayParser.official(traditional,2026).work.equals(data.work),"traditional government mirror");
        HolidayParser.Data cross=new HolidayParser.Data(2027);cross.add(LocalDate.of(2026,12,31),false,"元旦");check(cross.work.containsKey("2026-12-31"),"prior December is allowed");
        try{cross.add(LocalDate.of(2026,11,1),false,"元旦");throw new AssertionError("unrelated year accepted");}catch(IllegalArgumentException e){count++;}
        System.out.println("PASS "+count+" holiday-parser checks");
    }
}
