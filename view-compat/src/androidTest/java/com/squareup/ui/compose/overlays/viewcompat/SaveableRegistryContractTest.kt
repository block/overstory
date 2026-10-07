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
package com.squareup.ui.compose.overlays.viewcompat

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Characterizes saved-state registry behavior in the pinned Compose runtime. */
class SaveableRegistryContractTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun changingRegistryMovesProviderWithoutConsumingRestoredStateAgain() {
    val first = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
    val registry = mutableStateOf(first)
    val visible = mutableStateOf(true)
    rule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides registry.value) {
        if (visible.value) {
          val count = rememberSaveable { 7 }
          BasicText("$count", Modifier.testTag("count"))
        }
      }
    }
    rule.onNodeWithTag("count").assertTextEquals("7")
    val saved = rule.runOnIdle { first.performSave() }
    val key = saved.keys.single()
    val second = SaveableStateRegistry(mapOf(key to listOf(99)), canBeSaved = { true })
    rule.runOnIdle { registry.value = second }
    rule.onNodeWithTag("count").assertTextEquals("7")
    rule.runOnIdle {
      assertThat(first.performSave()).isEmpty()
      assertThat(second.performSave()).containsExactly(key, listOf(7))
      // Restoration belongs to initial creation; a registry change only transfers registration.
      assertThat(second.consumeRestored(key)).isEqualTo(99)
      assertThat(second.consumeRestored(key)).isNull()
      visible.value = false
    }
    rule.runOnIdle { assertThat(second.performSave()).isEmpty() }
  }
}
