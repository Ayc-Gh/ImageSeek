package com.op.aod.enhance.data

internal object DebugLogContract {
    const val METHOD_APPEND_DEBUG_LOG = "append_debug_log"
    const val EXTRA_BATCH = "batch"
    const val RESULT_PATH = "path"
    const val RESULT_EXTERNAL = "external"
    const val RESULT_ERROR = "error"
    const val MAX_BATCH_BYTES = 256 * 1024
}
