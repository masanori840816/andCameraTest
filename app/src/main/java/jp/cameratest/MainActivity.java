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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity implements Camera2Fragment.OnFragmentInteractionListener {
    private Button mBtnStart;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setThreadPolicy(policy);

        // Activityのタイトルを非表示にする.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // フルスクリーン表示.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnStart.setOnClickListener(
            (View v) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){

                // Camera2を使ったFragmentを開く.
                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction()
                        .addToBackStack(null);
                Camera2Fragment frgCamera = new Camera2Fragment();
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.add(R.id.layout_main_root, frgCamera, "ViewPhoto");

                // TODO: カメラ表示時のアニメーション設定.
                //ft.setCustomAnimations(R.animator.fragment_enter, R.animator.fragment_exit, R.animator.fragment_pop_enter,  R.animator.fragment_pop_exit);
                ft.commit();
                // Activityのボタンは非表示にする.
                mBtnStart.setVisibility(View.GONE);
            } else {
                // TODO:Cameraを使ったActivityを開く.
                Intent ittMainView_Camera = new Intent(MainActivity.this, CameraActivity.class);
                // 次画面のアクティビティ起動
                startActivity(ittMainView_Camera);
            }
        });
    }
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
    @Override
    public void onBackPressed() {
        // TODO: Fragment表示中は該当Fragmentを閉じる.
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            getFragmentManager().popBackStack(); // BackStackに乗っているFragmentを戻す
            // Activityのボタンを再表示する.
            mBtnStart.setVisibility(View.VISIBLE);
        }
        else{
            super.onBackPressed();
        }
    }
}