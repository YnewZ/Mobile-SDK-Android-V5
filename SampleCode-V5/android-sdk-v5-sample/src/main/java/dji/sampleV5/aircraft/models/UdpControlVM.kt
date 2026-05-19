package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.control.ExternalControlManager
import dji.v5.utils.common.LogUtils
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import kotlin.concurrent.thread

class UdpControlVM : DJIViewModel() {

    private val TAG = "UdpControlVM"
    private var udpSocket: DatagramSocket? = null
    private var isRunning = false
    private var serverThread: Thread? = null

    val serverStatusLiveData = MutableLiveData<String>("未启动")
    val receiveMessageLiveData = MutableLiveData<String>()

    fun startUdpServer(port: Int = 9999) {
        if (isRunning) return
        isRunning = true
        serverStatusLiveData.postValue("启动中...")

        serverThread = thread(name = "UdpControlServerThread") {
            try {
                udpSocket = DatagramSocket(port)
                udpSocket?.soTimeout = 0
                serverStatusLiveData.postValue("监听中: $port")
                LogUtils.i(TAG, "UDP 控制服务已启动，监听端口: $port")

                val buffer = ByteArray(1024)
                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)

                        val data = packet.data.copyOf(packet.length)
                        if (data.size == 24) {
                            val floats = parseUdpData(data)
                            val jsonParams = buildJsonParams(floats)
                            val timeStr = getTimeNow()
                            val msg = "接收时间：$timeStr，来自: ${packet.address.hostAddress}:${packet.port}，内容：${floats.contentToString()}"
                            LogUtils.i(TAG, msg)
                            receiveMessageLiveData.postValue(msg)
                            ExternalControlManager.handleExternalRequest(jsonParams)
                        } else {
                            LogUtils.i(TAG, "收到非24字节数据，长度: ${data.size}，已忽略")
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            LogUtils.e(TAG, "接收数据异常: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogUtils.e(TAG, "UDP 服务异常: ${e.message}")
                    serverStatusLiveData.postValue("异常: ${e.message}")
                }
            }
        }
    }

    fun stopUdpServer() {
        isRunning = false
        try {
            udpSocket?.close()
        } catch (_: Exception) {
        }
        udpSocket = null
        serverThread = null
        serverStatusLiveData.postValue("已停止")
        LogUtils.i(TAG, "UDP 控制服务已停止")
    }

    private fun parseUdpData(bytes: ByteArray): FloatArray {
        val floatCount = bytes.size / 4
        val floatArray = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val byte4 = bytes.copyOfRange(i * 4, i * 4 + 4)
            floatArray[i] = ByteBuffer.wrap(byte4)
                .order(ByteOrder.LITTLE_ENDIAN).float
        }
        return floatArray
    }

    private fun buildJsonParams(floats: FloatArray): String {
        return """
        {
            "roll": ${floats.getOrNull(0) ?: 0.0},
            "pitch": ${floats.getOrNull(1) ?: 0.0},
            "verticalThrottle": ${floats.getOrNull(2) ?: 0.0},
            "yaw": ${floats.getOrNull(5) ?: 0.0},
            "rollPitchControlMode": "VELOCITY",
            "yawControlMode": "ANGULAR_VELOCITY",
            "verticalControlMode": "VELOCITY",
            "rollPitchCoordinateSystem": "BODY"
        }
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        stopUdpServer()
    }

    private fun getTimeNow(): String {
        val currentTime = System.currentTimeMillis()
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(currentTime)
    }

    fun isServerRunning(): Boolean = isRunning
}
