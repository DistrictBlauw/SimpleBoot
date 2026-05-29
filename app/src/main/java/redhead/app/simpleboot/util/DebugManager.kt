package redhead.app.simpleboot.util

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.security.MessageDigest

object DebugManager {
    private const val PREFS = "SimpleBootPrefs"
    private const val KEY_DEBUG = "debug_mode"
    private const val DEBUG_PASSWORD_MD5 = "e51e4e7e50736d74d66d09d33c008cc9"

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEBUG, enabled).apply()
    }

    fun verifyPassword(password: String): Boolean {
        return md5(password) == DEBUG_PASSWORD_MD5
    }

    fun dumpUsbState(context: Context) {
        if (!isEnabled(context)) return
        val cmds = listOf(
            "echo '=== USB State Dump ==='",
            "getprop sys.usb.config",
            "getprop sys.usb.state",
            "getprop sys.usb.controller",
            "cat /sys/class/udc/*/state 2>/dev/null || echo 'no udc state'",
            "ls -la /sys/class/udc/ 2>/dev/null || echo 'no udc'",
            "echo '=== ConfigFS (actual mount) ==='",
            "mount | grep configfs || echo 'configfs not mounted'",
            "ls -la /config/usb_gadget/ 2>/dev/null || echo 'no /config gadget'",
            "echo '=== Gadget g1 (via /config) ==='",
            "cat /config/usb_gadget/g1/UDC 2>/dev/null || echo 'no UDC'",
            "cat /config/usb_gadget/g1/idVendor 2>/dev/null || echo 'no vendor'",
            "cat /config/usb_gadget/g1/idProduct 2>/dev/null || echo 'no product'",
            "ls -la /config/usb_gadget/g1/functions/ 2>/dev/null || echo 'no functions'",
            "ls -la /config/usb_gadget/g1/configs/ 2>/dev/null || echo 'no configs'",
            "echo '=== Gadget g2 ==='",
            "cat /config/usb_gadget/g2/UDC 2>/dev/null || echo 'no g2 UDC'",
            "ls -la /config/usb_gadget/g2/functions/ 2>/dev/null || echo 'no g2 functions'",
            "echo '=== Mass Storage (g1) ==='",
            "cat /config/usb_gadget/g1/functions/mass_storage.0/lun.0/file 2>/dev/null || echo 'no lun.0 file'",
            "cat /config/usb_gadget/g1/functions/mass_storage.0/lun/file 2>/dev/null || echo 'no lun file'",
            "cat /config/usb_gadget/g1/functions/mass_storage.0/lun.0/cdrom 2>/dev/null || echo 'no cdrom'",
            "cat /config/usb_gadget/g1/functions/mass_storage.0/lun.0/ro 2>/dev/null || echo 'no ro'",
            "echo '=== Mass Storage (g2) ==='",
            "cat /config/usb_gadget/g2/functions/mass_storage.0/lun.0/file 2>/dev/null || echo 'no g2 lun file'",
            "echo '=== Legacy USB ==='",
            "cat /sys/class/android_usb/android0/enable 2>/dev/null || echo 'no legacy enable'",
            "cat /sys/class/android_usb/android0/functions 2>/dev/null || echo 'no legacy functions'",
            "echo '=== Loop Devices ==='",
            "losetup -a 2>/dev/null | grep -E 'SimpleBoot|emulated' || echo 'no SimpleBoot loop devices'",
            "echo '=== USB Config Files ==='",
            "ls -la /config/usb_gadget/g1/configs/b.1/ 2>/dev/null || echo 'no b.1 config'",
            "readlink /config/usb_gadget/g1/configs/b.1/* 2>/dev/null || echo 'no symlinks in b.1'",
            "echo '=== USB State Dump End ==='"
        )
        val res = Shell.cmd(*cmds.toTypedArray()).exec()
        val output = (res.out + res.err).joinToString("\n")
        LogManager.logToFile(context, "[DebugDump]\n$output")
    }
}
