/*
 * Copyright (C) 2024-2026 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.ui.compose.overlays

import android.view.View
import android.widget.EditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayFocusLayerTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun dismissingCoveringOverlayRestoresFocusedEditor() {
    val overlays = mutableStateOf(emptyList<String>())
    rule.setContent {
      OverlayHost(
        overlays = overlays.value,
        keyOf = { it },
        coveredContent = {
          Box(Modifier.overlayFocusLayer { overlays.value.isNotEmpty() }) {
            BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
          }
        },
      ) { overlay, isTopmost ->
        Box(
          Modifier.size(48.dp)
            .testTag(overlay)
            .overlayFocusTarget(enabled = true, requestInitialFocus = isTopmost)
        )
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { overlays.value = listOf(overlayTag) }
    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()

    rule.runOnIdle { overlays.value = emptyList() }
    rule.onNodeWithTag(editorTag).assertIsFocused()
  }

  @Test
  fun coveredLayerRejectsFocusRequests() {
    val isCovered = mutableStateOf(true)
    rule.setContent {
      Box(Modifier.overlayFocusLayer { isCovered.value }) {
        BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
      }
    }

    rule.onNodeWithTag(editorTag).requestFocus().assertIsNotFocused()
    rule.runOnIdle { isCovered.value = false }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()
  }

  @Test
  fun overlayAttachedWhileCoveredRequestsInitialFocusWhenUncovered() {
    val isCovered = mutableStateOf(true)
    rule.setContent {
      Box(Modifier.overlayFocusLayer { isCovered.value }) {
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = true, requestInitialFocus = true)
        )
      }
    }
    rule.onNodeWithTag(overlayTag).assertIsNotFocused()

    rule.runOnIdle { isCovered.value = false }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
  }

  @Test
  fun uncoveringRestoresEditorInsideOverlayInsteadOfOverlay() {
    val isCovered = mutableStateOf(false)
    rule.setContent {
      Box(
        Modifier.overlayFocusLayer { isCovered.value }
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = true)
      ) {
        BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { isCovered.value = true }
    rule.onNodeWithTag(editorTag).assertIsNotFocused()
    rule.runOnIdle { isCovered.value = false }

    rule.onNodeWithTag(editorTag).assertIsFocused()
  }

  @Test
  fun uncoveringRestoresOverlayFocusedSinceEarlierEditorRestore() {
    val isCovered = mutableStateOf(false)
    rule.setContent {
      Box(
        Modifier.overlayFocusLayer { isCovered.value }
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = true)
      ) {
        BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()
    rule.runOnIdle { isCovered.value = true }
    rule.runOnIdle { isCovered.value = false }
    rule.onNodeWithTag(editorTag).assertIsFocused()
    rule.onNodeWithTag(overlayTag).requestFocus().assertIsFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()

    rule.runOnIdle { isCovered.value = true }
    rule.onNodeWithTag(overlayTag).assertIsNotFocused()
    rule.runOnIdle { isCovered.value = false }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()
  }

  @Test
  fun uncoveringWithoutSavedFocusRequestsOverlayInitialFocus() {
    val isCovered = mutableStateOf(false)
    val showEditor = mutableStateOf(true)
    rule.setContent {
      Box(
        Modifier.size(48.dp)
          .overlayFocusLayer { isCovered.value }
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = true)
      ) {
        if (showEditor.value) {
          BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
        }
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { isCovered.value = true }
    rule.runOnIdle { showEditor.value = false }
    rule.runOnIdle { isCovered.value = false }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
  }

  @Test
  fun nestedLayerStaysCoveredWhileEnclosingLayerIsCovered() {
    val isOuterCovered = mutableStateOf(false)
    val isInnerCovered = mutableStateOf(false)
    rule.setContent {
      Box(Modifier.overlayFocusLayer { isOuterCovered.value }) {
        Box(Modifier.overlayFocusLayer { isInnerCovered.value }) {
          BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
        }
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { isInnerCovered.value = true }
    rule.runOnIdle { isOuterCovered.value = true }
    rule.runOnIdle { isInnerCovered.value = false }
    rule.onNodeWithTag(editorTag).assertIsNotFocused()
    rule.runOnIdle { isOuterCovered.value = false }

    rule.onNodeWithTag(editorTag).assertIsFocused()
  }

  @Test
  fun layerInNestedCompositionReleasesFocusToOverlayAndGetsItBack() {
    val showOverlay = mutableStateOf(false)
    rule.setContent {
      Box {
        AndroidView(
          factory = { context ->
            ComposeView(context).apply {
              setContent {
                Box(Modifier.overlayFocusLayer { showOverlay.value }) {
                  BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
                }
              }
            }
          }
        )
        if (showOverlay.value) {
          Box(
            Modifier.size(48.dp)
              .testTag(overlayTag)
              .overlayFocusTarget(enabled = true, requestInitialFocus = true)
          )
        }
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { showOverlay.value = true }
    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()

    rule.runOnIdle { showOverlay.value = false }
    rule.onNodeWithTag(editorTag).assertIsFocused()
  }

  @Test
  fun coveringLayerWithoutOverlayFocusTargetClearsFocus() {
    val isCovered = mutableStateOf(false)
    rule.setContent {
      Box(Modifier.overlayFocusLayer { isCovered.value }) {
        BasicTextField(rememberTextFieldState(), Modifier.testTag(editorTag))
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()

    rule.runOnIdle { isCovered.value = true }
    rule.onNodeWithTag(editorTag).assertIsNotFocused()

    rule.runOnIdle { isCovered.value = false }
    rule.onNodeWithTag(editorTag).assertIsFocused()
  }

  @Test
  fun embeddedViewKeepsFocusWhileCoverIsApplied() {
    val isCovered = mutableStateOf(false)
    lateinit var editText: EditText
    var editTextHadFocusWhenCovered: Boolean? = null
    rule.setContent {
      val isCoveredNow = isCovered.value
      Box(Modifier.overlayFocusLayer { isCoveredNow }) {
        AndroidView(factory = { context -> EditText(context).also { editText = it } })
      }
      AndroidView(
        factory = { context -> View(context) },
        update = { if (isCoveredNow) editTextHadFocusWhenCovered = editText.hasFocus() },
      )
    }
    rule.runOnIdle { assertThat(editText.requestFocus()).isTrue() }

    rule.runOnIdle { isCovered.value = true }
    rule.runOnIdle {
      assertThat(editTextHadFocusWhenCovered).isTrue()
      assertThat(editText.hasFocus()).isFalse()
    }
  }

  @Test
  fun embeddedViewKeepsFocusWhileSnapshotCoverageIsApplied() {
    val showOverlay = mutableStateOf(false)
    lateinit var editText: EditText
    var editTextHadFocusWhenCovered: Boolean? = null
    rule.setContent {
      Box(Modifier.overlayFocusLayer { showOverlay.value }) {
        AndroidView(factory = { context -> EditText(context).also { editText = it } })
      }
      val isCoveredNow = showOverlay.value
      if (isCoveredNow) {
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = true, requestInitialFocus = true)
        )
      }
      AndroidView(
        factory = { context -> View(context) },
        update = { if (isCoveredNow) editTextHadFocusWhenCovered = editText.hasFocus() },
      )
    }
    rule.runOnIdle { assertThat(editText.requestFocus()).isTrue() }

    rule.runOnIdle { showOverlay.value = true }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.runOnIdle {
      assertThat(editTextHadFocusWhenCovered).isTrue()
      assertThat(editText.hasFocus()).isFalse()
    }
  }

  @Test
  fun snapshotCoverageRestoresFocusAfterCoveringOverlayIsRemoved() {
    val showOverlay = mutableStateOf(false)
    var isOverlayAttached = false
    var editorFocusedWhileOverlayAttached: Boolean? = null
    rule.setContent {
      Box(Modifier.overlayFocusLayer { showOverlay.value }) {
        BasicTextField(
          rememberTextFieldState(),
          Modifier.testTag(editorTag).onFocusChanged {
            if (it.isFocused) editorFocusedWhileOverlayAttached = isOverlayAttached
          },
        )
      }
      if (showOverlay.value) {
        DisposableEffect(Unit) {
          isOverlayAttached = true
          onDispose { isOverlayAttached = false }
        }
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = true, requestInitialFocus = true)
        )
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()
    rule.runOnIdle { showOverlay.value = true }
    rule.onNodeWithTag(overlayTag).assertIsFocused()

    rule.runOnIdle { showOverlay.value = false }

    rule.onNodeWithTag(editorTag).assertIsFocused()
    rule.runOnIdle { assertThat(editorFocusedWhileOverlayAttached).isFalse() }
  }

  @Test
  fun coveringOverlayTakesFocusOnceCoveredContentReleasesCapturedFocus() {
    val showOverlay = mutableStateOf(false)
    val editorFocusRequester = FocusRequester()
    rule.setContent {
      // Coverage changes while the frame applies, after the overlay requests initial focus.
      val isCoveredNow = showOverlay.value
      Box(Modifier.overlayFocusLayer { isCoveredNow }) {
        BasicTextField(
          rememberTextFieldState(),
          Modifier.testTag(editorTag).focusRequester(editorFocusRequester),
        )
      }
      if (isCoveredNow) {
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = true, requestInitialFocus = true)
        )
      }
    }
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()
    rule.runOnIdle { assertThat(editorFocusRequester.captureFocus()).isTrue() }

    rule.runOnIdle { showOverlay.value = true }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()
  }

  @Test
  fun coveredEmbeddedViewCannotKeepFocusFromCoveringOverlay() {
    val showOverlay = mutableStateOf(false)
    lateinit var editText: EditText
    rule.setContent {
      Box(Modifier.overlayFocusLayer { showOverlay.value }) {
        AndroidView(factory = { context -> EditText(context).also { editText = it } })
      }
      if (showOverlay.value) {
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = true, requestInitialFocus = true)
        )
      }
    }
    rule.runOnIdle { showOverlay.value = true }
    rule.onNodeWithTag(overlayTag).assertIsFocused()

    rule.runOnIdle { editText.requestFocus() }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.runOnIdle { assertThat(editText.hasFocus()).isFalse() }
  }

  @Test
  fun releasedFocusGoesToCoveringOverlayRatherThanHostBeforeIt() {
    releasedFocusGoesToCoveringOverlayRatherThanIndependentHost(isIndependentHostFirst = true)
  }

  @Test
  fun releasedFocusGoesToCoveringOverlayRatherThanHostAfterIt() {
    releasedFocusGoesToCoveringOverlayRatherThanIndependentHost(isIndependentHostFirst = false)
  }

  private fun releasedFocusGoesToCoveringOverlayRatherThanIndependentHost(
    isIndependentHostFirst: Boolean
  ) {
    val showOverlay = mutableStateOf(false)
    val editorFocusRequester = FocusRequester()
    rule.setContent {
      val independentHost: @Composable () -> Unit = {
        AndroidView(
          factory = { context ->
            ComposeView(context).apply {
              setContent {
                Box(
                  Modifier.size(48.dp)
                    .testTag(independentOverlayTag)
                    .overlayFocusTarget(enabled = true, requestInitialFocus = true)
                )
              }
            }
          }
        )
      }
      Column {
        if (isIndependentHostFirst) independentHost()
        AndroidView(
          factory = { context ->
            ComposeView(context).apply {
              setContent {
                // Coverage changes while the frame applies, after the overlay requests focus.
                val isCoveredNow = showOverlay.value
                Box(Modifier.overlayFocusLayer { isCoveredNow }) {
                  BasicTextField(
                    rememberTextFieldState(),
                    Modifier.testTag(editorTag).focusRequester(editorFocusRequester),
                  )
                }
                if (isCoveredNow) {
                  Box(
                    Modifier.size(48.dp)
                      .testTag(overlayTag)
                      .overlayFocusTarget(enabled = true, requestInitialFocus = true)
                  )
                }
              }
            }
          }
        )
        if (!isIndependentHostFirst) independentHost()
      }
    }
    rule.onNodeWithTag(independentOverlayTag).assertIsFocused()
    rule.onNodeWithTag(editorTag).requestFocus().assertIsFocused()
    rule.runOnIdle { assertThat(editorFocusRequester.captureFocus()).isTrue() }

    rule.runOnIdle { showOverlay.value = true }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.onNodeWithTag(independentOverlayTag).assertIsNotFocused()
    rule.onNodeWithTag(editorTag).assertIsNotFocused()
  }

  private companion object {
    const val overlayTag = "overlay"
    const val editorTag = "editor"
    const val independentOverlayTag = "independentOverlay"
  }
}
