package com.independentstudio.deadzone;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterFullscreen();
        FrameLayout root=new FrameLayout(this);game=new GameView(this);root.addView(game,new FrameLayout.LayoutParams(-1,-1));
        IntroView intro=new IntroView(this,()->root.removeViewAt(root.getChildCount()-1));root.addView(intro,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    private void enterFullscreen() {
        // These flags work on every Android version supported by this app.
        // The previous Android 11-only WindowInsets calls crashed at startup
        // on older phones before GameView could be displayed.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override protected void onPause(){super.onPause();if(game!=null)game.onPause();}
    @Override protected void onResume(){super.onResume();if(game!=null)game.onResume();}
}
