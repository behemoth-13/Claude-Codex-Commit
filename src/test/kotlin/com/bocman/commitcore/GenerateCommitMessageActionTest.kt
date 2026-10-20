package com.bocman.commitcore

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.BinaryContentRevision
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import git4idea.repo.GitRepositoryManager
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.BitSet
import java.util.concurrent.Callable

class GenerateCommitMessageActionTest : HeavyPlatformTestCase() {
    fun testExcludedHunkIsNotIncludedInPromptDiff() {
        val before = "first original\n" + (1..10).joinToString("") { "context $it\n" } + "last original\n"
        val after = before.replace("first original", "first selected")
            .replace("last original", "last excluded")
        withTrackedChange(before, after) { tracker, change ->
            val ranges = requireNotNull(tracker.getRanges())
            assertEquals("Fixture must have two separate hunks", 2, ranges.size)
            tracker.setExcludedFromCommit(ranges.last(), true)

            val diff = diff(listOf(change))
            assertTrue("Selected hunk must be present:\n$diff", diff.contains("+first selected"))
            assertFalse("Excluded hunk must not be sent to Claude:\n$diff", diff.contains("last excluded"))
        }
    }

    fun testSelectedLineWithinHunk() {
        withTrackedChange("before\nafter\n", "before\nselected line\nexcluded line\nafter\n") { tracker, change ->
            assertEquals(1, requireNotNull(tracker.getRanges()).size)
            tracker.setPartiallyExcludedFromCommit(BitSet().apply { set(2) }, Side.RIGHT, true)

            val diff = diff(listOf(change))
            assertTrue("Selected line must be present:\n$diff", diff.contains("+selected line"))
            assertFalse("Excluded line must not be sent to Claude:\n$diff", diff.contains("excluded line"))
        }
    }

    fun testAllHunksExcludedProducesEmptyDiff() {
        withTrackedChange("original\n", "excluded\n") { tracker, change ->
            tracker.setExcludedFromCommit(true)
            assertEquals("", diff(listOf(change)))
        }
    }

    fun testEmptySelectionDoesNotFallBackToWholeRepository() {
        withTrackedChange("original\n", "unselected\n") { _, _ ->
            assertEquals("", diff(emptyList()))
        }
    }

    fun testWholeFileSelection() {
        withTrackedChange("original\n", "selected\n") { _, change ->
            val diff = diff(listOf(change))
            assertTrue(diff, diff.contains("-original"))
            assertTrue(diff, diff.contains("+selected"))
        }
    }

    fun testBinaryAdditionIsIdentifiedAsAddition() {
        val diff = diff(listOf(Change(null, binaryRevision())))
        assertTrue(diff, diff.contains("Binary files /dev/null and icon.png differ"))
    }

    fun testBinaryDeletionIsIdentifiedAsDeletion() {
        val diff = diff(listOf(Change(binaryRevision(), null)))
        assertTrue(diff, diff.contains("Binary files icon.png and /dev/null differ"))
    }

    private fun binaryRevision(): BinaryContentRevision {
        val path = VcsUtil.getFilePath(orCreateProjectBaseDir.path + "/icon.png", false)
        return object : BinaryContentRevision {
            override fun getFile() = path
            override fun getRevisionNumber() = VcsRevisionNumber.NULL
            override fun getContent(): String? = null
            override fun getBinaryContent() = byteArrayOf(0, 1, 2, 3)
        }
    }

    private fun withTrackedChange(
        before: String,
        after: String,
        check: (PartialLocalLineStatusTracker, Change) -> Unit
    ) {
        val root = orCreateProjectBaseDir
        val filePath = root.toNioPath().resolve("sample.txt")

        git(root.toNioPath(), "init")
        Files.writeString(filePath, before)
        git(root.toNioPath(), "add", "sample.txt")
        git(root.toNioPath(), "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "Initial")
        root.refresh(false, true)

        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf(VcsDirectoryMapping(root.path, "Git"))
        VcsRepositoryManager.getInstance(project).waitForAsyncTaskCompletion()
        assertNotNull(GitRepositoryManager.getInstance(project).getRepositoryForRoot(root))

        val file = requireNotNull(root.findChild("sample.txt"))
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) { document.setText(after) }
        FileDocumentManager.getInstance().saveDocument(document)
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        val changes = ChangeListManagerImpl.getInstanceImpl(project)
        PlatformTestUtil.waitForPromise(changes.promiseWaitForUpdate())

        val trackers = LineStatusTrackerManager.getInstanceImpl(project)
        trackers.requestTrackerFor(document, this)
        try {
            trackers.waitUntilBaseContentsLoaded()
            val tracker = trackers.getLineStatusTracker(document) as PartialLocalLineStatusTracker
            tracker.setExcludedFromCommit(false)
            // The commit tree takes ChangeListChange entries from changelists,
            // not the unwrapped Change returned by getChange(file).
            val change = changes.defaultChangeList.changes.single { it.virtualFile == file }
            check(tracker, change)
            assertEquals("Generating a diff must not edit the document", after, document.text)
            assertEquals("Generating a diff must not stage changes", "", git(root.toNioPath(), "diff", "--cached"))
        } finally {
            trackers.releaseTrackerFor(document, this)
        }
    }

    private fun diff(changes: List<Change>): String = PlatformTestUtil.waitForFuture(
        ApplicationManager.getApplication().executeOnPooledThread(Callable {
            GenerateCommitMessageAction().getDiff(project, changes)
        })
    )

    private fun git(root: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(root.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("git ${args.joinToString(" ")}: $output", 0, process.waitFor())
        return output
    }
}
