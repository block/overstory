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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayHostTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun reorderKeepsStateAndLifecycleWithEachKey() {
    val overlays = mutableStateListOf("first", "second")
    val owners = mutableMapOf<String, LifecycleOwner>()
    var incrementSecond: () -> Unit = {}
    rule.setContent {
      OverlayHost(
        overlays = overlays,
        keyOf = { it },
        coveredContent = { BasicText("covered", Modifier.testTag("covered")) },
      ) { entry, isTopmost ->
        var count by rememberSaveable { mutableIntStateOf(0) }
        owners[entry] = LocalLifecycleOwner.current
        if (entry == "second") incrementSecond = { count++ }
        BasicText("$entry:$count:$isTopmost", Modifier.testTag(entry))
      }
    }
    rule.onNodeWithTag("covered").assertTextEquals("covered")
    val originalOwners = rule.runOnIdle { owners.toMap() }
    rule.runOnIdle {
      assertThat(owners.getValue("first").lifecycle.currentState).isEqualTo(State.CREATED)
      assertThat(owners.getValue("second").lifecycle.currentState).isEqualTo(State.RESUMED)
      incrementSecond()
      overlays.add(overlays.removeAt(0))
    }
    rule.onNodeWithTag("first").assertTextEquals("first:0:true")
    rule.onNodeWithTag("second").assertTextEquals("second:1:false")
    rule.runOnIdle {
      originalOwners.forEach { (key, owner) -> assertThat(owners[key]).isSameInstanceAs(owner) }
      assertThat(owners.getValue("first").lifecycle.currentState).isEqualTo(State.RESUMED)
      assertThat(owners.getValue("second").lifecycle.currentState).isEqualTo(State.CREATED)
    }
  }

  @Test
  fun removalDestroysLifecycleAndDiscardsStateBeforeReAdd() {
    val overlays = mutableStateListOf("overlay")
    lateinit var owner: LifecycleOwner
    var increment: () -> Unit = {}
    rule.setContent {
      OverlayHost(overlays, keyOf = { it }, coveredContent = {}) { _, _ ->
        owner = LocalLifecycleOwner.current
        var count by rememberSaveable { mutableIntStateOf(0) }
        increment = { count++ }
        BasicText("$count", Modifier.testTag("count"))
      }
    }
    val originalOwner = rule.runOnIdle { owner }
    rule.runOnIdle { increment() }
    rule.onNodeWithTag("count").assertTextEquals("1")
    rule.runOnIdle { overlays.clear() }
    rule.runOnIdle {
      assertThat(originalOwner.lifecycle.currentState).isEqualTo(State.DESTROYED)
      overlays.add("overlay")
    }
    rule.onNodeWithTag("count").assertTextEquals("0")
    rule.runOnIdle { assertThat(owner).isNotSameInstanceAs(originalOwner) }
  }

  @Test
  fun restoresEachEntryStateAfterHostRecreation() {
    val restoration = StateRestorationTester(rule)
    val increments = mutableMapOf<String, () -> Unit>()
    restoration.setContent {
      OverlayHost(listOf("first", "second"), keyOf = { it }, coveredContent = {}) { entry, _ ->
        var count by rememberSaveable { mutableIntStateOf(0) }
        increments[entry] = { count++ }
        BasicText("$count", Modifier.testTag(entry))
      }
    }
    rule.runOnIdle {
      increments.getValue("first")()
      repeat(2) { increments.getValue("second")() }
    }
    restoration.emulateSavedInstanceStateRestore()
    rule.onNodeWithTag("first").assertTextEquals("1")
    rule.onNodeWithTag("second").assertTextEquals("2")
  }

  @Test
  fun lifecycleNeverExceedsParentOrEntryCap() {
    val parent = TestLifecycleOwner(initialState = State.CREATED)
    val cap = mutableStateOf(State.STARTED)
    lateinit var owner: LifecycleOwner
    rule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parent) {
        OverlayHost(
          listOf("overlay"),
          keyOf = { it },
          coveredContent = {},
          maxLifecycleState = { _, _ -> cap.value },
        ) { _, _ ->
          owner = LocalLifecycleOwner.current
        }
      }
    }
    rule.runOnIdle {
      assertThat(owner.lifecycle.currentState).isEqualTo(State.CREATED)
      parent.currentState = State.RESUMED
      assertThat(owner.lifecycle.currentState).isEqualTo(State.STARTED)
      cap.value = State.RESUMED
    }
    rule.runOnIdle {
      assertThat(owner.lifecycle.currentState).isEqualTo(State.RESUMED)
      parent.currentState = State.CREATED
      assertThat(owner.lifecycle.currentState).isEqualTo(State.CREATED)
      parent.currentState = State.DESTROYED
      assertThat(owner.lifecycle.currentState).isEqualTo(State.DESTROYED)
    }
  }

  @Test
  fun wrapperAttachesBeforeActivationAndCoveredContentStartsAtItsCap() {
    val wrapperStates = mutableMapOf<LifecycleOwner, State>()
    val renderedStates = mutableMapOf<String, State>()
    rule.setContent {
      OverlayHost(
        listOf("covered", "top"),
        keyOf = { it },
        coveredContent = {},
        overlayContentWrapper = { content ->
          val owner = LocalLifecycleOwner.current
          wrapperStates[owner] = remember(owner) { owner.lifecycle.currentState }
          content()
        },
      ) { entry, _ ->
        val owner = LocalLifecycleOwner.current
        renderedStates[entry] = remember(entry, owner) { owner.lifecycle.currentState }
      }
    }
    rule.runOnIdle {
      assertThat(wrapperStates.values).containsExactly(State.INITIALIZED, State.INITIALIZED)
      assertThat(renderedStates).containsExactly("covered", State.CREATED, "top", State.RESUMED)
    }
  }

  @Test
  fun removingEntryBeforeParentIsCreatedDoesNotStartIt() {
    val parent = TestLifecycleOwner(initialState = State.INITIALIZED)
    val overlays = mutableStateListOf("overlay")
    val events = mutableListOf<Event>()
    lateinit var owner: LifecycleOwner
    rule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parent) {
        OverlayHost(overlays, keyOf = { it }, coveredContent = {}) { _, _ ->
          owner = LocalLifecycleOwner.current
        }
      }
    }
    rule.runOnIdle {
      assertThat(owner.lifecycle.currentState).isEqualTo(State.INITIALIZED)
      owner.lifecycle.addObserver(LifecycleEventObserver { _, event -> events += event })
      overlays.clear()
    }
    rule.runOnIdle {
      assertThat(owner.lifecycle.currentState).isEqualTo(State.DESTROYED)
      assertThat(events).doesNotContain(Event.ON_START)
      assertThat(events).doesNotContain(Event.ON_RESUME)
      assertThat(events).contains(Event.ON_DESTROY)
    }
  }

  @Test
  fun rejectsDuplicateKeys() {
    assertInvalidInput(listOf("same", "same"), "Overlay keys must be unique")
  }

  @Test
  fun rejectsUnsaveableKeys() {
    assertInvalidInput(listOf(Any()), "cannot be saved")
  }

  @Test
  fun rejectsInitializedLifecycleCap() {
    assertInvalidInput(listOf("overlay"), "CREATED, STARTED, or RESUMED", State.INITIALIZED)
  }

  @Test
  fun rejectsDestroyedLifecycleCap() {
    assertInvalidInput(listOf("overlay"), "CREATED, STARTED, or RESUMED", State.DESTROYED)
  }

  private fun assertInvalidInput(entries: List<Any>, message: String, cap: State = State.RESUMED) {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        rule.setContent {
          OverlayHost(
            entries,
            keyOf = { it },
            coveredContent = {},
            maxLifecycleState = { _, _ -> cap },
          ) { _, _ ->
          }
        }
        rule.waitForIdle()
      }
    assertThat(error).hasMessageThat().contains(message)
  }
}
