package net.flibustareader

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import net.flibustareader.core.*

class CatalogState : ViewModel() {
    var books by mutableStateOf<List<LocalBook>>(emptyList())
    var tab by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var searchAuthors by mutableStateOf(false)
    var page by mutableStateOf<CatalogPage?>(null)
    var currentAddress = ""
    val history = mutableListOf<Pair<String, CatalogPage>>()
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var initialized = false
}
