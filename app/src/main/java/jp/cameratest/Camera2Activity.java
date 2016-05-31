package jp.cameratest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import jp.cameratest.databinding.ActivityCamera2Binding;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Activity extends AppCompatActivity implements SelectFilterFragment.OnFragmentInteractionListener {
    private final static int RequestNumPermissionCamera = 1;
    private final static int RequestNumPermissionStorage = 2;
    private final static int TextureViewMaxWidth = 1920;
    private final static int TextureViewMaxHeight = 1080;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    private ImageReader previewImageReader;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean isFlashlightSupported;
    // 画像保存時Storageの権限を要求した場合に、BackgroundThread再開後に画像保存処理を行うためのフラグ.
    private boolean isPictureTaken = false;
    private Image capturedImage;
    // 端末を180度回転させた場合に、2回目のconfigureTransformを呼ぶのに使う.
    private int lastOrientationNum = -1;
    private int savedOrientationNum;
    private MediaActionSound mediaActionSound;
    private ActivityCamera2Binding binding;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 180);
        ORIENTATIONS.append(Surface.ROTATION_180, 90);
        ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // フルスクリーン表示.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camera2);

        // シャッター音の準備.
        mediaActionSound = new MediaActionSound();
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);

        // プレビュー用のViewを追加.
        binding = DataBindingUtil.setContentView(this, R.layout.activity_camera2);
        binding.texturePreviewCamera2.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Textureが有効化されたらプレビューを表示.
                // OS6.0以上ならCameraへのアクセス権確認.
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    requestCameraPermission();
                }
                else{
                    openCamera(width, height);
                }
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        if(binding.btnTakingPhotoCamera2 != null){
            binding.btnTakingPhotoCamera2.setOnClickListener(
                    (View v) ->{
                        takePicture();
                    }
            );
        }
        setSupportActionBar((Toolbar) binding.toolbarCamera2);
        // Toolbarのタイトルを非表示にする.
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setDisplayShowTitleEnabled(false);
        }
        // 画面回転時などSavedInstanceStateに値が残っていれば取得する.
        savedOrientationNum = (savedInstanceState == null)? -1: savedInstanceState.getInt(getString(R.string.saved_orientation_num));
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_camera2, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_filter_camera2:
                Log.d("testtest", "menu1 tap.");
                return true;
            case R.id.action_settings_camera2:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onResume(){
        super.onResume();

        startBackgroundThread();

        // Storageの権限要求後は画像の保存処理を行う.
        if(isPictureTaken){
            prepareSavingImage();
            isPictureTaken = false;
        }
        lastOrientationNum = getWindowManager().getDefaultDisplay().getRotation();

        if(savedOrientationNum == lastOrientationNum
                && previewSize != null){
            openCamera(previewSize.getWidth(), previewSize.getHeight());
            savedOrientationNum = -1;
        }
    }
    @Override
    public void onPause(){
        super.onPause();
        if(previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
        if(displayManager != null
                && displayListener != null){
            displayManager.unregisterDisplayListener(displayListener);
        }
        displayManager = null;
        displayListener = null;

        if(isInMultiWindowMode()){
            savedOrientationNum = getWindowManager().getDefaultDisplay().getRotation();
        }
        stopBackgroundThread();
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(previewImageReader != null){
            previewImageReader.close();
            previewImageReader = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mediaActionSound.release();

        savedOrientationNum = -1;
    }
    @Override
    public void onFragmentInteraction(Uri uri){

    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(getString(R.string.saved_orientation_num), lastOrientationNum);
    }
    @Override
    public void onRequestPermissionsResult(int intRequestCode
            , @NonNull String[] strPermissions
            , @NonNull int[] intGrantResults) {
        super.onRequestPermissionsResult(intRequestCode, strPermissions, intGrantResults);
        if(intGrantResults.length <= 0){
            return;
        }
        switch (intRequestCode){
            case RequestNumPermissionCamera:
                if (intGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(
                        () ->{
                            // 権限が許可されたらプレビュー画面の使用準備.
                            openCamera(binding.texturePreviewCamera2.getWidth(), binding.texturePreviewCamera2.getHeight());
                        }
                    );
                    return;
                }
                break;
            case RequestNumPermissionStorage:
                if (intGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storageへのアクセスが許可されたら、OnResumeで画像の保存処理実行.
                    isPictureTaken = true;
                    return;
                }
                break;
            default:
                return;
        }
        // 権限付与を拒否されたらMainActivityに戻る.
        finish();
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void requestCameraPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            // 権限が許可されていたらプレビュー画面の使用準備.
            openCamera(binding.texturePreviewCamera2.getWidth(), binding.texturePreviewCamera2.getHeight());
        }
        else{
            requestPermissions(new String[]{Manifest.permission.CAMERA}, RequestNumPermissionCamera);
        }
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void requestStoragePermission(){
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            // 権限付与済みであれば画像を保存する.
            prepareSavingImage();
        }
        else{
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}
                    , RequestNumPermissionStorage);
        }
    }
    private void openCamera(int width, int height) {
        if(width <= 0 || height <= 0){
            return;
        }
        // 画面回転を検出.
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
            }
            @Override
            public void onDisplayChanged(int displayId) {
                // Displayサイズの取得.
                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);

                configureTransform(displaySize.x, displaySize.y);

                int newOrientationNum = getWindowManager().getDefaultDisplay().getRotation();
                // 端末を180度回転させると2回目のconfigureTransformが呼ばれないのでここで実行.
                if(Math.abs(newOrientationNum - lastOrientationNum) == 2){
                    configureTransform(binding.texturePreviewCamera2.getWidth(), binding.texturePreviewCamera2.getHeight());
                    // 180度回転の場合はonResumeが呼ばれないのでここで角度情報を保持.
                    lastOrientationNum = newOrientationNum;
                }
            }
            @Override
            public void onDisplayRemoved(int displayId) {
            }
        };
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(displayListener, backgroundHandler);

        // Camera機能にアクセスするためのCameraManagerの取得.
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Back Cameraを取得してOpen.
            for (String strCameraId : manager.getCameraIdList()) {
                // Cameraから情報を取得するためのCharacteristics.
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(strCameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null
                        || facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    // Front Cameraならスキップ.
                    continue;
                }

                // 端末がFlashlightに対応しているか確認.
                Boolean isAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                isFlashlightSupported = (isAvailable != null && isAvailable);

                // ストリームの設定を取得(出力サイズを取得する).
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map == null) {
                    continue;
                }
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);

                // 配列から最大の組み合わせを取得する.
                Size maxImageSize = new Size(width, height);
                Optional<Size> maxSize = Stream.of(sizes)
                        .max((a, b) -> Integer.compare(a.getWidth(), b.getWidth()));

                if(maxSize != null){
                    maxImageSize = maxSize.get();
                }

                // 画像を取得するためのImageReaderの作成.
                previewImageReader = ImageReader.newInstance(maxImageSize.getWidth(), maxImageSize.getHeight(), ImageFormat.JPEG, 2);

                previewImageReader.setOnImageAvailableListener(
                    (ImageReader reader)-> {
                       capturedImage = reader.acquireLatestImage();

                       // OS6.0以上ならStorageへのアクセス権を確認.
                       if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                           requestStoragePermission();
                       }
                       else{
                            prepareSavingImage();
                       }
                    }
                    , backgroundHandler);

                int displayRotationNum = getWindowManager().getDefaultDisplay().getRotation();

                if(displayRotationNum == Surface.ROTATION_90
                        || displayRotationNum == Surface.ROTATION_270){
                    binding.texturePreviewCamera2.setAspectRatio(maxImageSize.getWidth(), maxImageSize.getHeight());
                }
                else{
                    binding.texturePreviewCamera2.setAspectRatio(maxImageSize.getHeight(), maxImageSize.getWidth());
                }

                // 取得したSizeのうち、画面のアスペクト比に合致していてTextureViewMaxWidth・TextureViewMaxHeight以下の最大値をセット.
                final float aspectRatio = ((float)maxImageSize.getHeight() / (float)maxImageSize.getWidth());

                int maxWidth;
                int maxHeight;

                if(isInMultiWindowMode()){
                    maxWidth = width;
                    maxHeight = height;
                }
                else{
                    maxWidth = TextureViewMaxWidth;
                    maxHeight = TextureViewMaxHeight;
                }

                Optional<Size> setSize = Stream.of(sizes)
                        .filter(size ->
                                size.getWidth() <= maxWidth
                                        && size.getHeight() <= maxHeight
                                        && size.getHeight() == (size.getWidth() * aspectRatio))
                        .max((a, b) -> Integer.compare(a.getWidth(), b.getWidth()));

                if(setSize == null){
                    previewSize = new Size(640, 480);
                }
                else{
                    previewSize = setSize.get();
                }

                try {
                    manager.openCamera(strCameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            if(cameraDevice == null){
                                cameraDevice = camera;
                            }
                            runOnUiThread(
                                () -> {
                                    // カメラ画面表示中はScreenをOffにしない.
                                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                });
                            createCameraPreviewSession();
                        }
                        @Override
                        public void onDisconnected(@NonNull CameraDevice cmdCamera) {
                            cameraDevice.close();
                            cameraDevice = null;
                        }
                        @Override
                        public void onError(@NonNull CameraDevice cmdCamera, int error) {
                            cameraDevice.close();
                            cameraDevice = null;
                        }
                    }, backgroundHandler);
                }catch (SecurityException e){
                    e.printStackTrace();
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void prepareSavingImage(){
        backgroundHandler.post(
            ()->{
                try {
                    try {
                        ByteBuffer buffer = capturedImage.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        saveImage(bytes);
                    }catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (capturedImage != null) {
                        capturedImage.close();
                    }
                }
            }
        );
    }
    private void saveImage(byte[] bytes) throws IOException {
        OutputStream output = null;
        try {
            // ファイルの保存先のディレクトリとファイル名.
            String strSaveDir = Environment.getExternalStorageDirectory().toString() + "/DCIM";
            String strSaveFileName = "pic_" + System.currentTimeMillis() +".jpg";

            final File file = new File(strSaveDir, strSaveFileName);

            // 生成した画像を出力する.
            output = new FileOutputStream(file);
            output.write(bytes);

            // 保存した画像を反映させる.
            MediaScannerConnection.scanFile(
                    getApplicationContext()
                    , new String[]{strSaveDir + "/" + strSaveFileName}
                    , new String[]{"image/jpeg"}
                    , (String path, Uri uri) ->{
                        runOnUiThread(
                            ()->{
                                Toast.makeText(getApplicationContext(), "Saved: " + path, Toast.LENGTH_SHORT).show();
                                // もう一度カメラのプレビュー表示を開始する.
                                if(cameraDevice == null){
                                    // 権限確認でPause状態から復帰したらCameraDeviceの取得も行う.
                                    openCamera(binding.texturePreviewCamera2.getWidth(), binding.texturePreviewCamera2.getHeight());
                                }
                                else{
                                    createCameraPreviewSession();
                                }
                            }
                        );
                    });
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
    private void createCameraPreviewSession(){
        if(cameraDevice == null || ! binding.texturePreviewCamera2.isAvailable() || previewSize == null) {
            return;
        }
        SurfaceTexture texture = binding.texturePreviewCamera2.getSurfaceTexture();
        if(texture == null) {
            return;
        }
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            // プレビューウインドウのリクエスト.
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        previewBuilder.addTarget(surface);

        try {
            cameraDevice.createCaptureSession(Arrays.asList(surface, previewImageReader.getSurface())
                    , new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            previewSession = session;
                            if(cameraDevice == null) {
                                return;
                            }
                            setCameraMode(previewBuilder);
                            try {
                                previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            Toast.makeText(getApplicationContext(), "onConfigureFailed", Toast.LENGTH_LONG).show();
                        }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void takePicture() {
        if(cameraDevice == null || previewSession == null) {
            return;
        }
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(previewImageReader.getSurface());
            setCameraMode(captureBuilder);

            // 画像の回転を調整する.
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION
                    , ORIENTATIONS.get(getWindowManager().getDefaultDisplay().getRotation()));
            // プレビュー画面の更新を一旦ストップ.
            previewSession.stopRepeating();

            // シャッター音を鳴らす.
  //          mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);

            // 画像の保存.
            previewSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void configureTransform(int viewWidth, int viewHeight){
        // 画面の回転に合わせてTextureViewの向き、サイズを変更する.
        if (binding.texturePreviewCamera2 == null || previewSize == null){
            return;
        }
        runOnUiThread(
            () ->{
                RectF rctView = new RectF(0, 0, viewWidth, viewHeight);
                RectF rctPreview = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
                float centerX = rctView.centerX();
                float centerY = rctView.centerY();

                Matrix matrix = new Matrix();

                int displayRotationNum = getWindowManager().getDefaultDisplay().getRotation();

                if(displayRotationNum == Surface.ROTATION_90
                        || displayRotationNum == Surface.ROTATION_270){

                    rctPreview.offset(centerX - rctPreview.centerX(), centerY - rctPreview.centerY());

                    matrix.setRectToRect(rctView, rctPreview, Matrix.ScaleToFit.FILL);

                    // 縦または横の画面一杯に表示するためのScale値を取得.
                    float scale = Math.max(
                            (float) viewHeight / previewSize.getHeight()
                            , (float) viewWidth / previewSize.getWidth()
                    );
                    matrix.postScale(scale, scale, centerX, centerY);
                    // ROTATION_90: 270度回転、ROTATION_270: 90度回転.
                    matrix.postRotate((90 * (displayRotationNum + 2)) % 360, centerX, centerY);
                }
                else{
                    // ROTATION_0: 0度回転、ROTATION_180: 180度回転.
                    matrix.postRotate(90 * displayRotationNum, centerX, centerY);
                }
                binding.texturePreviewCamera2.setTransform(matrix);
            }
        );
    }
    private void setCameraMode(CaptureRequest.Builder requestBuilder){
        // AutoFocus
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//        requestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);

        // 端末がFlashlightに対応していたら自動で使用されるように設定.
        if(isFlashlightSupported){
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraPreview");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    private void stopBackgroundThread(){
        backgroundThread.quitSafely();
        try{
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}
