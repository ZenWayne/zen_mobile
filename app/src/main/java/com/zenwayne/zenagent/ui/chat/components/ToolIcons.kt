package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.zenwayne.zenagent.data.ToolIcon

fun ToolIcon.vector(): ImageVector = when (this) {
    ToolIcon.Search -> Icons.Filled.Search
    ToolIcon.MapPin -> Icons.Filled.Place
    ToolIcon.Hotel -> Icons.Filled.Hotel
    ToolIcon.Flight -> Icons.Filled.FlightTakeoff
    ToolIcon.Weather -> Icons.Filled.Cloud
    ToolIcon.Code -> Icons.Filled.Code
    ToolIcon.Warning -> Icons.Filled.Warning
    ToolIcon.Generic -> Icons.Filled.Build
}
