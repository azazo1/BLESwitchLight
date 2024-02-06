package com.azazo1.bleswitchlight;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

enum RotationType {
    ON, OFF, PLAIN, PREVIEW
}

class CommandType {
    public static final byte ENTER_FREE_ROTATION = (byte) 0xef;
    public static final byte ENTER_NORMAL = (byte) 0xee;
    public static final byte MODIFY_ON = (byte) 0xa0;
    public static final byte MODIFY_OFF = (byte) 0xa1;
    public static final byte MODIFY_PLAIN = (byte) 0xa2;
    public static final byte SET_REMOTE_SWITCH = (byte) 0xb0;
}


/**
 * 此Activity用于调整舵机角度
 */
public class ModifyingActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView connText;
    private MySeekbar previewer;
    private MySeekbar onModifier;
    private MySeekbar offModifier;
    private MySeekbar plainModifier;

    @SuppressLint({"MissingInflatedId", "MissingPermission"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("调整舵机角度");
        setContentView(R.layout.activity_modifying);

        connText = findViewById(R.id.connectionStateText);

        previewer = findViewById(R.id.previewer);
        previewer.attachToActivity(findViewById(R.id.previewer_label), RotationType.PREVIEW);
        onModifier = findViewById(R.id.modifierOn);
        onModifier.attachToActivity(findViewById(R.id.on_seekbar_label), RotationType.ON);
        offModifier = findViewById(R.id.modifierOff);
        offModifier.attachToActivity(findViewById(R.id.off_seekbar_label), RotationType.OFF);
        plainModifier = findViewById(R.id.modifierPlain);
        plainModifier.attachToActivity(findViewById(R.id.plain_seekbar_label), RotationType.PLAIN);

        Button applyButton = findViewById(R.id.modifyApply);
        applyButton.setOnClickListener(v -> {
            applyModify();
        });

        MainActivity.instance.gattCallbackForOtherActivity = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (status == STATE_CONNECTED) {
                    connText.setText(R.string.connected);
                } else {
                    connText.setText(R.string.disconnected);
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.instance.gattCallbackForOtherActivity = null;
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onStart() {
        super.onStart();
        // 进入自由旋转模式
        MainActivity.instance.gattConnection.writeCharacteristic(
                MainActivity.instance.characteristic,
                new byte[]{CommandType.ENTER_FREE_ROTATION},
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        );
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onStop() {
        super.onStop();
        // 进入正常模式
        MainActivity.instance.gattConnection.writeCharacteristic(
                MainActivity.instance.characteristic,
                new byte[]{CommandType.ENTER_NORMAL},
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        );
    }

    @SuppressLint("MissingPermission")
    private void applyModify() {
        Toast.makeText(this, "请稍等，将会卡顿几秒以传输数据", Toast.LENGTH_LONG).show();
        for (MySeekbar seekbar : new MySeekbar[]{offModifier, onModifier, plainModifier}) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            byte commandCode;
            switch (seekbar.rotationType) {
                case ON -> commandCode = CommandType.MODIFY_ON;
                case OFF -> commandCode = CommandType.MODIFY_OFF;
                case PLAIN -> commandCode = CommandType.MODIFY_PLAIN;
                default -> {
                    throw new IllegalArgumentException("invalid rotation type");
                }
            }
            if (MainActivity.instance.characteristic == null) {
                Toast.makeText(this, R.string.disconnected, Toast.LENGTH_SHORT).show();
                return;
            }
            MainActivity.instance.gattConnection.writeCharacteristic(
                    MainActivity.instance.characteristic,
                    new byte[]{commandCode, (byte) seekbar.getProgress()},
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );
        }
        Toast.makeText(this, "传输完成", Toast.LENGTH_SHORT).show();
    }
}