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
    private int mState = Constants.TEST_STATE_UNKNOWN;
    private int mTestCount = 0;
    private int mScanSuccess = 0;
    private int mScanFailure = 0;
    private int mScanTimeout = 0;
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

    class TestHandler extends Handler {
        Message message;

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case Constants.TEST_CMD_INIT:
                    init();
                    break;

                case Constants.TEST_CMD_SCAN:
                    scan();
                    break;

                case Constants.TEST_CMD_RESCAN:
                    rescan();
                    break;

                case Constants.TEST_CMD_SCAN_TIMEOUT:
                    if (mTestCount == msg.arg1) {
                        checkScanResult(true, 0);
                    } else {
                        saveLog("Skip TEST_CMD_SCAN_TIMEOUT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    }
                    break;

                case Constants.TEST_CMD_CHECK_SCAN_RESULT:
                    checkScanResult(msg.arg1);
                    break;

                case Constants.TEST_CMD_CONNECT:
                    connect();
                    break;

                case Constants.TEST_CMD_CHECK_CONNECT_RESULT:
                    checkConnectResult();
                    break;

                case Constants.TEST_CMD_CONNECT_TIMEOUT:
                    if (mTestCount == msg.arg1) {
                        checkConnectResult(true);
                    } else {
                        saveLog("Skip TEST_CMD_CONNECT_TIMEOUT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    }
                    break;

                case Constants.TEST_CMD_STOP_SELF:
                    stopSelf();
                    break;

                case Constants.TEST_CMD_STOP:
                    stopTest();
                    saveLog("-> Manually stop testing -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_STOP);
                    break;

                default:
                    saveLog("Skip this command, " + msg.what, Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
                    break;
            }
        }
    }

    public void start() {
        saveLog("The start() is called.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_NOP);
        jumpTo(Constants.TEST_CMD_INIT, 0);
    }

    public void stop() {
        saveLog("The stop() is called.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_NOP);
        jumpTo(Constants.TEST_CMD_STOP, 0);
    }

    private void init() {
        mState = Constants.TEST_STATE_INITIALIZING;
        mTestCount = 0;
        mScanSuccess = 0;
        mScanFailure = 0;
        mScanTimeout = 0;
        mConnectionSuccess = 0;
        mConnectionTimeout = 0;

        saveLog("Initializing Wi-Fi tester", Constants.SAVE_TYPE_UI | Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_SHOW_TEXT);
        jumpTo(Constants.TEST_CMD_SCAN, 0);
    }

    private void scan() {
        if (mTestCount == Constants.MAX_TEST_COUNT) {
            jumpTo(Constants.TEST_CMD_STOP_SELF, 0);
            return;
        }

        mState = Constants.TEST_STATE_SCANNING;
        mTestCount++;
        saveLog("No. " + mTestCount + " Test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_START);
        saveLog("-> Start scanning -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);

        if (mScanReceiver == null) {
            mScanReceiver = new WifiReceiver(mTestHandler);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mScanReceiver, intentFilter);
        SystemClock.sleep(500);
        mWifiManager.startScan();
        jumpTo(Constants.TEST_CMD_SCAN_TIMEOUT, Constants.MAX_WAIT_TIME);
    }

    private void rescan() {
        mState = Constants.TEST_STATE_RESCANNING;
        if (mScanReceiver == null) {
            mScanReceiver = new WifiReceiver(mTestHandler);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mScanReceiver, intentFilter);
        SystemClock.sleep(500);
        mWifiManager.startScan();
        jumpTo(Constants.TEST_CMD_SCAN_TIMEOUT, Constants.MAX_WAIT_TIME);
        saveLog("Re-scan Wi-Fi access points.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_NOP);
    }

    private void checkScanResult(int updated) {
        checkScanResult(false, updated);
    }

    private synchronized void checkScanResult(boolean timeout, int updated) {
        if (mState != Constants.TEST_STATE_SCANNING && mState != Constants.TEST_STATE_RESCANNING) {
            return;
        }

        mTestHandler.removeMessages(Constants.TEST_CMD_SCAN_TIMEOUT);
        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }

        if (!timeout) {
            if (mState == Constants.TEST_STATE_SCANNING) {
                mState = Constants.TEST_STATE_SCANNED;
                List<ScanResult> results = mWifiManager.getScanResults();
                if (updated == 1) {
                    mScanSuccess++;
                    saveLog("-> Receive scanning result -> Success, " + results.size() + " Wi-Fi APs", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
                } else {
                    mScanFailure++;
                    saveLog("-> Receive scanning result -> Failure", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
                }
                saveLog("-> Total Test: " + mTestCount + ", Success: " + mScanSuccess + ", Failure: " + mScanFailure + ", Timeout: " + mScanTimeout + " -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            }
            jumpTo(Constants.TEST_CMD_CONNECT, 0);
        } else {
            mState = Constants.TEST_STATE_IDLE;
            if (mState == Constants.TEST_STATE_SCANNING) {
                mScanTimeout++;
                saveLog("-> Total Test: " + mTestCount + ", Success: " + mScanSuccess + ", Failure: " + mScanFailure + ", Timeout: " + mScanTimeout + " -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            }
            jumpTo(Constants.TEST_CMD_CONNECT, Constants.MAX_SCAN_INTERVAL);
        }
    }

    private synchronized void connect() {
        List<ScanResult> results = mWifiManager.getScanResults();;
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int configIdx = -1;

        configIdx = chooseConfiguredNetwork(results, configs);
        if ( configIdx >= 0) {
            mState = Constants.TEST_STATE_CONNECTING;

            String ssid = configs.get(configIdx).SSID;
            mConnectingSsid = ssid.substring(1, ssid.length() - 1);
            saveLog("-> Start connecting to " + mConnectingSsid + " -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);

            if (mConnectReceiver == null) {
                mConnectReceiver = new WifiReceiver(mTestHandler);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mCtxt.registerReceiver(mConnectReceiver, intentFilter);
            SystemClock.sleep(500);

            mWifiManager.disconnect();
            SystemClock.sleep(500);
            mWifiManager.enableNetwork(configs.get(configIdx).networkId, true);

            jumpTo(Constants.TEST_CMD_CONNECT_TIMEOUT, Constants.MAX_WAIT_TIME);
        } else if (configIdx == -1) {
            jumpTo(Constants.TEST_CMD_RESCAN, Constants.MAX_SCAN_INTERVAL);
        } else {
            jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_SCAN_INTERVAL);
        }
    }

    private synchronized void checkConnectResult() {
        checkConnectResult(false);
    }

    private synchronized void checkConnectResult(boolean timeout) {
        if (mState != Constants.TEST_STATE_CONNECTING) {
            return;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String ssid = info.getSSID().substring(1, info.getSSID().length() - 1);

        if (mConnectingSsid.equals(ssid)) {
            mConnectionSuccess++;
            mTestHandler.removeMessages(Constants.TEST_CMD_CONNECT_TIMEOUT);
            if (mConnectReceiver != null) {
                mCtxt.unregisterReceiver(mConnectReceiver);
                mConnectReceiver = null;
            }
            mState = Constants.TEST_STATE_CONNECTED;
            jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_TEST_INTERVAL);
            saveLog("-> Connected to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            saveLog("-> Total Test: " + mTestCount + ", Success: " + mConnectionSuccess + ", Timeout: " + mConnectionTimeout + " -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
        } else {
            if (timeout) {
                mConnectionTimeout++;
                if (mConnectReceiver != null) {
                    mCtxt.unregisterReceiver(mConnectReceiver);
                    mConnectReceiver = null;
                }
                mState = Constants.TEST_STATE_IDLE;
                jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_TEST_INTERVAL);
                saveLog("-> Connecting to " + mConnectingSsid + "is timeout", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
                saveLog("-> Total Connection: " + mTestCount + ", Success: " + mConnectionSuccess + ", Timeout: " + mConnectionTimeout + " -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_PROGRESS);
            }
        }
    }

    private void stopSelf()
    {
        stopTest();
        saveLog("-> Reach to max testing times, Test Complete -> [" + getStateText(mState) + "]", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_TEST_COMPLETE);
    }

    private void stopTest() {
        mState = Constants.TEST_STATE_STOP;
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
                saveLog("Log file is not closed successfully", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
            }
            mLogFile = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
    }

    private void jumpTo(int cmd, int delayInMillis) {
        if (cmd != Constants.TEST_CMD_UNKNOWN) {
            if (cmd == Constants.TEST_CMD_SCAN_TIMEOUT ||
                cmd == Constants.TEST_CMD_CONNECT_TIMEOUT) {
                Message msg = mTestHandler.obtainMessage(cmd);
                msg.arg1 = mTestCount;
                mTestHandler.sendMessageDelayed(msg, Constants.MAX_WAIT_TIME);
            } else {
                if (delayInMillis == 0) {
                    mTestHandler.sendEmptyMessage(cmd);
                } else {
                    mTestHandler.sendEmptyMessageDelayed(cmd, delayInMillis);
                }
            }
        }
    }

    private int chooseConfiguredNetwork(List<ScanResult> results, List<WifiConfiguration> configs) {
        int configIdx = -1;
        WifiConfiguration wifiConfig = null;

        if (!results.isEmpty() && !configs.isEmpty()) {
            if (configs.size() >= 2) {
                wifiConfig = configs.get(mTestCount % 2);
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
            configIdx = -2;
            saveLog("Configured Networks: 0", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_UNKNOWN);
        } else {
            configIdx = -3;
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

    private String getStateText(int state) {
        String text = "";

        switch (state) {
            case Constants.TEST_STATE_UNKNOWN:
                text = "TEST_STATE_UNKNOWN";
                break;

            case Constants.TEST_STATE_IDLE:
                text = "TEST_STATE_IDLE";
                break;

            case Constants.TEST_STATE_INITIALIZING:
                text = "TEST_STATE_INITIALIZING";
                break;

            case Constants.TEST_STATE_SCANNING:
                text = "TEST_STATE_SCANNING";
                break;

            case Constants.TEST_STATE_RESCANNING:
                text = "TEST_STATE_RESCANNING";
                break;

            case Constants.TEST_STATE_SCANNED:
                text = "TEST_STATE_SCANNED";
                break;

            case Constants.TEST_STATE_CONNECTING:
                text = "TEST_STATE_CONNECTING";
                break;

            case Constants.TEST_STATE_CONNECTED:
                text = "TEST_STATE_CONNECTED";
                break;

            case Constants.TEST_STATE_STOP:
                text = "TEST_STATE_STOP";
                break;
        }

        return text;
    }
}
