package com.dcrandroid.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.dcrandroid.data.Constants;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.dcrandroid.R;

/**
 * Created by Macsleven on 11/15/2015.
 */
public class ReaderActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    String address;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_ui_read);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1){
            requestCameraPermission();
        }else {
            startCamera();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult.getContents() != null) {
                address = scanResult.getContents();
                intent.putExtra(Constants.ADDRESS, address);
                setResult(RESULT_OK, intent);
                finish();
            }else{
                finish();
            }
        }
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            int allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
            if (allowed == PackageManager.PERMISSION_DENIED) {
                new AlertDialog.Builder(this).setTitle(R.string.permission)
                        .setMessage(R.string.camera_permission_scan)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.VIBRATE};
                                ActivityCompat.requestPermissions(ReaderActivity.this, permissions, 200);
                            }
                        }).setCancelable(false)
                        .show();
            }
            else {
                startCamera();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 200){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //permission granted
                startCamera();
            }else{
                Toast.makeText(this, R.string.denied_permission, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CaptureActivityAnyOrientation.class);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setPrompt(getString(R.string.scan_info));
        integrator.setCameraId(0);  // Use a specific camera of the device
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

}
