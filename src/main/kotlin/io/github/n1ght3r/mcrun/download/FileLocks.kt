package io.github.n1ght3r.mcrun.download

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Serializes writers of one cache file, both inside this JVM and across processes (two builds
 * running at once share `~/.gradle/caches/mc-run`).
 */
internal object FileLocks {
    private val local = ConcurrentHashMap<Path, ReentrantLock>()

    fun <T> withLock(target: Path, block: () -> T): T {
        val lockFile = target.resolveSibling(".${target.fileName}.lock").toAbsolutePath().normalize()
        Files.createDirectories(lockFile.parent)
        val jvmLock = local.computeIfAbsent(lockFile) { ReentrantLock() }
        jvmLock.lock()
        try {
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                val lock = acquire(channel)
                try {
                    return block()
                } finally {
                    lock.release()
                }
            }
        } finally {
            jvmLock.unlock()
        }
    }

    // A second plugin classloader in the same daemon has its own `local` map, so the OS lock can
    // still overlap inside one JVM. Poll until the other holder is done.
    private fun acquire(channel: FileChannel): FileLock {
        while (true) {
            try {
                return channel.lock()
            } catch (_: OverlappingFileLockException) {
                Thread.sleep(50)
            }
        }
    }
}
