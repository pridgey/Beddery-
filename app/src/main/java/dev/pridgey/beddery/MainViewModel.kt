package dev.pridgey.beddery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.standby.StandbyController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)

    val settings: StateFlow<AppSettings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun setStandbyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.update { it.copy(standbyEnabled = enabled, onboardingDone = it.onboardingDone || enabled) }
            if (enabled) StandbyController.start(getApplication()) else StandbyController.stop(getApplication())
        }
    }

    /** Make sure the watcher is running if the user enabled it (e.g. after it was killed). */
    fun ensureStandbyRunning(settings: AppSettings) {
        if (settings.standbyEnabled) StandbyController.start(getApplication())
    }
}
