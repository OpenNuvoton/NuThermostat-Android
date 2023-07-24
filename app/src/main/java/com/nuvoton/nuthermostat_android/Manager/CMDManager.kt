package com.nuvoton.nuthermostat_android

import com.nuvoton.nuisptool_android.Util.HEXTool
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import java.net.Socket
import kotlin.concurrent.thread

enum class Commands constructor(val value: UInt){
    //value 為 Int
    CMD_SET_SSID            ((0xb0).toUInt()),
    CMD_SET_PASSWORD	    ((0xb1).toUInt()),
    CMD_GET_UUID            ((0xb3).toUInt()),

    CMD_GET_INFO            ((0xa0).toUInt()),
    CMD_SET_POWER           ((0xa1).toUInt()),
    CMD_SET_HOT 	        ((0xa2).toUInt()),
    CMD_SET_TEMPERATURE		((0xa3).toUInt()),
    CMD_SET_DEFOG           ((0xa4).toUInt()),
    CMD_SET_LOCK            ((0xa5).toUInt()),
    CMD_SET_DATE_TIME       ((0xa6).toUInt()),

}

object CMDManager {

    private var _responseBuffer:ByteArray = byteArrayOf()
    private var _tempBuffer:ByteArray = byteArrayOf()
    private var _thesameIndex = 0
    public var _isOnlineListener: ((Boolean) -> Unit)? = null
    private var _isStationModeSuccess: ((Boolean,String) -> Unit)? = null
    private var _notifyDeviceInfoListener: ((isPowerOn:Boolean,isHot:Boolean,setTemperature:Int,localTemperature:Float,isDefog:Boolean,isLock:Boolean) -> Unit)? = null

    private var _mainTCPClient:Socket? = null

    public var _logcatListener: ((String) -> Unit)? = null
    fun setLogcatListener(callbacks: (String) -> Unit) {
        _logcatListener = callbacks
    }

    public fun initMainTCPClient(tcpClient:Socket){
        _mainTCPClient = tcpClient
        thread {
            SocketManager.funTCPClientReceive(tcpClient, ReadListener)
        }
    }

    public fun setStationModeSuccessListener(callback: (isSuccess:Boolean,ip:String) -> Unit){
        _isStationModeSuccess = callback
    }

    public fun setNotifyDeviceInfoListener(callback: (isPowerOn:Boolean,isHot:Boolean,setTemperature:Int,localTemperature:Float,isDefog:Boolean,isLock:Boolean) -> Unit){
        _notifyDeviceInfoListener = callback
    }

    public var ReadListener : ((Socket,ByteArray?) -> Unit) =  { tcpClient , readBf ->

        val it = readBf

        if (it != null) {
            if(it.size<64){
                Log.d("SocketCmdManager", "read Value.size < 64  !!!"  )

                _responseBuffer = _responseBuffer + it
            }
            _responseBuffer = it
        }

        if(_tempBuffer.contentEquals(it)){
            _thesameIndex = _thesameIndex + 1
        }else{
            _thesameIndex = 0
        }

        _tempBuffer = it!!.clone()

        if(_thesameIndex > 5){
//            Log.e("SocketCmdManager","斷線了")
//            SocketManager.funTCPClientClose(tcpClient)
//            if(_isOnlineListener!=null){
//                _isOnlineListener!!.invoke(false)
//            }
        }

        //todo 0xb2 Notify device station mode is success?
        if(_isStationModeSuccess != null && it[0] == 0xb2.toByte()){
            if(it[1] == 0x01.toByte()){
                val ipArray = byteArrayOf(it[2],it[4],it[6],it[8])
                val ip = HEXTool.hexToIp(HEXTool.toHexString(ipArray))

                Log.e("_isStationModeSuccess 成功:", "ip:"+ip)
                _isStationModeSuccess!!.invoke(true,ip)
            }else{
                Log.e("_isStationModeSuccess 失敗:", "")
                _isStationModeSuccess!!.invoke(false,"")
            }

            this.write_noACK(it)
        }

        //todo 0xa0 Notify device info
        if(_notifyDeviceInfoListener != null && it[0] == 0xa0.toByte()){
            val power = ( it[2] == 0x01.toByte() )
            val hot = ( it[3] == 0x01.toByte() )
            val sti = it[4].toInt()
            val stf = it[5].toInt()
            val setTemperature = "$sti".toInt()
            val lti = it[6].toInt()
            val ltf = it[7].toInt()
            val localTemperature = "$lti.$ltf".toFloat()
            val defog = ( it[8] == 0x01.toByte() )
            val lock = ( it[9] == 0x01.toByte() )
//            isPowerOn:Boolean,isHot:Boolean,setTemperature:Float,localTemperature:Float,isDefog:Boolean,isLock:Boolean
            _notifyDeviceInfoListener!!.invoke(power,hot,setTemperature,localTemperature,defog,lock)
        }

        if(it[0] == "41".toByte()){
            _isOnlineListener?.invoke(false)
        }

        Log.e("read :", HEXTool.toHexString(it!!))
        if(_logcatListener!=null){
            _logcatListener!!.invoke("read CMD:"+HEXTool.toHexString(it!!))
        }
    }

