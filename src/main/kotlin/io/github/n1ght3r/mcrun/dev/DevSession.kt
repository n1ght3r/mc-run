package io.github.n1ght3r.mcrun.dev

import io.github.n1ght3r.mcrun.util.JvmCommand
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the server and the client side by side.
 *
 * Lifecycle: start the server, wait for its `Done (...)!` line, start the client, then
 * - client exits: send `stop` to the server, wait up to 30 s, then kill it;
 * - server exits: kill the client;
 * - build cancelled or JVM shutting down: stop both, server through `stop` first.
 */
class DevSession(
    private val server: Launch,
    private val client: Launch,
    private val readyTimeoutSeconds: Int,
    private val logger: Logger,
) {
    /** One process to start: its command, the `java` binary, and where to write its argument file. */
    class Launch(val command: JvmCommand, val java: File, val argFile: File)

    @Volatile private var running = true
    @Volatile private var stopRequested = false
    private var serverProcess: Process? = null
    private var clientProcess: Process? = null
    private val tail = ArrayDeque<String>()

    /** Returns the server's exit code. Throws when the server crashed or never became ready. */
    fun run(): Int {
        val hook = Thread({ shutdown() }, "mc-run-dev-shutdown")
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            val srv = start(server).also { serverProcess = it }
            val ready = java.util.concurrent.CountDownLatch(1)
            pump(srv.inputStream, prefix = "") { line ->
                remember(line)
                if (READY.containsMatchIn(line)) ready.countDown()
            }
            forwardStdin(srv.outputStream)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(readyTimeoutSeconds.toLong())
            while (!ready.await(200, TimeUnit.MILLISECONDS)) {
                if (!srv.isAlive) {
                    throw GradleException("Server exited with code ${srv.exitValue()} before it was ready.\n${tailText()}")
                }
                if (System.nanoTime() > deadline) {
                    stopServer(srv)
                    throw GradleException("Server was not ready after ${readyTimeoutSeconds}s. Last lines:\n${tailText()}")
                }
            }

            logger.lifecycle("mc-run: server is ready, starting the client")
            val cli = start(client).also { clientProcess = it }
            pump(cli.inputStream, prefix = "[client] ") {}

            while (true) {
                if (!cli.isAlive) {
                    logger.lifecycle("mc-run: client exited, stopping the server")
                    stopServer(srv)
                    break
                }
                if (!srv.isAlive) {
                    logger.lifecycle("mc-run: server exited, closing the client")
                    destroyTree(cli)
                    break
                }
                Thread.sleep(200)
            }

            val code = srv.waitFor()
            if (code != 0 && !stopRequested) {
                throw GradleException("Server exited with code $code.\n${tailText()}")
            }
            return code
        } catch (e: InterruptedException) {
            // Build cancellation interrupts the task thread.
            Thread.currentThread().interrupt()
            throw GradleException("runDev was cancelled", e)
        } finally {
            shutdown()
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (_: IllegalStateException) {
                // The JVM is already shutting down; the hook runs anyway.
            }
        }
    }

    private fun start(launch: Launch): Process {
        val line = launch.command.commandLine(launch.java, launch.argFile)
        launch.command.workingDir.mkdirs()
        return ProcessBuilder(line)
            .directory(launch.command.workingDir)
            .redirectErrorStream(true)
            .start()
    }

    /** Idempotent: stops whatever is still alive. Safe to call from the shutdown hook. */
    @Synchronized
    private fun shutdown() {
        running = false
        clientProcess?.let { if (it.isAlive) destroyTree(it) }
        serverProcess?.let { if (it.isAlive) stopServer(it) }
    }

    private fun stopServer(process: Process) {
        stopRequested = true
        try {
            synchronized(process.outputStream) {
                process.outputStream.write("stop\n".toByteArray())
                process.outputStream.flush()
            }
        } catch (_: Exception) {
            // stdin already closed: the process is going away anyway.
        }
        if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            logger.warn("mc-run: server did not stop within {}s, killing it", STOP_TIMEOUT_SECONDS)
            destroyTree(process)
        }
    }

    private fun destroyTree(process: Process) {
        process.descendants().forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    private fun pump(input: InputStream, prefix: String, onLine: (String) -> Unit) {
        thread(isDaemon = true, name = "mc-run-dev-output") {
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println(prefix + line)
                    onLine(line)
                }
            }
        }
    }

    /**
     * Forwards the Gradle console to the server so commands can be typed. Polls `available()` so
     * the thread can end instead of blocking forever in `read()` after the session is over.
     */
    private fun forwardStdin(target: OutputStream) {
        thread(isDaemon = true, name = "mc-run-dev-stdin") {
            val buffer = ByteArray(1024)
            try {
                while (running) {
                    val available = System.`in`.available()
                    if (available <= 0) {
                        Thread.sleep(50)
                        continue
                    }
                    val read = System.`in`.read(buffer, 0, minOf(available, buffer.size))
                    if (read < 0) break
                    synchronized(target) {
                        target.write(buffer, 0, read)
                        target.flush()
                    }
                }
            } catch (_: Exception) {
                // Either side closed: nothing left to forward.
            }
        }
    }

    // Guarded by its own monitor, not `this`: shutdown() holds `this` while waiting for the server
    // to stop, and the output pump must keep draining meanwhile.
    private fun remember(line: String) = synchronized(tail) {
        tail.addLast(line)
        while (tail.size > TAIL_LINES) tail.removeFirst()
    }

    private fun tailText(): String = synchronized(tail) { tail.joinToString("\n") }

    companion object {
        val READY = Regex("""Done \(\d+(\.\d+)?s\)!""")
        private const val STOP_TIMEOUT_SECONDS = 30L
        private const val TAIL_LINES = 40
    }
}
