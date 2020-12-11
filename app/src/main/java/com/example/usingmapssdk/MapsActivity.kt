package com.example.usingmapssdk

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlacePicker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.annotations.NotNull

import java.util.*

@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var map: GoogleMap
    //used in getting the users location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var usersLastLocation: Location
    //used for getting location updates
    private lateinit var locationReq: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var locationUpdateState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                if (p0 != null) {
                    usersLastLocation = p0.lastLocation
                }
                placeMarker(LatLng(usersLastLocation.latitude, usersLastLocation.longitude))
            }
        }

        hanldeLocationRequest()
        //handles the autocomplete search
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            loadPlace()
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        //turns the ability to zoom on
        map.getUiSettings().setZoomControlsEnabled(true)
        map.setOnMarkerClickListener(this)
        settingUpMap()
    }
    //if we do not have permission then get the position. Use the fusedLocationClient to
    // get the users last location, and put a blue dot there
    private fun settingUpMap() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                location_permission
            )
            return
        }
        //creates the blue dot for the users location
        map.isMyLocationEnabled = true
        //changes the type of the map, other options would be HYBRID, SATELLITE, NORMAL
        map.mapType = GoogleMap.MAP_TYPE_TERRAIN
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null){
                usersLastLocation = location
                val latlong = LatLng(location.latitude, location.longitude)
                //put a marker on the users location
                placeMarker(latlong)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latlong, 12f))
            }
        }
    }
    // put a marker on the map at the users location
    private fun placeMarker(location: LatLng) {
        val myMarker = MarkerOptions().position(location)
        //gets the address and makes it the title of the marker
        myMarker.title("Marker")
        //places the marker on the map
        map.addMarker(myMarker)
    }
    // requests to use location updates
    private fun locationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                location_permission
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(locationReq, locationCallback, null)
    }
    // handles location updates
    private fun hanldeLocationRequest(){
        locationReq = LocationRequest()
        //how fast the app can update the location
        locationReq.interval = 10000
        locationReq.fastestInterval = 5000
        locationReq.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        val build = LocationSettingsRequest.Builder().addLocationRequest(locationReq)
        val settingsClient = LocationServices.getSettingsClient(this)
        //creates instance of location settings request
        val checkSettings = settingsClient.checkLocationSettings(build.build())
        checkSettings.addOnSuccessListener {
            locationUpdateState = true
            locationUpdates()
        }
        checkSettings.addOnFailureListener { temp ->
            if (temp is ResolvableApiException) {
                try {
                    //if we do not have the proper location settings, ask for them
                    temp.startResolutionForResult(this@MapsActivity, request_settings)
                } catch (sendEx: IntentSender.SendIntentException){

                }
            }
        }
    }
    // start the update request for location
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == request_settings) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                locationUpdates()
            }
        }
        //get the address of the updated location
        if (requestCode == request_settings) {
            if (resultCode == RESULT_OK) {
                val place = PlacePicker.getPlace(this, data)
                var addressText = place.name.toString()
                addressText += "\n" + place.address.toString()

                placeMarker(place.latLng)
            }
        }
    }
    // stops location update request
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // restart the location update request
    public override fun onResume() {
        super.onResume()
        if (!locationUpdateState) {
            locationUpdates()
        }
    }
    // creates an intent for place picker. This lets us search places
    private fun loadPlace() {
        val build = PlacePicker.IntentBuilder()
        try {
            startActivityForResult(build.build(this@MapsActivity), place_request)
        } catch (e: GooglePlayServicesRepairableException){
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException){
            e.printStackTrace()
        }
    }




    override fun onMarkerClick(p0: Marker?) = false

    companion object {
        private const val location_permission = 1
        private const val request_settings = 2
        private const val place_request = 3
    }


}