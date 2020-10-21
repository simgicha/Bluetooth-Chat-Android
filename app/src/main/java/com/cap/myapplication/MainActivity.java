package com.cap.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ServiceConnection, SerialListener{

    private String newline = "\r\n";

    private TextView receiveText;

    private SerialService service;
    private boolean initialStart = true;
    private enum Connected { False, Pending, True }
    private Connected connected = Connected.False;
    Button send_btn;
    EditText send_text;
    String scale_name_connect = "";
    private ArrayAdapter<String> BTArrayAdapter;

    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<BluetoothDevice>();
    private BluetoothDevice device;
    Button btnchangedevice;
    TextView txtscale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receiveText = (TextView)findViewById(R.id.receive_text);
         
		// TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        this.bindService(new Intent(getApplicationContext(), SerialService.class), this, Context.BIND_AUTO_CREATE);


        send_btn = (Button)findViewById(R.id.send_btn);
        send_text = (EditText)findViewById(R.id.send_text);
        send_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(send_text.getText().toString());
            }
        });

        btnchangedevice = (Button)findViewById(R.id.btnchangedevice);
        btnchangedevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectEvent();
            }
        });

        txtscale = (TextView)findViewById(R.id.txtscale);


        //showBluetoothDevices();
        connectEvent();
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnectScale();
        stopService(new Intent(getApplicationContext(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        Log.v("LLLLLLLLLLL", "MMMMMMMM HERE AT START "+service.toString());
        service.attach(this);

        if(initialStart) {
            initialStart = false;
            this.runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            startService(new Intent(getApplicationContext(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !this.isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            String devicenam = scale_name_connect;
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            Iterator<BluetoothDevice> iterator = pairedDevices.iterator();
            while (iterator.hasNext()) {
                BluetoothDevice device_find = iterator.next();
                Log.w(this.getClass().getSimpleName(),"Found " + device_find.getAddress() + " = "+ device_find.getName());
                if (device_find.getName().equals(devicenam)) {//WC Scale//"BTM0304C1H"

                    Log.v("XXXX", "XXXX "+device_find.getAddress());

                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_find.getAddress());
                    status("connecting...");
                    Log.v("JJJJJJ", device.getName()+" = "+device.getAddress());
                    //connected = Connected.Pending;
                    SerialSocket socket = new SerialSocket(getApplicationContext(), device);

                    service.connect(socket);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            //onSerialConnectError(e);
        }
    }

    private void disconnectScale() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getApplicationContext(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            byte[] data = (str + newline).getBytes();
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {

        receiveText.append(new String(data));
        String string = new String(data);

        //String numberOnly= string.replaceAll("[^0-9]", "");


        //receiveText.setText(string);


    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnectScale();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnectScale();
    }

   /* public void showBluetoothDevices(){
        builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("Make your selection");
        //create the arrayAdapter that contains the BTDevices, and set it to the ListView
        BTArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1);
        builder.setAdapter(BTArrayAdapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {

                Log.v("Device name", ""+item);
                BluetoothDevice dev = mDeviceList.get(item);
                Log.v("Item paring ", dev.getName());
                pairDevice(dev);
            }
        });
        //BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        //btAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(mReceiver, filter);
    }
*/
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    //showToast("Enabled");

                    //showEnabled();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mDeviceList = new ArrayList<BluetoothDevice>();

                //mProgressDlg.show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.v("RRRRRRRRRrrr","TTTTTTTTTTTTTTTTTTTTT");

/*                Intent newIntent = new Intent(getApplicationContext().getApplicationContext(), DeviceListActivity.class);

                newIntent.putParcelableArrayListExtra("device.list", mDeviceList);

                startActivity(newIntent);*/
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                mDeviceList.add(device);
                //Log.v("DDDDDDDDDDDDDDD",device.getName());

                //showToast("Found device " + device.getName());
            }
        }
    };



    private void pairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void connectEvent() {
        Log.v("KKKKKKK", "KKKKK");
        try {
//
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            final ArrayList<String> mArrayAdapter = new ArrayList<String>();

            // search target device in list of paired devices
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            Iterator<BluetoothDevice> iterator = pairedDevices.iterator();
            String stuff = "";
            while (iterator.hasNext()) {
                device = iterator.next();
                Log.w(this.getClass().getSimpleName(),
                        "Found " + device.getAddress() + " = "
                                + device.getName());
                mArrayAdapter.add(device.getAddress()+"\n"+device.getName());
            }

            CharSequence[] cs = mArrayAdapter.toArray(new CharSequence[mArrayAdapter.size()]);
            //AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this,R.style.AlertDialogTheme);
            alertDialogBuilder.setTitle("Select Scale");
            alertDialogBuilder.setInverseBackgroundForced(true);
            alertDialogBuilder.setItems(cs, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // The 'which' argument contains the index position
                    // of the selected item
                    Log.v("!!!!!!!!!!!!!",""+mArrayAdapter.get(which));
                    String selected_device = "";
                    selected_device = mArrayAdapter.get(which);
                    String[] arrInfo=selected_device.split("\n");
                    Log.v("****", arrInfo[0]);

                    //Save to database
                    ContentValues collect = new ContentValues();
                    collect.put("scale_name", arrInfo[1]);
                    collect.put("scale_address", arrInfo[0]);

                    scale_name_connect = arrInfo[1];
                    txtscale.setText(scale_name_connect);
                    connect();


                    dialog.dismiss();
                }
            });

            AlertDialog alert = alertDialogBuilder.create();
            alert.show();

        } catch (Exception e) {
        }
    }


}
