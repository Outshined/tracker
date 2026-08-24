package org.paul.tracker

import android.app.Application
import java.time.Instant
import org.paul.tracker.data.Clock

class TrackerApp : Application() {
    val clock: Clock = Clock { Instant.now() }
}
