package com.epoint.testusb.host.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.epoint.core.db.FrmDbUtil;
import com.epoint.core.util.common.FileSavePath;
import com.epoint.core.util.device.PermissionUtil;
import com.epoint.core.util.io.FileUtil;
import com.epoint.testusb.MyApplication;
import com.epoint.testusb.R;
import com.epoint.testusb.host.receiver.OpenDevicesReceiver;
import com.epoint.testusb.host.receiver.UsbDetachedReceiver;
import com.epoint.testusb.host.utils.FileTransformation;
import com.epoint.ui.baseactivity.FrmBaseActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 15:26
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class HostActivity extends FrmBaseActivity implements UsbDetachedReceiver.UsbDetachedListener, OpenDevicesReceiver.OpenDevicesListener {

    @BindView(R.id.tv_host_message)
    TextView tvHostMessage;

    @BindView(R.id.et_host_message)
    TextView etHostMessage;

    @BindView(R.id.btn_send_message)
    Button btnSendMessage;

    /**
     * 连接成功
     */
    private static final int CONNECTED_SUCCESS = 0;
    /**
     * 接收消息成功
     */
    private static final int RECEIVER_MESSAGE_SUCCESS = 1;
    /**
     * 发送消息成功
     */
    private static final int SEND_MESSAGE_SUCCESS = 2;
    /**
     * 自定义action
     */
    private static final String USB_ACTION = "com.epoint.testusb.host";
    /**
     * USB断开广播
     */
    private UsbDetachedReceiver mUsbDetachedReceiver;
    /**
     * 打开device设备通知
     */
    private OpenDevicesReceiver mOpenDevicesReceiver;
    private ExecutorService mThreadPool;

    private UsbManager mUsbManager;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbEndpoint mUsbEndpointOut;
    private UsbEndpoint mUsbEndpointIn;
    private boolean mToggle = true;
    private boolean isDetached = false;
    private byte[] mBytes = new byte[102400];
    private boolean isReceiverMessage = true;
    private UsbInterface mUsbInterface;
    private StringBuffer mStringBuffer = new StringBuffer();

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECTED_SUCCESS://车机和手机连接成功
                    setText("连接成功");
                    btnSendMessage.setEnabled(true);
                    loopReceiverMessage();
                    break;

                case RECEIVER_MESSAGE_SUCCESS://成功接受到数据
                    setText("--------成功接收数据--------\n");
                    mStringBuffer.delete(0,mStringBuffer.length());
                    break;

                case SEND_MESSAGE_SUCCESS://成功发送数据
                    setText("成功发送数据");
                    etHostMessage.setText("");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLayout(R.layout.usb_host_activity);

        init();
    }

    private void init() {
        setTitle("Host设备");
        getNbViewHolder().nbBack.setVisibility(View.GONE);

        if (!PermissionUtil.checkPermissionAllGranted(getContext(), PermissionUtil.PERMISSION_STORAGE)) {
            PermissionUtil.startRequestPermissions(getContext(), PermissionUtil.PERMISSION_STORAGE, PermissionUtil.REQUESTCODE_PERMISSION_STORAGE);
            return;
        }

        FileSavePath.getStoragePath();

        btnSendMessage.setEnabled(false);

        mUsbDetachedReceiver = new UsbDetachedReceiver(this);
        IntentFilter intentFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbDetachedReceiver, intentFilter);

        mThreadPool = Executors.newFixedThreadPool(5);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    @OnClick({R.id.btn_get_device, R.id.btn_send_message, R.id.btn_get_file})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_get_device:
                openDevices();
                break;
            case R.id.btn_send_message:
                final String message = etHostMessage.getText().toString();
                if (!TextUtils.isEmpty(message)) {
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            /**
                             * 发送数据的地方 , 只接受byte数据类型的数据
                             */
                            int i = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, message.getBytes(), message.getBytes().length, 3000);
                            if (i > 0) {//大于0表示发送成功
                                mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                            } else {
                                setText("发送失败");
                            }
                        }
                    });
                } else {
                    setText("发送信息内容不能为空");
                }
                break;
            case R.id.btn_get_file:

