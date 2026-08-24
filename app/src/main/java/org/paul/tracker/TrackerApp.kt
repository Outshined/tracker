package org.paul.tracker

import android.app.Application
import java.time.Clock

class TrackerApp : Application() {
    val clock: Clock = Clock.systemDefaultZone()
}
