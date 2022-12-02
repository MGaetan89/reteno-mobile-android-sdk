package com.reteno.core.data.repository

import android.text.TextUtils
import com.reteno.core.base.BaseUnitTest
import com.reteno.core.data.local.config.DeviceId
import com.reteno.core.data.local.config.DeviceIdMode
import com.reteno.core.data.local.config.RestConfig
import com.reteno.core.data.local.sharedpref.SharedPrefsManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Matchers.anyString

class ConfigRepositoryTest : BaseUnitTest() {

    // region constants ----------------------------------------------------------------------------
    companion object {
        private const val DEVICE_ID = "device_ID"
        private const val EXTERNAL_DEVICE_ID = "External_device_ID"
        private const val FCM_TOKEN = "FCM_Token"
        private const val DEFAULT_NOTIFICATION_CHANNEL = "default_notification_channel_ID"
    }
    // endregion constants -------------------------------------------------------------------------

    // region helper fields ------------------------------------------------------------------------
    @MockK
    private lateinit var sharedPrefsManager: SharedPrefsManager

    @RelaxedMockK
    private lateinit var restConfig: RestConfig

    private lateinit var SUT: ConfigRepositoryImpl
    // endregion helper fields ---------------------------------------------------------------------

    override fun before() {
        super.before()
        mockOperationQueue()
        SUT = ConfigRepositoryImpl(sharedPrefsManager, restConfig)
    }

    override fun after() {
        super.after()
        unMockOperationQueue()
    }

    @Test
    fun given_whenSetExternalDeviceId_thenDelegatedToRestConfig() {
        // Given
        every { restConfig["setExternalUserId"](anyString()) } returns Unit

        // When
        SUT.setExternalUserId(EXTERNAL_DEVICE_ID)

        // Then
        verify(exactly = 1) { restConfig["setExternalUserId"](EXTERNAL_DEVICE_ID) }
    }

    @Test
    fun given_whenGetDeviceId_thenDelegatedToRestConfig() {
        // Given
        val deviceId = DeviceId(DEVICE_ID, EXTERNAL_DEVICE_ID, DeviceIdMode.RANDOM_UUID)
        every { restConfig.deviceId } returns deviceId

        // When
        val result = SUT.getDeviceId()

        // Then
        verify(exactly = 1) { restConfig.deviceId }
        assertEquals(deviceId, result)
    }

    @Test
    fun given_whenSaveFcmToken_thenDelegatedToSharedPrefsManager() {
        // Given
        every { sharedPrefsManager.saveFcmToken(any()) } just runs

        // When
        SUT.saveFcmToken(FCM_TOKEN)

        // Then
        verify(exactly = 1) { sharedPrefsManager.saveFcmToken(FCM_TOKEN) }
    }

    @Test
    fun given_whenGetFcmToken_thenDelegatedToSharedPrefsManager() {
        // Given
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } returns false
        every { sharedPrefsManager.getFcmToken() } returns FCM_TOKEN

        // When
        val result = SUT.getFcmToken()

        // Then
        verify(exactly = 1) { sharedPrefsManager.getFcmToken() }
        assertEquals(FCM_TOKEN, result)
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun given_whenSaveDefaultNotificationChannel_thenDelegatedToSharedPrefsManager() {
        // Given
        every { sharedPrefsManager.saveDefaultNotificationChannel(any()) } just runs

        // When
        SUT.saveDefaultNotificationChannel(DEFAULT_NOTIFICATION_CHANNEL)

        // Then
        verify(exactly = 1) { sharedPrefsManager.saveDefaultNotificationChannel(DEFAULT_NOTIFICATION_CHANNEL) }
    }

    @Test
    fun given_whenGetDefaultNotificationChannel_thenDelegatedToSharedPrefsManager() {
        // Given
        every { sharedPrefsManager.getDefaultNotificationChannel() } returns DEFAULT_NOTIFICATION_CHANNEL

        // When
        val result = SUT.getDefaultNotificationChannel()

        // Then
        verify(exactly = 1) { sharedPrefsManager.getDefaultNotificationChannel() }
        assertEquals(DEFAULT_NOTIFICATION_CHANNEL, result)
    }
}