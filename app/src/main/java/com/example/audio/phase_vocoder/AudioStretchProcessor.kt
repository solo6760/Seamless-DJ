package com.example.audio.phase_vocoder

interface AudioStretchProcessor {
    fun stretch(buffer: FloatArray, ratio: Float): FloatArray
}
