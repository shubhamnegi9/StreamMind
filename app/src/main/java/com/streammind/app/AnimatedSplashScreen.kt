package com.streammind.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Shown for a short beat after the system splash screen hands off. The Android
 * SplashScreen API only supports an icon and a solid background — no arbitrary
 * text, no custom animation curves — so a real branded splash with the app name
 * and a proper entrance animation has to live in-app, in Compose, like this.
 */
@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "iconAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 250),
        label = "textAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0C0814)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "StreamMind",
                color = Color(0xFFF7F1FF),
                fontSize = 24.sp,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.alpha(textAlpha)
            )
        }
    }
}

private const val SPLASH_DURATION_MS = 1100L
