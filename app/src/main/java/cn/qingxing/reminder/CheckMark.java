package cn.qingxing.reminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/** Shared round-ended check. Labels retain their original text for accessibility. */
final class CheckMark extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    CheckMark(int color){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        path.moveTo(20,6);path.lineTo(9,17);path.lineTo(4,12);
    }
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();if(bounds.isEmpty())return;
        int save=canvas.save();float size=Math.min(bounds.width(),bounds.height());
        canvas.translate(bounds.exactCenterX()-size/2,bounds.exactCenterY()-size/2);
        canvas.scale(size/24f,size/24f);canvas.drawPath(path,paint);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    static CharSequence styled(Context context,CharSequence label){
        if(label==null||label.toString().indexOf('✅')<0)return label;
        SpannableString text=new SpannableString(label);int size=Math.round(20*context.getResources().getDisplayMetrics().density);
        for(int i=0;i<text.length();i++)if(text.charAt(i)=='✅')text.setSpan(new Inline(size),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
    private static final class Inline extends ReplacementSpan {
        final int size;final CheckMark mark=new CheckMark(0);
        Inline(int size){this.size=size;mark.setBounds(0,0,size,size);}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){return size;}
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
            Paint.FontMetrics metrics=paint.getFontMetrics();float center=baseline+(metrics.ascent+metrics.descent)/2;
            mark.paint.setColor(paint.getColor());mark.setAlpha(paint.getAlpha());
            int save=canvas.save();canvas.translate(x,center-size/2f);mark.draw(canvas);canvas.restoreToCount(save);
        }
    }
}
