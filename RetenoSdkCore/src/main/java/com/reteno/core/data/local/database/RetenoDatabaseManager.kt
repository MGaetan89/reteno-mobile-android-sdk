package com.reteno.core.data.local.database

import com.reteno.core.data.remote.model.user.UserDTO
import com.reteno.core.model.device.Device

interface RetenoDatabaseManager {

    fun insertDevice(device: Device)
    fun getDeviceEvents(limit: Int? = null): List<Pair<String, Device>>
    fun getDeviceEventsCount(): Long
    fun deleteDeviceEvents(count: Int, ascending: Boolean = true)

    fun insertUser(user: UserDTO)
    fun getUserEvents(limit: Int? = null): List<Pair<String, UserDTO>>
    fun getUserEventsCount(): Long
    fun deleteUserEvents(count: Int, ascending: Boolean = true)
}