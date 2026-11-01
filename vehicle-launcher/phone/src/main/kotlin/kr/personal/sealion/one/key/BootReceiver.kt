package kr.personal.sealion.one.key

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PhoneKeyStore.get(context).vehicle.value != null) KeyService.start(context)
    }
}
