package com.epoint.testusb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.epoint.ui.baseactivity.FrmBaseActivity;

import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 10:37
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class UsbHostActivity extends FrmBaseActivity {

    @BindView(R.id.tv_host_message)
    TextView tvHostMessage;

    @BindView(R.id.et_host_message)
    TextView etHostMessage;

    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private PendingIntent mPermissionIntent;
    private CommunicationThread mCommThread;
    private String mData;
    private volatile boolean mExit = false;

    private static final String ACTION_USB_PERMISSION = "com.mobilemerit.usbhost.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLayout(R.layout.usb_host_activity);
        initView();
    }

    private void initView() {
        setTitle("Host设备");
        getNbViewHolder().nbBack.setVisibility(View.GONE);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    }

    @OnClick({R.id.btn_get_device, R.id.btn_send_message, R.id.btn_get_file})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_get_device:
                pageControl.showLoading("获取中");
                checkDevice();
                break;
            case R.id.btn_send_message:
                setText("发送信息内容："+etHostMessage.getText().toString());
                break;
            case R.id.btn_get_file:
                toast("选择文件发送");
                break;
        }
    }

//    private void initStringControlTransfer(final UsbDeviceConnection deviceConnection, final int index, final String string) {
//        deviceConnection.controlTransfer(0x40, 52, 0, index, string.getBytes(), string.length(), 100);
//    }

    private class CommunicationThread extends Thread {

        @Override
        public void run() {
            mExit = false;
            UsbEndpoint endpointIn = null;
            UsbEndpoint endpointOut = null;
            UsbInterface usbInterface = mDevice.getInterface(0);
            UsbDeviceConnection connection = mUsbManager.openDevice(mDevice);
            if (connection == null) {
                return;
            }
            if (!connection.claimInterface(usbInterface, true)) {
                connection.close();
                return;
            }

            // 发送控制消息
//            initStringControlTransfer(connection, 0, "UsbTest Example"); // MANUFACTURER
//            initStringControlTransfer(connection, 1, "UsbTest"); // MODEL
//            initStringControlTransfer(connection, 2, "Test Usb Host and Accessory"); // DESCRIPTION
//            initStringControlTransfer(connection, 3, "0.1"); // VERSION
//            initStringControlTransfer(connection, 4, ""); // URI
//            initStringControlTransfer(connection, 5, "42"); // SERIAL
//            connection.controlTransfer(0x40, 53, 0, 0, new byte[]{}, 0, 100);

            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                final UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint;
                }
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    endpointOut = endpoint;
                }
            }
            if (endpointOut == null){
                setText("输出端口为空");
                return;
            }
//            byte[] data = etHostMessage.getText().toString().getBytes();
//            int ret = connection.bulkTransfer(endpointOut, data, data.length, 200);
//            if (ret > 0) {
//                setText("发送成功："+etHostMessage.getText().toString());
//            } else {
//                setText("发送失败");
//            }

            if (endpointIn == null){
                setText("输入端口为空");
                return;
            }
            while (!mExit){
                //接收消息
                int inMax = endpointIn.getMaxPacketSize();
                ByteBuffer byteBuffer = ByteBuffer.allocate(inMax);
                UsbRequest usbRequest = new UsbRequest();
                usbRequest.initialize(connection, endpointIn);
                usbRequest.queue(byteBuffer, inMax);
                if (connection.requestWait() == usbRequest) {
                    byte[] retData = byteBuffer.array();
//                for(Byte byte1 : retData){
//                    System.err.println(byte1);
//                }
                    try {
                        setText("数据接收成功：" + new String(retData, "utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        setText("数据接收成功：byte数组转String失败");
                        e.printStackTrace();
                    }
                }

                //发送消息
                mData = etHostMessage.getText().toString();
                synchronized (mData) {
                    if (!TextUtils.isEmpty(mData)){
                        // 此方法可能不可以发送，会返回错误
                        byte[] sendBuff = mData.getBytes();
                        int len = connection.bulkTransfer(endpointOut, sendBuff, sendBuff.length, 100);
                        if (len >= 0) {
                            setText("发送成功："+mData);
                        } else {
                            setText("发送失败");
                        }
                        // 此方法可能可以发送，不会返回错误
//                        UsbRequest request = new UsbRequest();
//                        request.initialize(connection, endpointOut);
//                        ByteBuffer buffer = ByteBuffer.wrap(mData.getBytes());
//                        boolean ret = request.queue(buffer, mData.getBytes().length);
//                        if (ret) {
//                            setText("发送成功："+mData);
//                        }else {
//                            setText("发送失败");
//                        }
                    }
                }
            }
        }
    }

    private void checkDevice() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        pageControl.hideLoading();
        if (deviceList == null || deviceList.isEmpty()) {
            setText("checkDevice fail");
            return;
        }
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            setText("device_name:" + device.getDeviceName());
            mUsbManager.requestPermission(device, mPermissionIntent);
            break;
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        setText("授权成功");
                        if (device != null) {
                            mDevice = device;
                            mCommThread = new CommunicationThread();
                            mCommThread.start();
                        }
                    } else {
                        setText("授权失败");
                    }
                }
            }
        }
    };

    private void setText(String text) {
        final String newText = "\n" + text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvHostMessage.setText(tvHostMessage.getText() + newText);
            }
        });
    }
}
