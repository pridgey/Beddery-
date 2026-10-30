package dev.pridgey.beddery.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import dev.pridgey.beddery.R

/**
 * Extra setup for phone makers that close background apps and block pop-ups over the lock
 * screen unless you switch them on in their own settings. Without these, Standby can stop
 * appearing when you plug in. Android can't tell us whether they're done, so the checklist
 * only remembers that the person opened them.
 */
object OemSetup {

    enum class Brand(@StringRes val label: Int) {
        XIAOMI(R.string.oem_brand_xiaomi),
        OPPO(R.string.oem_brand_oppo),
        VIVO(R.string.oem_brand_vivo),
    }

    /** [screens] are tried in order; the app's own App info page is the last resort. */
    class Step(
        val id: String,
        @StringRes val title: Int,
        @StringRes val detail: Int,
        val screens: (Context) -> List<Intent>,
    )

    fun brand(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Brand? {
        val name = "$manufacturer $brand".lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in name } -> Brand.XIAOMI
            // OnePlus and Realme run Oppo's ColorOS (or a close copy of it).
            listOf("oppo", "realme", "oneplus").any { it in name } -> Brand.OPPO
            listOf("vivo", "iqoo").any { it in name } -> Brand.VIVO
            else -> null
        }
    }

    fun steps(brand: Brand?): List<Step> = when (brand) {
        Brand.XIAOMI -> listOf(
            Step("xiaomi_autostart", R.string.oem_xiaomi_autostart, R.string.oem_xiaomi_autostart_detail) {
                listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            },
            Step("xiaomi_lock_screen", R.string.oem_xiaomi_lock_screen, R.string.oem_xiaomi_lock_screen_detail) { context ->
                listOf(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", context.packageName),
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                        .putExtra("extra_pkgname", context.packageName),
                )
            },
            Step("xiaomi_battery", R.string.oem_xiaomi_battery, R.string.oem_xiaomi_battery_detail) { emptyList() },
        )
        Brand.OPPO -> listOf(
            // On current ColorOS both switches live in App info → Battery usage, so App info is the right page.
            Step("oppo_background", R.string.oem_oppo_background, R.string.oem_oppo_background_detail) { emptyList() },
            Step("oppo_startup", R.string.oem_oppo_startup, R.string.oem_oppo_startup_detail) {
                listOf(
                    component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                )
            },
        )
        Brand.VIVO -> listOf(
            Step("vivo_background", R.string.oem_vivo_background, R.string.oem_vivo_background_detail) { emptyList() },
            Step("vivo_autostart", R.string.oem_vivo_autostart, R.string.oem_vivo_autostart_detail) {
                listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                )
            },
        )
        null -> emptyList()
    }

    /** Opens the best settings page this phone has for [step]. */
    fun open(context: Context, step: Step, start: (Intent) -> Boolean) {
        (step.screens(context) + appInfo(context)).firstOrNull { start(it) }
    }

    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
}
