package br.italo.italock.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico: poucas linhas de eventos (pedidos, falhas de rede, erros), num arquivo pequeno dentro do
 * app. Nunca recebe chaves nem senhas. É o que "Exportar registros" compartilha, junto com um resumo das configurações.
 */
object DiagLog {
    private const val TAG = "Italock"
    private const val MAX_BYTES = 64 * 1024
    @Volatile private var file: File? = null
    private val lock = Any()

    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, "diagnostico.log")
    }

    fun i(msg: String) = write("I", msg)
    fun w(msg: String) = write("W", msg)

    private fun write(level: String, msg: String) {
        if (level == "W") Log.w(TAG, msg) else Log.i(TAG, msg)
        val f = file ?: return
        val line = "${SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())} $level $msg\n"
        synchronized(lock) {
            runCatching {
                if (f.length() > MAX_BYTES) {
                    // Guarda a metade mais nova.
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2).substringAfter('\n'))
                }
                f.appendText(line)
            }
        }
    }

    fun read(): String = synchronized(lock) { runCatching { file?.readText() }.getOrNull().orEmpty() }
}
