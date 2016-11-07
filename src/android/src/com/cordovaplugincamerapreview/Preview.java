package com.cordovaplugincamerapreview;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import org.apache.cordova.LOG;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.util.List;

class Preview extends RelativeLayout implements SurfaceHolder.Callback {
    private final SurfaceView mSurfaceView;
    private final SurfaceHolder mHolder;
    private final String TAG = "Preview";
    private Camera.Size mPreviewSize;
    private List<Camera.Size> mSupportedPreviewSizes;
    private Camera mCamera;
    private int cameraId;
    private int displayOrientation;
    private int viewWidth;
    private int viewHeight;

    Preview(Context context) {
        super(context);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        viewWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        viewHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(viewWidth, viewHeight);

        if (mSupportedPreviewSizes != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            mPreviewSize = getBestAspectPreviewSize(displayOrientation, viewWidth, viewHeight, parameters, 0.0d);
            //mPreviewSize = getOptimalSize(mSupportedPreviewSizes, viewWidth, viewHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mSurfaceView !=  null) {
            int width = right - left;
            int height = bottom - top;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = (displayOrientation % 180 == 0) ? mPreviewSize.width : mPreviewSize.height;
                previewHeight = (displayOrientation % 180 == 0) ? mPreviewSize.height : mPreviewSize.width;
                Log.d(TAG, String.format("onLayout: {previewWidth=%d, previewHeight=%d}", previewWidth, previewHeight));
            }

            int newRight;
            int newBottom;
            int newTop;
            int newLeft;

            // Center the child SurfaceView within the parent.
            double ratio = previewWidth / (double) previewHeight;

            if (width / ratio > height) {
                Log.d(TAG, "center horizontally");
                double scaledWidth = height * ratio;
                newTop = 0;
                newBottom = height;
                newLeft = (int)Math.round((width - scaledWidth) / 2);
                newRight = (int)Math.round((width + scaledWidth) / 2);
            } else {
                Log.d(TAG, "center vertically");
                double scaledHeight = width / ratio;
                newTop = (int)Math.round((height - scaledHeight) / 2);
                newBottom = (int)Math.round((height + scaledHeight) / 2);
                newLeft = 0;
                newRight = width;
            }
            mSurfaceView.layout(newLeft, newTop, newRight, newBottom);

            Log.d(
                    TAG,
                    String.format(
                            "onLayout: {left=%d, right=%d, top=%d, bottom=%d}",
                            newLeft,
                            newRight,
                            newTop,
                            newBottom
                    )
            );
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        if (mCamera != null) {
            mSurfaceView.setWillNotDraw(false);

            try {
                mCamera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            mCamera.stopPreview();

            // Now that the size is known, set up the camera parameters and begin
            // the preview.
            setCameraPreviewSize();
            setCameraDisplayOrientation();
            mCamera.startPreview();
        }
    }

    void setCamera(Camera camera, int cameraId) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
        }

        mCamera = camera;
        this.cameraId = cameraId;

        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            setCameraPreviewSize();
            setCameraDisplayOrientation();

            if (!mHolder.isCreating()) {
                try {
                    camera.setPreviewDisplay(mHolder);
                    camera.startPreview();
                } catch (IOException exception) {
                    Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
                }
            }
        }
    }

    int getDisplayOrientation() {
        return displayOrientation;
    }
    // THIS
    public static Camera.Size getBestAspectPreviewSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       Camera.Parameters parameters,
                                                       double closeEnough) {
        double targetRatio=(double)width / height;
        Camera.Size optimalSize=null;
        double minDiff=Double.MAX_VALUE;

        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio=(double)height / width;
        }

        List<Size> sizes = parameters.getSupportedPreviewSizes();

        Collections.sort(sizes,
                Collections.reverseOrder(new SizeComparator()));

        for (Size size : sizes) {
            double ratio=(double)size.width / size.height;

            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize=size;
                minDiff=Math.abs(ratio - targetRatio);
            }

            if (minDiff < closeEnough) {
                break;
            }
        }

        return(optimalSize);
    }

    Camera.Size getOptimalSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) targetWidth / targetHeight;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) targetHeight / targetWidth;
        }
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        // OptimalSize should never be null (but this stops editors from complaining)
        if (optimalSize != null) {
            Log.d(TAG, String.format("getOptimalSize: {width=%d, height=%d}", optimalSize.width, optimalSize.height));
        }

        return optimalSize;
    }

    private void setCameraPreviewSize() {
        if (mSupportedPreviewSizes != null) {
            //mPreviewSize = getOptimalSize(mSupportedPreviewSizes, viewWidth, viewHeight);
            Camera.Parameters parameters = mCamera.getParameters();
            mPreviewSize = getBestAspectPreviewSize(displayOrientation, viewWidth, viewHeight, parameters, 0.0d);
            if (mPreviewSize != null) {
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                mCamera.setParameters(parameters);
            }
            requestLayout();
        }
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        DisplayMetrics dm = new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }

        Log.d(
                TAG,
                String.format(
                        "setCameraDisplayOrientation: {rotation=%d deg, facing=%s, orientation=%d deg, displayOrientation=%d deg}",
                        degrees,
                        info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back",
                        info.orientation,
                        displayOrientation
                )
        );
        mCamera.setDisplayOrientation(displayOrientation);
        requestLayout();
    }

    private static class SizeComparator implements
            Comparator<Camera.Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            int left=lhs.width * lhs.height;
            int right=rhs.width * rhs.height;

            if (left < right) {
                return(-1);
            }
            else if (left > right) {
                return(1);
            }

            return(0);
        }
    }
}

