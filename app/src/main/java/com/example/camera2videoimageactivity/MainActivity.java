package com.example.camera2videoimageactivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            try {
                setupCamera(width,height);
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
    private static class CompareSizeByArea implements Comparator<Size>


    {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());

        }
    }


    private String mCameraID;
    



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        startBackgroundThread();

        if(mTextureView.isAvailable())
        {
            try {
                setupCamera(mTextureView.getWidth(),mTextureView.getHeight());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }
        else
        {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause()
    {
        closeCamera();

        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas)
    {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if(hasFocas)
        {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        |View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        }
    }

    private void setupCamera(int width, int height) throws CameraAccessException {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        for(String cameraID : cameraManager.getCameraIdList())
        {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
            if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
            {
                continue;
            }
            mCameraID = cameraID;
            return;
        }

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
}