package com.azazo1.bleswitchlight;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static MainActivity instance;
    public static final String CHIP_BLE_ADDRESS = "A0:A3:B3:29:D4:92";
    public static final String CHIP_BLE_SERVICE_UUID = "9f867bb8-d8a6-43b2-b129-fefc96fcf689";
    public static final String CHIP_BLE_CHARACTERISTIC_UUID = "7c6473b1-0164-4842-be7f-d1cc945eaae6";
    public static final String CHIP_BLE_DESCRIPTOR_UUID = "08fc14c5-2002-4a9d-8672-15517c70697a";
    public static final String[] REQUESTED_PERMISSIONS = new String[]{BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION};
    public static final int REQUEST_PERMISSION_CODE = 205;
    public BluetoothGattCallback gattCallbackForOtherActivity; // 当其他Activity设置该值, 可以为那些activity提供回调
    public BluetoothGatt gattConnection;
    public BluetoothGattCharacteristic characteristic;
    public BluetoothGattService service;
    public BluetoothGattDescriptor descriptor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView connStateText;
    @SuppressLint("MissingPermission")
    private final Runnable onBTEnabled = () -> {
        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        byte[] address = convertBTAddress(CHIP_BLE_ADDRESS);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        gattConnection = device.connectGatt(MainActivity.this, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    gattConnection.discoverServices();
                    handler.postDelayed(() -> {
                        service = gattConnection.getService(UUID.fromString(CHIP_BLE_SERVICE_UUID));
                        characteristic = service.getCharacteristic(UUID.fromString(CHIP_BLE_CHARACTERISTIC_UUID));
                        descriptor = characteristic.getDescriptor(UUID.fromString(CHIP_BLE_DESCRIPTOR_UUID));
                    }, 1000);
                    handler.post(() -> {
                        connStateText.setText(R.string.connected);
                    });
                } else {
                    service = null;
                    characteristic = null;
                    descriptor = null;
                    handler.post(() -> {
                        connStateText.setText(R.string.disconnected);
                    });
                }
                Log.i("ble-state-change", String.format(Locale.getDefault(), "status:%d, newState:%d", status, newState));
                if (gattCallbackForOtherActivity != null) {
                    gattCallbackForOtherActivity.onConnectionStateChange(gatt, status, newState);
                }
            }

            @Override
            public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
//                Log.i("ble-char-read", String.format("value: %d, status: %d", value[0], status));
                if (gattCallbackForOtherActivity != null) {
                    gattCallbackForOtherActivity.onCharacteristicRead(gatt, characteristic, value, status);
                }
            }

            @Override
            public void onDescriptorRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status, @NonNull byte[] value) {
//                Log.i("ble-descriptor-read", String.format("value: %d, status: %d", value[0], status));
                if (gattCallbackForOtherActivity != null) {
                    gattCallbackForOtherActivity.onDescriptorRead(gatt, descriptor, status, value);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.i("ble-char-write", "status: " + status);
                if (gattCallbackForOtherActivity != null) {
                    gattCallbackForOtherActivity.onCharacteristicWrite(gatt, characteristic, status);
                }
            }
        });
    };

    private final ActivityResultLauncher<Intent> btEnableRequestLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK) {
            requestBTEnable();
        } else {
            onBTEnabled.run();
        }
    });


    private final Runnable onBTPermissionGranted = this::requestBTEnable;

    private ToggleButton toggleButton;

    @Override
    @SuppressLint("MissingInflatedId")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;
        if (checkBLEAvailable()) {
            checkBTPermission();
        }
        connStateText = findViewById(R.id.connectionStateText);
        connStateText.setText(R.string.disconnected);
        toggleButton = findViewById(R.id.switchLightButton);
        toggleButton.setOnClickListener(v -> {
            if (characteristic != null) {
                // 切换开关灯
                if (toggleButton.isChecked()) {
                    gattConnection.writeCharacteristic(
                            characteristic,
                            new byte[]{CommandType.SET_REMOTE_SWITCH, 1},
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                } else {
                    gattConnection.writeCharacteristic(
                            characteristic,
                            new byte[]{CommandType.SET_REMOTE_SWITCH, 0},
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                }
            } else {
                Toast.makeText(instance, "操作失效", Toast.LENGTH_SHORT).show();
            }
        });
        Button enterButton = findViewById(R.id.enterModifyingActivityButton);
        enterButton.setOnClickListener(v -> {
            if (characteristic != null) {
                Intent intent = new Intent(this, ModifyingActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.disconnected, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /**
     * 检查设备是否能使用BLE
     */
    private boolean checkBLEAvailable() {
        // 检查是否符合能使用BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_support, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        return true;
    }

    /**
     * 检查蓝牙权限
     */
    private void checkBTPermission() {
        boolean needRequest = false;
        for (String permission : REQUESTED_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            requestPermissions(REQUESTED_PERMISSIONS, REQUEST_PERMISSION_CODE); // 类递归调用
        } else {
            onBTPermissionGranted.run();
        }
    }

    /**
     * 将以字符串表示的蓝牙地址转换为字节形式
     * "A0:A3:B3:29:D4:92" -> new byte[6] {0xA0, 0xA3, ...}
     */
    public static byte[] convertBTAddress(String address) {
        // 转化蓝牙地址
        String[] bytesInString = address.split(":");
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            char[] oneByteInString = new char[2];
            bytesInString[i].toLowerCase().getChars(0, 2, oneByteInString, 0);
            byte sum = 0;

            if (Character.isAlphabetic(oneByteInString[0])) {
                sum += oneByteInString[0] - 'a' + 10;
            } else {
                sum += oneByteInString[0] - '0';
            }
            sum *= 16;
            if (Character.isAlphabetic(oneByteInString[1])) {
                sum += oneByteInString[1] - 'a' + 10;
            } else {
                sum += oneByteInString[1] - '0';
            }
            bytes[i] = sum;
        }
        return bytes;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            checkBTPermission();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * 请求打开蓝牙
     */
    private void requestBTEnable() {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager.getAdapter();
        if (adapter != null) {
            if (!adapter.isEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                btEnableRequestLauncher.launch(intent);
            } else {
                onBTEnabled.run();
            }
        } else {
            Toast.makeText(this, R.string.ble_not_support, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}