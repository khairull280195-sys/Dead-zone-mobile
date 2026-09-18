package com.independentstudio.deadzone;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
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
            if(y>getHeight()*.80f&&x>getWidth()*.34f&&x<getWidth()*.70f){world.weapon=Math.min(2,(int)((x-getWidth()*.34f)/(getWidth()*.12f)));world.firing=false;return true;}
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

    static final class Zombie{float x,z,hp=100,attack,phase,hitTime,death=-1,variant;boolean boss;}
    static final class SoundFx{
        final AudioTrack pistol=make(120,150,.88f),rifle=make(85,105,.98f),shotgun=make(260,72,1.15f),zombie=make(720,48,.24f);
        static AudioTrack make(int ms,float tone,float noise){int rate=22050,n=rate*ms/1000;short[] pcm=new short[n];Random r=new Random((long)(ms*tone));for(int i=0;i<n;i++){float t=i/(float)n,env=(float)Math.pow(1-t,ms>500?.55:2.2),wave=(float)Math.sin(6.28318*tone*i/rate);float grow=(float)Math.sin(6.28318*(tone*.34+18*t)*i/rate);float v=(ms>500?(wave*.55f+grow*.45f):(wave*.28f+(r.nextFloat()*2-1)*noise))*env;pcm[i]=(short)(Math.max(-1,Math.min(1,v))*24500);}AudioTrack a=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(n*2).setTransferMode(AudioTrack.MODE_STATIC).build();a.write(pcm,0,n);return a;}
        static void play(AudioTrack a){try{if(a.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)a.stop();a.setPlaybackHeadPosition(0);a.play();}catch(Exception ignored){}}
        void gun(int w){play(w==0?pistol:w==1?rifle:shotgun);}void growl(){play(zombie);}
    }
    static final class World implements GLSurfaceView.Renderer{
        private final Random rng=new Random();
        private final ArrayList<Zombie> zombies=new ArrayList<>();
        private final float[] projection=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
        private FloatBuffer cube,round;private int roundCount,program,aPosition,uMvp,uModel,uColor,surfaceW,surfaceH;private long lastTime;
        private float playerX,playerZ=8,yaw,pitch,health=100,fireClock,spawnClock,muzzleTime,growlClock=2,sceneTime;
        volatile float moveForward,moveSide,lookDx,lookDy;volatile boolean firing,dead,requestReset;
        volatile int weapon;private int kills,wave=1,remaining=7;private final SoundFx sounds=new SoundFx();
        private static final float[] CUBE={
            -1,-1,1,1,-1,1,1,1,1,-1,-1,1,1,1,1,-1,1,1, 1,-1,-1,-1,-1,-1,-1,1,-1,1,-1,-1,-1,1,-1,1,1,-1,
            -1,-1,-1,-1,-1,1,-1,1,1,-1,-1,-1,-1,1,1,-1,1,-1, 1,-1,1,1,-1,-1,1,1,-1,1,-1,1,1,1,-1,1,1,1,
            -1,1,1,1,1,1,1,1,-1,-1,1,1,1,1,-1,-1,1,-1, -1,-1,-1,1,-1,-1,1,-1,1,-1,-1,-1,1,-1,1,-1,-1,1};

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
            GLES20.glClearColor(.018f,.026f,.040f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);
            cube=ByteBuffer.allocateDirect(CUBE.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();cube.put(CUBE).position(0);
            float[] sphere=makeSphere(14,18);roundCount=sphere.length/3;round=ByteBuffer.allocateDirect(sphere.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();round.put(sphere).position(0);
            String vs="uniform mat4 uMvp,uModel;attribute vec3 aPosition;varying float vLight,vFog;void main(){vec3 n=normalize((uModel*vec4(normalize(aPosition),0.0)).xyz);vLight=0.30+0.70*max(dot(n,normalize(vec3(-0.45,0.85,0.25))),0.0);vec4 p=uMvp*vec4(aPosition,1.0);vFog=clamp((abs(p.w)-9.0)/34.0,0.0,0.72);gl_Position=p;}";
            String fs="precision mediump float;uniform vec4 uColor;varying float vLight,vFog;void main(){vec3 lit=uColor.rgb*vLight;vec3 fog=vec3(0.025,0.040,0.055);gl_FragColor=vec4(mix(lit,fog,vFog),uColor.a);}";
            program=link(vs,fs);aPosition=GLES20.glGetAttribLocation(program,"aPosition");uMvp=GLES20.glGetUniformLocation(program,"uMvp");uModel=GLES20.glGetUniformLocation(program,"uModel");uColor=GLES20.glGetUniformLocation(program,"uColor");reset();
        }
        @Override public void onSurfaceChanged(GL10 gl,int width,int height){surfaceW=width;surfaceH=height;GLES20.glViewport(0,0,width,height);Matrix.perspectiveM(projection,0,68,(float)width/Math.max(1,height),.08f,80);}
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
            for(Zombie z:zombies)drawZombie(z);
            // Moonlight and moving rain add depth without bitmap assets.
            drawRound(playerX+12,13,playerZ-31,2.5f,2.5f,1.1f,.68f,.72f,.70f);
            for(int i=0;i<42;i++){float rainX=playerX+((i*7.37f+sceneTime*2.1f)%30)-15,rainZ=playerZ+((i*11.13f+sceneTime*.9f)%34)-17,rainY=(i*3.17f-sceneTime*12)%8;if(rainY<0)rainY+=8;drawCube(rainX,rainY,rainZ,.012f,.23f,.012f,.20f,.32f,.42f,1);}
            float rightX=(float)Math.cos(rad),rightZ=(float)Math.sin(rad),gunLen=weapon==0?.30f:weapon==1?.54f:.46f,gunWide=weapon==2?.16f:.10f;
            drawCube(playerX+fx*.78f+rightX*.27f,1.25f+fy*.55f,playerZ+fz*.78f+rightZ*.27f,gunWide,.10f,gunLen,.10f,.11f,.13f,1);drawCube(playerX+fx*(.92f+gunLen*.30f)+rightX*.27f,1.25f+fy*.70f,playerZ+fz*(.92f+gunLen*.30f)+rightZ*.27f,.055f,.055f,gunLen*.55f,.035f,.038f,.042f,1);if(muzzleTime>0)drawCube(playerX+fx*(1.08f+gunLen)+rightX*.27f,1.25f+fy*.82f,playerZ+fz*(1.08f+gunLen)+rightZ*.27f,.12f,.12f,.12f,1,.55f,.08f,1);
            float crossX=playerX+fx*1.4f,crossY=1.65f+fy*1.4f,crossZ=playerZ+fz*1.4f;
            drawCube(crossX,crossY,crossZ,.026f,.006f,.006f,.95f,.95f,.95f,1);drawCube(crossX,crossY,crossZ,.006f,.026f,.006f,.95f,.95f,.95f,1);
            drawHud();
            GLES20.glDisableVertexAttribArray(aPosition);
        }
        private void update(float dt){
            sceneTime+=dt;float dx=lookDx,dy=lookDy;lookDx=lookDy=0;yaw+=dx;pitch=clamp(pitch-dy,-55,55);if(dead)return;
            float r=(float)Math.toRadians(yaw),forwardX=(float)Math.sin(r),forwardZ=-(float)Math.cos(r),sideX=(float)Math.cos(r),sideZ=(float)Math.sin(r);
            playerX+=(forwardX*moveForward+sideX*moveSide)*4.1f*dt;playerZ+=(forwardZ*moveForward+sideZ*moveSide)*4.1f*dt;playerX=clamp(playerX,-17.8f,17.8f);playerZ=clamp(playerZ,-20.8f,20.8f);
            fireClock-=dt;muzzleTime=Math.max(0,muzzleTime-dt);if(firing&&fireClock<=0){shoot();fireClock=weapon==0?.28f:weapon==1?.10f:.72f;muzzleTime=.055f;sounds.gun(weapon);}growlClock-=dt;if(growlClock<=0&&!zombies.isEmpty()){sounds.growl();growlClock=2.5f+rng.nextFloat()*4;}spawnClock-=dt;if(remaining>0&&spawnClock<=0){spawn();remaining--;spawnClock=.75f;}else if(remaining==0&&zombies.isEmpty()){wave++;remaining=5+wave*2;spawnClock=2;if(wave%3==0)spawnBoss();}
            for(int i=zombies.size()-1;i>=0;i--){Zombie z=zombies.get(i);z.hitTime=Math.max(0,z.hitTime-dt);if(z.death>=0){z.death+=dt;if(z.death>(z.boss?2.4f:1.65f))zombies.remove(i);continue;}float zx=playerX-z.x,zz=playerZ-z.z,d=(float)Math.hypot(zx,zz),reach=z.boss?1.85f:1.15f,speed=z.boss?.78f+wave*.025f:1.25f+wave*.05f;z.phase+=dt*(z.boss?3.1f:5.4f+wave*.12f);if(d>reach){z.x+=zx/d*speed*dt;z.z+=zz/d*speed*dt;}else{z.attack-=dt;if(z.attack<=0){health-=z.boss?24:9;z.attack=z.boss?1.05f:.72f;if(health<=0)dead=true;}}}
        }
        private void shoot(){float r=(float)Math.toRadians(yaw),fx=(float)Math.sin(r),fz=-(float)Math.cos(r),cone=weapon==2?.91f:weapon==1?.972f:.965f,damage=weapon==0?42:weapon==1?24:78;Zombie best=null;float bestScore=999;for(Zombie z:zombies){if(z.death>=0)continue;float dx=z.x-playerX,dz=z.z-playerZ,d=(float)Math.hypot(dx,dz),dot=(dx*fx+dz*fz)/Math.max(.01f,d),score=(1-dot)*d;if(dot>cone&&d<24&&score<bestScore){best=z;bestScore=score;}}if(best!=null){best.hp-=damage;best.hitTime=.14f;if(best.hp<=0){best.death=0;kills++;}}}
        private void spawn(){Zombie z=new Zombie();float a=rng.nextFloat()*6.283f;z.x=clamp(playerX+(float)Math.sin(a)*15,-17,17);z.z=clamp(playerZ+(float)Math.cos(a)*15,-20,20);z.phase=rng.nextFloat()*6.28f;z.variant=rng.nextInt(3);zombies.add(z);}
        private void spawnBoss(){Zombie z=new Zombie();float a=rng.nextFloat()*6.283f;z.x=clamp(playerX+(float)Math.sin(a)*18,-16,16);z.z=clamp(playerZ+(float)Math.cos(a)*18,-19,19);z.phase=rng.nextFloat()*6.28f;z.hp=700+wave*65;z.boss=true;zombies.add(z);sounds.growl();}
        private void reset(){playerX=0;playerZ=8;yaw=0;pitch=0;health=100;kills=0;wave=1;remaining=7;spawnClock=.2f;dead=false;requestReset=false;zombies.clear();lastTime=System.nanoTime();}
        private void useMesh(FloatBuffer b){b.position(0);GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,0,b);}
        private void drawCube(float x,float y,float z,float sx,float sy,float sz,float red,float green,float blue,float alpha){useMesh(cube);Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform4f(uColor,red,green,blue,alpha);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);}
        private void drawPart(float x,float y,float z,float sx,float sy,float sz,float rx,float ry,float rz,float red,float green,float blue){useMesh(round);Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.rotateM(model,0,ry,0,1,0);Matrix.rotateM(model,0,rx,1,0,0);Matrix.rotateM(model,0,rz,0,0,1);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform4f(uColor,red,green,blue,1);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,roundCount);}
        private void drawRound(float x,float y,float z,float sx,float sy,float sz,float red,float green,float blue){drawPart(x,y,z,sx,sy,sz,0,0,0,red,green,blue);}
        private void drawZombie(Zombie z){
            if(z.boss){drawBoss(z);return;}
            float dx=playerX-z.x,dz=playerZ-z.z,d=(float)Math.hypot(dx,dz),face=(float)Math.toDegrees(Math.atan2(-dx,-dz));
            float walk=(float)Math.sin(z.phase),bob=Math.abs((float)Math.sin(z.phase))*0.055f,leg=walk*30,arm=-walk*34-12;
            boolean attacking=d<1.35f&&z.death<0;if(attacking){arm=-78+walk*8;bob=.02f;}
            float fall=z.death<0?0:Math.min(92,z.death*105),sink=z.death<0?0:Math.min(.78f,z.death*.72f);
            float hurt=Math.max(0,z.hp/100f),flash=z.hitTime>0?1:0,skinR=flash>0?.92f:.24f+.18f*hurt,skinG=flash>0?.12f:.15f+.15f*hurt,skinB=flash>0?.10f:.09f;
            float shirtR=z.variant==0?.12f:z.variant==1?.25f:.10f,shirtG=z.variant==0?.22f:z.variant==1?.13f:.18f,shirtB=z.variant==0?.18f:z.variant==1?.10f:.27f;
            // Soft contact shadow and blood beneath wounded/dead infected.
            drawCube(z.x,.018f,z.z,.50f,.012f,.34f,.018f,.022f,.022f,1);if(z.hp<70)drawCube(z.x+.18f,.024f,z.z+.10f,.22f,.010f,.16f,.20f,.012f,.010f,1);
            float base=.02f-sink,bodyTilt=fall;
            drawPart(z.x-.18f,base+.52f+bob,z.z,.15f,.50f,.17f,leg,face,bodyTilt,.075f,.080f,.085f);
            drawPart(z.x+.18f,base+.52f+bob,z.z,.15f,.50f,.17f,-leg,face,bodyTilt,.075f,.080f,.085f);
            drawPart(z.x,base+1.36f+bob,z.z,.42f,.62f,.27f,0,face,bodyTilt,shirtR,shirtG,shirtB);
            drawPart(z.x-.43f,base+1.38f+bob,z.z-.03f,.105f,.62f,.105f,arm,face,bodyTilt,skinR,skinG,skinB);
            drawPart(z.x+.43f,base+1.38f+bob,z.z-.03f,.105f,.62f,.105f,-arm,face,bodyTilt,skinR,skinG,skinB);
            drawPart(z.x,base+2.18f+bob,z.z,.31f,.34f,.29f,walk*3,face,bodyTilt,skinR,skinG,skinB);
            float fr=(float)Math.toRadians(face),eyeX=(float)Math.sin(fr),eyeZ=(float)Math.cos(fr);
            drawRound(z.x-eyeX*.30f-.10f,base+2.23f+bob,z.z-eyeZ*.30f,.040f,.038f,.020f,.95f,.018f,.008f);drawRound(z.x-eyeX*.30f+.10f,base+2.23f+bob,z.z-eyeZ*.30f,.040f,.038f,.020f,.95f,.018f,.008f);
            drawPart(z.x,base+2.02f+bob,z.z-.30f,.12f,.035f,.025f,0,face,bodyTilt,.12f,.018f,.014f);
        }
        private void drawBoss(Zombie z){
            float dx=playerX-z.x,dz=playerZ-z.z,d=(float)Math.hypot(dx,dz),face=(float)Math.toDegrees(Math.atan2(-dx,-dz)),walk=(float)Math.sin(z.phase),bob=Math.abs(walk)*.07f;
            float fall=z.death<0?0:Math.min(94,z.death*65),sink=z.death<0?0:Math.min(1.25f,z.death*.55f),flash=z.hitTime>0?1:0,br=flash>0?1:.28f,bg=flash>0?.12f:.13f,bb=flash>0?.10f:.10f;
            float arm=d<2.0f&&z.death<0?-72+walk*12:-walk*24-28,base=-sink;
            drawRound(z.x,.035f,z.z,1.28f,.025f,.92f,.025f,.025f,.025f);
            drawPart(z.x-.46f,base+.80f+bob,z.z,.34f,.78f,.38f,walk*18,face,fall,.10f,.105f,.11f);drawPart(z.x+.46f,base+.80f+bob,z.z,.34f,.78f,.38f,-walk*18,face,fall,.10f,.105f,.11f);
            drawPart(z.x,base+2.15f+bob,z.z,.92f,1.05f,.58f,0,face,fall,br,bg,bb);drawPart(z.x,base+3.25f+bob,z.z-.04f,.49f,.46f,.45f,walk*2,face,fall,.31f,.18f,.13f);
            drawPart(z.x-.98f,base+2.18f+bob,z.z-.04f,.31f,.96f,.30f,arm,face,fall,.32f,.17f,.12f);drawPart(z.x+.98f,base+2.18f+bob,z.z-.04f,.31f,.96f,.30f,-arm,face,fall,.32f,.17f,.12f);
            drawRound(z.x-.98f,base+1.22f+bob,z.z-.18f,.43f,.40f,.43f,.34f,.14f,.09f);drawRound(z.x+.98f,base+1.22f+bob,z.z-.18f,.43f,.40f,.43f,.34f,.14f,.09f);
            float fr=(float)Math.toRadians(face),ex=(float)Math.sin(fr),ez=(float)Math.cos(fr);drawRound(z.x-ex*.46f-.17f,base+3.36f+bob,z.z-ez*.46f,.065f,.058f,.030f,1,.10f,.015f);drawRound(z.x-ex*.46f+.17f,base+3.36f+bob,z.z-ez*.46f,.065f,.058f,.030f,1,.10f,.015f);
            // Glowing chest core distinguishes The Brute from regular infected.
            drawRound(z.x-ex*.60f,base+2.35f+bob,z.z-ez*.60f,.19f,.19f,.08f,.72f,.035f,.015f);
        }
        private void drawHud(){
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);Matrix.orthoM(vp,0,0,surfaceW,0,surfaceH,-10,10);
            float mx=surfaceW-118,my=surfaceH-118,r=92;drawRound(mx,my,0,r,r,.02f,.025f,.035f,.045f);drawRound(mx,my,.02f,r-7,r-7,.02f,.07f,.09f,.10f);
            for(Zombie z:zombies)if(z.death<0)drawRound(mx+z.x/19f*(r-12),my-z.z/22f*(r-12),.10f,z.boss?10:5.5f,z.boss?10:5.5f,.02f,z.boss?1:.95f,z.boss?.35f:.04f,.02f);
            drawRound(mx+playerX/19f*(r-12),my-playerZ/22f*(r-12),.12f,7,7,.02f,.05f,.85f,.30f);
            float start=surfaceW*.40f,wy=54,slot=surfaceW*.085f;
            for(int i=0;i<3;i++){float x=start+i*slot;float hi=i==weapon?1:.22f;drawCube(x,wy,0,slot*.40f,34,.02f,.08f+hi*.20f,.10f+hi*.28f,.12f+hi*.18f,1);float len=i==0?17:i==1?31:26;drawCube(x,wy+3,.08f,6,7,len,.60f,.63f,.66f,1);if(i==2)drawCube(x,wy+3,.09f,11,8,10,.32f,.20f,.10f,1);}
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }
        private static float[] makeSphere(int stacks,int slices){float[] out=new float[stacks*slices*18];int k=0;for(int i=0;i<stacks;i++){float p0=(float)(-Math.PI/2+Math.PI*i/stacks),p1=(float)(-Math.PI/2+Math.PI*(i+1)/stacks);for(int j=0;j<slices;j++){float t0=(float)(2*Math.PI*j/slices),t1=(float)(2*Math.PI*(j+1)/slices);float[] a={cos(p0)*sin(t0),sin(p0),cos(p0)*cos(t0)},b={cos(p1)*sin(t0),sin(p1),cos(p1)*cos(t0)},c={cos(p1)*sin(t1),sin(p1),cos(p1)*cos(t1)},d={cos(p0)*sin(t1),sin(p0),cos(p0)*cos(t1)};for(float[] q:new float[][]{a,b,c,a,c,d})for(float v:q)out[k++]=v;}}return out;}
        private static float sin(float v){return(float)Math.sin(v);}private static float cos(float v){return(float)Math.cos(v);}
        private static int link(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
        private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);return s;}
        private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    }
}
