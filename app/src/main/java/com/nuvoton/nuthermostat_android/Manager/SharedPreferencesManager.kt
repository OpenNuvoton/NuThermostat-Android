/*
 * Copyright 2026 Nuvoton Technology Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
