# Publishing a GitHub release

Both plugins share the version in `gradle.properties`. Publish one GitHub release
per version with both plugin ZIPs attached.

1. Update `pluginVersion`, review the plugin change notes, and commit the release
   changes. Use that exact commit as the release target.
2. With JDK 21, test and build both flavors from a clean checkout of that commit:

   ```bash
   ./gradlew test buildPlugin -Pflavor=codex
   ./gradlew test buildPlugin -Pflavor=claude
   ```

3. Verify compatibility with the IDE versions and products claimed in the release.
   The current minimum declared build is 251 (IDE 2025.1), and the build target is
   IntelliJ IDEA Community 2025.1.4.1. A successful build alone is not verification
   of compatibility with other IDE releases.
4. Create a release at
   <https://github.com/behemoth-13/Claude-Codex-Commit/releases/new> with a `v<version>`
   tag, targeting the tested commit. Use `Claude Commit & Codex Commit <version>`
   as the title.
5. Summarize user-visible changes, compatibility requirements, installation steps,
   and each plugin's Marketplace availability. Attach these files from
   `build/distributions/`:
   - `Claude-Commit-<version>.zip`
   - `Codex-Commit-<version>.zip`
6. Generate and attach `SHA256SUMS.txt` for the two ZIPs, then publish the release.

GitHub's automatically generated notes use the categories in `.github/release.yml`.
Review generated notes before publishing. GitHub releases do not publish or
approve plugins on JetBrains Marketplace.
