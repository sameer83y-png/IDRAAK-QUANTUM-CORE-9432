
package com.idraak.quantum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idraak.quantum.engine.LocalMemoryStore
import com.idraak.quantum.engine.MemoryBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryDashboardScreen(
    onBack: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current

    var backupPassphrase by remember { mutableStateOf("") }
    var backupMessage by remember { mutableStateOf("") }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val encryptedBackup = MemoryBackupManager.createBackup(
                context,
                backupPassphrase.toCharArray()
            )
            val saved = try {
                if (encryptedBackup.isNullOrBlank()) {
                    false
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(encryptedBackup.toByteArray(Charsets.UTF_8))
                    } != null
                }
            } catch (_: Exception) {
                false
            }
            backupPassphrase = ""
            backupMessage = if (saved) {
                "خفیہ یادداشت کا بیک اپ محفوظ ہو گیا۔ پاس فریز الگ محفوظ رکھیں۔"
            } else {
                "بیک اپ محفوظ نہیں ہو سکا۔ کم از کم 10 حروف کا پاس فریز استعمال کریں۔"
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val restored = try {
                val backupText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                if (backupText.isNullOrBlank()) {
                    false
                } else {
                    MemoryBackupManager.restoreBackup(
                        context,
                        backupText,
                        backupPassphrase.toCharArray()
                    )
                }
            } catch (_: Exception) {
                false
            }
            backupPassphrase = ""
            backupMessage = if (restored) {
                refreshKey++
                "یادداشت بحال ہو گئی۔ موجودہ یادداشت بیک اپ سے بدل دی گئی ہے۔"
            } else {
                "بیک اپ بحال نہیں ہوا۔ پاس فریز یا فائل درست نہیں، یا فائل خراب ہے۔"
            }
        }
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    var refreshKey by remember {
        mutableStateOf(0)
    }

    var memoryPendingDeletion by remember {
        mutableStateOf<LocalMemoryStore.MemoryRecord?>(null)
    }

    var memoryPendingEdit by remember {
        mutableStateOf<LocalMemoryStore.MemoryRecord?>(null)
    }

    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editMessage by remember { mutableStateOf("") }

    var selectedEntryType by remember {
        mutableStateOf("درستگی")
    }

    var entryTitle by remember {
        mutableStateOf("")
    }

    var entryContent by remember {
        mutableStateOf("")
    }

    var saveMessage by remember {
        mutableStateOf("")
    }

    val allMemories = remember(refreshKey) {
        LocalMemoryStore.getAll(
            context = context,
            limit = 500
        )
    }

    val filteredMemories = remember(
        allMemories,
        searchQuery
    ) {
        if (searchQuery.isBlank()) {
            allMemories
        } else {
            val query = searchQuery.trim().lowercase(Locale.ROOT)

            allMemories.filter { memory ->
                memory.title.lowercase(Locale.ROOT).contains(query) ||
                memory.content.lowercase(Locale.ROOT).contains(query) ||
                memory.category.lowercase(Locale.ROOT).contains(query) ||
                memory.status.lowercase(Locale.ROOT).contains(query)
            }
        }
    }

    val pendingCount = allMemories.count {
        it.status == LocalMemoryStore.Status.PENDING.name
    }

    val verifiedCount = allMemories.count {
        it.status == LocalMemoryStore.Status.VERIFIED.name
    }

    val failedCount = allMemories.count {
        it.status == LocalMemoryStore.Status.FAILED.name
    }

    val rejectedCount = allMemories.count {
        it.status == LocalMemoryStore.Status.REJECTED.name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← ترتیبات پر واپس جائیں")
        }

        Text(
            text = "ادراک کی یادداشت",
            style = MaterialTheme.typography.headlineMedium,
            color = accentColor
        )

        Text(
            text = "مقامی یادداشت",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoryCountCard(
                title = "کل",
                count = allMemories.size,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "زیرِ تصدیق",
                count = pendingCount,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoryCountCard(
                title = "تصدیق شدہ",
                count = verifiedCount,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "ناکام",
                count = failedCount,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "مسترد",
                count = rejectedCount,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14243D))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "یادداشت کا محفوظ بیک اپ",
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "اپ ڈیٹ یا فون بدلنے سے پہلے خفیہ بیک اپ بنائیں۔ بحالی موجودہ یادداشت کو بیک اپ سے بدل دے گی۔",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("بیک اپ پاس فریز (کم از کم 10 حروف)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (backupPassphrase.length < 10) {
                                backupMessage = "کم از کم 10 حروف کا پاس فریز درج کریں۔"
                            } else {
                                backupMessage = ""
                                exportBackupLauncher.launch("idraak-memory-backup.json")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("بیک اپ بنائیں")
                    }
                    OutlinedButton(
                        onClick = {
                            if (backupPassphrase.length < 10) {
                                backupMessage = "بحالی کے لیے وہی پاس فریز درج کریں۔"
                            } else {
                                showRestoreConfirmation = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("بیک اپ بحال کریں")
                    }
                }
                if (backupMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(backupMessage, color = accentColor, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "پاس فریز بھولنے پر بیک اپ نہیں کھلے گا۔ اسے کسی محفوظ جگہ پر رکھیں۔",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF14243D)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "یادداشت میں معلومات شامل کریں",
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "منتخب کریں کہ ادراک کو کیا سکھانا ہے:",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedEntryType == "درستگی") {
                        Button(
                            onClick = {
                                selectedEntryType = "درستگی"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("درستگی")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedEntryType = "درستگی"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("درستگی")
                        }
                    }

                    if (selectedEntryType == "سیکھنا") {
                        Button(
                            onClick = {
                                selectedEntryType = "سیکھنا"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("سیکھنا")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedEntryType = "سیکھنا"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("سیکھنا")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = entryTitle,
                    onValueChange = {
                        entryTitle = it
                        saveMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("موضوع / عنوان")
                    },
                    placeholder = {
                        Text("مثال: پسندیدہ زبان")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = entryContent,
                    onValueChange = {
                        entryContent = it
                        saveMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = {
                        Text(
                            if (selectedEntryType == "درستگی") {
                                "درست معلومات"
                            } else {
                                "سبق / سیکھنے کی بات"
                            }
                        )
                    },
                    placeholder = {
                        Text("درست معلومات یہاں لکھیں")
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val title = entryTitle.trim()
                        val content = entryContent.trim()

                        if (title.isBlank() || content.isBlank()) {
                            saveMessage =
                                "براہِ کرم عنوان اور معلومات دونوں درج کریں۔"
                        } else {
                            val saved = if (
                                selectedEntryType == "درستگی"
                            ) {
                                LocalMemoryStore.saveCorrection(
                                    context = context,
                                    topic = title,
                                    correctedInformation = content
                                )
                            } else {
                                LocalMemoryStore.proposeLearning(
                                    context = context,
                                    topic = title,
                                    lesson = content
                                )
                            }

                            if (saved) {
                                entryTitle = ""
                                entryContent = ""
                                saveMessage =
                                    "کامیابی سے محفوظ ہو گیا۔ نیچے اس کی تصدیق کریں۔"
                                refreshKey++
                            } else {
                                saveMessage =
                                    "محفوظ نہیں ہو سکا۔ معلومات دیکھ کر دوبارہ کوشش کریں۔"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("زیرِ تصدیق محفوظ کریں")
                }

                if (saveMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = saveMessage,
                        color = accentColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "نئی معلومات آپ کی تصدیق تک زیرِ تصدیق رہیں گی۔ غیر مصدقہ معلومات کو درست نہیں مانا جائے گا۔",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("مقامی یادداشت تلاش کریں")
            },
            placeholder = {
                Text("عنوان، معلومات، قسم یا حالت")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "محفوظ اندراجات (${filteredMemories.size})",
                style = MaterialTheme.typography.titleSmall
            )

            TextButton(
                onClick = {
                    refreshKey++
                }
            ) {
                Text("تازہ کریں")
            }
        }

        if (filteredMemories.isEmpty()) {
            Text(
                text = if (allMemories.isEmpty()) {
                    "ابھی کوئی مقامی یادداشت محفوظ نہیں ہے۔"
                } else {
                    "اس تلاش سے کوئی یادداشت نہیں ملی۔"
                },
                modifier = Modifier.padding(12.dp),
                color = Color.Gray
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredMemories,
                    key = { it.id }
                ) { memory ->

                    MemoryRecordCard(
                        memory = memory,
                        accentColor = accentColor,
                        onVerify = {
                            val updated = LocalMemoryStore.updateStatus(
                                context = context,
                                memoryId = memory.id,
                                newStatus = LocalMemoryStore.Status.VERIFIED
                            )

                            if (updated) refreshKey++
                        },
                        onReject = {
                            val updated = LocalMemoryStore.updateStatus(
                                context = context,
                                memoryId = memory.id,
                                newStatus = LocalMemoryStore.Status.REJECTED
                            )

                            if (updated) refreshKey++
                        },
                        onEdit = {
                            memoryPendingEdit = memory
                            editTitle = memory.title
                            editContent = memory.content
                            editMessage = ""
                        },
                        onDelete = {
                            memoryPendingDeletion = memory
                        }
                    )
                }
            }
        }
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("یادداشت بحال کریں؟") },
            text = {
                Text("بحالی موجودہ مقامی یادداشت کو منتخب بیک اپ سے بدل دے گی۔ پہلے موجودہ یادداشت کا نیا بیک اپ بنا لیں۔ کیا آپ جاری رکھنا چاہتے ہیں؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirmation = false
                    importBackupLauncher.launch(arrayOf("application/json", "text/*"))
                }) {
                    Text("فائل منتخب کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text("منسوخ کریں")
                }
            }
        )
    }

    memoryPendingEdit?.let { memory ->
        AlertDialog(
            onDismissRequest = {
                memoryPendingEdit = null
            },
            title = {
                Text("یادداشت میں ترمیم")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("موضوع / عنوان") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        label = { Text("درست معلومات") }
                    )
                    if (editMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(editMessage, color = Color(0xFFFFC857))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "ترمیم کے بعد یادداشت دوبارہ زیرِ تصدیق ہو جائے گی۔ ادراک کے اعتماد سے پہلے اس کی دوبارہ تصدیق کریں۔",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = LocalMemoryStore.update(
                            context = context,
                            memoryId = memory.id,
                            title = editTitle,
                            content = editContent
                        )
                        if (updated) {
                            memoryPendingEdit = null
                            refreshKey++
                        } else {
                            editMessage = "محفوظ نہیں ہو سکا۔ عبارت دیکھ کر دوبارہ کوشش کریں۔"
                        }
                    }
                ) {
                    Text("زیرِ تصدیق محفوظ کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryPendingEdit = null }) {
                    Text("منسوخ کریں")
                }
            }
        )
    }

    memoryPendingDeletion?.let { memory ->
        AlertDialog(
            onDismissRequest = {
                memoryPendingDeletion = null
            },
            title = {
                Text("یہ یادداشت حذف کریں؟")
            },
            text = {
                Text(
                    "کیا آپ واقعی اسے ہمیشہ کے لیے حذف کرنا چاہتے ہیں: " +
                    "\"${memory.title}\"؟ یہ عمل واپس نہیں ہو سکے گا۔"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = LocalMemoryStore.delete(
                            context = context,
                            memoryId = memory.id
                        )

                        memoryPendingDeletion = null

                        if (deleted) {
                            refreshKey++
                        }
                    }
                ) {
                    Text(
                        text = "حذف کریں",
                        color = Color(0xFFFF7777)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        memoryPendingDeletion = null
                    }
                ) {
                    Text("منسوخ کریں")
                }
            }
        )
    }
}

@Composable
private fun MemoryCountCard(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF14243D)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                color = accentColor,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = count.toString(),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun MemoryRecordCard(
    memory: LocalMemoryStore.MemoryRecord,
    accentColor: Color,
    onVerify: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusLabel = when (memory.status) {
        "VERIFIED" -> "تصدیق شدہ"
        "PENDING" -> "زیرِ تصدیق"
        "FAILED" -> "ناکام"
        "REJECTED" -> "مسترد"
        "SUCCESS" -> "کامیاب"
        else -> "نامعلوم"
    }
    val categoryLabel = when (memory.category) {
        "USER_PREFERENCE" -> "ذاتی پسند"
        "GENERAL_FACT" -> "عام معلومات"
        "COMMAND" -> "حکم"
        "EXPERIENCE" -> "تجربہ"
        "CORRECTION" -> "درستگی"
        "LEARNING" -> "سیکھنا"
        else -> "دیگر"
    }
    val statusColor = when (memory.status) {
        "VERIFIED" -> Color(0xFF43CFA0)
        "PENDING" -> Color(0xFFFFC857)
        "FAILED", "REJECTED" -> Color(0xFFFF7777)
        "SUCCESS" -> accentColor
        else -> Color.LightGray
    }

    val dateText = remember(memory.createdAt) {
        SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale("ur", "PK")
        ).format(Date(memory.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF14243D)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = if (memory.title.startsWith("command_alias:")) {
                    "حکم کی ترکیب: " + memory.title.removePrefix("command_alias:")
                } else {
                    memory.title
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "قسم: $categoryLabel",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "حالت: $statusLabel",
                color = statusColor,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = memory.content,
                color = Color(0xFFD5E1F2),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dateText,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (memory.status == "PENDING") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onVerify) {
                        Text("تصدیق کریں")
                    }

                    Button(onClick = onReject) {
                        Text("مسترد کریں")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onEdit) {
                    Text("ترمیم کریں")
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = "یادداشت حذف کریں",
                        color = Color(0xFFFF7777)
                    )
                }
            }
        }
    }
}
