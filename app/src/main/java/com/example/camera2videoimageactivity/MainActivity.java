package com.example.camera2videoimageactivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            try {
                setupCamera(width, height);
                connectCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            Toast.makeText(getApplicationContext(), "TextureView is available", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            try {
                startPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            Toast.makeText(getApplicationContext(), "Camera Connection Succesfull !", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            camera.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

            camera.close();
            mCameraDevice = null;


        }
    };


    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());

        }
    }


    private String mCameraID;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;


    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);

    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            try {
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
                connectCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getApplicationContext(), "Application will not run without camera services", Toast.LENGTH_SHORT).show();

            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();

        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if (hasFocas) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        }
    }

    private void setupCamera(int width, int height) throws CameraAccessException {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        for (String cameraID : cameraManager.getCameraIdList()) {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                continue;
            }

            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
            int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
            boolean swapRotation = totalRotation == 90 || totalRotation == 270;
            int rotatedWidth = width;
            int rotatedHeight = height;

            if (swapRotation) {
                rotatedWidth = height;
                rotatedHeight = width;
            }

            mPreviewSize = choseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);


            mCameraID = cameraID;
            return;
        }

    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
           if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
           {
               if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
               {
                   cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
               }
               else
               {
                   if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                   {
                       Toast.makeText(this, "App requires to access the Camera", Toast.LENGTH_LONG).show();

                   }
                   requestPermissions(new String[] {Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_RESULT);
               }
           }
            else{
               cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
           }

        }
        catch (CameraAccessException e )
        {
            e.printStackTrace();
        }


    }

    private void startPreview() throws CameraAccessException {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mCaptureRequestBuilder.addTarget(previewSurface);

        mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.setRepeatingRequest(mCaptureRequestBuilder.build(), null,  mBackgroundHandler);   //null can be choose as capturecallback
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        Toast.makeText(getApplicationContext(), "Unable to setup camera preview ", Toast.LENGTH_SHORT).show();

                    }
                }, null);

    }

    private void closeCamera()
    {
        if(mCameraDevice != null)
        {
            mCameraDevice.close();
            mCameraDevice = null;

        }
    }

    private void startBackgroundThread()
    {
        mBackgroundHandlerThread = new HandlerThread("Camere2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());

    }

    private void stopBackgroundThread() throws InterruptedException {
        mBackgroundHandlerThread.quitSafely();
        mBackgroundHandlerThread.join();

        mBackgroundHandlerThread = null;
        mBackgroundHandler = null;
    }

    private static Size choseOptimalSize(Size[] choises, int width, int height)
    {
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choises)
        {
            if(option.getHeight() == option.getWidth() * height / width && option.getWidth() >= width && option.getHeight() >= height)
            {
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0 )
        {
            return Collections.min(bigEnough, new CompareSizeByArea());

        }
        else
        {
            return choises[0];
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation)
    {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }
}