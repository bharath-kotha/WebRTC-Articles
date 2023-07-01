// Variables to store local media stream and WebRTC connection
let rtcConnection;

// Variables to store MQTT client and configuration
const mqttBroker = 'wss://mqtt-dashboard.com:8884/mqtt';
const mqttTopicPrefix = 'webrtc/conference/';
let mqttClient;

// Function to handle MQTT message
function handleMqttMessage(topic, message) {
    const payload = JSON.parse(message);
    const role = document.getElementById('role').value

    // Ignore self messages
    if(payload.role === role) return;

    if (payload.type === 'sdp') {
        handleSdpMessage(payload.sdp);
    } else if (payload.type === 'ice') {
        handleIceCandidate(payload.candidate);
    }
}

// Function to handle SDP message
async function handleSdpMessage(sdp) {
    role = document.getElementById('role').value
    try {
        if (role === 'initiator') {
            await rtcConnection.setRemoteDescription({ type: 'answer', sdp });
            console.log('SDP answer set as remote description');
        } else if (role === 'receiver') {
            await rtcConnection.setRemoteDescription({ type: 'offer', sdp });
            console.log('SDP offer set as remote description');

            // Create answer and set local description
            const answer = await rtcConnection.createAnswer();
            await rtcConnection.setLocalDescription(answer);

            console.log('SDP answer created and set as local description');

            // Send answer to the initiator
            sendMqttMessage({
                meetingId: document.getElementById('meetingId').value,
                type: 'sdp',
                role: document.getElementById('role').value,
                sdp: rtcConnection.localDescription.sdp
            });
        }
    } catch (error) {
        console.error('Error setting remote description:', error);
    }
}

// Function to handle ICE candidate message
async function handleIceCandidate(candidate, role) {
    try {
        if (candidate) {
            await rtcConnection.addIceCandidate(candidate);
            console.log('ICE candidate added');
        }
    } catch (error) {
        console.error('Error adding ICE candidate:', error);
    }
}

// Function to send MQTT message
function sendMqttMessage(payload) {
    const meetingId = document.getElementById('meetingId').value;
    const mqttTopic = mqttTopicPrefix + meetingId
    mqttClient.publish(mqttTopic, JSON.stringify(payload));
}

// Function to set up local media
async function setupLocalMedia() {
    let localStream;
    try {
        // Request access to user's camera and microphone
        localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });

        // Add local media stream tracks to the connection
        localStream.getTracks().forEach((track) => {
            rtcConnection.addTrack(track, localStream);
        });

        // Display local video stream in the local video element
        const localVideo = document.getElementById('localVideo');
        localVideo.srcObject = localStream;
    } catch (error) {
        console.error('Error accessing local media:', error);
    }
}

// Function to create a new WebRTC connection
function createConnection() {
    // Create a new WebRTC connection with STUN server configuration
    const configuration = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };
    rtcConnection = new RTCPeerConnection(configuration);

    // Set up event listeners for the connection

    // Send SDP offer to the receiver
    rtcConnection.addEventListener('negotiationneeded', async () => {
        const role = document.getElementById('role').value;
        if (role === 'receiver') return;
        try {
            // Create offer and set local description
            const offer = await rtcConnection.createOffer();
            await rtcConnection.setLocalDescription(offer);

            console.log('SDP offer created and set as local description');

            // Send offer to the receiver
            sendMqttMessage({
                meetingId: document.getElementById('meetingId').value,
                type: 'sdp',
                role: document.getElementById('role').value,
                sdp: rtcConnection.localDescription.sdp
            });
        } catch (error) {
            console.error('Error creating SDP offer:', error);
        }
    });

    // Handle ICE candidates
    rtcConnection.addEventListener('icecandidate', (event) => {
        if (event.candidate) {
            // Send ICE candidate to the other participant
            sendMqttMessage({
                meetingId: document.getElementById('meetingId').value,
                type: 'ice',
                role: document.getElementById('role').value,
                candidate: event.candidate
            });
        }
    });

    // Set remote video stream as the source for the remote video element
    rtcConnection.addEventListener('track', (event) => {
        const remoteVideo = document.getElementById('remoteVideo');
        if (event.track.kind === 'video') {
            remoteVideo.srcObject = event.streams[0];
        }
    });
}

// Function to join the conference
async function joinConference() {
    const meetingId = document.getElementById('meetingId').value;

    // Connect to the MQTT broker
    mqttClient = mqtt.connect(mqttBroker);
    mqttTopic = mqttTopicPrefix + meetingId

    // Handle MQTT connection
    mqttClient.on('connect', () => {
        console.log('Connected to MQTT broker');

        // Subscribe to the MQTT topic
        mqttClient.subscribe(mqttTopic);
    });

    // Handle MQTT message
    mqttClient.on('message', handleMqttMessage);

    
    createConnection();
    // Set up local media and create the WebRTC connection
    await setupLocalMedia();
}

// Event listener for the "Join Conference" button
document.getElementById('joinButton').addEventListener('click', joinConference);
