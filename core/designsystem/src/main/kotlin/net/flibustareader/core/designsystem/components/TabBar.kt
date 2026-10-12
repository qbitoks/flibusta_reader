package net.flibustareader.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType

data class TabSpec(val label: String, val icon: ImageVector)

/**
 * Floating glass tab bar (guideline § 3.1): a pill with the tabs and a separate round
 * search button, 21 dp from the bottom and side edges. The selected tab sits on a light
 * capsule; it is not tinted with an accent colour.
 */
@Composable
fun FloatingTabBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    searchDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = Flibusta.colors
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 21.dp, end = 21.dp, bottom = 12.dp)
            .height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .glass(CircleShape, elevation = 6.dp)
                .padding(4.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                val isSelected = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .then(if (isSelected) Modifier.background(c.label.copy(alpha = if (c.isDark) 0.14f else 0.08f)) else Modifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = c.label, modifier = Modifier.size(26.dp))
                    Text(tab.label, style = FlibustaType.tab, color = c.label, maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .size(58.dp)
                .glass(CircleShape, elevation = 6.dp)
                .clickable(role = Role.Button, onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, searchDescription, tint = c.label, modifier = Modifier.size(28.dp))
        }
    }
}
