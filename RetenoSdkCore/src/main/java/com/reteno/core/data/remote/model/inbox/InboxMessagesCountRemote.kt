package com.reteno.core.data.remote.model.inbox

import com.google.gson.annotations.SerializedName

data class InboxMessagesCountRemote(
    @SerializedName("unreadCount")
    val unreadCount: Int
)