package jp.cameratest;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity implements Camera2Fragment.OnFragmentInteractionListener {

    private final static int SDKVER_LOLLIPOP = 21;

    private Button mBtnStart;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
              .detectAll()
              .penaltyLog()
              .build();
        StrictMode.setThreadPolicy(policy);

        // Activityのタイトルを非表示にする(フルスクリーンなら不要？).
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        // フルスクリーン表示.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mBtnStart = (Button)findViewById(R.id.btn_start);
        mBtnStart.setOnClickListener(mBtnStartClicked);
    }
    private final View.OnClickListener mBtnStartClicked = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
        /*if (Build.VERSION.SDK_INT >= SDKVER_LOLLIPOP)
        {
            // Camera2を使ったActivityを開く.
            Intent ittMainView_Camera2 = new Intent(MainActivity.this, Camera2Activity.class);
            // 次画面のアクティビティ起動
            startActivity(ittMainView_Camera2);
        }
        else
        {
            // TODO:Cameraを使ったActivityを開く.
            Intent ittMainView_Camera = new Intent(MainActivity.this, CameraActivity.class);
            // 次画面のアクティビティ起動
            startActivity(ittMainView_Camera);
        }*/
            FragmentManager fm = getFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            Camera2Fragment frgCamera = new Camera2Fragment();
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.add(R.id.layout_main_root, frgCamera, "ViewPhoto");
            //ft.setCustomAnimations(R.animator.fragment_enter, R.animator.fragment_exit, R.animator.fragment_pop_enter,  R.animator.fragment_pop_exit);
            ft.commit();

        }
    };
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    public void onFragmentInteraction(Uri uri){
        //you can leave it empty
        Log.d("manAcitvity", "FragmentInteraction");
    }
}
