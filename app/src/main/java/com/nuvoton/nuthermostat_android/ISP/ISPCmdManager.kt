package com.nuvoton.nuthermostat_android.ISP

import android.os.Build
import androidx.annotation.RequiresApi
import com.nuvoton.nuisptool_android.Util.HEXTool
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import java.net.Socket
import kotlin.concurrent.thread

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object ISPCmdManager {

    private var _responseBuffer:ByteArray = byteArrayOf()
    private var _tempBuffer:ByteArray = byteArrayOf()
    private var _thesameIndex = 0
    public var _isOnlineListener: ((Boolean) -> Unit)? = null
    private var _ispTCPClient:Socket? = null
    private var TAG = "ISPCmdManager"
    
    
    public fun initMainTCPClient(tcpClient: Socket){
        ISPCmdManager._ispTCPClient = tcpClient
        thread {
            SocketManager.funTCPClientReceive(tcpClient, ISPCmdManager.ReadListener)
        }
    }

    public var ReadListener :  ((Socket,ByteArray?) -> Unit) =  { tcpClient , readBf ->

        if (readBf != null) {
            if(readBf.size<64){
                Log.d(TAG, "read Value.size < 64  !!!"  )
                Log.d(TAG, "read Value.size < 64  !!!"  )
                Log.d(TAG, "read Value.size < 64  !!!"  )

                _responseBuffer = _responseBuffer + readBf
            }
            _responseBuffer = readBf
        }
        if(_tempBuffer.contentEquals(readBf)){
            _thesameIndex = _thesameIndex + 1
        }else{
            _thesameIndex = 0
        }
        _tempBuffer = readBf!!.clone()

        if(_thesameIndex > 5){
            Log.e(TAG,"斷線了")
            SocketManager.funTCPClientClose(tcpClient)
            if(_isOnlineListener!=null){
                _isOnlineListener!!.invoke(false)
            }
        }else{
            Log.e("read :", HEXTool.toHexString(readBf!!))
        }
    }

    fun sendCMD_GET_ISP_MODE_ON( callback: ((ByteArray?) -> Unit)) {

        val cmd = DeviceCommands.CMD_GET_ISP_MODE

        val sendBuffer = byteArrayOf(0xc0.toByte())
        val readBuffer = this.write(sendBuffer)

        callback.invoke(_responseBuffer)


    }

    fun sendCMD_SET_ISP_MODE_ON(callback: ((ByteArray?, isTimeout:Boolean) -> Unit)) {

        val cmd = DeviceCommands.CMD_SET_ISP_MODE
        val sendBuffer = byteArrayOf(0xc1.toByte(),0x01,0x01)

        var readBufferStrring = HEXTool.toHexString(sendBuffer)
        var display = HEXTool.toDisPlayString(readBufferStrring)
        Log.i(TAG, "write   CMD:"+display)
        var timeOutIndex = 0
        var isTimeOut = false
        _responseBuffer = byteArrayOf()

        SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer)
        while (_responseBuffer.isEmpty()){
            Thread.sleep(100)

            if(timeOutIndex > 50){
                callback.invoke(_responseBuffer,true)
                isTimeOut = true
                return
            }
            timeOutIndex = timeOutIndex + 1
            Log.i(TAG, "timeOutIndex :"+timeOutIndex + "   isTimeOut:"+isTimeOut)
        }

        Thread.sleep(1000)

        callback.invoke(_responseBuffer,isTimeOut)
    }

    fun sendCMD_CONNECT( callback: ((ByteArray?, Boolean, isTimeout:Boolean) -> Unit)) {

        ISPManager.packetNumber = (0x00000001).toUInt()

        val cmd = ISPCommands.CMD_CONNECT
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        var readBufferStrring = HEXTool.toHexString(sendBuffer)
        var display = HEXTool.toDisPlayString(readBufferStrring)
        Log.i(TAG, "write   CMD:"+display)
        var timeOutIndex = 0
        var isTimeOut = false
        _responseBuffer = byteArrayOf()

        SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer)
        while (_responseBuffer.isEmpty()){
            Thread.sleep(300)
            SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer)

            if(timeOutIndex > 60){
                callback.invoke(_responseBuffer,false,true)
                isTimeOut = true
                return
            }
            timeOutIndex = timeOutIndex + 1
            Log.i(TAG, "timeOutIndex :"+timeOutIndex + "   isTimeOut:"+isTimeOut)
        }

        Thread.sleep(1000)

        val isChecksum = ISPManager.isChecksum_PackNo(sendBuffer,_responseBuffer)
        callback.invoke(_responseBuffer,isChecksum,isTimeOut)
    }

    fun sendCMD_GET_DEVICEID( callback: ((ByteArray?, Boolean) -> Unit)) {

        val cmd = ISPCommands.CMD_GET_DEVICEID
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(_responseBuffer,isChecksum)
    }

    fun sendCMD_UPDATE_BIN(cmd: ISPCommands, sendByteArray:ByteArray, startAddress:UInt, callback: ((ByteArray?, Int) -> Unit)) {

        if(cmd != ISPCommands.CMD_UPDATE_APROM && cmd != ISPCommands.CMD_UPDATE_DATAFLASH){
            return
        }

        var firstData = byteArrayOf()//第一個cmd 為 48 byte
        for (i in 0..47){
            firstData = firstData + sendByteArray[i]
        }
        var remainDataList: List<ByteArray> = listOf()
        val remainData = sendByteArray.copyOfRange(48,sendByteArray.lastIndex+1)//第一個cmd 為 56 byte
//        val remainData = ByteArray(sendByteArray.size - 48)//第一個cmd 為 56 byte
        var index = 0
        var dataArray = byteArrayOf()
        for (byte in remainData){
            dataArray = dataArray + byte
            index = index + 1

            if(index == 56){
                index = 0
                remainDataList = remainDataList + dataArray.clone()
                dataArray = byteArrayOf()
            }
        }
        if(dataArray.isNotEmpty()){
            //還有剩
            for(i in dataArray.size+1..56){
                dataArray = dataArray + 0x00
            }

            if(dataArray.size == 56){
                remainDataList = remainDataList + dataArray.clone()
            }

        }
        Log.i("ISPManager", "CMD_UPDATE   CMD:"+cmd.toString()+ "  startAddress:"+startAddress+"  size:"+sendByteArray.size+"  allPackNum:"+dataArray.size+1)
        var sendBuffer = ISPCommandTool.toUpdataBin_CMD(cmd,
            ISPManager.packetNumber, startAddress , sendByteArray.size , firstData , true)

        var readBuffer = this.write(sendBuffer)

        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(readBuffer, 0) //5% 起跳

        if(isChecksum != true){
            callback.invoke(readBuffer, -1)
            return
        }

        for (i in 0..remainDataList.size-1){

            sendBuffer = ISPCommandTool.toUpdataBin_CMD(cmd,
                ISPManager.packetNumber, startAddress , sendByteArray.size , remainDataList[i] , false)
            readBuffer = this.write(sendBuffer)
            isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

            if(isChecksum != true){
                callback.invoke(readBuffer, -1)
                return
            }

            callback.invoke(readBuffer, (i.toDouble() / remainDataList.size * 100).toInt())
        }
        callback.invoke(readBuffer, 100)
    }


    fun sendCMD_ERASE_ALL( callback: ((ByteArray?, Boolean) -> Unit)) {

        val cmd = ISPCommands.CMD_ERASE_ALL
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)

        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(readBuffer, isChecksum)
    }

    fun sendCMD_READ_CONFIG( callback: ((ByteArray?) -> Unit)) {

        val cmd = ISPCommands.CMD_READ_CONFIG
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(readBuffer)
    }

    fun sendCMD_GET_FWVER( callback: ((ByteArray?, Boolean) -> Unit)) {

        val cmd = ISPCommands.CMD_GET_FWVER
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(readBuffer, isChecksum)
    }

    fun sendCMD_RUN_APROM( callback: ((Boolean) -> Unit)) {

        val cmd = ISPCommands.CMD_RUN_APROM
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        thread { SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer) }
        thread { SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer) }

        callback.invoke(true)
    }

    fun sendCMD_UPDATE_CONFIG( config0: UInt, config1: UInt, config2: UInt, config3: UInt, callback: ((ByteArray?) -> Unit)) {


        //config＿1  先寫死
        val cmd = ISPCommands.CMD_UPDATE_CONFIG
        val sendBuffer = ISPCommandTool.toUpdataCongigeCMD(config0, config1, config2,config3,
            ISPManager.packetNumber
        )
        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)

        callback.invoke(readBuffer)


    }

    fun sendCMD_SYNC_PACKNO( callback: ((ByteArray?) -> Unit)) {

        val cmd = ISPCommands.CMD_SYNC_PACKNO
        val sendBuffer = ISPCommandTool.toCMD(cmd, ISPManager.packetNumber)
        val readBuffer = this.write(sendBuffer)
        var isChecksum = ISPManager.isChecksum_PackNo(sendBuffer, readBuffer)
    }

    private fun write(sendBuffer: ByteArray):ByteArray{


        this._responseBuffer = byteArrayOf()
        thread { SocketManager.funTCPClientSend(_ispTCPClient!!,sendBuffer) }

        var readBufferStrring = HEXTool.toHexString(sendBuffer)
        var display = HEXTool.toDisPlayString(readBufferStrring)
        Log.i(TAG, "write   CMD:"+display)

        while (this._responseBuffer.size < 64){
            Thread.sleep(10)
        }
        return this._responseBuffer
    }
}