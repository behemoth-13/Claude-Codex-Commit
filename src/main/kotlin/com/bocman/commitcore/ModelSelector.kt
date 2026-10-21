package com.bocman.commitcore

import com.intellij.openapi.ui.ComboBox
import javax.swing.DefaultComboBoxModel

/** Read the editor directly: Apply must also save text that has not been confirmed with Enter. */
internal class ModelSelector(models: List<String>) : ComboBox<String>() {
    var selectedModel: String
        get() = editor.item?.toString().orEmpty().trim().let { if (it == DEFAULT_LABEL) "" else it }
        set(value) {
            val item = value.ifBlank { DEFAULT_LABEL }
            selectedItem = item
            editor.item = item
        }

    init {
        // Kotlin's isEditable assignment here writes JComboBox's protected field, bypassing editor setup.
        setEditable(true)
        updateModels(models)
        selectedModel = ""
    }

    fun updateModels(models: List<String>) {
        val edited = editor.item?.toString().orEmpty()
        val items = (listOf(DEFAULT_LABEL) + models + listOfNotNull(edited.trim().takeIf { it.isNotEmpty() }))
            .distinct()
        model = DefaultComboBoxModel(items.toTypedArray())
        selectedItem = edited.ifBlank { DEFAULT_LABEL }
        editor.item = edited
    }

    private companion object {
        const val DEFAULT_LABEL = "Default (CLI configuration)"
    }
}
