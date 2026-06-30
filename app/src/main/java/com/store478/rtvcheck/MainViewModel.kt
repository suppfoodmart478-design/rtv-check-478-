package com.store478.rtvcheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.store478.rtvcheck.data.ItemDatabaseHelper
import com.store478.rtvcheck.data.LookupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = ItemDatabaseHelper(application)

    private val _lookupState = MutableStateFlow<LookupState>(LookupState.Idle)
    val lookupState: StateFlow<LookupState> = _lookupState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    /** Triggered by barcode scan or manual submit (Enter / search button). */
    fun lookup(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        _inputText.value = trimmed
        _lookupState.value = LookupState.Loading

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                dbHelper.findByUpcOrSku(trimmed)
            }
            _lookupState.value = if (result != null) {
                LookupState.Found(result)
            } else {
                LookupState.NotFound(trimmed)
            }
        }
    }

    fun clear() {
        _inputText.value = ""
        _lookupState.value = LookupState.Idle
    }
}
