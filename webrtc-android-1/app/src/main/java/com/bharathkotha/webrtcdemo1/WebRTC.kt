package com.bharathkotha.webrtcdemo1

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

object WebRTC {
    val TAG = "WEBRTC"
    
    val eglBase = EglBase.create()
    var peerConnFactory : PeerConnectionFactory? = null
    var peerConnection : PeerConnection? = null
    var videoCapturer : VideoCapturer? = null
    var localVideoTrack : VideoTrack? = null
    var remoteVideoTrack : VideoTrack? = null
    var localView : SurfaceViewRenderer? = null
    var remoteView : SurfaceViewRenderer? = null

    lateinit var  role: String
    lateinit var meetingId: String
    lateinit var mqtt: MQTT
    lateinit var mqttTopic : String

    fun createPeerConnection(context : Context) {
        // Initialize PeerConnectionFactory
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        peerConnFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(createJavaAudioDevice(context))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()

        val iceBuilder = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
        iceBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
        val iceServers = arrayListOf(iceBuilder.createIceServer())

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer{
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.i(TAG, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "onIceConnectionChange: $state")
            }

            override fun onIceConnectionReceivingChange(change: Boolean) {
                Log.i(TAG, "onIceConnectionReceivingChange: $change")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.i(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(ice: IceCandidate?) {
                Log.i(TAG, "onIceCandidate: $ice")
                val payload = JSONObject()
                payload.put("type", "ice")
                payload.put("ice", ice)
                payload.put("sdpMid", ice?.sdpMid)
                payload.put("sdpMLineIndex", ice?.sdpMLineIndex)
                payload.put("description", ice?.sdp)

                sendMqttMessage(payload)
            }

            override fun onIceCandidatesRemoved(var1: Array<out IceCandidate>?) {
                Log.i(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.i(TAG, "onAddStream: $stream")
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.i(TAG, "onRemoveStream: $stream")   
            }

            override fun onDataChannel(dc: DataChannel?) {
                Log.i(TAG, "onDataChannel: $dc")
            }

            override fun onRenegotiationNeeded() {
                Log.i(TAG, "onRenegotiationNeeded: ")
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                Log.i(TAG, "onTrack: $transceiver")
                if (transceiver?.receiver?.track() is VideoTrack) {
                    Log.i(TAG, "onTrack: Adding remote video track")
                    remoteVideoTrack = transceiver.receiver.track() as VideoTrack
                    remoteView?.let {
                        remoteVideoTrack?.addSink(it)
                    }
                }
            }

        })
    }

    fun addVideoTracks(context: Context){
        Log.i(TAG, "addTracks: In add tracks method")
        //The video source represents a local video stream. It provides video frames to the peer connection for encoding, transmission, and rendering.
        val localVideoSource = peerConnFactory?.createVideoSource(false)
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                videoCapturer = enumerator.createCapturer(deviceName, null)         //A video capturer is a component that allows capturing video from a camera device and providing it as a video source for WebRTC sessions
            }
        }

        if(videoCapturer == null) {
            for (deviceName in deviceNames) {
                if (!enumerator.isFrontFacing(deviceName)) {
                    videoCapturer = enumerator.createCapturer(deviceName, null)
                }
            }
        }

        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, eglBase.eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, context/*localVideoOutput.context*/, localVideoSource?.capturerObserver)        // preparing it for capturing video frames from the camera or video input device

        localVideoTrack = peerConnFactory?.createVideoTrack("Video", localVideoSource)
        Log.i(TAG, "addTracks: Adding local track to peer connection")
        videoCapturer?.startCapture(480, 640, 30)
        peerConnection?.addTrack(localVideoTrack)
    }

    fun addAudioTracks() {
        val audioSource = peerConnFactory?.createAudioSource(MediaConstraints())
        val audioTrack = peerConnFactory?.createAudioTrack("Audio", audioSource)
        audioTrack?.setEnabled(true)

        peerConnection?.addTrack(audioTrack)
    }

    private fun createJavaAudioDevice(context: Context): JavaAudioDeviceModule? {
        return JavaAudioDeviceModule.builder(context)
            .createAudioDeviceModule()
    }

    fun startSignalling(meetingId: String, role: String) {
        this.meetingId = meetingId
        this.role = role
        mqttTopic = "webrtc/conference/$meetingId"
        mqtt = MQTT(mqttTopic, mqttMessageListener)
        if(role == "Initiator") {
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    Log.i(TAG, "onCreateSuccess: ${sdp?.description}")
                    peerConnection?.setLocalDescription(dummySdpObserver, sdp)
                    val payload = JSONObject()
                    payload.put("type", "sdp")
                    payload.put("sdp", sdp?.description)
                    sendMqttMessage(payload)
                }

                override fun onSetSuccess() {
                    Log.i(TAG, "onSetSuccess: ")
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "onCreateFailure: $error", )
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "onSetFailure: $error", )
                }

            }, createSDPConstraints())
        }
    }

    fun addViews(localView: SurfaceViewRenderer, remoteView: SurfaceViewRenderer) {
        this.localView = localView
        this.remoteView = remoteView

        localView.init(eglBase.eglBaseContext, null)
        remoteView.init(eglBase.eglBaseContext, null)

        localVideoTrack?.addSink(localView)
        remoteVideoTrack?.addSink(remoteView)
    }

    private fun createSDPConstraints() : MediaConstraints {
        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio","true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"))
        return sdpConstraints
    }

    private val mqttMessageListener : (String, String) -> Unit =  { _, payload ->
        val message = JSONObject(payload)
        val role = message.get("role") as String
        val type = message.get("type") as String
        if (role != this.role)  {
            if (type == "sdp") handleSdpMessage(message)
            else if (type == "ice") handleICEMessage(message)
        }
    }

    private fun handleSdpMessage(message: JSONObject) {
        if(role == "Initiator") {
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, message.get("sdp") as String)
            peerConnection?.setRemoteDescription(dummySdpObserver, sessionDescription)
        } else if (role == "Receiver") {
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, message.get("sdp") as String)
            peerConnection?.setRemoteDescription(dummySdpObserver, sessionDescription)
            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    peerConnection?.setLocalDescription(dummySdpObserver, sdp)
                    val payload = JSONObject()
                    payload.put("type", "sdp")
                    payload.put("sdp", sdp?.description)

                    sendMqttMessage(payload)
                }

                override fun onSetSuccess() {
                    TODO("Not yet implemented")
                }

                override fun onCreateFailure(error: String?) {
                    TODO("Not yet implemented")
                }

                override fun onSetFailure(error: String?) {
                    TODO("Not yet implemented")
                }

            }, createSDPConstraints())
        }
    }

    private fun handleICEMessage(message: JSONObject) {
        val sdpMid = message.get("sdpMid") as String
        val sdpMLineIndex = message.get("sdpMLineIndex") as Int
        val iceDescription = message.get("description") as String
        val ice = IceCandidate(sdpMid, sdpMLineIndex, iceDescription)

        peerConnection?.addIceCandidate(ice)
    }

    private fun sendMqttMessage(payload: JSONObject) {
        payload.put("role", role)
        mqtt.publish(payload.toString())
    }

    object dummySdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            Log.i(TAG, "onCreateSuccess: $sdp")
        }

        override fun onSetSuccess() {
            Log.i(TAG, "onSetSuccess: ")
        }

        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "onCreateFailure: $error")
        }

        override fun onSetFailure(error: String?) {
            Log.e(TAG, "onSetFailure: $error")
        }

    }
}