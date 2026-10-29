package cn.qingxing.reminder;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.graphics.Rect;

/** User-session access, real blank-area taps, restoration and notification entry. */
public final class HiddenLocationTests extends Instrumentation {
    Context c;MainActivity a;int n;
    void check(boolean ok,String message){n++;if(!ok)throw new AssertionError(message);}
    void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    int tabs(){return ((LinearLayout)a.root.findViewWithTag("appNavigation")).getChildCount();}
    void taps(int count){View blank=a.root.findViewWithTag("version_unlock");for(int i=0;i<count;i++)blank.performClick();}
    MainActivity launch(){return (MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    View desc(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=desc(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle out=new Bundle();c=getTargetContext();try{
        main(()->{Store s=new Store(c);s.enabled(false);s.prefs.edit().putBoolean("firstGuidePending",false).commit();c.stopService(new Intent(c,MonitorService.class));MockLocationService.stop(c);Planner.reset(c);});
        a=launch();waitForIdleSync();String config=new Store(c).config().toString();
        main(()->{check(!a.locationUnlocked&&tabs()==3,"fresh session hides location");a.page=4;a.render();check(a.page==3&&a.locationPage.map==null,"hidden page cannot be restored or opened directly");View blank=a.root.findViewWithTag("version_unlock");View label=a.root.findViewWithTag("version_label");check(blank!=null&&blank!=label,"separate blank area and version text");taps(9);check(!a.locationUnlocked&&a.versionTapCount==9,"nine clicks stay hidden");});
        Thread.sleep(2100);
        main(()->{taps(1);check(!a.locationUnlocked&&a.versionTapCount==1,"more than two seconds resets count");View label=a.root.findViewWithTag("version_label");for(int i=0;i<10;i++)label.performClick();check(!a.locationUnlocked&&a.versionTapCount==0,"version text never unlocks");taps(4);desc(a.root,"今天").performClick();desc(a.root,"设置").performClick();check(a.versionTapCount==0,"navigation clears partial sequence");a.contentScroll.fullScroll(View.FOCUS_DOWN);});
        waitForIdleSync();main(()->a.contentScroll.fullScroll(View.FOCUS_DOWN));waitForIdleSync();Thread.sleep(250);Rect bounds=new Rect();
        main(()->{View blank=a.root.findViewWithTag("version_unlock"),label=a.root.findViewWithTag("version_label");check(blank.getGlobalVisibleRect(bounds)&&bounds.width()>a.dp(120)&&bounds.height()>=a.dp(36),"blank target is visible and wide enough: "+bounds+" vs "+a.dp(36));int[] p=new int[2];label.getLocationOnScreen(p);check(bounds.left>=p[0]+label.getWidth(),"blank does not cover version text");});
        for(int i=0;i<10;i++){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true),"real blank touch "+i);down.recycle();up.recycle();Thread.sleep(80);}
        waitForIdleSync();
        main(()->{check(a.locationUnlocked&&tabs()==4&&a.page==3,"tenth real blank click reveals tab without jumping pages");check(desc(a.root,"定位")!=null,"location navigation exists after unlock");desc(a.root,"定位").performClick();check(a.page==4,"revealed location tab works");a.page=3;a.render();a.moveTaskToBack(true);});Thread.sleep(250);
        main(()->a.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));waitForIdleSync();Thread.sleep(300);
        main(()->check(a.locationUnlocked&&tabs()==4,"background and return keep session access"));
        ActivityMonitor recreation=addMonitor(MainActivity.class.getName(),null,false);main(a::recreate);MainActivity rebuilt=(MainActivity)waitForMonitorWithTimeout(recreation,5000);removeMonitor(recreation);check(rebuilt!=null,"activity recreation completed");a=rebuilt;waitForIdleSync();
        main(()->{check(a.locationUnlocked&&tabs()==4,"same-process recreation keeps unlocked session");Bundle state=new Bundle();a.onSaveInstanceState(state);state.putString("uiSession","previous-process");a.restoreLocationAccess(state);a.page=4;a.render();check(!a.locationUnlocked&&tabs()==3&&a.page==3,"saved state from another process cannot reopen location");taps(10);check(a.locationUnlocked,"can unlock again after stale state rejection");a.finish();});waitForIdleSync();Thread.sleep(200);
        a=launch();waitForIdleSync();
        main(()->{check(!a.locationUnlocked&&tabs()==3,"closing and reopening starts hidden");a.onNewIntent(new Intent(c,MainActivity.class).putExtra("locationPage",true));check(!a.locationUnlocked&&tabs()==3&&a.page==3,"notification open cannot bypass ten-click gate");check(!MockLocationService.active&&a.locationPage.map==null,"hidden session starts no location or simulation");check(config.equals(new Store(c).config().toString()),"access gesture does not change attendance configuration");a.finish();});
        out.putString("stream","PASS "+n+" hidden location access checks");finish(-1,out);
    }catch(Throwable t){out.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));finish(0,out);}finally{if(a!=null)main(()->{if(!a.isFinishing())a.finish();});}}
}
