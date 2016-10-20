package com.cordovaplugincamerapreview;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import org.apache.cordova.LOG;

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

        requestLayout();

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
            mPreviewSize = getOptimalSize(mSupportedPreviewSizes, viewWidth, viewHeight);
            Log.i(TAG, "called supported sizes");
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            int width = right - left;
            int height = bottom - top;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                if (displayOrientation == 90 || displayOrientation == 270) {
                    //noinspection SuspiciousNameCombination
                    previewWidth = mPreviewSize.height;
                    //noinspection SuspiciousNameCombination
                    previewHeight = mPreviewSize.width;
                }

                LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }

            int newWidth;
            int newHeight;
            int newTop;
            int newLeft;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int) ((previewWidth * height / previewHeight) * scale);
                newWidth = (width + scaledChildWidth) / 2;
                newHeight = (int) (height * scale);
                newTop = 0;
                newLeft = (width - scaledChildWidth) / 2;
            } else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int) ((previewHeight * width / previewWidth) * scale);
                newWidth = (int) (width * scale);
                newHeight = (height + scaledChildHeight) / 2;
                newTop = (height - scaledChildHeight) / 2;
                newLeft = 0;
            }
            child.layout(newLeft, newTop, newWidth, newHeight);

            Log.d("layout", "left:" + newLeft);
            Log.d("layout", "top:" + newTop);
            Log.d("layout", "right:" + newWidth);
            Log.d("layout", "bottom:" + newHeight);
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
            if (mSupportedPreviewSizes != null) {
                mPreviewSize = getOptimalSize(mSupportedPreviewSizes, width, height);
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                mCamera.setParameters(parameters);
            }

            setCameraDisplayOrientation();
            mCamera.startPreview();
            requestLayout();
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
                    Log.e(TAG, exception.getMessage());
                }
            }
        }
    }

    int getDisplayOrientation() {
        return displayOrientation;
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
            Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        }

        return optimalSize;
    }

    private void setCameraPreviewSize() {
        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalSize(mSupportedPreviewSizes, viewWidth, viewHeight);

            if (mPreviewSize != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                mCamera.setParameters(parameters);
            }
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

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back")
                + " camera is oriented -" + info.orientation + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }
}
