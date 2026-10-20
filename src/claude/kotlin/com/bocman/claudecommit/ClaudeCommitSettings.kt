package com.bocman.claudecommit

import com.bocman.commitcore.CommitSettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ClaudeCommitSettings", storages = [Storage("claude-commit.xml")])
class ClaudeCommitSettings : CommitSettings(), PersistentStateComponent<ClaudeCommitSettings.State> {
    class State : CommitSettings.State() {
        @JvmField
        var claudePath: String = ""
    }

    private var myState = State()
    override val settingsState: State get() = myState

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    override var executablePath: String
        get() = myState.claudePath
        set(value) {
            myState.claudePath = value
        }

    var claudePath: String
        get() = executablePath
        set(value) {
            executablePath = value
        }
}
