package com.example.camera2api;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.RemoteActionCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
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
import java.util.UUID;

import static android.hardware.camera2.CameraCaptureSession.*;

public class MainActivity extends AppCompatActivity {
private Button btnCapture;
TextureView textureView;
private static final SparseIntArray ORIENTATIONS= new SparseIntArray();
static {
    ORIENTATIONS.append(Surface.ROTATION_0,90);
    ORIENTATIONS.append(Surface.ROTATION_90,0);
    ORIENTATIONS.append(Surface.ROTATION_180,270);
    ORIENTATIONS.append(Surface.ROTATION_270,180);
}
private String cameraID;
private CameraDevice cameraDevice;
private CameraCaptureSession cameraCaptureSessions;
private CaptureRequest.Builder captureBuilder;
private Size imageDimension;
private ImageReader imageReader;

// save to file
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION=200;
    private Boolean mFLASHSUPPORTED;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice=camera;
            createCapturePreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice=null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView=findViewById(R.id.textureview);
        btnCapture=findViewById(R.id.btnCapture);
        assert textureView!=null;

        textureView.setSurfaceTextureListener(textureListener);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    private void takePicture() {
        if(cameraDevice == null)
            return;
        CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics=manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegsize =null;
            if(characteristics!=null)
                jpegsize=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            //capture img with custome size

            int width=640;
            int height=480;
            if(jpegsize!=null && jpegsize.length>0){
                width=jpegsize[0].getWidth();
                height=jpegsize[0].getHeight();
            }
            final ImageReader reader=ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            final List<Surface> outputSurface=new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // check orientation base on device

            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

            file=new File(Environment.getExternalStorageDirectory()+"/"+UUID.randomUUID().toString()+".jpg");
            ImageReader.OnImageAvailableListener readerListner=new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image=null;
                    try{
                        image=reader.acquireLatestImage();
                        ByteBuffer buffer=image.getPlanes()[0].getBuffer();
                        byte[] bytes=new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    }catch (FileNotFoundException e){
                        e.printStackTrace();
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                    finally {
                        {
                            if(image!=null)
                                image.close();
                        }
                    }


                }
                private void save(byte[] bytes)throws IOException {
                    OutputStream outputStream=null;
                    try{
                        outputStream=new FileOutputStream(file);
                        outputStream.write(bytes);
                    }finally {
                        if(outputStream!=null)
                            outputStream.close();
                    }
                }

            };

            reader.setOnImageAvailableListener(readerListner,mBackgroundHandler);
            final CaptureCallback captureListner=new CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this,"Saved"+file,Toast.LENGTH_LONG).show();
                    createCapturePreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurface,new CameraCaptureSession.StateCallback(){
                public void onConfigured(CameraCaptureSession cameraCaptureSession){
                    try{
                        cameraCaptureSession.capture(captureBuilder.build(),captureListner,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCapturePreview() {
        try{
            SurfaceTexture texture=textureView.getSurfaceTexture();
            assert  texture!=null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface=new Surface(texture);
            captureBuilder=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice==null)
                        return;
                    cameraCaptureSessions=cameraCaptureSession;
                    updatePreview();
                }


                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this,"Changed",Toast.LENGTH_LONG).show();

                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if(cameraDevice==null)
            Toast.makeText(MainActivity.this,"Error",Toast.LENGTH_LONG).show();
        captureBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    private void openCamera() {
    CameraManager manager=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
    try{
        cameraID=manager.getCameraIdList()[0];
        CameraCharacteristics characteristics=manager.getCameraCharacteristics(cameraID);
        StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        assert map!=null;
        imageDimension=map.getOutputSizes(SurfaceTexture.class)[0];

        //Check realtime permissions if run higher API 23

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CAMERA_PERMISSION);
            return;
        }
        manager.openCamera(cameraID,stateCallback, null);
    } catch (CameraAccessException e) {
        e.printStackTrace();
    }
    }

    TextureView.SurfaceTextureListener textureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==REQUEST_CAMERA_PERMISSION){
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"U can't use camera without permission",Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        statBackgroundThread();
        if(textureView.isAvailable()){
            openCamera();
        }
        else
            textureView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThtread();
        super.onPause();
    }

    private void stopBackgroundThtread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread=null;
            mBackgroundHandler=null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void statBackgroundThread() {
        mBackgroundThread=new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler=new Handler(mBackgroundThread.getLooper());
    }
}



