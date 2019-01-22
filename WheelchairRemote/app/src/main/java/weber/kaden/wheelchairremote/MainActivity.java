package weber.kaden.wheelchairremote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import io.github.controlwear.virtual.joystick.android.JoystickView;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

public class MainActivity extends AppCompatActivity {
    final int REQUEST_ENABLE_BT = 0;
    static final long SCAN_PERIOD = 1500;

    //Device info
    //Device name: SH-HC-08
    final String BLE_DEVICE_ADDRESS = "4C:3F:D3:02:DE:14";

    final UUID SERVICE =  UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    final UUID CHARACTERISTIC = convertFromInteger(0xFFE1);

    final UUID DESCRIPTOR = convertFromInteger(0x2901);


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mScanCallback;
    private BluetoothDevice mConnectedDevice;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic mGattCharacteristic;
    private BluetoothGattDescriptor mGattDescriptor;

    private boolean mScanning;
    private Handler mHandler;

    private Button mScanButton;
    private TextView mDeviceConnectedText;
    private Button mServerTestButton;


    private SeekBar mSeekBar;
    private int seekBarMultiplier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBluetooth();

        //setup scan button
        mScanButton = findViewById(R.id.scanButton);
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mScanning) {
                    scanLeDevice(true);
                }
            }
        });
        mDeviceConnectedText = findViewById(R.id.connectedTextView);

        //setup server test button
        mServerTestButton = findViewById(R.id.serverTestButton);
        mServerTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), sendRFIDIntentService.class);
                intent.putExtra(sendRFIDIntentService.URL_EXTRA,
                        "http://52.200.212.149:8080/" + "4/" + "00000000" );
                startService(intent);
            }
        });

        //setup the joystick (its listener will trigger a gatt update)
        JoystickView joyStick = findViewById(R.id.joystickView);
        joyStick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                //TODO: refactor
                //send new coordinates based on joystick position
                //byte[] newContents = makeByteArray(angle, strength);
                double speedSetting = ((double)seekBarMultiplier / 5.0); // multiplier is a value between 1 and five
                strength = (int)(strength * speedSetting);
                byte[] newContents = makeOmniByteArray(angle, strength);
                //byte[] newContents = makeOmniByteArray(capAngleDelta(angle), capStrengthDelta(strength));

                sendArray(newContents);
            }
        });

        //rotation buttons
        Button rotateLeftButton = findViewById(R.id.leftTurnButton);
        rotateLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendRotationArray(false);
            }
        });
        Button rotateRightButton = findViewById(R.id.rightTurnButton);
        rotateRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendRotationArray(true);
            }
        });

        //setup seekbar
        mSeekBar = findViewById(R.id.seekBar);
        seekBarMultiplier = mSeekBar.getProgress() + 1;
        System.out.println("seekbar: " + String.valueOf(seekBarMultiplier));
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                seekBarMultiplier = i + 1;
                System.out.println("seekbar: " + String.valueOf(seekBarMultiplier));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private int lastAngle = 0;
    private final int MAX_ANGLE_DELTA = 10;
    private int capAngleDelta(int angle){
        int newAngle = makeSafeDelta(angle, lastAngle, MAX_ANGLE_DELTA);
        lastAngle = newAngle;
        return newAngle;
    }
    private int lastStrength = 0;
    private final int MAX_STRENGTH_DELTA = 3;
    private int capStrengthDelta(int strength){
        int newStrength = makeSafeDelta(strength, lastStrength, MAX_STRENGTH_DELTA);
        lastStrength = newStrength;
        return newStrength;
    }

    private int makeSafeDelta(int current, int previous, int maxDelta){
        int newCoord;
        if(Math.abs(previous - current) >= maxDelta){
            if(current > previous){
                newCoord = previous + maxDelta;
            } else {
                newCoord = previous - maxDelta;
            }
        } else {
            newCoord = current;
        }
        return newCoord;
    }

    //old byte array, keep for reference
