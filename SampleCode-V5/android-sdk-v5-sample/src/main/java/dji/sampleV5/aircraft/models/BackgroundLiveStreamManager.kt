package dji.sampleV5.aircraft.models

import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.livestream.LiveStreamType
import dji.v5.manager.datacenter.livestream.StreamQuality
import dji.v5.manager.datacenter.livestream.LiveStreamSettings
import dji.v5.manager.datacenter.livestream.settings.RtmpSettings
import dji.v5.manager.interfaces.ILiveStreamManager

object BackgroundLiveStreamManager {

    private val streamManager: ILiveStreamManager by lazy {
        MediaDataCenter.getInstance().liveStreamManager
    }

    private var isStarted = false
    private var currentRtmpUrl: String = ""

    fun isStreaming(): Boolean = streamManager.isStreaming

    fun getCurrentRtmpUrl(): String = currentRtmpUrl

    fun startBackgroundStream(rtmpUrl: String) {
        if (rtmpUrl.isBlank()) return
        if (isStreaming() && currentRtmpUrl == rtmpUrl) return
        stopBackgroundStream()

        currentRtmpUrl = rtmpUrl
        val config = LiveStreamSettings.Builder()
            .setLiveStreamType(LiveStreamType.RTMP)
            .setRtmpSettings(
                RtmpSettings.Builder()
                    .setUrl(rtmpUrl)
                    .build()
            )
            .build()

        streamManager.liveStreamSettings = config
        streamManager.cameraIndex = ComponentIndexType.LEFT_OR_MAIN
        streamManager.liveStreamQuality = StreamQuality.HD

        streamManager.startStream(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                isStarted = true
            }

            override fun onFailure(error: IDJIError) {
                isStarted = false
            }
        })
    }

    fun stopBackgroundStream() {
        if (streamManager.isStreaming) {
            streamManager.stopStream(null)
        }
        isStarted = false
    }
}
