package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.control.ExternalControlManager
import dji.sampleV5.aircraft.data.DJIToastResult
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.payload.PayloadCenter
import dji.v5.manager.aircraft.payload.PayloadIndexType
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener
import dji.v5.utils.common.LogPath
import dji.v5.utils.common.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat

/**
 * Description :
 *
 * @author: Byte.Cai
 *  date : 2022/2/22
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
class PayLoadDataVM : DJIViewModel() {
    private lateinit var payloadIndexType: PayloadIndexType
    val receiveMessageLiveData = MutableLiveData<String>()
    private val payloadManagerMap = PayloadCenter.getInstance().payloadManager
    private val payloadDataListener = PayloadDataListener { bytes ->
        var result = "接收时间：${getTimeNow()}"
        if (bytes.isNotEmpty()) {
            // 解析 PSDK 发来的二进制 float 数组
            val floats = parsePsdkData(bytes)
            android.util.Log.e("PSDK_DEBUG", "源头收到数据: ${floats.contentToString()}")
            
            // 构建 JSON 参数字符串
            val jsonParams = buildJsonParams(floats)
            android.util.Log.e("PSDK_DEBUG", "构建的JSON: $jsonParams")
            
            result += "，接收内容：${floats.contentToString()}"
            LogUtils.i(LogPath.PAYLOAD, result)
            receiveMessageLiveData.postValue(result)
            
            // 直接调用 ExternalControlManager 处理（与 HTTP 接口调用相同逻辑）
            ExternalControlManager.handleExternalRequest(jsonParams)
        } else {
            result += "，接收内容为空"
            receiveMessageLiveData.postValue(result)
        }
    }

    /**
     * 解析 PSDK 发来的二进制 float 数组（小端序）
     */
    private fun parsePsdkData(bytes: ByteArray): FloatArray {
        val floatCount = bytes.size / 4
        val floatArray = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val byte4 = bytes.copyOfRange(i * 4, i * 4 + 4)
            floatArray[i] = ByteBuffer.wrap(byte4)
                .order(ByteOrder.LITTLE_ENDIAN).float
        }
        return floatArray
    }

    /**
     * 构建虚拟摇杆控制 JSON 参数
     */
    private fun buildJsonParams(floats: FloatArray): String {
        return """
        {
            "pitch": ${floats.getOrNull(0) ?: 0.0},
            "roll": ${floats.getOrNull(1) ?: 0.0},
            "yaw": ${floats.getOrNull(2) ?: 0.0},
            "vertical": ${floats.getOrNull(3) ?: 0.0},
            "rollPitchControlMode": "VELOCITY",
            "yawControlMode": "ANGULAR_VELOCITY",
            "verticalControlMode": "VELOCITY",
            "rollPitchCoordinateSystem": "BODY"
        }
        """.trimIndent()
    }

    fun sendMessageToPayLoadSdk(byteArray: ByteArray) {
        payloadManagerMap[payloadIndexType]?.sendDataToPayload(byteArray, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                sendToastMsg(DJIToastResult.success("Send success"))

            }

            override fun onFailure(error: IDJIError) {
                sendToastMsg(DJIToastResult.failed(error.toString()))

            }

        })
    }

    fun initPayloadDataListener(payloadIndexType: PayloadIndexType) {
        this.payloadIndexType = payloadIndexType
        payloadManagerMap[payloadIndexType]?.addPayloadDataListener(payloadDataListener)
    }

    override fun onCleared() {
        super.onCleared()
        payloadManagerMap[payloadIndexType]?.removePayloadDataListener(payloadDataListener)
    }

    private fun getTimeNow(): String {
        val currentTime = System.currentTimeMillis()
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(currentTime)
    }

    private fun sendToastMsg(djiToastResult: DJIToastResult) {
        toastResult?.postValue(djiToastResult)
    }
}