package cn.qingxing.reminder;
import android.app.job.*;
public final class HolidaySyncJob extends JobService {
    private int generation;
    @Override public boolean onStartJob(JobParameters p){int current=++generation;HolidaySync.start(this,false,()->{if(current==generation)jobFinished(p,false);});return true;}
    @Override public boolean onStopJob(JobParameters p){generation++;return true;}
}
