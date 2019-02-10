package com.nspider.nearbytest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

public class MainActivity extends AppCompatActivity {

    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final String TAG = "MainActivity";
    private Context context;
    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 5432;


    String SERVICE_ID;
    private ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            // Automatically accept the connection on both sides.
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback);
            logD("Connection initiated : " + endpointId + " ," + connectionInfo.toString());
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // We're connected! Can now start sending and receiving data.
                    logD("Connected : " + endpointId + " ," + result.toString());
                    Nearby.getConnectionsClient(context).stopAdvertising();
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    // The connection was rejected by one or both sides.
                    logW("Connection rejected : " + endpointId + " ," + result.toString());
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    // The connection broke before it was able to be accepted.
                    break;
                default:
                    // Unknown status code
            }
        }

        @Override
        public void onDisconnected(String endpointId) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
        }
    };
    private PayloadCallback payloadCallback =
            new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            // This always gets the full data of the payload. Will be null if it's not a BYTES
            // payload. You can check the payload type with payload.getType().
            byte[] receivedBytes = payload.asBytes();
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    };
    private TextView mDebugLogView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        SERVICE_ID = getPackageName();
        mDebugLogView = findViewById(R.id.textView);
        mDebugLogView.setMovementMethod(new ScrollingMovementMethod());
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        Nearby.getConnectionsClient(context)
                .startAdvertising(
                        getUserNickname(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            //Toast.makeText(context, "Advertising :" + getUserNickname(), Toast.LENGTH_SHORT).show();
                            logD("Advertising : " + getUserNickname());
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            logE("Unable to start advertising",e);
                        });
    }

    public String getUserNickname() {
        return "1";
    }


    public void clickHandler(View view) {
        switch (view.getId()){
            case R.id.button: {
                startAdvertising();
                break;
            }
            case R.id.button2:{
                switchToDiscoveryMode();
                break;
            }
        }
    }

    private void switchToDiscoveryMode() {
        startActivity(new Intent(MainActivity.this,DiscoverActivity.class));
        onStop();
        finish();
    }

    /**
     * Called when the user has accepted (or denied) our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            recreate();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        Nearby.getConnectionsClient(context).stopAdvertising();
        Nearby.getConnectionsClient(context).stopAllEndpoints();
    }

    protected void logV(String msg) {
        Log.v(TAG,msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_verbose)));
    }

    protected void logD(String msg) {
        Log.d(TAG,msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_debug)));
    }

    protected void logW(String msg) {
        Log.w(TAG,msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_warning)));
    }

    protected void logW(String msg, Throwable e) {
        Log.w(TAG,msg, e);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_warning)));
    }

    protected void logE(String msg, Throwable e) {
        Log.e(TAG,msg, e);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_error)));
    }

    private void appendToLogs(CharSequence msg) {
        mDebugLogView.append("\n");
        mDebugLogView.append(DateFormat.format("hh:mm", System.currentTimeMillis()) + ": ");
        mDebugLogView.append(msg);
    }

    private static CharSequence toColor(String msg, int color) {
        SpannableString spannable = new SpannableString(msg);
        spannable.setSpan(new ForegroundColorSpan(color), 0, msg.length(), 0);
        return spannable;
    }
}
