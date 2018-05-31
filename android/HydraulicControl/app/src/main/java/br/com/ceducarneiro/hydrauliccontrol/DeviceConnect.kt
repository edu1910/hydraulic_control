package br.com.ceducarneiro.hydrauliccontrol

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.AsyncTask
import java.io.IOException
import java.util.*

/**
 * Created by eduardo on 23/03/18.
 */
class DeviceConnect(device : BluetoothDevice, listener : DeviceConnectListener) : AsyncTask<Void?, Void?, BluetoothSocket?>() {

    private var mDevice : BluetoothDevice = device
    private var mListener : DeviceConnectListener = listener

    override fun onPreExecute() {
        mListener.onConnectStart()
    }

    override fun doInBackground(vararg params: Void?): BluetoothSocket? {
        var socket = mDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))

        try {
            socket?.connect()
        } catch (ex : IOException) {
            try {
                socket?.close()
            } catch (ex : IOException) {
                /* Empty */
            }
            socket = null
        }

        return socket
    }

    override fun onPostExecute(socket: BluetoothSocket?) {
        mListener.onConnectFinish(socket)
    }

    interface DeviceConnectListener {
        fun onConnectStart()
        fun onConnectFinish(socket : BluetoothSocket?)
    }
}