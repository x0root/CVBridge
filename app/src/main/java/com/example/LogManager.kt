package com.example

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "cvbridge_logs.txt")
        if (logFile?.exists() == true) {
            val fileLogs = logFile?.readLines() ?: emptyList()
            _logs.value = fileLogs
        }
    }

    fun log(tag: String, message: String, isError: Boolean = false) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val prefix = if (isError) "[ERROR]" else "[INFO]"
        val logEntry = "$time - $prefix [$tag] $message"
        
        synchronized(this) {
            val current = _logs.value.toMutableList()
            current.add(logEntry)
            if (current.size > 1000) {
                current.removeAt(0) // Keep at most 1000 entries
            }
            _logs.value = current
            
            // Save to file
            try {
                if (current.size >= 1000) {
                    logFile?.writeText(current.joinToString("\n") + "\n")
                } else {
                    logFile?.appendText("$logEntry\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun error(tag: String, message: String, error: Throwable? = null) {
        val errStr = error?.let { "\n${it.stackTraceToString()}" } ?: ""
        log(tag, "$message$errStr", true)
    }

    fun clear() {
        _logs.value = emptyList()
        logFile?.writeText("")
    }
    
    fun getAllLogs(): String {
        return _logs.value.joinToString("\n")
    }
}
