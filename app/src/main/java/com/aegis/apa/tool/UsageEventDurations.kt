package com.aegis.apa.tool

enum class UsageEventKind { RESUME, PAUSE, CLOSE_ALL, RESET }

data class UsageEventRecord(
    val packageName: String,
    val className: String,
    val timestamp: Long,
    val kind: UsageEventKind
)

data class UsageEventResult(val durations: Map<String, Long>, val isPartial: Boolean)

/** Clips observed activity intervals to [start, end); overlapping activities in a package are unioned. */
object UsageEventDurations {
    fun calculate(events: List<UsageEventRecord>, start: Long, end: Long): UsageEventResult {
        require(start <= end)
        val active = mutableMapOf<Pair<String, String>, Long>()
        val intervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        var partial = false
        fun close(key: Pair<String, String>, until: Long) {
            val since = active.remove(key) ?: return
            val clippedStart = maxOf(since, start)
            val clippedEnd = minOf(until, end)
            if (clippedEnd > clippedStart) {
                intervals.getOrPut(key.first) { mutableListOf() }.add(clippedStart to clippedEnd)
            }
        }
        events.sortedBy { it.timestamp }.filter { it.timestamp < end }.forEach { event ->
            val key = event.packageName to event.className
            when (event.kind) {
                UsageEventKind.RESUME -> if (event.packageName.isNotBlank()) {
                    active.putIfAbsent(key, event.timestamp)
                }
                UsageEventKind.PAUSE -> {
                    if (key !in active && event.timestamp >= start) partial = true
                    close(key, event.timestamp)
                }
                UsageEventKind.CLOSE_ALL -> active.keys.toList().forEach { close(it, event.timestamp) }
                UsageEventKind.RESET -> {
                    if (active.isNotEmpty() && event.timestamp >= start) partial = true
                    active.clear() // No shutdown event: never invent usage over a reboot gap.
                }
            }
        }
        active.keys.toList().forEach { close(it, end) }
        val durations = intervals.mapValues { (_, ranges) ->
            var total = 0L
            var mergedStart = -1L
            var mergedEnd = -1L
            ranges.sortedBy { it.first }.forEach { (from, to) ->
                if (mergedStart < 0) {
                    mergedStart = from
                    mergedEnd = to
                } else if (from <= mergedEnd) {
                    mergedEnd = maxOf(mergedEnd, to)
                } else {
                    total += mergedEnd - mergedStart
                    mergedStart = from
                    mergedEnd = to
                }
            }
            total + mergedEnd - mergedStart
        }
        return UsageEventResult(durations, partial)
    }
}
