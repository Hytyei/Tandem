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
    @PUT("me/profile") suspend fun saveProfileDetails(@Body b: ProfileDetails): User
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

    @GET("places") suspend fun places(): PlacesResponse
    @POST("places") suspend fun addPlace(@Body b: PlaceBody): Place
    @PUT("places/{id}") suspend fun editPlace(@Path("id") id: String, @Body b: PlaceBody): Place
    @POST("places/{id}/visit") suspend fun visitPlace(@Path("id") id: String): Place
    @PUT("places/{id}/review") suspend fun reviewPlace(@Path("id") id: String, @Body b: ReviewBody): Place
    @DELETE("places/{id}") suspend fun deletePlace(@Path("id") id: String)
    @POST("places/{id}/images") suspend fun uploadPlaceImage(@Path("id") id: String, @Body body: RequestBody): ImageId
    @DELETE("places/images/{id}") suspend fun deletePlaceImage(@Path("id") id: String): OkResponse

    @GET("addresses") suspend fun addresses(): AddressesResponse
    @POST("addresses") suspend fun addAddress(@Body b: AddressBody): SavedAddress
    @PUT("addresses/{id}") suspend fun editAddress(@Path("id") id: String, @Body b: AddressBody): SavedAddress
    @DELETE("addresses/{id}") suspend fun deleteAddress(@Path("id") id: String): OkResponse

    @GET("period") suspend fun period(): PeriodResponse
    @PUT("period/settings") suspend fun periodSettings(@Body b: PeriodSettingsBody): PeriodSettings
    @POST("period") suspend fun addPeriod(@Body b: PeriodBody): PeriodEntry
    @PUT("period/{id}") suspend fun editPeriod(@Path("id") id: String, @Body b: PeriodBody): PeriodEntry
    @DELETE("period/{id}") suspend fun deletePeriod(@Path("id") id: String): OkResponse
    @PUT("period/days/{date}") suspend fun periodDay(@Path("date") date: String, @Body b: PeriodDayBody): PeriodDay
    @GET("notes") suspend fun notes(): NotesResponse
    @POST("notes") suspend fun addNote(@Body b: NoteBody): Note
    @PUT("notes/{id}") suspend fun editNote(@Path("id") id: String, @Body b: NoteBody): Note
    @DELETE("notes/{id}") suspend fun deleteNote(@Path("id") id: String): OkResponse
    @POST("notes/{id}/items") suspend fun addNoteItem(@Path("id") id: String, @Body b: NewItemBody): Note
    @PUT("notes/items/{id}") suspend fun setItemDone(@Path("id") id: String, @Body b: ItemDoneBody): Note
    @PUT("notes/items/{id}") suspend fun setItemText(@Path("id") id: String, @Body b: ItemTextBody): Note
    @DELETE("notes/items/{id}") suspend fun deleteNoteItem(@Path("id") id: String): Note

    @GET("trips") suspend fun trips(): TripsResponse
    @POST("trips") suspend fun startTrip(@Body b: TripStartBody): Trip
    @GET("trips/{id}") suspend fun tripDetail(@Path("id") id: String): TripDetail
    @PUT("trips/{id}") suspend fun renameTrip(@Path("id") id: String, @Body b: TripTitleBody): Trip
    @DELETE("trips/{id}") suspend fun deleteTrip(@Path("id") id: String): OkResponse
    @POST("trips/{id}/join") suspend fun joinTrip(@Path("id") id: String): Trip
    @POST("trips/{id}/leave") suspend fun leaveTrip(@Path("id") id: String): Trip
    @POST("trips/{id}/points") suspend fun sendTripPoints(@Path("id") id: String, @Body b: FixesBody): Trip
    @POST("trips/{id}/end") suspend fun endTrip(@Path("id") id: String): Trip

    @PUT("reactions") suspend fun react(@Body b: ReactionBody): ReactionsResponse

    @GET("games") suspend fun games(): GamesResponse
    @POST("games/matches") suspend fun createMatch(@Body b: MatchBody): Match
    @PUT("games/matches/{id}/score") suspend fun submitScore(@Path("id") id: String, @Body b: ScoreBody): Match
    @DELETE("games/matches/{id}") suspend fun cancelMatch(@Path("id") id: String): OkResponse
    @POST("games/goals") suspend fun addGoal(@Body b: GoalBody): GameGoal
    @PUT("games/goals/{id}/settle") suspend fun settleGoal(@Path("id") id: String): GameGoal
    @DELETE("games/goals/{id}") suspend fun deleteGoal(@Path("id") id: String): OkResponse
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
            val orig = chain.request()
            val url = orig.url
            val b = orig.newBuilder()
            // Wikipedia / iTunes / Open Library / map tiles all ask clients to identify themselves.
            if (orig.header("User-Agent") == null) b.header("User-Agent", Geo.USER_AGENT)
            if (token != null && url.isHttps && url.host == apiHost()) b.header("Authorization", "Bearer $token")
            chain.proceed(b.build())
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
