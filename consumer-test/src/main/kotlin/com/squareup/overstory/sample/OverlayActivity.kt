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
package com.squareup.overstory.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.squareup.ui.compose.overlays.ExperimentalComposeOverlaysApi
import com.squareup.ui.compose.overlays.OverlayScrim
import com.squareup.ui.compose.overlays.viewcompat.ViewCompatibleOverlayHost

class OverlayActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { OverlayExample() }
  }
}

@OptIn(ExperimentalComposeOverlaysApi::class)
@Composable
private fun OverlayExample() {
  var showing by rememberSaveable { mutableStateOf(false) }
  ViewCompatibleOverlayHost(
    overlays = if (showing) listOf("dialog") else emptyList(),
    keyOf = { it },
    coveredContent = {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        BasicText(
          "Open overlay",
          Modifier.testTag("open").clickable { showing = true }.padding(24.dp),
        )
      }
    },
  ) { _, isTopmost ->
    OverlayScrim(
      renderScrimBackground = true,
      background = Color.Black.copy(alpha = .3f),
      blockPointerInput = true,
      focusable = isTopmost,
      requestInitialFocus = isTopmost,
      onTapDetected = { showing = false },
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.background(Color.White).padding(24.dp)) {
          var text by rememberSaveable { mutableStateOf("") }
          BasicTextField(text, { text = it }, Modifier.testTag("input"))
          BasicText(
            "Dismiss",
            Modifier.testTag("dismiss").clickable { showing = false }.padding(24.dp),
          )
        }
      }
    }
  }
}
