package app.decibravo

import android.app.Application
import app.decibravo.audio.FrameSource
import app.decibravo.audio.Recorder
import app.decibravo.data.Store

class DeciBravoApp : Application() {
    val store: Store by lazy { Store(getExternalFilesDir(null) ?: filesDir) }

    fun frameSource(): FrameSource = Recorder(this)
}
