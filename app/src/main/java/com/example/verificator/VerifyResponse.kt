package com.example.verificator

import com.google.gson.annotations.SerializedName

data class VerifyResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("register_number")
    val registerNumber: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("semester")
    val semester: String? = null,

    @SerializedName("result")
    val result: String? = null,

    @SerializedName("college")
    val college: String? = null
)

