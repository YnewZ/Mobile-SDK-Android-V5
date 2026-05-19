package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode
import dji.sdk.keyvalue.value.gimbal.GimbalAttitudeRange
import dji.sdk.keyvalue.value.gimbal.GimbalMode
import dji.sdk.keyvalue.value.gimbal.GimbalSpeedRotation
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.et.set
import kotlin.math.max
import kotlin.math.min

class GimbalVM : DJIViewModel() {

    enum class AngleMode {
        ABSOLUTE,
        RELATIVE
    }

    val gimbalAttitudePitch = MutableLiveData<Double?>()
    val gimbalAttitudeRoll = MutableLiveData<Double?>()
    val gimbalMode = MutableLiveData<GimbalMode?>()
    val gimbalAttitudeRange = MutableLiveData<GimbalAttitudeRange?>()
    val gimbalYawRelative = MutableLiveData<Double?>()
    val angleMode = MutableLiveData(AngleMode.ABSOLUTE)
    val angleInputValid = MutableLiveData(true)
    val angleInputError = MutableLiveData<String?>(null)
    val toastMessage = MutableLiveData<String>()

    init {
        setupListeners()
    }

    private fun setupListeners() {
        GimbalKey.KeyGimbalAttitude.create(ComponentIndexType.LEFT_OR_MAIN).listen(this) { attitude ->
            gimbalAttitudePitch.postValue(attitude?.pitch)
            gimbalAttitudeRoll.postValue(attitude?.roll)
        }

        GimbalKey.KeyGimbalMode.create(ComponentIndexType.LEFT_OR_MAIN).listen(this) { mode ->
            gimbalMode.postValue(mode)
        }

        GimbalKey.KeyGimbalAttitudeRange.create(ComponentIndexType.LEFT_OR_MAIN).listen(this) { range ->
            gimbalAttitudeRange.postValue(range)
        }

        GimbalKey.KeyYawRelativeToAircraftHeading.create(ComponentIndexType.LEFT_OR_MAIN).listen(this) { yaw ->
            gimbalYawRelative.postValue(yaw)
        }
    }

    fun rotateGimbalBySpeed(pitchSpeed: Double, rollSpeed: Double, yawSpeed: Double) {
        val rotation = GimbalSpeedRotation()
        rotation.pitch = pitchSpeed
        rotation.roll = rollSpeed
        rotation.yaw = yawSpeed
        
        toastMessage.postValue("Speed Rotate: Pitch=$pitchSpeed, Roll=$rollSpeed, Yaw=$yawSpeed")
        
        GimbalKey.KeyRotateBySpeed.create(ComponentIndexType.LEFT_OR_MAIN).action(rotation, {
            toastMessage.postValue("Speed rotate success")
        }, { error: IDJIError ->
            toastMessage.postValue("Speed rotate failed: ${error.description()}")
        })
    }

    fun rotateGimbalByAngle(pitch: Double, roll: Double, yaw: Double, isAbsolute: Boolean = true) {
        val rotation = GimbalAngleRotation()
        rotation.pitch = pitch
        rotation.roll = roll
        rotation.yaw = yaw
        rotation.mode = if (isAbsolute) GimbalAngleRotationMode.ABSOLUTE_ANGLE else GimbalAngleRotationMode.RELATIVE_ANGLE

        toastMessage.postValue("Angle Rotate: Pitch=$pitch, Roll=$roll, Yaw=$yaw, Mode=${if (isAbsolute) "ABSOLUTE" else "RELATIVE"}")

        GimbalKey.KeyRotateByAngle.create(ComponentIndexType.LEFT_OR_MAIN).action(rotation, {
            toastMessage.postValue("Angle rotate success")
        }, { error: IDJIError ->
            toastMessage.postValue("Angle rotate failed: ${error.description()}")
        })
    }

    fun setAngleMode(mode: AngleMode) {
        angleMode.postValue(mode)
    }

    fun validateAngleInput(pitch: Double, roll: Double, yaw: Double) {
        val range = gimbalAttitudeRange.value
        if (range == null) {
            angleInputValid.postValue(true)
            angleInputError.postValue(null)
            return
        }

        val currentPitch = gimbalAttitudePitch.value ?: 0.0
        val currentRoll = gimbalAttitudeRoll.value ?: 0.0
        val currentYaw = gimbalYawRelative.value ?: 0.0
        val targetPitch = if (angleMode.value == AngleMode.RELATIVE) currentPitch + pitch else pitch
        val targetRoll = if (angleMode.value == AngleMode.RELATIVE) currentRoll + roll else roll
        val targetYaw = if (angleMode.value == AngleMode.RELATIVE) currentYaw + yaw else yaw

        val errors = mutableListOf<String>()
        if (targetPitch < range.pitch.min || targetPitch > range.pitch.max) {
            errors.add("Pitch ${formatRange(targetPitch, range.pitch.min, range.pitch.max)}")
        }
        if (targetRoll < range.roll.min || targetRoll > range.roll.max) {
            errors.add("Roll ${formatRange(targetRoll, range.roll.min, range.roll.max)}")
        }
        if (targetYaw < range.yaw.min || targetYaw > range.yaw.max) {
            errors.add("Yaw ${formatRange(targetYaw, range.yaw.min, range.yaw.max)}")
        }

        val errorText = if (errors.isEmpty()) null else errors.joinToString("\n")
        angleInputValid.postValue(errorText == null)
        angleInputError.postValue(errorText)
    }

    fun setGimbalMode(mode: GimbalMode) {
        GimbalKey.KeyGimbalMode.create(ComponentIndexType.LEFT_OR_MAIN).set(mode, {
            toastMessage.postValue("Set mode success")
        }, { error: IDJIError ->
            toastMessage.postValue("Set mode failed: ${error.description()}")
        })
    }

    fun stopRotate() {
        val rotation = GimbalSpeedRotation()
        rotation.pitch = 0.0
        rotation.roll = 0.0
        rotation.yaw = 0.0

        GimbalKey.KeyRotateBySpeed.create(ComponentIndexType.LEFT_OR_MAIN).action(rotation, {
            toastMessage.postValue("Stop rotate success")
        }, { error: IDJIError ->
            toastMessage.postValue("Stop failed: ${error.description()}")
        })
    }

    private fun formatRange(target: Double, minValue: Double, maxValue: Double): String {
        val safeMin = min(minValue, maxValue)
        val safeMax = max(minValue, maxValue)
        return "target=${"%.1f".format(target)}, range=[${"%.1f".format(safeMin)}, ${"%.1f".format(safeMax)}]"
    }
}

