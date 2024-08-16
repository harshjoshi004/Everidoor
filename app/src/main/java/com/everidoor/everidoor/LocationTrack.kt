package com.everidoor.everidoor

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationTrack(private val mContext: Context) : Service(), LocationListener {
    private var checkGPS = false
    private var checkNetwork = false
    private var loc: Location? = null
    private var latitude = "-180.0"
    private var longitude = "-90.0"
    private val MIN_DISTANCE_CHANGE_FOR_UPDATES: Long = 10
    private val MIN_TIME_BW_UPDATES: Long = 1000 * 60 * 1
    private var locationManager: LocationManager? = null

    init {
        getLocation()
    }

    private fun getLocation(): Location? {
        try {
            locationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            checkGPS = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            checkNetwork = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

            if (!checkGPS && !checkNetwork) {
                // No provider is enabled
                return null
            }

            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Permissions are not granted
                return null
            }

            if (checkGPS) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat(),
                    this
                )
                loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else if (checkNetwork) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat(),
                    this
                )
                loc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            loc?.let {
                latitude = it.latitude.toString()
                longitude = it.longitude.toString()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return loc
    }

    fun getLongitude(): String {
        loc?.let {
            longitude = it.longitude.toString()
        }
        return longitude
    }

    fun getLatitude(): String {
        loc?.let {
            latitude = it.latitude.toString()
        }
        return latitude
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onLocationChanged(location: Location) {}
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}