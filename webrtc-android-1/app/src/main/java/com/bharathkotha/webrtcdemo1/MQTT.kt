package com.bharathkotha.webrtcdemo1

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import java.util.UUID

class MQTT(val topic: String,val callback: (String, String) -> Unit) {
    var client : Mqtt5AsyncClient
    val pendingMessage: ArrayList<String> = arrayListOf()
    init {
        val clientId = UUID.randomUUID().toString()
        client = MqttClient.builder()
            .identifier(clientId)
            .serverHost("mqtt-dashboard.com")
            .serverPort(1883)
            .useMqttVersion5()
            .buildAsync()

        val connFeature = client.connectWith().cleanStart(true).send()
        connFeature.whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e(TAG, "Connection Error: ${throwable.cause}", )
            }
            else {
                Log.i(TAG, "Connected. Sending queued messages ")
                pendingMessage.forEach { message ->
                    publishInternal(message)
                }
                pendingMessage.clear()
            }
        }

        client.publishes(MqttGlobalPublishFilter.ALL) { message ->
            val payload = Charsets.UTF_8.decode(message.payload.get()).toString()
            Log.i(TAG, "Received Message: $payload")
            callback(topic, payload)
        }

        // Subscribe to topic
        client.subscribeWith()
            .topicFilter(topic)
            .send()
    }

    fun publish(message: String) {
        Log.i(TAG, "publish: $message")
        if(client.state.isConnected) {
            publishInternal(message)
        } else {
            pendingMessage.add(message)
        }

    }

    private fun publishInternal(message: String) {
        client.publishWith()
            .topic(topic)
            .payload(message.toByteArray())
            .send()
            .whenComplete { publishResult, throwable ->
                if (throwable != null) Log.e(TAG, "publish error: $throwable")
                Log.i(TAG, "publish: ${publishResult.publish}")
            }
    }

    companion object {
        const val TAG = "MQTT"
    }
}