package com.joegec.joycon2android.connection.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joegec.joycon2android.ui.theme.Dimens
import com.joegec.joycon2android.ui.theme.TextDim

@Composable
fun TurnOffButton(onTurnOff: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onTurnOff, modifier = modifier.size(Dimens.minTouchTarget)) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = stringResource(R.string.turn_off_controllers),
            tint = TextDim,
            modifier = Modifier.size(Dimens.iconSizeSmall),
        )
    }
}
