package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.andrerinas.openheadunit.utils.AppLog
import com.google.protobuf.UnknownFieldSet

/**
 * Test-mode probe for a second video sink in the instrument cluster (#77).
 *
 * Declares a small H.264 sink marked as a cluster display and records what the phone does with
 * it: whether it opens the channel, sets the sink up, starts it, sends a codec configuration and
 * frames, and at which rate. Nothing is decoded or drawn: the frames are counted, acknowledged so
 * the phone keeps sending, and dropped. The answer is the `[ClusterProbe]` summary in the log at
 * the end of the session; "declared, never opened" means the phone does not offer a cluster
 * stream to this head unit and #77 stops there.
 *
 * Off unless test mode and the probe setting are both on, so a normal session declares exactly
 * what it declared before.
 */
object ClusterSinkProbe {

    /** First channel id the head unit does not use (see protocol/Channel). */
    const val CHANNEL = 14

    // MediaSinkService fields this pre-generated Control.java lacks, sent as unknown fields:
    // 6 display_id, 7 display_type (0 main, 1 cluster, 2 auxiliary).
    private const val FIELD_DISPLAY_ID = 6
    private const val FIELD_DISPLAY_TYPE = 7
    private const val DISPLAY_ID = 1L
    private const val DISPLAY_TYPE_CLUSTER = 1L

    @Volatile private var stats: ClusterProbeStats? = null

    /** Called while the service discovery response is built; adds the sink when enabled. */
    fun declare(services: MutableList<Control.Service>, enabled: Boolean, nowMs: Long) {
        stats = null
        if (!enabled) return
        val sink = Control.Service.MediaSinkService.newBuilder().apply {
            availableType = Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP
            audioType = Media.AudioStreamType.NONE
            availableWhileInCall = true
            addVideoConfigs(Control.Service.MediaSinkService.VideoConfiguration.newBuilder().apply {
                codecResolution = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
                frameRate = Control.Service.MediaSinkService.VideoConfiguration.VideoFrameRateType._30
                setDensity(160)
                setMarginWidth(0)
                setMarginHeight(0)
                setDecoderAdditionalDepth(1)
                setVideoCodecType(Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP)
            })
            mergeUnknownFields(
                UnknownFieldSet.newBuilder()
                    .addField(FIELD_DISPLAY_ID, UnknownFieldSet.Field.newBuilder().addVarint(DISPLAY_ID).build())
                    .addField(FIELD_DISPLAY_TYPE, UnknownFieldSet.Field.newBuilder().addVarint(DISPLAY_TYPE_CLUSTER).build())
                    .build(),
            )
        }.build()
        services.add(Control.Service.newBuilder().setId(CHANNEL).setMediaSinkService(sink).build())
        stats = ClusterProbeStats(nowMs)
        AppLog.i("[ClusterProbe] declared cluster sink on channel $CHANNEL (800x480 H.264 30 fps, display_type=cluster)")
    }

    /**
     * Takes every message on [CHANNEL] except the channel open, which the gateway answers as for
     * any channel. Returns false when the probe is off or the message is not its own.
     */
    internal fun handle(message: AapMessage, transport: AapTransport, nowMs: Long): Boolean {
        val s = stats ?: return false
        if (message.channel != CHANNEL) return false
        val flags = message.flags.toInt()
        // A continuation fragment has no type of its own: count its bytes only.
        if (flags and FLAG_FIRST == 0) {
            s.onBytes(message.size)
            return true
        }
        when (message.type) {
            MSG_CHANNEL_OPEN -> {
                s.onOpen(nowMs)
                AppLog.i("[ClusterProbe] phone opened the cluster channel")
                return false
            }
            Media.MsgType.MEDIA_MESSAGE_SETUP_VALUE -> {
                val request = message.parse(Media.MediaSetupRequest.newBuilder()).build()
                s.onSetup(nowMs)
                AppLog.i("[ClusterProbe] setup request, codec type ${request.type}")
                transport.send(AapMessage(CHANNEL, Media.MsgType.MEDIA_MESSAGE_CONFIG_VALUE,
                    Media.Config.newBuilder()
                        .setStatus(Media.Config.ConfigStatus.HEADUNIT)
                        .setMaxUnacked(4)
                        .addConfigurationIndices(0)
                        .build()))
                transport.send(AapMessage(CHANNEL, Media.MsgType.MEDIA_MESSAGE_VIDEO_FOCUS_NOTIFICATION_VALUE,
                    Media.VideoFocusNotification.newBuilder()
                        .setMode(Media.VideoFocusMode.VIDEO_FOCUS_PROJECTED)
                        .setUnsolicited(true)
                        .build()))
            }
            Media.MsgType.MEDIA_MESSAGE_START_VALUE -> {
                val start = message.parse(Media.Start.newBuilder()).build()
                transport.setSessionId(CHANNEL, start.sessionId)
                s.onStart(nowMs)
                AppLog.i("[ClusterProbe] start, session ${start.sessionId}, config ${start.configurationIndex}")
            }
            Media.MsgType.MEDIA_MESSAGE_STOP_VALUE -> {
                s.onStop()
                AppLog.i("[ClusterProbe] stop")
            }
            Media.MsgType.MEDIA_MESSAGE_CODEC_CONFIG_VALUE, Media.MsgType.MEDIA_MESSAGE_DATA_VALUE -> {
                val first = s.onFrame(message.type == Media.MsgType.MEDIA_MESSAGE_CODEC_CONFIG_VALUE, message.size, nowMs)
                if (first) AppLog.i("[ClusterProbe] first ${if (message.type == 1) "codec config" else "frame"} from the phone")
                transport.sendMediaAck(CHANNEL)
            }
            Media.MsgType.MEDIA_MESSAGE_VIDEO_FOCUS_REQUEST_VALUE -> {
                val focus = message.parse(Media.VideoFocusRequestNotification.newBuilder()).build()
                AppLog.i("[ClusterProbe] focus request ${focus.mode} (${focus.reason})")
            }
            else -> AppLog.i("[ClusterProbe] message type ${message.type}, ${message.size} bytes")
        }
        return true
    }

