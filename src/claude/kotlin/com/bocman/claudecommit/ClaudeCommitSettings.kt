package com.bocman.claudecommit

import com.bocman.commitcore.CommitSettings
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ClaudeCommitSettings", storages = [Storage("claude-commit.xml")])
class ClaudeCommitSettings : CommitSettings()
