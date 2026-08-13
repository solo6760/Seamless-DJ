package com.example.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SongMetadataResult(
    val bpm: Int?,
    val musicalKey: String,
    val camelotKey: String
)

class GeminiBpmService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    private val modelCandidates = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-1.5-flash-lite",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash",
        "gemini-1.5-flash"
    )

    suspend fun validateLowConfidenceMetadata(
        title: String,
        artist: String,
        dspBpm: Int,
        dspKey: String,
        bpmConfidence: Int,
        keyConfidence: Int,
        apiKey: String
    ): SongMetadataResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext SongMetadataResult(dspBpm, dspKey, "")
        }

        val promptText = "I analyzed the song '$title' by '$artist' and detected BPM ~$dspBpm (confidence $bpmConfidence%) and key ~$dspKey (confidence $keyConfidence%), but I'm not confident. Based on what you know, what are the actual BPM and musical key? Respond with: BPM: [number], Key: [key name]."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                    })
                })
            })
            put("tools", JSONArray().apply {
                put(JSONObject().apply { put("googleSearch", JSONObject()) })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        for (model in modelCandidates) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBodyStr = response.body?.string() ?: ""
                        val responseJson = JSONObject(responseBodyStr)
                        val candidates = responseJson.optJSONArray("candidates") ?: return@use
                        if (candidates.length() > 0) {
                            val text = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text", "") ?: ""
                            
                            Log.d("GeminiBpmService", "Validation Response ($model) for '$title': $text")
                            val parsed = parseMetadataResponse(text)
                            if (parsed.bpm != null || parsed.musicalKey != "Unknown") {
                                return@withContext parsed
                            }
                        }
                    } else {
                        Log.w("GeminiBpmService", "Model $model returned HTTP ${response.code}: ${response.message}. Trying next candidate...")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiBpmService", "Exception validating metadata for '$title' with model $model", e)
            }
        }

        // Fallback to standard lookup if specific validation prompt failed
        lookupMetadata(title, artist, apiKey)
    }

    suspend fun lookupMetadata(
        title: String,
        artist: String,
        apiKey: String
    ): SongMetadataResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext SongMetadataResult(null, "Unknown", "")
        }

        val promptText = "What is the musical key and BPM of the song '$title' by '$artist'? Respond with only the key (e.g., C major, G minor) and the BPM number separated by a comma."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                    })
                })
            })
            put("tools", JSONArray().apply {
                put(JSONObject().apply { put("googleSearch", JSONObject()) })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        for (model in modelCandidates) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBodyStr = response.body?.string() ?: ""
                        val responseJson = JSONObject(responseBodyStr)
                        val candidates = responseJson.optJSONArray("candidates") ?: return@use
                        if (candidates.length() > 0) {
                            val text = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text", "") ?: ""
                            
                            Log.d("GeminiBpmService", "Raw Metadata Response ($model) for '$title': $text")
                            val parsed = parseMetadataResponse(text)
                            if (parsed.bpm != null || parsed.musicalKey != "Unknown") {
                                return@withContext parsed
                            }
                        }
                    } else {
                        Log.w("GeminiBpmService", "Model $model returned HTTP ${response.code}: ${response.message}. Trying next candidate...")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiBpmService", "Exception calling model $model for metadata '$title'", e)
            }
        }

        // Fallback: try standard BPM lookup if metadata prompt didn't parse
        val bpmOnly = lookupBpm(title, artist, apiKey)
        SongMetadataResult(bpmOnly, "Unknown", "")
    }

    private fun parseMetadataResponse(text: String): SongMetadataResult {
        val bpm = parseAndValidateBpm(text)
        
        val camelot = com.example.util.CamelotWheel.parseKey(text)
        val camelotCodeStr = camelot?.formatted ?: ""

        val rawKeyClean = text
            .replace(Regex("""\b([4-9]\d|1\d\d|2[0-1]\d|220)\b"""), "")
            .replace(Regex("""(?i)BPM|beats per minute|comma|key:|bpm:"""), "")
            .replace(Regex("""[,\n\r]"""), " ")
            .trim()

        val displayKey = when {
            camelot != null && rawKeyClean.isNotBlank() -> "${camelot.formatted} / $rawKeyClean"
            camelot != null -> camelot.formatted
            rawKeyClean.isNotBlank() -> rawKeyClean
            else -> "Unknown"
        }

        return SongMetadataResult(bpm, displayKey, camelotCodeStr)
    }

    suspend fun lookupBpm(

        title: String,
        artist: String,
        apiKey: String
    ): Int? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            Log.w("GeminiBpmService", "API key is blank, skipping Gemini BPM lookup.")
            return@withContext null
        }

        val promptText = "What is the BPM (beats per minute) of the song '$title' by '$artist'? Respond with only the number."

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", promptText)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val toolsArray = JSONArray().apply {
                val searchToolObj = JSONObject().apply {
                    put("googleSearch", JSONObject())
                }
                put(searchToolObj)
            }
            put("tools", toolsArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        for (model in modelCandidates) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBodyStr = response.body?.string() ?: ""
                        val responseJson = JSONObject(responseBodyStr)
                        val candidates = responseJson.optJSONArray("candidates") ?: return@use
                        if (candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text", "")
                                Log.d("GeminiBpmService", "Raw Grounded Response ($model) for '$title': $text")
                                val bpm = parseAndValidateBpm(text)
                                if (bpm != null) {
                                    return@withContext bpm
                                }
                            }
                        }
                    } else {
                        val errBody = response.body?.string() ?: ""
                        Log.w("GeminiBpmService", "Model $model returned HTTP ${response.code}: $errBody")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiBpmService", "Exception calling model $model for '$title'", e)
            }
        }

        // Final fallback: try without tools if search grounding fails on all models
        for (model in modelCandidates) {
            try {
                val simpleJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                            })
                        })
                    })
                }
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(simpleJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.isNotBlank()) {
                            val jsonObj = JSONObject(bodyStr)
                            val text = jsonObj.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text", "") ?: ""
                            val bpm = parseAndValidateBpm(text)
                            if (bpm != null) return@withContext bpm
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback exceptions
            }
        }

        null
    }

    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext false

        // 1. Direct validation via GET models endpoint (Standard Google API key check)
        try {
            val listModelsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
            val listRequest = Request.Builder().url(listModelsEndpoint).get().build()
            client.newCall(listRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GeminiBpmService", "API Key validated successfully via GET models endpoint.")
                    return@withContext true
                } else {
                    val errStr = response.body?.string() ?: ""
                    Log.w("GeminiBpmService", "GET models returned HTTP ${response.code}: $errStr")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiBpmService", "Error testing key via GET models", e)
        }

        // 2. Secondary test via generateContent on model candidates
        for (model in modelCandidates) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Hi") })
                            })
                        })
                    })
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("GeminiBpmService", "API Key validated successfully via $model generateContent.")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                // Try next candidate
            }
        }

        false
    }

    private fun parseAndValidateBpm(text: String): Int? {
        val regex = Regex("""\b([4-9]\d|1\d\d|2[0-1]\d|220)\b""")
        val match = regex.find(text)
        if (match != null) {
            val number = match.value.toIntOrNull()
            if (number != null && number in 40..220) {
                return number
            }
        }

        val digitsRegex = Regex("""\b\d{2,3}\b""")
        val matches = digitsRegex.findAll(text)
        for (m in matches) {
            val num = m.value.toIntOrNull()
            if (num != null && num in 40..220) {
                return num
            }
        }

        return null
    }
}

