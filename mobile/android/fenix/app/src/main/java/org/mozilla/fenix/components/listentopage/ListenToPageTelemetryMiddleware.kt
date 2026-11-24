/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.listentopage

import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.ListenState
import mozilla.components.feature.listentopage.PlaybackPhase
import mozilla.components.feature.listentopage.PlaybackState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/** [Middleware] that records telemetry for the Listen to Page player. */
internal class ListenToPageTelemetryMiddleware : Middleware<ListenState, ListenAction> {

    // Record once per session, since PlaybackStarted is dispatched every chunk
    private var playbackStartedRecorded = false

    // Record once per session, since PlaybackEnded is dispatched each replay or speed change after the article ends
    private var playbackEndedRecorded = false

    override fun invoke(
        store: Store<ListenState, ListenAction>,
        next: (ListenAction) -> Unit,
        action: ListenAction,
    ) {
        handleListenToPageAction(store, action)
        next(action)
    }

    private fun handleListenToPageAction(store: Store<ListenState, ListenAction>, action: ListenAction) {
        when (action) {
            is ListenAction.Session -> handleSession(action)
            is ListenAction.Content -> handleContent(action)
            is ListenAction.Playback -> handlePlayback(store, action)
            is ListenAction.Controls -> handleControls(store, action)
            is ListenAction.Voices -> handleVoices(store, action)
            is ListenAction.Synthesis.SynthesisFailed -> Unit
            else -> Unit
        }
    }

    private fun handleSession(action: ListenAction.Session) {
        playbackStartedRecorded = false
        playbackEndedRecorded = false
        when (action) {
            is ListenAction.Session.ListenRequested -> Unit
            is ListenAction.Session.StopRequested -> {
                val source =
                    when (action.source) {
                        ListenAction.Session.StopSource.CloseButton -> CLOSE_BUTTON
                        ListenAction.Session.StopSource.TabClosed -> TAB_CLOSED
                    }
            }
        }
    }

    private fun handleContent(action: ListenAction.Content) {
        when (action) {
            is ListenAction.Content.ContentReady -> Unit

            ListenAction.Content.ContentUnavailable -> Unit
        }
    }

    private fun handlePlayback(store: Store<ListenState, ListenAction>, action: ListenAction.Playback) {
        when (action) {
            is ListenAction.Playback.PlaybackStarted ->
                if (!playbackStartedRecorded) {
                    playbackStartedRecorded = true
                }

            ListenAction.Playback.PlaybackEnded ->
                if (!playbackEndedRecorded) {
                    playbackEndedRecorded = true
                }

            ListenAction.Playback.PlaybackFailed -> Unit
            is ListenAction.Playback.SeekRequested -> {
                val direction = if (action.fraction >= store.state.articleProgress.fraction) FORWARD else BACKWARD
            }

            else -> Unit
        }
    }

    private fun handleControls(store: Store<ListenState, ListenAction>, action: ListenAction.Controls) {
        when (action) {
            ListenAction.Controls.PlayPauseClicked ->
                if (store.state.playbackState.isPlayingOrBuffering) {
                } else {
                }

            ListenAction.Controls.RewindClicked -> Unit
            ListenAction.Controls.ForwardClicked -> Unit
            ListenAction.Controls.VoicesClicked -> Unit
            ListenAction.Controls.VoicesDismissed -> Unit
            ListenAction.Controls.PlaybackSpeedClicked -> Unit
            ListenAction.Controls.PlaybackSpeedDismissed -> Unit
            is ListenAction.Controls.PlaybackSpeedSelected -> Unit
        }
    }

    private fun handleVoices(store: Store<ListenState, ListenAction>, action: ListenAction.Voices) {
        when (action) {
            is ListenAction.Voices.VoiceSelected -> {
                val isDefault = action.voice == store.state.voiceState.availableVoices.firstOrNull()
                val source =
                    when (action.source) {
                        ListenAction.Voices.VoiceSource.UserSelected -> USER_SELECTED
                        ListenAction.Voices.VoiceSource.Fallback -> FALLBACK
                    }
            }

            ListenAction.Voices.NoOfflineVoicesAvailable -> {
                val language = store.state.languageTag.orEmpty()
            }

            is ListenAction.Voices.AvailableVoicesLoaded -> Unit
        }
    }
}

private const val FORWARD = "forward"
private const val BACKWARD = "backward"
private const val CLOSE_BUTTON = "close_button"
private const val TAB_CLOSED = "tab_closed"
private const val USER_SELECTED = "user_selected"
private const val FALLBACK = "fallback"

private val PlaybackState.isPlayingOrBuffering: Boolean
    get() = phase == PlaybackPhase.Playing || phase == PlaybackPhase.Buffering
