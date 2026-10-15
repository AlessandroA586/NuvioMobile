package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.compose_player_panel_sources
import nuvio.composeapp.generated.resources.compose_player_playing
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerSourcesPanel(
    visible: Boolean,
    streamsUiState: StreamsUiState,
    contentTitle: String,
    currentSeason: Int?,
    currentEpisode: Int?,
    currentEpisodeTitle: String?,
    currentStreamUrl: String?,
    currentStreamName: String?,
    currentStreamSubtitle: String? = null,
    currentTorrentInfoHash: String? = null,
    currentTorrentFileIdx: Int? = null,
    currentTorrentFilename: String? = null,
    currentProviderName: String? = null,
    onFilterSelected: (String?) -> Unit,
    onStreamSelected: (StreamItem) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val contentLabel = if (currentSeason != null && currentEpisode != null) {
        buildString {
            append(stringResource(Res.string.compose_player_episode_code_full, currentSeason, currentEpisode))
            currentEpisodeTitle?.takeIf { it.isNotBlank() }?.let {
                append(" • ")
                append(it)
            }
        }
    } else {
        contentTitle
    }

    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.compose_player_panel_sources),
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = contentLabel,
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(16.dp))

            PlayerProviderFilterRow(
                streamsUiState = streamsUiState,
                onFilterSelected = onFilterSelected,
                onRefresh = onReload,
            )

            Spacer(Modifier.height(16.dp))

            PlayerStreamList(
                streamsUiState = streamsUiState,
                onStreamSelected = onStreamSelected,
                modifier = Modifier.weight(1f),
                currentStreamUrl = currentStreamUrl,
                currentStreamName = currentStreamName,
                currentStreamSubtitle = currentStreamSubtitle,
                currentTorrentInfoHash = currentTorrentInfoHash,
                currentTorrentFileIdx = currentTorrentFileIdx,
                currentTorrentFilename = currentTorrentFilename,
                currentProviderName = currentProviderName,
                currentLabel = stringResource(Res.string.compose_player_playing),
            )
        }
    }
}

internal fun List<StreamItem>.stablePlayerKeys(): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return map { stream ->
        val base = listOf(
            stream.addonId,
            stream.infoHash ?: stream.clientResolve?.infoHash ?: stream.url ?: stream.externalUrl ?: stream.streamLabel,
            stream.fileIdx ?: stream.clientResolve?.fileIdx ?: -1,
        ).joinToString("::")
        val count = occurrences[base] ?: 0
        occurrences[base] = count + 1
        "$base::$count"
    }
}

internal fun StreamItem.isCurrentPlayerStream(
    currentUrl: String?,
    currentName: String?,
    currentSubtitle: String? = null,
    currentInfoHash: String? = null,
    currentFileIdx: Int? = null,
    currentFilename: String? = null,
    currentProvider: String? = null,
): Boolean {
    // 1. Torrent / Debrid InfoHash match
    val effectiveHash = infoHash ?: clientResolve?.infoHash
    val effectiveIdx = fileIdx ?: clientResolve?.fileIdx
    if (!currentInfoHash.isNullOrBlank() && !effectiveHash.isNullOrBlank()) {
        if (effectiveHash.equals(currentInfoHash, ignoreCase = true)) {
            if (currentFileIdx == null || effectiveIdx == null || effectiveIdx == currentFileIdx) {
                return true
            }
        }
    }

    // 2. Direct URL match
    if (!currentUrl.isNullOrBlank() && playableDirectUrl == currentUrl) return true

    // 3. Normalized helpers
    fun norm(s: String?): String = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

    val sameProvider = currentProvider.isNullOrBlank() ||
        addonName.equals(currentProvider, ignoreCase = true) ||
        (sourceName != null && sourceName.equals(currentProvider, ignoreCase = true))

    val targetNameNorm = norm(currentName).takeIf { it.length >= 3 }
    val streamNameNorm = norm(streamLabel).takeIf { it.length >= 3 } ?: norm(name).takeIf { it.length >= 3 }

    val targetSubNorm = norm(currentSubtitle).takeIf { it.length >= 5 }
    val streamSubNorm = norm(description ?: title).takeIf { it.length >= 5 }
    val targetFileNorm = norm(currentFilename).takeIf { it.length >= 5 }
    val streamFileNorm = norm(behaviorHints.filename).takeIf { it.length >= 5 }

    // 4. Exact filename match (very high confidence)
    if (targetFileNorm != null && streamFileNorm != null && targetFileNorm == streamFileNorm) {
        return true
    }

    // 5. Match by exact stream name + provider
    if (sameProvider && targetNameNorm != null && streamNameNorm != null && targetNameNorm == streamNameNorm) {
        if (targetSubNorm == null || streamSubNorm == null || targetSubNorm == streamSubNorm) {
            return true
        }
    }

    // 6. Match by exact normalized release description
    if (targetSubNorm != null && streamSubNorm != null && targetSubNorm == streamSubNorm) {
        if (sameProvider || targetSubNorm.length >= 20) {
            return true
        }
    }

    return false
}
