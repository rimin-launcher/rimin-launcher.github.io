package kr.personal.sealion.one.key

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kr.personal.sealion.one.core.Command

/** Invisible trampoline used by launcher shortcuts: forwards one command to [KeyService]. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent.getStringExtra(KeyService.EXTRA_COMMAND)?.let { n -> Command.entries.firstOrNull { it.name == n } }
        if (command != null && PhoneKeyStore.get(this).vehicle.value != null) {
            KeyService.start(this, command)
            Toast.makeText(this, "${command.label} 요청", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "먼저 차량을 등록하세요", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
