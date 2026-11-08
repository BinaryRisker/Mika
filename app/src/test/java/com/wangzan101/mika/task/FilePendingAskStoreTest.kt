package com.wangzan101.mika.task

import com.wangzan101.mika.core.run.PendingAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class FilePendingAskStoreTest {
    @Test fun latestAskPerThreadSurvivesReopen() {
        val file = Files.createTempDirectory("pending-ask").resolve("asks.json").toFile()
        val first = PendingAsks(FilePendingAskStore(file))
        first.put(PendingAsk("a1", "t1", "发给哪个?", listOf("甲", "乙"), 10))
        first.put(PendingAsk("a2", "t1", "还是哪个?", listOf("丙", "丁"), 20))
        first.put(PendingAsk("a3", "t2", "几点?", emptyList(), 30))
        val restored = PendingAsks(FilePendingAskStore(file))
        assertEquals("还是哪个?", restored.get("t1")!!.prompt)
        assertEquals(listOf("丙", "丁"), restored.get("t1")!!.options)
        assertEquals("几点?", restored.get("t2")!!.prompt)
        assertEquals("还是哪个?", restored.remove("t1")!!.prompt)
        assertNull(PendingAsks(FilePendingAskStore(file)).get("t1"))
        assertEquals("几点?", PendingAsks(FilePendingAskStore(file)).get("t2")!!.prompt)
    }
}
