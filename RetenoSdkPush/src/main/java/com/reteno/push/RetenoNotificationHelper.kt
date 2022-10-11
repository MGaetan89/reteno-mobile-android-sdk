package com.reteno.push

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.reteno.push.Constants.KEY_ES_CONTENT
import com.reteno.push.Constants.KEY_ES_INTERACTION_ID
import com.reteno.push.Constants.KEY_ES_TITLE
import com.reteno.util.Logger
import com.reteno.util.getAppName
import com.reteno.util.getApplicationMetaData

internal object RetenoNotificationHelper {

    val TAG: String = RetenoNotificationHelper::class.java.simpleName

    private const val RETENO_DEFAULT_PUSH_ICON = "reteno_default_push_icon"
    private const val CHANNEL_DEFAULT_NAME = "default"
    private const val CHANNEL_DEFAULT_DESCRIPTION = "This is a default channel"
    const val CHANNEL_DEFAULT_ID: String = "CHANNEL_ID"

    private const val NOTIFICATION_ID_DEFAULT = 1

    internal fun getNotificationBuilderCompat(
        application: Application,
        bundle: Bundle,
        contentIntent: PendingIntent? = null,
    ): NotificationCompat.Builder {
        val icon = getNotificationIcon(application)
        val title = getNotificationTitle(application, bundle)
        val text = getNotificationText(bundle)

        val builder = NotificationCompat.Builder(application.applicationContext, CHANNEL_DEFAULT_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        text?.let(builder::setContentText)

        return builder
    }

    internal fun createChannel(context: Context) {
        val name = CHANNEL_DEFAULT_NAME
        val descriptionText = CHANNEL_DEFAULT_DESCRIPTION
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_DEFAULT_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    internal fun getNotificationId(bundle: Bundle): Int {
        val notificationIdString: String? = bundle.getString(KEY_ES_INTERACTION_ID)
        // FIXME notification id is calculated by hashCode() function on "es_interaction_id" string. Collisions may appear
        val notificationId: Int = notificationIdString?.hashCode() ?: NOTIFICATION_ID_DEFAULT
        /*@formatter:off*/ Logger.i(TAG, "getNotificationId(): ", "bundle = [" , bundle , "]", " notificationId = [", notificationId, "]")
        /*@formatter:on*/
        return notificationId
    }

    private fun getNotificationIcon(application: Application): Int {
        val metadata = application.getApplicationMetaData()
        val customIconResName = application.resources.getString(R.string.notification_icon)
        var icon = metadata.getInt(customIconResName)

        if (icon == 0) {
            /*@formatter:off*/ Logger.i(TAG, "getNotificationIcon(): ", "application = [" , application , "], No icon in metaData.")
            /*@formatter:on*/
            icon = getDefaultNotificationIcon(application.applicationContext)
        }

        return icon
    }

    /**
     * Gets default push notification resource id for RETENO_DEFAULT_PUSH_ICON in drawable.
     *
     * @param context Current application context.
     * @return int Resource id.
     */
    private fun getDefaultNotificationIcon(context: Context): Int {
        return try {
            /*@formatter:off*/ Logger.i(TAG, "getDefaultPushNotificationIconResourceId(): ", "context = [" , context , "]")
            /*@formatter:on*/
            val resources = context.resources
            resources.getIdentifier(RETENO_DEFAULT_PUSH_ICON, "drawable", context.packageName)
        } catch (ignored: Throwable) {
            0
        }
    }

    private fun getNotificationTitle(application: Application, bundle: Bundle): String {
        val title = bundle.getString(KEY_ES_TITLE) ?: application.getAppName()
        /*@formatter:off*/ Logger.i(TAG, "getNotificationName(): ", "application = [" , application , "], bundle = [" , bundle , "] title = [", title, "]")
        /*@formatter:on*/
        return title
    }

    private fun getNotificationText(bundle: Bundle): String? {
        val notificationText = bundle.getString(KEY_ES_CONTENT)
        /*@formatter:off*/ Logger.i(TAG, "getNotificationText(): ", "bundle = [" , bundle , "] notificationText = [", notificationText, "]")
        /*@formatter:on*/
        return notificationText
    }
}