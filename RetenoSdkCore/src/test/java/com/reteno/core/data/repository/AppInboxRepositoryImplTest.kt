package com.reteno.core.data.repository

import com.reteno.core.base.BaseUnitTest
import com.reteno.core.data.local.database.manager.RetenoDatabaseManagerAppInbox
import com.reteno.core.data.local.database.manager.RetenoDatabaseManagerDevice
import com.reteno.core.data.local.model.appinbox.AppInboxMessageDb
import com.reteno.core.data.local.model.appinbox.AppInboxMessageStatusDb
import com.reteno.core.data.remote.OperationQueue
import com.reteno.core.data.remote.PushOperationQueue
import com.reteno.core.data.remote.api.ApiClient
import com.reteno.core.data.remote.api.ApiContract
import com.reteno.core.data.remote.mapper.toDomain
import com.reteno.core.data.remote.mapper.toJson
import com.reteno.core.data.remote.model.appinbox.AppInboxMessagesStatusRemote
import com.reteno.core.data.remote.model.inbox.InboxMessagesRemote
import com.reteno.core.domain.ResponseCallback
import com.reteno.core.domain.callback.appinbox.RetenoResultCallback
import com.reteno.core.domain.model.appinbox.AppInboxMessages
import com.reteno.core.util.Logger
import com.reteno.core.util.Util
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.ZonedDateTime
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AppInboxRepositoryImplTest : BaseUnitTest() {

    companion object {
        private const val INBOX_ID = "ehc3-5hdh4-fde4yh-3d5g"
        private const val INBOX_OCCURRED_TIME = "2022-11-22T13:38:01Z"
        private const val INBOX_DEVICE_ID = "device_test"
        private val INBOX_STATUS = AppInboxMessageStatusDb.OPENED
        private const val PAGE = 2
        private const val PAGE_SIZE = 12

        private const val ERROR_CODE = 400
        private const val ERROR_CODE_REPEATABLE = 500
        private const val ERROR_MSG = "error_msg"
        private val ERROR_EXCEPTION = MockKException(ERROR_MSG)

        private const val REGULAR_DELAY = 30_000L
        private const val INITIAL_DELAY = 0L

        private const val MESSAGES_COUNT = 7
        private const val MESSAGES_COUNT_ZERO = 0

        private lateinit var scheduler: ScheduledExecutorService

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockObjectOperationQueue()
            mockObjectPushOperationQueue()
            scheduler = mockStaticScheduler()
            mockStaticJsonMappers()
            mockObjectUtil()
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unMockObjectOperationQueue()
            unMockObjectPushOperationQueue()
            unMockStaticScheduler()
            unMockStaticJsonMappers()
            unMockObjectUtil()
        }

        private fun mockStaticJsonMappers() {
            mockkStatic("com.reteno.core.data.remote.mapper.JsonMappersKt")
            mockkStatic("com.reteno.core.data.remote.mapper.AppInboxMapperKt")
        }

        private fun unMockStaticJsonMappers() {
            unmockkStatic("com.reteno.core.data.remote.mapper.JsonMappersKt")
            unmockkStatic("com.reteno.core.data.remote.mapper.AppInboxMapperKt")
        }
    }

    @RelaxedMockK
    private lateinit var apiClient: ApiClient

    @RelaxedMockK
    private lateinit var configRepository: ConfigRepository

    @RelaxedMockK
    private lateinit var databaseManagerAppInbox: RetenoDatabaseManagerAppInbox

    @RelaxedMockK
    private lateinit var databaseManagerDevice: RetenoDatabaseManagerDevice

    private lateinit var inboxRepository: AppInboxRepositoryImpl

    override fun before() {
        super.before()
        inboxRepository = AppInboxRepositoryImpl(apiClient, databaseManagerAppInbox, configRepository)
    }

    @Test
    fun whenMessageOpened_thenInsertDbAndPush() {
        val inboxStatus = getTestAppInboxDb()
        every { Util.getCurrentTimeStamp() } returns INBOX_OCCURRED_TIME
        every { configRepository.getDeviceId().id } returns INBOX_DEVICE_ID
        every { databaseManagerAppInbox.getAppInboxMessages(any()) } returnsMany listOf(
            listOf(inboxStatus),
            emptyList()
        )

        inboxRepository.saveMessageOpened(INBOX_ID)

        verify(exactly = 1) { apiClient.post(any(), any(), any()) }
        verify(exactly = 1) { databaseManagerAppInbox.insertAppInboxMessage(inboxStatus) }
    }

    @Test
    fun whenDbHasSavedMessage_thenSendToApi() {
        val inboxStatus = getTestAppInboxDb()
        val resultJson = "result_json"

        every { any<AppInboxMessagesStatusRemote>().toJson() } returns resultJson
        every { databaseManagerAppInbox.getAppInboxMessages(any()) } returnsMany listOf(
            listOf(inboxStatus),
            emptyList()
        )
        inboxRepository.pushMessagesStatus()

        verify(exactly = 1) { any<AppInboxMessagesStatusRemote>().toJson() }
        verify(exactly = 1) {
            apiClient.post(
                eq(ApiContract.AppInbox.MessagesStatus),
                eq(resultJson),
                any()
            )
        }
    }

    @Test
    fun whenMessagesPushSuccessful_thenTryPushNextMessages() {
        val inboxStatus = getTestAppInboxDb()
        val spyRepository = spyk(inboxRepository, recordPrivateCalls = true)
        every { databaseManagerAppInbox.getAppInboxMessages(any()) } returnsMany listOf(
            listOf(
                inboxStatus
            ), listOf(inboxStatus), emptyList()
        )
        every { apiClient.post(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess("")
        }

        spyRepository.pushMessagesStatus()

        verify(exactly = 2) { apiClient.post(any(), any(), any()) }
        verify(exactly = 2) { databaseManagerAppInbox.deleteAppInboxMessages(1) }
        verify(exactly = 1) { PushOperationQueue.nextOperation() }

        verify(exactly = 2) { spyRepository["fetchCount"]() }
    }

    @Test
    fun whenMessagesPushFailedAndErrorIsRepeatable_cancelPushOperations() {
        val inboxStatus = getTestAppInboxDb()
        every { databaseManagerAppInbox.getAppInboxMessages(any()) } returns listOf(inboxStatus)
        every { apiClient.post(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onFailure(ERROR_CODE_REPEATABLE, null, null)
        }

        inboxRepository.pushMessagesStatus()

        verify(exactly = 1) { apiClient.post(any(), any(), any()) }
        verify(exactly = 1) { PushOperationQueue.removeAllOperations() }
    }

    @Test
    fun whenMessagesPushFailedAndErrorIsNonRepeatable_thenTryPushNextMessages() {
        val inboxStatus = getTestAppInboxDb()
        every { databaseManagerAppInbox.getAppInboxMessages(any()) } returnsMany listOf(
            listOf(
                inboxStatus
            ), listOf(inboxStatus), emptyList()
        )
        every { apiClient.post(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onFailure(ERROR_CODE, null, null)
        }

        inboxRepository.pushMessagesStatus()

        verify(exactly = 2) { apiClient.post(any(), any(), any()) }
        verify(exactly = 3) { databaseManagerAppInbox.getAppInboxMessages() }
        verify(exactly = 2) { databaseManagerAppInbox.deleteAppInboxMessages(1) }
        verify(exactly = 1) { PushOperationQueue.nextOperation() }
    }

    @Test
    fun givenNoMessagesInDb_whenDevicePush_thenApiClientPutsDoesNotCalled() {
        every { databaseManagerDevice.getDevices(any()) } returns emptyList()

        inboxRepository.pushMessagesStatus()

        verify(exactly = 0) { apiClient.post(any(), any(), any()) }
        verify { PushOperationQueue.nextOperation() }

    }

    @Test
    fun noOutdatedInteraction_whenClearOldInteractions_thenSentNothing() {
        every { databaseManagerAppInbox.deleteAppInboxMessagesByTime(any()) } returns 0

        inboxRepository.clearOldMessages(ZonedDateTime.now())

        verify(exactly = 1) { databaseManagerAppInbox.deleteAppInboxMessagesByTime(any()) }
        verify(exactly = 0) { Logger.captureEvent(any()) }
    }

    @Test
    fun thereAreOutdatedInteraction_whenClearOldInteractions_thenSentCountDeleted() {
        val deletedInbox = 2
        every { databaseManagerAppInbox.deleteAppInboxMessagesByTime(any()) } returns deletedInbox
        val expectedMsg = "Outdated Inbox: - $deletedInbox"

        inboxRepository.clearOldMessages(ZonedDateTime.now())

        verify(exactly = 1) { databaseManagerAppInbox.deleteAppInboxMessagesByTime(any()) }
        verify(exactly = 1) { Logger.captureEvent(eq(expectedMsg)) }
    }

    @Test
    fun whenSetAllMessagesOpened_thenReturnSuccessCallback() {
        val jsonString = "{}"
        val callback = spyk<RetenoResultCallback<Unit>>()
        val spyRepository = spyk(inboxRepository, recordPrivateCalls = true)

        every { any<AppInboxMessagesStatusRemote>().toJson() } returns jsonString
        every { apiClient.post(any(), any(), any()) } answers {
            thirdArg<ResponseCallback>().onSuccess("")
        }

        spyRepository.setAllMessageOpened(callback)

        verify(exactly = 1) {
            apiClient.post(
                ApiContract.AppInbox.MessagesStatus,
                eq(jsonString),
                any()
            )
        }
        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { callback.onSuccess(Unit) }
        verify(exactly = 1) { databaseManagerAppInbox.deleteAllAppInboxMessages() }
        verify(exactly = 1) { spyRepository["fetchCount"]() }
    }

    @Test
    fun whenSetAllMessagesOpened_thenReturnErrorCallback() {
        val jsonString = "{}"
        val callback = spyk<RetenoResultCallback<Unit>>()

        every { any<AppInboxMessagesStatusRemote>().toJson() } returns jsonString
        every { apiClient.post(any(), any(), any()) } answers {
            thirdArg<ResponseCallback>().onFailure(ERROR_CODE, ERROR_MSG, ERROR_EXCEPTION)
        }

        inboxRepository.setAllMessageOpened(callback)

        verify(exactly = 1) {
            apiClient.post(
                ApiContract.AppInbox.MessagesStatus,
                eq(jsonString),
                any()
            )
        }
        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { callback.onFailure(ERROR_CODE, eq(ERROR_MSG), ERROR_EXCEPTION) }
        verify(exactly = 0) { databaseManagerAppInbox.deleteAllAppInboxMessages() }
    }

    @Test
    fun whenGetMessagesWithParams_thenResultSuccess() {
        val inboxMessages = mockk<AppInboxMessages>()
        val retenoCallback = mockk<RetenoResultCallback<AppInboxMessages>>(relaxed = true)
        val resultJson = "{}"
        val queryParams = mapOf(
            ApiContract.AppInbox.QUERY_PAGE to PAGE.toString(),
            ApiContract.AppInbox.QUERY_PAGE_SIZE to PAGE_SIZE.toString()
        )

        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess(resultJson)
        }
        every { any<InboxMessagesRemote>().toDomain() } returns inboxMessages

        inboxRepository.getMessages(PAGE, PAGE_SIZE, retenoCallback)

        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { retenoCallback.onSuccess(inboxMessages) }
        verify(exactly = 1) { any<InboxMessagesRemote>().toDomain() }
        verify(exactly = 1) {
            apiClient.get(
                eq(ApiContract.AppInbox.Messages),
                eq(queryParams),
                any()
            )
        }
    }

    @Test
    fun whenGetMessagesWithoutParams_thenResultSuccess() {
        val inboxMessages = mockk<AppInboxMessages>()
        val retenoCallback = mockk<RetenoResultCallback<AppInboxMessages>>(relaxed = true)
        val resultJson = "{}"
        val queryParams = mapOf(
            ApiContract.AppInbox.QUERY_PAGE to null,
            ApiContract.AppInbox.QUERY_PAGE_SIZE to null
        )

        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess(resultJson)
        }
        every { any<InboxMessagesRemote>().toDomain() } returns inboxMessages

        inboxRepository.getMessages(null, null, retenoCallback)

        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { retenoCallback.onSuccess(inboxMessages) }
        verify(exactly = 1) { any<InboxMessagesRemote>().toDomain() }
        verify(exactly = 1) {
            apiClient.get(
                eq(ApiContract.AppInbox.Messages),
                eq(queryParams),
                any()
            )
        }
    }

    @Test
    fun givenSomeError_whenGetMessages_thenResultFailure() {
        val retenoCallback = mockk<RetenoResultCallback<AppInboxMessages>>(relaxed = true)
        val queryParams = mapOf(
            ApiContract.AppInbox.QUERY_PAGE to null,
            ApiContract.AppInbox.QUERY_PAGE_SIZE to null
        )

        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onFailure(ERROR_CODE, ERROR_MSG, ERROR_EXCEPTION)
        }

        inboxRepository.getMessages(null, null, retenoCallback)

        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { retenoCallback.onFailure(ERROR_CODE, ERROR_MSG, ERROR_EXCEPTION) }
        verify(exactly = 1) {
            apiClient.get(
                eq(ApiContract.AppInbox.Messages),
                eq(queryParams),
                any()
            )
        }
    }

    @Test
    fun whenGetMessagesCount_thenResultSuccess() {
        val retenoCallback = mockk<RetenoResultCallback<Int>>(relaxed = true)
        mockkCountSuccessResponse()

        inboxRepository.getMessagesCount(retenoCallback)

        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { retenoCallback.onSuccess(MESSAGES_COUNT) }
        verify(exactly = 1) { apiClient.get(eq(ApiContract.AppInbox.MessagesCount), null, any()) }
    }

    @Test
    fun givenSomeError_whenGetMessagesCount_thenResultFailure() {
        val retenoCallback = mockk<RetenoResultCallback<Int>>(relaxed = true)
        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onFailure(ERROR_CODE, ERROR_MSG, ERROR_EXCEPTION)
        }

        inboxRepository.getMessagesCount(retenoCallback)

        verify(exactly = 1) { OperationQueue.addUiOperation(any()) }
        verify(exactly = 1) { retenoCallback.onFailure(ERROR_CODE, eq(ERROR_MSG), ERROR_EXCEPTION) }
        verify(exactly = 1) { apiClient.get(eq(ApiContract.AppInbox.MessagesCount), null, any()) }
    }

    @Test
    fun givenPollingIsInactive_whenSubscribeCountChanges_thenStartPolling() {
        inboxRepository.subscribeOnMessagesCountChanged(mockk())

        verify(exactly = 1) {
            scheduler.scheduleAtFixedRate(
                any(),
                INITIAL_DELAY,
                REGULAR_DELAY,
                TimeUnit.MILLISECONDS
            )
        }
    }

    @Test
    fun givenPollingIsActive_whenSubscribeCountChanges_thenPollingDoesNotChange() {
        inboxRepository.subscribeOnMessagesCountChanged(mockk())
        inboxRepository.subscribeOnMessagesCountChanged(mockk())

        verify(exactly = 1) {
            scheduler.scheduleAtFixedRate(
                any(),
                INITIAL_DELAY,
                REGULAR_DELAY,
                TimeUnit.MILLISECONDS
            )
        }
    }

    @Test
    fun givenListenersHaveMoreThanOneItem_whenUnsubscribeCountChanges_thenDoesNotStopPolling() {
        val listenerOne = mockk<RetenoResultCallback<Int>>()
        val listenerTwo = mockk<RetenoResultCallback<Int>>()
        inboxRepository.subscribeOnMessagesCountChanged(listenerOne)
        inboxRepository.subscribeOnMessagesCountChanged(listenerTwo)

        inboxRepository.unsubscribeMessagesCountChanged(listenerOne)

        verify(exactly = 0) { scheduler.shutdownNow() }
    }

    @Test
    fun givenListenersHaveOneItem_whenUnsubscribeCountChanges_thenStopPolling() {
        val listener = mockk<RetenoResultCallback<Int>>()
        inboxRepository.subscribeOnMessagesCountChanged(listener)

        inboxRepository.unsubscribeMessagesCountChanged(listener)

        verify(exactly = 1) { scheduler.shutdownNow() }
    }

    @Test
    fun whenUnsubscribeAll_thenStopPolling() {
        inboxRepository.subscribeOnMessagesCountChanged(mockk())
        inboxRepository.unsubscribeAllMessagesCountChanged()

        verify(exactly = 1) { scheduler.shutdownNow() }
    }

    @Test
    fun givenPollingIsActive_whenFetchCount_thenNotifySuccess() {
        val listener = spyk<RetenoResultCallback<Int>>()
        mockkCountSuccessResponse()

        inboxRepository.subscribeOnMessagesCountChanged(listener)

        verify(exactly = 1) { listener.onSuccess(MESSAGES_COUNT) }
    }

    @Test
    fun givenPollingIsActiveWithSeveralListeners_whenFetchCount_thenNotifySuccessToAllListeners() {
        val listenerOne = spyk<RetenoResultCallback<Int>>()
        val listenerTwo = spyk<RetenoResultCallback<Int>>()

        mockkCountSuccessResponse()

        inboxRepository.subscribeOnMessagesCountChanged(listenerOne)
        inboxRepository.subscribeOnMessagesCountChanged(listenerTwo)

        verify(exactly = 1) { listenerOne.onSuccess(MESSAGES_COUNT) }
        verify(exactly = 1) { listenerTwo.onSuccess(MESSAGES_COUNT) }
    }

    @Test
    fun givenPollingIsActiveAndHasValue_whenFetchCountAndValuesAreEquals_thenNotifySuccessWithoutCallback() {
        val listener = spyk<RetenoResultCallback<Int>>()
        mockkCountSuccessResponse()
        val currentThreadExecutor = Executor(Runnable::run)
        every { scheduler.scheduleAtFixedRate(any(), any(), any(), any()) } answers {
            currentThreadExecutor.execute(firstArg())
            currentThreadExecutor.execute(firstArg())
            mockk()
        }

        inboxRepository.subscribeOnMessagesCountChanged(listener)

        verify(exactly = 1) { listener.onSuccess(MESSAGES_COUNT) }
    }

    @Test
    fun givenPollingIsActiveAndHasValue_whenFetchCountAndValuesAreNotEquals_thenNotifySuccessWithoutCallback() {
        val listener = spyk<RetenoResultCallback<Int>>()
        mockkCountSuccessResponseForTwoCalls()
        val currentThreadExecutor = Executor(Runnable::run)
        every { scheduler.scheduleAtFixedRate(any(), any(), any(), any()) } answers {
            currentThreadExecutor.execute(firstArg())
            currentThreadExecutor.execute(firstArg())
            mockk()
        }

        inboxRepository.subscribeOnMessagesCountChanged(listener)

        verify(exactly = 1) { listener.onSuccess(MESSAGES_COUNT_ZERO) }
        verify(exactly = 1) { listener.onSuccess(MESSAGES_COUNT) }
    }

    @Test
    fun givenPollingIsActiveAndSomeError_whenFetchCount_thenNotifyFailure() {
        val listener = spyk<RetenoResultCallback<Int>>()
        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onFailure(ERROR_CODE, ERROR_MSG, ERROR_EXCEPTION)
        }

        inboxRepository.subscribeOnMessagesCountChanged(listener)

        verify(exactly = 1) { listener.onFailure(ERROR_CODE, eq(ERROR_MSG), ERROR_EXCEPTION) }
    }

    private fun getTestAppInboxDb(): AppInboxMessageDb {
        return AppInboxMessageDb(
            id = INBOX_ID,
            deviceId = INBOX_DEVICE_ID,
            occurredDate = INBOX_OCCURRED_TIME,
            status = INBOX_STATUS
        )
    }

    private fun mockkCountSuccessResponse() {
        val resultJson = """
            {
                "unreadCount": $MESSAGES_COUNT
            }
        """.trimIndent()

        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess(resultJson)
        }
    }

    private fun mockkCountSuccessResponseForTwoCalls() {
        val resultJsonFirst = """
            {
                "unreadCount": $MESSAGES_COUNT_ZERO
            }
        """.trimIndent()

        val resultJsonSecond = """
            {
                "unreadCount": $MESSAGES_COUNT
            }
        """.trimIndent()

        every { apiClient.get(any(), any(), any()) } answers {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess(resultJsonFirst)
        } andThenAnswer {
            val callback = thirdArg<ResponseCallback>()
            callback.onSuccess(resultJsonSecond)
        }
    }

}
