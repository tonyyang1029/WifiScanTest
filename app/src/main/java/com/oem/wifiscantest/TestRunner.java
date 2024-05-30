package com.oem.wifiscantest;

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TestRunner {
    private Context mCtxt;
    private Handler mUiHandler;
    private WifiManager mWifiManager;
    private TestHandler mTestHandler;
    private WifiReceiver mScanReceiver = null;
    private WifiReceiver mConnectReceiver = null;
    private int mState = Constants.MSG_CMD_UNKNOWN;
    private int mScanCount = 0;
    private int mScanSuccess = 0;
    private int mScanFailure = 0;
    private int mConnectionCount = 0;
    private int mConnectionSuccess = 0;
    private int mConnectionTimeout = 0;
    private String mConnectingSsid = null;
    private FileOutputStream mLogFile = null;

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
        mScanCount = 0;
        mScanSuccess = 0;
        mScanFailure = 0;
        mConnectionCount = 0;
        mConnectionSuccess = 0;
        mConnectionTimeout = 0;

        saveLog("Initializing Wi-Fi tester", Constants.SAVE_TYPE_UI | Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_SHOW_TEXT);

        //mWifiManager.setWifiEnabled(false);
        jumpTo(Constants.MSG_CMD_SCAN, 0);
    }

    private void scan() {
        if (mScanCount == Constants.MAX_TEST_COUNT) {
            jumpTo(Constants.MSG_CMD_STOP_SELF, 0);
            return;
        }

        mScanCount++;

        saveLog("No. " + mScanCount + " Test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_START);
        saveLog("-> Start scanning", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);

        if (mScanReceiver == null) {
            mScanReceiver = new WifiReceiver(mTestHandler);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mScanReceiver, intentFilter);

        SystemClock.sleep(500);

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

        if (success) {
            mScanSuccess++;
            saveLog("-> Receive scanning result -> Success, " + results.size() + " Wi-Fi APs", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
        } else {
            mScanFailure++;
            saveLog("-> Receive scanning result -> Failure", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
        }


        saveLog("-> Total Scan: " + mScanCount + ", Success: " + mScanSuccess + ", Failure: " + mScanFailure, Constants.SAVE_TYPE_ALL,Constants.MSG_UI_TEST_PROGRESS);

        configIdx = chooseConfiguredNetwork(results, configs);
        if ( configIdx != -1) {
            String ssid = configs.get(configIdx).SSID;
            mConnectingSsid = ssid.substring(1, ssid.length() - 1);
            saveLog("-> Start connecting to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);

            if (mConnectReceiver == null) {
                mConnectReceiver = new WifiReceiver(mTestHandler);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mCtxt.registerReceiver(mConnectReceiver, intentFilter);
            SystemClock.sleep(500);

            mWifiManager.disconnect();
            SystemClock.sleep(500);
            mConnectionCount++;
            mWifiManager.enableNetwork(configs.get(configIdx).networkId, true);

            jumpTo(Constants.MSG_CMD_WAIT_CONNECT_RESULT, Constants.MAX_WAIT_TIME);
        } else {
            jumpTo(Constants.MSG_CMD_SCAN, Constants.MAX_INTERVAL_TIME);
        }
    }

    private synchronized void finish(boolean timeout) {
        if (mState != Constants.MSG_CMD_WAIT_CONNECT_RESULT) {
            return;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String ssid = info.getSSID().substring(1, info.getSSID().length() - 1);

        if (mConnectingSsid.equals(ssid)) {
            mConnectionSuccess++;
            mTestHandler.removeMessages(Constants.MSG_CMD_WAIT_CONNECT_RESULT);
            if (mConnectReceiver != null) {
                mCtxt.unregisterReceiver(mConnectReceiver);
                mConnectReceiver = null;
            }
            jumpTo(Constants.MSG_CMD_SCAN, Constants.MAX_INTERVAL_TIME);
            saveLog("-> Connected to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            saveLog("-> Total Connection: " + mConnectionCount + ", Success: " + mConnectionSuccess + ", Timeout: " + mConnectionTimeout, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
        } else {
            if (timeout) {
                mConnectionTimeout++;
                if (mConnectReceiver != null) {
                    mCtxt.unregisterReceiver(mConnectReceiver);
                    mConnectReceiver = null;
                }
                jumpTo(Constants.MSG_CMD_SCAN, Constants.MAX_INTERVAL_TIME);
                saveLog("-> Connecting to " + mConnectingSsid + "is timeout", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            }
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
        if (mLogFile != null) {
            try {
                mLogFile.close();
            } catch (IOException e) {
                saveLog("Log file is not closed successfully.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
            }
            mLogFile = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
    }

    private void jumpTo(int cmd, int delayInMs) {
        mState = cmd;

        if (cmd == Constants.MSG_CMD_WAIT_CONNECT_RESULT) {
            Message msg = mTestHandler.obtainMessage(cmd);
            msg.arg1 = mScanCount;
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

        if (!results.isEmpty() && !configs.isEmpty()) {
            if (configs.size() >= 2) {
                wifiConfig = configs.get(mScanCount % 2);
            } else {
                wifiConfig = configs.get(0);
            }

            String ssid =  wifiConfig.SSID.substring(1, wifiConfig.SSID.length() - 1);
            for (ScanResult result : results) {
                if (ssid.equals(result.SSID)) {
                    configIdx = configs.indexOf(wifiConfig);
                    break;
                }
            }

            if (configIdx == -1) {
                saveLog("The configured network is not found.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
            }
        } else if (configs.isEmpty()) {
            saveLog("Configured Networks: 0", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
        } else {
            saveLog("Scan Results: 0", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
        }

        return configIdx;
    }

    private void saveLog(String log, int types, int ui) {
        if ((types & Constants.SAVE_TYPE_UI) == Constants.SAVE_TYPE_UI) {
            Message msg = mUiHandler.obtainMessage(ui);
            msg.obj = log + "\n";
            mUiHandler.sendMessage(msg);
        }

        if ((types & Constants.SAVE_TYPE_LOGCAT) == Constants.SAVE_TYPE_LOGCAT) {
            Log.i(Constants.TAG, log);
        }

        if ((types & Constants.SAVE_TYPE_FILE) == Constants.SAVE_TYPE_FILE) {
            saveToFile(log + "\n");
        }
    }
    private void saveToFile(String log) {
        if (mLogFile == null) {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String name = "wifiscantest_" + dateFormat.format(new Date()) + ".log";
            try {
                File file = new File(dir, name);
                file.createNewFile();
                mLogFile = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                saveLog("Log file is not found.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                return;
            } catch (IOException e) {
                saveLog("Log file is not created.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                return;
            }
        }

        try {
            mLogFile.write(log.getBytes());
        } catch (IOException e) {
            saveLog("Log is not saved into file.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
        }
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
                    saveLog("Skip MSG_CMD_WAIT_SCAN_RESULT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    break;

                case Constants.MSG_CMD_CONNECT:
                    connect(msg.arg1 == 1);
                    break;

                case Constants.MSG_CMD_WAIT_CONNECT_RESULT:
                    if (mScanCount == msg.arg1) {
                        finish(true);
                    } else {
                        saveLog("Skip MSG_CMD_WAIT_CONNECT_RESULT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    }
                    break;

                case Constants.MSG_CMD_FINISH:
                    finish(false);
                    break;

                case Constants.MSG_CMD_STOP_SELF:
                    saveLog("Reach to max testing times! Test Complete!", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_COMPLETE);
                    stopSelf();
                    break;

                case Constants.MSG_CMD_STOP:
                    saveLog("Manually stop testing!", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_STOP);
                    stopSelf();
                    break;

                default:
                    saveLog("Skip this command, " + msg.what, Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    break;
            }
        }
    }
}
