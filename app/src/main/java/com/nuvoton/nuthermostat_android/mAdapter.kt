package com.nuvoton.nuthermostat_android

import SharedPreferencesManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import com.nuvoton.nuisptool_android.Util.DialogTool
import com.nuvoton.nuthermostat_android.ISP.ISPCmdManager
import com.nuvoton.nuthermostat_android.MainActivity.Companion.SSID_LIST
import com.nuvoton.nuthermostat_android.Manager.ItemData
import com.nuvoton.nuthermostat_android.Manager.SocketManager
import com.nuvoton.nuthermostat_android.Util.Log
import kotlin.concurrent.thread


// 自定義 Adapter
class MyAdapter(context: Context, data: ArrayList<ItemData>) : ArrayAdapter<ItemData>(context, 0, data) {

    val activity = context as Activity
    val data = data

    // 儲存 ViewHolder 的資料類別，用來快取畫面元件
    private class ViewHolder {
        var text_device: TextView? = null
        var img_wifi: ImageView? = null
        var bt_delete_device: ImageButton? = null
        var bt_isp: ImageButton? = null
        var bt_item: Button? = null
        var wifi_progressBar : ProgressBar? = null
    }

    // 產生或重複使用畫面元件，並設定元件的值
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 取得或產生 ViewHolder
        var viewHolder: ViewHolder? = convertView?.tag as ViewHolder?
        var convertView: View? = convertView

        if (viewHolder == null) {
            viewHolder = ViewHolder()
            val inflater = LayoutInflater.from(context)
            convertView = inflater.inflate(R.layout.list_item, parent, false)
            viewHolder.text_device = convertView.findViewById(R.id.device_text)
            viewHolder.img_wifi = convertView.findViewById(R.id.wifi_img)
            viewHolder.bt_delete_device = convertView.findViewById(R.id.delete_bt)
            viewHolder.bt_isp = convertView.findViewById(R.id.ISP_bt)
            viewHolder.bt_item = convertView.findViewById(R.id.item_button)
            viewHolder.wifi_progressBar = convertView.findViewById(R.id.wifi_progressBar)
            convertView.tag = viewHolder
        }

        // 取得項目的資料
        val itemData = getItem(position)
        val ssid = itemData?.ssid
        viewHolder.text_device?.text = ssid
        val tcpClient =  SocketManager.getSocketForSSID(SSID_LIST[position].ssid)

