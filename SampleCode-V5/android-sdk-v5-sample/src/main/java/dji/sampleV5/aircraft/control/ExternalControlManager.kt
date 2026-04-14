package dji.sampleV5.aircraft.control

import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.utils.common.JsonUtil
import dji.v5.utils.common.LogUtils

/**
 * 外部控制管理器
 * 用于接收外部请求参数（如 JSON 字符串）并调用 MSDK 虚拟摇杆高级参数接口进行控制
 */
object ExternalControlManager {

    private val TAG = LogUtils.getTag(this)
    private var httpControlServer: HttpControlServer? = null

    /**
     * 启动控制服务（包括 HTTP 服务器）
     */
    fun startControlService(port: Int = 8080) {
        if (httpControlServer == null) {
            httpControlServer = HttpControlServer(port)
            httpControlServer?.start()
            LogUtils.i(TAG, "外部控制服务已启动")
        }
    }

    /**
     * 停止控制服务
     */
    fun stopControlService() {
        httpControlServer?.stop()
        httpControlServer = null
        LogUtils.i(TAG, "外部控制服务已停止")
    }

    /**
     * 处理外部控制请求并执行飞行指令
     * @param jsonParams 外部请求的 JSON 参数字符串
     * 
     * JSON 格式示例（所有参数均为可选，若不传则保持默认）：
     * {
     *   "pitch": 0.5,        // 俯仰角或速度
     *   "roll": 0.0,         // 翻滚角或速度
     *   "yaw": 10.0,         // 航向角或角速度
     *   "verticalThrottle": 1.2,     // 垂直高度或速度
     *   "rollPitchControlMode": "VELOCITY", // 俯仰翻滚控制模式 (ANGLE 或 VELOCITY)
     *   "yawControlMode": "ANGULAR_VELOCITY", // 航向控制模式 (ANGLE 或 ANGULAR_VELOCITY)
     *   "verticalControlMode": "VELOCITY"     // 垂直控制模式 (POSITION 或 VELOCITY)
     * }
     */
    fun handleExternalRequest(jsonParams: String) {
        try {
            // 1. 将 JSON 解析为 SDK 的飞行控制参数对象
            val param = JsonUtil.toBean(jsonParams, VirtualStickFlightControlParam::class.java)
            
            if (param == null) {
                LogUtils.e(TAG, "解析失败：传入的参数 JSON 格式不正确")
                return
            }

            // 2. 发送控制参数到无人机
            // 注意：调用此接口前需确保已通过 UI 或代码开启了虚拟摇杆控制权 (enableVirtualStick)
            VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(param)
            LogUtils.i(TAG, "成功执行外部控制指令: $jsonParams")

        } catch (e: Exception) {
            LogUtils.e(TAG, "处理外部控制请求时发生异常: ${e.message}")
        }
    }
}
