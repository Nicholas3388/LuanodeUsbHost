package host.usb.com.luanodeusbhost;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements UsbReceiver.UsbReceiverListener {
    private final static String TAG = "MainActivity";
    private TextView consoleView;
    private ScrollView scrollView;
    private ProgressBar progressBar;
    private Button sendButton;

    private UsbReceiver usbReceiver;
    private UsbManager usbManager;
    private SerialInputOutputManager serialIoManager;
    private UsbSerialPort sPort = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isGranted = false;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 8000;

    private List<UsbSerialPort> entries = new ArrayList<UsbSerialPort>();

    private final SerialInputOutputManager.Listener serialListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.updateReceivedData(data);
                }
            });
        }
    };

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();
                    handler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initListener();
        init();
        output("Baud rate: " + Constants.BAUD_RATE);
        output("Wait for attaching ......");
    }

    @Override
    protected void onPause() {
        super.onPause();
        serialPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        serialResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.clear_screen) {
            consoleView.setText("");
        } else if (id == R.id.luanode_homepage) {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse("https://github.com/Nicholas3388/LuaNode");
            intent.setData(content_url);
            startActivity(intent);
        } else if (id == R.id.pause_button) {
            output("Serial pause");
            serialPause();
        } else if (id == R.id.resume_button) {
            output("Serial resume");
            serialResume();
        }

        return super.onOptionsItemSelected(item);
    }

    private void initView() {
        consoleView = (TextView) findViewById(R.id.consoleText);
        scrollView = (ScrollView) findViewById(R.id.demoScroller);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        sendButton = (Button) findViewById(R.id.sendButton);
    }

    private void initListener() {
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sPort == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Serial device not found")
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // do nothing
                                }
                            });
                            /*.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });*/
                    AlertDialog alert = builder.create();
                    alert.show();
                }
            }
        });
    }

    private void init() {
        usbReceiver = new UsbReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(Constants.ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        new UsbReceiver().setUsbReceiverListener(this);
        hideProgressBar();
    }

    private void serialPause() {
        handler.removeMessages(MESSAGE_REFRESH);
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
                output("Port closed");
            } catch (IOException e) {
                // Ignore.
                output("Close port failed!");
            }
            sPort = null;
            entries.clear();
            isGranted = false;
        }
    }

    private void serialResume() {
        if (entries.isEmpty() && !isGranted) {
            handler.sendEmptyMessage(MESSAGE_REFRESH);
            return;
        }
        Log.d(TAG, "Resumed, port=" + sPort);
        serialInit();
    }

    private void serialInit() {
        if (sPort == null) {
            output("No serial device.");
        } else {
            final UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection = manager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                output("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(Constants.BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                showStatus("CD  - Carrier Detect", sPort.getCD());
                showStatus("CTS - Clear To Send", sPort.getCTS());
                showStatus("DSR - Data Set Ready", sPort.getDSR());
                showStatus( "DTR - Data Terminal Ready", sPort.getDTR());
                showStatus("DSR - Data Set Ready", sPort.getDSR());
                showStatus("RI  - Ring Indicator", sPort.getRI());
                showStatus("RTS - Request To Send", sPort.getRTS());
                scrollView.smoothScrollTo(0, consoleView.getBottom());
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                output("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                    output("Close port failed!");
                }
                sPort = null;
                return;
            }
            output("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void refreshDeviceList() {
        showProgressBar();
        output("Searching devices ......");

        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);
                final List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    Log.d(TAG, String.format("+ %s: %s port%s", driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                entries.clear();
                entries.addAll(result);
                hideProgressBar();
                Toast.makeText(MainActivity.this, entries.size() + " device found.", Toast.LENGTH_SHORT).show();
                output("Done searching, " + entries.size() + " device found.");
                startDeviceListening();
            }
        }.execute((Void) null);
    }

    private void startDeviceListening() {
        if (entries.isEmpty()) { return; }
        sPort = entries.get(0);
        if (sPort == null) {
            output("Cannot access device!");
        } else {
            handler.removeMessages(MESSAGE_REFRESH);
            serialInit();
        }
    }

    private void updateReceivedData(byte[] data) {
        //final String message = "Read " + data.length + " bytes: \n" + HexDump.dumpHexString(data) + "\n\n";
        final String message = "Read " + data.length + " bytes: \n" + new String(data) + "\n\n";
        output(message);
        scrollView.smoothScrollTo(0, consoleView.getBottom());
    }

    void showStatus(String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        output(msg);
    }

    private void stopIoManager() {
        if (serialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            serialIoManager.stop();
            serialIoManager = null;
            output("Stopping io manager ..");
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            serialIoManager = new SerialInputOutputManager(sPort, serialListener);
            executor.submit(serialIoManager);
            output("Starting io manager ..");
            try {
                sPort.setDTR(true);
                sPort.setRTS(true);
            } catch (Exception e) {
                e.printStackTrace();
                output("Set port failed");
            }
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void output(String content) {
        consoleView.append("> " + content + "\n");
    }

    public void onRequestPermission(UsbDevice device) {
        PendingIntent permissionIntent;
        permissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(Constants.ACTION_USB_PERMISSION), PendingIntent.FLAG_ONE_SHOT);
        usbManager.requestPermission(device, permissionIntent);
    }

    public void onPermissionGranted() {
        output("Permission granted!");
        refreshDeviceList();
    }

    public void onDeviceDetached() {
        output("Device detached!");
        // do something when device detached
        sPort = null;
        entries.clear();
        isGranted = false;
    }

    public void onDeviceAttached() {
        isGranted = true;
    }

    public void onUsbReceiverLog(String msg) {
        output(msg);
    }

}