        //測試連線
        if(tcpClient != null && tcpClient.isClosed != true && SSID_LIST[position].isConnect == true){
            thread {
                CMDManager.sendCMD_GET_INFO( callback = { readBF, isTrue ->
                    if (isTrue) {
                        activity.runOnUiThread {
                            SSID_LIST[position].isConnect = true
                            viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_connected)
                            viewHolder.img_wifi?.visibility = View.VISIBLE
                            viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                        }

                    } else {
                        activity.runOnUiThread {
                            SSID_LIST[position].isConnect = false
                            Log.d("resultLauncher", "position: $position"  + SSID_LIST[position].isConnect)
//                            viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_onnection_failed)
//                            viewHolder.img_wifi?.visibility = View.VISIBLE
//                            viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                            thread {
                                SocketManager.funTCPClientConnect(
                                    SSID_LIST[position].ip,
                                    520,
                                    3000,
                                    callback = { tcpClient, isConnect, error ->
                                        Log.d("resultLauncher", "funTCPClientConnect: $isConnect"  + SSID_LIST[position].ip)
                                        if (isConnect == true) {

                                            SocketManager.setSocketForSSID(SSID_LIST[position].ssid,tcpClient)
                                            SSID_LIST[position].isConnect = true
                                            Log.d("resultLauncher", "position: $position"  + SSID_LIST[position].isConnect)
                                            activity.runOnUiThread {
                                                viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_connected)
                                                viewHolder.img_wifi?.visibility = View.VISIBLE
                                                viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                                            }
                                        } else {
                                            SSID_LIST[position].isConnect = false

                                            activity.runOnUiThread {
                                                viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_onnection_failed)
                                                viewHolder.img_wifi?.visibility = View.VISIBLE
                                                viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                                            }
                                        }
                                    })
                            }

                        }
                    }
                })
            }
        }else{
            viewHolder.wifi_progressBar?.visibility = View.VISIBLE
            viewHolder.img_wifi?.visibility = View.INVISIBLE
        }

        if (tcpClient == null || SSID_LIST[position].isConnect == false) {

            thread {
                SocketManager.funTCPClientConnect(
                    SSID_LIST[position].ip,
                    520,
                    3000,
                    callback = { tcpClient, isConnect, error ->
                        Log.d("resultLauncher", "funTCPClientConnect: $isConnect"  + SSID_LIST[position].ip)
                        if (isConnect == true) {

                            SocketManager.setSocketForSSID(SSID_LIST[position].ssid,tcpClient)
                            SSID_LIST[position].isConnect = true
                            Log.d("resultLauncher", "position: $position"  + SSID_LIST[position].isConnect)
                            activity.runOnUiThread {
                                viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_connected)
                                viewHolder.img_wifi?.visibility = View.VISIBLE
                                viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                            }
                        } else {
                            SSID_LIST[position].isConnect = false

                            activity.runOnUiThread {
                                viewHolder.img_wifi?.setImageResource(R.drawable.icon_wifi_onnection_failed)
                                viewHolder.img_wifi?.visibility = View.VISIBLE
                                viewHolder.wifi_progressBar?.visibility = View.INVISIBLE
                            }
                        }
                    })
            }

        }

        // 設定 bt_item 的 OnClickListener
        viewHolder.bt_item?.setOnClickListener {
            Log.d("bt_item", "setOnClickListener:"+SSID_LIST[position].isConnect)

            // 在此執行點擊事件的操作
          if( SocketManager.getSocketForSSID(SSID_LIST[position].ssid) != null && SSID_LIST[position].isConnect){
              val tcpClient =  SocketManager.getSocketForSSID(SSID_LIST[position].ssid)
              CMDManager.initMainTCPClient(tcpClient!!)
                // 建立要跳轉的 Intent
              val intent = Intent(context, HomeActivity::class.java)
                // 執行跳轉
              context.startActivity(intent)
          }
        }
        // 設定 bt_delete_device 的 OnClickListener
        viewHolder.bt_delete_device?.setOnClickListener {
            Log.d("bt_delete_device", "setOnClickListener")
            // 在此執行點擊事件的操作
            DialogTool.showAlertDialog(activity,activity.getString(R.string.Delete_title),activity.getString(R.string.Delete_Message),true,true) { isClickOk, isClickNo ->
                if (isClickOk) {
                    SharedPreferencesManager.removeSSIDListItem(activity, SSID_LIST[position])
                    data.removeAt(position)
                    notifyDataSetChanged()
                }
            }
        }

        // 設定 bt_isp 的 OnClickListener
        viewHolder.bt_isp?.setOnClickListener {
            Log.d("bt_isp", "setOnClickListener")
            // 在此執行點擊事件的操作
            DialogTool.showAlertDialog(activity,activity.getString(R.string.ISP_OTA_Title),activity.getString(R.string.ISP_OTA_Message),true,true) { isClickOk, isClickNo ->
                if (isClickOk) {

                    if( SocketManager.getSocketForSSID(SSID_LIST[position].ssid) != null && SSID_LIST[position].isConnect){
                        val tcpClient =  SocketManager.getSocketForSSID(SSID_LIST[position].ssid)
                        ISPCmdManager.initMainTCPClient(tcpClient!!)
                        // 建立要跳轉的 Intent
                        val intent = Intent(context, ISPActivity::class.java)
                        // 執行跳轉
                        context.startActivity(intent)
                    }else{
                        DialogTool.showAlertDialog(activity,activity.getString(R.string.error),activity.getString(R.string.device_not_connect),true,false,null)
                    }

                }
            }
        }

        return convertView!!
    }
}



