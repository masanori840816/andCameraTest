package jp.cameratest;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
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
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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


public class Camera2Fragment extends Fragment {
    private Size mPreviewSize;

    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private OnFragmentInteractionListener mListener;

    public static Camera2Fragment newInstance(String param1, String param2) {
        Camera2Fragment fragment = new Camera2Fragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    public Camera2Fragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    private final TextureView.SurfaceTextureListener mCameraViewStatusChanged = new TextureView.SurfaceTextureListener(){
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Textureが有効化されたらカメラを初期化.
            prepareCameraView();
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
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // ビューにカメラプレビュー用のTextureView、ボタンを追加する.
        RelativeLayout rllLayout = new RelativeLayout(getActivity());
        rllLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mTextureView = new TextureView(getActivity());
        mTextureView.setSurfaceTextureListener(mCameraViewStatusChanged);
        rllLayout.addView(mTextureView);

        Button btnTakingPhoto = new Button(getActivity());
        btnTakingPhoto.setText(R.string.btn_taking_photo);

        btnTakingPhoto.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        btnTakingPhoto.setOnClickListener(
                (View v) ->{
                    takePicture();
                }
        );
        btnTakingPhoto.setGravity(Gravity.BOTTOM);

        rllLayout.addView(btnTakingPhoto);

        return rllLayout;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }
    @TargetApi(21)
    @Override
    public void onPause() {
        super.onPause();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
    @TargetApi(21)
    private void prepareCameraView() {

        // Camera機能にアクセスするためのCameraManagerの取得.
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
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
                mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];

                // プレビュー画面のサイズ調整.
                this.configureTransform();
                try {
                    manager.openCamera(strCameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            mCameraDevice = camera;
                            createCameraPreviewSession();
                        }

                        @Override
                        public void onDisconnected(CameraDevice cmdCamera) {
                            cmdCamera.close();
                            mCameraDevice = null;
                        }

                        @Override
                        public void onError(CameraDevice cmdCamera, int error) {
                            cmdCamera.close();
                            mCameraDevice = null;
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
    @TargetApi(21)
    protected void createCameraPreviewSession() {

        if(null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(null == texture) {
            return;
        }
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            // プレビューウインドウのリクエスト.
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
        mPreviewBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                    Toast.makeText(getActivity(), "onConfigureFailed", Toast.LENGTH_LONG).show();
                }
            }, null);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }
    @TargetApi(21)
    protected void updatePreview() {
        if(null == mCameraDevice) {
            Log.e("CameraView", "updatePreview error, ");
            return;
        }
        // オートフォーカスモードに設定する.
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 別スレッドで実行.
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @TargetApi(21)
    protected void takePicture() {
        if(null == mCameraDevice) {
            return;
        }
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

            Size[] jpegSizes = null;
            int width = 640;
            int height = 480;

            if (characteristics != null) {
                // デバイスがサポートしているストリーム設定からJpgの出力サイズを取得する.
                jpegSizes = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                if (jpegSizes != null && 0 < jpegSizes.length) {
                    width = jpegSizes[0].getWidth();
                    height = jpegSizes[0].getHeight();
                }
            }

            // 画像を取得するためのImageReaderの作成.
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // 画像を調整する.
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
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
                    Toast.makeText(getActivity(), "Saved:"+file, Toast.LENGTH_SHORT).show();
                    // もう一度カメラのプレビュー表示を開始する.
                    createCameraPreviewSession();
                }

            };
            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
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
                    getActivity().getApplicationContext(),
                    paths,
                    mimeTypes,
                    mScanSavedFileCompleted);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private MediaScannerConnection.OnScanCompletedListener mScanSavedFileCompleted = new MediaScannerConnection.OnScanCompletedListener(){
        @Override
        public void onScanCompleted(String path,
                                    Uri uri){
            // このタイミングでToastを表示する?
        }
    };
    @TargetApi(21)
    public void onConfigurationChanged(Configuration newConfig)
    {
        Log.d("cmrFragment", "onConfigurationChanged");
        // 画面の回転・サイズ変更でプレビュー画像の向きを変更する.
        super.onConfigurationChanged(newConfig);

        this.configureTransform();
    }
    @TargetApi(21)
    private void configureTransform()
    {
        // 画面の回転に合わせてmTextureViewの向き、サイズを変更する.
        if (null == mTextureView || null == mPreviewSize)
        {
            return;
        }
        Display dsply = getActivity().getWindowManager().getDefaultDisplay();

        int rotation = dsply.getRotation();
        Matrix matrix = new Matrix();

        Point pntDisplay = new Point();
        dsply.getSize(pntDisplay);

        RectF rctView = new RectF(0, 0, pntDisplay.x, pntDisplay.y);
        RectF rctPreview = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = rctView.centerX();
        float centerY = rctView.centerY();

        rctPreview.offset(centerX - rctPreview.centerX(), centerY - rctPreview.centerY());
        matrix.setRectToRect(rctView, rctPreview, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) rctView.width() / mPreviewSize.getWidth(),
                (float) rctView.height() / mPreviewSize.getHeight()
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
        mTextureView.setTransform(matrix);

    }
}
