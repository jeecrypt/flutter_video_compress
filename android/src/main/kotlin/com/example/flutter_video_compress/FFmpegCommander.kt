package com.example.flutter_video_compress

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import nl.bravobit.ffmpeg.FFtask
import java.io.File

class FFmpegCommander(private val channelName: String, private val context: Context) {
    private var stopCommand = false
    private var ffTask: FFtask? = null
    private val utility = Utility(channelName)


    fun startCompress(path: String, quality: Int, deleteOrigin: Boolean,
                      result: MethodChannel.Result, messenger: BinaryMessenger) {
        val ffmpeg = FFmpeg.getInstance(context)

        if (!ffmpeg.isSupported) {
            return result.error(channelName, "FlutterVideoCompress Error",
                    "ffmpeg is supported this platform")
        }

        val dir = context.getExternalFilesDir(
                "flutter_video_compress")

        if (dir != null && !dir.exists()) dir.mkdirs()

        val file = File(dir, path.substring(path.lastIndexOf("/")))
        utility.deleteExists(file)

        val crf = 28 - quality * 3

        val cmd = arrayOf("-i", path, "-vcodec", "h264", "-crf", "$crf",
                "-acodec", "aac", file.absolutePath)

        this.ffTask = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onProgress(message: String) {
                notifyProgress(message, messenger)

                if (stopCommand) {
                    print("FlutterVideoCompress: Video compression has stopped")
                    ffTask?.killRunningProcess()
                    stopCommand = false
                    val json = utility.getMediaInfoJson(path, context)
                    json.put("isCancel", true)
                    result.success(json.toString())
                }
            }

            override fun onFinish() {
                val json = utility.getMediaInfoJson(file.absolutePath, context)
                json.put("isCancel", false)
                result.success(json.toString())
                if (deleteOrigin) {
                    File(path).delete()
                }
            }
        })
    }


    fun convertVideoToGif(path: String, startTime: Long = 0, endTime: Long, duration: Long,
                          result: MethodChannel.Result, messenger: BinaryMessenger) {
        var gifDuration = 0L
        if (endTime > 0) {
            if (startTime > endTime) {
                result.error(channelName, "FlutterVideoCompress Error",
                        "startTime should be greater than startTime")
            } else {
                gifDuration = (endTime - startTime) * 1000
            }
        } else {
            gifDuration = duration * 1000
        }

        val ffmpeg = FFmpeg.getInstance(context)

        if (!ffmpeg.isSupported) {
            return result.error(channelName, "FlutterVideoCompress Error",
                    "ffmpeg is not supported this platform")
        }

        val dir = context.getExternalFilesDir("flutter_video_compress")

        if (dir != null && !dir.exists()) dir.mkdirs()


        val file = File(dir, utility.getFileNameWithGifExtension(path))
        utility.deleteExists(file)

        val cmd = arrayOf("-i", path, "-ss", startTime.toString(), "-t", gifDuration.toString(),
                "-vf", "scale=640:-2", file.absolutePath)

        this.ffTask = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onProgress(message: String) {
                notifyProgress(message, messenger)
            }

            override fun onFinish() {
                result.success(file.absolutePath)
            }
        })
    }

    private fun notifyProgress(message: String, messenger: BinaryMessenger) {
        if ("Duration" in message) {
            val reg = Regex("""Duration: ((\d{2}:){2}\d{2}\.\d{2}).*""")
            val totalTimeStr = message.replace(reg, "$1")
            val totalTime = utility.timeStrToTimestamp(totalTimeStr.trim())
            MethodChannel(messenger, channelName).invokeMethod(
                    "updateProgressTotalTime", totalTime)
        }

        if ("frame=" in message) {
            try {
                val reg = Regex("""frame.*time=((\d{2}:){2}\d{2}\.\d{2}).*""")
                val totalTimeStr = message.replace(reg, "$1")
                val time = utility.timeStrToTimestamp(totalTimeStr.trim())
                MethodChannel(messenger, channelName)
                        .invokeMethod("updateProgressTime", time)
            } catch (e: Exception) {
                print(e.stackTrace)
            }
        }

        MethodChannel(messenger, channelName).invokeMethod("updateProgress", message)
    }

    fun stopCompress() {
        if (ffTask != null && !ffTask!!.isProcessCompleted) {
            stopCommand = true
        }
    }
}