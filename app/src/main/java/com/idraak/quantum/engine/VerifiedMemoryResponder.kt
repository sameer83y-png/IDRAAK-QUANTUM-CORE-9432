
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var searchQuery by remember {
        mutableStateOf("")
    }

    var refreshKey by remember {
        mutableStateOf(0)
    }

    var memoryPendingDeletion by remember {
        mutableStateOf<LocalMemoryStore.MemoryRecord?>(null)
    }

    var selectedEntryType by remember {
        mutableStateOf("CORRECTION")
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
            Text("← BACK TO SETTINGS")
        }

        Text(
            text = "IDRAAK MEMORY",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF087DFF)
        )

        Text(
            text = "LOCAL MEMORY DASHBOARD",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoryCountCard(
                title = "TOTAL",
                count = allMemories.size,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "PENDING",
                count = pendingCount,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoryCountCard(
                title = "VERIFIED",
                count = verifiedCount,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "FAILED",
                count = failedCount,
                modifier = Modifier.weight(1f)
            )

            MemoryCountCard(
                title = "REJECTED",
                count = rejectedCount,
                modifier = Modifier.weight(1f)
            )
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
                    text = "ADD MEMORY KNOWLEDGE",
                    color = Color(0xFF65C8FF),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose what you want Idraak to learn:",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedEntryType == "CORRECTION") {
                        Button(
                            onClick = {
                                selectedEntryType = "CORRECTION"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CORRECTION")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedEntryType = "CORRECTION"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CORRECTION")
                        }
                    }

                    if (selectedEntryType == "LEARNING") {
                        Button(
                            onClick = {
                                selectedEntryType = "LEARNING"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("LEARNING")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedEntryType = "LEARNING"
                                saveMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("LEARNING")
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
                        Text("Topic / Title")
                    },
                    placeholder = {
                        Text("Example: Preferred language")
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
                            if (selectedEntryType == "CORRECTION") {
                                "Correct information"
                            } else {
                                "Learning / Lesson"
                            }
                        )
                    },
                    placeholder = {
                        Text("Write the correct information here")
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val title = entryTitle.trim()
                        val content = entryContent.trim()

                        if (title.isBlank() || content.isBlank()) {
                            saveMessage =
                                "Please enter both title and information."
                        } else {
                            val saved = if (
                                selectedEntryType == "CORRECTION"
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
                                    "Saved successfully. Please verify it below."
                                refreshKey++
                            } else {
                                saveMessage =
                                    "Could not save. Check the information and try again."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE AS PENDING")
                }

                if (saveMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = saveMessage,
                        color = Color(0xFF65C8FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "New entries stay PENDING until you manually verify them. Unverified information is not trusted knowledge.",
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
                Text("Search local memories")
            },
            placeholder = {
                Text("Title, content, category or status")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SAVED RECORDS (${filteredMemories.size})",
                style = MaterialTheme.typography.titleSmall
            )

            TextButton(
                onClick = {
                    refreshKey++
                }
            ) {
                Text("REFRESH")
            }
        }

        if (filteredMemories.isEmpty()) {
            Text(
                text = if (allMemories.isEmpty()) {
                    "Abhi koi local memory saved nahi hai."
                } else {
                    "Is search se koi memory nahi mili."
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
                        onDelete = {
                            memoryPendingDeletion = memory
                        }
                    )
                }
            }
        }
    }

    memoryPendingDeletion?.let { memory ->
        AlertDialog(
            onDismissRequest = {
                memoryPendingDeletion = null
            },
            title = {
                Text("Delete this memory?")
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete " +
                    "\"${memory.title}\"? This action cannot be undone."
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
                        text = "DELETE",
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
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun MemoryCountCard(
    title: String,
    count: Int,
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
                color = Color(0xFF65C8FF),
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
    onVerify: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (memory.status) {
        "VERIFIED" -> Color(0xFF43CFA0)
        "PENDING" -> Color(0xFFFFC857)
        "FAILED" -> Color(0xFFFF7777)
        "REJECTED" -> Color(0xFFFF7777)
        "SUCCESS" -> Color(0xFF65C8FF)
        else -> Color.LightGray
    }

    val dateText = remember(memory.createdAt) {
        SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.getDefault()
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
                text = memory.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "CATEGORY: ${memory.category}",
                color = Color(0xFF65C8FF),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "STATUS: ${memory.status}",
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
                        Text("VERIFY")
                    }

                    Button(onClick = onReject) {
                        Text("REJECT")
                    }
                }
            }

            TextButton(onClick = onDelete) {
                Text(
                    text = "DELETE MEMORY",
                    color = Color(0xFFFF7777)
                )
            }
        }
    }
}
