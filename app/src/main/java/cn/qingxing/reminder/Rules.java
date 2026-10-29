package cn.qingxing.reminder;

import java.time.*;
import java.util.*;

/** Pure decision rules: no Android dependency. Distances and accuracy are metres. */
public final class Rules {
    public static final String HOLIDAY_SOURCE = "https://www.beijing.gov.cn/cs/gncs/zcwj/202603/t20260327_4568275.html";
    private static final Set<LocalDate> OFF = new HashSet<>(), ON = new HashSet<>();
    static {
        for (String span : new String[]{"2026-01-01/2026-01-03", "2026-02-15/2026-02-23", "2026-04-04/2026-04-06", "2026-05-01/2026-05-05", "2026-06-19/2026-06-21", "2026-09-25/2026-09-27", "2026-10-01/2026-10-07"}) {
            String[] s = span.split("/");
            for (LocalDate d=LocalDate.parse(s[0]); !d.isAfter(LocalDate.parse(s[1])); d=d.plusDays(1)) OFF.add(d);
        }
        for (String d : new String[]{"2026-01-04","2026-02-14","2026-02-28","2026-05-09","2026-09-20","2026-10-10"}) ON.add(LocalDate.parse(d));
    }
    public static boolean workday(LocalDate d, Map<String, Boolean> overrides) {
        if (overrides.containsKey(d.toString())) return overrides.get(d.toString());
        if (ON.contains(d)) return true;
        if (OFF.contains(d)) return false;
        return d.getDayOfWeek().getValue() <= 5;
    }
    public static boolean knownYear(int year) { return year == 2026; }
    public static int minutes(String t) {
        LocalTime v = LocalTime.parse(t); return v.getHour()*60+v.getMinute();
    }
    public static boolean inWindow(int minute, String start, String end) {
        return minute >= minutes(start) && minute < minutes(end);
    }
    public static boolean validWindows(String as,String ae,String ps,String pe) {
        try {int a=minutes(as),b=minutes(ae),c=minutes(ps),d=minutes(pe);return a<b&&b<=c&&c<d;}
        catch(RuntimeException e){return false;}
    }
    public static long at(LocalDate d, String time, ZoneId zone) {
        return d.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli();
    }
    public static final long MINUTE=60_000L, WIFI_POLL=10_000L;
    public static boolean matches(String bound,String observed){return bound!=null&&!bound.trim().isEmpty()&&bound.trim().equals(observed);}
    public static boolean eligible(String window,boolean connected){return "am".equals(window)?connected:"pm".equals(window)&&!connected;}
    public static long nextNode(long started,long now){return started+(Math.max(0,now-started)/MINUTE+1)*MINUTE;}
    public static boolean due(long started,long next,long now){return now>=started&&now>=next&&now-started>=MINUTE;}
}
