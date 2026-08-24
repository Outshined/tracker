package org.bohme.tracker

import android.app.Application
import java.time.Instant
import org.bohme.tracker.data.Clock

class TrackerApp : Application() {
    val clock: Clock = Clock { Instant.now() }
}
