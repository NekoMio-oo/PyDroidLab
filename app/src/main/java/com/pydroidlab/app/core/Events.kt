package com.pydroidlab.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A rendered matplotlib figure routed to the Charts panel. */
data class Chart(val path: String, val title: String, val id: Long = System.nanoTime())

/** Debug-pause notification from the bdb engine. */
data class DebugPause(val file: String, val line: Int)

/**
 * Central in-app event bus.  The Python `pdl_host` bridge writes into these
 * flows; Compose screens collect them.
 */
object EventBus {
    private val _stdout = MutableSharedFlow<String>(extraBufferCapacity = 4096)
    private val _stderr = MutableSharedFlow<String>(extraBufferCapacity = 4096)
    private val _charts = MutableSharedFlow<Chart>(extraBufferCapacity = 64)
    private val _debugPause = MutableSharedFlow<DebugPause>(extraBufferCapacity = 16)
    private val _debugFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    val stdout: SharedFlow<String> = _stdout
    val stderr: SharedFlow<String> = _stderr
    val charts: SharedFlow<Chart> = _charts
    val debugPause: SharedFlow<DebugPause> = _debugPause
    val debugFinished: SharedFlow<Unit> = _debugFinished

    /** All charts captured in the session (for the paged Charts panel). */
    private val _chartList = MutableStateFlow<List<Chart>>(emptyList())
    val chartList: StateFlow<List<Chart>> = _chartList

    fun emitStdout(s: String) { _stdout.tryEmit(s) }
    fun emitStderr(s: String) { _stderr.tryEmit(s) }

    fun emitChart(c: Chart) {
        _chartList.value = _chartList.value + c
        _charts.tryEmit(c)
    }

    fun clearCharts() { _chartList.value = emptyList() }

    fun emitDebugPause(p: DebugPause) { _debugPause.tryEmit(p) }
    fun emitDebugFinished() { _debugFinished.tryEmit(Unit) }
}
