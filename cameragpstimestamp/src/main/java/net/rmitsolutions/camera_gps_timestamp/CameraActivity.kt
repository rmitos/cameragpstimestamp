package net.rmitsolutions.camera_gps_timestamp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.instacart.library.truetime.TrueTime
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.FileCallback
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rmitsolutions.camera_gps_timestamp.databinding.ActivityCameraBinding
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale


class CameraActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding
    private lateinit var googleMap: GoogleMap
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var selectedLocation: LatLng? = null
    private var serverTime = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.cameraView.setLifecycleOwner(this)

        if (!allPermissionsGranted()) {
            requestPermissions()
        }

        setMapView(savedInstanceState)

        viewBinding.cameraView.addCameraListener(cameraListener)

        viewBinding.takePhotoButton.setOnClickListener {
            takePhoto()
        }

        viewBinding.switchCameraButton.setOnClickListener {
            switchCamera()
        }

        lifecycleScope.launch {
            serverTime = getServerTime()
        }
    }

    private val cameraListener = object : CameraListener() {
        override fun onPictureTaken(result: PictureResult) {
            result.toBitmap { bitmap ->
                onFileSaved(bitmap)
            }
            /*val name =
                SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            val folder = File(filesDir, CameraGpsConstants.tempImageFolder)
            if (!folder.exists()) {
                folder.mkdir()
            }
            val path = "${folder.path}/${name}.jpg"
            result.toFile(File(path), FileCallback {
                onFileSaved(path)
            })*/
        }
    }

    private fun takePhoto() {
        if (selectedLocation == null) {
            Toast.makeText(
                this@CameraActivity,
                "Please wait till your current location is found.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showCameraControls(false)
        viewBinding.cameraView.takePictureSnapshot()
    }

    private fun onFileSaved(bitmap: Bitmap?) {
        if(bitmap == null) {
            Toast.makeText(
                this@CameraActivity,
                "Image not captured.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val storePath = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Images", "")
        val data = Intent()
        data.putExtra(CameraGpsConstants.filePathExtra, Uri.parse(storePath))
        setResult(Activity.RESULT_OK, data)
        Toast.makeText(
            this@CameraActivity,
            "Image saved.",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun setMapView(savedInstanceState: Bundle?) {
        viewBinding.mapView.onCreate(savedInstanceState)
        viewBinding.mapView.isClickable = false
        viewBinding.mapView.getMapAsync { map ->
            googleMap = map
            googleMap.uiSettings.isMapToolbarEnabled = false
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isCompassEnabled = false
            googleMap.uiSettings.setAllGesturesEnabled(false)
            startLocationRequest()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).toTypedArray()
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && it.value == false) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext, "Please accept all the permissions to take photo.", Toast.LENGTH_SHORT
            ).show()
            this.finish()
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationRequest() {
        if (allPermissionsGranted()) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient!!.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener {
                    val location = it
                    val latLng = LatLng(location.latitude, location.longitude)
                    selectedLocation = latLng
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .draggable(false)
                            .icon(bitmapFromVector(R.drawable.baseline_location_on))
                    )
                    reverseGeocode(latLng)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                }
        } else {
            requestPermissions()
        }
    }

    private fun reverseGeocode(latLang: LatLng) {
        val geo = Geocoder(this, Locale.getDefault())
        val address = geo.getFromLocation(latLang.latitude, latLang.longitude, 1)
        val add = address?.get(0)?.getAddressLine(0)
        viewBinding.locationAddress.text =
            "Lat:${latLang.latitude}, Long:${latLang.longitude}\n${serverTime}\n${if (add.isNullOrEmpty()) "" else add}"
    }

    private fun bitmapFromVector(vectorResId: Int): BitmapDescriptor {
        //drawable generator
        val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)!!
        vectorDrawable.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight
        )
        //bitmap genarator
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        //canvas genaret
        //pass bitmap in canvas constructor
        val canvas = Canvas(bitmap)
        //pass canvas in drawable
        vectorDrawable.draw(canvas)
        //return BitmapDescriptorFactory
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun showCameraControls(show: Boolean) {
        if (show) {
            viewBinding.waitMessageText.visibility = View.GONE
            viewBinding.switchCameraButton.visibility = View.VISIBLE
            viewBinding.takePhotoButton.visibility = View.VISIBLE
        } else {
            viewBinding.waitMessageText.visibility = View.VISIBLE
            viewBinding.switchCameraButton.visibility = View.GONE
            viewBinding.takePhotoButton.visibility = View.GONE
        }
    }

    //taking too long to get time
    private suspend fun getServerTime(): String {
        val time = withContext(Dispatchers.IO) {
            try {
                if (!TrueTime.isInitialized()) {
                    val trueTime = TrueTime()
                    trueTime.withNtpHost("time1.google.com").initialize()
                }
                val dateTime = TrueTime.now()
                val formatter: DateFormat =
                    SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH)
                return@withContext formatter.format(dateTime)
            } catch (e: Exception) {
                return@withContext ""
            }
        }
        return time
    }

    /* ============= */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewBinding.mapView.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewBinding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewBinding.mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        viewBinding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        viewBinding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient = null
        viewBinding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        viewBinding.mapView.onLowMemory()
    }

    private fun switchCamera() {
        if (viewBinding.cameraView.facing == Facing.BACK) {
            viewBinding.cameraView.facing = Facing.FRONT
        } else {
            viewBinding.cameraView.facing = Facing.BACK
        }
    }
}