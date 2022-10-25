package com.reteno.push

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.reteno.push.Constants.KEY_ES_CONTENT
import com.reteno.push.Constants.KEY_ES_INTERACTION_ID
import com.reteno.push.Constants.KEY_ES_NOTIFICATION_IMAGE
import com.reteno.push.Constants.KEY_ES_TITLE
import com.reteno.push.base.robolectric.BaseRobolectricTest
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@Config(sdk = [26])
class RetenoNotificationServiceTest : BaseRobolectricTest() {

    private val pushService by lazy {
        RetenoNotificationService()
    }

    @Test
    @Throws(Exception::class)
    fun givenValidToken_whenOnNewToken_thenSavedToRepository() {
        val expectedToken = "4bf5c8e5-72d5-4b3c-81d6-85128928e296"
        pushService.onNewToken(expectedToken)

        val configRepository = reteno.serviceLocator.configRepositoryProvider.get()
        val actualToken = configRepository.getFcmToken()
        assertEquals(expectedToken, actualToken)
    }

    @Test
    @Throws(Exception::class)
    fun givenValidNotification_whenShowNotification_thenNotificationShown() {
        val interactionId = "interaction_id_1231_4321_9900_0011"

        val bundle = Bundle().apply {
            putString(KEY_ES_INTERACTION_ID, interactionId)
            putString(KEY_ES_TITLE, "Title")
            putString(KEY_ES_CONTENT, "content")
            putString(
                KEY_ES_NOTIFICATION_IMAGE,
                "https://png.pngtree.com/png-clipart/20210425/original/pngtree-lying-down-a-friend-png-image_6248990.jpg"
            )
        }

        pushService.showNotification(bundle)

        val notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, Shadows.shadowOf(notificationManager).size())
    }
}