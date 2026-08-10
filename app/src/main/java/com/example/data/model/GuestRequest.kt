package com.example.data.model

data class GuestRequest(
    val id: String,
    val track: Track,
    val requestedBy: String,
    val upvotes: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
