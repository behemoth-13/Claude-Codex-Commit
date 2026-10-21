package com.bocman.commitcore

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

class CommitConfigurableTest : LightPlatformTestCase() {
    fun testApplyResetAndReopenPreserveCustomModel() {
        val settings = PluginFlavor.settings()
        val original = settings.state
        val configurable = CommitConfigurable()
        try {
            settings.loadState(XmlSerializer.deserialize(Element("State"), original.javaClass))
            settings.executablePath = "/missing/model-test-cli"
            val panel = configurable.createComponent()!!
            configurable.reset()
            assertTrue(panel.preferredSize.height > 0)
            val selector = UIUtil.findComponentOfType(panel, ModelSelector::class.java)!!
            assertFalse(configurable.isModified)
            selector.editor.item = "  future-custom-model  "
            assertTrue(configurable.isModified)
            assertEquals("", settings.model)
            configurable.apply()
            assertEquals("future-custom-model", settings.model)
            assertFalse(configurable.isModified)
            selector.editor.item = "discarded-edit"
            configurable.reset()
            assertEquals("future-custom-model", selector.selectedModel)
            configurable.disposeUIResources()
            val reopened = configurable.createComponent()!!
            configurable.reset()
            assertTrue(reopened.preferredSize.height > 0)
            val reopenedSelector = UIUtil.findComponentOfType(reopened, ModelSelector::class.java)!!
            assertEquals("future-custom-model", reopenedSelector.selectedModel)
            reopenedSelector.selectedModel = ""
            configurable.apply()
            assertEquals("", settings.model)
        } finally {
            configurable.disposeUIResources()
            settings.loadState(original)
        }
    }
}
