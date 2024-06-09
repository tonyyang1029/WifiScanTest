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
import java.util.Random;

public class TestRunner {
    private Context mCtxt;
    private Handler mUiHandler;
    private WifiManager mWifiManager;
    private TestHandler mTestHandler;
    private WifiReceiver mScanReceiver = null;
    private WifiReceiver mReScanReceiver = null;
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
    private FileOutputStream mStateFile = null;

    public TestRunner(Context ctxt, Handler uiHandler) {
        mCtxt = ctxt;
        mUiHandler = uiHandler;
        mState = Constants.TEST_STATE_IDLE;

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

                case Constants.TEST_CMD_CHECK_SCAN_RESULT:
                    checkScanResult(msg.arg1);
                    break;

                case Constants.TEST_CMD_SCAN_TIMEOUT:
                    if (mTestCount == msg.arg1) {
                        scanTimeout();
                    } else {
                        saveLog("-> Skip TEST_CMD_SCAN_TIMEOUT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                    }
                    break;

                case Constants.TEST_CMD_RESCAN:
                    rescan();
                    break;

                case Constants.TEST_CMD_CHECK_RESCAN_RESULT:
                    checkReScanResult();
                    break;

                case Constants.TEST_CMD_CONNECT:
                    connect();
                    break;

                case Constants.TEST_CMD_RECONNECT:
                    reconnect();
                    break;

                case Constants.TEST_CMD_CHECK_CONNECT_RESULT:
                    checkConnectResult();
                    break;

                case Constants.TEST_CMD_CONNECT_TIMEOUT:
                    if (mTestCount == msg.arg1) {
                        connectTimeout();
                    } else {
                        saveLog("-> Skip TEST_CMD_CONNECT_TIMEOUT command", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                    }
                    break;

                case Constants.TEST_CMD_STOP_SELF:
                    stopBySelf();
                    break;

                case Constants.TEST_CMD_STOP:
                    stopByUser();
                    break;

                default:
                    saveLog("Skip this command, " + msg.what, Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                    break;
            }
        }
    }

    public void start() {
        if (mState == Constants.TEST_STATE_IDLE ) {
            saveLog("-> Start Wi-Fi tester", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            jumpTo(Constants.TEST_CMD_INIT, 0);
        } else {
            saveLog("-> Skip, Wi-Fi tester is already running", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
        }
    }

    public void stop() {
        if (mState != Constants.TEST_STATE_IDLE) {
            saveLog("-> Stop Wi-Fi tester", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            jumpTo(Constants.TEST_CMD_STOP, 0);
        } else {
            saveLog("-> Skip, Wi-Fi tester is already stopped", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
        }
    }

    private void init() {
        mState = Constants.TEST_STATE_INITIALIZING;

        mTestCount = 0;
        mScanSuccess = 0;
        mScanFailure = 0;
        mScanTimeout = 0;
        mConnectionSuccess = 0;
        mConnectionTimeout = 0;

        saveLog("Initializing Wi-Fi tester", Constants.SAVE_TYPE_UI | Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_SET);
        jumpTo(Constants.TEST_CMD_SCAN, 0);
    }

    private void scan() {
        if (mTestCount == Constants.MAX_TEST_COUNT) {
            jumpTo(Constants.TEST_CMD_STOP_SELF, 0);
            return;
        }

        mState = Constants.TEST_STATE_SCANNING;
        mTestCount++;
        saveLog("No. " + mTestCount + " Test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_SET);
        saveLog("-> Start scanning test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveLog("-> Scanning", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveState(Constants.MSG_UI_STATE_SET, "No. " + mTestCount + " Test");
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");

        if (mScanReceiver == null) {
            mScanReceiver = new WifiReceiver(Constants.TEST_CMD_CHECK_SCAN_RESULT, mTestHandler);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mScanReceiver, intentFilter);

        SystemClock.sleep(500);
        mWifiManager.startScan();

        jumpTo(Constants.TEST_CMD_SCAN_TIMEOUT, Constants.MAX_WAIT_TIME);
    }

    private synchronized void checkScanResult(int updated) {
        // TODO: debug code
        // if (mTestCount % 3 == 0) {
        //     return;
        // }

        if (mState != Constants.TEST_STATE_SCANNING) return;

        mState = Constants.TEST_STATE_SCANNED;
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");

        mTestHandler.removeMessages(Constants.TEST_CMD_SCAN_TIMEOUT);

        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }

        List<ScanResult> results = mWifiManager.getScanResults();
        if (updated == 1) {
            mScanSuccess++;
            saveLog("-> Scanned, Success, " + results.size() + " Wi-Fi APs", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        } else {
            mScanFailure++;
            saveLog("-> Scanned, Failure", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        }
        saveLog("-> Total Test: " + mTestCount + ", Scan Success: " + mScanSuccess + ", Scan Failure: " + mScanFailure + ", Scan Timeout: " + mScanTimeout, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveLog("-> End scanning test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        jumpTo(Constants.TEST_CMD_CONNECT, 0);
    }

    private synchronized void scanTimeout() {
        mState = Constants.TEST_STATE_SCAN_TIMEOUT;
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");
        mScanTimeout++;
        saveLog("-> Scanning is timeout", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveLog("-> Total Test: " + mTestCount + ", Scan Success: " + mScanSuccess + ", Scan Failure: " + mScanFailure + ", Scan Timeout: " + mScanTimeout, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);

        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }

        saveLog("-> End scanning test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        jumpTo(Constants.TEST_CMD_CONNECT, 0);
    }

    private synchronized void connect() {
        List<ScanResult> results = mWifiManager.getScanResults();
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int configIdx = -1;

        configIdx = chooseConfiguredNetwork(results, configs);
        // TODO: debug coce
        // configIdx = -1;
        // configIdx = -2;
        // configIdx = -3;
        if ( configIdx >= 0 ) {
            if (mState == Constants.TEST_STATE_SCANNED || mState == Constants.TEST_STATE_SCAN_TIMEOUT) {
                saveLog("\n", Constants.SAVE_TYPE_UI, Constants.MSG_UI_LOG_APPEND);
                saveLog("-> Start connecting", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            }

            String ssid = configs.get(configIdx).SSID;
            mConnectingSsid = ssid.substring(1, ssid.length() - 1);

            mState = Constants.TEST_STATE_CONNECTING;
            saveLog("-> Connecting to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");

            if (mConnectReceiver == null) {
                mConnectReceiver = new WifiReceiver(Constants.TEST_CMD_CHECK_CONNECT_RESULT, mTestHandler);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mCtxt.registerReceiver(mConnectReceiver, intentFilter);
            SystemClock.sleep(500);

            mWifiManager.disconnect();
            SystemClock.sleep(500);
            mWifiManager.enableNetwork(configs.get(configIdx).networkId, true);

            jumpTo(Constants.TEST_CMD_CONNECT_TIMEOUT, Constants.MAX_WAIT_TIME);
        } else if (configIdx == -1 || configIdx == -2) {
            if (mState == Constants.TEST_STATE_SCANNED || mState == Constants.TEST_STATE_SCAN_TIMEOUT) {
                saveLog("\n", Constants.SAVE_TYPE_UI, Constants.MSG_UI_LOG_APPEND);
                saveLog("-> Start connecting", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            }
            saveLog("-> Choosing configured network is failed, " + configIdx, Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            jumpTo(Constants.TEST_CMD_CONNECT_TIMEOUT, Constants.MAX_WAIT_TIME);
            jumpTo(Constants.TEST_CMD_RESCAN, Constants.MAX_SCAN_INTERVAL);
        } else {
            saveLog("-> There are no configured networks in Wi-Fi settings", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_SCAN_INTERVAL);
        }
    }

    private synchronized void checkConnectResult() {
        if (mState != Constants.TEST_STATE_CONNECTING) return;

        WifiInfo info = mWifiManager.getConnectionInfo();
        String ssid = info.getSSID().substring(1, info.getSSID().length() - 1);
        if (mConnectingSsid.equals(ssid)) {
            mTestHandler.removeMessages(Constants.TEST_CMD_RECONNECT);
            mTestHandler.removeMessages(Constants.TEST_CMD_CONNECT_TIMEOUT);
            mConnectionSuccess++;

            if (mConnectReceiver != null) {
                mCtxt.unregisterReceiver(mConnectReceiver);
                mConnectReceiver = null;
            }

            mState = Constants.TEST_STATE_CONNECTED;
            saveLog("-> Connected to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            saveLog("-> Total Test: " + mTestCount + ", Connection Success: " + mConnectionSuccess + ", Connection Timeout: " + mConnectionTimeout, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");
            saveLog("-> End connecting test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
            jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_TEST_INTERVAL);
        } else {
            jumpTo(Constants.TEST_CMD_RECONNECT, Constants.MAX_RECONNECT_INTERVAL);
        }
    }

    private synchronized void connectTimeout() {
        mTestHandler.removeMessages(Constants.TEST_CMD_RESCAN);
        mTestHandler.removeMessages(Constants.TEST_CMD_RECONNECT);
        mTestHandler.removeMessages(Constants.TEST_CMD_CONNECT_TIMEOUT);

        if (mReScanReceiver != null) {
            mCtxt.unregisterReceiver(mReScanReceiver);
            mReScanReceiver = null;
        }

        if (mConnectReceiver != null) {
            mCtxt.unregisterReceiver(mConnectReceiver);
            mConnectReceiver = null;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String ssid = info.getSSID().substring(1, info.getSSID().length() - 1);
        if ((mState == Constants.TEST_STATE_CONNECTING) && (mConnectingSsid.equals(ssid))) {
            mConnectionSuccess++;
            mState = Constants.TEST_STATE_CONNECTED;
            saveLog("-> Connected to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        } else {
            mConnectionTimeout++;
            mState = Constants.TEST_STATE_CONNECT_TIMEOUT;
            saveLog("-> Connecting is timeout", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        }
        saveLog("-> Total Test: " + mTestCount + ", Success: " + mConnectionSuccess + ", Timeout: " + mConnectionTimeout, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");
        saveLog("-> End connecting test", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);

        jumpTo(Constants.TEST_CMD_SCAN, Constants.MAX_TEST_INTERVAL);
    }

    private void rescan() {
        mState = Constants.TEST_STATE_RESCANNING;
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");
        saveLog("-> Re-scanning", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);

        if (mReScanReceiver == null) {
            mReScanReceiver = new WifiReceiver(Constants.TEST_CMD_CHECK_RESCAN_RESULT, mTestHandler);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mCtxt.registerReceiver(mReScanReceiver, intentFilter);

        SystemClock.sleep(500);
        mWifiManager.startScan();
    }

    private synchronized void checkReScanResult() {
        if (mState != Constants.TEST_STATE_RESCANNING) return;

        mState = Constants.TEST_STATE_RESCANNED;
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");
        saveLog("-> Re-scanned", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);

        if (mReScanReceiver != null) {
            mCtxt.unregisterReceiver(mReScanReceiver);
            mReScanReceiver = null;
        }

        jumpTo(Constants.TEST_CMD_CONNECT, 0);
    }

    private synchronized void reconnect() {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int configIdx = -1;
        for (WifiConfiguration config : configs) {
            String ssid = config.SSID.substring(1, config.SSID.length() - 1);
            if (mConnectingSsid.equals(ssid)) {
                configIdx = configs.indexOf(config);
                break;
            }
        }

        mWifiManager.disconnect();
        SystemClock.sleep(500);
        mWifiManager.enableNetwork(configs.get(configIdx).networkId, true);

        saveLog("-> Re-connecting to " + mConnectingSsid, Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
    }

    private void stopBySelf() {
        saveLog("-> Reach to max testing times, Test Complete", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        stopTest();
    }

    private void stopByUser() {
        saveLog("-> Manually stop testing", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        stopTest();
    }

    private void stopTest() {
        mState = Constants.TEST_STATE_STOP;
        saveLog("-> Stop", Constants.SAVE_TYPE_ALL, Constants.MSG_UI_LOG_APPEND);
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");

        mTestHandler.removeCallbacksAndMessages(null);

        if (mScanReceiver != null) {
            mCtxt.unregisterReceiver(mScanReceiver);
            mScanReceiver = null;
        }

        if (mReScanReceiver != null) {
            mCtxt.unregisterReceiver(mReScanReceiver);
            mReScanReceiver = null;
        }

        if (mConnectReceiver != null) {
            mCtxt.unregisterReceiver(mConnectReceiver);
            mConnectReceiver = null;
        }

        mState = Constants.TEST_STATE_IDLE;
        saveState(Constants.MSG_UI_STATE_APPEND, "-> [" + getStateText(mState) + "]");

        if (mLogFile != null) {
            try {
                mLogFile.close();
            } catch (IOException e) {
                saveLog("Log file is not closed successfully", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            }
            mLogFile = null;
        }

        if (mStateFile != null) {
            try {
                mStateFile.close();
            } catch (IOException e) {
                saveLog("State file is not closed successfully", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            }
            mStateFile = null;
        }

        mUiHandler.sendEmptyMessage(Constants.MSG_UI_STOP);
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
            if (configs.size() == 1) {
                wifiConfig = configs.get(0);
            } else {
                Random random = new Random();
                int randomIdx = random.nextInt(configs.size());
                wifiConfig = configs.get(randomIdx);
            }

            String ssid =  wifiConfig.SSID.substring(1, wifiConfig.SSID.length() - 1);
            for (ScanResult result : results) {
                if (ssid.equals(result.SSID)) {
                    configIdx = configs.indexOf(wifiConfig);
                    break;
                }
            }

            if (configIdx == -1) {
                saveLog("The configured network is not found.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
            }
        } else if (results.isEmpty()) {
            configIdx = -2;
            saveLog("Scan Results: 0", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
        } else {
            configIdx = -3;
            saveLog("Configured Networks: 0", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
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
            saveLogToFile(log + "\n");
        }
    }

    private void saveLogToFile(String log) {
        if (mLogFile == null) {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String name = "wifiscantest_log_" + dateFormat.format(new Date()) + ".log";
            try {
                File file = new File(dir, name);
                file.createNewFile();
                mLogFile = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                saveLog("Log file is not found.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                return;
            } catch (IOException e) {
                saveLog("Log file is not created.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                return;
            }
        }

        try {
            mLogFile.write(log.getBytes());
        } catch (IOException e) {
            saveLog("Log is not saved into file.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
        }
    }

    private void saveState(int msgWhat, String state) {
        Message msg = mUiHandler.obtainMessage(msgWhat, state + "\n");
        mUiHandler.sendMessage(msg);
        saveStateToFile(state + "\n");
    }

    private void saveStateToFile(String state) {
        if (mStateFile == null) {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String name = "wifiscantest_state_" + dateFormat.format(new Date()) + ".log";
            try {
                File file = new File(dir, name);
                file.createNewFile();
                mStateFile = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                saveLog("State file is not found.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                return;
            } catch (IOException e) {
                saveLog("State file is not created.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
                return;
            }
        }

        try {
            mStateFile.write(state.getBytes());
        } catch (IOException e) {
            saveLog("State is not saved into file.", Constants.SAVE_TYPE_LOGCAT, Constants.MSG_UI_LOG_NOP);
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

            case Constants.TEST_STATE_RESCANNED:
                text = "TEST_STATE_RESCANNED";
                break;

            case Constants.TEST_STATE_SCAN_TIMEOUT:
                text = "TEST_STATE_SCAN_TIMEOUT";
                break;

            case Constants.TEST_STATE_CONNECTING:
                text = "TEST_STATE_CONNECTING";
                break;

            case Constants.TEST_STATE_CONNECTED:
                text = "TEST_STATE_CONNECTED";
                break;

            case Constants.TEST_STATE_CONNECT_TIMEOUT:
                text = "TEST_STATE_CONNECT_TIMEOUT";
                break;

            case Constants.TEST_STATE_STOP:
                text = "TEST_STATE_STOP";
                break;
        }

        return text;
    }
}
