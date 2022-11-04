package com.reteno.core.data.remote.model.event

import com.google.gson.annotations.SerializedName

data class EventsDTO(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("externalUserId")
    val externalUserId: String? = null,
    @SerializedName("events")
    val eventList: List<EventDTO>
)