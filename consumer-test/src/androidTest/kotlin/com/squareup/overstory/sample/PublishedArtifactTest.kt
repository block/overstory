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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/** Runs in a separate Gradle build whose only Overstory input is the published Maven artifact. */
class PublishedArtifactTest {
  @get:Rule val rule = createAndroidComposeRule<OverlayActivity>()

  @Test
  fun artifactPreservesOverlayStateAcrossActivityRecreation() {
    rule.onNodeWithTag("open").performClick()
    rule.onNodeWithTag("input").performTextInput("Preserved")
    rule.activityRule.scenario.recreate()
    rule.onNodeWithTag("input").assertTextEquals("Preserved")
    rule.onNodeWithTag("dismiss").performClick()
    rule.onNodeWithTag("input").assertDoesNotExist()
    rule.onNodeWithTag("open").assertIsDisplayed()
  }
}
