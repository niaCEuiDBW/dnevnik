package com.example.dnevnik

import android.content.Context
import androidx.drawerlayout.widget.DrawerLayout
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton

enum class ScreenMode {
    DIARY, ARCHIVE, TRASH, PEOPLE
}

enum class SubFilter {
    NOTES, PEOPLE
}

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenu: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var fabAddNote: FloatingActionButton

    private lateinit var addNoteContainer: LinearLayout
    private lateinit var inputNoteText: EditText
    private lateinit var inputHashtags: MultiAutoCompleteTextView
    private lateinit var btnAddPhoto: Button
    private lateinit var btnSaveNote: Button

    private lateinit var mainRecyclerView: RecyclerView
    private lateinit var menuArchive: Button
    private lateinit var menuTrash: Button

    private lateinit var navDiary: Button
    private lateinit var navPeople: Button

    private lateinit var btnDropdownManage: Button
    private lateinit var layoutDropdownContent: LinearLayout
    private lateinit var btnSetPassword: Button
    private lateinit var btnRemovePassword: Button

    private lateinit var layoutLockScreenContainer: View

    private var selectedPhotoUri: Uri? = null

    private val allNotes = mutableListOf<NoteEntity>()
    private val currentDisplayedNotes = mutableListOf<NoteEntity>()
    private lateinit var notesAdapter: NotesAdapter

    private lateinit var personDao: PersonDao
    private val allPeople = mutableListOf<PersonEntity>()
    private val currentDisplayedPeople = mutableListOf<PersonEntity>()
    private lateinit var peopleAdapter: PeopleAdapter

    private var editingPosition: Int? = null
    private var currentMode = ScreenMode.DIARY
    private var currentSubFilter = SubFilter.NOTES

    private lateinit var noteDao: NoteDao
    private lateinit var encryptedPrefs: SharedPreferences

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            Toast.makeText(this, "Фото готово!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        try {
            encryptedPrefs = EncryptedSharedPreferences.create(
                this,
                "secure_diary_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("DIARY_DEBUG", "Кривой ключ шифрования! Пересоздаю файл настроек...", e)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                deleteSharedPreferences("secure_diary_prefs")
            } else {
                getSharedPreferences("secure_diary_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            }
            encryptedPrefs = EncryptedSharedPreferences.create(
                this,
                "secure_diary_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        searchEditText = findViewById(R.id.searchEditText)
        fabAddNote = findViewById(R.id.fabAddNote)
        addNoteContainer = findViewById(R.id.addNoteContainer)
        inputNoteText = findViewById(R.id.inputNoteText)
        inputHashtags = findViewById(R.id.inputHashtags)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        mainRecyclerView = findViewById(R.id.mainRecyclerView)

        navDiary = findViewById(R.id.navDiary)
        navPeople = findViewById(R.id.navPeople)
        menuArchive = findViewById(R.id.menuArchive)
        menuTrash = findViewById(R.id.menuTrash)

        btnDropdownManage = findViewById(R.id.btnDropdownManage)
        layoutDropdownContent = findViewById(R.id.layoutDropdownContent)
        btnSetPassword = findViewById(R.id.btnSetPassword)
        btnRemovePassword = findViewById(R.id.btnRemovePassword)

        layoutLockScreenContainer = findViewById(R.id.layoutLockScreenContainer)

        val db = AppDatabase.getDatabase(this)
        noteDao = db.noteDao()
        personDao = db.personDao()

        mainRecyclerView.layoutManager = LinearLayoutManager(this)
        notesAdapter = NotesAdapter(currentDisplayedNotes, onItemClick = { showNoteDetailDialog(it) })

        // ИСПРАВЛЕНИЕ: Передаем корректные обработчики клика и закрепа (через долгое нажатие / меню опций)
        peopleAdapter = PeopleAdapter(currentDisplayedPeople,
            onPersonClick = { showPersonDetailDialog(it) },
            onPinClick = { togglePersonPin(it) }
        )
        mainRecyclerView.adapter = notesAdapter

        setupHashtagAutocomplete()
        loadNotesFromDatabase()
        loadPeopleFromDatabase()

        checkAppLock()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateScreen() }
            override fun afterTextChanged(s: Editable?) {}
        })

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (currentSubFilter == SubFilter.PEOPLE) {
                    peopleAdapter.updateData(currentDisplayedPeople) // Сбрасываем визуальный свайп обратно
                    showPersonOptionsDialog(currentDisplayedPeople[position])
                } else {
                    notesAdapter.notifyItemChanged(position)
                    showActionMenuDialog(position)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(mainRecyclerView)

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        menuArchive.setOnClickListener {
            currentMode = ScreenMode.ARCHIVE
            drawerLayout.closeDrawer(GravityCompat.START)
            updateScreen()
        }

        menuTrash.setOnClickListener {
            currentMode = ScreenMode.TRASH
            drawerLayout.closeDrawer(GravityCompat.START)
            updateScreen()
        }

        btnDropdownManage.setOnClickListener {
            if (layoutDropdownContent.isVisible) {
                layoutDropdownContent.isGone = true
                btnDropdownManage.text = "Настройки  ▼"
            } else {
                layoutDropdownContent.isVisible = true
                btnDropdownManage.text = "Настройки  ▲"
            }
        }

        btnSetPassword.setOnClickListener { showSetPinDialog() }
        btnRemovePassword.setOnClickListener { showRemovePinDialog() }

        navDiary.setOnClickListener {
            currentSubFilter = SubFilter.NOTES
            currentMode = ScreenMode.DIARY
            updateScreen()
        }

        navPeople.setOnClickListener {
            currentSubFilter = SubFilter.PEOPLE
            currentMode = ScreenMode.PEOPLE
            updateScreen()
        }

        fabAddNote.setOnClickListener {
            if (currentSubFilter == SubFilter.PEOPLE) {
                showAddPersonDialog(null)
            } else {
                if (currentMode != ScreenMode.DIARY) {
                    currentMode = ScreenMode.DIARY
                    updateScreen()
                }
                editingPosition = null
                btnSaveNote.text = "Сохранить"
                inputNoteText.text.clear()
                inputHashtags.text.clear()
                addNoteContainer.isVisible = !addNoteContainer.isVisible
                if (addNoteContainer.isVisible) setupHashtagAutocomplete()
            }
        }

        btnAddPhoto.setOnClickListener { pickPhotoLauncher.launch("image/*") }

        btnSaveNote.setOnClickListener {
            val text = inputNoteText.text.toString()
            val tags = inputHashtags.text.toString()
            if (text.isNotBlank()) {
                val pos = editingPosition
                if (pos != null) {
                    val oldNote = currentDisplayedNotes[pos]
                    noteDao.updateNote(oldNote.copy(text = text, tags = tags, photoUriString = selectedPhotoUri?.toString() ?: oldNote.photoUriString))
                } else {
                    noteDao.insertNote(NoteEntity(text = text, tags = tags, photoUriString = selectedPhotoUri?.toString()))
                }
                inputNoteText.text.clear()
                inputHashtags.text.clear()
                selectedPhotoUri = null
                addNoteContainer.isGone = true
                editingPosition = null
                btnSaveNote.text = "Сохранить"
                setupHashtagAutocomplete()
                loadNotesFromDatabase()
            }
        }
    }

    private fun closeDropdownAndDrawer() {
        if (::layoutDropdownContent.isInitialized) {
            layoutDropdownContent.isGone = true
            btnDropdownManage.text = "Настройки  ▼"
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun checkAppLock() {
        val savedPin = encryptedPrefs.getString("app_pin_code", null)
        val tabContainer = findViewById<LinearLayout>(R.id.tabContainer)

        if (!savedPin.isNullOrEmpty()) {
            layoutLockScreenContainer.visibility = View.VISIBLE
            btnMenu.visibility = View.INVISIBLE
            fabAddNote.visibility = View.GONE
            tabContainer.visibility = View.GONE

            val etPin = layoutLockScreenContainer.findViewById<EditText>(R.id.etLockScreenPin)
            val btnUnlock = layoutLockScreenContainer.findViewById<Button>(R.id.btnUnlock)

            btnUnlock.setOnClickListener {
                val input = etPin.text.toString()
                if (input == savedPin) {
                    layoutLockScreenContainer.visibility = View.GONE
                    btnMenu.visibility = View.VISIBLE
                    fabAddNote.visibility = View.VISIBLE
                    tabContainer.visibility = View.VISIBLE
                    Toast.makeText(this, "Доступ разрешен!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Неверный PIN-код!", Toast.LENGTH_SHORT).show()
                    etPin.text.clear()
                }
            }
        } else {
            layoutLockScreenContainer.visibility = View.GONE
            tabContainer.visibility = View.VISIBLE
        }
    }

    private fun showSetPinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, findViewById(android.R.id.content), false)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.pin_tvPinDialogTitle)
        val etPin = dialogView.findViewById<EditText>(R.id.pin_etPinCode)
        val btnCancel = dialogView.findViewById<Button>(R.id.pin_btnCancelPin)
        val btnConfirm = dialogView.findViewById<Button>(R.id.pin_btnConfirmPin)

        tvTitle.text = "Установка нового PIN (4 цифры)"
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val pin = etPin.text.toString()
            if (pin.length == 4) {
                encryptedPrefs.edit { putString("app_pin_code", pin) }
                Toast.makeText(this, "Пароль установлен!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                closeDropdownAndDrawer()
            } else {
                Toast.makeText(this, "Нужно ровно 4 цифры!", Toast.LENGTH_SHORT).show()
            }
        }
        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE) // Белый фон
            cornerRadius = 45f // Твой фирменный радиус скругления
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()
    }

    private fun showRemovePinDialog() {
        val savedPin = encryptedPrefs.getString("app_pin_code", null)
        if (savedPin.isNullOrEmpty()) {
            Toast.makeText(this, "Пароль не был установлен!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_input, findViewById(android.R.id.content), false)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.pin_tvPinDialogTitle)
        val etPin = dialogView.findViewById<EditText>(R.id.pin_etPinCode)
        val btnCancel = dialogView.findViewById<Button>(R.id.pin_btnCancelPin)
        val btnConfirm = dialogView.findViewById<Button>(R.id.pin_btnConfirmPin)

        tvTitle.text = "Введите текущий PIN для удаления"
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val pin = etPin.text.toString()
            if (pin == savedPin) {
                encryptedPrefs.edit { remove("app_pin_code") }
                Toast.makeText(this, "Защита отключена", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                closeDropdownAndDrawer()
            } else {
                Toast.makeText(this, "Неверный PIN!", Toast.LENGTH_SHORT).show()
                etPin.text.clear()
            }
        }
        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE) // Белый фон
            cornerRadius = 45f // Твой фирменный радиус скругления
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()
    }

    private fun loadPeopleFromDatabase() {
        allPeople.clear()
        allPeople.addAll(personDao.getAllPeople())
        updateScreen()
    }

    private fun loadNotesFromDatabase() {
        allNotes.clear()
        allNotes.addAll(noteDao.getAllNotes())
        if (allNotes.isEmpty()) {
            noteDao.insertNote(NoteEntity(text = "Добро пожаловать в дневник!", tags = "#дневник", photoUriString = null))
            allNotes.addAll(noteDao.getAllNotes())
        }
        updateScreen()
    }

    private fun updateScreen() {
        val searchQuery = searchEditText.text.toString().trim().lowercase()
        addNoteContainer.isGone = true
        mainRecyclerView.isVisible = true

        if (currentSubFilter == SubFilter.PEOPLE) {
            mainRecyclerView.adapter = peopleAdapter
            currentDisplayedPeople.clear()

            val filtered = when (currentMode) {
                ScreenMode.PEOPLE, ScreenMode.DIARY -> {
                    searchEditText.hint = "Поиск людей..."
                    fabAddNote.isVisible = true
                    allPeople.filter { !it.isArchived && !it.isDeleted }
                }
                ScreenMode.ARCHIVE -> {
                    searchEditText.hint = "Архив Людей"
                    fabAddNote.isGone = true
                    allPeople.filter { it.isArchived && !it.isDeleted }
                }
                ScreenMode.TRASH -> {
                    searchEditText.hint = "Корзина Людей"
                    fabAddNote.isGone = true
                    allPeople.filter { it.isDeleted }
                }
            }

            val sortedPeople = filtered.sortedWith(
                compareByDescending<PersonEntity> { it.isPinned }.thenByDescending { it.id }
            )

            if (searchQuery.isNotEmpty()) {
                currentDisplayedPeople.addAll(sortedPeople.filter { it.name.lowercase().contains(searchQuery) })
            } else {
                currentDisplayedPeople.addAll(sortedPeople)
            }
            peopleAdapter.updateData(currentDisplayedPeople)

        } else {
            mainRecyclerView.adapter = notesAdapter
            currentDisplayedNotes.clear()

            val filtered = when (currentMode) {
                ScreenMode.DIARY, ScreenMode.PEOPLE -> {
                    searchEditText.hint = "Поиск заметок..."
                    fabAddNote.isVisible = true
                    allNotes.filter { !it.isArchived && !it.isDeleted }
                }
                ScreenMode.ARCHIVE -> {
                    searchEditText.hint = "Архив Заметок"
                    fabAddNote.isGone = true
                    allNotes.filter { it.isArchived && !it.isDeleted }
                }
                ScreenMode.TRASH -> {
                    searchEditText.hint = "Корзина Заметок"
                    fabAddNote.isGone = true
                    allNotes.filter { it.isDeleted }
                }
            }

            val sortedNotes = filtered.sortedWith(
                compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.id }
            )

            if (searchQuery.isNotEmpty()) {
                currentDisplayedNotes.addAll(sortedNotes.filter { it.text.lowercase().contains(searchQuery) || it.tags.lowercase().contains(searchQuery) })
            } else {
                currentDisplayedNotes.addAll(sortedNotes)
            }
            notesAdapter.notifyDataSetChanged()
        }
    }

    private fun setupHashtagAutocomplete() {
        val defaultTags = mutableSetOf("#друзья", "#работа", "#дневник", "#мысли")
        try {
            val dbTags = noteDao.getAllUsedTags()
            for (tagLine in dbTags) {
                val words = tagLine.split("\\s+".toRegex())
                for (word in words) { if (word.startsWith("#") && word.length > 1) defaultTags.add(word) }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, defaultTags.toList())
        inputHashtags.setAdapter(adapter)
        inputHashtags.threshold = 1
        inputHashtags.setTokenizer(object : MultiAutoCompleteTextView.Tokenizer {
            override fun findTokenStart(text: CharSequence, cursor: Int): Int {
                var i = cursor
                while (i > 0 && text[i - 1] != ' ') i--
                return i
            }
            override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
                var i = cursor
                while (i < text.length && text[i] != ' ') i++
                return i
            }
            override fun terminateToken(text: CharSequence): CharSequence {
                return if (text.isNotEmpty() && text[text.length - 1] == ' ') text else "$text "
            }
        })
    }

    // ИСПРАВЛЕНИЕ БАГА 2: Считываем и заполняем абсолютно все характеристики человека
    private fun showAddPersonDialog(personToEdit: PersonEntity?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_person, findViewById(android.R.id.content), false)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // Поиск View строго по ID из твоего XML
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etHeight = dialogView.findViewById<EditText>(R.id.etHeight)
        val etBirthday = dialogView.findViewById<EditText>(R.id.etBirthday)
        val etHobbies = dialogView.findViewById<EditText>(R.id.etHobbies)
        val etMusic = dialogView.findViewById<EditText>(R.id.etMusic)
        val etMovies = dialogView.findViewById<EditText>(R.id.etMovies)
        val etWishlist = dialogView.findViewById<EditText>(R.id.etWishlist)

        val btnSave = dialogView.findViewById<Button>(R.id.btnSavePerson)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPerson)

        // Если редактируем — заполняем поля старыми данными
        if (personToEdit != null) {
            etName.setText(personToEdit.name)
            etHeight.setText(personToEdit.height)
            etBirthday.setText(personToEdit.birthday)
            etHobbies.setText(personToEdit.hobbies)
            etMusic.setText(personToEdit.favoriteMusic)
            etMovies.setText(personToEdit.favoriteMovies)
            etWishlist.setText(personToEdit.wishlistNotes)
            btnSave.text = "Обновить"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotBlank()) {
                val p = PersonEntity(
                    id = personToEdit?.id ?: 0,
                    name = name,
                    height = etHeight.text.toString().trim(),
                    birthday = etBirthday.text.toString().trim(),
                    hobbies = etHobbies.text.toString().trim(),
                    favoriteMusic = etMusic.text.toString().trim(),
                    favoriteMovies = etMovies.text.toString().trim(),
                    wishlistNotes = etWishlist.text.toString().trim(),
                    isArchived = personToEdit?.isArchived ?: false,
                    isDeleted = personToEdit?.isDeleted ?: false,
                    isPinned = personToEdit?.isPinned ?: false
                )
                if (personToEdit != null) personDao.updatePerson(p) else personDao.insertPerson(p)
                dialog.dismiss()
                loadPeopleFromDatabase()
            } else {
                etName.error = "Введите имя"
            }
        }

        // ────────────── ВОТ СЮДА ВСТАВЛЯЕМ КОД ЗАКРУГЛЕНИЯ ──────────────
        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE) // Белый цвет фона окошка
            cornerRadius = 45f // Радиус закругления (такой же, как у остальных окон)
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)
        // ────────────────────────────────────────────────────────────────

        dialog.show()
    }

    // ИСПРАВЛЕНИЕ БАГА 2: Наполняем BottomSheet всеми деталями анкеты, а не только именем
    private fun showPersonDetailDialog(person: PersonEntity) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_view_note, findViewById(android.R.id.content), false)
        dialog.setContentView(view)

        val details = """
             Имя: ${person.name}
             Рост: ${if(person.height.isNotEmpty()) person.height + " см" else "не указан"}
             День рождения: ${person.birthday.ifEmpty { "не указан" }}
             Хобби: ${person.hobbies.ifEmpty { "нет" }}
             Музыка: ${person.favoriteMusic.ifEmpty { "нет" }}
             Фильмы: ${person.favoriteMovies.ifEmpty { "нет" }}
             Вишлист: ${person.wishlistNotes.ifEmpty { "пусто" }}
        """.trimIndent()

        view.findViewById<TextView>(R.id.tvDetailText).text = details
        view.findViewById<TextView>(R.id.tvDetailTags).text = "Полная анкета"
        view.findViewById<ImageView>(R.id.ivDetailImage).isGone = true
        view.findViewById<Button>(R.id.btnCloseDetail).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPersonOptionsDialog(person: PersonEntity) {
        val pinOptionText = if (person.isPinned) "Открепить анкету" else "Закрепить анкету"

        val options = if (person.isDeleted) {
            arrayOf("Восстановить", "Удалить навсегда")
        } else {
            arrayOf(pinOptionText, "Изменить", "В архив", "В корзину")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(person.name)
            .setItems(options) { _, which ->
                if (person.isDeleted) {
                    when (which) {
                        0 -> { // Восстановить из корзины
                            personDao.updatePerson(person.copy(isDeleted = false))
                            loadPeopleFromDatabase()
                        }
                        1 -> { // Удалить навсегда
                            personDao.deletePerson(person)
                            loadPeopleFromDatabase()
                        }
                    }
                } else {
                    when (which) {
                        0 -> { // Закрепить / Открепить
                            togglePersonPin(person)
                        }
                        1 -> { // Изменить (Редактировать)
                            showAddPersonDialog(person)
                        }
                        2 -> { // В архив
                            personDao.updatePerson(person.copy(isArchived = true))
                            loadPeopleFromDatabase()
                        }
                        3 -> { // В корзину
                            personDao.updatePerson(person.copy(isDeleted = true))
                            loadPeopleFromDatabase()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        // НАСТРОЙКА ЗАКРУГЛЕНИЯ ПРЯМО В КОДЕ:
        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 45f
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)

        dialog.show()

        // Красим кнопку отмены в черный
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
    }

    // Вспомогательный метод инверсии закрепа анкеты
    private fun togglePersonPin(person: PersonEntity) {
        personDao.updatePerson(person.copy(isPinned = !person.isPinned))
        loadPeopleFromDatabase()
    }

    private fun showActionMenuDialog(position: Int) {
        val targetNote = currentDisplayedNotes[position]
        val pinActionText = if (targetNote.isPinned) "Открепить заметку" else "Закрепить заметку"

        val options = when (currentMode) {
            ScreenMode.DIARY -> arrayOf(pinActionText, "Изменить", "В архив", "Удалить")
            ScreenMode.ARCHIVE -> arrayOf("Вернуть", "Удалить")
            ScreenMode.TRASH -> arrayOf("Восстановить", "Навсегда")
            else -> arrayOf()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Действие")
            .setItems(options) { _, which ->
                when (currentMode) {
                    ScreenMode.DIARY -> when (which) {
                        0 -> {
                            noteDao.updateNote(targetNote.copy(isPinned = !targetNote.isPinned))
                            loadNotesFromDatabase()
                        }
                        1 -> prepareEditNote(position)
                        2 -> { noteDao.updateNote(targetNote.copy(isArchived = true)); loadNotesFromDatabase() }
                        3 -> { noteDao.updateNote(targetNote.copy(isDeleted = true)); loadNotesFromDatabase() }
                    }
                    ScreenMode.ARCHIVE -> if (which == 0) {
                        noteDao.updateNote(targetNote.copy(isArchived = false)); loadNotesFromDatabase()
                    } else {
                        noteDao.updateNote(targetNote.copy(isDeleted = true)); loadNotesFromDatabase()
                    }
                    ScreenMode.TRASH -> if (which == 0) {
                        noteDao.updateNote(targetNote.copy(isDeleted = false)); loadNotesFromDatabase()
                    } else {
                        noteDao.deleteNote(targetNote); loadNotesFromDatabase()
                    }
                    else -> {}
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        // НАСТРОЙКА ЗАКРУГЛЕНИЯ ПРЯМО В КОДЕ:
        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 45f
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)

        dialog.show()

        // Красим кнопку отмены в черный
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
    }


    private fun prepareEditNote(position: Int) {
        val note = currentDisplayedNotes[position]
        editingPosition = position
        inputNoteText.setText(note.text)
        inputHashtags.setText(note.tags)
        btnSaveNote.text = "Обновить"
        addNoteContainer.isVisible = true
    }

    private fun     showNoteDetailDialog(note: NoteEntity) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_view_note, findViewById(android.R.id.content), false)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.tvDetailText).text = note.text
        view.findViewById<TextView>(R.id.tvDetailTags).text = note.tags
        val iv = view.findViewById<ImageView>(R.id.ivDetailImage)
        if (!note.photoUriString.isNullOrEmpty()) {
            iv.isVisible = true
            iv.setImageURI(note.photoUriString.toUri())
            iv.setOnClickListener { showFullscreenImageDialog(note.photoUriString) }
        } else { iv.isGone = true }
        view.findViewById<Button>(R.id.btnCloseDetail).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showFullscreenImageDialog(imageUriString: String) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val view = layoutInflater.inflate(R.layout.dialog_fullscreen_image, findViewById(android.R.id.content), false)
        dialog.setView(view)
        view.findViewById<ImageView>(R.id.ivFullscreenImage).setImageURI(imageUriString.toUri())
        view.findViewById<ImageButton>(R.id.btnCloseFullscreen).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

class NotesAdapter(
    private val notes: List<NoteEntity>,
    private val onItemClick: (NoteEntity) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        return NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))
    }
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.tvText.text = if (note.isPinned) "📌 ${note.text}" else note.text
        holder.tvTags.text = note.tags

        if (!note.photoUriString.isNullOrEmpty()) {
            holder.ivImage.isVisible = true
            holder.ivImage.setImageURI(note.photoUriString.toUri())
        } else { holder.ivImage.isGone = true }
        holder.itemView.setOnClickListener { onItemClick(note) }
    }
    override fun getItemCount(): Int = notes.size
    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvText: TextView = itemView.findViewById(R.id.tvNoteText)
        val tvTags: TextView = itemView.findViewById(R.id.tvNoteTags)
        val ivImage: ImageView = itemView.findViewById(R.id.ivNoteImage)
    }
}