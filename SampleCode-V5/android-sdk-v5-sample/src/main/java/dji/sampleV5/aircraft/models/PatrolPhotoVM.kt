package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.et.set
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.media.MediaFile
import dji.v5.manager.datacenter.media.MediaFileDownloadListener
import dji.v5.manager.datacenter.media.MediaFileListDataSource
import dji.v5.manager.datacenter.media.MediaFileListState
import dji.v5.manager.datacenter.media.MediaFileListStateListener
import dji.v5.manager.datacenter.media.PullMediaFileListParam
import dji.v5.utils.common.ContextUtil
import dji.v5.utils.common.LogUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PatrolPhotoVM : DJIViewModel() {

    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    private val serverUrl = "http://192.168.3.100:6001/partol/result"
    val statusMessage = MutableLiveData<String>()
    val isProcessing = MutableLiveData(false)

    fun captureAndSend(id: String) {
        if (isProcessing.value == true) return
        if (id.isBlank()) {
            statusMessage.postValue("Please enter an ID")
            return
        }
        isProcessing.postValue(true)
        shootPhoto(id)
    }

    private fun shootPhoto(id: String) {
        statusMessage.postValue("Setting camera mode...")
        CameraKey.KeyCameraMode.create(cameraIndex).set(CameraMode.PHOTO_NORMAL, {
            statusMessage.postValue("Taking photo...")
            CameraKey.KeyStartShootPhoto.create(cameraIndex).action({
                statusMessage.postValue("Photo taken, downloading...")
                syncLatestPhoto(id)
            }, { error: IDJIError ->
                statusMessage.postValue("Take photo failed: ${error.description()}")
                isProcessing.postValue(false)
            })
        }, { error: IDJIError ->
            statusMessage.postValue("Set mode failed: ${error.description()}")
            isProcessing.postValue(false)
        })
    }

    private fun syncLatestPhoto(id: String) {
        try {
            val mediaManager = MediaDataCenter.getInstance().mediaManager
            statusMessage.postValue("Fetching photo list...")

            // Configure data source so the media manager knows which camera to query
            val mediaSource = MediaFileListDataSource.Builder()
                .setIndexType(cameraIndex)
                .build()
            mediaManager.setMediaFileDataSource(mediaSource)

            // Listen for UP_TO_DATE — that's when the file list data is actually ready
            val stateListener = object : MediaFileListStateListener {
                override fun onUpdate(state: MediaFileListState) {
                    if (state == MediaFileListState.UP_TO_DATE) {
                        mediaManager.removeMediaFileListStateListener(this)
                        val allFiles = mediaManager.mediaFileListData?.data
                        LogUtils.e(logTag, "File list ready, total files: ${allFiles?.size ?: 0}")
                        allFiles?.forEach { LogUtils.e(logTag, "  file: ${it.fileName}") }
                        val latestPhoto = findLatestPhotoFromList(allFiles)
                        if (latestPhoto != null) {
                            downloadFile(latestPhoto, id)
                        } else {
                            statusMessage.postValue("No photo found on aircraft (total ${allFiles?.size ?: 0} files)")
                            isProcessing.postValue(false)
                        }
                    }
                }
            }
            mediaManager.addMediaFileListStateListener(stateListener)

            // Pull the list; data arrives when state reaches UP_TO_DATE
            mediaManager.pullMediaFileListFromCamera(
                PullMediaFileListParam.Builder().mediaFileIndex(0).count(50).build(),
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        LogUtils.e(logTag, "PullMediaFileListFromCamera onSuccess")
                    }

                    override fun onFailure(error: IDJIError) {
                        mediaManager.removeMediaFileListStateListener(stateListener)
                        statusMessage.postValue("Fetch file list failed: ${error.description()}")
                        isProcessing.postValue(false)
                    }
                }
            )
        } catch (e: Exception) {
            statusMessage.postValue("Sync failed: ${e.message}")
            isProcessing.postValue(false)
        }
    }

    private fun findLatestPhotoFromList(list: List<MediaFile>?): MediaFile? {
        if (list.isNullOrEmpty()) return null
        return list.filter {
            it.fileName.endsWith(".JPG", ignoreCase = true) || it.fileName.endsWith(".DNG", ignoreCase = true)
        }.maxByOrNull { it.fileIndex }
    }

    private fun downloadFile(mediaFile: MediaFile, id: String) {
        val context = ContextUtil.getContext() ?: run {
            isProcessing.postValue(false)
            return
        }
        val tempFile = File(context.cacheDir, "patrol_${id}_${System.currentTimeMillis()}.jpg")
        val fos = FileOutputStream(tempFile)
        val bos = BufferedOutputStream(fos)

        mediaFile.pullOriginalMediaFileFromCamera(0L, object : MediaFileDownloadListener {
            override fun onStart() {
                statusMessage.postValue("Downloading photo...")
            }

            override fun onProgress(total: Long, current: Long) {}

            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
                try {
                    bos.write(data)
                } catch (e: Exception) {
                    LogUtils.e(logTag, "Write error: ${e.message}")
                }
            }

            override fun onFinish() {
                try {
                    bos.close()
                    fos.close()
                } catch (e: Exception) {
                    LogUtils.e(logTag, "Close stream error: ${e.message}")
                }
                statusMessage.postValue("Converting to base64...")
                try {
                    val bytes = FileInputStream(tempFile).use { it.readBytes() }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    tempFile.delete()
                    sendHttpRequest(id, base64)
                } catch (e: Exception) {
                    statusMessage.postValue("Base64 conversion failed: ${e.message}")
                    tempFile.delete()
                    isProcessing.postValue(false)
                }
            }

            override fun onFailure(error: IDJIError?) {
                try {
                    bos.close()
                    fos.close()
                } catch (e: Exception) {
                    LogUtils.e(logTag, "Close stream error: ${e.message}")
                }
                tempFile.delete()
                statusMessage.postValue("Download failed: ${error?.description()}")
                isProcessing.postValue(false)
            }
        })
    }

    private fun sendHttpRequest(id: String, base64: String) {
        statusMessage.postValue("Sending to server...")
        Thread {
            try {
                val json = "{\"id\":\"$id\",\"images\":\"$base64\"}"
                val url = URL(serverUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.write(json.toByteArray(Charsets.UTF_8))

                val responseCode = conn.responseCode
                val responseMsg = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                if (responseCode == 200) {
                    statusMessage.postValue("Send success! Server: $responseMsg")
                } else {
                    statusMessage.postValue("Server error $responseCode: $responseMsg")
                }
            } catch (e: Exception) {
                statusMessage.postValue("HTTP request failed: ${e.message}")
            }
            isProcessing.postValue(false)
        }.start()
    }
}
