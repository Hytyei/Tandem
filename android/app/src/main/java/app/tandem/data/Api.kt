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
    @HTTP(method = "DELETE", path = "me", hasBody = true) suspend fun deleteAccount(@Body b: DeleteBody): OkResponse
    @PUT("me/avatar") suspend fun uploadAvatar(@Body body: RequestBody): User

    @POST("couple") suspend fun createCouple(): CoupleInfo
    @POST("couple/join") suspend fun joinCouple(@Body b: JoinBody): CoupleInfo
    @POST("couple/leave") suspend fun leaveCouple(): OkResponse
    @PATCH("couple") suspend fun setAnniversary(@Body b: AnniversaryBody): CoupleInfo

    @POST("moods") suspend fun postMood(@Body b: MoodBody): MoodEntry
    @GET("moods") suspend fun moods(@Query("limit") limit: Int = 100, @Query("before") before: Long? = null): MoodsResponse

    @POST("diary") suspend fun postDiary(@Body b: DiaryBody): DiaryEntry
    @GET("diary") suspend fun diary(@Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): DiaryResponse
    @POST("diary/{id}/images") suspend fun uploadDiaryImage(@Path("id") id: String, @Body body: RequestBody): ImageId
    @PUT("diary/{id}") suspend fun updateDiary(@Path("id") id: String, @Body b: DiaryBody): DiaryEntry
    @DELETE("diary/{id}") suspend fun deleteDiary(@Path("id") id: String)
    @DELETE("diary/image/{id}") suspend fun deleteDiaryImage(@Path("id") id: String): OkResponse

    @GET("events") suspend fun events(): EventsResponse
    @POST("events") suspend fun addEvent(@Body b: EventBody): CalendarEvent
    @PUT("events/{id}") suspend fun updateEvent(@Path("id") id: String, @Body b: EventBody): CalendarEvent
    @DELETE("events/{id}") suspend fun deleteEvent(@Path("id") id: String)

    @GET("sync") suspend fun sync(): SyncResponse
    @GET("stats") suspend fun statsDetail(@Query("tz") tzMinutes: Int): StatsDetail
    @GET("memories") suspend fun memories(@Query("date") today: String): Memories
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

object ApiFactory {
    /** The sign-in token is only ever attached to requests for the Tandem server itself (https + its own host). */
    fun client(tokenProvider: () -> String?, apiHost: () -> String?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = tokenProvider()
            val url = chain.request().url
            val req = if (token != null && url.isHttps && url.host == apiHost()) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else chain.request()
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
