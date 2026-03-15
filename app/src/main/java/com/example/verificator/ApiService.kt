package com.example.verificator

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("verify")
    suspend fun verifyCertificate(
        @Part image: MultipartBody.Part
    ): Response<VerifyResponse>
}
