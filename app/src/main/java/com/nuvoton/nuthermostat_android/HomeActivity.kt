package com.nuvoton.nuthermostat_android

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import com.nuvoton.nuisptool_android.Util.DialogTool
import com.nuvoton.nuisptool_android.Util.DialogTool.HAS_LOADING
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import kotlin.concurrent.thread

class HomeActivity : AppCompatActivity() {

    private lateinit var _power_bt: ImageButton
    private lateinit var _icon_home: ImageView
    private lateinit var _set_Hot_Mode_bt: Button
    private lateinit var _set_Cool_Mode_bt: Button
    private lateinit var _target_temperature_text: TextView
    private lateinit var _current_temperature_text: TextView
    private lateinit var _seekBar: SeekBar
    private lateinit var _defog_switch: Switch
    private lateinit var _lock_switch: Switch
    private lateinit var _infoData: InfoData
    private lateinit var _enableView: ImageView
    private lateinit var _context: Context

    // 資料類別，用來儲存每個項目的資料
    data class InfoData(
        var isPowerOn: Boolean,
        var isHot: Boolean,
        var temperature: Int,
        var localTemperature: Float,
        var isDefog: Boolean,
        var isLock: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        _context = this
        _power_bt = findViewById(R.id.power_bt)
        _power_bt.setOnClickListener(_power_onClickButton)
        _enableView = findViewById(R.id.enableView)
        _icon_home = findViewById(R.id.icon_home)
        _set_Hot_Mode_bt = findViewById(R.id.set_Hot_Mode_bt)
        _set_Hot_Mode_bt.setOnClickListener(_set_Hot_Mode_onClickButton)
        _set_Cool_Mode_bt = findViewById(R.id.set_Cool_Mode_bt)
        _set_Cool_Mode_bt.setOnClickListener(_set_Cool_Mode_onClickButton)
        _target_temperature_text = findViewById(R.id.Target_temperature_text)
        _current_temperature_text = findViewById(R.id.Current_temperature_text)
        _defog_switch = findViewById(R.id.antifrost_switch)
        _defog_switch.setOnCheckedChangeListener(_set_defog_switch_OnCheckedChangeListener)
        _lock_switch = findViewById(R.id.Lock_switch)
        _lock_switch.setOnCheckedChangeListener(_set_Lock_Mode_switch_OnCheckedChangeListener)
        _seekBar = findViewById(R.id.seekBar)
        _seekBar.max = 30
        _seekBar.min = 16
        _seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {/*拖动开始时的逻辑*/
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                /*进度值改变时的逻辑*/
                runOnUiThread {
                    _target_temperature_text.text = progress.toString()
                    _infoData.temperature = progress
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                /*拖动结束时的逻辑*/
                if(HAS_LOADING){
                    runOnUiThread {
                        DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
                    }
                }

                thread {
                    CMDManager.sendCMD_SET_Temperature(seekBar!!.progress, callback = { _, isTrue ->
                        if (isTrue) {
                            upDataUI(_infoData)
                        } else {
                            runOnUiThread {
                                DialogTool.showAlertDialog(
                                    _context,
                                    getString(R.string.error),
                                    getString(R.string.confirm_status),
                                    true,
                                    false
                                ) { isClickOk: Boolean, isClickNo: Boolean ->
                                    finish()
                                }
                            }
                        }
                    })
                }
            }
        })

        CMDManager.setLogcatListener {
//            runOnUiThread { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); }
        }

//        SocketManager.setIsOnlineListener {
//            if(it == true){
//                return@setIsOnlineListener
//            }
//            runOnUiThread {
//                DialogTool.showProgressDialog(this,null,getString(R.string.confirm_status),false)
//            }
//        }

        this.setNotifyDeviceInfoListener()

        runOnUiThread {
            DialogTool.showProgressDialog(this, null, getString(R.string.loading), false)
        }

