package me.lifegrep.heart.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.SimpleDateFormat;
import java.util.Date;

import me.lifegrep.heart.R;
import me.lifegrep.heart.model.DailyActivity;
import me.lifegrep.heart.model.Event;
import me.lifegrep.heart.services.BluetoothLeService;
import me.lifegrep.heart.services.ScratchWriter;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    ToggleButton heartToggle;
    TextView heartbeat;
    Button startScan;
    TextView pairedDevice;
    Spinner dailyActivities;
    FloatingActionButton button_start, button_stop;
    ArrayAdapter<CharSequence> postureAdapter, categoriesAdapter;

    private BluetoothAdapter blueAdapter;
    private BluetoothLeService blueService;
    private boolean serviceConnected;

    private String deviceAddress; // "00:22:D0:85:88:8E";

    private final int REQUEST_SCAN = 1;
    private static SimpleDateFormat formatActivityFilename = new SimpleDateFormat("yyMMdd");

    //region lifecycle methods

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity created");

        setContentView(R.layout.activity_main);
        heartToggle = (ToggleButton) findViewById(R.id.tg_heart);
        heartToggle.setEnabled(false);
        heartbeat = (TextView) findViewById(R.id.tv_heartrate);
        startScan = (Button) findViewById(R.id.bt_pair);
        pairedDevice = (TextView) findViewById(R.id.tv_paired_device);
        //TODO add posture radio buttons
        dailyActivities = (Spinner) findViewById(R.id.sp_activity);
        button_start = (FloatingActionButton) findViewById(R.id.ab_start);
        button_stop = (FloatingActionButton) findViewById(R.id.ab_stop);


        /*postureAdapter = ArrayAdapter.createFromResource(this,
                                                         R.array.postures,
                                                         android.R.layout.simple_spinner_item);*/
        categoriesAdapter = ArrayAdapter.createFromResource(this,
                R.array.activity_categories_descriptors,
                android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        // postureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categoriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        // posture.setAdapter(postureAdapter);
        dailyActivities.setAdapter(categoriesAdapter);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "Activity starting");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            this.heartToggle.setEnabled(false);
            this.startScan.setEnabled(false);
            this.pairedDevice.setText(R.string.text_monitornobluetooth);
            this.heartbeat.setText("");
            return;
        }

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        this.deviceAddress = sharedPref.getString(getString(R.string.paired_device), "");
        if (this.deviceAddress != null && this.deviceAddress != "") {
            this.pairedDevice.setText(this.deviceAddress);
            startMonitoringService();
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "Activity on pause");
    }


    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "Activity stopping");
        unregisterReceiver(gattUpdateReceiver);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Activity on destroy");
        // TODO how to check that they are running to unbind/unregister??
        if (blueService != null) {
            unbindService(serviceConnection);
            blueService = null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    //endregion


    //region  BLUETOOTH LE SERVICE
    // connects to service and receives broadcasts to show HR on screen


    // Listener registered for sw_heart toggle
    //TODO not working
    public void toggleHeartMonitor(View view) {
        if (heartToggle.isChecked()) {
            startMonitoringService();
        } else {
            stopMonitoringService();
            heartbeat.setText(R.string.text_monitoroff);
        }
    }


    /**
     * Allows the user to select a device to pair with the app
     **/
    public void scanDevices(View view) {
        Intent intent_scan = new Intent(this, DeviceScanActivity.class);
        startActivityForResult(intent_scan, REQUEST_SCAN);
    }


    /**
     * Receives the selected device address result from the device scan activity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SCAN) { // Make sure this is the scan result
            if (resultCode == RESULT_OK) { // Make sure the request was successful
                String address = data.getStringExtra("deviceAddr");
                Log.i(TAG, "User selected device with address " + address);
                this.saveDeviceAddress(address);
                this.startMonitoringService();
            }
        }
    }

    protected void saveDeviceAddress(String address) {
        this.deviceAddress = address;
        this.pairedDevice.setText(address);
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.paired_device), address);
        editor.commit();
    }


    /**
     * Starts the heart monitor service running in background
     */
    private void startMonitoringService() {
        if (this.deviceAddress == null || this.deviceAddress == "")
            return;
        Log.i(TAG, "Starting service");
        final Intent blueServiceIntent = new Intent(this, BluetoothLeService.class);
        blueServiceIntent.putExtra("address", this.deviceAddress);
        startService(blueServiceIntent); // needed for Service not to die if activity unbinds
        bindService(blueServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        Log.i(TAG, "Bound to service");
    }


    //TODO figure out the order between disconnecting, unbinding and stopping the service
    private void stopMonitoringService() {
        Log.i(TAG, "Stopping service");
        unregisterReceiver(gattUpdateReceiver);
        blueService.disconnect();
        unbindService(serviceConnection);
        Intent stopIntent = new Intent(this, BluetoothLeService.class);
        blueService.stopService(stopIntent);
        blueService = null;
    }


    /**
     * Get a bluetooth adapter and request the user to enable bluetooth if it is not yet enabled
     * TODO can I start an activity from inside the service? If so, move this to the service, the activity should not be responsible for BT setting
     */
    private BluetoothAdapter getBluetoothAdapter() {

        final BluetoothManager blueManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter blueAdapter = blueManager.getAdapter();

        // enable bluetooth if it is not already enabled
        if (!blueAdapter.isEnabled()) {
            int REQUEST_ENABLE_BT = 1;
            Log.i(TAG, "Requesting bluetooth to turn on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            //TODO check for the case where the user selects not to enable BT
        }
        return blueAdapter;
    }


    // manage service lifecycle.
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service connected to activity");
            blueService = ((BluetoothLeService.LocalBinder) service).getService();
            serviceConnected = true;
            heartToggle.setChecked(true);
        }


        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "Service disconnected from activity");
            serviceConnected = false;
            heartToggle.setChecked(false);
            unregisterReceiver(gattUpdateReceiver);
            // maybe it was an accident? Let's try to get it back!
            startMonitoringService();
        }
    };


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // handles events fired by the service.
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                heartbeat.setText(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }

            if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                heartbeat.setText("Disconnected");
            }
            // blatantly ignore any other events - activity doesn't really care, service does
        }
    };

    //endregion


    //region daily activities saving

    // Listener registered for ab_start
    public void startActivity(View view) {
        int position = dailyActivities.getSelectedItemPosition();
        String curr_act = getResources().getStringArray(R.array.activity_categories_names)[position];
        //TODO add posture name from selected radio
        String curr_posture = "posture";
        DailyActivity activity = new DailyActivity(Event.TP_START, curr_act, curr_posture , new Date());
        saveActivity(activity);

        Toast.makeText(this, "Started: " + this.dailyActivities.getSelectedItem().toString(), Toast.LENGTH_LONG );

        button_stop.setEnabled(true);
        button_stop.setClickable(true);
        button_start.setBackgroundColor(Color.GRAY);
        button_start.setEnabled(false);
        button_start.setClickable(false);
    }

    // Listener registered for ab_stop
    public void stopActivity(View view) {
        //TODO save selected activity on destroy to use it here instead of blanks
        DailyActivity activity = new DailyActivity(Event.TP_STOP, "", "", new Date());
        saveActivity(activity);
        button_start.setEnabled(true);
        button_start.setClickable(true);
        button_stop.setBackgroundColor(Color.GRAY);
        button_stop.setEnabled(false);
        button_stop.setClickable(false);
        Toast.makeText(this, "Stopped activity",Toast.LENGTH_LONG );
    }

    private void saveActivity(DailyActivity activity) {
        String dt = formatActivityFilename.format(new Date());
        ScratchWriter writer = new ScratchWriter(this, "act" + dt + ".csv");
        writer.saveData(activity.toCSV());
    }

    //endregion
}
