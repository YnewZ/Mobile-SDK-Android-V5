package dji.sampleV5.aircraft.control

import dji.v5.utils.common.LogUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 一个极简的 HTTP 服务器，用于接收来自 Postman 等工具的控制请求
 */
class HttpControlServer(private val port: Int = 8080) {

    private val TAG = "HttpControlServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "HttpControlServerThread") {
            try {
                serverSocket = ServerSocket(port)
                LogUtils.i(TAG, "HTTP 控制服务已启动，监听端口: $port")
                
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogUtils.e(TAG, "服务运行异常: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        LogUtils.i(TAG, "HTTP 控制服务已停止")
    }

    private fun handleClient(client: Socket) {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                var line: String? = reader.readLine()
                
                // 简单的 HTTP 协议解析：检查方法和路径
                if (line != null && line.startsWith("POST /virtualstick/control")) {
                    var contentLength = 0
                    while (line != null && line.isNotEmpty()) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = line.substring(15).trim().toInt()
                        }
                        line = reader.readLine()
                    }

                    // 读取 POST Body (JSON)
                    val body = CharArray(contentLength)
                    reader.read(body, 0, contentLength)
                    val jsonParams = String(body)

                    LogUtils.i(TAG, "收到来自 HTTP 的控制请求: $jsonParams")
                    
                    // 调用统一的控制接口
                    ExternalControlManager.handleExternalRequest(jsonParams)

                    // 返回 HTTP 响应
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: application/json\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.write("{\"status\":\"success\", \"message\":\"Command received\"}".toByteArray())
                    out.flush()
                } else {
                    // 非 POST 请求返回 405
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".toByteArray())
                    out.flush()
                }
                client.close()
            } catch (e: Exception) {
                LogUtils.e(TAG, "处理客户端请求失败: ${e.message}")
            }
        }
    }
}
