package com.m4sak1.ryousyoku.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m4sak1.ryousyoku.model.Menu

val BackgroundColor = Color(0xFFFFF8F0)
val HeaderColor = Color(0xFFFF8C42)
val TextColor = Color(0xFF4A4A4A)
val CardBackground = Color.White
val BorderColor = Color(0xFFFFE0C2)

@Composable
fun MainScreen(viewModel: MenuViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMenuForDialog by remember { mutableStateOf<Menu?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header()

            when (val state = uiState) {
                is MenuState.Loading -> {
                    // Handled by overlay
                }
                is MenuState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            state.activeMenu?.let { activeMenu ->
                                LatestMenuBlock(
                                    menu = activeMenu,
                                    onClick = { selectedMenuForDialog = activeMenu }
                                )
                            }
                        }

                        item {
                            ArchiveBlock(
                                menus = state.allMenus,
                                onClick = { selectedMenuForDialog = it }
                            )
                        }

                        item {
                            Footer()
                        }
                    }
                }
                is MenuState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${state.message}", color = Color.Red)
                    }
                }
            }
        }

        if (uiState is MenuState.Loading) {
            LoadingOverlay()
        }
    }

    selectedMenuForDialog?.let { menu ->
        ImageModal(
            menu = menu,
            onDismiss = { selectedMenuForDialog = null }
        )
    }
}

@Composable
fun Header() {
    Surface(
        color = HeaderColor,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "寮食献立",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp)
        )
    }
}

@Composable
fun BlockContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                color = HeaderColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            color = BorderColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(bottom = 10.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun LatestMenuBlock(menu: Menu, onClick: () -> Unit) {
    BlockContainer(title = "今週の献立") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(10.dp)
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(menu.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "今週の献立",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = menu.period,
            color = Color(0xFF888888),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ArchiveBlock(menus: List<Menu>, onClick: (Menu) -> Unit) {
    var displayCount by remember { mutableIntStateOf(20) }

    BlockContainer(title = "過去の献立一覧") {
        Column {
            menus.take(displayCount).forEach { menu ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onClick(menu) }
                        )
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val y = size.height - strokeWidth / 2
                            drawLine(
                                color = Color(0xFFEEEEEE),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📄 ${menu.period}",
                        color = TextColor,
                        fontSize = 14.sp
                    )
                }
            }

            if (displayCount < menus.size) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { displayCount += 20 },
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 200.dp)
                ) {
                    Text("もっと表示する", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
    ) {
        Text(
            text = "© 2026 m4sak1",
            color = Color(0xFF999999),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FooterLink("利用規約", "https://ryousyoku.m4sak1.me/terms.html")
            Text(" | ", color = Color(0xFFDDDDDD), fontSize = 12.sp)
            FooterLink("しくみ", "https://ryousyoku.m4sak1.me/about.html")
        }
        FooterLink("ソース(リンクは変更される可能性があります)", "https://www.kagawa-nct.ac.jp/dormitoryE/kondate.pdf")
    }
}

@Composable
fun FooterLink(text: String, url: String) {
    val context = LocalContext.current
    Text(
        text = text,
        color = HeaderColor,
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    )
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)), // 0.6 alpha black
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = HeaderColor,
            trackColor = Color(0x4DFFFFFF), // 0.3 alpha white
            strokeWidth = 5.dp,
            modifier = Modifier.size(50.dp)
        )
    }
}

@Composable
fun ImageModal(menu: Menu, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9000000)) // 0.85 alpha black
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                color = Color.White,
                fontSize = 40.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 30.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(menu.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Expanded Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color(0x33FFFFFF), // 0.2 alpha white
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF)),
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(menu.pdfUrl)))
                    }
                ) {
                    Text(
                        text = "📥 元のPDFを開く・ダウンロード",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
