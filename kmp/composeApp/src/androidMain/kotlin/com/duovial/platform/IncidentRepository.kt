package com.duovial.platform

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.duovial.state.Incident
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IncidentRepository {

    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    private val pattern = Regex("incident_(\\d+)_part(\\d+)\\.mp4")

    fun scanIncidents(context: Context): List<Incident> {
        val incidents = mutableListOf<Incident>()
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("incident_%")
        val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

        val partIndexCache = mutableMapOf<String, Int>()

        resolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = Uri.withAppendedPath(collectionUri, id.toString())
                val uriStr = uri.toString()
                val timestampSec = extractTimestamp(name) ?: continue
                val partIndex = extractPartIndexFromName(name)
                partIndexCache[uriStr] = partIndex

                val existing = incidents.indexOfFirst { it.timestampSec == timestampSec }
                if (existing >= 0) {
                    val incident = incidents[existing]
                    val sortedParts = (incident.parts + uriStr).sortedBy { partIndexCache[it] ?: 0 }
                    incidents[existing] = incident.copy(parts = sortedParts)
                } else {
                    val date = dateFormat.format(Date(timestampSec * 1000L))
                    incidents.add(Incident(timestampSec = timestampSec, parts = listOf(uriStr), date = date))
                }
            }
        }
        return incidents.sortedByDescending { it.timestampSec }
    }

    private fun extractTimestamp(filename: String): Long? {
        return pattern.find(filename)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun extractPartIndexFromName(filename: String): Int {
        return pattern.find(filename)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    }
}
