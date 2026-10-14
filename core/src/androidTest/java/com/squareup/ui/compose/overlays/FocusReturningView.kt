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

import android.content.Context
import android.view.View

/**
 * Asks [hostView] for focus as this view leaves the window, as Android does when it removes the
 * view holding focus. That happens while Compose detaches the content around this view, after its
 * modifier nodes ran `onDetach` but before they are marked detached.
 */
internal class FocusReturningView(context: Context, private val hostView: View) : View(context) {
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    hostView.requestFocus()
  }
}
