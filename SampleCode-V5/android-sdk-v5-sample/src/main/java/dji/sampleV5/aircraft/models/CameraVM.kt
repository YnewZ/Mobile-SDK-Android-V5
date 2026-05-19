package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.util.FtpUploadUtil
import dji.sampleV5.aircraft.util.GallerySaver
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.et.set
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.media.MediaFile
import dji.v5.manager.datacenter.media.MediaFileDownloadListener
import dji.v5.manager.datacenter.media.PullMediaFileListParam
import dji.v5.utils.common.ContextUtil
import dji.v5.utils.common.LogUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CameraVM : DJIViewModel() {

    val cameraMode = MutableLiveData<CameraMode?>()
    val zoomRatio = MutableLiveData<Double?>()
    val toastMessage = MutableLiveData<String>()
    val photoSavedPath = MutableLiveData<String>()

    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    @Volatile
    private var isSyncing = false

    init {
        setupListeners()
    }

    private fun setupListeners() {
        CameraKey.KeyCameraMode.create(cameraIndex).listen(this) { mode ->
            cameraMode.postValue(mode)
        }

        val zoomRatiosKey = KeyTools.createCameraKey(CameraKey.KeyCameraZoomRatios, cameraIndex, CameraLensType.CAMERA_LENS_ZOOM)
        zoomRatiosKey.listen(this) { ratio ->
            zoomRatio.postValue(ratio)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        CameraKey.KeyCameraMode.create(cameraIndex).set(mode, {
            toastMessage.postValue("Camera mode set success: ${mode}")
        }, { error: IDJIError ->
            toastMessage.postValue("Camera mode failed: ${error.description()}")
        })
    }

    fun setZoomRatio(ratio: Double) {
        val zoomRatiosKey = KeyTools.createCameraKey(CameraKey.KeyCameraZoomRatios, cameraIndex, CameraLensType.CAMERA_LENS_ZOOM)
        KeyManager.getInstance().setValue(zoomRatiosKey, ratio, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                toastMessage.postValue("Zoom set to ${ratio}x")
            }

            override fun onFailure(error: IDJIError) {
                toastMessage.postValue("Zoom failed: ${error.description()}")
            }
        })
    }

    fun shootPhoto() {
        if (isSyncing) {
            toastMessage.postValue("Photo sync in progress, please wait...")
            return
        }
        CameraKey.KeyCameraMode.create(cameraIndex).set(CameraMode.PHOTO_NORMAL, {
            CameraKey.KeyStartShootPhoto.create(cameraIndex).action({
                toastMessage.postValue("Photo taken! Syncing to gallery...")
                syncLatestPhotoToGallery()
            }, { error: IDJIError ->
                toastMessage.postValue("Take photo failed: ${error.description()}")
            })
        }, { error: IDJIError ->
            toastMessage.postValue("Set mode failed: ${error.description()}")
        })
    }

    private fun syncLatestPhotoToGallery() {
        if (isSyncing) return
        isSyncing = true

        try {
            val mediaManager = MediaDataCenter.getInstance().mediaManager
            var latestPhoto = findLatestPhotoFromList(mediaManager.mediaFileListData?.data)

            if (latestPhoto != null) {
                downloadAndSaveToGallery(latestPhoto)
                return
            }

            mediaManager.pullMediaFileListFromCamera(
                PullMediaFileListParam.Builder().mediaFileIndex(0).count(20).build(),
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        latestPhoto = findLatestPhotoFromList(mediaManager.mediaFileListData?.data)
                        if (latestPhoto != null) {
                            downloadAndSaveToGallery(latestPhoto!!)
                        } else {
                            isSyncing = false
                            toastMessage.postValue("No photo found on aircraft. Please check Media page.")
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        isSyncing = false
                        toastMessage.postValue("Fetch file list failed: ${error.description()}")
                    }
                }
            )
        } catch (e: Exception) {
            isSyncing = false
            toastMessage.postValue("Sync failed: ${e.message}")
            LogUtils.e(logTag, "syncLatestPhotoToGallery error: ${e.message}")
        }
    }

    private fun findLatestPhotoFromList(list: List<MediaFile>?): MediaFile? {
        if (list.isNullOrEmpty()) return null
        return list.filter {
            it.fileName.endsWith(".JPG", ignoreCase = true) || it.fileName.endsWith(".DNG", ignoreCase = true)
        }.maxByOrNull { it.fileIndex }
    }

    private fun downloadAndSaveToGallery(mediaFile: MediaFile) {
        val context = ContextUtil.getContext() ?: run {
            isSyncing = false
            return
        }
        val cacheDir = File(context.cacheDir, "photo_download")
        cacheDir.mkdirs()
        val tempFile = File(cacheDir, mediaFile.fileName)
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val fos = FileOutputStream(tempFile)
        val bos = BufferedOutputStream(fos)

        mediaFile.pullOriginalMediaFileFromCamera(0L, object : MediaFileDownloadListener {
            override fun onStart() {
                toastMessage.postValue("Downloading ${mediaFile.fileName}...")
            }

            override fun onProgress(total: Long, current: Long) {}

            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
                try {
                    bos.write(data)
                } catch (e: IOException) {
                    LogUtils.e(logTag, "Write error: ${e.message}")
                }
            }

            override fun onFinish() {
                try {
                    bos.close()
                    fos.close()
                } catch (e: IOException) {
                    LogUtils.e(logTag, "Close stream error: ${e.message}")
                }

                val savedUri = GallerySaver.saveFileToGallery(context, tempFile, mediaFile.fileName)
                if (savedUri != null) {
                    photoSavedPath.postValue("Saved to gallery: $savedUri")
                    toastMessage.postValue("Photo saved to gallery!")
                } else {
                    photoSavedPath.postValue("Failed to save to gallery")
                    toastMessage.postValue("Failed to save to gallery")
                }

                FtpUploadUtil.uploadFile(tempFile, mediaFile.fileName) { success, msg ->
                    if (success) {
                        toastMessage.postValue("Photo uploaded to FTP: $msg")
                    } else {
                        toastMessage.postValue("FTP upload failed: $msg")
                    }
                    tempFile.delete()
                    isSyncing = false
                }
            }

            override fun onFailure(error: IDJIError?) {
                try {
                    bos.close()
                    fos.close()
                } catch (e: Exception) {
                    LogUtils.e(logTag, "Close stream error: ${e.message}")
                }
                toastMessage.postValue("Download failed: ${error?.description()}")
                tempFile.delete()
                isSyncing = false
            }
        })
    }
}
