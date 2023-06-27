package com.nuvoton.nuthermostat_android

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.nuvoton.nuisptool_android.Util.DialogTool
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import java.util.*
import kotlin.concurrent.thread

private lateinit var _codeScanner :CodeScanner

class QRCodeActivity : AppCompatActivity() {

    private var _tempSSID_APP:String = ""
    private var _tempSSID_Device:String = ""
    private var _tempIP_Device:String = ""

    private var _set_SSID:String = ""
    private var _setPWD:String = ""
    private var variable = false
    private var _context :Activity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)

        _context = this

        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA),9527)
        }else{
            StartSacnning()
        }
    }

    private fun StartSacnning(){
        val scannerView: CodeScannerView = findViewById(R.id.scanner_view)
        _codeScanner = CodeScanner(this,scannerView)
        _codeScanner.camera = CodeScanner.CAMERA_BACK
        _codeScanner.formats = CodeScanner.ALL_FORMATS

        _codeScanner.autoFocusMode = AutoFocusMode.SAFE
        _codeScanner.scanMode = ScanMode.SINGLE
        _codeScanner.isAutoFocusEnabled = true
        _codeScanner.isFlashEnabled = false

        _codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread{
//                Toast.makeText(this,"QR:${it.text}",Toast.LENGTH_SHORT).show()

                val s = it.text.split(",")
                if(s[0] != "Nuvoton"){
                    Toast.makeText(this,getString(R.string.Not_Nuvoton_QR_Code),Toast.LENGTH_SHORT).show()
                    _codeScanner.startPreview()
                    return@runOnUiThread
                }

                _tempSSID_APP = SocketManager.funGetSSID(this)
                _tempSSID_Device = s[1]
                DialogTool.showInputAlertDialog(this,getString(R.string.please_set_wifi_title),getString(R.string.please_set_wifi)+_tempSSID_Device,_tempSSID_APP ,callback = { ssid , pw ->
                    if(ssid == "" || pw == ""){
                        DialogTool.showAlertDialog(this,"Error","SSID or PWD cannot be empty.",false,true,null)
                        return@showInputAlertDialog
                    }
                    _set_SSID = ssid
                    _setPWD = pw
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                    startActivityForResult(panelIntent,789)
                })


            }
        }

        _codeScanner.errorCallback = ErrorCallback {
            runOnUiThread{
                Toast.makeText(this,"error:${it.message}",Toast.LENGTH_SHORT).show()
            }
        }

        scannerView.setOnClickListener {
            _codeScanner.startPreview()
        }

    }

    /**
     * 接收要求權限後廣播
     */
    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
            StartSacnning()
        }else{
            Toast.makeText(this,"Camera Permissions denied",Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 設定WiFi後回來
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 789){
            Log.i("onActivityResult","ACTION_WIFI back")

            DialogTool.showProgressDialog(this,getString(R.string.setting),getString(R.string.plwaseWaite),false)

            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    Log.i("Timer","isConnect = "+variable)
                    // 在這裡執行要觸發的事件
                    if(variable != true){
                        runOnUiThread {  DialogTool.showAlertDialog(_context as QRCodeActivity,getString(R.string.setting),getString(R.string.connect_error),true,false,null) }
                        return
                    }
                }
            }, 30000)

            thread {
                SocketManager.funTCPClientConnect("192.168.4.1",520,5000, callback = { tcpClient, isConnect, error->
                    Log.i("onActivityResult","isConnect = "+isConnect)

                    if(isConnect != true){
                        runOnUiThread {  DialogTool.showAlertDialog(this,getString(R.string.setting),getString(R.string.connect_error),true,false,null) }
                        return@funTCPClientConnect
                    }

                    //todo SET SSID PASSWORD
                    CMDManager.initMainTCPClient(tcpClient!!)
                    CMDManager.sendCMD_SET_SSID(_set_SSID, callback = { readBF , isTrue ->
                        Log.i("onActivityResult","sendCMD_SET_SSID  readBF="+readBF +"    isTrue:"+isTrue)
                        if(isConnect != true){
                            runOnUiThread {  DialogTool.showAlertDialog(this,getString(R.string.setting),getString(R.string.connect_error),true,false,null) }
                            return@sendCMD_SET_SSID
                        }
                        Thread.sleep(500)

                        CMDManager.sendCMD_SET_PASSWORD(_setPWD, callback = { readBF, isTrue ->
                            Log.i("onActivityResult","sendCMD_SET_PASSWORD  readBF="+readBF +"    isTrue:"+isTrue)
                            if(isConnect != true){
                                runOnUiThread {  DialogTool.showAlertDialog(this,getString(R.string.setting),getString(R.string.connect_error),true,false,null) }
                                return@sendCMD_SET_PASSWORD
                            }
                        })
                    })
                })
            }

        }

        if(requestCode == 666){

            DialogTool.showProgressDialog(this,getString(R.string.setting),getString(R.string.plwaseWaite),false)

            if(_tempIP_Device != ""){
                val isPing =pingWithRetry(_tempIP_Device,3)
                Log.i(TAG,"isPing  "+isPing )

                if(!isPing){
                    runOnUiThread {
                        DialogTool.showAlertDialog(this,
                            getString(R.string.please_set_wifi_title),
                            getString(R.string.please_set_wifi_toHome),
                            true,
                            false
                        ) { isOk, isNo ->
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                            startActivityForResult(panelIntent, 666)
                        }
                    }
                }else{
                    thread {

                            Thread.sleep(5000)
                        runOnUiThread {
                            DialogTool.dismissDialog()
                        }
                            val intent = Intent().apply {
                                this.putExtra("WiFi_DONE_DEVICE_SSID", _tempSSID_Device)
                                this.putExtra("WiFi_DONE_DEVICE_IP", _tempIP_Device)
                            }
                            setResult(RESULT_OK, intent)
                            finish()

                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(::_codeScanner.isInitialized){
            _codeScanner?.startPreview()
        }

        CMDManager.setStationModeSuccessListener { istrue, ip ->
            Log.i(TAG,"setStationModeSuccessListener  "+istrue +"  ,ip:"+ip)
            _tempIP_Device = ip
            variable = true

            thread {
                Thread.sleep(3000)
                val isPing =pingWithRetry(ip,3)
                Log.i(TAG,"isPing  "+isPing )

                if(istrue && !isPing){
                    runOnUiThread {
                        DialogTool.showAlertDialog(this,getString(R.string.please_set_wifi_title),getString(R.string.please_set_wifi_toHome),
                        true,false
                        ) { isOk, isNo ->
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                            startActivityForResult(panelIntent, 666)
                        }
                    }
                    return@thread
                }

                if(istrue && isPing){
                    thread {
                        if(istrue == true){
                            Thread.sleep(20000)
                            runOnUiThread {
                                DialogTool.dismissDialog()
                            }
                            val intent = Intent().apply {
                                this.putExtra("WiFi_DONE_DEVICE_SSID", _tempSSID_Device)
                                this.putExtra("WiFi_DONE_DEVICE_IP", ip)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    }
                    return@thread
                }

                if(!istrue || !isPing){
                    runOnUiThread {
                    DialogTool.showAlertDialog(this,getString(R.string.error),getString(R.string.please_try_again),
                        true,false, null)
                    }
                    return@thread
                }
            }

        }
    }

    override fun onPause() {
        super.onPause()
        if(::_codeScanner.isInitialized){
            _codeScanner?.releaseResources()
        }
    }

    fun checkVariableAfterDelay(variable: Boolean): Boolean {
        var result = false

        thread(start = true) {
            Thread.sleep(30000)
            if (variable) {
                result = true
            }
        }

        while (!result) {
            Thread.sleep(100)
        }

        return result
    }

    private fun ping(host: String): Boolean {
        val runtime = Runtime.getRuntime()
        val process = runtime.exec("/system/bin/ping -c 1 $host")
        try {
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                return true
            } else {
                return false
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return false
        } finally {
            process.destroy()
        }
    }

    fun pingWithRetry(host: String, retryCount: Int): Boolean {
        var retry = retryCount
        while (retry > 0) {
            if (ping(host)) {
                return true
            }
            retry--
        }
        return false
    }

}