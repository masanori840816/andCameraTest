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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Camera2Activity extends AppCompatActivity {
    private final static int REQUEST_PERMISSION_CAMERA = 1;
    private final static int REQUEST_PERMISSION_STORAGE = 2;
    private Size previewSize;
    private TextureView previewTextureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;

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
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy(){
        super.onDestroy();
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int intRequestCode, String[] strPermissions, int[] intGrantResults) {
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
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            takePicture();
        }
        else{
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_STORAGE);
        }
    }
    private void initCameraView(){
        // プレビュー用のViewを追加.
        previewTextureView = new TextureView(this);
        previewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Textureが有効化されたらカメラを初期化.
                prepareCameraView();
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
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
    private void prepareCameraView() {

        // Camera機能にアクセスするためのCameraManagerの取得.
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Back Cameraを取得してOpen.
            for (String strCameraId : manager.getCameraIdList()) {
                // Cameraから情報を取得するためのCharacteristics.
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(strCameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    // Front Cameraならスキップ.
                    continue;
                }
                // ストリームの設定を取得(出力サイズを取得する).
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                // TODO: 配列から最大の組み合わせを取得する.
                previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

                // プレビュー画面のサイズ調整.
                this.configureTransform();
                try {
                    manager.openCamera(strCameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            if(cameraDevice == null){
                                cameraDevice = camera;
                            }
                            createCameraPreviewSession();
                        }

                        @Override
                        public void onDisconnected(CameraDevice cmdCamera) {
                            cmdCamera.close();
                            cameraDevice = null;
                        }

                        @Override
                        public void onError(CameraDevice cmdCamera, int error) {
                            cmdCamera.close();
                            cameraDevice = null;
                            Log.e("CameraView", "onError");
                        }
                    }, null);
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
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    previewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                    Toast.makeText(getApplicationContext(), "onConfigureFailed", Toast.LENGTH_LONG).show();
                }
            }, null);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e("CameraView", "updatePreview error, ");
            return;
        }
        // オートフォーカスモードに設定する.
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 別スレッドで実行.
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void takePicture() {
        if(cameraDevice == null) {
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());

            Size[] jpegSizes = null;
            int width = 640;
            int height = 480;

            // デバイスがサポートしているストリーム設定からJpgの出力サイズを取得する.
            jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            // 画像を取得するためのImageReaderの作成.
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(previewTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // 画像を調整する.
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            // ファイルの保存先のディレクトリとファイル名.
            String strSaveDir = Environment.getExternalStorageDirectory().toString();
            String strSaveFileName = "pic_" + System.currentTimeMillis() +".jpg";

            final File file = new File(strSaveDir, strSaveFileName);

            // 別スレッドで画像の保存処理を実行.
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
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
                        // 生成した画像を出力する.
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            // 別スレッドで実行.
            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            final Handler backgroudHandler = new Handler(thread.getLooper());
            reader.setOnImageAvailableListener(readerListener, backgroudHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {
                    // 画像の保存が終わったらToast表示.
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(getApplicationContext(), "Saved:"+file, Toast.LENGTH_SHORT).show();
                    // もう一度カメラのプレビュー表示を開始する.
                    createCameraPreviewSession();
                }

            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, backgroudHandler);
                    } catch (CameraAccessException e) {

                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, backgroudHandler);
            // 保存した画像を反映させる.
            String[] paths = {strSaveDir + "/" + strSaveFileName};
            String[] mimeTypes = {"image/jpeg"};
            MediaScannerConnection.scanFile(
                    getApplicationContext(),
                    paths,
                    mimeTypes,
                    (String path, Uri uri) -> {
                        // TODO: Scan完了後にToast表示するか.
                    });

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
        Display dsply = getWindowManager().getDefaultDisplay();

        int rotation = dsply.getRotation();
        Matrix matrix = new Matrix();

        Point pntDisplay = new Point();
        dsply.getSize(pntDisplay);

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

        switch (rotation) {
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
}
