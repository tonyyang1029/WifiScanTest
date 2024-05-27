package com.oem.wifiscantest;

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.List;

public class TestRunner {
    private Context mCtxt;
    private Handler mUiHandler;
    private WifiManager mWifiManager;
    private TestHandler mTestHandler;
    private WifiReceiver mScanReceiver = null;
    private WifiReceiver mConnectReceiver = null;
    private int mState = Constants.MSG_CMD_UNKNOWN;
    private int mCount = 0;
    private int mSuccess = 0;
    private int mFailure = 0;
    private String mConnectingSsid = null;

    public TestRunner(Context ctxt, Handler uiHandler) {
        mCtxt = ctxt;
        mUiHandler = uiHandler;

        mWifiManager = (WifiManager) mCtxt.getSystemService(Context.WIFI_SERVICE);
        mTestHandler = new TestHandler();
    }

    public void start() {
        jumpTo(Constants.MSG_CMD_INIT, 0);
    }

    public void stop() {
        jumpTo(Constants.MSG_CMD_STOP, 0);
    }

    private void init() {
        mCount = 0;
        mSuccess = 0;
        mFailure = 0;

        Message msg = mUiHandler.obtainMessage(Constants.MSG_UI_SHOW_TEXT, "Initializing Wi-Fi tester");
        mUiHandler.sendMessage(msg);
        Log.i(Constants.TAG, "Initializing Wi-Fi tester");

        mWifiManager.setWifiEnabled(false);
        jumpTo(Constants.MSG_CMD_SCAN, 0);
    }

    private void scan() {
        if (mCount == Constants.MAX_TEST_COUNT) {
            jumpTo(Constants.MSG_CMD_STOP_SELF, 0);
            return;
        }

        mCount++;

        Message msg = mUiHandler.obtainMessage(Constants.MSG_UI_TEST_START);
        msg.obj = "No. " + mCount + " Test\n-> Start scanning";
        mUiHandler.sendMessage(msg);
        Log.i(Constants.TAG, "No. " + mCount + " Test");
        Log.i(Constants.TAG, "-> Start scanning");

        if (mScanReceiver == null) {
            mScanReceiver = new WifiReceiver(mTestHandler, mWifiManager);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mScanReceiver, intentFilter);

        mWifiManager.setWifiEnabled(true);
        mWifiManager.startScan();

        jumpTo(Constants.MSG_CMD_WAIT_SCAN_RESULT, 0);
    }

    private synchronized void connect(boolean success) {
        if (mState != Constants.MSG_CMD_WAIT_SCAN_RESULT) {
            return;
        }

        List<ScanResult> results = null;
        List<WifiConfiguration> configs = null;
        int configIdx = -1;

        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }
        results = mWifiManager.getScanResults();
        configs = mWifiManager.getConfiguredNetworks();

        Message msg = mUiHandler.obtainMessage(Constants.MSG_UI_TEST_PROGRESS);
        if (success) {
            mSuccess++;
            msg.obj = "-> Receive scanning result -> Success, " + results.size() + " Wi-Fi APs";
            Log.i(Constants.TAG, "-> Receive scanning result -> Success, " + results.size() + " Wi-Fi APs");
        } else {
            mFailure++;
            msg.obj = "-> Receive scanning result -> Failure";
            Log.i(Constants.TAG, "-> Receive scanning result -> Failure");
        }
        mUiHandler.sendMessage(msg);

        configIdx = chooseConfiguredNetwork(results, configs);
        if ( configIdx != -1) {
            String ssid = configs.get(configIdx).SSID;
            mConnectingSsid = ssid.substring(1, ssid.length() - 1);
            msg = mUiHandler.obtainMessage(Constants.MSG_UI_TEST_PROGRESS);
            msg.what = Constants.MSG_UI_TEST_PROGRESS;
            msg.obj = "-> Start connecting to " + mConnectingSsid;
            mUiHandler.sendMessage(msg);
            Log.i(Constants.TAG, "-> Start connecting to " + mConnectingSsid);

            if (mConnectReceiver == null) {
                mConnectReceiver = new WifiReceiver(mTestHandler, mWifiManager);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mCtxt.registerReceiver(mConnectReceiver, intentFilter);

            //mWifiManager.disconnect();
            mWifiManager.enableNetwork(configs.get(configIdx).networkId, true);
            //mWifiManager.reconnect();

            jumpTo(Constants.MSG_CMD_WAIT_CONNECT_RESULT, 5000);
        } else {
            jumpTo(Constants.MSG_CMD_SCAN, 1000);
        }
    }

