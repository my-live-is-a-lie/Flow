package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.dpadFocusGroup

/** Horizontal strip of the related videos of the most recent watches. */
@Composable
internal fun BecauseYouWatchedShelf(
    lane: BecauseYouWatchedLane,
    onVideoClick: (Video) -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (lane.videos.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.home_because_you_watched),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LazyRow(
            modifier = Modifier.dpadFocusGroup(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(lane.videos, key = { it.id }) { video ->
                MediaVideoCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    modifier =
                        Modifier
                            .width(cardWidth)
                            .testTag("because_you_watched_card"),
                )
            }
        }
    }
}
