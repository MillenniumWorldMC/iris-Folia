/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.pack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PackDownloaderTest {
    @Test
    public void resolvesBranchReference() {
        assertEquals(
                "https://codeload.github.com/IrisDimensions/overworld/zip/refs/heads/feature/release",
                PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "feature/release")
        );
    }

    @Test
    public void resolvesQualifiedHeadReference() {
        assertEquals(
                "https://codeload.github.com/IrisDimensions/overworld/zip/refs/heads/master",
                PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "refs/heads/master")
        );
    }

    @Test
    public void resolvesTagReference() {
        assertEquals(
                "https://codeload.github.com/IrisDimensions/overworld/zip/refs/tags/v4.0.0",
                PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "refs/tags/v4.0.0")
        );
    }

    @Test
    public void resolvesCommitReference() {
        assertEquals(
                "https://github.com/IrisDimensions/overworld/archive/8e32852ee6ecd039fae27a36f701f57cdc02e83f.zip",
                PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "8e32852ee6ecd039fae27a36f701f57cdc02e83f")
        );
    }

    @Test
    public void rejectsUnsafeRepositoryAndReference() {
        assertThrows(IllegalArgumentException.class, () -> PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld?raw=1", "master"));
        assertThrows(IllegalArgumentException.class, () -> PackDownloader.resolveGithubArchiveUrl("../overworld", "master"));
        assertThrows(IllegalArgumentException.class, () -> PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "refs/heads/../master"));
        assertThrows(IllegalArgumentException.class, () -> PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", "refs/pull/123/head"));
        assertThrows(IllegalArgumentException.class, () -> PackDownloader.resolveGithubArchiveUrl("IrisDimensions/overworld", ""));
    }
}
