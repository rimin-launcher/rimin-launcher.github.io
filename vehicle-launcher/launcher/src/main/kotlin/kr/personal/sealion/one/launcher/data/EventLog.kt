package kr.personal.sealion.one.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LogEvent(val at: Long, val source: String, val message: String)

/** Small rolling log of lock/unlock and automation actions, persisted to app storage. */
class EventLog(context: Context) {
    private val file = File(context.filesDir, "events.json")
    private val state = MutableStateFlow(load())
    val flow: StateFlow<List<LogEvent>> = state

    @Synchronized
    fun add(source: String, message: String) {
        val next = (listOf(LogEvent(System.currentTimeMillis(), source, message)) + state.value).take(MAX)
        state.value = next
        runCatching {
            file.writeText(JSONArray(next.map {
                JSONObject().put("at", it.at).put("source", it.source).put("message", it.message)
            }).toString())
        }
    }

    private fun load(): List<LogEvent> = runCatching {
        val array = JSONArray(file.readText())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            LogEvent(o.getLong("at"), o.getString("source"), o.getString("message"))
        }
    }.getOrDefault(emptyList())

    companion object { private const val MAX = 200 }
}
