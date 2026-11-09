package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/** How many of the most recent watches seed the Because-you-watched lane and its page. */
internal const val BECAUSE_YOU_WATCHED_SEEDS = 5

/** How many suggestions each watched video contributes to the lane and its page. */
internal const val BECAUSE_YOU_WATCHED_PER_SEED = 3

/**
 * Merges per-seed related lists round-robin so no single seed dominates, dropping duplicates and
 * the seeds themselves, capped at [maxVideos] total and [maxPerSeed] per seed.
 */
internal fun mergeRelatedRoundRobin(
    perSeed: List<List<Video>>,
    excludeIds: Set<String>,
    maxVideos: Int = Int.MAX_VALUE,
    maxPerSeed: Int = Int.MAX_VALUE,
): List<Video> {
    val seen = HashSet<String>()
    val videos = ArrayList<Video>()
    var index = 0
    while (videos.size < maxVideos && index < maxPerSeed) {
        var added = false
        for (list in perSeed) {
            if (videos.size >= maxVideos) break
            val video = list.getOrNull(index) ?: continue
            if (video.id in excludeIds) continue
            if (seen.add(video.id)) {
                videos.add(video)
                added = true
            }
        }
        if (!added) break
        index++
    }
    return videos
}