    private synchronized void complete() {
        if (mState != Constants.MSG_CMD_WAIT_CONNECT_RESULT) {
            return;
        }

        if (mConnectReceiver != null) {
            mCtxt.unregisterReceiver(mConnectReceiver);
            mConnectReceiver = null;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String ssid = info.getSSID().substring(1, info.getSSID().length() - 1);
        if (mConnectingSsid.equals(ssid)) {
            Message msg = mUiHandler.obtainMessage(Constants.MSG_UI_APPEND_TEXT);
            msg.obj = "-> Connected to " + mConnectingSsid;
            mUiHandler.sendMessage(msg);
            Log.i(Constants.TAG, "-> Connected to " + mConnectingSsid);

            msg = mUiHandler.obtainMessage(Constants.MSG_UI_APPEND_TEXT);
            msg.obj = "Total: " + mCount + ", Success: " + mSuccess + ", Failure: " + mFailure;
            mUiHandler.sendMessage(msg);
            Log.i(Constants.TAG, "Total: " + mCount + ", Success: " + mSuccess + ", Failure: " + mFailure);

            jumpTo(Constants.MSG_CMD_SCAN, 1000);
        }
    }

    private void stopSelf()
    {
        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }
        if (mConnectReceiver != null) {
            mCtxt.unregisterReceiver(mConnectReceiver);
            mConnectReceiver = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
    }

    private void jumpTo(int cmd, int delayInMs) {
        mState = cmd;

        if (cmd == Constants.MSG_CMD_WAIT_CONNECT_RESULT) {
            Message msg = mTestHandler.obtainMessage(cmd);
            msg.arg1 = mCount;
            mTestHandler.sendMessageDelayed(msg, delayInMs);
        } else {
            if (delayInMs == 0) {
                mTestHandler.sendEmptyMessage(cmd);
            } else {
                mTestHandler.sendEmptyMessageDelayed(cmd, delayInMs);
            }
        }
    }

    private int chooseConfiguredNetwork(List<ScanResult> results, List<WifiConfiguration> configs) {
        int configIdx = -1;
        WifiConfiguration wifiConfig = null;

        if (results.size() != 0 && configs.size() != 0) {
            if (configs.size() >= 2) {
                wifiConfig = configs.get(mCount % 2);
            } else {
                wifiConfig = configs.get(0);
            }

            for (ScanResult result : results) {
                if (wifiConfig.SSID.contains(result.SSID)) {
                    configIdx = configs.indexOf(wifiConfig);
                    break;
                }
            }
        }
        return configIdx;
    }

    class TestHandler extends Handler {
        Message message;

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case Constants.MSG_CMD_INIT:
                    init();
                    break;

                case Constants.MSG_CMD_SCAN:
                    scan();
                    break;

                case Constants.MSG_CMD_WAIT_SCAN_RESULT:
                    Log.i(Constants.TAG, "Skip MSG_CMD_WAIT_SCAN_RESULT command");
                    break;

                case Constants.MSG_CMD_CONNECT:
                    connect(msg.arg1 == 1);
                    break;

                case Constants.MSG_CMD_WAIT_CONNECT_RESULT:
                    if (mCount == msg.arg1) {
                        complete();
                    }
                    break;

                case Constants.MSG_CMD_COMPLETE:
                    complete();
                    break;

                case Constants.MSG_CMD_STOP_SELF:
                    mUiHandler.sendEmptyMessage(Constants.MSG_UI_TEST_COMPLETE);
                    stopSelf();
                    break;

                case Constants.MSG_CMD_STOP:
                    mUiHandler.sendEmptyMessage(Constants.MSG_UI_TEST_STOP);
                    stopSelf();
                    break;

                default:
                    Log.i(Constants.TAG, "Skip this command, " + msg.what);
                    break;
            }
        }
    }
}
