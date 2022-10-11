package com.reteno.fcm

import android.app.Application
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.reteno.push.RetenoPushService
import com.reteno.util.Logger

class RetenoFirebaseServiceHandler(private val application: Application) {

    private val pushService: RetenoPushService = RetenoPushService(application)

    /**
     * Call from your implementation of [FirebaseMessagingService.onCreate]
     */
    fun onCreate() {
        /*@formatter:off*/ Logger.i(TAG, "onCreate(): ", "application = ", application)
        /*@formatter:on*/
        // TODO: Nothing to do yet. Maybe remove later
    }

    /**
     * Call from your implementation of [FirebaseMessagingService.onNewToken]
     */
    fun onNewToken(token: String) {
        /*@formatter:off*/ Logger.i(TAG, "onNewToken(): ", "token = [" , token , "]")
        /*@formatter:on*/
        pushService.onNewToken(token)
    }

    /**
     * Call from your implementation of
     * [FirebaseMessagingService.onMessageReceived]
     */
    fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageMap = remoteMessage.data
        /*@formatter:off*/ Logger.i(TAG, "onMessageReceived(): ", "messageMap = [" , messageMap , "]")
        /*@formatter:on*/

        val hasInteractionId = messageMap.containsKey(KEY_INTERACTION_ID)
        if (hasInteractionId) {
            pushService.onPushReceived(getBundle(messageMap))
        } else {
            // TODO: SEND notification to broadcast receiver to be handled by application dev
        }
    }

    /**
     * @param messageMap [RemoteMessage]'s data map.
     */
    private fun getBundle(messageMap: Map<String, String>?): Bundle {
        val bundle = Bundle()
        if (messageMap != null) {
            for ((key, value) in messageMap) {
                bundle.putString(key, value)
            }
        }
        return bundle
    }

    companion object {
        val TAG: String = RetenoFirebaseServiceHandler::class.java.simpleName
        const val KEY_INTERACTION_ID = "es_interaction_id"
    }
}