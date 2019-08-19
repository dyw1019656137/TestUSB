package com.epoint.testusb;

import android.util.Log;

import com.epoint.core.application.FrmApplication;
import com.epoint.testusb.host.utils.CrashHandler;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 10:12
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class MyApplication extends FrmApplication {

    private static final String TAG = "testUsbCommunication";

    @Override
    public void onCreate() {
        super.onCreate();

        CrashHandler.getInstance().init(this);
    }

    public static void printLogDebug(String logString) {
        Log.d(TAG, logString);
    }
}
