package com.navigation.smartcarddriver


import android.content.Context
import android.media.MediaPlayer
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import java.lang.ref.WeakReference


class HomeActivity : AppCompatActivity() {


        private lateinit var mapView: MapView
        private lateinit var firestore: FirebaseFirestore
        private lateinit var locationPermissionHelper: LocationPermissionHelper
        private lateinit var pointAnnotationManager: PointAnnotationManager
        private lateinit var rideRequestPopup: LinearLayout
        private lateinit var driverID: String

        private var driverLat = 0.0
        private var driverLng = 0.0

        private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
                mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
        }

        private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
                mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
                mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
        }

        private val onMoveListener = object : OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) {
                        onCameraTrackingDismissed()
                }
                override fun onMove(detector: MoveGestureDetector): Boolean = false
                override fun onMoveEnd(detector: MoveGestureDetector) {}
        }

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.home_activity)
                // Retrieve driverID from intent
                driverID = intent.getStringExtra("driverID") ?: ""
                mapView = findViewById(R.id.mapView)


                rideRequestPopup = findViewById(R.id.rideRequestPopup)
                firestore = FirebaseFirestore.getInstance()

                // In your onCreate or initialization method:
                pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

                // Ensure the popup is initially hidden
                rideRequestPopup.visibility = View.GONE

                locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
                locationPermissionHelper.checkPermissions {
                        onMapReady()
                }
                val btnLogout = findViewById<Button>(R.id.btnLogout)
                btnLogout.setOnClickListener {
                        Toast.makeText(this, "Đăng xuất thành công!", Toast.LENGTH_SHORT).show()

                        // Chuyển sang LoginActivity
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Xóa các activity trước đó
                        startActivity(intent)
                        finish() // Kết thúc activity hiện tại
                }

                // Start listening for ride requests
                listenForRideRequests()
        }




        private fun onMapReady() {
                mapView.getMapboxMap().setCamera(
                        CameraOptions.Builder()
                                .zoom(14.0)
                                .build()
                )

                mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { _ ->
                        initLocationComponent()
                        setupGesturesListener()
                }
        }

        private fun setupGesturesListener() {
                mapView.gestures.addOnMoveListener(onMoveListener)
        }

        private fun initLocationComponent() {
                val locationComponentPlugin = mapView.location
                locationComponentPlugin.updateSettings {
                        enabled = true
                        locationPuck = LocationPuck2D(
                                bearingImage = getDrawable(R.drawable.mapbox_user_puck_icon),
                                shadowImage = getDrawable(R.drawable.mapbox_user_icon_shadow),
                                topImage =getDrawable(R.drawable.mapbox_user_top_icon)
                        )
                }
                // Lắng nghe vị trí thay đổi
                locationComponentPlugin.addOnIndicatorPositionChangedListener { point ->
                        driverLat = point.latitude()
                        driverLng = point.longitude()

                }
                locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
                locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        }

        private fun onCameraTrackingDismissed() {
                Toast.makeText(this, "Tracking Disabled", Toast.LENGTH_SHORT).show()
                mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
                mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
                mapView.gestures.removeOnMoveListener(onMoveListener)
        }

        override fun onDestroy() {
                super.onDestroy()
                mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
                mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
                mapView.gestures.removeOnMoveListener(onMoveListener)
        }
        private fun showUserMarker(lat: Double, lng: Double) {
                val annotationApi = mapView.annotations
                val pointAnnotationManager = annotationApi.createPointAnnotationManager()

                // Chuyển drawable thành bitmap
                val bitmap = bitmapFromDrawableRes(this, R.drawable.blue_marker)

                if (bitmap != null) {
                        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
                                bitmap.let {
                                        style.addImage("blue_marker", it)
                                }
                        }
                }


                // Đặt marker mới
                val pointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(lng, lat))
                        .withIconImage("blue_marker")

                pointAnnotationManager.create(pointAnnotationOptions)



        }
        private fun adjustCamera(driverLat: Double, driverLng: Double, userLat: Double, userLng: Double) {
                val driverPoint = Point.fromLngLat(driverLng, driverLat)
                val userPoint = Point.fromLngLat(userLng, userLat)

                val bounds = CoordinateBounds(driverPoint, userPoint)

                val cameraOptions = CameraOptions.Builder()
                        .center(bounds.center())
                        .zoom(12.0) // Set zoom level phù hợp
                        .padding(EdgeInsets(100.0, 100.0, 100.0, 100.0)) // Thêm padding để dễ nhìn thấy 2 điểm
                        .build()

                mapView.getMapboxMap().setCamera(cameraOptions)
        }




        private fun listenForRideRequests() {
                firestore.collection("ride_requests")
                        .addSnapshotListener { queryDocumentSnapshots, e ->
                                if (e != null) {
                                        Toast.makeText(this@HomeActivity, "Lỗi khi lấy yêu cầu: ${e.message}", Toast.LENGTH_SHORT).show()
                                        return@addSnapshotListener
                                }


                                var pendingRequestID: String? = null
                                var pendingUserID: String? = null
                                var userLat: Double? = null
                                var userLng: Double? = null

                                if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty) {
                                        for (document in queryDocumentSnapshots) {
                                                val requestID = document.id
                                                val userID = document.getString("userID")
                                                val status = document.getString("status")
                                                val acceptedBy = document.getString("acceptedBy")
                                                val lat = document.getDouble("userLat")  // Lấy tọa độ từ Firestore
                                                val lng = document.getDouble("userLng")

                                                // **BƯỚC 1: Nếu tài xế đã nhận cuốc, chuyển sang NavigationActivity**
                                                if (status == "accepted" && acceptedBy == driverID) {

                                                        val intent = Intent(this, NavigationActivity::class.java).apply {
                                                                putExtra("driverID", driverID)
                                                                putExtra("userID", userID)
                                                                putExtra("requestID", requestID)
                                                        }
                                                        startActivity(intent)
                                                        hidePopupForAllUsers()
                                                        finish()
                                                        Toast.makeText(this@HomeActivity, "Bạn đã nhận cuốc, chuyển đến Navigation!", Toast.LENGTH_SHORT).show()
                                                        return@addSnapshotListener
                                                }

                                                // **BƯỚC 2: Nếu chưa có cuốc accepted, tìm cuốc "pending"**
                                                if (status == "pending" && pendingRequestID == null) {
                                                        pendingRequestID = requestID
                                                        pendingUserID = userID
                                                        userLat = lat
                                                        userLng = lng
                                                }
                                        }
                                }

                                // **BƯỚC 3: Nếu có cuốc "pending", hiển thị popup + marker**
                                if (pendingRequestID != null) {
                                        rideRequestPopup.visibility = View.VISIBLE
                                        // **Play alarm sound**
                                        val mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound) // Replace "alarm_sound" with your sound file
                                        mediaPlayer.isLooping = true
                                        mediaPlayer.start()
                                        // **Hiển thị marker trên bản đồ**
                                        if (userLat != null && userLng != null) {
                                                showUserMarker(userLat, userLng)
                                                initLocationComponent()
                                                adjustCamera(driverLat,driverLng,userLat,userLng)
                                        }

                                        // **Khi ấn nút "Chấp nhận" trong popup**
                                        val acceptButton = findViewById<Button>(R.id.acceptButton)
                                        acceptButton.setOnClickListener {
                                                firestore.collection("ride_requests")
                                                        .document(pendingRequestID)
                                                        .update("status", "accepted", "acceptedBy", driverID)
                                                        .addOnSuccessListener {
                                                                val intent = Intent(this, NavigationActivity::class.java).apply {
                                                                        putExtra("driverID", driverID)
                                                                        putExtra("userID", pendingUserID)
                                                                        putExtra("requestID", pendingRequestID)

                                                                        mediaPlayer.stop()
                                                                        mediaPlayer.release()

                                                                }
                                                                finish()
                                                                startActivity(intent)
                                                                hidePopupForAllUsers()
                                                                Toast.makeText(this@HomeActivity, "Bạn đã nhận cuốc!", Toast.LENGTH_SHORT).show()
                                                        }
                                        }
                                } else {
                                        rideRequestPopup.visibility = View.GONE

                                }
                        }
        }




        private fun hidePopupForAllUsers() {
                rideRequestPopup.visibility = View.GONE
        }

        // ✅ Fixed `getBitmapFromDrawable` function
        private fun bitmapFromDrawableRes(context: Context, @DrawableRes resId: Int): Bitmap? {
                val sourceDrawable = ContextCompat.getDrawable(context, resId) ?: return null
                val bitmap = Bitmap.createBitmap(
                        sourceDrawable.intrinsicWidth,
                        sourceDrawable.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                sourceDrawable.setBounds(0, 0, canvas.width, canvas.height)
                sourceDrawable.draw(canvas)
                return bitmap
        }


}