        thread {
            CMDManager.sendCMD_GET_INFO { bytes, b ->
                if (b == false) {
                    runOnUiThread {
                        DialogTool.showAlertDialog(
                            this,
                            getString(R.string.error),
                            getString(R.string.confirm_status),
                            true,
                            false
                        ) { isClickOk: Boolean, isClickNo: Boolean ->
                            finish()
                        }
                    }
                }
            }
        }

    }

    private fun setNotifyDeviceInfoListener() {
        CMDManager.setNotifyDeviceInfoListener { isPowerOn, isHot, setTemperature, localTemperature, isDefog, isLock ->
            _infoData =
                InfoData(isPowerOn, isHot, setTemperature, localTemperature, isDefog, isLock)
            upDataUI(_infoData)
        }
    }

    fun upDataUI(infoData: InfoData) {
        runOnUiThread {
            DialogTool.dismissDialog()
            if (infoData.isPowerOn == true) {
                _power_bt.setImageResource(R.drawable.icon_power_on)
            } else {
                _power_bt.setImageResource(R.drawable.icon_power_off)
            }
            if (infoData.isHot == true) {
                _icon_home.setImageResource(R.drawable.icon_home_hot)
            } else {
                _icon_home.setImageResource(R.drawable.icon_home_cool)
            }
            _target_temperature_text.text = infoData.temperature.toString()
            _current_temperature_text.text = infoData.localTemperature.toString()
            _defog_switch.setOnCheckedChangeListener(null)
            _defog_switch.isChecked = infoData.isDefog == true
            _defog_switch.setOnCheckedChangeListener(_set_defog_switch_OnCheckedChangeListener)
            _lock_switch.setOnCheckedChangeListener(null)
            _lock_switch.isChecked = infoData.isLock == true
            _lock_switch.setOnCheckedChangeListener(_set_Lock_Mode_switch_OnCheckedChangeListener)
            _seekBar.progress = infoData.temperature

            if (infoData.isPowerOn) {
                _enableView.visibility = View.GONE
            } else {
                _enableView.visibility = View.VISIBLE
            }
        }
    }

    private val _power_onClickButton = View.OnClickListener {
        if(HAS_LOADING){
            runOnUiThread {
                DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
            }
        }
        thread {
            CMDManager.sendCMD_SET_POWER(!_infoData.isPowerOn, callback = { _, isTrue ->
                if (isTrue) {
                    _infoData.isPowerOn = !_infoData.isPowerOn
                    upDataUI(_infoData)
                } else {
                    runOnUiThread {
                        DialogTool.showAlertDialog(
                            this,
                            getString(R.string.error),
                            getString(R.string.confirm_status),
                            true,
                            false
                        ) { isClickOk: Boolean, isClickNo: Boolean ->
                            finish()
                        }
                    }
                }
            })
        }
    }
    private val _set_Hot_Mode_onClickButton = View.OnClickListener {
        if(HAS_LOADING){
            runOnUiThread {
                DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
            }
        }
        thread {
            CMDManager.sendCMD_SET_Hot(true, callback = { _, isTrue ->
                if (isTrue) {
                    _infoData.isHot = true
                    upDataUI(_infoData)
                } else {
                    runOnUiThread {
                        DialogTool.showAlertDialog(
                            this,
                            getString(R.string.error),
                            getString(R.string.confirm_status),
                            true,
                            false
                        ) { isClickOk: Boolean, isClickNo: Boolean ->
                            finish()
                        }
                    }
                }
            })
        }
    }
    private val _set_Cool_Mode_onClickButton = View.OnClickListener {
        if(HAS_LOADING){
            runOnUiThread {
                DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
            }
        }
        thread {
            CMDManager.sendCMD_SET_Hot(false, callback = { _, isTrue ->
                if (isTrue) {
                    _infoData.isHot = false
                    upDataUI(_infoData)
                } else {
                    runOnUiThread {
                        DialogTool.showAlertDialog(
                            this,
                            getString(R.string.error),
                            getString(R.string.confirm_status),
                            true,
                            false
                        ) { isClickOk: Boolean, isClickNo: Boolean ->
                            finish()
                        }
                    }
                }
            })
        }
    }
    private val _set_defog_switch_OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if(HAS_LOADING){
                runOnUiThread {
                    DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
                }
            }
            thread {
                CMDManager.sendCMD_SET_Defog(isChecked, callback = { _, isTrue ->
                    if (isTrue) {
                        _infoData.isDefog = isChecked
                        upDataUI(_infoData)
                    } else {
                        runOnUiThread {
                            DialogTool.showAlertDialog(
                                this,
                                getString(R.string.error),
                                getString(R.string.confirm_status),
                                true,
                                false
                            ) { isClickOk: Boolean, isClickNo: Boolean ->
                                finish()
                            }
                        }
                    }
                })
            }
        }
    private val _set_Lock_Mode_switch_OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if(HAS_LOADING){
                runOnUiThread {
                    DialogTool.showProgressDialog(_context,null, getString(R.string.loading),false)
                }
            }
            thread {
                CMDManager.sendCMD_SET_Lock(isChecked, callback = { _, isTrue ->
                    if (isTrue) {
                        _infoData.isLock = isChecked
                        upDataUI(_infoData)
                    } else {
                        runOnUiThread {
                            DialogTool.showAlertDialog(
                                this,
                                getString(R.string.error),
                                getString(R.string.confirm_status),
                                true,
                                false
                            ) { isClickOk: Boolean, isClickNo: Boolean ->
                                finish()
                            }
                        }
                    }
                })
            }
        }


}