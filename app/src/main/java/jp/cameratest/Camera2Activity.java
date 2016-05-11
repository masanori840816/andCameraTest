package jp.cameratest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Camera2Activity extends AppCompatActivity {
    private final static int REQUEST_PERMISSION_CAMERA = 1;
    private final static int REQUEST_PERMISSION_STORAGE = 2;
    private Size previewSize;
    private TextureView previewTextureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    private ImageReader imgReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean isFlashlightSupported;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Activityのタイトルを非表示にする.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // フルスクリーン表示.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camera2);

        // OS6.0以上ならCameraへのアクセス権確認.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            requestCameraPermission();
        }
        else{
            initCameraView();
        }
    }
    @Override
    public void onResume(){
        super.onResume();
        startBackgroundThread();
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPause(){
        if(previewSession != null){
            previewSession.close();
            previewSession = null;
        }
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }

        stopBackgroundThread();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }
    @Override
    public void onRequestPermissionsResult(int intRequestCode
            , @NonNull String[] strPermissions
            , @NonNull int[] intGrantResults) {
        super.onRequestPermissionsResult(intRequestCode, strPermissions, intGrantResults);
        switch (intRequestCode){
            case REQUEST_PERMISSION_CAMERA:
                if (intGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 権限が許可されたらプレビュー画面の使用準備.
                    initCameraView();
                }
                else{
                    // 権限付与を拒否されたらMainActivityに戻る.
                    finish();
                }
                break;
            case REQUEST_PERMISSION_STORAGE:
                if (intGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storageへのアクセスが許可されたら画像を保存する.
                    takePicture();
                }
                break;
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onConfigurationChanged(Configuration newConfig){
        Log.d("camera2Activity", "onConfigurationChanged");
        // 画面の回転・サイズ変更でプレビュー画像の向きを変更する.
        super.onConfigurationChanged(newConfig);

        configureTransform();
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void requestCameraPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            // 権限が許可されていたらプレビュー画面の使用準備.
            initCameraView();
        }
        else{
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
        }
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void requestStoragePermission(){
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            takePicture();
        }
        else{
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}
                    , REQUEST_PERMISSION_STORAGE);
        }
    }
    private void initCameraView(){
        // プレビュー用のViewを追加.
        previewTextureView = new TextureView(this);
        previewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Textureが有効化されたらプレビューを表示.
                openCamera(width, height);
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform();
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        LinearLayout layoutPreview = (LinearLayout) findViewById(R.id.layout_preview_camera2);
        if(layoutPreview != null){
            layoutPreview.addView(previewTextureView);
        }

        Button btnTakingPhoto = (Button) findViewById(R.id.btn_taking_photo_camera2);
        if(btnTakingPhoto != null){
            btnTakingPhoto.setOnClickListener(
                    (View v) ->{
                        // OS6.0以上ならStorageへのアクセス権を確認.
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestStoragePermission();
                        }
                        else{
                            takePicture();
                        }
                    }
            );
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void openCamera(int width, int height) {
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
                isFlashlightSupported = (isAvailable != null);

                // ストリームの設定を取得(出力サイズを取得する).
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map != null){
                    Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                    for(Size size : sizes){
                        Log.d("Camera2Activity", "width:" + size.getWidth() + " height:" + size.getHeight());
                    }
                    // TODO: 配列から最大の組み合わせを取得する.
                    previewSize = map.getOutputSizes(ImageFormat.JPEG)[0];
                }
// 画像を取得するためのImageReaderの作成.
                //ImageReader reader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 1);
                // TODO: 正しい縦横比で出来るだけ大きなサイズの指定.
                imgReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);

                imgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                           @Override
                           public void onImageAvailable(ImageReader reader) {
                               Image image = null;
                               try {
                                   image = reader.acquireLatestImage();

                                   // TODO: Fragmentで取得した画像を表示.保存ボタンが押されたら画像の保存を実行する.
                                   ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                   byte[] bytes = new byte[buffer.capacity()];
                                   buffer.get(bytes);
                                   saveImage(bytes);
                               } catch (FileNotFoundException e) {
                                   e.printStackTrace();
                               } catch (IOException e) {
                                   e.printStackTrace();
                               } finally {
                                   if (image != null) {
                                       image.close();
                                   }
                               }
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
                                   String[] paths = {strSaveDir + "/" + strSaveFileName};
                                   String[] mimeTypes = {"image/jpeg"};
                                   MediaScannerConnection.scanFile(
                                           getApplicationContext(),
                                           paths,
                                           mimeTypes,
                                           null);
                               } finally {
                                   if (output != null) {
                                       output.close();
                                   }
                               }
                           }
                       }
                        , backgroundHandler);

                // プレビュー画面のサイズ調整.
                configureTransform();
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
                            cmdCamera.close();
                            cameraDevice = null;
                        }
                        @Override
                        public void onError(@NonNull CameraDevice cmdCamera, int error) {
                            cmdCamera.close();
                            cameraDevice = null;
                            Log.e("CameraView", "onError");
                        }
                    }, backgroundHandler);
                }catch (SecurityException se){
                    se.printStackTrace();
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void createCameraPreviewSession() {

        if(cameraDevice == null || ! previewTextureView.isAvailable() || previewSize == null) {
            return;
        }
        SurfaceTexture texture = previewTextureView.getSurfaceTexture();
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
            cameraDevice.createCaptureSession(Arrays.asList(surface, imgReader.getSurface())
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
            }, backgroundHandler);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void takePicture() {
        if(cameraDevice == null) {
            return;
        }
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imgReader.getSurface());
            setCameraMode(captureBuilder);

            // TODO: 画像の回転を調整する.
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION
                    , ORIENTATIONS.get(getWindowManager().getDefaultDisplay().getRotation()));

            previewSession.stopRepeating();
            previewSession.capture(captureBuilder.build()
                    , new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session
                                , @NonNull CaptureRequest request
                                , @NonNull TotalCaptureResult result) {
                            // 画像の保存が終わったらToast表示.
                            super.onCaptureCompleted(session, request, result);
                            Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();
                            // もう一度カメラのプレビュー表示を開始する.
                            createCameraPreviewSession();
                        }
                    }
                    , null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void configureTransform(){
        // 画面の回転に合わせてmTextureViewの向き、サイズを変更する.
        if (previewTextureView == null || previewSize == null){
            return;
        }
        Matrix matrix = new Matrix();

        Point pntDisplay = new Point();
        getWindowManager().getDefaultDisplay().getSize(pntDisplay);

        RectF rctView = new RectF(0, 0, pntDisplay.x, pntDisplay.y);
        RectF rctPreview = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = rctView.centerX();
        float centerY = rctView.centerY();

        rctPreview.offset(centerX - rctPreview.centerX(), centerY - rctPreview.centerY());
        matrix.setRectToRect(rctView, rctPreview, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) rctView.width() / previewSize.getWidth(),
                (float) rctView.height() / previewSize.getHeight()
        );
        matrix.postScale(scale, scale, centerX, centerY);

        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                matrix.postRotate(0, centerX, centerY);
                break;
            case Surface.ROTATION_90:
                matrix.postRotate(270, centerX, centerY);
                break;
            case Surface.ROTATION_180:
                matrix.postRotate(180, centerX, centerY);
                break;
            case Surface.ROTATION_270:
                matrix.postRotate(90, centerX, centerY);
                break;
        }
        previewTextureView.setTransform(matrix);
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setCameraMode(CaptureRequest.Builder requestBuilder){
        // AutoFocus
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        requestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);

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
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
