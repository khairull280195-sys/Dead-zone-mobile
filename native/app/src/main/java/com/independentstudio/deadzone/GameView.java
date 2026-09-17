package com.independentstudio.deadzone;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class GameView extends View {
    final Paint p = new Paint(3); final Random random = new Random();
    float px, py, hp, aimX=1, aimY=0, spawnClock, shotClock, reloadClock;
    int ammo, reserve, kills, wave, left, moveId=-1, aimId=-1;
    float moveOX,moveOY,moveX,moveY,aimOX,aimOY,aimPX,aimPY;
    boolean firing, over, won; long last;
    final ArrayList<Zombie> zombies=new ArrayList<>(); final ArrayList<Bullet> bullets=new ArrayList<>();
    static class Zombie { float x,y,hp,speed,hit; }
    static class Bullet { float x,y,dx,dy,life; }

    public GameView(Context c){ super(c); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); setFocusable(true); reset(); }
    void reset(){px=640;py=360;hp=100;ammo=30;reserve=120;kills=0;wave=1;left=6;spawnClock=.5f;reloadClock=0;over=false;won=false;zombies.clear();bullets.clear();last=System.nanoTime();invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float sx=getWidth()/1280f,sy=getHeight()/720f;c.save();c.scale(sx,sy);tick();drawGame(c);c.restore();postInvalidateOnAnimation();}
    void tick(){long now=System.nanoTime();float dt=Math.min(.033f,(now-last)/1_000_000_000f);last=now;if(over)return;shotClock=Math.max(0,shotClock-dt);
        if(reloadClock>0&&(reloadClock-=dt)<=0){int n=Math.min(30-ammo,reserve);ammo+=n;reserve-=n;}
        float mx=0,my=0;if(moveId>=0){mx=(moveX-moveOX)/75;my=(moveY-moveOY)/75;float l=(float)Math.hypot(mx,my);if(l>1){mx/=l;my/=l;}}px=Math.max(35,Math.min(1245,px+mx*245*dt));py=Math.max(65,Math.min(685,py+my*245*dt));
        if(firing&&shotClock<=0)shoot();spawnClock-=dt;if(left>0&&spawnClock<=0){spawn();left--;spawnClock=.55f;}else if(left==0&&zombies.isEmpty()){wave++;left=4+wave*2;spawnClock=2.5f;}
        for(Zombie z:zombies){float dx=px-z.x,dy=py-z.y,l=(float)Math.hypot(dx,dy);if(l>28){z.x+=dx/l*z.speed*dt;z.y+=dy/l*z.speed*dt;}else if((z.hit-=dt)<=0){hp-=9;z.hit=.75f;if(hp<=0)over=true;}}
        for(Bullet b:bullets){b.x+=b.dx*780*dt;b.y+=b.dy*780*dt;b.life-=dt;}
        for(int i=bullets.size()-1;i>=0;i--){Bullet b=bullets.get(i);boolean hit=false;for(int j=zombies.size()-1;j>=0;j--){Zombie z=zombies.get(j);if(Math.hypot(b.x-z.x,b.y-z.y)<25){z.hp-=25;hit=true;if(z.hp<=0){zombies.remove(j);kills++;}break;}}if(hit||b.life<=0)bullets.remove(i);}
        if(px>1130&&py<165&&kills>=10)won=true;
    }
    void shoot(){if(reloadClock>0)return;if(ammo<=0){reload();return;}ammo--;shotClock=.12f;Bullet b=new Bullet();b.x=px+aimX*26;b.y=py+aimY*26;b.dx=aimX;b.dy=aimY;b.life=1.3f;bullets.add(b);}
    void reload(){if(ammo<30&&reserve>0&&reloadClock<=0)reloadClock=1.25f;}
    void spawn(){Zombie z=new Zombie();int e=random.nextInt(4);if(e==0){z.x=20+random.nextInt(1240);z.y=58;}else if(e==1){z.x=1255;z.y=60+random.nextInt(630);}else if(e==2){z.x=20+random.nextInt(1240);z.y=695;}else{z.x=25;z.y=60+random.nextInt(630);}z.hp=50+wave*4;z.speed=75+wave*3;zombies.add(z);}
    void color(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);}void text(Canvas c,String s,float x,float y,float size,int color){color(color);p.setTextSize(size);c.drawText(s,x,y,p);}
    void drawGame(Canvas c){color(Color.rgb(16,24,32));c.drawRect(0,0,1280,720,p);p.setStrokeWidth(2);p.setColor(Color.rgb(24,38,49));for(int x=0;x<1280;x+=80)c.drawLine(x,55,x,720,p);for(int y=55;y<720;y+=80)c.drawLine(0,y,1280,y,p);
        color(Color.rgb(23,79,50));c.drawRect(1110,55,1255,165,p);text(c,"SAFE ROOM",1125,118,18,Color.rgb(125,255,155));color(Color.rgb(86,180,255));c.drawCircle(px,py,20,p);p.setColor(Color.WHITE);p.setStrokeWidth(7);c.drawLine(px,py,px+aimX*38,py+aimY*38,p);
        for(Zombie z:zombies){color(Color.rgb(98,180,75));c.drawCircle(z.x,z.y,22,p);color(Color.RED);c.drawCircle(z.x-7,z.y-5,3,p);c.drawCircle(z.x+7,z.y-5,3,p);}for(Bullet b:bullets){color(Color.rgb(255,229,107));c.drawCircle(b.x,b.y,5,p);}
        color(Color.argb(220,0,0,0));c.drawRect(0,0,1280,55,p);String s="HP "+Math.max(0,(int)hp)+"     AMMO "+ammo+"/"+reserve+"     KILLS "+kills+"     WAVE "+wave+(reloadClock>0?"     RELOADING...":"");text(c,s,22,36,23,Color.WHITE);text(c,"SURVIVE • GET 10 KILLS • REACH SAFE ROOM",765,35,16,Color.LTGRAY);
        float lx=moveId>=0?moveOX:130,ly=moveId>=0?moveOY:590,rx=aimId>=0?aimOX:1140,ry=aimId>=0?aimOY:590;color(Color.argb(35,255,255,255));c.drawCircle(lx,ly,76,p);color(Color.argb(80,255,255,255));c.drawCircle(moveId>=0?moveX:lx,moveId>=0?moveY:ly,34,p);color(Color.argb(45,255,50,40));c.drawCircle(rx,ry,76,p);color(Color.argb(100,255,50,40));c.drawCircle(aimId>=0?aimPX:rx,aimId>=0?aimPY:ry,34,p);text(c,"MOVE",lx-30,ly+6,15,Color.WHITE);text(c,"FIRE",rx-28,ry+6,15,Color.WHITE);
        if(won){color(Color.argb(235,0,42,20));c.drawRect(260,245,1020,445,p);text(c,"SAFE ROOM REACHED — YOU SURVIVED!",410,350,28,Color.rgb(125,255,155));}if(over){color(Color.argb(225,0,0,0));c.drawRect(0,0,1280,720,p);text(c,"YOU WERE OVERRUN",440,320,42,Color.rgb(255,81,71));text(c,"Tap anywhere to restart",485,380,23,Color.WHITE);}}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){float x=e.getX()*1280/getWidth(),y=e.getY()*720/getHeight();int a=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){if(over){reset();return true;}if(x<430&&moveId<0){moveId=id;moveOX=moveX=x;moveOY=moveY=y;}else if(x>850&&aimId<0){aimId=id;aimOX=aimPX=x;aimOY=aimPY=y;firing=true;}}else if(a==MotionEvent.ACTION_MOVE){for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float xx=e.getX(i)*1280/getWidth(),yy=e.getY(i)*720/getHeight();if(pid==moveId){moveX=xx;moveY=yy;}if(pid==aimId){aimPX=xx;aimPY=yy;float dx=xx-aimOX,dy=yy-aimOY,l=(float)Math.hypot(dx,dy);if(l>10){aimX=dx/l;aimY=dy/l;}}}}else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){if(id==moveId)moveId=-1;if(id==aimId){aimId=-1;firing=false;}}return true;}
}
