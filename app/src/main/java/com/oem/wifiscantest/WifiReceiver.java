package com.oem.wifiscantest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.net.NetworkInterface;
import java.util.Objects;

public class WifiReceiver extends BroadcastReceiver {
    private int mCmd = Constants.TEST_CMD_UNKNOWN;
    private Handler mHandler;

    public WifiReceiver(int cmd, Handler handler) {
        mCmd = cmd;
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
            Log.i(Constants.TAG, "-> Broadcast received, " + WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            Message msg = mHandler.obtainMessage(mCmd);
            msg.arg1 = updated ? 1 : 0;
            mHandler.sendMessage(msg);
        } else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            Log.i(Constants.TAG, "-> Broadcast received, " + WifiManager.NETWORK_STATE_CHANGED_ACTION + info.toString());
            if (info.getType() == ConnectivityManager.TYPE_WIFI &&
                info.getState() == NetworkInfo.State.CONNECTED) {
                mHandler.sendEmptyMessage(mCmd);
            }
        }
    }
}
