package com.epoint.testusb.device.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.epoint.core.db.FrmDbUtil;
import com.epoint.core.util.common.FileSavePath;
import com.epoint.core.util.device.PermissionUtil;
import com.epoint.core.util.io.FileUtil;
import com.epoint.testusb.R;
import com.epoint.testusb.device.receiver.OpenAccessoryReceiver;
import com.epoint.testusb.host.receiver.UsbDetachedReceiver;
import com.epoint.testusb.host.utils.FileTransformation;
import com.epoint.ui.baseactivity.FrmBaseActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 16:24
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class DeviceActivity extends FrmBaseActivity implements OpenAccessoryReceiver.OpenAccessoryListener, UsbDetachedReceiver.UsbDetachedListener {

    @BindView(R.id.tv_device_message)
    TextView tvDeviceMessage;

    @BindView(R.id.et_device_message)
    EditText etDeviceMessage;

    @BindView(R.id.btn_send_message)
    Button btnSendMessage;

    private static final int SEND_MESSAGE_SUCCESS = 0;
    private static final int RECEIVER_MESSAGE_SUCCESS = 1;
    private static final String USB_ACTION = "com.epoint.testusb.device";
    private UsbManager mUsbManager;
    private OpenAccessoryReceiver mOpenAccessoryReceiver;
    private UsbDetachedReceiver mUsbDetachedReceiver;

    private ParcelFileDescriptor mParcelFileDescriptor;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private PendingIntent mPendingIntent;

    private ExecutorService mThreadPool;
    private byte[] mBytes = new byte[102400];
    private StringBuffer mStringBuffer = new StringBuffer();

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SEND_MESSAGE_SUCCESS:
                    setText("发送消息成功");
                    etDeviceMessage.setText("");
                    etDeviceMessage.clearComposingText();
                    break;

                case RECEIVER_MESSAGE_SUCCESS:
                    setText("--------接收消息成功--------\n");
                    mStringBuffer.delete(0,mStringBuffer.length());
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLayout(R.layout.usb_device_activity);
        init();
    }

    private void init() {
        setTitle("Accessory设备");
        getNbViewHolder().nbBack.setVisibility(View.GONE);

        if (!PermissionUtil.checkPermissionAllGranted(getContext(), PermissionUtil.PERMISSION_STORAGE)) {
            PermissionUtil.startRequestPermissions(getContext(), PermissionUtil.PERMISSION_STORAGE, PermissionUtil.REQUESTCODE_PERMISSION_STORAGE);
            return;
        }

        btnSendMessage.setEnabled(false);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mThreadPool = Executors.newFixedThreadPool(3);

        mUsbDetachedReceiver = new UsbDetachedReceiver(this);
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbDetachedReceiver, filter);

        mOpenAccessoryReceiver = new OpenAccessoryReceiver(this);
        mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(USB_ACTION), 0);
        IntentFilter intentFilter = new IntentFilter(USB_ACTION);
        registerReceiver(mOpenAccessoryReceiver, intentFilter);
    }

    /**
     * 打开Accessory模式
     *
     * @param usbAccessory
     */
    private void openAccessory(UsbAccessory usbAccessory) {
        mParcelFileDescriptor = mUsbManager.openAccessory(usbAccessory);
        if (mParcelFileDescriptor != null) {
            FileDescriptor fileDescriptor = mParcelFileDescriptor.getFileDescriptor();
            mFileInputStream = new FileInputStream(fileDescriptor);
            mFileOutputStream = new FileOutputStream(fileDescriptor);
            btnSendMessage.setEnabled(true);

            mThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    int i = 0;
                    while (i >= 0) {
                        try {
                            i = mFileInputStream.read(mBytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                            setText("接收消息异常：" + e.toString());
                            break;
                        }
                        if (i > 0) {
                            mStringBuffer.append(new String(mBytes, 0, i) + "\n");
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
                        } else {
                            setText("接收消息失败");
                        }
                    }
                }
            });
        }
    }

    @OnClick({R.id.btn_get_host, R.id.btn_send_message, R.id.btn_get_file})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_get_host:
                setText("打开Accessory模式");
                UsbAccessory[] accessories = mUsbManager.getAccessoryList();
                UsbAccessory usbAccessory = (accessories == null ? null : accessories[0]);
                if (usbAccessory != null) {
                    if (mUsbManager.hasPermission(usbAccessory)) {
                        setText("有权限");
                        openAccessory(usbAccessory);
                    } else {
                        setText("无权限");
                        mUsbManager.requestPermission(usbAccessory, mPendingIntent);
                    }
                }
                break;
            case R.id.btn_send_message:
                final String message = etDeviceMessage.getText().toString();
                if (!TextUtils.isEmpty(message)) {
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mFileOutputStream.write(message.getBytes());
                                mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
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
                        try {
                            mFileOutputStream.write(fileMessage.getBytes());

                            mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                            setText("开始发送文件");
                            File file = new File(FileSavePath.getStoragePath() + "/111.txt");
                            if (file.exists()) {
                                byte[] fileBytes = FileTransformation.fileToBytes(file.getPath());
                                mFileOutputStream.write(fileBytes);
                                mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                                setText("文件发送成功");
                                mFileOutputStream.write(fileMessageEnd.getBytes());
                                mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                                setText("文件发送完成");
                            } else {
                                setText("文件不存在");
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            setText("文件发送异常");
                        }
                    }
                });
                break;
        }
    }

    @Override
    public void openAccessoryModel(UsbAccessory usbAccessory) {
        setText("打开Accessory模式");
        openAccessory(usbAccessory);
    }

    @Override
    public void openAccessoryError() {
        setText("打开Accessory模式失败");
    }

    @Override
    public void usbDetached() {
        setText("USB断开连接");
    }

    private void setText(String text) {
        final String newText = "\n" + text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvDeviceMessage.setText(tvDeviceMessage.getText() + newText);
            }
        });
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        unregisterReceiver(mOpenAccessoryReceiver);
        unregisterReceiver(mUsbDetachedReceiver);
        if (mParcelFileDescriptor != null) {
            try {
                mParcelFileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mFileInputStream != null) {
            try {
                mFileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mFileOutputStream != null) {
            try {
                mFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
