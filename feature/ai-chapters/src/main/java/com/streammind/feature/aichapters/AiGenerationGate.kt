package com.streammind.feature.aichapters

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents duplicate generation requests from multiple ViewModels or recomposed
 * screens. The cache check must run inside this keyed lock so two callers cannot
 * both observe an empty cache and call Gemini.
 */
object AiGenerationGate {
    private const val DEFAULT_COOLDOWN_MS = 60_000L
    private val globalLock = Mutex()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    suspend fun <T> withKey(
        key: String,
        block: suspend () -> T
    ): T {
        val mutex = locks.getOrPut(key) { Mutex() }
        return globalLock.withLock {
            mutex.withLock {
                checkCooldown("*")
                checkCooldown(key)
                try {
                    block()
                } catch (throwable: Throwable) {
                    if (isQuotaExceeded(throwable)) {
                        cooldownUntil["*"] = System.currentTimeMillis() + DEFAULT_COOLDOWN_MS
                        cooldownUntil[key] = System.currentTimeMillis() + DEFAULT_COOLDOWN_MS
                    }
                    throw throwable
                }
            }
        }
    }

    private fun checkCooldown(key: String) {
        val until = cooldownUntil[key] ?: return
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0) {
            throw IllegalStateException(
                "Gemini quota is temporarily unavailable. Please try again in " +
                    "${(remaining + 999) / 1000} seconds."
            )
        }
        cooldownUntil.remove(key, until)
    }

    private fun isQuotaExceeded(throwable: Throwable): Boolean {
        if (throwable is HttpException && throwable.code() == 429) return true
        return generateSequence(throwable) { it.cause }
            .any { it.message?.contains("HTTP 429", ignoreCase = true) == true }
    }
}
