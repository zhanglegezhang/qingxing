package cn.qingxing.reminder;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;

/** Real notification PendingIntents update visible views without navigation. */
public final class HomeSyncTests extends Instrumentation {
    Context c;MainActivity a;int checks;boolean preview;
    void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    void main(Runnable r){Throwable[] e={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    Notification notice(String key){for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==Alerts.id(key))return n.getNotification();return null;}
    Notification waitNotice(String key)throws Exception{long end=SystemClock.elapsedRealtime()+3000;Notification n;while((n=notice(key))==null&&SystemClock.elapsedRealtime()<end)Thread.sleep(40);check(n!=null,"notification posted: "+key);return n;}
    void settle()throws Exception{waitForIdleSync();Thread.sleep(120);waitForIdleSync();}
    boolean roundCheck(CharSequence text){return text instanceof android.text.Spanned&&((android.text.Spanned)text).getSpans(0,text.length(),android.text.style.ReplacementSpan.class).length>0;}
    void checkMarkRendering(){
        String plain="去企微打上班卡",confirmed="✅ 上班卡已确认";
        check(CheckMark.styled(c,plain)==plain,"unmarked labels remain unchanged");
        CharSequence styled=CheckMark.styled(c,confirmed);check(confirmed.contentEquals(styled),"check styling retains accessible confirmation text");
        check(roundCheck(styled),"confirmed text uses native round check instead of font emoji");
        check(!roundCheck(CheckMark.styled(c,"❌ 未完成")),"unconfirmed cross remains unchanged");
        android.text.style.ReplacementSpan span=((android.text.Spanned)styled).getSpans(0,styled.length(),android.text.style.ReplacementSpan.class)[0];
        android.graphics.Paint paint=new android.graphics.Paint(3);paint.setTextSize(26);int size=span.getSize(paint,styled,0,1,null);
        check(size==Math.round(20*c.getResources().getDisplayMetrics().density),"check uses reviewed 20dp slot");
        for(int color:new int[]{MainActivity.GREEN,android.graphics.Color.WHITE}){
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
            CheckMark mark=new CheckMark(color);mark.setBounds(0,0,48,48);mark.draw(canvas);
            check(android.graphics.Color.alpha(bitmap.getPixel(3,3))==0&&android.graphics.Color.alpha(bitmap.getPixel(24,8))==0,"round check has no square background "+color);
            check(bitmap.getPixel(18,33)==color&&bitmap.getPixel(39,13)==color,"check path and stroke render in requested color "+color);
            bitmap.eraseColor(0);mark.setAlpha(100);mark.draw(canvas);check(android.graphics.Color.alpha(bitmap.getPixel(18,33))==100,"drawable alpha respected "+color);bitmap.recycle();
            android.graphics.Bitmap inline=android.graphics.Bitmap.createBitmap(size+4,size+12,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas inlineCanvas=new android.graphics.Canvas(inline);paint.setColor(color);paint.setAlpha(255);
            span.draw(inlineCanvas,styled,0,1,2,0,size/2+10,size+12,paint);boolean colored=false,wrong=false;
            for(int y=0;y<inline.getHeight();y++)for(int x=0;x<inline.getWidth();x++){int pixel=inline.getPixel(x,y);if(android.graphics.Color.alpha(pixel)==255){colored=true;if((pixel&0xffffff)!=(color&0xffffff))wrong=true;}}
            check(colored&&!wrong,"inline check inherits surrounding text color "+color);inline.recycle();
        }
    }
    void shot(String name)throws Exception{android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"rendered screenshot available "+name);try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();}
    @Override public void onCreate(Bundle args){super.onCreate(args);preview=args!=null&&"true".equals(args.getString("preview"));start();}
    @Override public void onStart(){Bundle result=new Bundle();c=getTargetContext();try{
        checkMarkRendering();
        main(()->{Store s=new Store(c);s.enabled(false);c.stopService(new Intent(c,MonitorService.class));Planner.reset(c);FlowTests.window(s,"pm");});
        a=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();View root=a.root;
        main(()->{Store s=new Store(c);FlowTests.ready(s);FlowTests.sample(c,false);});PendingIntent old=waitNotice("pm").actions[0].actionIntent;old.send();settle();
        main(()->{Store s=new Store(c);check(s.day().optBoolean("pmDone")&&!s.day().optBoolean("amDone"),"notification confirms only PM");check("✅ 下班卡已确认".contentEquals(((Button)a.root.findViewWithTag("attendance_pm")).getText()),"home updates without navigation");check(roundCheck(((Button)a.root.findViewWithTag("attendance_pm")).getText()),"live notification confirmation keeps round check rendering");check(root==a.root,"live update preserves root");check(((LinearLayout)a.root.findViewWithTag("pendingHost")).getChildCount()==0,"pending card removed");check(s.pending().length()==0,"confirmation removes pending");check(s.prefs.getString("events","").contains("收到打卡确认")&&s.prefs.getString("events","").contains("按钮已同步"),"notification debug trace visible");
            JSONObject d=s.day();Store.put(d,"pmDone",false);s.day(d);Planner.reset(c);FlowTests.ready(s);FlowTests.sample(c,false);
        });settle();old.send();settle();check(!new Store(c).day().optBoolean("pmDone"),"old notification rejected for new round");
        main(()->{a.page=3;a.render();a.fields.get("ssid").setText("保留的未保存内容");});waitNotice("pm").actions[0].actionIntent.send();settle();main(()->{check("保留的未保存内容".contentEquals(a.fields.get("ssid").getText()),"confirmation preserves unsaved draft");a.stashDraft();a.page=0;a.render();check("✅ 下班卡已确认".contentEquals(((Button)a.root.findViewWithTag("attendance_pm")).getText()),"return home shows confirmation");});
        String before=new Store(c).prefs.getString("day","");main(()->a.launchWeCom(null));check(before.equals(new Store(c).prefs.getString("day","")),"failed WeCom launch leaves attendance unchanged");check(new Store(c).prefs.getString("events","").contains("未安装企业微信"),"missing WeCom diagnosed");
        main(()->{Store s=new Store(c);s.day(new JSONObject());a.page=0;a.render();((Button)a.root.findViewWithTag("attendance_am")).performClick();check(!s.day().optBoolean("amDone"),"actual missing-app button click does not confirm");check("去企微打上班卡".contentEquals(((Button)a.root.findViewWithTag("attendance_am")).getText()),"new AM label shown");
            a.attendanceLaunch("am",new Intent(Intent.ACTION_MAIN).setClassName("invalid.missing","invalid.missing.Main"));check(!s.day().optBoolean("amDone"),"unresolvable intent does not confirm");});
        IntentFilter weCom=new IntentFilter(Intent.ACTION_MAIN);weCom.addCategory(Intent.CATEGORY_LAUNCHER);ActivityMonitor monitor=addMonitor(weCom,new ActivityResult(Activity.RESULT_CANCELED,null),true);
        try{for(String key:new String[]{"am","pm"}){
            main(()->{Store s=new Store(c);FlowTests.window(s,"pm");if(key.equals("pm")){FlowTests.ready(s);FlowTests.sample(c,false);}a.attendanceLaunch(key,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.tencent.wework"));check(s.day().optBoolean(key+"Done"),"successful launch confirms "+key);check(!s.day().optBoolean((key.equals("am")?"pm":"am")+"Done"),"other attendance unchanged "+key);check(("✅ "+(key.equals("am")?"上班":"下班")+"卡已确认").contentEquals(((Button)a.root.findViewWithTag("attendance_"+key)).getText()),"successful launch updates home "+key);if(key.equals("pm"))check(s.pending().length()==0,"launch clears active reminder");});
        }check(monitor.getHits()==2,"both attendance paths request Enterprise WeChat");}finally{removeMonitor(monitor);}
        main(()->{check(a.root.findViewWithTag("debugScroll").getLayoutParams().height==a.dp(456),"log viewport is three times previous height");
            JSONObject raw=new JSONObject();Store.put(raw,"module","监控通知");Store.put(raw,"event","检查后已提交");Store.put(raw,"detail","monitor_quiet_v2;声震请求=false");check(!FriendlyLog.message(raw).contains("false")&&!FriendlyLog.message(raw).contains("monitor_quiet"),"raw notification identifiers hidden");
            Store.put(raw,"module","检查");Store.put(raw,"detail","上班；符合提醒条件=true；本轮开始=1234567890000");check(FriendlyLog.message(raw).contains("上班卡")&&!FriendlyLog.message(raw).contains("123456"),"Wi-Fi decision explained without internal timestamps");
            Store.put(raw,"module","动态");Store.put(raw,"event","系统事件：android.intent.action.BOOT_COMPLETED");check(FriendlyLog.message(raw).contains("手机刚刚启动")&&!FriendlyLog.message(raw).contains("android.intent"),"old system events translated");
            Store.put(raw,"module","声震");Store.put(raw,"event","开始 5 秒提醒");check(FriendlyLog.message(raw).contains("5 秒"),"five second reminder explained");Store.put(raw,"event","开始 3 秒提醒");check(FriendlyLog.message(raw).contains("3 秒"),"old reminder duration stays accurate");Store.put(raw,"module","企微");Store.put(raw,"event","打开失败");check(FriendlyLog.message(raw).contains("没有记为已打卡"),"failure explanation retains meaning");});
        main(()->{Store s=new Store(c);for(int i=0;i<105;i++)s.debug("测试","记录 "+i,"本地诊断",i==104);check(Store.array(s.prefs.getString("events","[]")).length()==100,"unified log bounded");s.debugLimited("重复","相同事件","相同内容",true);s.debugLimited("重复","相同事件","相同内容",true);org.json.JSONArray logs=Store.array(s.prefs.getString("events","[]"));check(logs.optJSONObject(0).optString("event").equals(logs.optJSONObject(1).optString("event")),"identical events are not deduplicated");});settle();main(()->{LinearLayout rows=a.root.findViewWithTag("debugRows");check(rows.getChildCount()==100,"all one hundred entries accessible");boolean highlighted=false;for(int i=0;i<rows.getChildCount();i++)if(((TextView)rows.getChildAt(i)).getCurrentTextColor()==0xffb42318)highlighted=true;check(highlighted,"failure records highlighted even when newer normal events arrive");});
        main(()->{a.page=0;a.render();});settle();shot("qa-check-home.png");
        main(()->{Store s=new Store(c);s.prefs.edit().putBoolean("autostartConfirmed",true).commit();a.page=3;a.render();check(roundCheck(((TextView)a.root.findViewWithTag("auth_precise")).getText()),"actual allowed authorization button has round check");a.wifiCaptured("TestOffice");check(a.configBadges.get("Wi-Fi").getCompoundDrawables()[0] instanceof CheckMark,"bound Wi-Fi uses same native check");
            Button primary=a.action(a.body,"✅ 已完成",()->{},true);check(primary.getCurrentTextColor()==android.graphics.Color.WHITE&&roundCheck(primary.getText()),"deep button uses white round check");a.body.removeView(primary);
        });settle();shot("qa-check-authorizations.png");main(()->a.contentScroll.smoothScrollTo(0,a.dp(240)));Thread.sleep(400);waitForIdleSync();shot("qa-check-binding.png");
        result.putString("stream","PASS "+checks+" notification/home/debug checks");finish(-1,result);
    }catch(Throwable t){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(0,result);}finally{main(()->{new Store(c).enabled(false);c.stopService(new Intent(c,MonitorService.class));Planner.reset(c);Planner.rebuild(c);if(a!=null)a.finish();});}}
}
