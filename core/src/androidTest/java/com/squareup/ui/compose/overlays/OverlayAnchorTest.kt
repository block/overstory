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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayAnchorTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun movingAndResizingUpdatesRootBoundsWithTheSameKey() {
    val registry = RecordingRegistry()
    val offset = mutableIntStateOf(10)
    val size = mutableIntStateOf(20)
    rule.setContent {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier.offset { IntOffset(offset.intValue, 30) }
            .size(size.intValue.dp)
            .overlayAnchor(registry)
            .testTag("anchor")
        )
      }
    }
    val originalKey = rule.runOnIdle { registry.anchors.keys.single() }
    val originalBounds = rule.runOnIdle { registry.anchors.values.single() }
    rule.runOnIdle {
      offset.intValue = 40
      size.intValue = 35
    }
    val expectedBounds = rule.onNodeWithTag("anchor").fetchSemanticsNode().boundsInRoot
    rule.runOnIdle {
      assertThat(registry.anchors).containsExactly(originalKey, expectedBounds)
      assertThat(expectedBounds).isNotEqualTo(originalBounds)
      assertThat(registry.removed).isEmpty()
    }
  }

  @Test
  fun enablingAndDisablingStationaryAnchorControlsRegistration() {
    val registry = RecordingRegistry()
    val enabled = mutableStateOf(false)
    rule.setContent { Box(Modifier.size(20.dp).overlayAnchor(registry, enabled.value)) }
    rule.runOnIdle {
      assertThat(registry.anchors).isEmpty()
      enabled.value = true
    }
    val firstKey = rule.runOnIdle { registry.anchors.keys.single() }
    rule.runOnIdle { enabled.value = false }
    rule.runOnIdle {
      assertThat(registry.anchors).isEmpty()
      assertThat(registry.removed).containsExactly(firstKey)
      enabled.value = true
    }
    rule.runOnIdle { assertThat(registry.anchors.keys.single()).isNotEqualTo(firstKey) }
  }

  @Test
  fun equalRegistryReplacementTransfersRegistrationAndDetachesFromTheNewOwner() {
    val first = RecordingRegistry()
    val second = RecordingRegistry()
    assertThat(first).isEqualTo(second)
    assertThat(first).isNotSameInstanceAs(second)
    val registry = mutableStateOf(first, referentialEqualityPolicy())
    val attached = mutableStateOf(true)
    rule.setContent { if (attached.value) Box(Modifier.size(20.dp).overlayAnchor(registry.value)) }
    val key = rule.runOnIdle { first.anchors.keys.single() }
    rule.runOnIdle { registry.value = second }
    rule.runOnIdle {
      assertThat(first.anchors).isEmpty()
      assertThat(first.removed).containsExactly(key)
      assertThat(second.anchors.keys).containsExactly(key)
      attached.value = false
    }
    rule.runOnIdle {
      assertThat(second.anchors).isEmpty()
      assertThat(second.removed).containsExactly(key)
      assertThat(first.removed).containsExactly(key)
    }
  }

  @Test
  fun removingOneOfTwoAnchorsOnlyRemovesItsRegistration() {
    val registry = RecordingRegistry()
    val showFirst = mutableStateOf(true)
    rule.setContent {
      Box {
        if (showFirst.value) Box(Modifier.size(20.dp).overlayAnchor(registry))
        Box(Modifier.size(40.dp).overlayAnchor(registry))
      }
    }
    val original = rule.runOnIdle { registry.anchors.toMap() }
    rule.runOnIdle {
      assertThat(original).hasSize(2)
      showFirst.value = false
    }
    rule.runOnIdle {
      assertThat(registry.anchors).hasSize(1)
      val remaining = registry.anchors.entries.single()
      assertThat(remaining.value).isEqualTo(original[remaining.key])
      assertThat(registry.removed).containsExactly(original.keys.single { it != remaining.key })
    }
  }

  @Test
  fun reusingLayoutEndsThePreviousRegistration() {
    val registry = RecordingRegistry()
    val item = mutableIntStateOf(1)
    rule.setContent {
      ReusableContent(item.intValue) { Box(Modifier.size(20.dp).overlayAnchor(registry)) }
    }
    val originalKey = rule.runOnIdle { registry.anchors.keys.single() }
    rule.runOnIdle { item.intValue = 2 }
    rule.runOnIdle {
      assertThat(registry.removed).containsExactly(originalKey)
      assertThat(registry.anchors.keys.single()).isNotEqualTo(originalKey)
    }
  }

  private data class RecordingRegistry(val label: String = "registry") : OverlayAnchorRegistry {
    val anchors = linkedMapOf<String, Rect>()
    val removed = mutableListOf<String>()

    override fun registerAnchor(key: String, boundsInRoot: Rect) {
      anchors[key] = boundsInRoot
    }

    override fun unregisterAnchor(key: String) {
      anchors.remove(key)
      removed += key
    }
  }
}