//                toast("选择文件发送");
                final String fileMessage = "file_start";
                final String fileMessageEnd = "file_end";
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        /**
                         * 发送数据的地方 , 只接受byte数据类型的数据
                         */
                        int i = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, fileMessage.getBytes(), fileMessage.getBytes().length, 3000);
                        if (i > 0) {//大于0表示发送成功
                            mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                            setText("开始发送文件");
                            File file = new File(FileSavePath.getStoragePath() + "/111.txt");
                            if (file.exists()) {
                                byte[] fileBytes = FileTransformation.fileToBytes(file.getPath());
                                int j = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, fileBytes, fileBytes.length, 3000);
                                if (j > 0) {
                                    mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                                    setText("文件发送成功");
                                    int k = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, fileMessageEnd.getBytes(), fileMessageEnd.getBytes().length, 3000);
                                    if (k > 0) {
                                        mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                                        setText("文件发送完成");
                                    }
                                } else {
                                    setText("文件发送失败");
                                }
                            } else {
                                setText("文件不存在");
                            }
                        } else {
                            setText("开始发送文件失败");
                        }
                    }
                });
                break;
        }
    }

    /**
     * 打开设备 , 让host和device端连起来
     */
    private void openDevices() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(pageControl.getContext(), 0, new Intent(USB_ACTION), 0);
        IntentFilter intentFilter = new IntentFilter(USB_ACTION);
        mOpenDevicesReceiver = new OpenDevicesReceiver(this);
        registerReceiver(mOpenDevicesReceiver, intentFilter);

        //列举设备(手机)
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if (deviceList != null && deviceList.size() != 0) {
            for (UsbDevice usbDevice : deviceList.values()) {
                int productId = usbDevice.getProductId();
//                if (productId != 377 && productId != 7205) {
                if (mUsbManager.hasPermission(usbDevice)) {
                    setText("已有权限");
                    initAccessory(usbDevice);
                } else {
                    setText("需要申请权限");
                    mUsbManager.requestPermission(usbDevice, pendingIntent);
                }
//                }
            }
        } else {
            setText("请连接USB");
        }
    }

    /**
     * 发送命令 , 让device设备进入Accessory模式
     *
     * @param usbDevice
     */
    private void initAccessory(UsbDevice usbDevice) {
        UsbDeviceConnection usbDeviceConnection = mUsbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            setText("请连接USB");
            return;
        }

        //根据AOA协议打开Accessory模式
        initStringControlTransfer(usbDeviceConnection, 0, "Google, Inc."); // MANUFACTURER
        initStringControlTransfer(usbDeviceConnection, 1, "AccessoryChat"); // MODEL
        initStringControlTransfer(usbDeviceConnection, 2, "Accessory Chat"); // DESCRIPTION
        initStringControlTransfer(usbDeviceConnection, 3, "1.0"); // VERSION
        initStringControlTransfer(usbDeviceConnection, 4, "http://www.android.com"); // URI
        initStringControlTransfer(usbDeviceConnection, 5, "0123456789"); // SERIAL
        usbDeviceConnection.controlTransfer(0x40, 53, 0, 0, new byte[]{}, 0, 100);
        usbDeviceConnection.close();
        MyApplication.printLogDebug("initAccessory success");
        setText("initAccessory success");
        initDevice();
    }

    private void initStringControlTransfer(UsbDeviceConnection deviceConnection, int index, String string) {
        deviceConnection.controlTransfer(0x40, 52, 0, index, string.getBytes(), string.length(), 100);
    }

    /**
     * 初始化设备(手机) , 当手机进入Accessory模式后 , 手机的PID会变为Google定义的2个常量值其中的一个 ,
     */
    private void initDevice() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (mToggle) {
                    SystemClock.sleep(1000);
                    HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                    Collection<UsbDevice> values = deviceList.values();
                    if (!values.isEmpty()) {
                        for (UsbDevice usbDevice : values) {
                            int productId = usbDevice.getProductId();
                            if (productId == 0x2D00 || productId == 0x2D01) {
                                if (mUsbManager.hasPermission(usbDevice)) {
                                    mUsbDeviceConnection = mUsbManager.openDevice(usbDevice);
                                    if (mUsbDeviceConnection != null) {
                                        mUsbInterface = usbDevice.getInterface(0);
                                        int endpointCount = mUsbInterface.getEndpointCount();
                                        for (int i = 0; i < endpointCount; i++) {
                                            UsbEndpoint usbEndpoint = mUsbInterface.getEndpoint(i);
                                            if (usbEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                                if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                                    mUsbEndpointOut = usbEndpoint;
                                                } else if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                                                    mUsbEndpointIn = usbEndpoint;
                                                }
                                            }
                                        }
                                        if (mUsbEndpointOut != null && mUsbEndpointIn != null) {
                                            MyApplication.printLogDebug("connected success");
                                            setText("连接成功");
                                            mHandler.sendEmptyMessage(CONNECTED_SUCCESS);
                                            mToggle = false;
                                            isDetached = true;
                                        } else {
                                            setText("输入输出端口有问题");
                                        }
                                    }
                                } else {
                                    setText("初始化设备无权限");
                                    mUsbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(pageControl.getContext(), 0, new Intent(""), 0));
                                }
                            } else {
                                setText("productId为：" + productId + ",请重新打开APP");
                            }
                        }
                    } else {
                        setText("获取到的设备为空");
                        finish();
                    }
                }
            }
        });
    }

    @Override
    public void openAccessoryModel(UsbDevice usbDevice) {
        initAccessory(usbDevice);
    }

    @Override
    public void openDevicesError() {
        setText("USB连接错误");
    }

    /**
     * 接受消息线程 , 此线程在设备(手机)初始化完成后 , 就一直循环接受消息
     */
    private void loopReceiverMessage() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(1000);
                while (isReceiverMessage) {
                    /**
                     * 循环接受数据的地方 , 只接受byte数据类型的数据
                     */
                    if (mUsbDeviceConnection != null && mUsbEndpointIn != null) {
                        int i = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, mBytes, mBytes.length, 3000);
                        MyApplication.printLogDebug(i + "");
                        if (i > 0) {
                            mStringBuffer.append(new String(mBytes, 0, i) + "\n");
                            setText("startFile：" + FrmDbUtil.getConfigValue("startFile"));
                            if (TextUtils.equals(FrmDbUtil.getConfigValue("startFile"), "1")) {
                                String fileName = System.currentTimeMillis() + ".txt";
                                try {
                                    FileTransformation.bytesToFile(mBytes, fileName);
                                    setText("字节转化文件成功");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    setText("字节转化文件失败：" + e.getMessage());
                                }
                                File file = new File(FileSavePath.getStoragePath() + "/" + fileName);
                                if (file.exists()) {
                                    FileUtil.doOpenFile(pageControl.getContext(), file.getPath());
                                }
                            }
                            if (mStringBuffer.toString().contains("file_start")) {
                                FrmDbUtil.setConfigValue("startFile", "1");
                            } else if (mStringBuffer.toString().contains("file_end")) {
                                FrmDbUtil.setConfigValue("startFile", "0");
                            } else {
                                FrmDbUtil.setConfigValue("startFile", "-1");
                            }
                            setText("接收的数据：" + mStringBuffer);
                            mHandler.sendEmptyMessage(RECEIVER_MESSAGE_SUCCESS);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void usbDetached() {
        if (isDetached) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
            mUsbDeviceConnection = null;
        }
        mUsbEndpointIn = null;
        mUsbEndpointOut = null;
        mToggle = false;
        isReceiverMessage = false;
        mThreadPool.shutdownNow();
        unregisterReceiver(mUsbDetachedReceiver);
        unregisterReceiver(mOpenDevicesReceiver);
    }

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
