package net.rmitsolutions.camera_gps_timestamp

import androidx.core.content.FileProvider

class CameraGpsPhotoFileProvider : FileProvider() {
    //extend fileprovider to stop collision with other library file provider
    //this class is empty and used in manifest
}