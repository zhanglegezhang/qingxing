package cn.qingxing.reminder;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native view composition. Business rules and storage stay outside the presentation layer. */
final class Screens {
    final MainActivity a;final Store store;final HolidayCalendar calendar;
    Screens(MainActivity host){a=host;store=a.store;calendar=new HolidayCalendar(a);}
    LinearLayout card(String title,String sub){LinearLayout c=a.card();a.heading(c,title,sub);return c;}
    TextView text(String s,int size,int color,boolean bold){return a.text(s,size,color,bold);}
    void go(int page){a.stashDraft();a.page=page;a.render();}
    void home(){
        LinearLayout pendingHost=new LinearLayout(a);pendingHost.setOrientation(1);pendingHost.setTag("pendingHost");a.body.addView(pendingHost,new LinearLayout.LayoutParams(-1,-2));pendingItems(pendingHost);
        LinearLayout attendance=card("今天的打卡","点击按钮会打开企业微信，同时记为已打卡并停止对应提醒，请在企业微信完成打卡。打开失败时不会记为已打卡。已确认的按钮连续双击可取消（半秒内），取消后重新监控，满一分钟后按 Wi-Fi 情况提醒。");JSONObject d=store.day();
        LinearLayout done=a.row(attendance);
        a.action(done,d.optBoolean("amDone")?"✅ 上班卡已确认":"去企微打上班卡",()->a.attendanceClick("am"),false).setTag("attendance_am");
        a.action(done,d.optBoolean("pmDone")?"✅ 下班卡已确认":"去企微打下班卡",()->a.attendanceClick("pm"),false).setTag("attendance_pm");
        LinearLayout debug=card("最近动态","这里会记录什么时候开始监控、什么时候提醒，以及需要你处理的问题。最新消息在上面，最多保留 100 条，可以上下滑动；红字表示需要留意。");debug.setBackground(a.bg(0xffe8f0f8,14));debug.setTag("debugCard");
        ScrollView scroll=new ScrollView(a);scroll.setTag("debugScroll");scroll.setNestedScrollingEnabled(true);debug.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout rows=new LinearLayout(a);rows.setOrientation(1);rows.setTag("debugRows");scroll.addView(rows);debug(rows,scroll);
    }
    private void pendingItems(LinearLayout parent){
        JSONObject pending=store.pending();Iterator<String> it=pending.keys();while(it.hasNext()){
            String key=it.next();JSONObject p=pending.optJSONObject(key);if(!AttendanceData.validPending(key,p))continue;
            LinearLayout item=a.card(parent);a.heading(item,store.displayTitle(key,p),"每分钟按 Wi-Fi 条件提醒，状态变化或确认后停止。");item.addView(text("下次检查 "+a.clock(p.optLong("nextAt")),12,MainActivity.MUTED,false));

        }
    }
    private void debug(LinearLayout parent,ScrollView scroll){
        int oldY=scroll.getScrollY();parent.removeAllViews();JSONArray lines=Store.array(store.prefs.getString("events","[]"));int count=Math.min(100,lines.length());
        if(count==0)parent.addView(text("还没有新动态，开始监控或去企微打卡后，会在这里告诉你。",13,MainActivity.MUTED,false));
        for(int i=0;i<count;i++){JSONObject line=lines.optJSONObject(i);if(line==null)continue;
            String stamp=Instant.ofEpochMilli(line.optLong("at")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
            TextView row=text(stamp+"\n"+FriendlyLog.message(line),13,line.optBoolean("error")?0xffb42318:MainActivity.INK,false);
            row.setPadding(a.dp(6),a.dp(8),a.dp(6),a.dp(8));row.setTextIsSelectable(true);parent.addView(row);
        }
        ViewGroup.LayoutParams lp=scroll.getLayoutParams();lp.height=a.dp(456);scroll.setLayoutParams(lp);
        if(oldY>0)scroll.post(()->scroll.scrollTo(0,oldY));
    }
    void refreshHome(boolean dataChanged){
        if(a.page!=0||a.root==null)return;
        if(dataChanged){
            JSONObject day=store.day();
            for(String w:new String[]{"am","pm"}){Button button=a.root.findViewWithTag("attendance_"+w);if(button==null)continue;
                String label=(day.optBoolean(w+"Done")?"✅ ":"去企微打")+(w.equals("am")?"上班卡":"下班卡")+(day.optBoolean(w+"Done")?"已确认":"");
                if(!label.contentEquals(button.getText())){button.setText(label);store.debug("界面","按钮已同步",(w.equals("am")?"上班":"下班")+"卡="+(day.optBoolean(w+"Done")?"已确认":"未确认"),false);}
            }
            LinearLayout pending=a.root.findViewWithTag("pendingHost");if(pending!=null){pending.removeAllViews();pendingItems(pending);}
        }
        LinearLayout rows=a.root.findViewWithTag("debugRows");ScrollView scroll=a.root.findViewWithTag("debugScroll");if(rows!=null&&scroll!=null)debug(rows,scroll);
    }

    void section(LinearLayout parent,String title,String group,String help){LinearLayout row=a.row(parent);row.addView(text(title,15,MainActivity.INK,true));a.help(row,title,help,true);}
    void enabled(boolean on){
        a.commitWifiOnExit();a.stashDraft();if(store.enabled()==on)return;
        if(!on)new AlarmReceiver().onReceive(a,new Intent("PAUSE"));
        else {store.enabled(true);Planner.rebuild(a);MonitorService.reconcile(a,true);store.debug("开关","已开启",Authorizations.ready(a)?"按工作日、时段及配置监控":"等待必要授权："+Authorizations.missing(a),false);}
        a.render();
    }
    void attendance(){
        JSONObject cfg=store.config();
        LinearLayout wifi=a.card();section(wifi,"公司 Wi-Fi","Wi-Fi","仅按名称匹配，同名接入点切换不影响连接判断。每 10 秒读取；必要授权不足时暂停监控，授权完成后 Wi-Fi 关闭或读取失败按未连接处理。无需密码，不获取定位或步数。");
        LinearLayout wr=a.row(wifi);EditText ssid=new EditText(a);ssid.setText(a.current("ssid"));ssid.setHint("公司 Wi-Fi 名称");ssid.setTextSize(14);ssid.setSingleLine(true);wr.addView(ssid,new LinearLayout.LayoutParams(0,a.dp(44),1.6f));a.fields.put("ssid",ssid);Button bind=a.action(wr,"绑定当前 Wi-Fi",a::captureWifi,false);bind.setTag("绑定当前 Wi-Fi");a.configBadges.put("Wi-Fi",bind);
        LinearLayout times=a.card();section(times,"提醒时段","时段","开始时间包含，结束时间不包含；两段不重叠。清空四个时间后立即暂停监控。取消确认会重新开始一分钟计时。");timeRow(times,"上班","amStart","amEnd",cfg);timeRow(times,"下班","pmStart","pmEnd",cfg);LinearLayout actions=a.row(times);a.button(actions,"重置",()->a.resetTimes(false),false);a.button(actions,"清空",()->a.resetTimes(true),false);
        a.autoSaveStatus=text("",11,MainActivity.MUTED,false);a.autoSaveStatus.setTag("autoSaveStatus");times.addView(a.autoSaveStatus);
        LinearLayout toggle=a.card();toggle.setTag("attendanceToggle");LinearLayout r=a.row(toggle);r.addView(text("打卡提醒总开关：",15,MainActivity.INK,true));a.help(r,"打卡提醒总开关","开启后仅在工作日、对应时段、必要授权完成且未确认时监控；关闭会立即停止监控和提醒。",true);r.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));r.addView(text("关",13,MainActivity.MUTED,false));Switch enabled=new Switch(a);enabled.setShowText(false);enabled.setText("");enabled.setChecked(store.enabled());enabled.setContentDescription("打卡提醒总开关");enabled.setTag("monitor_switch");r.addView(enabled);r.addView(text("开",13,MainActivity.MUTED,false));enabled.setOnCheckedChangeListener((button,on)->enabled(on));a.watchFields();
    }
    void timeRow(LinearLayout parent,String title,String start,String end,JSONObject cfg){LinearLayout row=a.row(parent);row.setBaselineAligned(false);row.setPadding(0,a.dp(4),0,0);TextView label=text(title,13,MainActivity.INK,true);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(0,0,0,0);label.setIncludeFontPadding(false);label.setTag("time_label_"+start);row.addView(label,new LinearLayout.LayoutParams(a.dp(46),a.dp(40)));for(String key:new String[]{start,end}){
        if(key.equals(end)){TextView dash=text("—",14,MainActivity.MUTED,false);dash.setGravity(Gravity.CENTER);row.addView(dash,new LinearLayout.LayoutParams(a.dp(28),-2));}
        EditText field=new EditText(a);field.setText(a.current(key));field.setHint("--:--");field.setSingleLine(true);field.setFocusable(false);field.setCursorVisible(false);field.setTextColor(MainActivity.GREEN);field.setTextSize(18);field.setGravity(Gravity.CENTER);field.setIncludeFontPadding(false);field.setBackground(a.bg(0xffeef4ef,8));field.setPadding(a.dp(6),0,a.dp(6),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(40),1);row.addView(field,lp);a.fields.put(key,field);field.setContentDescription(title+(key.equals(start)?"开始":"停止")+"时间");field.setOnClickListener(v->{a.commitWifiOnExit();a.root.requestFocus();a.pickTime(field.getText().toString(),value->a.selectedTime(key,value));});
    }}
    void calendar(){
        int year=LocalDate.now().getYear();LinearLayout sync=card("工作日历",HolidaySync.running()?"正在检查最新安排…":store.prefs.getString("holiday_status","每天自动检查，手动修改的日期优先。"));
        sync.addView(text(year+" · "+calendar.sourceLabel(year),12,MainActivity.GREEN,true));long last=store.prefs.getLong("holiday_checked",0);sync.addView(text(last==0?"尚未联网检查": "最近检查 "+a.clock(last),12,MainActivity.MUTED,false));
        LinearLayout syncActions=a.row(sync);a.button(syncActions,HolidaySync.running()?"正在同步…":"立即更新",a::syncCalendar,true);
        JSONObject data=calendar.cached(year);JSONArray papers=data.optJSONArray("papers");String paper=papers!=null&&papers.length()>0?papers.optString(0):Rules.HOLIDAY_SOURCE;
        if(HolidayCalendar.governmentUrl(paper))a.button(syncActions,"公告来源",()->a.open(new Intent(Intent.ACTION_VIEW,Uri.parse(paper))),false);
        if(!calendar.known(year))sync.addView(text("本年安排待取得：自动打卡暂停。你也可以明确设置某天是否上班。",13,0xff97552d,true));
        LinearLayout month=a.card();a.body.removeView(month);a.body.addView(month,0);LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);month.addView(head);
        Button prev=new Button(a);prev.setText("‹");head.addView(prev,new LinearLayout.LayoutParams(a.dp(48),a.dp(44)));TextView label=text(a.month.getYear()+"年 "+a.month.getMonthValue()+"月",19,MainActivity.INK,true);label.setGravity(Gravity.CENTER);head.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button next=new Button(a);next.setText("›");head.addView(next,new LinearLayout.LayoutParams(a.dp(48),a.dp(44)));
        for(Button b:new Button[]{prev,next}){b.setElevation(0);b.setStateListAnimator(null);b.setBackground(a.bg(0xffedf3ee,12));b.setTextColor(MainActivity.GREEN);b.setTextSize(23);}
        prev.setOnClickListener(v->{a.month=a.month.minusMonths(1);a.render();});next.setOnClickListener(v->{a.month=a.month.plusMonths(1);a.render();});
        LinearLayout weekdays=new LinearLayout(a);month.addView(weekdays);for(String day:new String[]{"一","二","三","四","五","六","日"}){TextView t=text(day,12,MainActivity.MUTED,false);t.setGravity(Gravity.CENTER);weekdays.addView(t,new LinearLayout.LayoutParams(0,a.dp(26),1));}
        int offset=a.month.atDay(1).getDayOfWeek().getValue()-1,days=a.month.lengthOfMonth();
        for(int row=0;row<(offset+days+6)/7;row++){LinearLayout line=new LinearLayout(a);line.setBaselineAligned(false);month.addView(line,new LinearLayout.LayoutParams(-1,a.dp(48)));for(int col=0;col<7;col++){int number=row*7+col-offset+1;TextView cell=text("",13,MainActivity.INK,false);cell.setGravity(Gravity.CENTER);cell.setPadding(0,a.dp(2),0,a.dp(2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(44),1);lp.setMargins(a.dp(1),a.dp(2),a.dp(1),a.dp(2));line.addView(cell,lp);if(number<1||number>days)continue;
            LocalDate date=a.month.atDay(number);boolean ready=calendar.ready(date),work=calendar.workday(date),custom=calendar.overrides().has(date.toString());String tag=custom?"自定":!ready?"待定":work?"班":"休";cell.setText(number+"\n"+tag);cell.setContentDescription(date.toString()+" "+tag);cell.setTextColor(!ready?MainActivity.MUTED:work?MainActivity.GREEN:0xff956044);if(date.equals(LocalDate.now())){cell.setBackground(a.bg(MainActivity.GREEN,13));cell.setTextColor(Color.WHITE);}else if(custom)cell.setBackground(a.bg(0xffe5eee8,13));cell.setOnClickListener(v->dayDialog(date));
        }}
        LinearLayout calendarActions=a.row(month);LinearLayout.LayoutParams spacing=(LinearLayout.LayoutParams)calendarActions.getLayoutParams();spacing.topMargin=a.dp(10);calendarActions.setLayoutParams(spacing);a.button(calendarActions,"回到本月",()->{a.month=YearMonth.now();a.render();},false);a.help(calendarActions,"日历标记","班：上班；休：休息；自定：你的修改；待定：尚无可靠安排。点日期可调整，自动同步不会覆盖自定日期。");
    }
    void dayDialog(LocalDate date){String state=!calendar.ready(date)?"待同步":calendar.workday(date)?"上班":"休息";String name=calendar.name(date);new AlertDialog.Builder(a).setTitle(date.format(DateTimeFormatter.ofPattern("M月d日 E",Locale.CHINA))+" · "+state).setItems(new String[]{"设为上班","设为休息","恢复自动安排"},(d,w)->a.changeDay(date,w==0?Boolean.TRUE:w==1?Boolean.FALSE:null)).setNegativeButton("取消",null).show();}
    static final class NavIcon extends View {
        final int index,color;final Paint p=new Paint(3);
        NavIcon(Context c,int index,int color){super(c);this.index=index;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(index==0){Path path=new Path();path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(19,21);path.lineTo(19,9);path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);c.drawPath(path,p);}
            else if(index==4){Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(9,18,4,13,4,9);pin.cubicTo(4,-1,20,-1,20,9);pin.cubicTo(20,13,15,18,12,22);c.drawPath(pin,p);c.drawCircle(12,9,3,p);}
            else if(index==2){c.drawRoundRect(3,5,21,21,3,3,p);c.drawLine(3,10,21,10,p);c.drawLine(8,2,8,7,p);c.drawLine(16,2,16,7,p);c.drawLine(8,14,10,14,p);c.drawLine(14,14,16,14,p);c.drawLine(8,18,10,18,p);}
            else{c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double r=i*Math.PI/4;c.drawLine(12+(float)Math.cos(r)*8,12+(float)Math.sin(r)*8,12+(float)Math.cos(r)*10,12+(float)Math.sin(r)*10,p);}}
            c.restore();
        }
    }
}
