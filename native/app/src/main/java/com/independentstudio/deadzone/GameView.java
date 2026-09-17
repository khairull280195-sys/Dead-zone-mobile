package com.independentstudio.deadzone;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameView extends GLSurfaceView {
    private final World world;
    private int moveId = -1, lookId = -1;
    private float moveStartX, moveStartY, moveX, moveY, lastLookX, lastLookY;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        world = new World();
        setRenderer(world);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(), index=event.getActionIndex(), id=event.getPointerId(index);
        float x=event.getX(index), y=event.getY(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(world.dead){world.requestReset=true;return true;}
            if(x<getWidth()*.48f&&moveId<0){moveId=id;moveStartX=moveX=x;moveStartY=moveY=y;}
            else if(lookId<0){lookId=id;lastLookX=x;lastLookY=y;world.firing=true;}
        }else if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getPointerCount();i++){int pid=event.getPointerId(i);float xx=event.getX(i),yy=event.getY(i);
                if(pid==moveId){moveX=xx;moveY=yy;}
                if(pid==lookId){world.lookDx+=(xx-lastLookX)*.20f;world.lookDy+=(yy-lastLookY)*.16f;lastLookX=xx;lastLookY=yy;}
            }
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            if(id==moveId){moveId=-1;world.moveForward=world.moveSide=0;}
            if(id==lookId){lookId=-1;world.firing=false;}
        }
        if(moveId>=0){float radius=Math.max(80,getWidth()*.11f);world.moveSide=clamp((moveX-moveStartX)/radius,-1,1);world.moveForward=clamp((moveStartY-moveY)/radius,-1,1);}
        return true;
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    static final class Zombie{float x,z,hp=100,attack;}
    static final class World implements GLSurfaceView.Renderer{
        private final Random rng=new Random();
        private final ArrayList<Zombie> zombies=new ArrayList<>();
        private final float[] projection=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
        private FloatBuffer cube;private int program,aPosition,uMvp,uModel,uColor;private long lastTime;
        private float playerX,playerZ=8,yaw,pitch,health=100,fireClock,spawnClock,muzzleTime;
        volatile float moveForward,moveSide,lookDx,lookDy;volatile boolean firing,dead,requestReset;
        private int kills,wave=1,remaining=7;
        private static final float[] CUBE={
            -1,-1,1,1,-1,1,1,1,1,-1,-1,1,1,1,1,-1,1,1, 1,-1,-1,-1,-1,-1,-1,1,-1,1,-1,-1,-1,1,-1,1,1,-1,
            -1,-1,-1,-1,-1,1,-1,1,1,-1,-1,-1,-1,1,1,-1,1,-1, 1,-1,1,1,-1,-1,1,1,-1,1,-1,1,1,1,-1,1,1,1,
            -1,1,1,1,1,1,1,1,-1,-1,1,1,1,1,-1,-1,1,-1, -1,-1,-1,1,-1,-1,1,-1,1,-1,-1,-1,1,-1,1,-1,-1,1};

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
            GLES20.glClearColor(.018f,.026f,.040f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);
            cube=ByteBuffer.allocateDirect(CUBE.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();cube.put(CUBE).position(0);
            String vs="uniform mat4 uMvp,uModel;attribute vec3 aPosition;varying float vLight,vFog;void main(){vec3 n=normalize((uModel*vec4(normalize(aPosition),0.0)).xyz);vLight=0.30+0.70*max(dot(n,normalize(vec3(-0.45,0.85,0.25))),0.0);vec4 p=uMvp*vec4(aPosition,1.0);vFog=clamp((abs(p.w)-9.0)/34.0,0.0,0.72);gl_Position=p;}";
            String fs="precision mediump float;uniform vec4 uColor;varying float vLight,vFog;void main(){vec3 lit=uColor.rgb*vLight;vec3 fog=vec3(0.025,0.040,0.055);gl_FragColor=vec4(mix(lit,fog,vFog),uColor.a);}";
            program=link(vs,fs);aPosition=GLES20.glGetAttribLocation(program,"aPosition");uMvp=GLES20.glGetUniformLocation(program,"uMvp");uModel=GLES20.glGetUniformLocation(program,"uModel");uColor=GLES20.glGetUniformLocation(program,"uColor");reset();
        }
        @Override public void onSurfaceChanged(GL10 gl,int width,int height){GLES20.glViewport(0,0,width,height);Matrix.perspectiveM(projection,0,68,(float)width/Math.max(1,height),.08f,80);}
        @Override public void onDrawFrame(GL10 gl){
            long now=System.nanoTime();float dt=Math.min(.04f,(now-lastTime)/1_000_000_000f);lastTime=now;if(requestReset)reset();update(dt);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float rad=(float)Math.toRadians(yaw),cp=(float)Math.cos(Math.toRadians(pitch)),fx=(float)Math.sin(rad)*cp,fy=(float)Math.sin(Math.toRadians(pitch)),fz=-(float)Math.cos(rad)*cp;
            Matrix.setLookAtM(view,0,playerX,1.65f,playerZ,playerX+fx,1.65f+fy,playerZ+fz,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);
            GLES20.glUseProgram(program);cube.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,0,cube);
            // Wet concrete yard, road, lane markings and perimeter warehouses.
            drawCube(0,-.25f,0,19,.18f,22,.075f,.095f,.105f,1);drawCube(0,-.04f,0,4.2f,.025f,21,.055f,.060f,.064f,1);
            for(int z=-18;z<=18;z+=6)drawCube(0,-.005f,z,.12f,.018f,1.25f,.62f,.56f,.18f,1);
            drawCube(-19,2.2f,0,.35f,2.45f,22,.13f,.15f,.16f,1);drawCube(19,2.2f,0,.35f,2.45f,22,.13f,.15f,.16f,1);drawCube(0,2.2f,-22,19,2.45f,.35f,.12f,.14f,.15f,1);drawCube(0,2.2f,22,19,2.45f,.35f,.12f,.14f,.15f,1);
            for(int i=-3;i<=3;i++){float bx=i*5;drawCube(bx,1,-8,.75f,1,2.1f,.12f,.15f,.17f,1);drawCube(bx,1.4f,-5.82f,.62f,.42f,.045f,.12f,.30f,.38f,1);if((i&1)==0){drawCube(bx,1,7,1.4f,1,.8f,.24f,.18f,.12f,1);drawCube(bx,2.04f,7,1.43f,.06f,.83f,.08f,.07f,.055f,1);}}
            // Street lights, crates, barrels and glowing warehouse windows.
            for(int i=-2;i<=2;i++){float lz=i*8;drawCube(-6.3f,1.7f,lz,.08f,1.7f,.08f,.10f,.11f,.12f,1);drawCube(-6.3f,3.35f,lz,.38f,.10f,.22f,.95f,.70f,.25f,1);drawCube(6.3f,1.7f,lz,.08f,1.7f,.08f,.10f,.11f,.12f,1);drawCube(6.3f,3.35f,lz,.38f,.10f,.22f,.95f,.70f,.25f,1);}
            for(int i=0;i<6;i++){float bx=-15+(i%3)*2.0f,bz=-16+(i/3)*2.2f;drawCube(bx,.48f,bz,.72f,.48f,.72f,.32f,.20f,.10f,1);drawCube(bx,.99f,bz,.68f,.035f,.68f,.08f,.055f,.035f,1);}
            for(int i=0;i<5;i++){float bz=-12+i*3;drawCube(15,.55f,bz,.38f,.55f,.38f,.20f,.13f,.08f,1);drawCube(15,.60f,bz,.40f,.055f,.40f,.32f,.12f,.04f,1);}
            for(Zombie z:zombies){float hurt=Math.max(0,z.hp/100f),skin=.22f+.22f*hurt;drawCube(z.x,.23f,z.z,.58f,.018f,.46f,.20f,.015f,.012f,1);drawCube(z.x-.20f,.48f,z.z,.17f,.48f,.18f,.10f,.11f,.12f,1);drawCube(z.x+.20f,.48f,z.z,.17f,.48f,.18f,.10f,.11f,.12f,1);drawCube(z.x,1.32f,z.z,.43f,.65f,.28f,.16f,.20f,.17f,1);drawCube(z.x,2.16f,z.z,.32f,.34f,.30f,skin,.30f*hurt,.16f,1);drawCube(z.x-.45f,1.30f,z.z-.08f,.11f,.66f,.11f,skin,.27f*hurt,.14f,1);drawCube(z.x+.45f,1.30f,z.z-.08f,.11f,.66f,.11f,skin,.27f*hurt,.14f,1);drawCube(z.x-.11f,2.22f,z.z-.31f,.045f,.040f,.018f,.95f,.06f,.025f,1);drawCube(z.x+.11f,2.22f,z.z-.31f,.045f,.040f,.018f,.95f,.06f,.025f,1);}
            float rightX=(float)Math.cos(rad),rightZ=(float)Math.sin(rad);drawCube(playerX+fx*.78f+rightX*.27f,1.25f+fy*.55f,playerZ+fz*.78f+rightZ*.27f,.11f,.10f,.42f,.10f,.11f,.13f,1);drawCube(playerX+fx*1.02f+rightX*.27f,1.25f+fy*.70f,playerZ+fz*1.02f+rightZ*.27f,.055f,.055f,.24f,.035f,.038f,.042f,1);if(muzzleTime>0)drawCube(playerX+fx*1.32f+rightX*.27f,1.25f+fy*.82f,playerZ+fz*1.32f+rightZ*.27f,.12f,.12f,.12f,1,.55f,.08f,1);
            float crossX=playerX+fx*1.4f,crossY=1.65f+fy*1.4f,crossZ=playerZ+fz*1.4f;
            drawCube(crossX,crossY,crossZ,.026f,.006f,.006f,.95f,.95f,.95f,1);drawCube(crossX,crossY,crossZ,.006f,.026f,.006f,.95f,.95f,.95f,1);
            GLES20.glDisableVertexAttribArray(aPosition);
        }
        private void update(float dt){
            float dx=lookDx,dy=lookDy;lookDx=lookDy=0;yaw+=dx;pitch=clamp(pitch-dy,-55,55);if(dead)return;
            float r=(float)Math.toRadians(yaw),forwardX=(float)Math.sin(r),forwardZ=-(float)Math.cos(r),sideX=(float)Math.cos(r),sideZ=(float)Math.sin(r);
            playerX+=(forwardX*moveForward+sideX*moveSide)*4.1f*dt;playerZ+=(forwardZ*moveForward+sideZ*moveSide)*4.1f*dt;playerX=clamp(playerX,-17.8f,17.8f);playerZ=clamp(playerZ,-20.8f,20.8f);
            fireClock-=dt;muzzleTime=Math.max(0,muzzleTime-dt);if(firing&&fireClock<=0){shoot();fireClock=.18f;muzzleTime=.055f;}spawnClock-=dt;if(remaining>0&&spawnClock<=0){spawn();remaining--;spawnClock=.75f;}else if(remaining==0&&zombies.isEmpty()){wave++;remaining=5+wave*2;spawnClock=2;}
            for(Zombie z:zombies){float zx=playerX-z.x,zz=playerZ-z.z,d=(float)Math.hypot(zx,zz);if(d>1.15f){z.x+=zx/d*(1.25f+wave*.05f)*dt;z.z+=zz/d*(1.25f+wave*.05f)*dt;}else{z.attack-=dt;if(z.attack<=0){health-=9;z.attack=.72f;if(health<=0)dead=true;}}}
        }
        private void shoot(){float r=(float)Math.toRadians(yaw),fx=(float)Math.sin(r),fz=-(float)Math.cos(r);Zombie best=null;float bestScore=999;for(Zombie z:zombies){float dx=z.x-playerX,dz=z.z-playerZ,d=(float)Math.hypot(dx,dz),dot=(dx*fx+dz*fz)/Math.max(.01f,d),score=(1-dot)*d;if(dot>.965f&&d<24&&score<bestScore){best=z;bestScore=score;}}if(best!=null){best.hp-=34;if(best.hp<=0){zombies.remove(best);kills++;}}}
        private void spawn(){Zombie z=new Zombie();float a=rng.nextFloat()*6.283f;z.x=clamp(playerX+(float)Math.sin(a)*15,-17,17);z.z=clamp(playerZ+(float)Math.cos(a)*15,-20,20);zombies.add(z);}
        private void reset(){playerX=0;playerZ=8;yaw=0;pitch=0;health=100;kills=0;wave=1;remaining=7;spawnClock=.2f;dead=false;requestReset=false;zombies.clear();lastTime=System.nanoTime();}
        private void drawCube(float x,float y,float z,float sx,float sy,float sz,float red,float green,float blue,float alpha){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform4f(uColor,red,green,blue,alpha);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);}
        private static int link(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
        private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);return s;}
        private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    }
}
