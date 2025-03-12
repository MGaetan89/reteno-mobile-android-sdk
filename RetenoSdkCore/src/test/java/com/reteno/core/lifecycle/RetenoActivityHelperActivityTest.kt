package com.reteno.core.lifecycle

import android.app.Activity
import com.reteno.core.base.robolectric.BaseRobolectricTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController


@OptIn(ExperimentalCoroutinesApi::class)
class RetenoActivityHelperActivityTest : BaseRobolectricTest() {

    // region helper fields ------------------------------------------------------------------------
    @RelaxedMockK
    private lateinit var retenoLifecycleCallbacks: RetenoLifecycleCallbacks

    private lateinit var activityController: ActivityController<Activity>

    private lateinit var SUT: RetenoActivityHelper
    // endregion helper fields ---------------------------------------------------------------------


    override fun before() {
        super.before()
        SUT = RetenoActivityHelperImpl()
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
    }

    @Test
    fun whenActivityResumed_thenLifecycleCallbackResumeCalled() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume()

        // Then
        verify(exactly = 1) { retenoLifecycleCallbacks.resume(activityController.get()) }
    }

    @Test
    fun whenActivityPaused_thenLifecycleCallbackPauseCalled() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume().pause()

        // Then
        verify(exactly = 1) { retenoLifecycleCallbacks.pause(activityController.get()) }
    }

    @Test
    fun whenActivityStopped_thenLifecycleCallbackStopCalled() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume().pause().stop()

        // Then
        verify(exactly = 1) { retenoLifecycleCallbacks.stop(activityController.get()) }
    }

    @Test
    fun whenActivityIsStarted_thenCanPresentMessagesFalse() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start()

        // Then
        assertFalse(SUT.canPresentMessages())
    }

    @Test
    fun whenActivityIsResumed_thenCanPresentMessagesTrue() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume()

        // Then
        assertTrue(SUT.canPresentMessages())
    }

    @Test
    fun whenActivityIsPaused_thenCanPresentMessagesFalse() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume().pause()

        // Then
        assertFalse(SUT.canPresentMessages())
    }

    @Test
    fun whenActivityIsStopped_thenCanPresentMessagesFalse() = runTest {
        //Given
        createRetenoAndAdvanceInit()
        SUT.enableLifecycleCallbacks(activityController.get().application)
        SUT.registerActivityLifecycleCallbacks("KEY", retenoLifecycleCallbacks)
        // When
        activityController.start().resume().pause().stop()

        // Then
        assertFalse(SUT.canPresentMessages())
    }
}