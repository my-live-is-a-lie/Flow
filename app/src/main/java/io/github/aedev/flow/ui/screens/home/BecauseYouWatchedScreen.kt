package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FeedGridTopPadding
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowPullToRefreshBox
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.card.VideoCardLayout
import io.github.aedev.flow.ui.components.shared.rememberFeedGridPlan

private const val LOAD_MORE_THRESHOLD = 5

/** The full list behind the Because-you-watched lane, loading more as the viewer scrolls. */
@Composable
fun BecauseYouWatchedScreen(
    onVideoClick: (Video) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BecauseYouWatchedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    val latestState by rememberUpdatedState(uiState)
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { index ->
            val state = latestState
            if (index != null && state.hasMore && !state.isLoading && !state.isLoadingMore &&
                index >= state.videos.size - LOAD_MORE_THRESHOLD
            ) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.home_because_you_watched),
                onBack = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector =
                                if (uiState.isFullWidthView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(R.string.toggle_view_mode),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        FlowPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    FlowLoadingIndicator()
                }

                uiState.videos.isEmpty() -> {
                    FlowEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title = stringResource(R.string.because_you_watched_empty),
                        subtitle = stringResource(R.string.because_you_watched_empty_body),
                        icon = Icons.Outlined.History,
                    )
                }

                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val feedLayout = rememberFeedGridLayout(maxWidth)
                        val plan =
                            rememberFeedGridPlan(
                                layout = feedLayout,
                                listMode = uiState.isFullWidthView,
                                itemCount = uiState.videos.size,
                                spansOwnRow = { false },
                                includeLastRun = true,
                                itemsKey = uiState.videos,
                                compactRowSpacing = 12.dp,
                            )
                        LazyVerticalGrid(
                            columns = plan.cells,
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                plan.contentPadding(
                                    top = FeedGridTopPadding,
                                    bottom = flowBottomContentPadding(),
                                ),
                            verticalArrangement = Arrangement.spacedBy(plan.rowSpacing),
                        ) {
                            itemsIndexed(
                                items = uiState.videos,
                                key = { _, video -> video.id },
                                span = { index, _ ->
                                    plan.span(index, spansOwnRow = false, maxLineSpan = maxLineSpan)
                                },
                            ) { index, video ->
                                MediaVideoCard(
                                    video = video,
                                    layout =
                                        if (plan.isListCard(index)) VideoCardLayout.Row else VideoCardLayout.Stacked,
                                    onClick = { onVideoClick(video) },
                                    thumbnailWidth = plan.listThumbnailWidth,
                                )
                            }
                            if (uiState.isLoadingMore) {
                                item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                                    FlowFeedProgress()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
