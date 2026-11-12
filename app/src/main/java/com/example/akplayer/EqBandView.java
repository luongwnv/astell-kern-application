package com.example.akplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** One real device EQ band, with gain in Android's native millibels. */
public class EqBandView extends View {
    public interface Listener { short onGainChanged(short gain); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int frequency; private final short min, max; private short gain; private final Listener listener; private boolean available;
    public EqBandView(Context context,int frequency,short min,short max,short gain,boolean available,Listener listener){super(context);this.frequency=frequency;this.min=min;this.max=max;this.gain=gain;this.available=available;this.listener=listener;setContentDescription(String.format(Locale.US,"%d Hz, %.1f dB",frequency,gain/100f));}
    private float yForGain(){return top()+(max-gain)*(bottom()-top())/(float)(max-min);}
    private float top(){return getHeight()*.12f;} private float bottom(){return getHeight()*.86f;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,d=getResources().getDisplayMetrics().density,y=yForGain();paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(11*d);paint.setColor(0xffc6d7dd);c.drawText(String.format(Locale.US,"%.1f",gain/100f),cx,top()-8*d,paint);
        paint.setStrokeWidth(0.5f*d);paint.setColor(0xff273137);for(int i=0;i<=10;i++){float gridY=top()+(bottom()-top())*i/10f;c.drawLine(0,gridY,getWidth(),gridY,paint);}paint.setStrokeWidth(d);paint.setColor(0xff4e646b);c.drawLine(cx,top(),cx,bottom(),paint);paint.setColor(available?0xffed375b:0xff7e4d59);paint.setStrokeWidth(1.5f*d);c.drawLine(cx,y,cx,bottom(),paint);c.drawRect(cx-5*d,y-8*d,cx+5*d,y+8*d,paint);
        paint.setColor(0xffb9c9ce);paint.setTextSize(10*d);String f=frequency>=1000?(frequency%1000==0?String.format(Locale.US,"%dk",frequency/1000):String.format(Locale.US,"%.1fk",frequency/1000f)):Integer.toString(frequency);c.drawText(f,cx,bottom()+17*d,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(!available||max<=min)return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:getParent().requestDisallowInterceptTouchEvent(true);case MotionEvent.ACTION_MOVE:float f=Math.max(0f,Math.min(1f,(e.getY()-top())/(bottom()-top())));int mb=Math.round((max-f*(max-min))/10f)*10;gain=(short)Math.max(min,Math.min(max,mb));gain=listener.onGainChanged(gain);setContentDescription(String.format(Locale.US,"%d Hz, %.1f dB",frequency,gain/100f));invalidate();return true;case MotionEvent.ACTION_UP:getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;default:return false;}}
    @Override public boolean performClick(){super.performClick();return true;}
}
