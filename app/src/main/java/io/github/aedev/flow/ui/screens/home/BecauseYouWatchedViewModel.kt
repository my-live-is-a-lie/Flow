package io.github.aedev.flow.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.engagement.FeedInvalidationBus
import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BECAUSE_YOU_WATCHED_PAGE_SIZE = 20
private const val BECAUSE_YOU_WATCHED_EXPAND_BATCH = 3

data class BecauseYouWatchedUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isFullWidthView: Boolean = false,
)

/**
 * The dedicated Because-you-watched page: the merged related pool of the most recent watches,
 * paged as the viewer scrolls. When the pool runs dry it expands to older history seeds, so the
 * page only ends when the watch history does.
 */
@HiltViewModel
class BecauseYouWatchedViewModel
    @Inject
    constructor(
        private val feedSources: HomeFeedSources,
        private val playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BecauseYouWatchedUiState(isLoading = true))
        val uiState: StateFlow<BecauseYouWatchedUiState> = _uiState.asStateFlow()

        private var seeds: List<GraphSeedInput> = emptyList()
        private var pool: List<Video> = emptyList()
        private var seedCursor = 0
        private var loadJob: Job? = null

        init {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.becauseYouWatchedFullWidthView.collect { fullWidth ->
                    _uiState.update { it.copy(isFullWidthView = fullWidth) }
                }
            }
            // A dismissal from a card menu lands here too, so the video leaves the page at once;
            // the persisted exclusion keeps it out of every later rebuild.
            viewModelScope.launch {
                FeedInvalidationBus.events.collect { event ->
                    when (event) {
                        is FeedInvalidationBus.Event.NotInterested -> dropVideo(event.videoId)
                        is FeedInvalidationBus.Event.ChannelBlocked -> dropChannel(event.channelId)
                        else -> Unit
                    }
                }
            }
        }

        fun toggleViewMode() {
            val newValue = !_uiState.value.isFullWidthView
            _uiState.update { it.copy(isFullWidthView = newValue) }
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setBecauseYouWatchedFullWidthView(newValue)
            }
        }

        private fun dropVideo(videoId: String) {
            pool = pool.filter { it.id != videoId }
            _uiState.update { state ->
                val videos = state.videos.filter { it.id != videoId }
                state.copy(
                    videos = videos,
                    hasMore = videos.size < pool.size || seedCursor < seeds.size,
                )
            }
        }

        private fun dropChannel(channelId: String) {
            pool = pool.filter { it.channelId != channelId }
            _uiState.update { state ->
                val videos = state.videos.filter { it.channelId != channelId }
                state.copy(
                    videos = videos,
                    hasMore = videos.size < pool.size || seedCursor < seeds.size,
                )
            }
        }

        fun loadInitial() {
            if (loadJob?.isActive == true || _uiState.value.videos.isNotEmpty()) return
            loadJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    seeds = runCatching { feedSources.historySeedInputs() }.getOrElse { emptyList() }
                    val batch = seeds.take(BECAUSE_YOU_WATCHED_SEEDS)
                    pool = fetchMerged(batch, forceRefresh = false)
                    seedCursor = batch.size
                    _uiState.update {
                        it.copy(
                            videos = pool.take(BECAUSE_YOU_WATCHED_PAGE_SIZE),
                            isLoading = false,
                            hasMore = pool.size > BECAUSE_YOU_WATCHED_PAGE_SIZE || seedCursor < seeds.size,
                        )
                    }
                }
        }

        fun loadMore() {
            val state = _uiState.value
            if (state.isLoading || state.isLoadingMore || !state.hasMore) return
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    _uiState.update { it.copy(isLoadingMore = true) }
                    val shown = _uiState.value.videos.size
                    // Serve the next page from the pool; only when it runs dry expand to older
                    // seeds, one batch at a time, until something new arrives or history ends.
                    while (shown >= pool.size && seedCursor < seeds.size) {
                        val batch = seeds.drop(seedCursor).take(BECAUSE_YOU_WATCHED_EXPAND_BATCH)
                        seedCursor += batch.size
                        val fresh =
                            fetchMerged(
                                batch,
                                forceRefresh = false,
                                excludeIds = pool.mapTo(HashSet()) { it.id },
                            )
                        if (fresh.isNotEmpty()) pool = pool + fresh
                    }
                    val next = pool.take((shown + BECAUSE_YOU_WATCHED_PAGE_SIZE).coerceAtMost(pool.size))
                    _uiState.update {
                        it.copy(
                            videos = next,
                            isLoadingMore = false,
                            hasMore = next.size < pool.size || seedCursor < seeds.size,
                        )
                    }
                }
        }

        /**
         * Re-fetches the lane seeds fresh, bypassing the related cache, and rebuilds the pool from
         * them; the fresh lists also refresh the shared Room cache the Home lane reads.
         */
        fun refresh() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    _uiState.update { it.copy(isRefreshing = true) }
                    seeds = runCatching { feedSources.historySeedInputs() }.getOrElse { emptyList() }
                    val batch = seeds.take(BECAUSE_YOU_WATCHED_SEEDS)
                    pool = fetchMerged(batch, forceRefresh = true)
                    seedCursor = batch.size
                    _uiState.update {
                        it.copy(
                            videos = pool.take(BECAUSE_YOU_WATCHED_PAGE_SIZE),
                            isRefreshing = false,
                            hasMore = pool.size > BECAUSE_YOU_WATCHED_PAGE_SIZE || seedCursor < seeds.size,
                        )
                    }
                }
        }

        private suspend fun fetchMerged(
            batch: List<GraphSeedInput>,
            forceRefresh: Boolean,
            excludeIds: Set<String> = emptySet(),
        ): List<Video> {
            if (batch.isEmpty()) return emptyList()
            val seedIds = batch.map { it.id }.toSet()
            val perSeed =
                coroutineScope {
                    batch
                        .map { seed ->
                            async {
                                feedSources.relatedVideos(seed.id, ::cacheFilters, forceRefresh)
                            }
                        }.awaitAll()
                }
            return mergeRelatedRoundRobin(perSeed, seedIds + excludeIds, maxPerSeed = BECAUSE_YOU_WATCHED_PER_SEED)
        }

        private suspend fun cacheFilters(): HomeFeedCacheFilters =
            HomeFeedCacheFilters(exclusions = FlowNeuroEngine.feedExclusions())
    }
