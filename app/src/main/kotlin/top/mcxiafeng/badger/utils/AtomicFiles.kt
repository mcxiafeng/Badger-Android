package top.mcxiafeng.badger.utils

import android.util.AtomicFile
import java.io.File
import java.io.OutputStream

/** Publish a complete file or preserve its previous bytes. Callers own path validation and error reporting. */
internal fun atomicWrite(file: File, write: (OutputStream) -> Unit) {
    val atomic = AtomicFile(file)
    val output = atomic.startWrite()
    try {
        write(output)
        atomic.finishWrite(output)
    } catch (e: Throwable) {
        atomic.failWrite(output)
        throw e
    }
}
