package com.independentstudio.deadzone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.MotionEvent;
import android.view.View;

public final class IntroView extends View {
    public interface Listener { void onStartGame(); }
    private final Paint p = new Paint(3);
    private final Listener listener;
    private final long started = System.currentTimeMillis();
    private final AudioTrack music;
    private boolean finished;

    public IntroView(Context context, Listener listener) {
        super(context); this.listener = listener; setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        music = createEpicMusic(); music.setLoopPoints(0, music.getBufferSizeInFrames(), -1); music.play();
    }

    @Override protected void onDraw(Canvas c) {
        float w=getWidth(),h=getHeight(),t=(System.currentTimeMillis()-started)/1000f;
        p.setShader(new LinearGradient(0,0,0,h,Color.rgb(3,8,16),Color.rgb(38,5,8),Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        // Moon, fog and ruined city silhouette.
        p.setColor(Color.rgb(210,220,214));p.setShadowLayer(38,0,0,Color.argb(150,210,225,220));c.drawCircle(w*.79f,h*.22f,h*.105f,p);p.clearShadowLayer();
        p.setColor(Color.rgb(7,10,14));for(int i=0;i<13;i++){float bw=w/12f+((i%3)*8),bh=h*(.12f+(i%5)*.035f),x=i*w/12f-10;c.drawRect(x,h*.66f-bh,x+bw,h*.66f,p);for(int y=0;y<3;y++){p.setColor(Color.rgb(80,27,18));c.drawRect(x+12+y*18,h*.66f-bh+18,x+18+y*18,h*.66f-bh+27,p);}p.setColor(Color.rgb(7,10,14));}
        // Animated zombie silhouettes rising from the fog.
        for(int i=0;i<6;i++){float x=w*(.08f+i*.17f),rise=Math.min(1,Math.max(0,t*.5f-i*.10f)),y=h*(.79f-.08f*rise),s=h*(.055f+(i%2)*.012f);p.setColor(Color.rgb(5,8,8));c.drawCircle(x,y-s*1.75f,s*.36f,p);c.drawOval(x-s*.34f,y-s*1.42f,x+s*.34f,y,p);p.setStrokeWidth(s*.18f);c.drawLine(x-s*.23f,y-s*1.18f,x-s*.70f,y-s*.55f,p);c.drawLine(x+s*.23f,y-s*1.18f,x+s*.72f,y-s*.68f,p);}
        float pulse=.92f+.08f*(float)Math.sin(t*2.3f);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));p.setTextSize(h*.135f*pulse);p.setColor(Color.rgb(212,28,36));p.setShadowLayer(24,0,5,Color.BLACK);c.drawText("DEAD ZONE",w/2,h*.42f,p);p.clearShadowLayer();
        p.setTextSize(h*.030f);p.setColor(Color.rgb(205,211,216));p.setLetterSpacing(.18f);c.drawText("MOBILE  •  SURVIVE THE NIGHT",w/2,h*.49f,p);p.setLetterSpacing(0);
        float alpha=.55f+.45f*(float)Math.sin(t*3);p.setColor(Color.argb((int)(alpha*255),185,24,32));c.drawRoundRect(w*.34f,h*.73f,w*.66f,h*.84f,18,18,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.WHITE);c.drawRoundRect(w*.34f,h*.73f,w*.66f,h*.84f,18,18,p);p.setStyle(Paint.Style.FILL);p.setTextSize(h*.033f);c.drawText("TAP TO SURVIVE",w/2,h*.80f,p);
        p.setTextSize(h*.018f);p.setColor(Color.LTGRAY);c.drawText("Headphones recommended",w/2,h*.92f,p);postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP&&!finished){finished=true;try{music.stop();music.release();}catch(Exception ignored){};listener.onStartGame();}return true;}

    private static AudioTrack createEpicMusic(){int rate=22050,seconds=12,n=rate*seconds;short[] pcm=new short[n];int[] notes={55,65,73,82,49,58,65,73};for(int i=0;i<n;i++){float time=i/(float)rate,beat=time*2.0f,bar=beat/4f;int chord=((int)bar)%4,root=notes[chord];float bass=(float)Math.sin(6.28318*root*time)*.30f;float fifth=(float)Math.sin(6.28318*root*1.5f*time)*.15f;int step=((int)(beat*2))%8;float arp=(float)Math.sin(6.28318*notes[step]*4*time)*.13f;float kickPhase=(beat-(float)Math.floor(beat));float kick=(float)Math.sin(6.28318*(70-45*kickPhase)*time)*(float)Math.exp(-kickPhase*12)*.40f;float rise=Math.min(1,time/2.5f),v=(bass+fifth+arp+kick)*rise;pcm[i]=(short)(Math.max(-1,Math.min(1,v))*25000);}AudioTrack a=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(n*2).setTransferMode(AudioTrack.MODE_STATIC).build();a.write(pcm,0,n);return a;}
}
