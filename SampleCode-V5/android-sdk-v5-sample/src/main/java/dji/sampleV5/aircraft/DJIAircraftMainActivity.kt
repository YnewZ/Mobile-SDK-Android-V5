package dji.sampleV5.aircraft

import android.os.Bundle
import dji.sampleV5.aircraft.control.ExternalControlManager
import dji.sampleV5.aircraft.models.BackgroundLiveStreamManager
import dji.v5.common.utils.GeoidManager
import dji.v5.ux.core.communication.DefaultGlobalPreferences
import dji.v5.ux.core.communication.GlobalPreferencesManager
import dji.v5.ux.core.util.UxSharedPreferencesUtil
import dji.v5.ux.sample.showcase.defaultlayout.DefaultLayoutActivity
import dji.v5.ux.sample.showcase.widgetlist.WidgetsActivity

class DJIAircraftMainActivity : DJIMainActivity() {

    private val rtmpUrl = "rtmp://172.20.63.105:2035/live/QWE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ExternalControlManager.startControlService(8080)
        observeSDKRegisterForBackgroundStream()
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundLiveStreamManager.stopBackgroundStream()
        ExternalControlManager.stopControlService()
    }

    private fun observeSDKRegisterForBackgroundStream() {
        msdkManagerVM.lvRegisterState.observe(this) { resultPair ->
            if (resultPair.first) {
                BackgroundLiveStreamManager.startBackgroundStream(rtmpUrl)
            }
        }
    }

    override fun prepareUxActivity() {
        UxSharedPreferencesUtil.initialize(this)
        GlobalPreferencesManager.initialize(DefaultGlobalPreferences(this))
        GeoidManager.getInstance().init(this)

        enableDefaultLayout(DefaultLayoutActivity::class.java)
        enableWidgetList(WidgetsActivity::class.java)
    }

    override fun prepareTestingToolsActivity() {
        enableTestingTools(AircraftTestingToolsActivity::class.java)
    }
}