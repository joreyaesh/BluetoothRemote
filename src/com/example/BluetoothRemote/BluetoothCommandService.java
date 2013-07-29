
package com.example.BluetoothRemote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;

import com.example.RemoteCommand;
import com.example.RemoteValues;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothCommandService {

    // Parameters
    private long timeLastSend = System.currentTimeMillis() / 10;
    private int mPreviousX = -1;
    private int mPreviousY = -1;
    private int mPreviousScrollY = -1;
    private int downXPosition = -1;
    private int downYPosition = -1;
    private int upXPosition = -1;
    private int upYPosition = -1;
    private int scrollAmount = -1;
    private int parameter1 = 0;
    private int parameter2 = 0;

    // Debugging
    private static final String TAG = "BluetoothCommandService";
    private static final boolean D = true;

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("002b8631-0000-1000-8000-00805f9b34fb");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private BluetoothSocket mSocket;
    private OutputStream mOutStream;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothRemote session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothCommandService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mSocket = null;
    }

    /**
     * Set the current state of the command connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothRemote.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the command service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        mSocket = null;

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        mSocket = null;

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothRemote.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Send the address of the connected device back to the UI Activity
        msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_DEVICE_ADDRESS);
        bundle = new Bundle();
        bundle.putString(BluetoothRemote.DEVICE_ADDRESS, device.getAddress());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothRemote.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothCommandService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothRemote.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Set the state back to STATE_LISTEN
        setState(STATE_LISTEN);


        // Start the service over to restart listening mode
        BluetoothCommandService.this.start();
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = "Secure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(
                        MY_UUID_SECURE);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mSocket.connect();
            } catch (Exception e) {
                // Close the socket
                try {
                    mSocket.close();
                } catch (Exception e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothCommandService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mSocket.close();
                mSocket = null;

            } catch (Exception e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mSocket = socket;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket output stream
            try {
                tmpOut = socket.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mOutStream.write(buffer);
                mOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
                connectionLost();
            }
        }

        public void cancel() {
            try {
                mSocket.close();
                mSocket = null;
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public void checkConnection() {
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.CHECK_CONNECTION;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleNewTab() {
        // handle pressing the "New Tab" option. Simulates a Ctrl+T.
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.NEW_TAB;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleDelete() {
        // handle pressing the "Del" button. Simulates a Ctrl+Backspace.
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.BACKSPACE;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleText(String text) {
        // handle sending text to the computer
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.TYPE;

        rcm.string1 = text;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleEnter() {
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.ENTER;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleLeftClick() {
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.MOUSE_LEFT;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleRightClick() {
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.MOUSE_RIGHT;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    public void handleTouch(MotionEvent m) {
        if(m.getActionMasked() == MotionEvent.ACTION_DOWN){ // for recognizing a tap rather than a move
            downXPosition = (int) m.getX();
            downYPosition = (int) m.getY();
        }

        else if(m.getActionMasked() == MotionEvent.ACTION_UP){ // for recognizing a tap rather than a move
            upXPosition = (int) m.getX();
            upYPosition = (int) m.getY();

            int dx = Math.abs(upXPosition - downXPosition);
            int dy = Math.abs(upYPosition - downYPosition);
            if(dx <= 5 && dy <= 5){
                handleLeftClick();
            }
        }


            if((System.currentTimeMillis() / 10 - timeLastSend) > 2){ // lower amount of packets sent
                int x = (int) m.getX();
                int y = (int) m.getY();
                if(x != 0 || y != 0){
                    int action = m.getActionMasked();
                    if(action != MotionEvent.ACTION_MOVE){
                        mPreviousX = x;
                        mPreviousY = y;
                    }

                    RemoteCommand rcm = new RemoteCommand();
                    byte[] buffer;

                    int dx = x - mPreviousX;
                    int dy = y - mPreviousY;

                    rcm.command = RemoteValues.MOVE_MOUSE_BY;

                    parameter1 = dx;
                    parameter2 = dy;

                    // re-init
                    rcm.parameter1 = parameter1;
                    rcm.parameter2 = parameter2;

                    buffer = rcm.getByteArray();
                    write(buffer);
                    timeLastSend = System.currentTimeMillis() / 10;
                    parameter1= 0;
                    parameter2= 0;

                    mPreviousX = x;
                    mPreviousY = y;
                }
            }
        }

    public void handleMultiTouch(MotionEvent m) {
        if(m.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN){ // for recognizing a tap rather than a move
            downXPosition = (int) m.getX();
            downYPosition = (int) m.getY();
        }

        else if(m.getActionMasked() == MotionEvent.ACTION_POINTER_UP){ // for recognizing a tap rather than a move
            upXPosition = (int) m.getX();
            upYPosition = (int) m.getY();

            int dx = Math.abs(upXPosition - downXPosition);
            int dy = Math.abs(upYPosition - downYPosition);
            if(dx <= 5 && dy <= 5){
                handleRightClick();
            }
        }

        int y = (int) m.getY();
        if(y != 0){
            int action = m.getActionMasked();
            if(action != MotionEvent.ACTION_MOVE){
                mPreviousScrollY = y;
            }

            RemoteCommand rcm = new RemoteCommand();
            byte[] buffer;

            rcm.command = RemoteValues.MOUSE_SCROLL;

            int dy = y - mPreviousScrollY;

            scrollAmount += dy;

            if(scrollAmount >= 50 || scrollAmount <= -50){

                parameter1 = scrollAmount/50;

                // re-init
                rcm.parameter1 = parameter1;

                buffer = rcm.getByteArray();
                write(buffer);
                parameter1= 0;

                scrollAmount = 0;
            }

            mPreviousScrollY = y;


            // to avoid incorrect mouse movements after scrolling
            mPreviousX = (int) m.getX();
            mPreviousY = (int) m.getY();
        }
    }

}
