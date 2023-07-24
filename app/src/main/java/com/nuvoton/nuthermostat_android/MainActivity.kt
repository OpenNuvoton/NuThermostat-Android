package com.nuvoton.nuthermostat_android

import SharedPreferencesManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nuvoton.nuthermostat_android.Manager.ItemData
import com.nuvoton.nuthermostat_android.Manager.NSDManager
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import com.nuvoton.nuthermostat_android.Util.PermissionManager
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private lateinit var _addDevice_Button: ImageButton
    private lateinit var _mListView: ListView
    private var _adapter: ArrayAdapter<*>? = null
    companion object {
        var SSID_LIST = ArrayList<ItemData>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //讀取
        SSID_LIST = SharedPreferencesManager.getSSIDList(this)
        val num = BuildConfig.VERSION_NAME
        val verNum = findViewById<View>(R.id.verNum) as TextView
        verNum.text = "ver "+num
        _addDevice_Button = findViewById<View>(R.id.add_device_button) as ImageButton
        _addDevice_Button!!.setOnClickListener(onClickAddDeviceButton)

        _mListView = findViewById<ListView>(R.id.device_listview)

        val adapter = MyAdapter(this, SSID_LIST)
        _adapter = adapter
        _mListView.adapter = _adapter
        _adapter!!.notifyDataSetChanged()



        this.setPermission()
        finderNsdService()
    }

    override fun onResume() {
        super.onResume()

//        finderNsdService()

        _adapter = MyAdapter(this, SSID_LIST)
        _mListView.adapter = _adapter
        _adapter!!.notifyDataSetChanged()

    }

    private fun finderNsdService(){
        Log.d("MainActivity", "finderNsdService")
        val wifiManager = this.getSystemService(WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()

        try {
            NSDManager.discoveryNDS(this,callback = { name, ip ->
                var isHaveSameIP = false
//                i in 0..array.count()-1
                for (i in 0..SSID_LIST.size-1){
                    if(ip == SSID_LIST[i].ip){
                        isHaveSameIP = true
                        break
                    }

//                    if(name.indexOf(SSID_LIST[i].ssid)>0){
//                        SSID_LIST[i].ip = ip
//                    }
                }

                if(isHaveSameIP ==  true){
                    return@discoveryNDS
                }
                thread {
                    SocketManager.funTCPClientConnect(ip,520,3000, callback = { socket ,isTrue,error ->
                        if(isTrue == false){
                            return@funTCPClientConnect
                        }
                        CMDManager.sendCMD_GET_UUID { bytes, b ->
                            val asciiStringBuilder = StringBuilder()

                            for (byte in bytes!!) {
                                asciiStringBuilder.append(byte.toChar())
                            }

                            for (i in 0..SSID_LIST.size-1){
                                if(asciiStringBuilder.toString() == SSID_LIST[i].ssid){
                                    SSID_LIST[i].ip = ip
                                    break
                                }
                            }

                        }
                    })
                }

                SharedPreferencesManager.saveSSIDList(this,SSID_LIST)

                runOnUiThread {  _adapter!!.notifyDataSetChanged() }
            })
        } catch (e: Exception) {
            // 处理异常的代码块

        }

//        multicastLock.release();
    }

    private val onClickAddDeviceButton = View.OnClickListener {

        val intent = Intent(this, QRCodeActivity::class.java).apply {
//            this.putExtra("JsonName", "set_command")
        }
        resultLauncher.launch(intent)
    }

    /**
     * activity back intent
     */
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (RESULT_OK == activityResult.resultCode) {
            val back_ssid = activityResult.data?.getStringExtra("WiFi_DONE_DEVICE_SSID")
            val back_ip = activityResult.data?.getStringExtra("WiFi_DONE_DEVICE_IP")
            Log.d( "resultLauncher","WiFi_DONE_DEVICE_SSID: $back_ssid")
            if(back_ssid == null && back_ip != ""){
                return@registerForActivityResult
            }

            // 假設要新增的 item 資料
            val newItem = ItemData(back_ssid!!, back_ip!!,false)

            // 檢查 title 是否已經存在於 list 中
            val isTitleExist = SSID_LIST.any { it.ssid == newItem.ssid }

            // 若 title 不存在，則新增 item 到 list
            if (isTitleExist) {
                return@registerForActivityResult
            }

            SSID_LIST.add(newItem)

            _adapter!!.notifyDataSetChanged()
            //儲存
            SharedPreferencesManager.saveSSIDList(this, SSID_LIST)
        }
    }

    private fun setPermission(): Boolean {

        val pm = PermissionManager(this)
        val permissionArray = ArrayList<PermissionManager.PermissionType>()
        permissionArray.add(PermissionManager.PermissionType.GPS)
        permissionArray.add(PermissionManager.PermissionType.WIFI)

        pm.selfPermission("權限", permissionArray)

        return false
    }


}