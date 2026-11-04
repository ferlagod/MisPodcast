/*
 * Mis Podcast - Gestor y reproductor libre de podcasts para Android
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General de GNU tal como fue
 * publicada por la Free Software Foundation, ya sea la versión 3 de la
 * Licencia, o (a tu elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil,
 * pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
 * COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.
 * Consulta la Licencia Pública General de GNU para más detalles.
 *
 * Deberías haber recibido una copia de la Licencia Pública General de GNU
 * junto con este programa. Si no es así, consulta <https://www.gnu.org/licenses/>.
 */

package com.ferlagod.mispodcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mispodcast.MisPodcastApplication
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.model.GlobalStats
import com.ferlagod.mispodcast.data.model.WrappedData
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de estadísticas: totales de escucha, desglose por podcast y resumen anual "Wrapped".
 */
class StatsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PodcastRepository = (application as MisPodcastApplication).container.podcastRepository,
    preferencesManager: UserPreferencesManager = (application as MisPodcastApplication).container.preferencesManager
) : AndroidViewModel(application) {

    val podcastStats: StateFlow<List<PlaybackStatsEntity>> = repository.getPodcastStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalStats: StateFlow<GlobalStats> = repository.getGlobalStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalStats())

    val timeSavedMs: StateFlow<Long> = preferencesManager.timeSavedMsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==========================================
    // RESUMEN ANUAL "WRAPPED"
    // ==========================================

    private val _wrappedData = MutableStateFlow<WrappedData?>(null)
    val wrappedData: StateFlow<WrappedData?> = _wrappedData.asStateFlow()

    private val _selectedWrappedYear = MutableStateFlow(java.time.Year.now().value)
    val selectedWrappedYear: StateFlow<Int> = _selectedWrappedYear.asStateFlow()

    private val _wrappedAvailableYears = MutableStateFlow(listOf(java.time.Year.now().value))
    val wrappedAvailableYears: StateFlow<List<Int>> = _wrappedAvailableYears.asStateFlow()

    private val _isLoadingWrapped = MutableStateFlow(false)
    val isLoadingWrapped: StateFlow<Boolean> = _isLoadingWrapped.asStateFlow()

    init {
        loadWrapped()
    }

    fun loadWrapped(year: Int = _selectedWrappedYear.value) {
        _selectedWrappedYear.value = year
        viewModelScope.launch {
            _isLoadingWrapped.value = true
            _wrappedAvailableYears.value = repository.getWrappedAvailableYears()
            _wrappedData.value = repository.getWrappedStats(year)
            _isLoadingWrapped.value = false
        }
    }
}
