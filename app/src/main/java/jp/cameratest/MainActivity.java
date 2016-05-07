package jp.cameratest;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Activityのタイトルを非表示にする.
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        // フルスクリーン表示.
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        Button btnStart = (Button) findViewById(R.id.btn_start);
        btnStart.setOnClickListener(
            (View v) -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                // Camera2を使ったActivityを開く.
                Intent intentCamera2 = new Intent(MainActivity.this, Camera2Activity.class);
                // 次画面のアクティビティ起動
                startActivity(intentCamera2);

            } else {
                // Cameraを使ったActivityを開く.
                Intent intentCamera = new Intent(MainActivity.this, CameraActivity.class);
                // 次画面のアクティビティ起動
                startActivity(intentCamera);
            }
        });
    }
}