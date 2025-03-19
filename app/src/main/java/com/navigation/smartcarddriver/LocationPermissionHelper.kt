package com.navigation.smartcarddriver

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.lang.ref.WeakReference

class LocationPermissionHelper(private val activityRef: WeakReference<Activity>) {

    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        const val PERMISSION_REQUEST_CODE = 1001
    }

    fun checkPermissions(onPermissionGranted: () -> Unit) {
        val activity = activityRef.get() ?: return // Get the activity safely
        if (hasLocationPermissions(activity)) {
            onPermissionGranted()
        } else {
            ActivityCompat.requestPermissions(activity, LOCATION_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasLocationPermissions(activity: Activity): Boolean {
        return LOCATION_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
