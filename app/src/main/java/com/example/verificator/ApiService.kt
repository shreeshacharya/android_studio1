package com.example.verificator

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @Multipart
    @POST("verify")
    suspend fun verifyCertificate(
        @Part image: MultipartBody.Part,
        @Part("register_number") registerNumber: RequestBody? = null
    ): Response<VerifyResponse>

    @GET("record/{register_number}")
    suspend fun getRecord(
        @Path("register_number") registerNumber: String
    ): Response<VerifyResponse>
}
