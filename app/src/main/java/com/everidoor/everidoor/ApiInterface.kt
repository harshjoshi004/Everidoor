package com.everidoor.everidoor

import com.everidoor.everidoor.model.LocationPost
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiInterface {

    @POST("location_details")
    fun postLocation(@Body location: LocationPost): Call<LocationPost>
}