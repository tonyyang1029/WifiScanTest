package com.oem.wifiscantest;

public class Constants {
    static final String TAG = "WifiScanTest";

    static final int MAX_TEST_COUNT                         = 2500;
    static final int MAX_SCAN_INTERVAL                      = 10000;
    static final int MAX_RECONNECT_INTERVAL                 = 5000;
    static final int MAX_TEST_INTERVAL                      = 10000;
    static final int MAX_WAIT_TIME                          = 30000;

    static final int MSG_UI_LOG_NOP                         = 0;
    static final int MSG_UI_LOG_SET                         = 1;
    static final int MSG_UI_LOG_APPEND                      = 2;
    static final int MSG_UI_STATE_SET                       = 3;
    static final int MSG_UI_STATE_APPEND                    = 4;
    static final int MSG_UI_STOP                            = 5;

    static final int TEST_STATE_UNKNOWN                     = -1;
    static final int TEST_STATE_IDLE                        = 0;
    static final int TEST_STATE_INITIALIZING                = 1;
    static final int TEST_STATE_SCANNING                    = 2;
    static final int TEST_STATE_RESCANNING                  = 3;
    static final int TEST_STATE_SCANNED                     = 4;
    static final int TEST_STATE_RESCANNED                   = 5;
    static final int TEST_STATE_SCAN_TIMEOUT                = 6;
    static final int TEST_STATE_CONNECTING                  = 7;
    static final int TEST_STATE_CONNECTED                   = 8;
    static final int TEST_STATE_CONNECT_TIMEOUT             = 9;
    static final int TEST_STATE_STOP                        = 10;

    static final int TEST_CMD_UNKNOWN                       = -1;
    static final int TEST_CMD_INIT                          = 0;
    static final int TEST_CMD_SCAN                          = 1;
    static final int TEST_CMD_CHECK_SCAN_RESULT             = 2;
    static final int TEST_CMD_SCAN_TIMEOUT                  = 3;
    static final int TEST_CMD_CONNECT                       = 4;
    static final int TEST_CMD_CHECK_CONNECT_RESULT          = 5;
    static final int TEST_CMD_CONNECT_TIMEOUT               = 6;
    static final int TEST_CMD_RESCAN                        = 7;
    static final int TEST_CMD_CHECK_RESCAN_RESULT           = 8;
    static final int TEST_CMD_RECONNECT                     = 9;
    static final int TEST_CMD_STOP                          = 10;
    static final int TEST_CMD_STOP_SELF                     = 11;

    static final int SAVE_TYPE_UI                           = 1;
    static final int SAVE_TYPE_LOGCAT                       = 1 << 1;
    static final int SAVE_TYPE_FILE                         = 1 << 2;
    static final int SAVE_TYPE_ALL                          = SAVE_TYPE_UI | SAVE_TYPE_LOGCAT | SAVE_TYPE_FILE;
}
