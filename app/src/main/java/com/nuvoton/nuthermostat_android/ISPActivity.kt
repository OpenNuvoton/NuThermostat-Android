package com.nuvoton.nuthermostat_android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.google.android.material.textfield.TextInputEditText
import com.nuvoton.nuisptool_android.Util.DialogTool
import com.nuvoton.nuisptool_android.Util.HEXTool
import com.nuvoton.nuisptool_android.Util.HEXTool.to2HexString
import com.nuvoton.nuthermostat_android.ISP.*
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import java.io.File
import java.util.*
import kotlin.concurrent.thread

@RequiresApi(Build.VERSION_CODES.Q)
class ISPActivity : AppCompatActivity() {

    private var TAG = "ISPActivity"
    private lateinit var _burnButton: ImageButton
    private lateinit var _selectButton: ImageButton
    private lateinit var _editText: TextInputEditText
    private lateinit var _progressBar: ProgressBar
    private var apromBinDataText = ""
    private var _apromSize = 0
    private var flishBinDataText = ""
    private var _DataFlashSize = 0

    private var _tempIsISPMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ispactivity)

        _selectButton = findViewById<View>(R.id.select_Button) as ImageButton
        _selectButton!!.setOnClickListener(onClickSelectButton)
        _editText = findViewById<View>(R.id.my_edit_text) as TextInputEditText
        _burnButton = findViewById<View>(R.id.burn_button) as ImageButton
        _burnButton!!.setOnClickListener(onClickBurnButton)
        _progressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        _progressBar.setMax(100); // 設定最大值為100
        _progressBar.setProgress(0); // 設定目前進度為50

        //讀取ＪＳＯＮ檔產生列表
        FileManager.loadChipInfoFile(this)
        FileManager.loadChipPdidFile(this)
        FileManager.getChipInfoByPDID("00F25841")
        _apromSize = FileManager.CHIP_DATA.chipInfo.AP_size.split("*")[0].toInt()
        _DataFlashSize = FileManager.CHIP_DATA.chipInfo.DF_size.split("*")[0].toInt()


    }

    override fun onResume() {
        super.onResume()

//        ＥＳＰ３２測試用

//        thread {
//            SocketManager.funTCPClientConnect("192.168.4.1",520,500, callback = { clint, istrue, e ->
//                Log.i(TAG, "funTCPClientConnect ---- 192.168.4.1 " + istrue)
//                if(istrue){
//                    ISPCmdManager.initMainTCPClient(clint!!)
//                }
//            })
//        }

        if (_tempIsISPMode == true) {
            return
        }

        DialogTool.showProgressDialog(this, "Set ISP Mode", "Loading...", false)
        thread {
            ISPCmdManager.sendCMD_SET_ISP_MODE_ON { bytes, isTimeout ->
                DialogTool.dismissDialog()
                if (isTimeout) {
                    runOnUiThread {
                        DialogTool.showAlertDialog(this, "0xC1 timeout", true, false, null)
                    }
                    _tempIsISPMode = false
                } else {
                    _tempIsISPMode = true
                }
            }
        }
    }

    private val onClickSelectButton = View.OnClickListener {

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"

        val chooser = Intent.createChooser(intent, null)
        resultLauncher_APROM.launch(chooser)

    }

    /**
     * on _button_burn Click Button
     * 開始燒入
     */
    private val onClickBurnButton = View.OnClickListener {


        if (FileManager.APROM_BIN == null || FileManager.APROM_BIN!!.byteArray.isEmpty()) {
            DialogTool.showAlertDialog(this, "APROM Bin isEmpty", true, false, null)
            return@OnClickListener
        }

        if (_tempIsISPMode == false) {
            DialogTool.showAlertDialog(this, "The Device is not in ISP mode.", true, false, null)
            return@OnClickListener
        }

        thread {
            ISPCmdManager.sendCMD_CONNECT { bytes, isChecksum, isTimeout ->
                DialogTool.dismissDialog()

                if (isTimeout == true) {
                    Log.i(TAG, "sendCMD_CONNECT ---- Search Device is time out.")
                    runOnUiThread {
                        DialogTool.showAlertDialog(
                            this,
                            "sendCMD_CONNECT is time out.",
                            true,
                            false,
                            null
                        )

                    }
                    return@sendCMD_CONNECT
                }

                if (isChecksum == false) {
                    Log.i(TAG, "sendCMD_CONNECT ---- isChecksum fail")
                    runOnUiThread {
                        DialogTool.showAlertDialog(this, "Checksum fail", true, false, null)
                    }
                    return@sendCMD_CONNECT
                }

                //燒入開始
                this.brunAPROM()
            }

        }
    }

    fun brunAPROM() {
        //需要照順序 EraseALL> Config bit > APROM > DATAFLASH > Reset Run
        var hasFaile = false
        var sTime: Date = Calendar.getInstance().time//系统现在时间
        var tempAPsize = 0
        var tempFlashsize = 0
        thread {
            //  APROM
            runOnUiThread {
                DialogTool.showProgressDialog(this, "Burn", "APROM is burning ...", true)
            }

            val dataArray = FileManager.APROM_BIN!!.byteArray
            tempAPsize = dataArray.size

            var startAddress = (0x00000000).toUByte().toUInt()

            ISPManager.sendCMD_UPDATE_BIN(ISPCommands.CMD_UPDATE_APROM, dataArray, startAddress, callback = { readArray, progress ->

                Log.i(TAG, "sendCMD_UPDATE_APROM : " + progress + "%")

                runOnUiThread {
                    _progressBar.setProgress(progress); // 設定新的進度
                    DialogTool.upDataProgressDialog(progress)
                    if (progress == 100) {
                        DialogTool.dismissDialog()

                        DialogTool.showAlertDialog(this,"Info",getString(R.string.burn_ok),true,false, callback = { isOk,isNo ->
                            ISPManager.sendCMD_RUN_APROM( callback = {
                                runOnUiThread {
                                    this.finish()
                                }
                            })
                        })
                    }
                    if (progress == -1) {
                        DialogTool.dismissDialog()
                        hasFaile = true
                        DialogTool.showAlertDialog(this, "burn APROM Failed.", true, false, null)
                    }
                }
            })

        }
    }

    private var resultLauncher_APROM = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val myData: Intent? = result.data
            if (myData != null) {
                val uri = myData.data

                if (uri == null) {
                    return@registerForActivityResult
                }

                val bin = FileData()
                bin.uri = uri
                bin.name = FileManager.getNameByUri(uri, this)
                bin.path = uri.path.toString()
                bin.type = "APROM"
                bin.file = File(uri.path)

                if (bin.name.indexOf(".bin") <= -1) {
                    runOnUiThread {
                        DialogTool.showAlertDialog(this, getString(R.string.not_bin), true, false, null)
                    }
                    return@registerForActivityResult
                }

                FileManager.APROM_BIN = bin //存回

                thread {
                    Log.i(TAG, "APROM_BIN PATH:" + bin.path)
                    val inputStream = this.contentResolver.openInputStream(uri)
                    bin.byteArray = inputStream!!.readBytes()

                    if (_apromSize * 1024 < bin.byteArray.size) {
                        runOnUiThread {
                            DialogTool.showAlertDialog(this, getString(R.string.bin_size_over), true, false, null)
                        }
                        return@thread
                    }

                    runOnUiThread {
//                        DialogTool.showProgressDialog(this,"Please Wait","Data Loading ...",false)
//                        _text_message_display.setText("")
                        _editText.setText(bin.name)
                    }

//                    thread {
//                        val tempText = bin.byteArray.to2HexString()
//                        val cArray = tempText.chunked(16+16+16) // 空格16 字元16x2
//                        for (i in 0..cArray.size-1){
//                            runOnUiThread {
//                                _text_message_display.append(HEXTool.toHex16String(i * 16) + "：" + cArray[i] + "\n")
//                            }
//                        }
//
//                        runOnUiThread {
//                            _text_message_display.setMovementMethod(ScrollingMovementMethod())
//                            _checkbox_aprom.setText("APROM："+bin.name)
//                            _checkbox_aprom.isChecked = true
//                            _checkbox_aprom.isEnabled = true
//                            _progressBar.visibility = View.INVISIBLE
//                            DialogTool.dismissDialog()
//                        }
//                    }

                }


            }
        }
    }
}