package com.mamadrones.gcs.data.local.datastore

import android.util.Xml
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.io.StringReader
import java.util.UUID
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser

/** GPX 1.1 route exchange. It intentionally imports/exports only ordered route points. */
class MissionDraftGpxCodec @Inject constructor() {
    fun encode(draft: MissionDraft): String {
        val output = StringBuilder()
        output.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        output.append("<gpx version=\"1.1\" creator=\"Mama GCS\" xmlns=\"$GPX_NAMESPACE\">\n")
        output.append("  <rte>\n    <name>").append(escapeXml(draft.name)).append("</name>\n")
        draft.waypoints.forEachIndexed { index, point ->
            output.append("    <rtept lat=\"").append(point.latitude).append("\" lon=\"")
                .append(point.longitude).append("\"><name>WP ").append(index + 1)
                .append("</name></rtept>\n")
        }
        output.append("  </rte>\n</gpx>")
        return output.toString().also { require(it.length <= MAX_FILE_CHARS) { "Export exceeds the file size limit" } }
    }

    fun decode(content: String): MissionDraft {
        require(content.length <= MAX_FILE_CHARS) { "File exceeds the import size limit" }
        require(!DOCTYPE.containsMatchIn(content)) { "GPX files with document type declarations are not supported" }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(content))

        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) event = parser.next()
        require(event == XmlPullParser.START_TAG && parser.name == "gpx") { "Expected a GPX document" }
        require(parser.getAttributeValue(null, "version") == "1.1") { "Only GPX 1.1 routes are supported" }
        require(parser.namespace == GPX_NAMESPACE) { "GPX namespace is missing or unsupported" }

        var routeCount = 0
        var inRoute = false
        var name = "Imported GPX route"
        val points = mutableListOf<DraftWaypoint>()
        while (true) {
            event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "rte" -> {
                        routeCount++
                        require(routeCount == 1) { "Import one GPX route at a time" }
                        inRoute = true
                    }
                    "name" -> if (inRoute && points.isEmpty()) {
                        name = parser.nextText().trim().ifBlank { "Imported GPX route" }
                            .take(MissionDraft.MAX_NAME_LENGTH)
                    }
                    "rtept" -> {
                        require(inRoute) { "Route point is outside the GPX route" }
                        require(points.size < MissionDraft.MAX_WAYPOINTS) { "Route has too many waypoints" }
                        val latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            ?: error("GPX route point has no valid latitude")
                        val longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            ?: error("GPX route point has no valid longitude")
                        points += DraftWaypoint(UUID.randomUUID().toString(), latitude, longitude)
                        skipCurrentElement(parser)
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "rte") {
                inRoute = false
            }
        }
        require(routeCount == 1) { "GPX file does not contain a route" }
        return MissionDraft(name, points)
    }

    private fun skipCurrentElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> error("Unexpected end of GPX route point")
            }
        }
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character.isISOControl() && character !in listOf('\t', '\n', '\r')) return@forEach
            append(when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '\"' -> "&quot;"
                '\'' -> "&apos;"
                else -> character.toString()
            })
        }
    }

    private companion object {
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val MAX_FILE_CHARS = 256_000
        val DOCTYPE = Regex("<!\\s*DOCTYPE", RegexOption.IGNORE_CASE)
    }
}
