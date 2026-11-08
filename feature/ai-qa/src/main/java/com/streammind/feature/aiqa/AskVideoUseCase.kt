package com.streammind.feature.aiqa

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface AskResult {
    /** Emitted once, before generation starts, so the UI can show sources immediately. */
    data class Sources(val chunks: List<RetrievedChunk>) : AskResult

    /** A partial piece of the answer, to be appended to what's already rendered. */
    data class Token(val text: String) : AskResult

    data object Done : AskResult
}

class AskVideoUseCase @Inject constructor(
    private val retrieveChunks: RetrieveRelevantChunksUseCase,
    private val qaRepository: QaRepository
) {
    operator fun invoke(videoId: String, question: String): Flow<AskResult> = flow {
        val chunks = retrieveChunks(videoId, question)

        if (chunks.isEmpty()) {
            emit(AskResult.Sources(emptyList()))
            emit(
                AskResult.Token(
                    "No transcript content is indexed for this video yet, so there's " +
                        "nothing to search. Try again once embedding finishes."
                )
            )
            emit(AskResult.Done)
            return@flow
        }

        // Surface sources before the answer streams — the user can start reading
        // and jumping to timestamps while the model is still generating.
        emit(AskResult.Sources(chunks))

        val context = chunks.joinToString("\n") { "[${it.segment.startMs}ms] ${it.segment.text}" }
        qaRepository.streamAnswer(question, context).collect { token ->
            emit(AskResult.Token(token))
        }
        emit(AskResult.Done)
    }
}
