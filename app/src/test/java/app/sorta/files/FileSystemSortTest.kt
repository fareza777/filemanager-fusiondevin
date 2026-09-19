package app.sorta.files

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.SortField
import app.sorta.files.core.fs.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSystemSortTest {

    private fun item(name: String, dir: Boolean = false, size: Long = 0, mod: Long = 0) =
        FileItem("/x/$name", name, dir, size, mod, null,
            if (dir) FileTypeCategory.FOLDER else FileTypeCategory.OTHER)

    @Test fun `name sort keeps dirs first`() {
        val items = listOf(item("z.txt"), item("BDir", true), item("a.txt"), item("ADir", true))
        val sorted = FileSystem.sortList(items, SortSpec(SortField.NAME, true))
        assertEquals(listOf("ADir", "BDir", "a.txt", "z.txt"), sorted.map { it.name })
    }

    @Test fun `size sort descending`() {
        val items = listOf(item("s", size = 1), item("m", size = 50), item("l", size = 900))
        val sorted = FileSystem.sortList(items, SortSpec(SortField.SIZE, false))
        assertEquals(listOf("l", "m", "s"), sorted.map { it.name })
    }

    @Test fun `date sort ascending`() {
        val items = listOf(item("new", mod = 200), item("old", mod = 100))
        val sorted = FileSystem.sortList(items, SortSpec(SortField.DATE, true))
        assertEquals(listOf("old", "new"), sorted.map { it.name })
    }

    @Test fun `keepBoth naming`() {
        val host = object : app.sorta.files.core.ops.OperationHost {
            override suspend fun askConflict(s: String, d: String) =
                app.sorta.files.core.ops.ConflictPolicy.SKIP to false
            override suspend fun trashMove(src: java.io.File) = ""
        }
        val eng = app.sorta.files.core.ops.OperationEngine(host)
        val dir = createTempDir()
        java.io.File(dir, "a.txt").writeText("1")
        java.io.File(dir, "a (1).txt").writeText("2")
        assertEquals("a (2).txt", eng.keepBothName(dir, "a.txt"))
        assertEquals("b.txt", eng.keepBothName(dir, "b.txt"))
    }
}
