package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Machine
import com.example.ui.theme.StatusDueSoon
import com.example.ui.theme.StatusOk
import com.example.ui.theme.StatusOverdue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun MachineCard(
    machine: Machine,
    onClick: () -> Unit,
    onAddMaintenanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dueInfo = calculateDueStatus(machine.nextDueDate)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("machine_card_${machine.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Category Icon, Name, Group Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getCategoryColor(machine.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(machine.category),
                        contentDescription = machine.category,
                        tint = getCategoryColor(machine.category),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = machine.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = machine.groupTag.ifBlank { "Allgemein" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (machine.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = machine.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Due Status Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = dueInfo.backgroundColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = dueInfo.icon,
                        contentDescription = null,
                        tint = dueInfo.contentColor,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dueInfo.statusText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = dueInfo.contentColor
                        )
                        if (machine.nextDueMaintenance.isNotBlank()) {
                            Text(
                                text = machine.nextDueMaintenance,
                                style = MaterialTheme.typography.bodySmall,
                                color = dueInfo.contentColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (machine.tasks.size > 1) {
                            Text(
                                text = "${machine.tasks.size} anstehende Aufgaben",
                                style = MaterialTheme.typography.labelSmall,
                                color = dueInfo.contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Row: Last maintenance date & Quick Add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (machine.lastMaintenanceDate.isNotBlank()) {
                        "Zuletzt: ${machine.lastMaintenanceDate}"
                    } else {
                        "Noch keine Wartung"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onAddMaintenanceClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("add_maintenance_shortcut_${machine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Wartung", fontSize = 12.sp)
                }
            }
        }
    }
}

data class DueInfo(
    val statusText: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val contentColor: Color
)

fun calculateDueStatus(nextDueDateStr: String?): DueInfo {
    if (nextDueDateStr.isNullOrBlank()) {
        return DueInfo(
            statusText = "Keine Wartung terminiert",
            icon = Icons.Default.CalendarMonth,
            backgroundColor = Color(0xFFF1F5F9),
            contentColor = Color(0xFF64748B)
        )
    }

    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dueDate = LocalDate.parse(nextDueDateStr.trim(), formatter)
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, dueDate)

        when {
            daysUntil < 0 -> {
                DueInfo(
                    statusText = "Überfällig seit ${-daysUntil} Tag(en) ($nextDueDateStr)",
                    icon = Icons.Default.Warning,
                    backgroundColor = StatusOverdue.copy(alpha = 0.12f),
                    contentColor = StatusOverdue
                )
            }
            daysUntil == 0L -> {
                DueInfo(
                    statusText = "Heute fällig! ($nextDueDateStr)",
                    icon = Icons.Default.NotificationImportant,
                    backgroundColor = StatusOverdue.copy(alpha = 0.15f),
                    contentColor = StatusOverdue
                )
            }
            daysUntil <= 30 -> {
                DueInfo(
                    statusText = "Fällig in $daysUntil Tagen ($nextDueDateStr)",
                    icon = Icons.Default.NotificationImportant,
                    backgroundColor = StatusDueSoon.copy(alpha = 0.15f),
                    contentColor = StatusDueSoon
                )
            }
            else -> {
                DueInfo(
                    statusText = "Nächste Wartung: $nextDueDateStr (in $daysUntil Tagen)",
                    icon = Icons.Default.CalendarMonth,
                    backgroundColor = Color(0xFFE0F2FE),
                    contentColor = Color(0xFF0369A1)
                )
            }
        }
    } catch (e: Exception) {
        DueInfo(
            statusText = "Terminiert: $nextDueDateStr",
            icon = Icons.Default.CalendarMonth,
            backgroundColor = Color(0xFFF1F5F9),
            contentColor = Color(0xFF64748B)
        )
    }
}

fun getCategoryIcon(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("auto") || lower.contains("kfz") || lower.contains("wagen") -> Icons.Default.DirectionsCar
        lower.contains("rasen") || lower.contains("garten") || lower.contains("pflanze") -> Icons.Default.Yard
        lower.contains("rauch") || lower.contains("sicher") || lower.contains("alarm") -> Icons.Default.Security
        lower.contains("haus") || lower.contains("küche") || lower.contains("bad") -> Icons.Default.Home
        lower.contains("werkzeug") || lower.contains("bohrer") || lower.contains("säge") -> Icons.Default.Handyman
        else -> Icons.Default.Build
    }
}

fun getCategoryColor(category: String): Color {
    val lower = category.lowercase()
    return when {
        lower.contains("auto") || lower.contains("kfz") -> Color(0xFF0284C7)
        lower.contains("rasen") || lower.contains("garten") -> Color(0xFF16A34A)
        lower.contains("rauch") || lower.contains("sicher") -> Color(0xFFDC2626)
        lower.contains("haus") -> Color(0xFF7C3AED)
        lower.contains("werkzeug") -> Color(0xFFD97706)
        else -> Color(0xFF0D9488)
    }
}
