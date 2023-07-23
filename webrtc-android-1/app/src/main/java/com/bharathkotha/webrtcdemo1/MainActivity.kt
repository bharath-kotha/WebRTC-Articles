package com.bharathkotha.webrtcdemo1

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.bharathkotha.webrtcdemo1.databinding.ActivityMainBinding
import org.webrtc.EglBase


class MainActivity : AppCompatActivity() {
    var eglBase : EglBase? = null
    lateinit var binding : ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        addSpinnerOptions(binding)
        setContentView(binding.root)

        eglBase = EglBase.create()

        binding.startConference.setOnClickListener {
            hideConfiguration()
            WebRTC.createPeerConnection(this)
            WebRTC.addVideoTracks(this)
            WebRTC.addAudioTracks()
            WebRTC.startSignalling(binding.meetingID.text.toString(), binding.role.selectedItem.toString())
            WebRTC.addViews(binding.localView, binding.remoteView)
        }
    }

    private fun addSpinnerOptions(binding: ActivityMainBinding) {
        val adapter = ArrayAdapter(this, androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item, arrayOf("Initiator", "Receiver"))
        adapter.setDropDownViewResource(androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item)
        binding.role.adapter = adapter
    }

    private fun hideConfiguration() {
        binding.meetingIDLabel.visibility = View.GONE
        binding.meetingID.visibility = View.GONE
        binding.meetingRoleLable.visibility = View.GONE
        binding.role.visibility = View.GONE
        binding.startConference.visibility = View.GONE
    }

}

