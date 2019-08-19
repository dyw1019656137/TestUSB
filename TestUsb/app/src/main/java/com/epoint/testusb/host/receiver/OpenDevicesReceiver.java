package com.epoint.testusb.host.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 15:28
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： 打开device设备通知
 */
public class OpenDevicesReceiver  extends BroadcastReceiver {

    private OpenDevicesListener mOpenDevicesListener;

    public OpenDevicesReceiver(OpenDevicesListener openDevicesListener) {
        mOpenDevicesListener = openDevicesListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            if (usbDevice != null) {
                mOpenDevicesListener.openAccessoryModel(usbDevice);
            } else {
                mOpenDevicesListener.openDevicesError();
            }
        } else {
            mOpenDevicesListener.openDevicesError();
        }
    }

    public interface OpenDevicesListener {
        /**
         * 打开Accessory模式
         *
         * @param usbDevice
         */
        void openAccessoryModel(UsbDevice usbDevice);

        /**
         * 打开设备(手机)失败
         */
        void openDevicesError();
    }
}
