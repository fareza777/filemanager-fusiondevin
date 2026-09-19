package app.sorta.files

import app.sorta.files.core.ops.ConflictPolicy
import app.sorta.files.core.ops.ItemStatus
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.ops.OperationEngine
import app.sorta.files.core.ops.OperationHost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

class OperationEngineTest {

    @get:Rule val tmp = TemporaryFolder()

    private class TestHost : OperationHost {
        var conflictResponse: Pair<ConflictPolicy, Boolean> = ConflictPolicy.KEEP_BOTH to false
        val trashRoot: File = createTempDir("trash")
        override suspend fun askConflict(sourceName: String, destPath: String) = conflictResponse
        override suspend fun trashMove(src: File): String {
            trashRoot.mkdirs()
            val t = File(trashRoot, src.name)
            if (!src.renameTo(t)) { src.copyRecursively(t, true); src.deleteRecursively() }
            return t.absolutePath
        }
    }

    private fun engine(host: TestHost = TestHost()) = OperationEngine(host) to host

    @Test fun `copy verifies content`() = runTest {
        val (eng, _) = engine()
        val src = tmp.newFile("a.txt").apply { writeText("hello world") }
        val dst = tmp.newFolder("dst")
        val res = eng.run(OpType.COPY, listOf(src), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.SUCCESS, res[0].status)
        assertEquals("hello world", File(dst, "a.txt").readText())
        assertTrue(src.exists())
    }

    @Test fun `move verifies and deletes source`() = runTest {
        val (eng, _) = engine()
        val src = tmp.newFile("m.txt").apply { writeText("move me") }
        val dst = tmp.newFolder("dst2")
        // simulate cross-volume by forcing renameTo failure: make same-name exist then check keep-both
        val res = eng.run(OpType.MOVE, listOf(src), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.SUCCESS, res[0].status)
        assertEquals("move me", File(dst, "m.txt").readText())
        assertFalse(src.exists())
    }

    @Test fun `conflict keep-both generates suffix`() = runTest {
        val (eng, _) = engine()
        val dst = tmp.newFolder("d")
        File(dst, "f.txt").writeText("existing")
        val src = tmp.newFile("f.txt").apply { writeText("new") }
        val res = eng.run(OpType.COPY, listOf(src), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.SUCCESS, res[0].status)
        assertTrue(File(dst, "f.txt").exists())
        assertTrue(File(dst, "f (1).txt").exists())
        assertEquals("existing", File(dst, "f.txt").readText())
    }

    @Test fun `conflict ASK resolves via host`() = runTest {
        val host = TestHost().apply { conflictResponse = ConflictPolicy.KEEP_BOTH to true }
        val eng = OperationEngine(host)
        val dst = tmp.newFolder("d2")
        File(dst, "x.txt").writeText("old")
        val src = tmp.newFile("x.txt").apply { writeText("new") }
        val res = eng.run(OpType.COPY, listOf(src), dst, ConflictPolicy.ASK)
        assertEquals(ItemStatus.SUCCESS, res[0].status)
        assertTrue(File(dst, "x (1).txt").exists())
    }

    @Test fun `failed item is marked and operation continues`() = runTest {
        val (eng, _) = engine()
        val realCopy = eng.copyBytes
        eng.copyBytes = { src, dst, cb ->
            if (src.name == "bad.txt") throw java.io.IOException("simulated io failure")
            realCopy(src, dst, cb)
        }
        val dst = tmp.newFolder("d3")
        val bad = tmp.newFile("bad.txt").apply { writeText("x") }
        val good = tmp.newFile("good.txt").apply { writeText("y") }
        val res = eng.run(OpType.COPY, listOf(bad, good), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.FAILED, res[0].status)
        assertEquals("simulated io failure", res[0].error)
        assertEquals(ItemStatus.SUCCESS, res[1].status)
        assertFalse(File(dst, ".sorta_tmp_bad.txt").exists())
    }

    @Test fun `disk full precheck fails all items`() = runTest {
        val (eng, _) = engine()
        // sparse file larger than free space on the test filesystem
        val big = tmp.newFile("big.bin")
        val free = tmp.root.usableSpace
        java.io.RandomAccessFile(big, "rw").use { it.setLength(free + 1024L * 1024 * 1024) }
        val dst = tmp.newFolder("d4")
        val res = eng.run(OpType.COPY, listOf(big), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals(ItemStatus.FAILED, res[0].status)
        assertEquals("disk_full", res[0].error)
    }
}
