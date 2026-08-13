package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.model.BpmStatus
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSource
import com.example.data.model.Track
import com.example.data.model.TrackSource
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ArchiveAudioImporter {

    fun importFolder(context: Context, folderUri: Uri): Playlist {
        val timestamp = System.currentTimeMillis()
        val playlistId = "custom_folder_$timestamp"
        val outputDir = File(context.filesDir, "imported_playlists/$playlistId").apply { mkdirs() }
        val extractedFiles = mutableListOf<File>()

        try {
            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)
            scanDirectory(context, folderUri, childrenUri, outputDir, extractedFiles)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val folderName = getFolderNameFromUri(context, folderUri) ?: "Imported Music Folder"
        return createPlaylistFromAudioFiles(context, playlistId, folderName, extractedFiles)
    }

    fun importArchivesOrAudioFiles(
        context: Context,
        uris: List<Uri>,
        playlistNameOverride: String? = null
    ): Playlist {
        val timestamp = System.currentTimeMillis()
        val playlistId = "custom_archive_$timestamp"
        val outputDir = File(context.filesDir, "imported_playlists/$playlistId").apply { mkdirs() }
        val extractedFiles = mutableListOf<File>()

        for (uri in uris) {
            val fileName = getFileNameFromUri(context, uri).lowercase()
            val mimeType = context.contentResolver.getType(uri) ?: ""

            try {
                when {
                    fileName.endsWith(".zip") -> {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            extractedFiles.addAll(extractZipStream(stream, outputDir))
                        }
                    }
                    fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") -> {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            extractedFiles.addAll(extractTarGzStream(stream, isGzip = true, outputDir))
                        }
                    }
                    fileName.endsWith(".tar") -> {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            extractedFiles.addAll(extractTarGzStream(stream, isGzip = false, outputDir))
                        }
                    }
                    isAudioFileName(fileName) || mimeType.startsWith("audio/") -> {
                        val cleanName = File(getFileNameFromUri(context, uri)).name
                        val outFile = File(outputDir, cleanName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        extractedFiles.add(outFile)
                    }
                    else -> {
                        // Fallback: try zip stream extraction in case extension was stripped
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            extractedFiles.addAll(extractZipStream(stream, outputDir))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val defaultTitle = playlistNameOverride ?: if (uris.size == 1) {
            val name = getFileNameFromUri(context, uris.first()).substringBeforeLast(".")
            "Imported Archive ($name)"
        } else {
            "Custom Imported Audio Mix"
        }

        return createPlaylistFromAudioFiles(context, playlistId, defaultTitle, extractedFiles)
    }

    private fun scanDirectory(
        context: Context,
        folderUri: Uri,
        childrenUri: Uri,
        outputDir: File,
        outFiles: MutableList<File>
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val displayName = cursor.getString(nameCol) ?: "file"
                    val mimeType = cursor.getString(mimeCol) ?: ""

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
                        scanDirectory(context, folderUri, subChildrenUri, outputDir, outFiles)
                    } else if (isAudioFileName(displayName.lowercase()) || mimeType.startsWith("audio/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val outFile = File(outputDir, File(displayName).name)
                        context.contentResolver.openInputStream(docUri)?.use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        outFiles.add(outFile)
                    } else if (isArchiveFileName(displayName.lowercase())) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        context.contentResolver.openInputStream(docUri)?.use { stream ->
                            outFiles.addAll(extractArchiveStream(stream, displayName.lowercase(), outputDir))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractArchiveStream(inputStream: InputStream, name: String, outputDir: File): List<File> {
        return when {
            name.endsWith(".zip") -> extractZipStream(inputStream, outputDir)
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTarGzStream(inputStream, isGzip = true, outputDir)
            name.endsWith(".tar") -> extractTarGzStream(inputStream, isGzip = false, outputDir)
            else -> extractZipStream(inputStream, outputDir)
        }
    }

    private fun extractZipStream(inputStream: InputStream, outputDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && isAudioFileName(name.lowercase())) {
                        val cleanName = File(name).name
                        val outFile = File(outputDir, cleanName)
                        outFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedFiles.add(outFile)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractedFiles
    }

    private fun extractTarGzStream(inputStream: InputStream, isGzip: Boolean, outputDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        try {
            val rawStream = if (isGzip) GZIPInputStream(BufferedInputStream(inputStream)) else BufferedInputStream(inputStream)
            val header = ByteArray(512)

            while (true) {
                var bytesRead = 0
                while (bytesRead < 512) {
                    val count = rawStream.read(header, bytesRead, 512 - bytesRead)
                    if (count < 0) break
                    bytesRead += count
                }
                if (bytesRead < 512) break
                if (header.all { it == 0.toByte() }) break

                val nameEnd = header.indexOfFirst { it == 0.toByte() }.let { if (it in 0..99) it else 100 }
                val name = String(header, 0, nameEnd, Charsets.US_ASCII).trim()

                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim().replace("\u0000", "")
                val fileSize = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }

                if (fileSize > 0 && isAudioFileName(name.lowercase())) {
                    val outFile = File(outputDir, File(name).name)
                    outFile.outputStream().use { fos ->
                        var remaining = fileSize
                        val buf = ByteArray(4096)
                        while (remaining > 0) {
                            val readCount = rawStream.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (readCount < 0) break
                            fos.write(buf, 0, readCount)
                            remaining -= readCount
                        }
                    }
                    extractedFiles.add(outFile)

                    val padding = (512 - (fileSize % 512)) % 512
                    if (padding > 0) {
                        var skipped = 0L
                        while (skipped < padding) {
                            val count = rawStream.skip(padding - skipped)
                            if (count <= 0) break
                            skipped += count
                        }
                    }
                } else if (fileSize > 0) {
                    var remaining = fileSize
                    val buf = ByteArray(4096)
                    while (remaining > 0) {
                        val readCount = rawStream.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (readCount < 0) break
                        remaining -= readCount
                    }
                    val padding = (512 - (fileSize % 512)) % 512
                    if (padding > 0) {
                        var skipped = 0L
                        while (skipped < padding) {
                            val count = rawStream.skip(padding - skipped)
                            if (count <= 0) break
                            skipped += count
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractedFiles
    }

    private fun createPlaylistFromAudioFiles(
        context: Context,
        playlistId: String,
        playlistName: String,
        files: List<File>
    ): Playlist {
        val coverImages = listOf(
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop"
        )

        val tracks = files.mapIndexed { index, file ->
            val retriever = MediaMetadataRetriever()
            var title = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
            var artist = "Custom Track"
            var durationMs = 210000L
            var albumArtUrl = coverImages[index % coverImages.size]

            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                    if (it.isNotBlank()) title = it.trim()
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                    if (it.isNotBlank()) artist = it.trim()
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    if (it > 0) durationMs = it
                }
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val artFile = File(file.parentFile, "art_${file.nameWithoutExtension}.jpg")
                    artFile.writeBytes(artBytes)
                    albumArtUrl = artFile.toURI().toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

            // If artist was not found in ID3 tag, parse common filename patterns: "Artist - Title" or "01 - Artist - Title"
            if (artist == "Custom Track" || artist.isBlank()) {
                val rawName = file.nameWithoutExtension
                val splitParts = if (rawName.contains(" - ")) {
                    rawName.split(" - ")
                } else if (rawName.contains("_-_")) {
                    rawName.split("_-_")
                } else null

                if (splitParts != null && splitParts.size >= 2) {
                    val cleanParts = splitParts.map { it.trim().replace("_", " ") }
                    if (cleanParts.size == 2) {
                        artist = cleanParts[0]
                        title = cleanParts[1]
                    } else if (cleanParts.size >= 3) {
                        // e.g. "01 - Artist - Title"
                        artist = cleanParts[1]
                        title = cleanParts.drop(2).joinToString(" - ")
                    }
                }
            }

            Track(
                id = "tr_local_${System.currentTimeMillis()}_$index",
                title = title,
                artist = artist,
                albumArtUrl = albumArtUrl,
                durationMs = durationMs,
                streamUrl = file.absolutePath,
                bpm = 124,
                bpmStatus = BpmStatus.UNKNOWN,
                musicalKey = "Unknown",
                source = TrackSource.LOCAL_FILE,
                sourceUrl = file.absolutePath,
                introOffsetSec = 0,
                segmentDurationSec = 90
            )
        }

        return Playlist(
            id = playlistId,
            name = playlistName,
            description = "Custom Local Playlist (${tracks.size} tracks: MP3, FLAC, WAV, ZIP/TAR)",
            coverUrl = tracks.firstOrNull()?.albumArtUrl ?: coverImages.first(),
            source = PlaylistSource.CUSTOM,
            genre = "Custom Local Mix",
            avgBpm = 126,
            tracks = tracks
        )
    }

    private fun isAudioFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") ||
                lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg")
    }

    private fun isArchiveFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar") || lower.endsWith(".rar")
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "file"
                    }
                }
            }
        } catch (e: Exception) {
            name = uri.lastPathSegment ?: "file"
        }
        return name
    }

    private fun getFolderNameFromUri(context: Context, uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfterLast("/").substringAfterLast(":")
        } catch (e: Exception) {
            getFileNameFromUri(context, uri)
        }
    }
}
