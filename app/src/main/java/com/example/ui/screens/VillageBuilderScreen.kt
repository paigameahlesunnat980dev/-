package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.VillageLocation
import com.example.model.VillageProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VillageBuilderScreen(
    villages: List<VillageProject>,
    activeVillageId: String,
    activeLocations: List<VillageLocation>,
    onSelectVillage: (String) -> Unit,
    onCreateVillage: (String, String) -> Unit,
    onRenameVillage: (String, String, String) -> Unit,
    onConnectLocations: (VillageLocation, VillageLocation) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenExplore: () -> Unit,
    onBack: () -> Unit
) {
    var newVillageName by remember { mutableStateOf("") }
    var newVillageDesc by remember { mutableStateOf("") }
    var editingVillage by remember { mutableStateOf<VillageProject?>(null) }
    var firstConnectLoc by remember { mutableStateOf<VillageLocation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("builder_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "CREATE MY VILLAGE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AmberGold
                )
                Text(
                    text = "Save, reopen & connect Murgi Tola village projects",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Create New Village Project
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CREATE NEW VILLAGE PROJECT",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldVibrant
                        )
                        OutlinedTextField(
                            value = newVillageName,
                            onValueChange = { newVillageName = it },
                            label = { Text("Village Name (e.g. Murgi Tola East)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_village_name_input")
                        )
                        OutlinedTextField(
                            value = newVillageDesc,
                            onValueChange = { newVillageDesc = it },
                            label = { Text("Description (optional)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (newVillageName.isNotBlank()) {
                                    onCreateVillage(newVillageName, newVillageDesc)
                                    newVillageName = ""
                                    newVillageDesc = ""
                                }
                            },
                            enabled = newVillageName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_create_village_submit")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("CREATE & OPEN PROJECT", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Connect Scanned Locations in Active Project
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CONNECT PANORAMIC LOCATIONS (${activeLocations.size} SPOTS)",
                            style = MaterialTheme.typography.labelLarge,
                            color = SkyBlueAccent
                        )
                        Text(
                            text = if (firstConnectLoc == null) {
                                "Tap any two scanned locations below to build a walkable 3D road & 360° portal link between them."
                            } else {
                                "Selected '${firstConnectLoc!!.name}' — now tap a second location to link!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        if (activeLocations.size < 2) {
                            OutlinedButton(
                                onClick = onOpenScanner,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan Another 360° Spot to Connect Roads")
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                activeLocations.forEach { loc ->
                                    val isFirst = firstConnectLoc?.id == loc.id
                                    Surface(
                                        color = if (isFirst) SkyBlueAccent.copy(alpha = 0.2f) else ObsidianBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                1.dp,
                                                if (isFirst) SkyBlueAccent else ObsidianBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                if (firstConnectLoc == null) {
                                                    firstConnectLoc = loc
                                                } else if (firstConnectLoc!!.id == loc.id) {
                                                    firstConnectLoc = null
                                                } else {
                                                    onConnectLocations(firstConnectLoc!!, loc)
                                                    firstConnectLoc = null
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    Icons.Filled.Link,
                                                    contentDescription = null,
                                                    tint = if (isFirst) SkyBlueAccent else AmberGold,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                            }
                                            Text(
                                                text = "${loc.connectedIds.size} links",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onOpenExplore,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldVibrant, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("WALK IN ACTIVE VILLAGE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Saved Village Projects List (Reopen / Rename)
            item {
                Text(
                    text = "SAVED VILLAGE PROJECTS (${villages.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(villages, key = { it.id }) { project ->
                val isActive = project.id == activeVillageId
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.5.dp,
                            if (isActive) AmberGold else ObsidianBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectVillage(project.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isActive) AmberGold else TextPrimary
                                )
                                if (isActive) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Active project",
                                        tint = EmeraldVibrant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (project.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        IconButton(onClick = { editingVillage = project }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit project", tint = TextSecondary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editingVillage?.let { proj ->
        var editName by remember(proj.id) { mutableStateOf(proj.name) }
        var editDesc by remember(proj.id) { mutableStateOf(proj.description) }
        AlertDialog(
            onDismissRequest = { editingVillage = null },
            title = { Text("Edit Village Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Village Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameVillage(proj.id, editName, editDesc)
                        editingVillage = null
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingVillage = null }) { Text("Cancel") }
            }
        )
    }
}
