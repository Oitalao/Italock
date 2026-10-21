package br.italo.italock.data

import br.italo.italock.lock.LockClient.LockStatus
import org.json.JSONObject

/**
 * O que a tag NFC de um computador faz, pelos dois interruptores dos Detalhes ("Travar pela tag" e "Destravar pela tag"):
 *  - os dois ligados: alterna conforme o cadeado (travado destrava, destravado trava);
 *  - só um: faz só aquilo, sem consultar o cadeado;
 *  - nenhum: a tag desse computador não faz nada.
 * Destravar pela tag sempre pede a biometria (quem decide isso é a tela da tag, não esta regra).
 */
object TagRule {
    enum class Step { UNLOCK, LOCK, ASK_STATE, NOTHING }

    const val K_LOCK = "tagLock"
    const val K_UNLOCK = "tagUnlock"
    /** Configuração antiga (até a 1.5): "toggle", "unlock" ou "lock". */
    const val K_LEGACY = "tagAction"

    /** Primeiro passo ao encostar a tag. */
    fun first(lock: Boolean, unlock: Boolean): Step = when {
        lock && unlock -> Step.ASK_STATE
        unlock -> Step.UNLOCK
        lock -> Step.LOCK
        else -> Step.NOTHING
    }

    /**
     * Depois de consultar o cadeado (só quando os dois estão ligados): travado destrava, destravado trava.
     * Qualquer outro estado não faz nada (quem chamou mostra o motivo).
     */
    fun afterState(lock: Boolean, unlock: Boolean, status: LockStatus?): Step = when (status) {
        LockStatus.LOCKED -> if (unlock) Step.UNLOCK else Step.NOTHING
        LockStatus.UNLOCKED -> if (lock) Step.LOCK else Step.NOTHING
        else -> Step.NOTHING
    }

    /**
     * Lê os dois interruptores do JSON do computador. Sem eles, migra a configuração antiga:
     * alternar → os dois; só destravar → só destravar; só travar → só travar; sem nada → os dois (era o padrão).
     * Retorna (travar, destravar).
     */
    fun read(o: JSONObject): Pair<Boolean, Boolean> {
        if (o.has(K_LOCK) || o.has(K_UNLOCK)) return o.optBoolean(K_LOCK, true) to o.optBoolean(K_UNLOCK, true)
        return fromLegacy(o.optString(K_LEGACY))
    }

    fun fromLegacy(key: String?): Pair<Boolean, Boolean> = when (key) {
        "unlock" -> false to true
        "lock" -> true to false
        else -> true to true
    }

    /** Resumo para os registros e para a tela. */
    fun summary(lock: Boolean, unlock: Boolean): String = when {
        lock && unlock -> "Trava e destrava"
        unlock -> "Só destrava"
        lock -> "Só trava"
        else -> "Desligada"
    }
}