    /** Logs what the phone did with the sink; called once when the session ends. */
    fun logSummary(nowMs: Long) {
        val s = stats ?: return
        stats = null
        AppLog.i("[ClusterProbe] ${s.summary(nowMs)}")
    }

    private const val FLAG_FIRST = 0x01
    private const val MSG_CHANNEL_OPEN = Control.ControlMsgType.MESSAGE_CHANNEL_OPEN_REQUEST_VALUE
}

/** What the phone did with the cluster sink during one session. Times are elapsed-realtime ms. */
class ClusterProbeStats(private val declaredAtMs: Long) {
    private var openAtMs = -1L
    private var setupAtMs = -1L
    private var startAtMs = -1L
    private var firstFrameAtMs = -1L
    private var lastFrameAtMs = -1L
    private var stops = 0
    private var codecConfigs = 0
    private var frames = 0
    private var bytes = 0L

    fun onOpen(nowMs: Long) { if (openAtMs < 0) openAtMs = nowMs }
    fun onSetup(nowMs: Long) { if (setupAtMs < 0) setupAtMs = nowMs }
    fun onStart(nowMs: Long) { if (startAtMs < 0) startAtMs = nowMs }
    fun onStop() { stops++ }
    fun onBytes(size: Int) { bytes += size }

    /** Returns true for the first message carrying video (config or frame). */
    fun onFrame(codecConfig: Boolean, size: Int, nowMs: Long): Boolean {
        val first = codecConfigs + frames == 0
        if (codecConfig) codecConfigs++ else frames++
        bytes += size
        if (first) firstFrameAtMs = nowMs
        lastFrameAtMs = nowMs
        return first
    }

    /** How far the phone went: the first step it never reached ends the probe. */
    val furthest: String
        get() = when {
            frames > 0 -> "streamed"
            codecConfigs > 0 -> "codec config only"
            startAtMs >= 0 -> "started, no video"
            setupAtMs >= 0 -> "set up, never started"
            openAtMs >= 0 -> "opened, never set up"
            else -> "declared, never opened"
        }

    fun summary(nowMs: Long): String {
        fun at(t: Long) = if (t < 0) "-" else "+${t - declaredAtMs} ms"
        val streamMs = lastFrameAtMs - firstFrameAtMs
        val fps = if (frames > 1 && streamMs > 0) "%.1f".format(java.util.Locale.ROOT, (frames - 1) * 1000.0 / streamMs) else "-"
        val kbps = if (streamMs > 0) "%.0f".format(java.util.Locale.ROOT, bytes * 8.0 / streamMs) else "-"
        return "result=$furthest open=${at(openAtMs)} setup=${at(setupAtMs)} start=${at(startAtMs)} " +
            "firstVideo=${at(firstFrameAtMs)} codecConfigs=$codecConfigs frames=$frames bytes=$bytes " +
            "fps=$fps kbps=$kbps stops=$stops session=${nowMs - declaredAtMs} ms"
    }
}
