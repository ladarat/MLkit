package com.ying.mlkit

import org.jmrtd.lds.icao.MRZInfo

interface CameraMLKItListener {
    fun onPassportRead(mrzInfo: MRZInfo)
    fun onError()
}