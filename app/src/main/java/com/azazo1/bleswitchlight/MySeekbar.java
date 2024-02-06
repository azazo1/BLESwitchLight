package com.azazo1.bleswitchlight;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;


public class MySeekbar extends androidx.appcompat.widget.AppCompatSeekBar {
    private TextView label;
    public RotationType rotationType;

    public MySeekbar(@NonNull Context context) {
        super(context);
    }

    public MySeekbar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MySeekbar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void attachToActivity(TextView label, RotationType rotationType) {
        setMin(0);
        setMax(25);
        this.label = label;
        this.rotationType = rotationType;
        setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && MainActivity.instance.characteristic != null) {
                    MainActivity.instance.gattConnection.writeCharacteristic(
                            MainActivity.instance.characteristic,
                            new byte[]{CommandType.SET_REMOTE_SWITCH, (byte) progress},
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                }
                int prefix;
                switch (rotationType) {
                    case OFF -> {
                        prefix = R.string.rotation_off;
                    }
                    case ON -> {
                        prefix = R.string.rotation_on;
                    }
                    case PLAIN -> {
                        prefix = R.string.rotation_plain;
                    }
                    case PREVIEW -> {
                        prefix = R.string.preview;
                    }
                    default -> {
                        prefix = R.string.preview;
                    }
                }
                label.setText(String.format(Locale.getDefault(), "%s: %d", getContext().getString(prefix), progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }
}
