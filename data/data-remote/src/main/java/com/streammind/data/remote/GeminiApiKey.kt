package com.streammind.data.remote

import javax.inject.Qualifier

/**
 * Only the :app module can see BuildConfig.GEMINI_API_KEY (it's generated per-module).
 * Rather than have this module reach up to :app for it — which would also create a
 * circular dependency — :app provides the raw string under this qualifier, and
 * everything here just injects it. Keeps the dependency direction one-way.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiApiKey
