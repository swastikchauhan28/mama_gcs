package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.repository.MissionDraftFileCodec
import java.util.UUID
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

/** Bounded GeoJSON FeatureCollection containing an ordered list of waypoint Point features. */
class MissionDraftGeoJsonCodec @Inject constructor() : MissionDraftFileCodec {
    override fun encode(draft: MissionDraft): String {
        val features = JSONArray()
        draft.waypoints.forEachIndexed { index, point ->
            features.put(JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("sequence", index + 1))
                .put("geometry", JSONObject()
                    .put("type", "Point")
                    .put("coordinates", JSONArray().put(point.longitude).put(point.latitude))))
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("name", draft.name)
            .put("properties", JSONObject().put("format", FORMAT).put("version", VERSION))
            .put("features", features)
            .toString(2)
            .also { require(it.length <= MAX_FILE_CHARS) { "Export exceeds the file size limit" } }
    }

    override fun decode(content: String): MissionDraft {
        require(content.length <= MAX_FILE_CHARS) { "File exceeds the import size limit" }
        val root = JSONObject(content)
        require(root.optString("type") == "FeatureCollection") { "Expected a GeoJSON FeatureCollection" }
        val properties = root.optJSONObject("properties")
        if (properties?.has("format") == true) {
            require(properties.optString("format") == FORMAT && properties.optInt("version", -1) == VERSION) {
                "Unsupported Mama GCS route file version"
            }
        }
        val features = root.optJSONArray("features") ?: error("FeatureCollection has no features array")
        require(features.length() in 0..MissionDraft.MAX_WAYPOINTS) { "Route has too many waypoints" }
        val points = buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: error("Invalid feature at position ${index + 1}")
                require(feature.optString("type") == "Feature") { "Invalid feature at position ${index + 1}" }
                val geometry = feature.optJSONObject("geometry") ?: error("Waypoint geometry is missing")
                require(geometry.optString("type") == "Point") { "Only Point waypoints are supported" }
                val coordinates = geometry.optJSONArray("coordinates") ?: error("Waypoint coordinates are missing")
                require(coordinates.length() >= 2) { "Waypoint needs longitude and latitude" }
                val longitude = coordinates.getDouble(0)
                val latitude = coordinates.getDouble(1)
                add(DraftWaypoint(UUID.randomUUID().toString(), latitude, longitude))
            }
        }
        val name = root.optString("name", "Imported route").trim().ifBlank { "Imported route" }
            .take(MissionDraft.MAX_NAME_LENGTH)
        return MissionDraft(name, points)
    }

    private companion object {
        const val FORMAT = "mama-gcs-route"
        const val VERSION = 1
        const val MAX_FILE_CHARS = 256_000
    }
}
