package br.italo.italock.watch

import android.content.Context
import android.os.SystemClock
import br.italo.italock.R
import br.italo.italock.data.DiagLog
import br.italo.italock.i18n.L10n
import br.italo.italock.service.UnlockSession
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Ponte com o app Ítalock do relógio Wear OS (MessageClient da Data Layer do Google).
 * Tudo protegido: sem Google Play Services (ou sem relógio), cada chamada vira nada, sem erro.
 *
 * Relógio longe do celular: a Data Layer passa pela nuvem do Google quando os dois têm internet (Wi-Fi ou dados).
 *
 * Mensagens (JSON UTF-8), iguais às do módulo :wear:
 *  celular → relógio: /italock/prompt, /italock/prompt/end, /italock/pcs, /italock/result
 *  relógio → celular: /italock/answer, /italock/pcs/get, /italock/action
 */
object WearBridge {
    const val CAP_WEAR = "italock_wear"
    const val PATH_PROMPT = "/italock/prompt"
    const val PATH_PROMPT_END = "/italock/prompt/end"
    const val PATH_PCS = "/italock/pcs"
    const val PATH_RESULT = "/italock/result"
    const val PATH_ANSWER = "/italock/answer"
    const val PATH_PCS_GET = "/italock/pcs/get"
    const val PATH_ACTION = "/italock/action"

    /** Google Play Services presente e utilizável (sem ele o recurso some da tela). */
    fun available(context: Context): Boolean = runCatching {
        com.google.android.gms.common.GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * Idioma escolhido no celular ("pt-BR", "system", "en"...): vai junto em toda mensagem para o relógio, que passa a
     * usar o mesmo idioma ("system" = o idioma do próprio relógio).
     */
    fun withLang(context: Context, json: JSONObject): JSONObject = json.put("lang", L10n.tag(context))

    fun sendPrompt(context: Context, session: UnlockSession, hideName: Boolean) {
        val leftMs = (session.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(5_000)
        val json = JSONObject()
            .put("session", session.id)
            // O relógio fica no pulso, mas segue a mesma regra de privacidade da tela de bloqueio.
            .put("pc", if (hideName) L10n.s(R.string.wearmsg_your_computer) else session.pc.displayName)
            .put("user", if (hideName) "" else session.user)
            .put("expiresAt", System.currentTimeMillis() + leftMs)
        toWatches(context, PATH_PROMPT, withLang(context, json))
    }

    fun endPrompt(context: Context, sessionId: String) =
        toWatches(context, PATH_PROMPT_END, JSONObject().put("session", sessionId))

    /** Manda para todos os relógios alcançáveis que têm o app (capability "italock_wear"). Em segundo plano. */
    fun toWatches(context: Context, path: String, json: JSONObject) {
        if (!available(context)) return
        val app = context.applicationContext
        thread(isDaemon = true, name = "wear-send") {
            runCatching {
                val info = com.google.android.gms.tasks.Tasks.await(
                    com.google.android.gms.wearable.Wearable.getCapabilityClient(app)
                        .getCapability(CAP_WEAR, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE),
                    5, TimeUnit.SECONDS,
                )
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                for (node in info.nodes) {
                    runCatching {
                        com.google.android.gms.tasks.Tasks.await(
                            com.google.android.gms.wearable.Wearable.getMessageClient(app).sendMessage(node.id, path, bytes), 5, TimeUnit.SECONDS,
                        )
                    }
                }
            }.onFailure { DiagLog.w("relógio Wear OS: envio de $path falhou: ${it.javaClass.simpleName}") }
        }
    }

    /** Resposta a um nó específico (quem pediu). Bloqueante: chamar fora da thread da tela. */
    fun reply(context: Context, nodeId: String, path: String, json: JSONObject) {
        if (!available(context)) return
        runCatching {
            com.google.android.gms.tasks.Tasks.await(
                com.google.android.gms.wearable.Wearable.getMessageClient(context.applicationContext)
                    .sendMessage(nodeId, path, json.toString().toByteArray(Charsets.UTF_8)),
                5, TimeUnit.SECONDS,
            )
        }.onFailure { DiagLog.w("relógio Wear OS: resposta $path falhou: ${it.javaClass.simpleName}") }
    }
}