//    private int lastX = 0;
//    private int lastY = 0;
//    private final int MAX_COORD_CHANGE = 3;
//    // new byte array, length 6 with each pair corresponding to strength from 0 to 255 and
//    // direction 0 for clockwise and 1 for counterclockwise.
//    private byte[] makeByteArray(int angle, int strength) {
//        //convert polar angle and strength to cartesian xy coordinates.
//        double exactX = Math.cos(Math.toRadians(angle)) * strength;
//        int x = (int)Math.round(exactX);
//        double exactY = Math.sin(Math.toRadians(angle)) * strength;
//        int y = (int)Math.round(exactY);
//
//        //slow any large changes in joystick position
//        x = makeSafeDelta(x, lastX, MAX_COORD_CHANGE);
//        lastX = x;
//        y = makeSafeDelta(y, lastY, MAX_COORD_CHANGE);
//        lastY = y;
//        //make compatible with EE convention
//        int offsetX = x + 100;
//        int offsetY = y + 100;
//        System.out.println("x: " + offsetX + " y: " + offsetY);
//        byte[] array = new byte[2];
//        array[0] = (byte)offsetX;
//        array[1] = (byte)offsetY;
//        return array;
//    }

    private void sendRotationArray(boolean clockwise){
        //how to ramp up the acceleration?
        byte[] array;
        if (clockwise) {
            array = new byte[] {100, 2, 100, 2, 100, 2} ;
        } else {
            array = new byte[] {100, 1, 100, 1, 100, 1} ;
        }
        sendArray(array);
    }

    private void sendArray(byte[] array) {
        if (mGattCharacteristic != null) {
            mGattCharacteristic.setValue(array);
            gatt.writeCharacteristic(mGattCharacteristic);
        }
    }

    private final double WHEEL_1_ANGLE = Math.toRadians(150);
    private final double WHEEL_2_ANGLE = Math.toRadians(30);
    private final double WHEEL_3_ANGLE = Math.toRadians(270);
    private final double CONVERSION_RATE = 2.55;
    private byte[] makeOmniByteArray(int angle, int strength){
        // use the polar coordinates
        double polarAngle = Math.toRadians(angle);
        //use online tip to convert joystick input to wheel strength, then convert to 0-255 scale for EE
        int wheel1Strength = (int)(strength * Math.sin(WHEEL_1_ANGLE - polarAngle) * CONVERSION_RATE);
        int wheel2Strength = (int)(strength * Math.sin(WHEEL_2_ANGLE - polarAngle) * CONVERSION_RATE);
        int wheel3Strength = (int)(strength * Math.sin(WHEEL_3_ANGLE - polarAngle) * CONVERSION_RATE);

        byte[] array = new byte[6];

        // lots of duplicate code here but a function is too complicated to be worthwhile
        if (wheel1Strength < 0){
            array[0] = (byte)(Math.abs(wheel1Strength));
            array[1] = 1;
        } else {
            array[0] = (byte) wheel1Strength;
            array[1] = 2;
        }
        //wheel 2
        if (wheel2Strength < 0){
            array[2] = (byte)(Math.abs(wheel2Strength));
            array[3] = 1;
        } else {
            array[2] = (byte) wheel2Strength;
            array[3] = 2;
        }
        //wheel 3
        if (wheel3Strength < 0){
            array[4] = (byte)(Math.abs(wheel3Strength));
            array[5] = 1;
        } else {
            array[4] = (byte) wheel3Strength;
            array[5] = 2;
        }

        System.out.println("Wheel 1: " + (array[0]) + " " + array[1] +
                " Wheel 2: " + (array[2]) + " " + array[3] +
                " Wheel 3: " + (array[4]) + " " + array[5]);
        return array;
    }
    // omni info
    // 1\   /2
    //    -
    //    3
    //Degrees: 1: 150, 2: 30, 3: 270
    // direction: clockwise is positive, send a 0, counterclockwise send a 1


//Bluetooth functions
    private void initBluetooth(){
        // Use this check to determine whether BLE is supported on the remote. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Ensures Bluetooth is available on the remote and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //start scan
        mScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                if (bluetoothDevice.getAddress().equals(BLE_DEVICE_ADDRESS)){
                    mConnectedDevice = bluetoothDevice;
                    connect();
                }
            }
        };
        mHandler = new Handler();
        scanLeDevice(true);

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                mGattCharacteristic = gatt.getService(SERVICE)
                        .getCharacteristic(CHARACTERISTIC);
                gatt.setCharacteristicNotification(mGattCharacteristic, true);

                mGattDescriptor = mGattCharacteristic.getDescriptor(DESCRIPTOR);
                mGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(mGattDescriptor);
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if(newState == STATE_CONNECTED){
                    gatt.discoverServices();
                } else {
                    //TODO: reenable scan button
                }
            }
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                String RFID = characteristic.getStringValue(0);
                // send RFID to server
                System.out.println("characteristic change: " + RFID);
                //test
                System.out.println(characteristic.getValue());
                Intent intent = new Intent(getApplicationContext(), sendRFIDIntentService.class);
                intent.putExtra(sendRFIDIntentService.URL_EXTRA,
                        "http://52.200.212.149:8080/" + "4/" + RFID );
                startService(intent);

            }

        };
    }
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan(mScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
    }

    private void connect(){
        gatt = mConnectedDevice.connectGatt(this, true, mGattCallback);
        if (gatt != null){
            scanLeDevice(false);
            //TODO: make sure to test and reenable button when the device is turned on and off
            mScanButton.setVisibility(View.INVISIBLE);
            setDeviceConnectedText(true);
        } else {
            setDeviceConnectedText(false);
        }
    }

    private void setDeviceConnectedText(boolean connected){
        if (connected){
            mDeviceConnectedText.setText(R.string.connected_label);
        } else {
            mDeviceConnectedText.setText(R.string.not_connected_label);
        }
    }

    private String checkGattDescriptor(BluetoothGattDescriptor descriptor){
        if (descriptor == null){
            return "null";
        }
        byte[] contents = descriptor.getValue();
        if (contents.length < 1){
            return "empty";
        } else {
            int strength = contents[0]; // ?
            return String.valueOf(strength);
        }
    }

    @Override
    protected void onPause() {
        scanLeDevice(false);
        super.onPause();
    }
    @Override
    protected void onStop() {
        scanLeDevice(false);
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        scanLeDevice(false);
        super.onDestroy();
    }

    public UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);
    }


}
