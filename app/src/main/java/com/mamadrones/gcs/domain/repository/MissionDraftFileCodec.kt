package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.MissionDraft

/** Portable local route format. Encoding and decoding do not upload or execute a mission. */
interface MissionDraftFileCodec {
    fun encode(draft: MissionDraft): String
    fun decode(content: String): MissionDraft
}
