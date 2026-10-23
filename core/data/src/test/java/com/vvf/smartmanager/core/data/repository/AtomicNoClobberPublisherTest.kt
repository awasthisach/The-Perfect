package com.vvf.smartmanager.core.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class AtomicNoClobberPublisherTest {
    @Test fun unsupportedLinksFailClosedWithoutClobberingARacingDestination() {
        val root=Files.createTempDirectory("no-clobber").toFile()
        try {
            val temp=File(root,"temp").apply { writeText("decrypted") }
            val target=File(root,"target")
            val publisher=AtomicNoClobberPublisher(
                link={_,_-> target.writeText("other-writer");throw IOException("unsupported links") },
                unlink={error("must not unlink someone else's file")},
                discardUnpublished={it.delete()})
            assertTrue(runCatching { publisher.publish(temp,target) }.isFailure)
            assertEquals("other-writer",target.readText())
            assertFalse(temp.exists())
        } finally {root.deleteRecursively()}
    }

    @Test fun successfulPublicationIsCompleteAndNeverOverwritesAnExistingFile() {
        val root=Files.createTempDirectory("no-clobber").toFile()
        try {
            val temp=File(root,"temp").apply {writeText("complete")}
            val target=File(root,"target")
            val publisher=AtomicNoClobberPublisher(
                link={from,to->Files.createLink(File(to).toPath(),File(from).toPath());Unit},
                unlink={Files.delete(File(it).toPath())},discardUnpublished={it.delete()})
            publisher.publish(temp,target)
            assertEquals("complete",target.readText())
            assertFalse(temp.exists())
            temp.writeText("replacement")
            assertTrue(runCatching {publisher.publish(temp,target)}.isFailure)
            assertEquals("complete",target.readText())
        } finally {root.deleteRecursively()}
    }

    @Test fun failedUnlinkAfterPublicationNeverShredsThePublishedInode() {
        val root=Files.createTempDirectory("no-clobber").toFile()
        try {
            val temp=File(root,"temp").apply {writeText("complete")}
            val target=File(root,"target")
            var discarded=false
            val publisher=AtomicNoClobberPublisher(
                link={from,to->Files.createLink(File(to).toPath(),File(from).toPath());Unit},
                unlink={throw IOException("injected unlink failure")},discardUnpublished={discarded=true})
            val error=runCatching {publisher.publish(temp,target)}.exceptionOrNull()
            assertTrue(error is SharedPlaintextLinkCleanupException)
            assertFalse(discarded)
            assertEquals("complete",target.readText())
            assertEquals("complete",temp.readText())
        } finally {root.deleteRecursively()}
    }
}
