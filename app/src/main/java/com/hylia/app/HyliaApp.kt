package com.hylia.app

import android.app.Application
import com.hylia.app.coaching.CoachingEngine
import com.hylia.app.profile.ProfileStore

class HyliaApp : Application() {

    val profileStore by lazy { ProfileStore(this) }
    val coachingEngine by lazy { CoachingEngine(profileStore) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        profileStore.rollToToday()
    }

    companion object {
        lateinit var instance: HyliaApp
            private set
    }
}