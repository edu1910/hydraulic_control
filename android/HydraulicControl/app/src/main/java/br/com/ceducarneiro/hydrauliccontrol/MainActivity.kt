package br.com.ceducarneiro.hydrauliccontrol

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.CompoundButton
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.InputStream
import java.io.OutputStream


const val REQUEST_ENABLE_BT = 10

class MainActivity : AppCompatActivity(), DeviceConnect.DeviceConnectListener,
        SeekBar.OnSeekBarChangeListener, UploadMotorsValuesListener, DownloadMotorsValuesListener,
        CompoundButton.OnCheckedChangeListener, DemoModeListener {

    private var mConnectionDialog : AlertDialog? = null
    private var mBluetoothAdapter : BluetoothAdapter? = null
    private var mDevice : BluetoothDevice? = null
    private var mSocket : BluetoothSocket? = null
    private var mInputStream : InputStream? = null
    private var mOutputStream : OutputStream? = null

    private var demoTask: DemoModeTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startConnection()
    }

    override fun onResume() {
        super.onResume()

        seekbar_engine1.setOnSeekBarChangeListener(this)
        seekbar_engine2.setOnSeekBarChangeListener(this)
        seekbar_engine3.setOnSeekBarChangeListener(this)
        seekbar_engine4.setOnSeekBarChangeListener(this)
        switch_present.setOnCheckedChangeListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                connectToDevice()
            } else {
                finish()
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        closeCurrentDialog()

        if (mSocket != null) {
            mSocket?.close()
        }

        super.onDestroy()
    }

    override fun onConnectStart() {
        // Empty
    }

    override fun onConnectFinish(socket: BluetoothSocket?) {
        mSocket = socket

        if (socket != null) {
            mInputStream = socket.inputStream
            mOutputStream = socket.outputStream

            downloadMotorsValues()
            closeCurrentDialog()
        } else {
            startConnection()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        val lastValue: Int = if (seekBar != null && seekBar.tag != null) seekBar.tag as Int else 0

        if ((lastValue < progress && lastValue+20 <= progress)
                || (lastValue > progress && lastValue-20 >= progress)) {
            seekBar?.tag = progress
            uploadMotorsValues()
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        seekbar_engine1.isEnabled = !isChecked
        seekbar_engine2.isEnabled = !isChecked
        seekbar_engine3.isEnabled = !isChecked
        seekbar_engine4.isEnabled = !isChecked

        if (demoTask != null) {
            demoTask!!.cancel(true)
        }

        if (isChecked) {
            demoTask = DemoModeTask(this)
            demoTask!!.execute()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        // Empty
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        uploadMotorsValues()
    }

    override fun onUploadMotorsValuesStart() {
        // Empty
    }

    override fun onUploadMotorsValuesFinish(success : Boolean) {
        if (!success) {
            if (mSocket != null) {
                try {
                    mSocket?.close()
                } catch (ex : Exception) {
                    // Empty
                }

                mSocket = null
                startConnection()
            }
        }
    }

    override fun onDownloadMotorsValuesStart() {
        seekbar_engine1.setOnSeekBarChangeListener(null)
        seekbar_engine2.setOnSeekBarChangeListener(null)
        seekbar_engine3.setOnSeekBarChangeListener(null)
        seekbar_engine4.setOnSeekBarChangeListener(null)
    }

    override fun onDownloadMotorsValuesFinish(success: Boolean, motor1: Int?, motor2: Int?, motor3: Int?, motor4: Int?) {
        if (success) {
            seekbar_engine1.progress = motor1!!
            seekbar_engine2.progress = motor2!!
            seekbar_engine3.progress = motor3!!
            seekbar_engine4.progress = motor4!!
        } else {
            startConnection()
        }

        seekbar_engine1.setOnSeekBarChangeListener(this)
        seekbar_engine2.setOnSeekBarChangeListener(this)
        seekbar_engine3.setOnSeekBarChangeListener(this)
        seekbar_engine4.setOnSeekBarChangeListener(this)
    }

    override fun onDemoModeAngleChanged(angles: Array<Int?>) {
        seekbar_engine1.setOnSeekBarChangeListener(null)
        seekbar_engine2.setOnSeekBarChangeListener(null)
        seekbar_engine3.setOnSeekBarChangeListener(null)
        seekbar_engine4.setOnSeekBarChangeListener(null)

        if (angles.size == 4) {
            seekbar_engine1.progress = angles[0]!!
            seekbar_engine1.tag = angles[0]!!
            seekbar_engine2.progress = angles[1]!!
            seekbar_engine2.tag = angles[1]!!
            seekbar_engine3.progress = angles[2]!!
            seekbar_engine3.tag = angles[2]!!
            seekbar_engine4.progress = angles[3]!!
            seekbar_engine4.tag = angles[3]!!
            uploadMotorsValues()
        }

        seekbar_engine1.setOnSeekBarChangeListener(this)
        seekbar_engine2.setOnSeekBarChangeListener(this)
        seekbar_engine3.setOnSeekBarChangeListener(this)
        seekbar_engine4.setOnSeekBarChangeListener(this)
    }

    private fun uploadMotorsValues() {
        UploadMotorsValuesTask(mOutputStream!!, this)
                .executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, seekbar_engine1.progress,
                        seekbar_engine2.progress, seekbar_engine3.progress, seekbar_engine4.progress)
    }

    private fun startConnection() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            finish()
        } else {
            if (enableBluetooth()) {
                connectToDevice()
            }
        }
    }

    private fun downloadMotorsValues() {
        DownloadMotorsValuesTask(mOutputStream!!, mInputStream!!, this).execute()
    }

    private fun closeCurrentDialog() {
        if (mConnectionDialog != null && mConnectionDialog?.isShowing!!)
            mConnectionDialog?.dismiss()
    }

    private fun enableBluetooth() : Boolean {
        val needEnabled = !mBluetoothAdapter?.isEnabled!!

        if (needEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        return !needEnabled
    }

    private fun connectToDevice() {
        showWaitingConnDialog()

        val pairedDevices = mBluetoothAdapter?.bondedDevices
        mDevice = null

        if (pairedDevices?.size!! > 0) {
            for (device in pairedDevices) {
                if (device.address == "20:15:03:18:19:71") {
                    mDevice = device
                    break
                }
            }
        }

        if (mDevice != null) {
            DeviceConnect(mDevice!!, this).execute()
        } else {
            finish()
        }
    }

    private fun showWaitingConnDialog() {
        if (!isFinishing && (mConnectionDialog == null || !mConnectionDialog!!.isShowing)) {
            closeCurrentDialog()
            mConnectionDialog = AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setNegativeButton(getString(R.string.cancel), { _, _ -> finish() })
                    .setTitle(getString(R.string.waiting_connection_title))
                    .setMessage(getString(R.string.waiting_connection_message))
                    .show()
        }
    }

    class DownloadResult {
        var success: Boolean = false
        var motor1: Int? = null
        var motor2: Int? = null
        var motor3: Int? = null
        var motor4: Int? = null
    }

    class DownloadMotorsValuesTask(private val outputStream : OutputStream,
                                   private val inputStream: InputStream,
                                   private val listener: DownloadMotorsValuesListener)
        : AsyncTask<Int, Void?, DownloadResult>() {

        override fun onPreExecute() {
            listener.onDownloadMotorsValuesStart()
        }

        override fun doInBackground(vararg params: Int?): DownloadResult {
            val result = DownloadResult()

            try {
                outputStream.write(0xF5)
                val respondeCode = inputStream.read()

                if (respondeCode == 0xF5) {
                    result.motor1 = 180-inputStream.read()
                    result.motor2 = 180-inputStream.read()
                    result.motor3 = 180-inputStream.read()
                    result.motor4 = 180-inputStream.read()
                    result.success = true
                }
            } catch (ex : Exception) {
                result.success = false
            }

            return result
        }

        override fun onPostExecute(result: DownloadResult) {
            listener.onDownloadMotorsValuesFinish(result.success, result.motor1,
                    result.motor2, result.motor3, result.motor4)
        }

    }

    class UploadMotorsValuesTask(private val outputStream : OutputStream,
                                 private val listener : UploadMotorsValuesListener)
        : AsyncTask<Int, Void?, Boolean>() {

        override fun onPreExecute() {
            listener.onUploadMotorsValuesStart()
        }

        override fun doInBackground(vararg params : Int?): Boolean {
            var success = true

            try {
                val motor1 = 180-params[0]!!
                val motor2 = 180-params[1]!!
                val motor3 = 180-params[2]!!
                val motor4 = 180-params[3]!!

                outputStream.write(0xF7)
                outputStream.write(motor1)
                outputStream.write(motor2)
                outputStream.write(motor3)
                outputStream.write(motor4)

                Thread.sleep(200)

                Log.d("MOTORS", String.format("[1: %03d] [2: %03d] [3: %03d] [4: %03d]",
                        motor1, motor2, motor3, motor4))
            } catch (ex : Exception) {
                success = false
            }

            return success
        }

        override fun onPostExecute(success : Boolean) {
            listener.onUploadMotorsValuesFinish(success)
        }

    }

    class DemoModeTask(private val listener: DemoModeListener): AsyncTask<Void?, Int?, Void?>() {

        private var angles: Array<Int> = Array(4){0}
        private var increasing = true

        override fun onPreExecute() {
            // Empty
        }

        override fun doInBackground(vararg params : Void?): Void? {
            while (true) {
                onProgressUpdate(angles[0], angles[1], angles[2], angles[3])

                if (increasing) {
                    for (idx in 0..3) {
                        angles[idx] = minOf(180, angles[idx] + 20)

                        if (angles[idx] == 180) {
                            increasing = false
                        }
                    }
                } else {
                    for (idx in 0..3) {
                        angles[idx] = maxOf(0, angles[idx] - 20)

                        if (angles[idx] == 0) {
                            increasing = true
                        }
                    }
                }

                Thread.sleep(500)
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            listener.onDemoModeAngleChanged(values.toList().toTypedArray())
        }

        override fun onPostExecute(result : Void?) {
            // Empty
        }

    }

}

interface DownloadMotorsValuesListener {
    fun onDownloadMotorsValuesStart()
    fun onDownloadMotorsValuesFinish(success : Boolean, motor1 : Int?, motor2 : Int?, motor3 : Int?, motor4 : Int?)
}

interface UploadMotorsValuesListener {
    fun onUploadMotorsValuesStart()
    fun onUploadMotorsValuesFinish(success : Boolean)
}

interface DemoModeListener {
    fun onDemoModeAngleChanged(angles: Array<Int?>)
}
