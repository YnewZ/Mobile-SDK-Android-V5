package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragCameraPageBinding
import dji.sampleV5.aircraft.models.CameraVM
import dji.sampleV5.aircraft.util.ToastUtils

class CameraFragment : DJIFragment() {
    private val cameraVM: CameraVM by activityViewModels()
    private var binding: FragCameraPageBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragCameraPageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListener()
        observeData()
    }

    private fun initListener() {
        binding?.btnShootPhoto?.setOnClickListener {
            cameraVM.shootPhoto()
        }

        binding?.btnSetZoom?.setOnClickListener {
            val zoomStr = binding?.etZoomRatio?.text.toString()
            val zoom = zoomStr.toDoubleOrNull() ?: 1.0
            if (zoom > 0) {
                cameraVM.setZoomRatio(zoom)
            } else {
                ToastUtils.showToast("Please enter a valid zoom value")
            }
        }

        binding?.btnSetModePhoto?.setOnClickListener {
            cameraVM.setCameraMode(dji.sdk.keyvalue.value.camera.CameraMode.PHOTO_NORMAL)
        }

        binding?.btnSetModeVideo?.setOnClickListener {
            cameraVM.setCameraMode(dji.sdk.keyvalue.value.camera.CameraMode.VIDEO_NORMAL)
        }
    }

    private fun observeData() {
        cameraVM.cameraMode.observe(viewLifecycleOwner) { mode ->
            mode?.let {
                binding?.tvCameraMode?.text = "Mode: ${it}"
            }
        }

        cameraVM.zoomRatio.observe(viewLifecycleOwner) { ratio ->
            ratio?.let {
                binding?.tvZoomRatio?.text = "Zoom: ${it}x"
            }
        }

        cameraVM.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                ToastUtils.showToast(it)
            }
        }

        cameraVM.photoSavedPath.observe(viewLifecycleOwner) { path ->
            path?.let {
                binding?.tvSavedPath?.text = it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}