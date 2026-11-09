package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
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
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowPullToRefreshBox
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.card.VideoCardLayout

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
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    val latestState by rememberUpdatedState(uiState)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
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
                uiState.isLoading -> FlowLoadingIndicator()

                uiState.videos.isEmpty() -> {
                    FlowEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title = stringResource(R.string.because_you_watched_empty),
                        subtitle = stringResource(R.string.because_you_watched_empty_body),
                        icon = Icons.Outlined.History,
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding =
                            PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = flowBottomContentPadding(),
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.videos, key = { it.id }) { video ->
                            MediaVideoCard(
                                video = video,
                                layout = VideoCardLayout.Row,
                                onClick = { onVideoClick(video) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item(key = "loading_more") {
                                FlowFeedProgress()
                            }
                        }
                    }
                }
            }
        }
    }
}
