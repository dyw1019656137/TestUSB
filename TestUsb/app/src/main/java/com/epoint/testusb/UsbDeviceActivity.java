package com.epoint.testusb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.epoint.ui.baseactivity.FrmBaseActivity;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/9 10:38
 * 版本： [1.0, 2019/5/9]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class UsbDeviceActivity extends FrmBaseActivity {

    @BindView(R.id.tv_device_message)
    TextView tvDeviceMessage;

    @BindView(R.id.et_device_message)
    EditText etDeviceMessage;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInStream;
    private FileOutputStream mOutStream;
    private CommunicationThread mCommThread;
    private PendingIntent mPermissionIntent;
    private volatile boolean mExit;
    private String mData;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLayout(R.layout.usb_device_activity);
        initView();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    }

    private void initView() {
        setTitle("Device设备");
        getNbViewHolder().nbBack.setVisibility(View.GONE);
    }

    @OnClick({R.id.btn_get_host, R.id.btn_send_message, R.id.btn_get_file})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_get_host:
                setText("获取host设备");
                getAccessory();
                break;
            case R.id.btn_send_message:
                setText("发送信息内容：" + etDeviceMessage.getText().toString());
                break;
            case R.id.btn_get_file:
                toast("选择文件发送");
                break;
        }
    }

    private void getAccessory() {
        UsbAccessory accessory = (UsbAccessory) getIntent().getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        if (accessory != null) {
            setText("getAccessory成功：" + accessory.getDescription());
            mUsbManager.requestPermission(accessory, mPermissionIntent);
        } else {
            setText("getAccessory失败");
        }
    }

    private class CommunicationThread extends Thread {
        @Override
        public void run() {
            mExit = false;
            byte[] msg = new byte[256];
            while (!mExit) {
                try {
                    // 阻塞在此处
                    int len = mInStream.read(msg);
                    if (len > 0) {
                        setText("数据接收成功：" + new String(msg, "utf-8"));
                    } else {
                        setText("数据接收失败");
                    }
                } catch (final Exception e) {
                    setText("数据接收异常");
                    break;
                }
                mData = etDeviceMessage.getText().toString();
                synchronized (mData) {
                    if (!TextUtils.isEmpty(mData)) {
                        try {
                            mOutStream.write(mData.getBytes());// 可以成功发送
                            setText("发送成功：" + mData);
                        } catch (IOException e) {
                            setText("发送异常");
                            continue;
                        }
                    }
                }
            }
        }
    }

    private void openAccessory(UsbAccessory accessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInStream = new FileInputStream(fd);
            mOutStream = new FileOutputStream(fd);
            if (mInStream == null) {
                return;
            }
            mCommThread = new CommunicationThread();
            mCommThread.start();
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        setText("授权成功");
                        if (accessory != null) {
                            mAccessory = accessory;
                            openAccessory(mAccessory);
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
                tvDeviceMessage.setText(tvDeviceMessage.getText() + newText);
            }
        });
    }
}
