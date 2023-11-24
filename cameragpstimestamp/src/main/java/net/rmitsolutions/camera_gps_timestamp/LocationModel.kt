package net.rmitsolutions.camera_gps_timestamp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
class LocationModel(val latitude: Double, val longitude: Double) : Parcelable