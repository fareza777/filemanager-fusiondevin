package app.sorta.files

import app.sorta.files.core.ops.ConflictPolicy
import app.sorta.files.core.ops.ItemStatus
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.ops.OperationEngine
import app.sorta.files.core.ops.OperationHost
import app.sorta.files.core.rename.BatchRenamePattern
import app.sorta.files.core.rules.RuleEngine
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.data.db.SortRule
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase2Test {

    @get:Rule val tmp = TemporaryFolder()

    private class TestHost : OperationHost {
        override suspend fun askConflict(s: String, d: String) = ConflictPolicy.KEEP_BOTH to false
        override suspend fun trashMove(src: File) = ""
    }

    private fun eng() = OperationEngine(TestHost())

    @Test fun `cancel mid-copy removes temp and keeps source`() = runTest {
        val e = eng()
        val src = tmp.newFile("big.bin")
        src.writeBytes(ByteArray(20 * 1024 * 1024) { it.toByte() })
        val dst = tmp.newFolder("dst")
        // cancel after ~1MB copied
        var copied = 0L
        val real = e.copyBytes
        e.copyBytes = { s, d, cb, sc ->
            real(s, d, { n -> copied += n; cb(n) }, { copied < 1024 * 1024 })
        }
        val job = launch { e.run(OpType.COPY, listOf(src), dst, ConflictPolicy.KEEP_BOTH) }
        job.join()
        assertFalse(File(dst, ".sorta_tmp_big.bin").exists())
        assertFalse(File(dst, "big.bin").exists())
        assertTrue(src.exists())
        assertEquals(20L * 1024 * 1024, src.length())
    }

    @Test fun `moving dir into itself fails`() = runTest {
        val e = eng()
        val parent = tmp.newFolder("parent")
        val child = File(parent, "child").apply { mkdirs() }
        val res = e.run(OpType.MOVE, listOf(parent), child, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.FAILED, res[0].status)
        assertEquals("into_itself", res[0].error)
        assertTrue(parent.exists())
    }

    @Test fun `overwrite file onto existing dir fails`() = runTest {
        val e = eng()
        val dst = tmp.newFolder("d")
        File(dst, "f.txt").mkdirs() // a DIRECTORY named f.txt
        val src = tmp.newFile("f.txt").apply { writeText("x") }
        val host = object : OperationHost {
            override suspend fun askConflict(s: String, d: String) = ConflictPolicy.OVERWRITE to false
            override suspend fun trashMove(f: File) = ""
        }
        val res = OperationEngine(host).run(OpType.COPY, listOf(src), dst, ConflictPolicy.ASK)
        assertEquals(ItemStatus.FAILED, res[0].status)
        assertEquals("dest_exists_dir", res[0].error)
    }

    // --- BatchRenamePattern ---

    @Test fun `rename tokens produce expected names`() {
        val dir = tmp.newFolder()
        val f = File(dir, "photo.jpg").apply { createNewFile(); setLastModified(1700000000000L) }
        val r = BatchRenamePattern("{name}_{n}{ext}", start = 5, step = 1, padding = 3)
        assertEquals("photo_005.jpg", r.newName(f, 0))
        assertEquals("photo_006.jpg", r.newName(f, 1))
    }

    @Test fun `rename step and padding`() {
        val dir = tmp.newFolder()
        val f = File(dir, "a.txt").apply { createNewFile() }
        val r = BatchRenamePattern("f{n}{ext}", start = 0, step = 10, padding = 2)
        assertEquals("f00.txt", r.newName(f, 0))
        assertEquals("f10.txt", r.newName(f, 1))
        assertEquals("f20.txt", r.newName(f, 2))
    }

    @Test fun `rename date token`() {
        val dir = tmp.newFolder()
        val f = File(dir, "x.png").apply { createNewFile(); setLastModified(1609459200000L) } // 2021-01-01
        val r = BatchRenamePattern("{date}_{name}{ext}")
        assertTrue(r.newName(f, 0).startsWith("20210101") || r.newName(f,0).startsWith("20201231"))
    }

    @Test fun `rename preview flags conflicts`() {
        val dir = tmp.newFolder()
        val f1 = File(dir, "a.txt").apply { createNewFile() }
        val f2 = File(dir, "b.txt").apply { createNewFile() }
        // same target for both
        val r = BatchRenamePattern("same{ext}")
        val pv = r.preview(listOf(f1, f2))
        assertTrue(pv[0].conflict)
        assertTrue(pv[1].conflict)
        // collision with existing file
        File(dir, "target.txt").createNewFile()
        val r2 = BatchRenamePattern("target{ext}")
        assertTrue(r2.preview(listOf(f1))[0].conflict)
    }

    @Test fun `find replace mode`() {
        val dir = tmp.newFolder()
        val f = File(dir, "IMG_2021.jpg").apply { createNewFile() }
        val r = BatchRenamePattern(find = "IMG", replace = "photo")
        assertEquals("photo_2021.jpg", r.newName(f, 0))
    }

    // --- RuleEngine ---

    private fun item(name: String) =
        FileItem("/x/$name", name, false, 0, 0, null, FileTypeCategory.OTHER)

    @Test fun `rule matching precedence`() {
        val rules = listOf(
            SortRule(id = 1, name = "pdfs", matchType = "EXT", matchValue = "pdf", targetFolder = "/t"),
            SortRule(id = 2, name = "report", matchType = "NAME_CONTAINS", matchValue = "report", targetFolder = "/u"),
        )
        // first matching rule wins
        assertEquals(1L, RuleEngine.firstMatch(rules, item("report.pdf"))!!.id)
        assertEquals(2L, RuleEngine.firstMatch(rules, item("report.txt"))!!.id)
        assertEquals(null, RuleEngine.firstMatch(rules, item("x.zip")))
    }

    @Test fun `rule regex and disabled`() {
        val rules = listOf(
            SortRule(id = 1, name = "off", matchType = "EXT", matchValue = "jpg", targetFolder = "/t", enabled = false),
            SortRule(id = 2, name = "img", matchType = "NAME_REGEX", matchValue = "img_\\d+", targetFolder = "/u"),
        )
        assertEquals(2L, RuleEngine.firstMatch(rules, item("img_42.jpg"))!!.id)
        assertEquals(null, RuleEngine.firstMatch(rules, item("other.jpg")))
    }

    // --- ZIP ---

    @Test fun `zip compress and extract roundtrip`() {
        val dir = tmp.newFolder("src")
        File(dir, "a.txt").writeText("alpha")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.txt").writeText("beta")
        val out = tmp.newFile("out.zip").apply { delete() }
        app.sorta.files.core.zip.ZipCompressor.compress(listOf(dir), out)
        assertTrue(out.exists())
        val dest = tmp.newFolder("ex")
        app.sorta.files.core.zip.ZipExtractor.extract(out, dest, ConflictPolicy.KEEP_BOTH,
            { d, n -> n })
        assertEquals("alpha", File(dest, "src/a.txt").readText())
        assertEquals("beta", File(dest, "src/sub/b.txt").readText())
    }

    @Test fun `zip-slip entry rejected`() {
        // craft zip with ../evil entry
        val evil = tmp.newFile("evil.zip")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(evil)).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("../evil.txt"))
            z.write("x".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("ok.txt"))
            z.write("y".toByteArray())
            z.closeEntry()
        }
        val dest = tmp.newFolder("exd")
        app.sorta.files.core.zip.ZipExtractor.extract(evil, dest, ConflictPolicy.KEEP_BOTH, { _, n -> n })
        assertFalse(File(dest.parentFile, "evil.txt").exists())
        assertTrue(File(dest, "ok.txt").exists())
    }
}
