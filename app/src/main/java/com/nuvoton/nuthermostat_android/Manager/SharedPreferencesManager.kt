import android.content.Context
import com.google.gson.Gson
import com.nuvoton.nuthermostat_android.Manager.ItemData

object SharedPreferencesManager {

    private const val SHARED_PREF_NAME = "my_shared_pref"
    private const val SSID_LIST_KEY = "ssid_list_key"

    fun saveSSIDList(context: Context, ssidList: ArrayList<ItemData>) {
        val gson = Gson()
        val ssidListJson = gson.toJson(ssidList)
        val sharedPref = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(SSID_LIST_KEY, ssidListJson)
            apply()
        }
    }

    fun getSSIDList(context: Context): ArrayList<ItemData> {
        val sharedPref = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
        val ssidListJson = sharedPref.getString(SSID_LIST_KEY, "")
        return if (ssidListJson.isNullOrEmpty()) {
            ArrayList()
        } else {
            val gson = Gson()
            gson.fromJson(ssidListJson, Array<ItemData>::class.java).toCollection(ArrayList())
        }
    }

    fun removeSSIDListItem(context: Context, ssidItem: ItemData) {
        val sharedPref = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
        val ssidListJson = sharedPref.getString(SSID_LIST_KEY, "")
        if (!ssidListJson.isNullOrEmpty()) {
            val gson = Gson()
            val ssidList = gson.fromJson(ssidListJson, Array<ItemData>::class.java).toMutableList()
            val filteredList = ssidList.filter { it.ssid != ssidItem.ssid }.toCollection(ArrayList())
            saveSSIDList(context, filteredList)
        }
    }

}
