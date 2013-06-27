
package com.example.BluetoothRemote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.IntentIntegrator;
import com.example.android.IntentResult;

import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * This is the main Activity that displays the current command session.
 */
public class BluetoothRemote extends Activity {
    // Debugging
    private static final String TAG = "BluetoothRemote";
    private static final boolean D = true;

    // Message types sent from the BluetoothCommandService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothCommandService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_BOOKMARK = 5;

    // Layout Views
    private RelativeLayout myLayout;
    private EditText textBox;
    private Button enterButton;
    private Button deleteButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the command services
    private BluetoothCommandService mCommandService = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);

        myLayout = (RelativeLayout) findViewById(R.id.rlayout);
        myLayout.setOnTouchListener(new RelativeLayout.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getPointerCount() == 1)
                    mCommandService.handleTouch(event);
                else if(event.getPointerCount() == 2){
                    mCommandService.handleMultiTouch(event);
                }
                return true;
            }
        });

        textBox = (EditText) findViewById(R.id.textBox);
        textBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                mCommandService.handleText(textBox.getText().toString());
                textBox.setText("");
                return false;
            }
        });

        deleteButton = (Button) findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mCommandService.handleDelete();

            }
        });

        enterButton = (Button) findViewById(R.id.enterButton);
        enterButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCommandService.handleText(textBox.getText().toString());
                textBox.setText("");
                mCommandService.handleEnter();
            }
        });

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupCommand() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        // otherwise set up the command service
        else {
            if (mCommandService==null)
                setupCommand();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mCommandService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mCommandService.getState() == BluetoothCommandService.STATE_NONE) {
              // Start the Bluetooth command services
              mCommandService.start();
            }
        }
    }

    private void setupCommand() {
        if(D)if(D)Log.d(TAG, "setupCommand()");

        // Initialize the BluetoothCommandService to perform bluetooth connections
        mCommandService = new BluetoothCommandService(this, mHandler);
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth command services
        if (mCommandService != null) mCommandService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }



    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }

    // The Handler that gets information back from the BluetoothCommandService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothCommandService.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    break;
                case BluetoothCommandService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothCommandService.STATE_LISTEN:
                case BluetoothCommandService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS));
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a command session
                    setupCommand();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    if(D)Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_BOOKMARK:
                // When the request to open a bookmark returns
                if(resultCode == Activity.RESULT_OK){
                    // Get the URL
                    String url;
                    url = data.getExtras()
                            .getString(BookmarkListActivity.EXTRA_BOOKMARK_URL);
                    // Open the bookmark
                    openBookmark(url);
                }
                break;
            default:
                IntentResult scanResult = IntentIntegrator.parseActivityResult(
                        requestCode, resultCode, data);
                if (scanResult != null) {
                    if(D)Log.d(TAG, scanResult.toString());
                    String address = getAddress(scanResult.getContents());
                    connectDevice(address);
                }
        }
    }

    private String getAddress(String tmp){
        try{
            String[] strings = tmp.split("");
            for(int i = 2; i < tmp.length()-1 ; i += 2){
                strings[i] = strings[i].replace(strings[i], (strings[i]+":"));
            }
            StringBuilder builder = new StringBuilder();
            for(String s : strings) {
                builder.append(s);
            }
            String address = builder.toString();
            return address;
        } catch(NullPointerException e){
            toast("No QR code received");
            if(D)Log.d(TAG, "No QR code received");
        }
        return "";
    }

    private void connectDevice(String address) {
        try{
            // Get the BluetoothDevice object
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mCommandService.connect(device);
        } catch(IllegalArgumentException e){
            if(D)Log.d(TAG, "Incorrect bluetooth address received from QR code");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            case R.id.new_tab:
                // Open a new browser tab
                mCommandService.handleNewTab();
                return true;
            case R.id.add_bookmark:
                // Open a dialog to add a bookmark
                addBookmark();
                return true;
            case R.id.open_bookmark:
                // Launch the BookmarkListActivity to see all bookmarks
                serverIntent = new Intent(this, BookmarkListActivity.class);
                startActivityForResult(serverIntent, REQUEST_BOOKMARK);
                return true;
            case R.id.scan_qr_code:
                // Launch the qr code scanner
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.initiateScan();
                return true;
        }
        return false;
    }

    private void addBookmark() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Enter Bookmark URL");
        alert.setMessage("ex: \"http://www.google.com\"");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String url = input.getText().toString();
                saveBookmark(url);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    public void openBookmark(String url){
        mCommandService.handleNewTab();
        mCommandService.handleText(url);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCommandService.handleEnter();
    }

    private void saveBookmark(String url){
        try
        {
            FileOutputStream fos = openFileOutput("bookmarks.txt", Context.MODE_APPEND);
            fos.write(url.getBytes());
            fos.write(System.getProperty("line.separator").getBytes());
            fos.close();
            toast("Bookmark successfully saved.");
        }
        catch (Exception ex)
        {
            toast("Error saving bookmark: " + ex.getLocalizedMessage());
        }
    }

    private void toast(String text){
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        toast.show();
    }
}
