package com.example.progettoappiot

import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    // ── Autenticazione ──────────────────────────────────────────────
    @POST("login")
    fun login(@Body body: Map<String, String>): Call<LoginResponse>

    @POST("register")
    fun register(@Body body: Map<String, String>): Call<RegisterResponse>

    // ── Porta ───────────────────────────────────────────────────────
    @GET("stato_porta")
    fun getStatoPorta(): Call<StatoPortaResponse>

    @POST("apri_porta")
    fun apriPorta(@Body body: Map<String, String>): Call<Void>

    @POST("chiudi_porta")
    fun chiudiPorta(@Body body: Map<String, String>): Call<Void>

    // ── Accessi ─────────────────────────────────────────────────────
    @GET("accessi")
    fun getAccessi(@Query("limit") limit: Int = 5): Call<List<Accesso>>

    @GET("accessi/count")
    fun getAccessiCount(): Call<Map<String, Int>>

    // ── Tag RFID (solo admin) ────────────────────────────────────────
    @GET("tags")
    fun getTags(): Call<List<Tag>>

    @PATCH("tags/{tag_id}")
    fun updateTag(
        @Path("tag_id") tagId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Call<Map<String, Boolean>>

    // ── Utenti (solo admin) ──────────────────────────────────────────
    @GET("users")
    fun getUsers(): Call<List<UserItem>>

    @PATCH("users/{username}")
    fun updateUser(
        @Path("username") username: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Call<GenericResponse>

    @PATCH("users/{username}/password")
    fun changePassword(
        @Path("username") username: String,
        @Body body: Map<String, String>
    ): Call<GenericResponse>

    @POST("users/reset_password")
    fun resetPassword(@Body body: Map<String, String>): Call<GenericResponse>

    @POST("users/request_reset")
    fun requestReset(@Body body: Map<String, String>): Call<GenericResponse>

    @POST("users/approve_reset")
    fun approveReset(@Body body: Map<String, String>): Call<GenericResponse>

    @POST("users/reject_reset")
    fun rejectReset(@Body body: Map<String, String>): Call<GenericResponse>

    @GET("users/pending_resets")
    fun getPendingResets(): Call<List<String>>

    @PATCH("users/{username}/profile_picture")
    fun updateProfilePicture(
        @Path("username") username: String,
        @Body body: Map<String, String>
    ): Call<GenericResponse>

    // ── Notifiche push FCM ──────────────────────────────────────────
    @POST("register_fcm_token")
    fun registerFcmToken(@Body body: Map<String, String>): Call<Map<String, Boolean>>

    @DELETE("tags/{tagId}")
    fun deleteTag(@Path("tagId") tagId: String): Call<GenericResponse>

    @DELETE("users/{username}")
    fun deleteUser(@Path("username") username: String): Call<GenericResponse>
}
