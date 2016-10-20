package com.cordovaplugincamerapreview;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

public class CameraPreview extends CordovaPlugin implements CameraActivity.CameraPreviewListener {

    private final String TAG = "CameraPreview";

    private final int permissionsReqId = 0;
    private CallbackContext execCallback;
    private JSONArray execArgs;

    private CameraActivity fragment;
    private CallbackContext takePictureCallbackContext;
    private FrameLayout containerView;

    public CameraPreview() {
        super();
        Log.d(TAG, "Constructing");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("setOnPictureTakenHandler")) {
            setOnPictureTakenHandler(callbackContext);
            return true;
        } else if (action.equals("startCamera")) {
            if (cordova.hasPermission(Manifest.permission.CAMERA)) {
                return startCamera(args);
            }

            execCallback = callbackContext;
            execArgs = args;
            cordova.requestPermission(this, permissionsReqId, Manifest.permission.CAMERA);
            return true;
        } else if (action.equals("takePicture")) {
            return takePicture(args, callbackContext);
        } else if (action.equals("setColorEffect")) {
            return setColorEffect(args);
        } else if (action.equals("stopCamera")) {
            return stopCamera();
        } else if (action.equals("hideCamera")) {
            return hideCamera();
        } else if (action.equals("showCamera")) {
            return showCamera();
        } else if (action.equals("switchCamera")) {
            return switchCamera();
        } else if (action.equals("setFlashMode")) {
            return setFlashMode(args);
        } else if (action.equals("setFocusMode")) {
            return setFocusMode(args);
        }

        return false;
    }

    private boolean startCamera(final JSONArray args) {
        if (fragment != null) {
            return false;
        }
        fragment = new CameraActivity();
        fragment.setEventListener(this);

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                try {
                    DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
                    int x = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(0), metrics);
                    int y = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(1), metrics);
                    int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(2), metrics);
                    int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(3), metrics);
                    String defaultCamera = args.getString(4);
                    Boolean tapToTakePicture = args.getBoolean(5);
                    Boolean dragEnabled = args.getBoolean(6);
                    Boolean toBack = args.getBoolean(7);

                    fragment.defaultCamera = defaultCamera;
                    fragment.tapToTakePicture = tapToTakePicture;
                    fragment.dragEnabled = dragEnabled;
                    fragment.setRect(x, y, width, height);

                    //create or update the layout params for the container view
                    Activity activity = cordova.getActivity();
                    if (containerView == null) {
                        containerView = new FrameLayout(activity.getApplicationContext());
                        // Look up a view id we inject to ensure there are no conflicts
                        int cameraViewId = activity.getResources().getIdentifier(activity.getClass().getPackage().getName() + ":id/camera_container", null, null);
                        containerView.setId(cameraViewId);
                    }
                    if (containerView.getParent() != webView.getView().getParent()) {
                        if (containerView.getParent() != null) {
                            ((ViewGroup) containerView.getParent()).removeView(containerView);
                        }
                        FrameLayout.LayoutParams containerLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                        ((ViewGroup) webView.getView().getParent()).addView(containerView, containerLayoutParams);
                    }
                    //display camera bellow the webview
                    if (toBack) {
                        webView.getView().setBackgroundColor(0x00000000);
                        webView.getView().bringToFront();
                    } else {
                        //set camera back to front
                        containerView.setAlpha(Float.parseFloat(args.getString(8)));
                        containerView.bringToFront();
                    }

                    //add the fragment to the container
                    FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.add(containerView.getId(), fragment);
                    fragmentTransaction.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    private boolean takePicture(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        try {
            double maxWidth = args.optDouble(0, 0);
            double maxHeight = args.optDouble(1, 0);
            fragment.takePicture((int) Math.floor(maxWidth), (int) Math.floor(maxHeight));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void onPictureTaken(String originalPicturePath) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, originalPicturePath);
        pluginResult.setKeepCallback(true);
        takePictureCallbackContext.sendPluginResult(pluginResult);
    }

    private boolean setColorEffect(final JSONArray args) {
        if (fragment == null) {
            return false;
        }

        Camera camera = fragment.getCamera();
        if (camera == null) {
            return true;
        }

        Camera.Parameters params = camera.getParameters();

        try {
            String effect = args.getString(0);

            if (effect.equals("aqua")) {
                params.setColorEffect(Camera.Parameters.EFFECT_AQUA);
            } else if (effect.equals("blackboard")) {
                params.setColorEffect(Camera.Parameters.EFFECT_BLACKBOARD);
            } else if (effect.equals("mono")) {
                params.setColorEffect(Camera.Parameters.EFFECT_MONO);
            } else if (effect.equals("negative")) {
                params.setColorEffect(Camera.Parameters.EFFECT_NEGATIVE);
            } else if (effect.equals("none")) {
                params.setColorEffect(Camera.Parameters.EFFECT_NONE);
            } else if (effect.equals("posterize")) {
                params.setColorEffect(Camera.Parameters.EFFECT_POSTERIZE);
            } else if (effect.equals("sepia")) {
                params.setColorEffect(Camera.Parameters.EFFECT_SEPIA);
            } else if (effect.equals("solarize")) {
                params.setColorEffect(Camera.Parameters.EFFECT_SOLARIZE);
            } else if (effect.equals("whiteboard")) {
                params.setColorEffect(Camera.Parameters.EFFECT_WHITEBOARD);
            } else {
                return false;
            }

            fragment.setCameraParameters(params);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean stopCamera() {
        if (fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.remove(fragment);
        fragmentTransaction.commit();
        fragment = null;

        return true;
    }

    private boolean showCamera() {
        if (fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.show(fragment);
        fragmentTransaction.commit();

        return true;
    }

    private boolean hideCamera() {
        if (fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(fragment);
        fragmentTransaction.commit();

        return true;
    }

    private boolean switchCamera() {
        if (fragment == null) {
            return false;
        }
        fragment.switchCamera();
        return true;
    }

    private boolean setFlashMode(final JSONArray args) {
        if (fragment == null) {
            return false;
        }
        try {
            fragment.setFlashMode(args.getInt(0));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean setFocusMode(final JSONArray args) {
        if (fragment == null) {
            return false;
        }

        try {
            fragment.setFocusMode(args.getInt(0));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void setOnPictureTakenHandler(CallbackContext callbackContext) {
        Log.d(TAG, "setOnPictureTakenHandler");
        takePictureCallbackContext = callbackContext;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                execCallback.sendPluginResult(new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION));
                return;
            }
        }
        if (requestCode == permissionsReqId) {
            startCamera(execArgs);
        }
    }
}
