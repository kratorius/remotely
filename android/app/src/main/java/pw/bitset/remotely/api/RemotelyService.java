package pw.bitset.remotely.api;

import retrofit.Call;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.POST;

public interface RemotelyService {
    String CONTENT_TYPE_JSON = "Content-Type: application/json";

    // Media endpoints.
    @POST("/media/volume_up")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mediaVolumeUp();

    @POST("/media/volume_down")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mediaVolumeDown();

    @POST("/media/volume_mute")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mediaVolumeMute();

    @POST("/media/play")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mediaPlay();

    @POST("/media/pause")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mediaPause();

    // Keyboard endpoints.
    @POST("/keyboard/press")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> keyboardPress(@Body Keycode keycode);

    // Mouse endpoints.
    @POST("/mouse/move")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mouseMove(@Body DeltaCoordinates deltaCoordinates);

    @POST("/mouse/click_left")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mouseClickLeft();

    @POST("/mouse/double_click_left")
    @Headers(CONTENT_TYPE_JSON)
    Call<Void> mouseDoubleClickLeft();

    // _meta endpoints.
    @GET("/_meta/ping")
    @Headers(CONTENT_TYPE_JSON)
    Call<Pong> ping();
}
