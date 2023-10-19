package com.example.openedmaps

//import com.mikirinkode.openstreetmapandroid.databinding.ActivityMainBinding
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.GpsStatus
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.telephony.SmsManager
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import com.example.openedmaps.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class MainActivity : AppCompatActivity(), IMyLocationProvider, MapListener, GpsStatus.Listener {

    //variables
    private val LOCATION_REQUEST_CODE = 100
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var mMyLocationOverlay : MyLocationNewOverlay
    private lateinit var controller: IMapController

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    val hotspotsLocations = listOf(
        Pair("Hluhluwe iMfolozi Park", GeoPoint(-28.219831,31.951865)),
        Pair("Umgeni River Bird Park", GeoPoint(-29.808167,31.017467)),
        Pair("Table Mountain", GeoPoint(-33.9625,18.4039)),
        Pair("Kruger National Park", GeoPoint(-24.9279,31.5249)),
        Pair("Robben Island", GeoPoint(-33.8054,18.3666)),
        Pair("Blyde River Canyon Nature Reserve", GeoPoint(-24.5570,30.8323)),
        Pair("Victoria & Alfred Waterfront", GeoPoint(-33.9036,18.4210)),
        Pair("Durban Beachfront", GeoPoint(-29.8526,31.0257)),
        Pair("Golden Gate Highlands National Park", GeoPoint(-28.5823, 28.5823)),
        Pair("Tsitsikamma National Park", GeoPoint(-33.9702,23.8866)),
        Pair("Sun City", GeoPoint(-29.7999,31.03758)),
        Pair("Drakensberg Mountains", GeoPoint(-29.7999,31.03758)),
        Pair("Addo Elephant National Park", GeoPoint(-33.569469,25.750720)),
        Pair("iSimangaliso Wetland Park", GeoPoint(-27.803262, 32.547096)),
        Pair("Wakkerstroom", GeoPoint(-27.355249,30.162390)),
        Pair("West Coast National Park", GeoPoint(-32.962216,18.115601)),
        Pair("Wilderness National Park", GeoPoint(-33.985609,22.635741)),
        Pair("Mkuze Game Reserve", GeoPoint(-27.595486, 32.037622)),
    )

    private val hotspotMarkers = mutableListOf<Marker>() //initializr an empty list for hotspot markers
    private val noteMap = HashMap<GeoPoint, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //init binding
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE)
        )
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.mapCenter
        mapView.setMultiTouchControls(true)
        mapView.getLocalVisibleRect(Rect())

        mMyLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this),mapView)
        controller = mapView.controller

        mMyLocationOverlay.enableMyLocation()
        mMyLocationOverlay.enableFollowLocation()
        mMyLocationOverlay.isDrawAccuracyEnabled = true

        //set the initial zoom level
        controller.setZoom(6.0)

        mapView.overlays.add(mMyLocationOverlay)
        setupMap()
        mapView.addMapListener(this)

        //check and request location permissions
        managePermissions()


        //create a custom overlay for the animated marker
        val animatedMarkerOverlay = object : Overlay(this) {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                //calculate the latitude and longitude from the geopoint
                val geoPoint = mMyLocationOverlay.myLocation
                latitude = geoPoint.latitude
                longitude = geoPoint.longitude

                //create a custom dialog or info window to display the latitude and longitude
                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(R.layout.custom)

                val latitudeTextView = dialog.findViewById<TextView>(R.id.latitudeTextView)
                val longitudeTextView = dialog.findViewById<TextView>(R.id.longitudeTextView)

                latitudeTextView.text = "Latitude: $latitude"
                longitudeTextView.text = "Longitude: $longitude"

                dialog.show()

                return true
            }
        }

        // add the animatedmarkeroverlay to the map
        mapView.overlays.add(animatedMarkerOverlay)
        //get a reference to the "view hotspots" button
        val viewHotspotsButton = findViewById<Button>(R.id.viewHotspotsButton)

        //add on onclicklistener to the button
        viewHotspotsButton.setOnClickListener {
            //call a function to add hotspot markers when the button is clicked
            addHotspotMarkers()
        }

        val showRoutesButton = findViewById<Button>(R.id.showRoutesButton)
        showRoutesButton.setOnClickListener {
            calculateAndDisplayRoutes()
        }
    }

    private fun calculateAndDisplayRoutes() {
        val startPoint = mMyLocationOverlay.myLocation

        if (startPoint == null)
        {
            Toast.makeText(this,"Location loading error", Toast.LENGTH_SHORT).show()
            return
        }

        for ((startLocationName, endPoint) in hotspotsLocations){
            GlobalScope.launch(Dispatchers.IO){
                val roadManager = OSRMRoadManager(this@MainActivity,"OBP_Tuto/1.0")
                var road: Road? = null
                var retryCount = 0

                while (road == null && retryCount < 3) {
                    road = try{
                        roadManager.getRoad(arrayListOf(startPoint, endPoint))
                    } catch (e: Exception) {
                        null
                    }
                    retryCount++
                }

                withContext(Dispatchers.Main) {
                    if (road != null && road.mStatus == Road.STATUS_OK) {
                        val roadOverlay = RoadManager.buildRoadOverlay(road)
                        mapView.overlays.add(roadOverlay)

                        //Display the route details in an AlertDialog
                        val routeDetails = "Start Location: Your Current Location\nEnd Location: $startLocationName\nDistance: ${road.mLength}"
                        showRouteDetailsDialog(routeDetails)

                        mapView.invalidate()
                    }else {
                        Toast.makeText(this@MainActivity,"Error when loading road - Status=${road?.mStatus ?: "unknown"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showRouteDetailsDialog(routeDetails: String) {
        runOnUiThread {
            val alertDialog = AlertDialog.Builder(this@MainActivity)
            alertDialog.setTitle("Route Details")
            alertDialog.setMessage(routeDetails)
            alertDialog.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.create().show()
        }
    }

    private fun setupMap(){
        Configuration.getInstance().load(this,
            PreferenceManager.getDefaultSharedPreferences(this))
        //mapView = binding.mapView
        mapController = mapView.controller
        mapView.setMultiTouchControls(true)

        //init the start point
        val startPoint = GeoPoint(-29.8587, 31.0218)
        mapController.setCenter(startPoint)
        mapController.setZoom(12.0)

        //create marker for the start point (ic_location)
        val icLocationMarker = Marker(mapView)
        icLocationMarker.position = startPoint
        icLocationMarker.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_location, null)

        //add a click listener to the ic_location marker
        icLocationMarker.setOnMarkerClickListener{marker , mapView ->
            val latitude = marker.position.latitude
            val longitude = marker.position.longitude

            val dialog = Dialog(this@MainActivity)
            dialog.setContentView(R.layout.custom)

            val latitudeTextView = dialog.findViewById<TextView>(R.id.latitudeTextView)
            val longitudeTextView = dialog.findViewById<TextView>(R.id.longitudeTextView)

            latitudeTextView.text = "Latitude: $latitude"
            longitudeTextView.text = "Longitude: $longitude"

            dialog.show()

            true //Return true to indicate that the event is consumed
        }

        // Add the ic_location marker to the mapView
        mapView.overlays.add(icLocationMarker)
    }

    private fun addHotspotMarkers(){
        //clear any existing hotspot markers from the map
        mapView.overlays.removeAll(hotspotMarkers)

        for ((name, location) in hotspotsLocations){
            val marker = Marker(mapView)
            marker.position = location
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_location, null)

            // create a custom dialog for displaying location name and adding a note
            marker.setOnMarkerClickListener { marker, mapView ->
                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(R.layout.custom_marker_dialog)

                val noteEditText = dialog.findViewById<EditText>(R.id.noteEditText)
                val saveNoteButton = dialog.findViewById<Button>(R.id.saveNoteButton)
                val displayNoteTextView = dialog.findViewById<TextView>(R.id.displayNoteTextView)

                //Display the location name
                val locationNameTextView = dialog.findViewById<TextView>(R.id.locationNameTextView)

                //load and display the saved note for this marker
                val savedNote = loadNote(marker.position)
                locationNameTextView.text = savedNote

                saveNoteButton.setOnClickListener {
                    val note = noteEditText.text.toString()
                    saveNote(marker.position, note)
                    displayNoteTextView.text = note
                }

                dialog.show()
                true
            }

            hotspotMarkers.add(marker) // Add the marker to the list
        }

        //add the new hotspot markers to the map
        mapView.overlays.addAll(hotspotMarkers)
        mapView.invalidate() //refresh the map to display the new markers


    }

    private fun saveNote(location: GeoPoint, note: String) {
        noteMap[location] = note
    }

    private fun loadNote(location: GeoPoint): String{
        return noteMap[location] ?: ""
    }

    override fun onScroll(event: ScrollEvent?): Boolean {
        //handle map scroll event here
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        //handle map zoom event here
        return false
    }

    override fun onGpsStatusChanged(p0: Int) {
        //handle GPS status changes here
    }

    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
        //start location provider here
        return true
    }

    override fun stopLocationProvider() {
        //stop location provider here
    }

    override fun getLastKnownLocation(): Location {
        // get last known location here
        return Location("last_known_location")
    }

    override fun destroy() {
        //destroy resources here
    }

    //handle permissions
    private fun isLocationPermissionGranted(): Boolean
    {
        val fineLocation = ActivityCompat.checkSelfPermission(this,
            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ActivityCompat.checkSelfPermission(this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocation && coarseLocation
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE)
        {
            if (grantResults.isNotEmpty())
            {
                for (result in grantResults)
                {
                    if (result == PackageManager.PERMISSION_GRANTED){
                        //handle permission granted
                        //you can re-initialize the map here if needed
                        //setupMap()
                    }
                    else{
                        Toast.makeText(this,"Location permissions denied",Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun managePermissions()
    {
        val requestPermissions = mutableListOf<String>()

        //location permissions
        if (!isLocationPermissionGranted())
        {
            //if theses weren't granted
            requestPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        //sms permission
        if(!isSmsPermissionGranted()){
            requestPermissions.add(android.Manifest.permission.SEND_SMS)
        }

        if (requestPermissions.isNotEmpty())
        {
            ActivityCompat.requestPermissions(this,requestPermissions.toTypedArray(),LOCATION_REQUEST_CODE)
        }
    }

    private fun isSmsPermissionGranted(): Boolean{
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
}