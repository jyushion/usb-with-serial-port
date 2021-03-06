package com.hd.serialport.method

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.serialport.SerialPortFinder
import com.hd.serialport.config.UsbPortDeviceType
import com.hd.serialport.engine.SerialPortEngine
import com.hd.serialport.engine.UsbPortEngine
import com.hd.serialport.help.RequestUsbPermission
import com.hd.serialport.help.SystemSecurity
import com.hd.serialport.listener.SerialPortMeasureListener
import com.hd.serialport.listener.UsbMeasureListener
import com.hd.serialport.param.SerialPortMeasureParameter
import com.hd.serialport.param.UsbMeasureParameter
import com.hd.serialport.usb_driver.*
import com.hd.serialport.utils.L
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by hd on 2017/8/22 .
 * usb device measurement controller
 */
@SuppressLint("StaticFieldLeak")
object DeviceMeasureController {

    private lateinit var usbManager: UsbManager

    private lateinit var usbPortEngine: UsbPortEngine

    private lateinit var serialPortEngine: SerialPortEngine

    private var usbPortMeasure = false

    private var serialPortMeasure = false

    fun init(context: Context, openLog: Boolean) {
        init(context, openLog, null)
    }

    fun init(context: Context, openLog: Boolean, callback: RequestUsbPermission.RequestPermissionCallback? = null) {
        init(context, openLog, true,callback)
    }

    fun init(context: Context, openLog: Boolean, requestUsbPermission: Boolean, callback: RequestUsbPermission.RequestPermissionCallback? = null) {
        if (!SystemSecurity.check(context)) throw RuntimeException("There are a error in the current system usb module !")
        L.allowLog = openLog
        usbPortMeasure = false
        serialPortMeasure = false
        this.usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        serialPortEngine = SerialPortEngine(context.applicationContext)
        usbPortEngine = UsbPortEngine(context.applicationContext, usbManager)
        if (requestUsbPermission)
            RequestUsbPermission.newInstance().requestAllUsbDevicePermission(context.applicationContext, callback)
    }

    fun scanUsbPort(): List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    fun scanSerialPort(): ConcurrentHashMap<String, String> = SerialPortFinder().allDevices

    fun measure(usbDevice: UsbDevice, deviceType: UsbPortDeviceType, usbMeasureParameter: UsbMeasureParameter, usbMeasureListener: UsbMeasureListener) {
        val driver = when (deviceType) {
            UsbPortDeviceType.USB_CDC_ACM -> CdcAcmSerialDriver(usbDevice)
            UsbPortDeviceType.USB_CP21xx -> Cp21xxSerialDriver(usbDevice)
            UsbPortDeviceType.USB_FTD -> FtdiSerialDriver(usbDevice)
            UsbPortDeviceType.USB_PL2303 -> ProlificSerialDriver(usbDevice)
            UsbPortDeviceType.USB_CH34xx -> Ch34xSerialDriver(usbDevice)
            else -> throw NullPointerException("unknown usb device type:$deviceType")
        }
        measure(driver.ports[0], usbMeasureParameter, usbMeasureListener)
    }

    fun measure(usbSerialDriverList: List<UsbSerialDriver>?, usbMeasureParameter: UsbMeasureParameter, usbMeasureListener: UsbMeasureListener) {
        if (usbSerialDriverList != null) {
            usbSerialDriverList.filter { it.deviceType == usbMeasureParameter.usbPortDeviceType || usbMeasureParameter.usbPortDeviceType == UsbPortDeviceType.USB_OTHERS }
                    .filter { it.ports[0] != null }.forEach { measure(it.ports[0], usbMeasureParameter, usbMeasureListener) }
        } else {
            measure(scanUsbPort(), usbMeasureParameter, usbMeasureListener)
        }
    }

    fun measure(usbSerialPort: UsbSerialPort?, usbMeasureParameter: UsbMeasureParameter, usbMeasureListener: UsbMeasureListener) {
        if (usbSerialPort != null) {
            usbPortEngine.open(usbSerialPort, usbMeasureParameter, usbMeasureListener)
            usbPortMeasure = true
        } else {
            measure(usbSerialDriverList = null, usbMeasureParameter = usbMeasureParameter, usbMeasureListener = usbMeasureListener)
        }
    }

    fun measure(paths: Array<String>?, serialPortMeasureParameter: SerialPortMeasureParameter, serialPortMeasureListeners: List<SerialPortMeasureListener>) {
        if (paths != null) {
            for (index in paths.indices) {
                val path = paths[index]
                if (!path.isEmpty()) {
                    serialPortMeasureParameter.devicePath = path
                    if (serialPortMeasureListeners.size == paths.size) {
                        measure(serialPortMeasureParameter, serialPortMeasureListeners[index])
                    } else {
                        measure(serialPortMeasureParameter, serialPortMeasureListeners[0])
                    }
                }
            }
        } else {
            measure(SerialPortFinder().allDevicesPath, serialPortMeasureParameter, serialPortMeasureListeners)
        }
    }

    fun measure(serialPortMeasureParameter: SerialPortMeasureParameter, serialPortMeasureListener: SerialPortMeasureListener) {
        if (!serialPortMeasureParameter.devicePath.isNullOrEmpty()) {
            serialPortEngine.open(serialPortMeasureParameter, serialPortMeasureListener)
            serialPortMeasure = true
        } else {
            measure(paths = null, serialPortMeasureParameter = serialPortMeasureParameter, serialPortMeasureListeners = listOf(serialPortMeasureListener))
        }
    }

    fun write(data: List<ByteArray>?) {
        if (usbPortMeasure)
            usbPortEngine.write(data)
        if (serialPortMeasure)
            serialPortEngine.write(data)
    }

    fun stop() {
        L.d("DeviceMeasureController stop")
        if (usbPortMeasure) {
            usbPortEngine.stop()
            usbPortMeasure = false
        }
        if (serialPortMeasure) {
            serialPortEngine.stop()
            serialPortMeasure = false
        }
    }
}