    fun sendCMD_GET_UUID(callback: ((ByteArray?, Boolean) -> Unit)){

        if(_mainTCPClient == null){
            callback.invoke(_responseBuffer,false)
            return
        }

        val cmd: Byte = Commands.CMD_GET_UUID.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd

        if(this.write(sendBuffer)  == null){
            callback.invoke(_responseBuffer,false)
            return
        }

        callback.invoke(_responseBuffer,true)
    }

    fun sendCMD_SET_SSID(ssid:String,callback: ((ByteArray?, Boolean) -> Unit)){

        if(_mainTCPClient == null){
            callback.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_SSID.value.toByte()
        val ssidBytes = ssid.toByteArray()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + ssidBytes.size.toByte() + ssidBytes
        var checksum : Byte = 0x00
        for (s in sendBuffer){
            checksum = (checksum + s).toByte()
        }
        sendBuffer = sendBuffer + checksum

        if(this.write(sendBuffer)  == null){
            callback.invoke(_responseBuffer,false)
            return
        }
        callback.invoke(_responseBuffer,true)

    }

    fun sendCMD_SET_PASSWORD(pwd:String,callback: ((ByteArray?, Boolean) -> Unit)){

        if(_mainTCPClient == null){
            callback.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_PASSWORD.value.toByte()
        val dataBytes = pwd.toByteArray()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + dataBytes.size.toByte() + dataBytes
        var checksum : Byte = 0x00
        for (s in sendBuffer){
            checksum = (checksum + s).toByte()
        }
        sendBuffer = sendBuffer + checksum
        if(this.write(sendBuffer)  == null){
            callback.invoke(_responseBuffer,false)
            return
        }
        callback.invoke(_responseBuffer,true)
    }

    fun sendCMD_GET_INFO( callback: ((ByteArray?, Boolean) -> Unit)) {

        if(_mainTCPClient == null){
            callback.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_GET_INFO.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd
        if(this.write(sendBuffer)  == null){
            callback.invoke(_responseBuffer,false)
            return
        }
        callback.invoke(_responseBuffer,true)
    }

    fun sendCMD_GET_INFO(client: Socket, callback: ((ByteArray?, Boolean) -> Unit)) {
        val cmd = Commands.CMD_GET_INFO.value.toByte()
        val sendBuffer = byteArrayOf(cmd)

        try {
            // 寫入資料到伺服器
            client.getOutputStream().write(sendBuffer)

            // 從伺服器讀取回應
            val responseBuffer = ByteArray(64)
            val bytesRead = client.getInputStream().read(responseBuffer)
            if (bytesRead > 0) {
                callback.invoke(responseBuffer.copyOfRange(0, bytesRead), true)
            } else {

            }
        } catch (e: Exception) {
            // 處理任何在讀寫過程中可能發生的錯誤
            e.printStackTrace()
            callback.invoke(null, false)
            return
        }
    }

    fun sendCMD_SET_POWER( setON:Boolean,callback: ((ByteArray?, Boolean) -> Unit)?) {

        if(_mainTCPClient == null){
            callback?.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_POWER.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + 0x01
        if(setON){
            sendBuffer = sendBuffer + 0x01
        }else{
            sendBuffer = sendBuffer + 0x00
        }

        if(this.write(sendBuffer)  == null){
            callback?.invoke(_responseBuffer,false)
            return
        }
        callback?.invoke(_responseBuffer,true)
    }

    fun sendCMD_SET_Hot( setHot:Boolean,callback: ((ByteArray?, Boolean) -> Unit)?) {

        if(_mainTCPClient == null){
            callback?.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_HOT.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + 0x01
        if(setHot){
            sendBuffer = sendBuffer + 0x01
        }else{
            sendBuffer = sendBuffer + 0x00
        }

        if(this.write(sendBuffer)  == null){
            callback?.invoke(_responseBuffer,false)
            return
        }
        callback?.invoke(_responseBuffer,true)
    }

    fun sendCMD_SET_Defog( setDefog:Boolean,callback: ((ByteArray?, Boolean) -> Unit)?) {

        if(_mainTCPClient == null){
            callback?.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_DEFOG.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + 0x01
        if(setDefog){
            sendBuffer = sendBuffer + 0x01
        }else{
            sendBuffer = sendBuffer + 0x00
        }

        if(this.write(sendBuffer)  == null){
            callback?.invoke(_responseBuffer,false)
            return
        }
        callback?.invoke(_responseBuffer,true)
    }

    fun sendCMD_SET_Lock( setLock:Boolean,callback: ((ByteArray?, Boolean) -> Unit)?) {

        if(_mainTCPClient == null){
            callback?.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_LOCK.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + 0x01
        if(setLock){
            sendBuffer = sendBuffer + 0x01
        }else{
            sendBuffer = sendBuffer + 0x00
        }

        if(this.write(sendBuffer)  == null){
            callback?.invoke(_responseBuffer,false)
            return
        }
        callback?.invoke(_responseBuffer,true)
    }

    fun sendCMD_SET_Temperature( temperature:Int,callback: ((ByteArray?, Boolean) -> Unit)?) {

        if(_mainTCPClient == null){
            callback?.invoke(_responseBuffer,false)
            return
        }

        val cmd = Commands.CMD_SET_TEMPERATURE.value.toByte()
        var sendBuffer = byteArrayOf()
        sendBuffer = sendBuffer + cmd + 0x01 + temperature.toByte()

        if(this.write(sendBuffer)  == null){
            callback?.invoke(_responseBuffer,false)
            return
        }
        callback?.invoke(_responseBuffer,true)
    }

    private fun write(sendBuffer: ByteArray): ByteArray? {
        val timeoutMillis: Long = 5000
        this._responseBuffer = byteArrayOf()
        var retryCount = 0 // 重試次數
        var success = false // 是否成功接收到回應

        while (!success && retryCount < 2) { // 最多重試三次
            thread { SocketManager.funTCPClientSend(_mainTCPClient!!, sendBuffer) }

            val startTime = System.currentTimeMillis()
            var bfStrring = HEXTool.toHexString(sendBuffer)
            var display = HEXTool.toDisPlayString(bfStrring)
//            Log.i("SocketCmdManager", "write CMD:$display")

            if(_logcatListener!=null){
                _logcatListener!!.invoke("write CMD:$display")
            }

            while (this._responseBuffer.size < 64) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime > timeoutMillis) {
                    Log.e("SocketCmdManager", "write timeout, retrying...")
                    if(_logcatListener!=null){
                        _logcatListener!!.invoke("write timeout, retrying...")
                    }
                    retryCount++
                    break // 跳出內部 while 循環，進行下一次嘗試
                }
                Thread.sleep(10)
            }

            // 判斷是否成功接收到回應
            if (this._responseBuffer.size >= 64) {
                success = true
            } else {
                this._responseBuffer = byteArrayOf() // 清空緩存，準備下一次嘗試
            }
        }

        if (!success) {
            Log.e("SocketCmdManager", "write failed after $retryCount retries")

            if(_logcatListener!=null){
                _logcatListener!!.invoke("write failed after $retryCount retries")
            }

            _isOnlineListener?.invoke(false)

            return null
        }

        return this._responseBuffer
    }

//    private fun write(sendBuffer: ByteArray): ByteArray? {
//        val timeoutMillis: Long = 10000
//        this._responseBuffer = byteArrayOf()
//        thread { SocketManager.funTCPClientSend(_mainTCPClient!!, sendBuffer) }
//
//        val startTime = System.currentTimeMillis()
//        var bfStrring = HEXTool.toHexString(sendBuffer)
//        var display = HEXTool.toDisPlayString(bfStrring)
//        Log.i("SocketCmdManager", "write CMD:$display")
//
//        if(_logcatListener!=null){
//            _logcatListener!!.invoke("write CMD:$display")
//        }
//
//        while (this._responseBuffer.size < 64) {
//            val elapsedTime = System.currentTimeMillis() - startTime
//            if (elapsedTime > timeoutMillis) {
//                Log.e("SocketCmdManager", "write timeout")
//                Log.e("SocketCmdManager", "write timeout:"+HEXTool.toDisPlayString(HEXTool.toHexString(_responseBuffer)))
//                if(_logcatListener!=null){
//                    _logcatListener!!.invoke("write timeout")
//                }
//                return null
//            }
//            Thread.sleep(10)
//        }
//        return this._responseBuffer
//    }

    private fun write_noACK(sendBuffer: ByteArray){

        thread { SocketManager.funTCPClientSend(_mainTCPClient!!, sendBuffer) }

        var bfStrring = HEXTool.toHexString(sendBuffer)
        var display = HEXTool.toDisPlayString(bfStrring)
        Log.i("SocketCmdManager", "write_noACK CMD:$display")

        if(_logcatListener!=null){
            _logcatListener!!.invoke("write_noACK:$display")
        }

    }
}