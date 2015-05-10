package jp.cameratest;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;


public class CameraActivity extends Activity {

    private TextureView mTextureView;
    private Button mBtnTakingPhoto;
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
        setContentView(R.layout.activity_camera);
        mTextureView = (TextureView)findViewById(R.id.camera2_view);
        mTextureView.setSurfaceTextureListener(mCameraViewStatusChanged);

        mBtnTakingPhoto = (Button)findViewById(R.id.btn_taking_photo);
        mBtnTakingPhoto.setOnClickListener(mBtnShotClicked);
    }
    private final TextureView.SurfaceTextureListener mCameraViewStatusChanged = new TextureView.SurfaceTextureListener(){
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // TODO:Textureが有効化されたらカメラを初期化.

        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };
    private final View.OnClickListener mBtnShotClicked = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            // TODO:画像を保存する.
        }
    };
}
