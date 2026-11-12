package com.example.akplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Simple line and transport icons matching the player's monochrome controls. */
public class PlayerIcon extends View {
    private String icon; private float iconScale=1f; private final int color; private final Paint p=new Paint(3);
    public PlayerIcon(Context c,String icon,int color,Runnable action,String description){super(c);this.icon=icon;this.color=color;setContentDescription(description);setOnClickListener(v->action.run());}
    public void setIconScale(float value){iconScale=value;invalidate();}
    public void setIcon(String value){if(!icon.equals(value)){icon=value;invalidate();}}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth(),getHeight())/48f*iconScale;canvas.scale(scale,scale);p.setColor(color);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.SQUARE);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        Path path=new Path();
        switch(icon){
            case "hamburger":p.setStrokeWidth(2.3f);p.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(-11,-8,11,-8,p);canvas.drawLine(-11,0,11,0,p);canvas.drawLine(-11,8,11,8,p);break;
            case "search":p.setStrokeWidth(2.3f);p.setStrokeCap(Paint.Cap.ROUND);canvas.drawCircle(-2,-2,9,p);canvas.drawLine(4.5f,4.5f,11,11,p);break;
            case "refresh":p.setStrokeWidth(2.3f);p.setStrokeCap(Paint.Cap.ROUND);canvas.drawArc(-10,-10,10,10,-45,290,false,p);path.moveTo(2,-10);path.lineTo(10,-10);path.lineTo(10,-2);canvas.drawPath(path,p);break;
            case "next_small":p.setStrokeWidth(1.8f);path.moveTo(-4,-7);path.lineTo(3,0);path.lineTo(-4,7);canvas.drawPath(path,p);break;
            case "down":p.setStrokeWidth(1.8f);path.moveTo(-7,-3);path.lineTo(0,4);path.lineTo(7,-3);canvas.drawPath(path,p);break;
            case "check":p.setColor(0xff9ccad4);p.setStrokeWidth(2f);path.moveTo(-9,0);path.lineTo(-3,6);path.lineTo(10,-8);canvas.drawPath(path,p);break;
            case "home":path.moveTo(-11,-1);path.lineTo(0,-10);path.lineTo(11,-1);path.lineTo(11,10);path.lineTo(-11,10);path.close();canvas.drawPath(path,p);break;
            case "playing":path.moveTo(-11,-8);path.lineTo(7,-8);path.lineTo(11,8);path.lineTo(-7,8);path.close();canvas.drawPath(path,p);canvas.drawLine(0,-4,-3,4,p);canvas.drawLine(-3,4,2,4,p);break;
            case "menu":for(int x=-1;x<=1;x+=2)for(int y=-1;y<=1;y+=2)canvas.drawCircle(x*5,y*5,3.3f,p);break;
            case "back":canvas.drawLine(5,-10,-5,0,p);canvas.drawLine(-5,0,5,10,p);break;
            case "wordmarkA":
                p.setStyle(Paint.Style.FILL);path.setFillType(Path.FillType.EVEN_ODD);path.moveTo(-21,17);path.lineTo(-3,-18);path.lineTo(2,-18);path.lineTo(19,17);path.lineTo(14,17);path.lineTo(9,7);path.lineTo(-9,7);path.close();path.moveTo(-6,2);path.lineTo(6,2);path.lineTo(0,-11);path.close();canvas.drawPath(path,p);break;
            case "logo": path.moveTo(-9,12);path.lineTo(1,-13);path.lineTo(10,12);path.moveTo(-6,5);path.lineTo(5,0);canvas.drawPath(path,p);break;
            case "play":p.setStyle(Paint.Style.FILL);path.moveTo(-7,-12);path.lineTo(11,0);path.lineTo(-7,12);path.close();canvas.drawPath(path,p);break;
            case "pause":p.setStyle(Paint.Style.FILL);canvas.drawRect(-8,-11,-3,11,p);canvas.drawRect(3,-11,8,11,p);break;
            case "previous":case "next":boolean prev=icon.equals("previous");canvas.scale(prev?-1:1,1);p.setStyle(Paint.Style.FILL);path.moveTo(-8,-9);path.lineTo(5,0);path.lineTo(-8,9);path.close();canvas.drawPath(path,p);canvas.drawRect(7,-9,9,9,p);break;
            case "queue":canvas.drawLine(-11,-8,9,-8,p);canvas.drawLine(-11,-2,9,-2,p);canvas.drawLine(-11,4,4,4,p);p.setStyle(Paint.Style.FILL);path.moveTo(7,2);path.lineTo(12,6);path.lineTo(7,10);path.close();canvas.drawPath(path,p);break;
            case "star":for(int i=0;i<10;i++){double a=Math.PI*i/5-Math.PI/2;float radius=i%2==0?11:5.5f;float x=(float)Math.cos(a)*radius,y=(float)Math.sin(a)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();canvas.drawPath(path,p);break;
            case "plus":canvas.drawLine(-10,0,10,0,p);canvas.drawLine(0,-10,0,10,p);break;
            case "repeat":canvas.drawLine(-9,-5,8,-5,p);canvas.drawLine(8,-5,5,-8,p);canvas.drawLine(8,-5,5,-2,p);canvas.drawLine(9,5,-8,5,p);canvas.drawLine(-8,5,-5,2,p);canvas.drawLine(-8,5,-5,8,p);break;
            case "shuffle":
                p.setStrokeWidth(2f);p.setStrokeCap(Paint.Cap.ROUND);path.moveTo(-10,-7);path.lineTo(-7,-7);path.cubicTo(-2,-7,1,7,7,7);path.lineTo(10,7);canvas.drawPath(path,p);
                path.reset();path.moveTo(6,3);path.lineTo(10,7);path.lineTo(6,11);canvas.drawPath(path,p);
                path.reset();path.moveTo(-10,7);path.lineTo(-7,7);path.cubicTo(-5,7,-3,5,-2,3);path.moveTo(2,-3);path.cubicTo(4,-6,5,-7,7,-7);path.lineTo(10,-7);canvas.drawPath(path,p);
                path.reset();path.moveTo(6,-11);path.lineTo(10,-7);path.lineTo(6,-3);canvas.drawPath(path,p);break;
            case "expand":canvas.drawRect(-9,-9,9,9,p);canvas.drawLine(-6,-6,4,4,p);canvas.drawLine(4,4,4,-1,p);canvas.drawLine(4,4,-1,4,p);break;
            case "more":p.setStyle(Paint.Style.FILL);canvas.drawCircle(-7,0,1.5f,p);canvas.drawCircle(0,0,1.5f,p);canvas.drawCircle(7,0,1.5f,p);break;
        }canvas.restore();}
}
