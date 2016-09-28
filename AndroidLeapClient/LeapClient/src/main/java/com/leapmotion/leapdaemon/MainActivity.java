package com.leapmotion.leapdaemon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String TAG = "LeapClient";

    private TextView mLog;

    private IRelayService mService;
    boolean mBound = false;
    private Intent mServiceIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.leap_client);
        mLog = (TextView)findViewById(R.id.log);

        mServiceIntent = new Intent(this, RelayService.class);
        startService(mServiceIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        log("MainActivity onStart");
        bindService(mServiceIntent, mConnection, Context.BIND_AUTO_CREATE);

        Handler scheduler = new Handler();
        registerReceiver(mReceiver, new IntentFilter(RelayService.BROADCAST_ACTION), null, scheduler);
    }

    @Override
    protected void onStop() {
        super.onStop();
        log("MainActivity onStop");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        log("MainActivity onDestroy");
        stopService(mServiceIntent);
        super.onDestroy();
    }

    private void log(String msg) {
        mLog.setText(mLog.getText() + "\n" + msg);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            log("MainActivity onServiceConnected");
            mBound = true;
            IRelayService binder = IRelayService.Stub.asInterface(service);
            mService = binder;
            try {
                mService.scanAccessories();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            log("MainActivity onServiceDisconnected");
            mBound = false;
            mService = null;
            if (!isFinishing()) {
                bindService(mServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("MainActivity onReceive broadcast");
            if (mService == null)
                return;
            try {
                log("IsAccessoryPermitted: " + (mService.isAccessoryPermitted() ? "true" : "false"));
                log("IsAccessoryOpen: " + (mService.isAccessoryOpen() ? "true" : "false"));
                log("IsIPCServerRunning: " + (mService.isIPCServerRunning() ? "true" : "false"));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
}
