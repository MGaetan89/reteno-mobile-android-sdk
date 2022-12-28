package com.reteno.core.domain.controller

import com.reteno.core.base.BaseUnitTest
import com.reteno.core.data.remote.OperationQueue
import com.reteno.core.data.remote.PushOperationQueue
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

class ScheduleControllerImplTest : BaseUnitTest() {

    // region constants ----------------------------------------------------------------------------
    companion object {
        private const val CLEAR_OLD_DATA_DELAY = 3000L

        private lateinit var scheduler: ScheduledExecutorService

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            mockStaticLog()
            mockObjectOperationQueue()
            mockObjectPushOperationQueue()

            scheduler = mockStaticScheduler()
            mockObjectPushDataWorker()
        }

        @JvmStatic
        @AfterClass
        fun afterClass() {
            unMockStaticLog()
            unMockObjectOperationQueue()
            unMockObjectPushOperationQueue()

            unMockStaticScheduler()
            unMockObjectPushDataWorker()
        }
    }
    // endregion constants -------------------------------------------------------------------------

    // region helper fields ------------------------------------------------------------------------
    @RelaxedMockK
    private lateinit var contactController: ContactController

    @RelaxedMockK
    private lateinit var interactionController: InteractionController

    @RelaxedMockK
    private lateinit var eventController: EventController

    @RelaxedMockK
    private lateinit var appInboxController: AppInboxController

    @RelaxedMockK
    private lateinit var recommendationController: RecommendationController



    private lateinit var SUT: ScheduleController
    // endregion helper fields ---------------------------------------------------------------------

    override fun before() {
        super.before()
        SUT = ScheduleControllerImpl(contactController, interactionController, eventController, appInboxController, recommendationController, mockk(relaxed = true))
    }

    override fun after() {
        super.after()
        clearMocks(PushOperationQueue)
    }

    @Test
    fun giveSchedulerControllerFirstCalled_whenStartScheduler_thenScheduleNewFixRateTask() {
        // When
        SUT.startScheduler()

        // Then
        verify { scheduler.scheduleAtFixedRate(any(), any(), any(), any()) }
    }

    @Test
    fun whenStartScheduler_thenAddPushOperation() {
        // Given
        val currentThreadExecutor = Executor(Runnable::run)
        every { PushOperationQueue.addOperation(any()) } answers {
            currentThreadExecutor.execute(firstArg())
            PushOperationQueue.nextOperation()
        }

        // When
        SUT.startScheduler()

        // Then
        verify(exactly = 6) { PushOperationQueue.addOperation(any()) }
        verify(exactly = 7) { PushOperationQueue.nextOperation() }
        verify { contactController.pushDeviceData() }
        verify { contactController.pushUserData() }
        verify { interactionController.pushInteractions() }
        verify { eventController.pushEvents() }
        verify { appInboxController.pushAppInboxMessagesStatus() }
    }

    @Test
    fun whenStopSchedule_thenSchedulerShutdown() {
        // When
        SUT.startScheduler()
        SUT.stopScheduler()

        // Then
        verify { scheduler.shutdownNow() }
    }

    @Test
    fun whenForcePush_thenAddPushOperation() {
        // When
        SUT.forcePush()

        // Then
        verify(exactly = 6) { PushOperationQueue.addOperation(any()) }
        verify(exactly = 1) { PushOperationQueue.nextOperation() }
    }

    @Test
    fun whenForcePushCalledTwiceOneSecond_thenDoesNotAddPushOperationTwice() {
        // When
        SUT.forcePush()
        SUT.forcePush()

        // Then
        verify(exactly = 6) { PushOperationQueue.addOperation(any()) }
        verify(exactly = 1) { PushOperationQueue.nextOperation() }
    }

    @Test
    fun whenClearOldOperation_thenAddOperationToQueueWithDelay() {
        // When
        SUT.clearOldData()

        // Then
        verify(exactly = 4) { OperationQueue.addOperationAfterDelay(any(), eq(CLEAR_OLD_DATA_DELAY)) }
        verify { interactionController.clearOldInteractions() }
        verify { eventController.clearOldEvents() }
        verify { appInboxController.clearOldMessagesStatus() }
    }

}