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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayFocusTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun requestingInitialFocusClaimsFocusWhenContentHasNone() {
    val requestInitialFocus = mutableStateOf(false)
    rule.setContent {
      Box(
        Modifier.size(48.dp)
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = requestInitialFocus.value)
      )
    }

    rule.runOnIdle { requestInitialFocus.value = true }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
  }

  @Test
  fun requestingInitialFocusPreservesFocusedContent() {
    val requestInitialFocus = mutableStateOf(false)
    rule.setContent {
      val text = remember { mutableStateOf("") }
      Box(
        Modifier.size(48.dp)
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = requestInitialFocus.value)
      ) {
        BasicTextField(
          value = text.value,
          onValueChange = { text.value = it },
          modifier = Modifier.testTag(contentTag),
        )
      }
    }

    rule.onNodeWithTag(contentTag).requestFocus().assertIsFocused()
    rule.runOnIdle { requestInitialFocus.value = true }

    rule.onNodeWithTag(contentTag).assertIsFocused()
  }

  @Test
  fun enabledWithoutInitialFocusRequestRemainsFocusable() {
    rule.setContent {
      Box(
        Modifier.size(48.dp)
          .testTag(overlayTag)
          .overlayFocusTarget(enabled = true, requestInitialFocus = false)
      )
    }

    rule.onNodeWithTag(overlayTag).requestFocus().assertIsFocused()
  }

  @Test
  fun reEnablingWithInitialFocusRequestReclaimsFocus() {
    val enabled = mutableStateOf(true)
    rule.setContent {
      Column {
        Box(
          Modifier.size(48.dp)
            .testTag(overlayTag)
            .overlayFocusTarget(enabled = enabled.value, requestInitialFocus = true)
        )
        Box(Modifier.size(48.dp).testTag(externalTag).focusable())
      }
    }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
    rule.runOnIdle { enabled.value = false }
    rule.onNodeWithTag(externalTag).requestFocus().assertIsFocused()
    rule.runOnIdle { enabled.value = true }

    rule.onNodeWithTag(overlayTag).assertIsFocused()
  }

  @Test
  fun removedTargetDoesNotTakeFocusWhileItDetaches() {
    val isShown = mutableStateOf(true)
    lateinit var hostView: View
    rule.setContent {
      hostView = LocalView.current
      Column {
        if (isShown.value) {
          Box(
            Modifier.size(48.dp)
              .testTag(overlayTag)
              .overlayFocusTarget(enabled = true, requestInitialFocus = false)
          ) {
            AndroidView({ FocusReturningView(it, hostView) })
          }
        }
        Box(Modifier.size(48.dp).testTag(externalTag).focusable())
      }
    }

    // Leave no view focused, so that the host searches its content for a target to focus.
    rule.runOnIdle {
      hostView.clearFocus()
      isShown.value = false
    }

    rule.onNodeWithTag(externalTag).requestFocus().assertIsFocused()
  }

  @Test
  fun contentOfRemovedTargetDoesNotTakeFocusWhileItDetaches() {
    val isShown = mutableStateOf(true)
    lateinit var hostView: View
    rule.setContent {
      hostView = LocalView.current
      Column {
        if (isShown.value) {
          Box(Modifier.overlayFocusTarget(enabled = true, requestInitialFocus = false)) {
            Box(Modifier.size(48.dp).testTag(contentTag).focusable()) {
              AndroidView({ FocusReturningView(it, hostView) })
            }
          }
        }
        Box(Modifier.size(48.dp).testTag(externalTag).focusable())
      }
    }

    // Leave no view focused, so that the host searches its content for a target to focus.
    rule.runOnIdle {
      hostView.clearFocus()
      isShown.value = false
    }

    rule.onNodeWithTag(externalTag).requestFocus().assertIsFocused()
  }

  private companion object {
    const val overlayTag = "overlay"
    const val contentTag = "overlay content"
    const val externalTag = "external"
  }
}
