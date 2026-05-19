package dji.sampleV5.aircraft.util

import dji.v5.utils.common.LogUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.net.SocketException

object FtpUploadUtil {
    private const val TAG = "FtpUploadUtil"

    var ftpHost = "172.20.63.196"
    var ftpPort = 21
    var ftpUser = "anonymous"
    var ftpPassword = ""
    var remotePath = "/drone_photos/"

    fun uploadFile(file: File, remoteFileName: String, callback: (Boolean, String) -> Unit) {
        Thread {
            val ftpClient = FTPClient()
            try {
                ftpClient.connect(ftpHost, ftpPort)
                val loginSuccess = ftpClient.login(ftpUser, ftpPassword)
                if (!loginSuccess) {
                    callback(false, "FTP login failed")
                    return@Thread
                }

                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.enterLocalPassiveMode()

                if (!ftpClient.changeWorkingDirectory(remotePath)) {
                    ftpClient.makeDirectory(remotePath)
                    ftpClient.changeWorkingDirectory(remotePath)
                }

                FileInputStream(file).use { input ->
                    val success = ftpClient.storeFile(remoteFileName, input)
                    if (success) {
                        LogUtils.i(TAG, "FTP upload success: $remoteFileName")
                        callback(true, "Uploaded to FTP: $remotePath$remoteFileName")
                    } else {
                        callback(false, "FTP storeFile failed")
                    }
                }

                ftpClient.logout()
            } catch (e: SocketException) {
                LogUtils.e(TAG, "FTP connection error: ${e.message}")
                callback(false, "Connection error: ${e.message}")
            } catch (e: Exception) {
                LogUtils.e(TAG, "FTP upload error: ${e.message}")
                callback(false, e.message ?: "Unknown error")
            } finally {
                if (ftpClient.isConnected) {
                    try { ftpClient.disconnect() } catch (_: Exception) {}
                }
            }
        }.start()
    }
}
