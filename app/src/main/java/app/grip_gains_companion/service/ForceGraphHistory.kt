package app.grip_gains_companion.service

import app.grip_gains_companion.model.ForceHistoryEntry

/** Display-only history. Older graph detail is reduced with min/max pairs, never training data. */
class ForceGraphHistory {
    private val points = ArrayList<ForceHistoryEntry>()
    private var publishedAt: Long? = null

    val latestTimestamp: Long? get() = points.lastOrNull()?.timestamp?.time
    fun restartTimeline() { publishedAt = null }

    fun clear() { points.clear(); publishedAt = null }

    fun add(point: ForceHistoryEntry): List<ForceHistoryEntry>? {
        points.add(point)
        if (points.size > 8192) {
            val reduced = ArrayList<ForceHistoryEntry>(6146)
            reduced.add(points.first())
            // Retain the latest 4096+ samples at full resolution and peaks in the older overview.
            for (start in 0 until 4096 step 4) {
                val group = points.subList(start, start + 4)
                val low = group.indices.minBy { group[it].force }
                val high = group.indices.maxBy { group[it].force }
                for (index in listOf(low, high).distinct().sorted()) {
                    if (reduced.last() != group[index]) reduced.add(group[index])
                }
            }
            reduced.addAll(points.subList(4096, points.size))
            points.clear(); points.addAll(reduced)
        }
        val now = point.timestamp.time
        val previous = publishedAt
        if (previous != null && now >= previous && now - previous < 50) return null
        publishedAt = now
        return points.toList()
    }
}

/** Use the incoming sample timeline, not the phone wall clock, to select recent measurements. */
fun visibleForceHistory(history: List<ForceHistoryEntry>, windowSeconds: Int): List<ForceHistoryEntry> {
    if (windowSeconds <= 0 || history.isEmpty()) return history
    val cutoff = history.last().timestamp.time - windowSeconds * 1000L
    return history.filter { it.timestamp.time >= cutoff }
}
