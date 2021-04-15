package com.example.iot_sender_receiver_1;


import androidx.appcompat.app.AppCompatActivity;

//import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.eclipse.paho.android.service.BuildConfig;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private Toast toast;
    final String TAG = "MAIN_TAG";
    private static final String ACTION_USB_PERMISSION = "com.android.recipes.USB_PERMISSION";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    UsbSerialPort port;
    MQTTService mqttService;
    Button button0;
    EditText input;
    TextView textView;
    View decorView;

    private void initUSBPort(){
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            Log.d("UART", "UART is not available");

        }else {
            Log.d("UART", "UART is available");

            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {

                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);

                manager.requestPermission(driver.getDevice(), PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));

                return;
            } else {

                port = driver.getPorts().get(0);
                try {
                    Log.d("UART", "openned succesful");
                    port.open(connection);
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
                    Executors.newSingleThreadExecutor().submit(usbIoManager);

                } catch (Exception e) {
                    Log.d("UART", "There is error");
                }
            }
        }
    }

    private void sendDataMQTT(String data){

        MqttMessage msg = new MqttMessage();
        msg.setId(1234);
        msg.setQos(0);
        msg.setRetained(true);

        byte[] b = data.getBytes(Charset.forName("UTF-8"));
        msg.setPayload(b);

        Log.d("ABC","Publish :" + msg);
        try {
            mqttService.mqttAndroidClient.publish("tiviluson/feeds/iot-sender-receiver", msg);

        }catch (MqttException e){
        }
    }

    void LedOn() {
        textView.setText("LED");
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundColor(Color.GREEN);
    }

    void LedOff() {
        textView.setText("LED");
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundColor(Color.RED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestDataFromAdafruit("1", "2");
        decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility == 0) {
                    hideSystemBar();
                }
            }
        });

        button0 = findViewById(R.id.button2);
        textView = findViewById(R.id.textView2);
        input = findViewById(R.id.editTextTextPersonName2);


        button0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDataMQTT(input.getText().toString());
            }
        });

        initUSBPort();
        mqttService = new MQTTService(this);
        mqttService.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //if (topic.equals("tiviluson/feeds/iot-test")) {
                if (message.toString().equals("1")) {
                    port.write("1".getBytes(), 1000);
                    LedOn();
                }
                else if(message.toString().equals("0")) {
                    port.write("0".getBytes(), 1000);
                    LedOff();
                }
                //   }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }

    private String buffer = "";
    @Override
    public void onNewData(byte[] data) {
        buffer += new String(data);
        //Toast.makeText(getApplicationContext(), "buffer received", Toast.LENGTH_SHORT).show();

        try {
            if (buffer.contains("#") && buffer.contains("!")) {
                int index_soc = buffer.indexOf("#");
                int index_eof = buffer.indexOf("!");
                // Log.d("UART", "y");
                String cmd = buffer.substring(index_soc + 1, index_eof);
                // Log.d("ABC", cmd);
                sendDataMQTT(cmd);
                if (cmd.equals("1")){
                    LedOn();
                }
                else if (cmd.equals("0"))
                    LedOff();
                else
                    textView.setBackgroundColor(0x0000FF);
                buffer = "";

            } } catch (Exception e) {
            textView.setText("Error");
            buffer = "";
        }
    }
    //    private String buffer = "";
//    @Override
//    public void onNewData(byte[] data) {
//        buffer += new String(data);
//        //Log.d("UART", "Received: " + new String(data));
//        if (buffer.contains("#") && buffer.contains("!")) {
//            try {
//                int index_soc = buffer.indexOf("#");
//                int index_eoc = buffer.indexOf("!");
//                String sentData = buffer.substring(index_soc + 1, index_eoc);
//                buffer = "";
//                Log.d("UART", sentData);
//                sendDataMQTT(sentData);
//            } catch (Exception e) {
//
//            }
//        }
//    }
//
    @Override
    public void onRunError(Exception e) {

    }

    private void requestDataFromAdafruit(String ID, String value){
        OkHttpClient okHttpClient = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        String apiURL = "https://io.adafruit.com/tiviluson/feeds/iot-sender-receiver";
        final Request request = builder.url(apiURL).build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {
                int code = response.code();
                Log.d("Server JSON", String.valueOf(code));
                String message = response.body().string();
                int idx = message.indexOf("last_value");
                Character value = message.charAt(idx+"last_value".length()+3);
                //Log.d("Last Value", value.toString());
                if (value == '1') {
                    try {
//                        ledStatus.setBackgroundColor(Color.rgb(3, 255, 3));
//                        ledStatus.setText("ON");
                        LedOn();
                        port.write("1".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                }
                if (value == '0') {
                    try {
                        LedOff();
                        port.write("0".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                }
            }
        });
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            decorView.setSystemUiVisibility(hideSystemBar());
        }
    }

    private int hideSystemBar() {
        return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    }
}