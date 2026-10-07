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

import android.os.Bundle
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.squareup.ui.compose.overlays.ExperimentalComposeOverlaysApi
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class ViewCompatibleOverlayHostTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun coveredContentFillsHostWithoutForcingOverlaySize() {
    rule.setContent {
      ViewCompatibleOverlayHost(
        overlays = listOf("overlay"),
        keyOf = { it },
        modifier = Modifier.size(width = 240.dp, height = 160.dp),
        coveredContent = { Box(Modifier.testTag("covered")) },
      ) { _, _ ->
        Box(Modifier.size(40.dp).testTag("overlay"))
      }
    }

    rule.onNodeWithTag("covered").assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(160.dp)
    rule.onNodeWithTag("overlay").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun reorderKeepsSaveableStateAndLifecycleWithEachKey() {
    val overlays = mutableStateListOf("first", "second")
    val owners = mutableMapOf<String, LifecycleOwner>()
    var incrementSecond: () -> Unit = {}

    rule.setContent {
      ViewCompatibleOverlayHost(
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
    rule.onNodeWithTag("second").assertTextEquals("second:0:true")
    rule.runOnIdle {
      assertThat(owners.getValue("first").lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
      assertThat(owners.getValue("second").lifecycle.currentState)
        .isEqualTo(Lifecycle.State.RESUMED)
      incrementSecond()
    }

    rule.onNodeWithTag("second").assertTextEquals("second:1:true")
    rule.runOnIdle { overlays.add(overlays.removeAt(0)) }

    rule.onNodeWithTag("first").assertTextEquals("first:0:true")
    rule.onNodeWithTag("second").assertTextEquals("second:1:false")
    rule.runOnIdle {
      assertThat(owners.getValue("first").lifecycle.currentState).isEqualTo(Lifecycle.State.RESUMED)
      assertThat(owners.getValue("second").lifecycle.currentState)
        .isEqualTo(Lifecycle.State.CREATED)
    }
  }

  @Test
  fun restoresSeparateRegistriesBeforeEmbeddedViewsAreCreated() {
    val restoration = StateRestorationTester(rule)
    val restoredValues = mutableMapOf<String, Int?>()
    val statesAtViewCreation = mutableMapOf<String, Lifecycle.State>()
    restoration.setContent {
      ViewCompatibleOverlayHost(
        overlays = listOf("covered", "top"),
        keyOf = { it },
        coveredContent = {},
      ) { entry, _ ->
        val owner = LocalSavedStateRegistryOwner.current
        AndroidView(
          factory = { context ->
            val registry = owner.savedStateRegistry
            restoredValues[entry] = registry.consumeRestoredStateForKey("count")?.getInt("value")
            statesAtViewCreation[entry] = owner.lifecycle.currentState
            registry.registerSavedStateProvider("count") {
              Bundle().apply { putInt("value", if (entry == "covered") 1 else 2) }
            }
            TextView(context)
          }
        )
      }
    }
    rule.runOnIdle {
      assertThat(restoredValues).containsExactly("covered", null, "top", null)
      assertThat(statesAtViewCreation)
        .containsExactly("covered", Lifecycle.State.CREATED, "top", Lifecycle.State.RESUMED)
    }
    restoration.emulateSavedInstanceStateRestore()
    rule.runOnIdle {
      assertThat(restoredValues).containsExactly("covered", 1, "top", 2)
      assertThat(statesAtViewCreation)
        .containsExactly("covered", Lifecycle.State.CREATED, "top", Lifecycle.State.RESUMED)
    }
  }

  @Test
  fun replacingParentRestoresViewsAndReregistersTheirProviders() {
    val restoration = StateRestorationTester(rule)
    var factories = 0
    var increment: () -> Unit = {}
    lateinit var view: TextView
    val parent = mutableStateOf(TestLifecycleOwner(initialState = Lifecycle.State.RESUMED))
    lateinit var registryOwner: SavedStateRegistryOwner
    lateinit var lifecycleOwner: LifecycleOwner
    restoration.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parent.value) {
        ViewCompatibleOverlayHost(listOf("overlay"), keyOf = { it }, coveredContent = {}) { _, _ ->
          var count by rememberSaveable { mutableIntStateOf(0) }
          increment = { count++ }
          BasicText("$count", Modifier.testTag("count"))
          registryOwner = LocalSavedStateRegistryOwner.current
          lifecycleOwner = LocalLifecycleOwner.current
          val owner = registryOwner
          AndroidView(
            factory = { context ->
              factories++
              TextView(context).apply {
                text =
                  owner.savedStateRegistry.consumeRestoredStateForKey("text")?.getString("value")
                    ?: "initial"
                owner.savedStateRegistry.registerSavedStateProvider("text") {
                  Bundle().apply { putString("value", text.toString()) }
                }
                view = this
              }
            }
          )
        }
      }
    }
    val originalOwner = rule.runOnIdle { registryOwner }
    val originalView =
      rule.runOnIdle {
        assertThat(factories).isEqualTo(1)
        increment()
        view.also { it.text = "before replacement" }
      }
    rule.onNodeWithTag("count").assertTextEquals("1")
    rule.runOnIdle { parent.value = TestLifecycleOwner(initialState = Lifecycle.State.STARTED) }
    rule.runOnIdle {
      assertThat(originalOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.DESTROYED)
      assertThat(registryOwner).isNotSameInstanceAs(originalOwner)
      assertThat(registryOwner.lifecycle).isSameInstanceAs(lifecycleOwner.lifecycle)
      assertThat(registryOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.STARTED)
      assertThat(factories).isEqualTo(2)
      assertThat(view).isNotSameInstanceAs(originalView)
      assertThat(view.text.toString()).isEqualTo("before replacement")
      view.text = "after replacement"
      increment()
    }
    rule.onNodeWithTag("count").assertTextEquals("2")
    restoration.emulateSavedInstanceStateRestore()
    rule.runOnIdle {
      assertThat(factories).isEqualTo(3)
      assertThat(view.text.toString()).isEqualTo("after replacement")
    }
    rule.onNodeWithTag("count").assertTextEquals("2")
  }

  @Test
  fun repeatedParentReplacementPreservesNestedStateAndCleansUpProviders() {
    val restoration = StateRestorationTester(rule)
    val parent = mutableStateOf(TestLifecycleOwner(initialState = Lifecycle.State.RESUMED))
    val selected = mutableStateOf("first")
    val increments = mutableMapOf<String, () -> Unit>()
    val registries = mutableMapOf<String, SaveableStateRegistry>()
    val views = mutableMapOf<String, TextView>()
    val factories = mutableMapOf<String, Int>()
    restoration.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parent.value) {
        ViewCompatibleOverlayHost(listOf("covered", "top"), keyOf = { it }, coveredContent = {}) {
          entry,
          _ ->
          registries[entry] = checkNotNull(LocalSaveableStateRegistry.current)
          val holder = rememberSaveableStateHolder()
          holder.SaveableStateProvider(selected.value) {
            var count by rememberSaveable { mutableIntStateOf(0) }
            increments[entry] = { count++ }
            BasicText("$count", Modifier.testTag(entry))
          }
          val owner = LocalSavedStateRegistryOwner.current
          AndroidView(
            factory = { context ->
              factories[entry] = factories.getOrDefault(entry, 0) + 1
              TextView(context).apply {
                text =
                  owner.savedStateRegistry.consumeRestoredStateForKey("text")?.getString("value")
                    ?: entry
                owner.savedStateRegistry.registerSavedStateProvider("text") {
                  Bundle().apply { putString("value", text.toString()) }
                }
                views[entry] = this
              }
            }
          )
        }
      }
    }
    rule.runOnIdle {
      increments.getValue("covered")()
      repeat(2) { increments.getValue("top")() }
    }
    rule.runOnIdle { selected.value = "second" }
    rule.runOnIdle {
      repeat(3) { increments.getValue("covered")() }
      repeat(4) { increments.getValue("top")() }
    }

    repeat(3) { replacement ->
      val previousRegistries = rule.runOnIdle { registries.toMap() }
      val previousViews = rule.runOnIdle { views.toMap() }
      rule.runOnIdle {
        views.forEach { (entry, view) -> view.text = "$entry:$replacement" }
        parent.value = TestLifecycleOwner(initialState = Lifecycle.State.RESUMED)
      }
      rule.onNodeWithTag("covered").assertTextEquals("${3 + replacement}")
      rule.onNodeWithTag("top").assertTextEquals("${4 + replacement}")
      rule.runOnIdle {
        previousRegistries.forEach { (entry, registry) ->
          assertThat(registries.getValue(entry)).isNotSameInstanceAs(registry)
          assertThat(registry.performSave()).isEmpty()
          assertThat(views.getValue(entry)).isNotSameInstanceAs(previousViews.getValue(entry))
          assertThat(views.getValue(entry).text.toString()).isEqualTo("$entry:$replacement")
          assertThat(factories.getValue(entry)).isEqualTo(replacement + 2)
          increments.getValue(entry)()
        }
      }
    }

    rule.runOnIdle { views.forEach { (entry, view) -> view.text = "$entry:final" } }
    restoration.emulateSavedInstanceStateRestore()
    rule.onNodeWithTag("covered").assertTextEquals("6")
    rule.onNodeWithTag("top").assertTextEquals("7")
    rule.runOnIdle {
      views.forEach { (entry, view) ->
        assertThat(view.text.toString()).isEqualTo("$entry:final")
        assertThat(factories.getValue(entry)).isEqualTo(5)
      }
      selected.value = "first"
    }
    rule.onNodeWithTag("covered").assertTextEquals("1")
    rule.onNodeWithTag("top").assertTextEquals("2")
  }
}
