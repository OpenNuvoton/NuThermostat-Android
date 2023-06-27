package com.nuvoton.nuthermostat_android.ISP

import android.os.Build
import androidx.annotation.RequiresApi
import com.nuvoton.nuthermostat_android.Util.Log

enum class NulinkInterfaceType constructor(val value: Byte) {

    USB    (0x00),
//  HID    (0x01),
    UART   (0x00),
    SPI    (0x03),
    I2C    (0x04),
    RS485  (0x05),
    CAN    (0x06),
    WiFi   (0x07),
    BLE    (0x08)
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object ISPManager {
    /**
     * Config 設定值
     */
    private var read_endpoint_index = 0
    private var write_endpoint_index = 1
    private var connect_interface_index = 0
    private var byteSize = 64
    private val forceClaim = true
    private val timeOut = 100
    private val isSearchLoop = false

    public var packetNumber: UInt = (0x00000005).toUInt()
    public var interfaceType : NulinkInterfaceType = NulinkInterfaceType.WiFi

    private var _readListener: ((ByteArray) -> Unit)? = null
    private var _byteArrayResultListener: ((ByteArray) -> Unit)? = null
//    fun setByteArrayRequestListener(callbacks: (ByteArray)->Unit){
//        _byteArrayResultListener = callbacks
//    }

    fun sendCMD_UPDATE_BIN(cmd: ISPCommands ,sendByteArray:ByteArray,startAddress:UInt, callback: ((ByteArray?, Int) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_UPDATE_BIN(cmd,sendByteArray,startAddress, callback = { readBuffer, P ->
                callback.invoke(readBuffer, P)
            })
            return
        }

    }


    fun sendCMD_ERASE_ALL( callback: ((ByteArray?, Boolean) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_ERASE_ALL { readBuffer, isChecksum ->
                callback.invoke(readBuffer, isChecksum)
            }
            return
        }

    }

    fun sendCMD_READ_CONFIG( callback: ((ByteArray?) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_READ_CONFIG {
                callback.invoke(it)
            }
            return
        }


    }

    fun sendCMD_GET_FWVER(callback: ((ByteArray?, Boolean) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_GET_FWVER { readBuffer, isChecksum ->
                callback.invoke(readBuffer, isChecksum)
            }
            return
        }

    }

    fun sendCMD_RUN_APROM( callback: ((Boolean) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_RUN_APROM {
                callback.invoke(it)
            }
            return
        }
    }

    fun sendCMD_UPDATE_CONFIG(config0: UInt,config1: UInt,config2: UInt,config3: UInt, callback: ((ByteArray?) -> Unit)) {

        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_UPDATE_CONFIG(config0,config1,config2,config3, callback = {
                callback.invoke(it)
            })
            return
        }

    }

//    fun sendCMD_SYNC_PACKNO( callback: ((ByteArray?) -> Unit)) {
//
//        val cmd = ISPCommands.CMD_SYNC_PACKNO
//        val sendBuffer = ISPCommandTool.toCMD(cmd, packetNumber)
//        this.write( sendBuffer)
//        val readBuffer = this.read()
//        var isChecksum = this.isChecksum_PackNo(sendBuffer, readBuffer)
//    }

    fun sendCMD_GET_DEVICEID( callback: ((ByteArray?, Boolean) -> Unit)) {


        //如果是WiFi
        if(ISPManager.interfaceType == NulinkInterfaceType.WiFi){
            ISPCmdManager.sendCMD_GET_DEVICEID { readBuffer, isChecksum ->
                callback.invoke(readBuffer,isChecksum)
            }
            return
        }
    }

//    private fun sendCMD(usbDevice: UsbDevice, cmd: ISPCommands) {
//
//        thread {
//            val sendBuffer = ISPCommandTool.toCMD(cmd, packetNumber)
//            this.write(usbDevice, sendBuffer)
//            val readBuffer = this.read(usbDevice)
//            this.isChecksum_PackNo(sendBuffer, readBuffer)
//
//        }
//    }

    public fun isChecksum_PackNo(sendBuffer: ByteArray, readBuffer: ByteArray?): Boolean {

        if (readBuffer == null) {
            Log.i("isChecksum_PackNo", "readBuffer == null")
            return false
        }

        //如果是CAN 無條件回true CAN沒有Checksum
        if(ISPManager.interfaceType == NulinkInterfaceType.CAN){
            return true
        }

        //checksum
        val checksum = ISPCommandTool.toChecksumBySendBuffer(sendBuffer)
        val resultChecksum = ISPCommandTool.toChecksumByReadBuffer(readBuffer)

        if (checksum != resultChecksum) {
            Log.i("isChecksum_PackNo", "checksum $checksum != resultChecksum $resultChecksum")
            return false
        }

        //checkPackNo
        val packNo = packetNumber + (0x00000001).toUInt()
        val resultPackNo = ISPCommandTool.toPackNo(readBuffer)

        if (packNo.toUInt() != resultPackNo) {
            Log.i("isChecksum_PackNo", "packNo $packNo != resultPackNo $resultPackNo")
            return false
        }
        packetNumber = packNo + (0x00000001).toUInt()
        Log.i(
            "isChecksum_PackNo",
            "packNo $packNo == resultPackNo $resultPackNo ,checksum $checksum == resultChecksum $resultChecksum"
        )
        return true
    }




}

