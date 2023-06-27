package com.nuvoton.nuisptool_android.Util

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import android.view.LayoutInflater
import com.google.android.material.textfield.TextInputEditText
import com.nuvoton.nuthermostat_android.R

object DialogTool {

    private var _progressDialog: ProgressDialog? = null
    private var _alertDialog : AlertDialog? = null
    private var _isTimeOut = false
    public val HAS_LOADING = false

    fun showAlertDialog(
        context: Context,
        title:String,
        message: String,
        isOkEnable: Boolean,
        isCancelEnable: Boolean,
        callback:((isClickOk:Boolean, isClickNo:Boolean) -> Unit)?) {

        this.dismissDialog()

        val builder = AlertDialog.Builder(context)
        _alertDialog = builder.create()
        _alertDialog!!.setTitle(title)
        _alertDialog!!.setMessage(message)
        if (isOkEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_POSITIVE,"ok",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(true,false)
            })
        }
        if (isCancelEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_NEGATIVE,"Cancel",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(false,true)
            })
        }

        _alertDialog!!.show()
    }

    fun showAlertDialog(context: Context,message: String,isOkEnable: Boolean,isCancelEnable: Boolean,callback:((isClickOk:Boolean,isClickNo:Boolean) -> Unit)?) {

        this.dismissDialog()

        val builder = AlertDialog.Builder(context)
        _alertDialog = builder.create()

        _alertDialog!!.setMessage(message)
        if (isOkEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_POSITIVE,"ok",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(true,false)
            })
        }
        if (isCancelEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_NEGATIVE,"Cancel",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(false,true)
            })
        }

        _alertDialog!!.show()
    }

    fun showInputAlertDialog(context: Context,title: String,message: String,text1: String,callback:((ssid:String,pw:String) -> Unit)?) {

        this.dismissDialog()

        val item = LayoutInflater.from(context).inflate(R.layout.dialog_layout_edittext, null)
        val SSID_Text = item.findViewById(R.id.InputSSID_Text) as TextInputEditText
        SSID_Text.setText(text1.replace("",""))

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(item)
            .setNegativeButton(R.string.next) { _, _ ->
                val SSID_Text = item.findViewById(R.id.InputSSID_Text) as TextInputEditText
                val PW_Text = item.findViewById(R.id.InputPW_Text) as TextInputEditText
                val ssid = SSID_Text.text.toString()
                val pw = PW_Text.text.toString()
                if (TextUtils.isEmpty(ssid)||TextUtils.isEmpty(pw)) {
                    if(callback != null) {
                        callback.invoke("","")
                    }
                } else {
                    if(callback != null) {
                        callback.invoke(ssid,pw)
                    }
                }
            }
            .show()
    }

    fun showProgressDialog(context: Context,title:String?,message: String,isHorizontalStyle:Boolean) {

        this.dismissDialog()

        _progressDialog = ProgressDialog(context)
        if(title!= null) {
            _progressDialog!!.setTitle(title)
        }
        _progressDialog!!.setMessage(message)
        if(isHorizontalStyle){_progressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);}
        _progressDialog!!.setProgress(0);
        _progressDialog!!.setCancelable(false);
        _progressDialog!!.show()
    }

    fun upDataProgressDialog(progress:Int) {
        if(_progressDialog == null){
            return
        }
        _progressDialog!!.setProgress(progress);
    }


    fun dismissDialog(){

        _isTimeOut = false

        if(_progressDialog != null ){
            _progressDialog!!.dismiss()
        }

        if(_alertDialog != null ){
            _alertDialog!!.dismiss()
        }
    }
}