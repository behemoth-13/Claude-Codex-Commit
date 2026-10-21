package com.bocman.codexcommit

import com.bocman.commitcore.CommitSettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "CodexCommitSettings", storages = [Storage("codex-commit.xml")])
class CodexCommitSettings : CommitSettings(), PersistentStateComponent<CodexCommitSettings.State> {
    class State : CommitSettings.State() {
        @JvmField
        var codexPath: String = ""
    }

    private var myState = State()
    override val settingsState: State get() = myState

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    override var executablePath: String
        get() = myState.codexPath
        set(value) {
            myState.codexPath = value
        }
}
