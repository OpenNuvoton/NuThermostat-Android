package com.nuvoton.nuthermostat_android.Manager

import android.content.ContentValues.TAG
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.nuvoton.nuthermostat_android.Util.Log
import java.net.InetAddress
import java.net.URL

object NSDManager {

    private var nsdManager: NsdManager? = null
//    private var SERVICE_TYPE = "_services._dns-sd._udp"
    private var SERVICE_TYPE = "_Nuvoton._tcp"
    private var mServiceName = ""
    private val DISCOVERY_TIMEOUT_MS = 15000 // 15秒
    private lateinit var discoveryListener: NsdManager.DiscoveryListener
    private lateinit var serviceInfoCallback: ((NsdName:String,NsdIP:String) -> Unit)


//    dnsName:String,port:Int,host:InetAddress
    public fun  discoveryNDS(context: Context,callback: ((NsdName:String,NsdIP:String) -> Unit)){
    Log.e("NSDManager", "discoveryNDS")
        if(nsdManager == null){
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager?
        }
        serviceInfoCallback = callback

        discoverService()
    }

    private fun discoverService() {
//        // 先停止服务发现
//        nsdManager?.stopServiceDiscovery(discoveryListener)

        // 开始服务发现
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopServiceDiscovery() {
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service discovery: ${e.message}")
        }

//        serviceList.clear()
    }

    // Instantiate a new DiscoveryListener
    init {
        discoveryListener = object : NsdManager.DiscoveryListener {

            // Called as soon as service discovery begins.
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it.
                Log.d(TAG, "Service discovery success$service")

                try {
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                resolveService(service)
//                nsdManager!!.resolveService(service, resolveListener)

            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "Service service lost: $service")
//                discoverService()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Service Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Service Discovery failed: Error code:$errorCode")
                try {
                    nsdManager!!.stopServiceDiscovery(this)
                } catch (e: IllegalArgumentException) {
                    // 監聽器未註冊，忽略錯誤
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Service Discovery failed: Error code:$errorCode")
                nsdManager!!.stopServiceDiscovery(this)
            }
        }
    }


    fun resolveService(service: NsdServiceInfo) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // 处理解析失败
                Log.e(TAG, "Service Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // 处理服务解析完成
                Log.e(TAG, "Service Resolve Succeeded. $serviceInfo")
                val mService = serviceInfo
                val port: Int = serviceInfo.port
                val host: InetAddress = serviceInfo.host
                val name = serviceInfo.host.hostName
                val canonicalHostName = serviceInfo.host.canonicalHostName
                val hostName = host.hostName

                Log.d(TAG, "serviceInfo：${serviceInfo.toString()}")
                Log.d(TAG, "service Info  port：$port   host:$host  name:$name    canonicalHostName:$canonicalHostName   hostName:$hostName")

                serviceInfoCallback.invoke(name,host.hostAddress)
            }
        }
        nsdManager?.resolveService(service, listener)
    }

    private val resolveListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Service Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.e(TAG, "Service Resolve Succeeded. $serviceInfo")

//            if (serviceInfo.serviceName == mServiceName) {
//                Log.d(TAG, "Same IP.")
//                return
//            }
            val mService = serviceInfo
            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host
            val name = serviceInfo.host.hostName
            val canonicalHostName = serviceInfo.host.canonicalHostName


            Log.d(TAG, "service Info  port：$port   host:$host  name:$name    canonicalHostName:$canonicalHostName")
        }
    }
}