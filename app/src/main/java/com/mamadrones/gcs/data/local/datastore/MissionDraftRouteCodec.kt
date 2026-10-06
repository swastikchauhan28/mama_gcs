package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.repository.MissionDraftFileCodec
import com.mamadrones.gcs.domain.repository.MissionDraftFileFormat
import javax.inject.Inject

/** Routes local file operations to explicit formats; import format is detected by content. */
class MissionDraftRouteCodec @Inject constructor(
    private val geoJson: MissionDraftGeoJsonCodec,
    private val gpx: MissionDraftGpxCodec,
) : MissionDraftFileCodec {
    override fun encode(draft: MissionDraft, format: MissionDraftFileFormat): String = when (format) {
        MissionDraftFileFormat.GEOJSON -> geoJson.encode(draft)
        MissionDraftFileFormat.GPX -> gpx.encode(draft)
    }

    override fun decode(content: String): MissionDraft = when (content.trimStart().firstOrNull()) {
        '{' -> geoJson.decode(content)
        '<' -> gpx.decode(content)
        else -> error("Unsupported route file. Choose a GeoJSON or GPX file.")
    }
}
