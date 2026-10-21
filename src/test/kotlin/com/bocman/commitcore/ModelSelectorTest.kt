package com.bocman.commitcore

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import javax.swing.UIManager
import javax.swing.plaf.InsetsUIResource

class ModelSelectorTest : LightPlatformTestCase() {
    fun testEditableModelSelectorCanBeLaidOutWithIdeTheme() {
        val previousUI = UIManager.put("ComboBoxUI", DarculaComboBoxUI::class.java.name)
        // The headless test theme does not supply Darcula's required padding default.
        val previousPadding = UIManager.put("ComboBox.padding", InsetsUIResource(0, 0, 0, 0))
        try {
            val selector = ModelSelector(listOf("known-model"))
            assertTrue(selector.ui is DarculaComboBoxUI)
            assertTrue(selector.preferredSize.width > 0)
            assertTrue(selector.minimumSize.height > 0)
            assertSame(selector, selector.editor.editorComponent.parent)
            selector.setSize(selector.preferredSize)
            selector.doLayout()
            assertTrue(selector.editor.editorComponent.width > 0)
            selector.editor.item = "custom-model"
            selector.updateModels(listOf("new-model"))
            assertTrue(selector.preferredSize.width > 0)
            assertEquals("custom-model", selector.selectedModel)
        } finally {
            UIManager.put("ComboBoxUI", previousUI)
            UIManager.put("ComboBox.padding", previousPadding)
        }
    }

    fun testDefaultAndUncommittedCustomText() {
        val selector = ModelSelector(listOf("known-model"))
        assertEquals("", selector.selectedModel)
        assertEquals("Default (CLI configuration)", selector.editor.item)
        selector.editor.item = "  future-model  "
        assertEquals("future-model", selector.selectedModel)
        selector.selectedModel = ""
        assertEquals("", selector.selectedModel)
        selector.editor.item = "  "
        assertEquals("", selector.selectedModel)
    }

    fun testRefreshPreservesTypedAndSavedModels() {
        val selector = ModelSelector(listOf("old-model"))
        selector.selectedModel = "saved-custom-model"
        selector.updateModels(listOf("new-model"))
        assertEquals("saved-custom-model", selector.selectedModel)
        selector.editor.item = "  still-typing  "
        selector.updateModels(listOf("another-model"))
        assertEquals("  still-typing  ", selector.editor.item)
        assertEquals("still-typing", selector.selectedModel)
        selector.selectedModel = ""
        selector.updateModels(listOf("latest-model"))
        assertEquals("", selector.selectedModel)
    }
}
