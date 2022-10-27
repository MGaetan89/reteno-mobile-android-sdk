package com.reteno.core.di.provider

import com.reteno.core.di.base.ProviderWeakReference
import com.reteno.core.domain.controller.EventController

class EventsControllerProvider(private val eventsRepositoryProvider: EventsRepositoryProvider) :
    ProviderWeakReference<EventController>() {

    override fun create(): EventController {
        return EventController(eventsRepositoryProvider.get())
    }
}