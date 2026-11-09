package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/** One slot of the home grid: a video card, or one of the shelves that own a whole row. */
internal sealed interface HomeFeedRow {
    val key: String

    data class VideoCard(
        val video: Video,
    ) : HomeFeedRow {
        override val key: String get() = video.id
    }

    data object ContinueWatching : HomeFeedRow {
        override val key: String = "continue_watching_shelf"
    }

    data object BecauseYouWatched : HomeFeedRow {
        override val key: String = "because_you_watched_shelf"
    }

    data object Shorts : HomeFeedRow {
        override val key: String = "shorts_shelf"
    }

    val spansRow: Boolean get() = this !is VideoCard
}

/**
 * The shelves span every column, so they go in after exactly one full first row; anywhere else
 * the row above them would render with holes in it. The because-you-watched lane is the
 * exception: it owns the first row, so nothing sits above it to leave a hole.
 */
internal fun homeFeedRows(
    videos: List<Video>,
    columns: Int,
    showContinueWatching: Boolean,
    showShorts: Boolean,
    showBecauseYouWatched: Boolean = false,
): List<HomeFeedRow> {
    if (videos.isEmpty()) return emptyList()
    val firstRun = columns.coerceAtLeast(1).coerceAtMost(videos.size)
    return buildList {
        if (showBecauseYouWatched) add(HomeFeedRow.BecauseYouWatched)
        videos.take(firstRun).mapTo(this, HomeFeedRow::VideoCard)
        if (showContinueWatching) add(HomeFeedRow.ContinueWatching)
        if (showShorts) add(HomeFeedRow.Shorts)
        videos.drop(firstRun).mapTo(this, HomeFeedRow::VideoCard)
    }
}
