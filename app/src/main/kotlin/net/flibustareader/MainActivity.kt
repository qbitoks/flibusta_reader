package net.flibustareader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import net.flibustareader.core.*
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private lateinit var repository: BookRepository
    private var books: List<LocalBook> get() = state.books; set(value) { state.books = value }
    private val state by lazy { androidx.lifecycle.ViewModelProvider(this)[CatalogState::class.java] }
    private var tab: Int get() = state.tab; set(value) { state.tab = value }
    private var query: String get() = state.query; set(value) { state.query = value }
    private var searchAuthors: Boolean get() = state.searchAuthors; set(value) { state.searchAuthors = value }
    private var page: CatalogPage? get() = state.page; set(value) { state.page = value }
    private var currentAddress: String get() = state.currentAddress; set(value) { state.currentAddress = value }
    private val history get() = state.history
    private var busy: Boolean get() = state.busy; set(value) { state.busy = value }
    private var status: String? get() = state.status; set(value) { state.status = value }
    private var selected by mutableStateOf<CatalogEntry?>(null)
    private var sources by mutableStateOf(false)
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform("Импорт книги…") { repository.import(uri); books = repository.library(); tab = 0 }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = BookRepository(applicationContext)
        books = repository.library()
        if (!state.initialized) {
            state.initialized = true
            savedInstanceState?.let {
                tab = it.getInt("tab")
                query = it.getString("query").orEmpty()
                searchAuthors = it.getBoolean("authors")
                currentAddress = it.getString("address").orEmpty()
                val addresses = it.getStringArrayList("history").orEmpty()
                if (currentAddress.isNotBlank()) perform("Восстановление каталога…") {
                    addresses.forEach { address -> history.add(address to repository.page(address)) }
                    page = repository.page(currentAddress)
                }
            }
        }
        setContent { ReaderTheme { App() } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab)
        outState.putString("query", query)
        outState.putBoolean("authors", searchAuthors)
        outState.putString("address", currentAddress)
        outState.putStringArrayList("history", ArrayList(history.map { it.first }))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() { super.onResume(); if (::repository.isInitialized) books = repository.library() }

    private fun perform(message: String, action: () -> Unit) {
        if (busy) return
        busy = true; status = message
        state.viewModelScope.launch {
            try { runInterruptible(Dispatchers.IO) { action() }; status = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Не удалось выполнить действие" }
            finally { busy = false }
        }
    }

    private fun load(address: String, addHistory: Boolean = false, append: Boolean = false) {
        val previous = page
        val previousAddress = currentAddress
        perform("Загрузка каталога…") {
            val result = repository.page(address)
            if (addHistory && previous != null) history.add(previousAddress to previous)
            page = if (append && previous != null) result.copy(entries = (previous.entries + result.entries).distinctBy { it.id }) else result
            currentAddress = address
        }
    }

    private fun open(book: LocalBook) {
        startActivity(Intent(this, ReaderActivity::class.java).putExtra("id", book.id).putExtra("path", book.path).putExtra("title", book.title).putExtra("author", book.author))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun App() {
        BackHandler(enabled = !busy && tab == 1 && history.isNotEmpty()) {
            val previous = history.removeAt(history.lastIndex); currentAddress = previous.first; page = previous.second
        }
        Scaffold(
            topBar = {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("FLIBUSTA READER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                            Text(if (tab == 0) "Мои книги" else "Найти историю", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { sources = true }, enabled = !busy) { Text("Источник") }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("▤", fontSize = 24.sp) }, label = { Text("Библиотека") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("⌕", fontSize = 28.sp) }, label = { Text("Поиск") })
                }
            }
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset)) {
                if (status != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(status!!, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            if (!busy) TextButton(onClick = { status = null }) { Text("Закрыть") }
                        }
                    }
                }
                if (tab == 0) Library() else Catalog()
            }
        }
        selected?.let { entry ->
            AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text(entry.title) },
                text = { LazyColumn { item { Text(entry.author, fontWeight = FontWeight.Medium); Spacer(Modifier.height(12.dp)); Text(entry.summary.ifBlank { "Описание отсутствует" }) } } },
                confirmButton = {
                    Row {
                        if (entry.downloads.isEmpty()) Text("Скачивание недоступно", Modifier.padding(16.dp))
                        entry.downloads.keys.forEach { format ->
                            TextButton(enabled = !busy, onClick = {
                                selected = null
                                perform("Скачивание…") {
                                    repository.download(entry, format) { message -> status = message }
                                    books = repository.library(); tab = 0
                                }
                            }) { Text("Скачать $format") }
                        }
                    }
                }, dismissButton = { TextButton(onClick = { selected = null }, enabled = !busy) { Text("Назад") } })
        }
        if (sources) SourcesDialog()
    }

    @Composable private fun Library() {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { importer.launch(arrayOf("*/*")) }) { Text("Добавить файл") }
            OutlinedButton(enabled = !busy, onClick = { perform("Подготовка…") { val book = repository.demo(); books = repository.library(); runOnUiThread { if (!isDestroyed) open(book) } } }) { Text("Демо-книга") }
        }
        val prefs = getSharedPreferences("reader", MODE_PRIVATE)
        val lastId = prefs.getString("lastBook", null)
        val last = books.firstOrNull { it.id == lastId }
        if (last != null) {
            Card(Modifier.padding(20.dp).fillMaxWidth().clickable { open(last) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("ПРОДОЛЖИТЬ ЧТЕНИЕ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(last.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Открыть на сохранённом месте →", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (books.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Каждая книга —\nновое путешествие", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Найдите книгу в каталоге или добавьте FB2 / EPUB с устройства. Демо-книга поможет проверить чтение без интернета.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(books, key = { it.id }) { book ->
                    Column {
                        Cover(book.title, book.id, Modifier.fillMaxWidth().height(205.dp).clickable { open(book) })
                        Spacer(Modifier.height(8.dp))
                        Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { open(book) })
                        Text(book.author.ifBlank { book.format }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(enabled = !busy, onClick = { perform("Удаление…") { repository.remove(book); books = repository.library() } }, contentPadding = PaddingValues(0.dp)) { Text("Удалить", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }

    @Composable private fun Cover(title: String, id: String, modifier: Modifier) {
        val colors = listOf(Color(0xFF53446C), Color(0xFF3F6561), Color(0xFF9A634E), Color(0xFF52657E))
        val color = colors[Math.floorMod(id.hashCode(), colors.size)]
        Surface(shape = RoundedCornerShape(12.dp), modifier = modifier) {
            Box(Modifier.background(Brush.verticalGradient(listOf(color, color.copy(alpha = .85f)))).padding(18.dp)) {
                Text("FR / БИБЛИОТЕКА", color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart))
                Text("ЧИТАТЬ →", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
    }

    @Composable private fun Catalog() {
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(if (searchAuthors) "Фамилия автора" else "Название книги") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !searchAuthors, onClick = { searchAuthors = false }, label = { Text("Книги") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = searchAuthors, onClick = { searchAuthors = true }, label = { Text("Авторы") })
                Spacer(Modifier.weight(1f))
                Button(enabled = !busy && query.isNotBlank(), onClick = {
                    history.clear()
                    load("${repository.catalogBase}/opds/search?searchType=${if (searchAuthors) "authors" else "books"}&searchTerm=${URLEncoder.encode(query.trim(), "UTF-8")}")
                }) { Text("Найти") }
            }
            Row {
                TextButton(enabled = !busy, onClick = { history.clear(); load("${repository.catalogBase}/opds/new") }) { Text("Новинки") }
                if (history.isNotEmpty()) TextButton(enabled = !busy, onClick = { val previous = history.removeAt(history.lastIndex); currentAddress = previous.first; page = previous.second }) { Text("← Назад") }
            }
        }
        val result = page
        if (result == null) {
            Column(Modifier.padding(28.dp)) {
                Text("Что будем читать?", style = MaterialTheme.typography.headlineSmall)
                Text("Поиск по названию или фамилии автора. Найденные книги можно скачать и читать без сети.", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(result.title, style = MaterialTheme.typography.titleMedium) }
                if (result.entries.isEmpty()) item { Text("Ничего не найдено. Попробуйте другой запрос.") }
                items(result.entries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { val target = entry.navigation; if (target != null) load(target, addHistory = true) else selected = entry }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (entry.navigation != null) "→" else "▤", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                if (entry.author.isNotBlank()) Text(entry.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.downloads.isNotEmpty()) Text(entry.downloads.keys.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
                result.next?.let { next -> item { OutlinedButton(enabled = !busy, onClick = { load(next, append = true) }, modifier = Modifier.fillMaxWidth()) { Text("Показать ещё") } } }
            }
        }
    }

    @Composable private fun SourcesDialog() {
        var catalog by remember { mutableStateOf(repository.catalogBase) }
        var downloads by remember { mutableStateOf(repository.downloadBase) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { sources = false }, title = { Text("Источники книг") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Каталог и сервер скачивания можно настроить отдельно. flisland.net используйте для каталога.")
                OutlinedTextField(catalog, { catalog = it }, label = { Text("Каталог · HTTPS") }, singleLine = true)
                OutlinedTextField(downloads, { downloads = it }, label = { Text("Скачивание · HTTPS") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            try { repository.saveSources(catalog, downloads); history.clear(); page = null; sources = false }
            catch (e: Exception) { error = e.message }
        }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { sources = false }) { Text("Отмена") } })
    }
}
