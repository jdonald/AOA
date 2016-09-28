package com.leapmotion.leapdaemon;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.os.IBinder;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class RelayService extends Service {

    private static final String TAG = "LeapClient";

    public static final String BROADCAST_ACTION = "com.leapmotion.leapdaemon.BROADCAST_ACTION";

    private static final String ACTION_USB_PERMISSION = "com.leapmotion.leapdaemon.action.USB_PERMISSION";

    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private boolean mIsIPCServerRunning = false;
    private boolean mIsAccessoryPermitted = false;
    private boolean mIsAccessoryOpen = false;
    private Intent mBroadcastIntent;

    private Thread mAccessoryRevMsgThread;
    private Thread mIPCServerThread;

    @Override
    public void onCreate() {
        super.onCreate();
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED));
        mBroadcastIntent = new Intent(BROADCAST_ACTION);
        mBroadcastIntent.setPackage(getPackageName());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mUsbDeviceReceiver);
        closeAccessory();
        stopIPCServer();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        startIPCServer();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return mBinder;
    }

    private final IRelayService.Stub mBinder = new IRelayService.Stub() {
        public void scanAccessories() {
            Log.d(TAG, "scanAccessories");
            UsbAccessory[] accessories = mUsbManager.getAccessoryList();
            UsbAccessory accessory = (accessories == null ? null : accessories[0]);
            if (accessory != null && !mIsAccessoryOpen) {
                if (mUsbManager.hasPermission(accessory)) {
                    mIsAccessoryPermitted = true;
                    openAccessory(accessory);
                } else {
                    synchronized (mUsbReceiver) {
                        if (!mPermissionRequestPending) {
                            mUsbManager.requestPermission(accessory, mPermissionIntent);
                            mPermissionRequestPending = true;
                        }
                    }
                }
            }
        }

        public boolean isIPCServerRunning() {
            return mIsIPCServerRunning;
        }

        public boolean isAccessoryPermitted() {
            return mIsAccessoryPermitted;
        }

        public boolean isAccessoryOpen() {
            return mIsAccessoryOpen;
        }
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.v(TAG, "permission granted for accessory" + accessory);
                        mIsAccessoryPermitted = true;
                        if (accessory != null) {
                            openAccessory(accessory);
                        }
                    } else {
                        mIsAccessoryPermitted = false;
                        Log.d(TAG, "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                    sendBroadcast(mBroadcastIntent);
                }
            }
        }
    };

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                Log.v(TAG, "Accessory Detached");
                UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null) {
                    closeAccessory();;
                }
                sendBroadcast(mBroadcastIntent);
            }
        }
    };

    private void openAccessory(UsbAccessory accessory) {
        Log.d(TAG, "openAccessory: " + accessory);
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        mIsAccessoryOpen = mFileDescriptor != null;
        if (mIsAccessoryOpen) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            mAccessoryRevMsgThread = new Thread() {
                @Override
                public void run() {
                    int ret = 0;
                    byte[] buffer = new byte[16384];
                    while (ret >= 0) {
                        try {
                            ret = mInputStream.read(buffer);
                        } catch (IOException e) {
                            break;
                        }
                        if (ret > 0) {
                            String text = new String(buffer, 0, ret);
                            Log.d(TAG, "Received: " + text);
                        }
                    }
                }
            };
            mAccessoryRevMsgThread.start();
            Log.d(TAG, "openAccessory succeeded");
        } else {
            Log.d(TAG, "openAccessory fail");
        }
        sendBroadcast(mBroadcastIntent);
    }

    private void closeAccessory() {
        mIsAccessoryOpen = false;
        mIsAccessoryPermitted = false;
        if (mAccessoryRevMsgThread != null) {
            try {
                mAccessoryRevMsgThread.join();
            } catch(InterruptedException e) {
            } finally {
                mAccessoryRevMsgThread = null;
            }
        }
        if (mFileDescriptor != null) {
            try {
                mFileDescriptor.close();
            } catch (IOException e) {
            } finally {
                mFileDescriptor = null;
            }
        }
        sendBroadcast(mBroadcastIntent);
    }

    public boolean send(byte[] msg) {
        if (mOutputStream != null) {
            try {
                mOutputStream.write(msg);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
            return true;
        }
        return false;
    }

    void startIPCServer() {
        Log.v(TAG, "start IPCServer");
        if (mIsIPCServerRunning)
            return;
        mIPCServerThread = new Thread() {
            @Override
            public void run() {
                // IPC Server
            }
        };
        mIPCServerThread.start();
        mIsIPCServerRunning = true;
    }

    void stopIPCServer() {
        Log.v(TAG, "stop IPCServer");
        if (!mIsIPCServerRunning)
            return;

        // stop IPCServer

        if (mIPCServerThread != null) {
            try {
                mIPCServerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mIPCServerThread = null;
            }
        }
        mIsIPCServerRunning = false;
    }
}
