package app.tandem.data

import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

interface TandemApi {
    @POST("auth/register") suspend fun register(@Body b: RegisterBody): AuthResponse
    @POST("auth/login") suspend fun login(@Body b: LoginBody): AuthResponse

    @GET("me") suspend fun me(): MeResponse
    @PATCH("me") suspend fun updateProfile(@Body b: ProfileBody): User
    @PUT("me/avatar") suspend fun uploadAvatar(@Body body: RequestBody): User

    @POST("couple") suspend fun createCouple(): CoupleInfo
    @POST("couple/join") suspend fun joinCouple(@Body b: JoinBody): CoupleInfo
    @PATCH("couple") suspend fun setAnniversary(@Body b: AnniversaryBody): CoupleInfo

    @POST("moods") suspend fun postMood(@Body b: MoodBody): MoodEntry
    @GET("moods") suspend fun moods(@Query("limit") limit: Int = 100, @Query("before") before: Long? = null): MoodsResponse

    @POST("diary") suspend fun postDiary(@Body b: DiaryBody): DiaryEntry
    @GET("diary") suspend fun diary(@Query("limit") limit: Int = 50, @Query("before") before: Long? = null): DiaryResponse
    @DELETE("diary/{id}") suspend fun deleteDiary(@Path("id") id: String)

    @GET("events") suspend fun events(): EventsResponse
    @POST("events") suspend fun addEvent(@Body b: EventBody): CalendarEvent
    @PUT("events/{id}") suspend fun updateEvent(@Path("id") id: String, @Body b: EventBody): CalendarEvent
    @DELETE("events/{id}") suspend fun deleteEvent(@Path("id") id: String)

    @GET("sync") suspend fun sync(): SyncResponse
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

object ApiFactory {
    fun client(tokenProvider: () -> String?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = tokenProvider()
            val req = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
            chain.proceed(req)
        }
        .build()

    fun create(baseUrl: String, client: OkHttpClient): TandemApi =
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(TandemApi::class.java)
}
