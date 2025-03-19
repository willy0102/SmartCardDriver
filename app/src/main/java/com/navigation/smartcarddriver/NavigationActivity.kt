package com.navigation.smartcarddriver

import android.annotation.SuppressLint
import com.google.android.gms.location.LocationRequest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.common.location.Location
import com.mapbox.common.location.LocationService
import com.mapbox.common.location.LocationServiceFactory
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.observable.model.RenderMode
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.linear
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.building.api.MapboxBuildingsApi
import com.mapbox.navigation.ui.maps.building.view.MapboxBuildingView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp.lifecycleOwner
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation

import com.mapbox.navigation.core.trip.session.LocationObserver

import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.turf.TurfMeasurement
import com.mapbox.navigation.ui.maps.camera.view.MapboxRecenterButton
import kotlinx.coroutines.launch


import java.util.Locale


class NavigationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private val mapboxNavigation by requireMapboxNavigation()
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var requestID: String
    private lateinit var userID: String
    private lateinit var driverID: String
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var maneuverView: MapboxManeuverView
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer
    private lateinit var tvHealthIssues: TextView
    private lateinit var tvAllergy: TextView
    private lateinit var tvPrescriptions: TextView
    private lateinit var tvRelativesPhone: TextView
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var mapboxMap: MapboxMap
    private lateinit var recenterButton: MapboxRecenterButton
    private lateinit var tts: TextToSpeech
    private lateinit var sortedHospitalsWithDetails: List<Triple<DocumentSnapshot, Double, String?>>


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the access token before setting up MapboxNavigationApp
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this@NavigationActivity)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )
        setContentView(R.layout.navigation_activity)
        mapView = findViewById(R.id.mapView)
        mapboxMap = mapView.getMapboxMap()

        maneuverView = findViewById(R.id.maneuverView)
        firestore = FirebaseFirestore.getInstance()
        initializeNavigation()
        initializeManeuverApi()
        initializeVoiceInstructions()


        driverID = intent.getStringExtra("driverID") ?: ""
        requestID = intent.getStringExtra("requestID") ?: ""
        userID = intent.getStringExtra("userID") ?: ""

        val btnCallPatient = findViewById<Button>(R.id.btn_Callpatient)
        val btnEndTrip = findViewById<Button>(R.id.btn_endTrip)
        val btnHospitalList = findViewById<Button>(R.id.btnHospital)
        val HospitalList = findViewById<ScrollView>(R.id.HospitalList)

        if (requestID.isNotEmpty()) {
            fetchUserLocationFromFirestore()
        } else {
            Toast.makeText(this, "Request ID is missing", Toast.LENGTH_SHORT).show()
        }

        tvHealthIssues = findViewById(R.id.tvHealthIssues)
        tvAllergy = findViewById(R.id.tvAllergy)
        tvPrescriptions = findViewById(R.id.tvPrescriptions)
        tvRelativesPhone = findViewById(R.id.tvRelativesPhone)
        // Find the button and layout
        val btnShowHealthInfo: Button = findViewById(R.id.btnShowHealthInfo)
        val healthInfoLayout: LinearLayout = findViewById(R.id.healthInfoLayout)
        if (userID.isNotEmpty()) {
            // Fetch patient data based on the userID
            fetchPatientData(userID)
        } else {
            Toast.makeText(this, "User ID is missing", Toast.LENGTH_SHORT).show()
        }
        // Initially hide  layout
        HospitalList.visibility = View.GONE
        healthInfoLayout.visibility = View.GONE
        fetchNearbyHospitals(requestID)

        // Set up the button click listener to toggle health info visibility
        btnShowHealthInfo.setOnClickListener {
            if (healthInfoLayout.visibility == View.GONE) {
                // Show the health info layout
                healthInfoLayout.visibility = View.VISIBLE
            } else {
                // Hide the health info layout
                healthInfoLayout.visibility = View.GONE
            }
        }
        btnHospitalList.setOnClickListener {
            if (HospitalList.visibility == View.GONE) {
                // Show the health info layout
                HospitalList.visibility = View.VISIBLE
                showHospitalList(sortedHospitalsWithDetails)
            } else {
                // Hide the health info layout
                HospitalList.visibility = View.GONE
            }
        }
        // Xử lý khi ấn nút gọi bệnh nhân
        btnCallPatient.setOnClickListener {
            firestore.collection("users").document(userID)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val phoneNumber = documentSnapshot.getString("phoneNumber")
                        if (!phoneNumber.isNullOrEmpty()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "Số điện thoại không tồn tại!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lỗi lấy số điện thoại!", Toast.LENGTH_SHORT).show()
                }
        }


        // Xử lý khi ấn nút kết thúc chuyến đi
        btnEndTrip.setOnClickListener {
            val driverRef = firestore.collection("drivers").document(driverID)
            val requestRef = firestore.collection("ride_requests").document(requestID) // Đổi từ "requests" thành "ride_requests"

            // Cập nhật trạng thái requestID thành "completed"
            requestRef.update("status", "completed")
                .addOnSuccessListener {
                    // Khi cập nhật request thành công, tiếp tục cập nhật số chuyến của tài xế
                    driverRef.update("trip", FieldValue.increment(1))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Đã cập nhật số chuyến!", Toast.LENGTH_SHORT).show()
                            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                            mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
                            speechApi.cancel()
                            voiceInstructionsPlayer.shutdown()
                            // Quay lại HomeActivity
                            val intent = Intent(this, HomeActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Lỗi cập nhật số chuyến!", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lỗi cập nhật trạng thái request!", Toast.LENGTH_SHORT).show()
                }
        }

        enableLocationComponent()
        initializeAnnotations()

    }
    private fun showHospitalList(hospitals: List<Triple<DocumentSnapshot, Double, String?>>) {
        val hospitalListView = findViewById<ListView>(R.id.hospital_list)

        // Map the list to display hospital names and distances
        val hospitalNamesWithDistances = hospitals.map { triple ->
            val hospitalName = triple.first.id
            val distance = (Math.round(triple.second / 1000))

            "$hospitalName : $distance km"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, hospitalNamesWithDistances)
        hospitalListView.adapter = adapter

        hospitalListView.setOnItemClickListener { _, _, position, _ ->
            val phoneNumber = hospitals[position].third

            if (!phoneNumber.isNullOrEmpty()) {
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(callIntent)
            } else {
                Toast.makeText(this, "Bệnh viện này không có số điện thoại", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun initializeAnnotations() {
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
    }
    private fun fetchNearbyHospitals(requestID: String) {
        val firestore = FirebaseFirestore.getInstance()

        // Step 1: Fetch User Location from `ride_requests`
        firestore.collection("ride_requests").document(requestID)
            .get()
            .addOnSuccessListener { rideRequest ->
                val userLat = rideRequest.getDouble("userLat")
                val userLng = rideRequest.getDouble("userLng")

                if (userLat != null && userLng != null) {
                    val userPoint = Point.fromLngLat(userLng, userLat)

                    // Step 2: Fetch Hospitals from `hospitals`
                    firestore.collection("hospitals")
                        .get()
                        .addOnSuccessListener { hospitalDocuments ->
                            val nearbyHospitalsWithDetails = mutableListOf<Triple<DocumentSnapshot, Double, String?>>()

                            for (hospital in hospitalDocuments) {
                                val hospitalLat = hospital.getDouble("latitude")
                                val hospitalLng = hospital.getDouble("longitude")
                                val phoneNumber = hospital.getString("phone_number") // Fetch phone number

                                if (hospitalLat != null && hospitalLng != null) {
                                    val hospitalPoint = Point.fromLngLat(hospitalLng, hospitalLat)

                                    // Calculate distance
                                    val distance = Math.abs(TurfMeasurement.distance(userPoint, hospitalPoint, "meters"))

                                    if (distance <= 20000) { // Within 20km
                                        nearbyHospitalsWithDetails.add(Triple(hospital, distance, phoneNumber))

                                    }
                                }
                            }

                            // Sort hospitals by distance (nearest to farthest)
                            val sortedHospitals: List<DocumentSnapshot> = nearbyHospitalsWithDetails
                                .sortedBy { it.second } // Sort by distance
                                .map { it.first } // Extract DocumentSnapshot
                            sortedHospitalsWithDetails= nearbyHospitalsWithDetails.sortedBy { it.second } // Sort by distance

                            showHospitalsOnMap(userLat, userLng, sortedHospitals)

                        }
                } else {
                    Log.e("Location", "User location not found in ride_requests")
                }
            }
    }



    private fun showHospitalsOnMap(userLat: Double, userLng: Double, hospitals: List<DocumentSnapshot>) {
        addMarker(userLat, userLng, "Patient Location", R.drawable.patient_marker)

        for (hospital in hospitals) {
            val hospitalLat = hospital.getDouble("latitude") ?: continue
            val hospitalLng = hospital.getDouble("longitude") ?: continue
            val hospitalName = hospital.getString("name") ?: "Hospital"

            addMarker(hospitalLat, hospitalLng, hospitalName, R.drawable.hospital_marker)
        }

        Toast.makeText(this, "${hospitals.size} hospitals found within 20km", Toast.LENGTH_SHORT).show()
    }

    fun addMarker(latitude: Double, longitude: Double, title: String, drawableRes: Int) {
        val point = Point.fromLngLat(longitude, latitude)
        val drawable = ContextCompat.getDrawable(this, drawableRes)
        val bitmap = drawable?.let { drawableToBitmap(it) }

        if (bitmap != null) {
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)

            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun enableLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = ContextCompat.getDrawable(this@NavigationActivity, R.drawable.mapbox_user_puck_icon),
                shadowImage = ContextCompat.getDrawable(this@NavigationActivity, R.drawable.mapbox_user_icon_shadow),
            )
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)

    }


    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }


    private fun fetchPatientData(userID: String) {
        // Get the patient's document from Firestore using their userID
        firestore.collection("users").document(userID)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    // Extract data from Firestore
                    val healthIssues = documentSnapshot.getString("healthIssues") ?: "No data"
                    val allergy = documentSnapshot.getString("allergy") ?: "No data"
                    val prescriptions = documentSnapshot.getString("prescriptions") ?: "No data"
                    val relativesPhone = documentSnapshot.getString("relativesPhone") ?: "No data"

                    // Display the data in the TextViews
                    tvHealthIssues.text = healthIssues
                    tvAllergy.text = allergy
                    tvPrescriptions.text = prescriptions
                    tvRelativesPhone.text = relativesPhone
                } else {
                    // If the document does not exist
                    Toast.makeText(this, "Patient not found!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // Handle any errors
                Toast.makeText(this, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }




    private fun initializeNavigation() {


        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(
                NavigationOptions.Builder(this@NavigationActivity)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            )

            Log.d("MapboxSetup", "MapboxNavigationApp is set up")
        } else {
            Log.d("MapboxSetup", "MapboxNavigationApp is already set up")
        }

        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera = NavigationCamera(
            mapboxMap,
            mapView.camera,
            viewportDataSource
        )
        // Initialize RouteLineApi and RouteLineView
        val mapboxRouteLineOptions = MapboxRouteLineOptions.Builder(this)
            .withRouteLineBelowLayerId("road-label")
            .build()
        routeLineApi = MapboxRouteLineApi(mapboxRouteLineOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineOptions)



        MapboxNavigationApp.registerObserver(object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
            }
        })
    }
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { result ->
                mapboxMap.getStyle { style ->
                    routeLineView.renderRouteDrawData(style, result)
                }
            }
        } else {
            // Handle case when there are no routes
        }
    }


    private fun initializeManeuverApi() {
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this).build()
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)

        )
    }

    private fun initializeVoiceInstructions() {


        // Create TextToSpeech instance
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set Samsung TTS as the engine
                val samsungTTS = "com.samsung.SMT" // Samsung TTS engine package name
                val result = tts.setEngineByPackageName(samsungTTS)

                // Check if the engine was set successfully
                if (result == TextToSpeech.SUCCESS) {
                    tts.language = Locale("vi")
                } else {
                    // Handle case where Samsung TTS engine fails
                    println("Failed to set Samsung TTS engine")
                }
            } else {
                println("TextToSpeech initialization failed")
            }
        }
        speechApi = MapboxSpeechApi(
            this,
            accessToken = getString(R.string.mapbox_access_token),
            Locale("vi", "VN").language
        )
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
            this,
            Locale("vi", "VN").language
        )
    }



    private fun fetchUserLocationFromFirestore() {

        firestore.collection("ride_requests").document(requestID)
            .get()
            .addOnSuccessListener { document ->
                val userLat = document.getDouble("userLat")
                val userLng = document.getDouble("userLng")
                if (userLat != null && userLng != null) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

// Request the current location
                    fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let {
                                val originLongitude = it.longitude
                                val originLatitude = it.latitude

                                Log.d(
                                    "LocationDebug_01",
                                    "Origin - Longitude: $originLongitude, Latitude: $originLatitude"
                                )
                                Log.d(
                                    "LocationDebug_02",
                                    "Destination - Longitude: $userLng, Latitude: $userLat"
                                )

                                // Use these coordinates as your origin
                                val origin = Point.fromLngLat(originLongitude, originLatitude)
                                val destination = Point.fromLngLat(userLng, userLat)

                                requestRoute(origin, destination)
                            } ?: run {
                                Log.e("LOCATION", "Failed to get current location")
                            }
                        }


                }
            }
    }




    private fun requestRoute(origin: Point, destination: Point) {
        Log.d("NavigationDebug", "Requesting route from $origin to $destination")

        // Request route from Mapbox Navigation
        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(listOf(origin, destination))
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    if (routes.isNotEmpty()) {
                        Log.d("NavigationDebug", "Routes ready: ${routes.size}")

                        // Gọi navigation
                        setRouteAndStartNavigation(routes)

                        // Xóa dòng này nếu đã gọi trong `setRouteAndStartNavigation`
                        // navigationCamera.requestNavigationCameraToOverview()
                    } else {
                        Log.e("NavigationError", "No routes found")
                        Toast.makeText(
                            this@NavigationActivity,
                            "No routes found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    val errorMessages = reasons.joinToString { it.message ?: "Unknown error" }
                    Log.e("NavigationError", "Route request failed: $errorMessages")

                    Toast.makeText(
                        this@NavigationActivity,
                        "Route request failed: $errorMessages",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: RouterOrigin
                ) {
                    Log.e("NavigationError", "Route request canceled from $routerOrigin")

                    Toast.makeText(
                        this@NavigationActivity,
                        "Route request canceled from $routerOrigin",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                })
    }



    private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {
        if (routes.isNotEmpty()) {
            Log.d("NavigationDebug", "Setting route and starting navigation")

            // Set route cho navigation
            mapboxNavigation.setNavigationRoutes(routes)
            Log.d("NavigationDebug", "Routes set: ${routes.size}")

            // Start the trip session
            mapboxNavigation.startTripSession()
            // Request the camera to follow the navigation
            navigationCamera.requestNavigationCameraToOverview(
                stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                    .maxDuration(3000)
                    .build()
            )
            // Vẽ tuyến đường lên bản đồ

            // Show the maneuver view
            maneuverView.visibility = View.VISIBLE
            Log.d("NavigationDebug", "setRouteAndStartNavigation is being called")
            Toast.makeText(this, "Navigation started", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("NavigationError", "No routes found")
            Toast.makeText(this, "No routes found", Toast.LENGTH_SHORT).show()
        }
    }


    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // Get maneuvers and render them in the maneuver view
        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuverView.renderManeuvers(maneuvers)
        Log.d("NavigationDebug", "Rendered maneuvers: $maneuvers")
    }

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        // Generate and play voice instructions
        speechApi.generate(voiceInstructions, speechCallback)
        Log.d("NavigationDebug", "Voice instructions generated: $voiceInstructions")
    }

    private val speechCallback = MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
        expected.fold(
            { error ->
                Log.e("NavigationError", "Speech generation error: $error")
                voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback)
            },
            { value ->
                Log.d("NavigationDebug", "Speech generation successful: $value")
                voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback)
            }
        )
    }

    private val voiceInstructionsPlayerCallback = MapboxNavigationConsumer<SpeechAnnouncement> { value ->
        // Clean up speech API resources
        speechApi.clean(value)
        Log.d("NavigationDebug", "Cleaned up speech announcement: $value")
    }


    override fun onDestroy() {
        super.onDestroy()
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
    }
}
