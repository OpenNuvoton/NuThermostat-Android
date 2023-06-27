package com.nuvoton.nuthermostat_android.Manager

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.nuvoton.nuisptool_android.Util.HEXTool
import com.nuvoton.nuthermostat_android.CMDManager
import com.nuvoton.nuthermostat_android.Util.Log
import java.io.*
import java.net.*
import java.util.*

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)

// 資料類別，用來儲存每個項目的資料
data class ItemData(val ssid: String, var ip:String, var isConnect:Boolean = false)

object SocketManager {
    // 創建 HashMap
    private val tcpClientMap = HashMap<String, Socket?>()
    // 使用 SSID 從 TCP_CLIENT_MAP 中獲取 Socket
    fun getSocketForSSID(ssid: String): Socket? {
        return tcpClientMap[ssid]
    }
    // 將 Socket 存入 TCP_CLIENT_MAP 中，使用 SSID 作為鍵
    fun setSocketForSSID(ssid: String, socket: Socket?) {
        tcpClientMap[ssid] = socket
    }

//    private var tcpClient = Socket()
    private val encodingFormat = "GBK"
    private var tcpClientConnectStatus = false
    private var tcpClientTargetServerIP = "192.168.4.1"
    private var tcpClientTargetServerPort = 520
    private var tcpClientOutputStream: OutputStream? = null
    private var tcpClientInputStreamReader: InputStreamReader? = null
    private val tcpClientReceiveBuffer = StringBuffer()

    fun setIsOnlineListener(callbacks: (Boolean) -> Unit) {
        CMDManager._isOnlineListener = callbacks
    }

//    public fun newTcpClient(): Socket {
//        val tcpClient = Socket()
//        return tcpClient
//    }

//    //客户端连接
//    //需要子线程
public fun funTCPClientConnect(address:String,port:Int,timeout:Int,callback: (( Socket?, Boolean, Exception?) -> Unit)) {
    if (address.isEmpty()) {
        Log.e("目标服务端IP不能为空,否则无法连接", "")
        return
    }
    var maxRetries = 2
    var retries = 0
    var connected = false
    var lastException: Exception? = null
    while (retries < maxRetries && !connected) {
        try {
            //一定要注意，每次连接必须是一个新的Socket对象，否则如果在其他地方关闭了socket对象，那么就无法
            //继续连接了，因为默认对象已经关闭了
            val tcpClient = Socket()
            tcpClient.connect(InetSocketAddress(address, port), timeout)

            //发送心跳包
            tcpClient.keepAlive = true
            //注意这里，不同的电脑PC端可能用到编码方式不同，通常会使用GBK格式而不是UTF-8格式
            val printWriter = PrintWriter(
                OutputStreamWriter(tcpClient.getOutputStream(), encodingFormat),
                true
            )
            //注意
            // 将缓冲区的数据强制输出，用于清空缓冲区，若直接调用close()方法，则可能会丢失缓冲区的数据。所以通俗来讲它起到的是刷新的作用。
            //printWriter.flush();
            // 用于关闭数据流
            ///printWriter.close();
            //printWriter.write("ssid:NT_ZY_BUFFALO pwd:12345678")
            printWriter.flush()
            tcpClientConnectStatus = true

            Log.e("连接服务端成功", "TCPClient"+address)
            Log.e("开启客户端接收", "TCPClient"+address)

            callback.invoke(tcpClient, true, null)
            connected = true
        } catch (e: Exception) {
            lastException = e
            when (e) {
                is SocketTimeoutException -> {
                    Log.e("SocketManager", "连接超时"+address)
                    e.printStackTrace()
                }
                is NoRouteToHostException -> {
                    Log.e("SocketManager", "该地址不存在"+address)
                    e.printStackTrace()
                }
                is ConnectException -> {
                    Log.e("SocketManager", "连接异常或被拒绝"+address)
                    e.printStackTrace()
                }
                else -> {
                    e.printStackTrace()
                    Log.e("SocketManager", "连接结束"+address)
                }
            }
            retries++
            if (retries < maxRetries) {
                Thread.sleep(3000) //等待一秒再重試
            } else {
                callback.invoke(null, false, e)
            }
        }
    }
}

    public fun funTCPClientClose(tcpClient:Socket) {
        Log.e("SocketManager","funTCPClientClose 中斷連接")
        tcpClientConnectStatus = false
        tcpClientInputStreamReader?.close()
        tcpClientOutputStream?.close()
        tcpClient?.close()

    }
    //客户端发送
    //需要子线程
    public fun funTCPClientSend(tcpClient:Socket ,msg: ByteArray) {
        if (msg.isNotEmpty() && tcpClientConnectStatus) {
            //这里要注意，只要曾经连接过，isConnected便一直返回true，无论现在是否正在连接
            if (tcpClient.isConnected) {
                try {
                    tcpClient.getOutputStream().write(msg)
                    Log.e("信息发送成功","信息发送成功"+ HEXTool.toHexString(msg))

                } catch (e: IOException) {
                    Log.e("信息发送失败", "信息发送失败"+HEXTool.toHexString(msg))
                    e.printStackTrace()
                    tcpClientInputStreamReader?.close()
                    tcpClientOutputStream?.close()
                    tcpClient.close()
                }
            }
        }
    }

    //客户端接收的消息
    //添加子线程
    public fun funTCPClientReceive(tcpClient:Socket ,callback: ((Socket,ByteArray?) -> Unit)) {
        Log.e("开启客户端接收成功", "TCPClient")
        while (tcpClientConnectStatus) {
            if (tcpClient.isConnected) {

                try {
                    //將InputStream轉換為Byte
                    val inputStream = tcpClient!!.getInputStream()
                    val reader =BufferedReader(InputStreamReader(inputStream))
                    val bao = ByteArrayOutputStream()
                    val buff = ByteArray(64)
                    var bytesRead = inputStream.read(buff)

                    Log.e("bytesRead", "bytesRead:"+bytesRead +"  data:"+buff)

                    while(bytesRead != -1){
                        callback.invoke(tcpClient,buff)
                        bytesRead = inputStream.read(buff)
                    }

                }catch (e: Exception) {
                    Log.e("开启客户端接收失败", "Exception:"+e)
                    tcpClientInputStreamReader?.close()
                    tcpClientOutputStream?.close()
                    tcpClient.close()
                    CMDManager._isOnlineListener?.invoke(false)
                    break
                }


            } else {
                Log.e("开启客户端接收失败", "TCPClient")
                tcpClientInputStreamReader?.close()
                tcpClientOutputStream?.close()
                tcpClient.close()
                CMDManager._isOnlineListener?.invoke(false)
                break
            }
        }
    }

    //获取设备局域网IP,没开wifi的情况下获取的会是内网ip
    public fun funGetLocalAddress(context: Context): String {
        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        val ssid = wifiInfo.ssid
        val localIP =
            (ipAddress and 0xff).toString() + "." + (ipAddress shr 8 and 0xff) + "." + (ipAddress shr 16 and 0xff) + "." + (ipAddress shr 24 and 0xff)
        Log.e("localIP", localIP +"   SSID:"+ssid.toString())
        return localIP
    }
    public fun funGetSSID(context: Context): String {
        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        val ssid = wifiInfo.ssid.replace("\"", "")
        val localIP =
            (ipAddress and 0xff).toString() + "." + (ipAddress shr 8 and 0xff) + "." + (ipAddress shr 16 and 0xff) + "." + (ipAddress shr 24 and 0xff)
        Log.e("localIP", localIP +"   SSID:"+ssid.toString())
        return ssid
    }
}