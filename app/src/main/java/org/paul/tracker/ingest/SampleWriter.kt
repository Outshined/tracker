package org.paul.tracker.ingest

import org.paul.tracker.data.Sample

/**
 * Insert or replace a sample by [Sample.id].
 * Rejects the whole sample (IllegalArgumentException) if `id` or `metricId` is blank
 * or any `values` entry is non-finite.
 * Caller supplies `id`: manual UI uses a random UUID; a future Bluetooth source
 * may pass a deterministic id so retries do not duplicate rows.
 */
fun interface SampleWriter {
    fun upsert(sample: Sample)
}
