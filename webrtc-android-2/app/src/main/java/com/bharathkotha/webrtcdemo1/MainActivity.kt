package com.bharathkotha.webrtcdemo1

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.bharathkotha.webrtcdemo1.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    lateinit var binding : ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startConference.setOnClickListener {
            hideConfiguration()
            WebRTC.createPeerConnection(this)
            WebRTC.addVideoTracks(this)
            WebRTC.addAudioTracks()
            WebRTC.startSignalling(binding.meetingID.text.toString())
            WebRTC.addViews(binding.localView, binding.remoteView)
        }
    }

    private fun hideConfiguration() {
        binding.meetingIDLabel.visibility = View.GONE
        binding.meetingID.visibility = View.GONE
        binding.startConference.visibility = View.GONE
    }

}

