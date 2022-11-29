package com.reteno.core.di.provider

import com.reteno.core.di.base.ProviderWeakReference
import com.reteno.core.domain.controller.ScheduleController

class ScheduleControllerProvider(
    private val contactControllerProvider: ContactControllerProvider,
    private val interactionControllerProvider: InteractionControllerProvider,
    private val eventsControllerProvider: EventsControllerProvider,
    private val appInboxControllerProvider: AppInboxControllerProvider,
    private val recommendationControllerProvider: RecommendationControllerProvider,
    private val workManagerProvider: WorkManagerProvider
) : ProviderWeakReference<ScheduleController>() {

    override fun create(): ScheduleController {
        return ScheduleController(
            contactControllerProvider.get(),
            interactionControllerProvider.get(),
            eventsControllerProvider.get(),
            appInboxControllerProvider.get(),
            recommendationControllerProvider.get(),
            workManagerProvider.get()
        )
    }
}