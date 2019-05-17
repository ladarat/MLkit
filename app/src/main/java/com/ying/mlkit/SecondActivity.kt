package com.ying.mlkit

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_second.*
import org.jmrtd.lds.icao.MRZInfo

class SecondActivity : AppCompatActivity() {

    private var mrzInfo: MRZInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        val intent = intent
        if (intent.hasExtra("MRZInfo")) {
            mrzInfo = intent.getSerializableExtra("MRZInfo") as MRZInfo
            documentText.text = mrzInfo?.documentNumber
            birthDate.text = mrzInfo?.dateOfBirth
            expiryDate.text = mrzInfo?.dateOfExpiry
        } else {
            onBackPressed()
        }
    }
}
