package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragGimbalPageBinding
import dji.sampleV5.aircraft.models.GimbalVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.value.gimbal.GimbalMode

class GimbalFragment : DJIFragment() {
    private val gimbalVM: GimbalVM by activityViewModels()
    private var binding: FragGimbalPageBinding? = null

    private val gimbalModes = arrayOf("FOLLOW", "FPV", "FREE")
    private val angleInputWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            validateAngleInput()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragGimbalPageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        initListener()
        observeData()
        validateAngleInput()
    }

    private fun initViews() {
        val modeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, gimbalModes)
        binding?.spinnerGimbalMode?.adapter = modeAdapter
        binding?.radioAbsoluteAngle?.isChecked = true

        binding?.btnResetGimbal?.visibility = View.GONE
        binding?.btnCalibrateGimbal?.visibility = View.GONE
    }

    private fun initListener() {
        binding?.rgAngleMode?.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radio_relative_angle -> GimbalVM.AngleMode.RELATIVE
                else -> GimbalVM.AngleMode.ABSOLUTE
            }
            gimbalVM.setAngleMode(mode)
            validateAngleInput()
        }

        binding?.etPitch?.addTextChangedListener(angleInputWatcher)
        binding?.etRoll?.addTextChangedListener(angleInputWatcher)
        binding?.etYaw?.addTextChangedListener(angleInputWatcher)

        binding?.btnRotateAngle?.setOnClickListener {
            rotateByAngle()
        }

        binding?.btnRotateBySpeed?.setOnClickListener {
            rotateBySpeed()
        }

        binding?.btnStopRotate?.setOnClickListener {
            gimbalVM.stopRotate()
        }

        binding?.btnSetMode?.setOnClickListener {
            setGimbalMode()
        }
    }

    private fun observeData() {
        gimbalVM.gimbalAttitudePitch.observe(viewLifecycleOwner) { pitch ->
            pitch?.let {
                binding?.tvPitch?.text = getString(R.string.pitch_label) + ": ${it}"
            }
            validateAngleInput()
        }

        gimbalVM.gimbalAttitudeRoll.observe(viewLifecycleOwner) { roll ->
            roll?.let {
                binding?.tvRoll?.text = getString(R.string.roll_label) + ": ${it}"
            }
            validateAngleInput()
        }

        gimbalVM.gimbalYawRelative.observe(viewLifecycleOwner) { yaw ->
            yaw?.let {
                binding?.tvYaw?.text = getString(R.string.yaw_label) + ": ${it}"
            }
            validateAngleInput()
        }

        gimbalVM.gimbalMode.observe(viewLifecycleOwner) { mode ->
            mode?.let {
                binding?.tvMode?.text = it.toString()
            }
        }

        gimbalVM.gimbalAttitudeRange.observe(viewLifecycleOwner) { range ->
            range?.let {
                val rangeText = """
                    Pitch: ${it.pitch.min} ~ ${it.pitch.max}
                    Roll: ${it.roll.min} ~ ${it.roll.max}
                    Yaw: ${it.yaw.min} ~ ${it.yaw.max}
                """.trimIndent()
                binding?.tvRange?.text = rangeText
            }
            validateAngleInput()
        }

        gimbalVM.angleMode.observe(viewLifecycleOwner) { mode ->
            val checkedId = if (mode == GimbalVM.AngleMode.RELATIVE) {
                R.id.radio_relative_angle
            } else {
                R.id.radio_absolute_angle
            }
            binding?.rgAngleMode?.setOnCheckedChangeListener(null)
            binding?.rgAngleMode?.check(checkedId)
            binding?.rgAngleMode?.setOnCheckedChangeListener { _, selectedId ->
                val selectedMode = if (selectedId == R.id.radio_relative_angle) {
                    GimbalVM.AngleMode.RELATIVE
                } else {
                    GimbalVM.AngleMode.ABSOLUTE
                }
                gimbalVM.setAngleMode(selectedMode)
                validateAngleInput()
            }
        }

        gimbalVM.angleInputValid.observe(viewLifecycleOwner) { isValid ->
            binding?.btnRotateAngle?.isEnabled = isValid
            binding?.btnRotateAngle?.alpha = if (isValid) 1f else 0.5f
        }

        gimbalVM.angleInputError.observe(viewLifecycleOwner) { errorText ->
            binding?.tvAngleValidation?.text = errorText ?: getString(R.string.gimbal_angle_input_valid)
            binding?.tvAngleValidation?.visibility = View.VISIBLE
        }

        gimbalVM.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                ToastUtils.showToast(it)
            }
        }
    }

    private fun rotateByAngle() {
        val pitch = binding?.etPitch?.text.toString().toDoubleOrNull() ?: 0.0
        val roll = binding?.etRoll?.text.toString().toDoubleOrNull() ?: 0.0
        val yaw = binding?.etYaw?.text.toString().toDoubleOrNull() ?: 0.0
        val isAbsolute = gimbalVM.angleMode.value != GimbalVM.AngleMode.RELATIVE

        gimbalVM.rotateGimbalByAngle(pitch, roll, yaw, isAbsolute = isAbsolute)
    }

    private fun rotateBySpeed() {
        val pitchSpeed = binding?.etPitchSpeed?.text.toString().toDoubleOrNull() ?: 0.0
        val rollSpeed = binding?.etRollSpeed?.text.toString().toDoubleOrNull() ?: 0.0
        val yawSpeed = binding?.etYawSpeed?.text.toString().toDoubleOrNull() ?: 0.0

        gimbalVM.rotateGimbalBySpeed(pitchSpeed, rollSpeed, yawSpeed)
    }

    private fun setGimbalMode() {
        val position = binding?.spinnerGimbalMode?.selectedItemPosition ?: 0
        val mode = GimbalMode.find(position)
        gimbalVM.setGimbalMode(mode)
    }

    private fun validateAngleInput() {
        val pitch = binding?.etPitch?.text.toString().toDoubleOrNull() ?: 0.0
        val roll = binding?.etRoll?.text.toString().toDoubleOrNull() ?: 0.0
        val yaw = binding?.etYaw?.text.toString().toDoubleOrNull() ?: 0.0
        gimbalVM.validateAngleInput(pitch, roll, yaw)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}
