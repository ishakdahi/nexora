@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nexora.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.app.data.LogEntry
import com.nexora.app.data.Scenario
import com.nexora.app.data.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalLang = compositionLocalOf { "fr" }

@Composable
fun t(fr: String, ar: String): String = tr(LocalLang.current, fr, ar)

private val OkGreen = Color(0xFF22C55E)
private val TRIGGERS = listOf("MANUAL", "TIME", "POWER_CONNECTED", "POWER_DISCONNECTED", "BATTERY_LOW")

fun triggerName(l: String, type: String): String = when (type) {
    "MANUAL" -> tr(l, "Manuel", "يدوي")
    "TIME" -> tr(l, "Heure", "وقت")
    "POWER_CONNECTED" -> tr(l, "Chargeur branché", "توصيل الشاحن")
    "POWER_DISCONNECTED" -> tr(l, "Chargeur débranché", "فصل الشاحن")
    "BATTERY_LOW" -> tr(l, "Batterie faible", "بطارية ضعيفة")
    else -> type
}

fun dayLabels(l: String): List<String> =
    if (l == "ar") listOf("ن", "ث", "ر", "خ", "ج", "س", "ح") else listOf("L", "M", "M", "J", "V", "S", "D")

fun summary(l: String, s: Scenario): String =
    if (s.triggerType == "TIME") {
        val days = if (s.daysMask == 127) tr(l, "Tous les jours", "كل يوم")
        else dayLabels(l).filterIndexed { i, _ -> (s.daysMask and (1 shl i)) != 0 }.joinToString(" ")
        String.format(Locale.US, "%02d:%02d", s.hour, s.minute) + " · " + days
    } else triggerName(l, s.triggerType)

fun detailText(l: String, d: String): String = when {
    d == "OK" -> tr(l, "Exécuté avec succès", "تم التنفيذ بنجاح")
    d == "KILL" -> tr(l, "Ignoré : arrêt général actif", "تم التجاهل: الإيقاف العام مفعّل")
    d == "RATE" -> tr(l, "Ignoré : limite de répétition", "تم التجاهل: حد التكرار")
    d == "COND" -> tr(l, "Conditions non remplies", "الشروط غير مستوفاة")
    d == "NO_NOTIF" -> tr(l, "Permission de notification manquante", "إذن الإشعارات مفقود")
    d.startsWith("ERROR:") -> d.removePrefix("ERROR:")
    else -> d
}

// ───────────────────────── Racine + navigation ─────────────────────────

@Composable
fun NexoraRoot(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        t("Accueil", "الرئيسية") to Icons.Default.Home,
        t("Scénarios", "السيناريوهات") to Icons.AutoMirrored.Filled.List,
        t("Créer", "إنشاء") to Icons.Default.Add,
        t("Modèles", "نماذج") to Icons.Default.Star,
        t("Réglages", "الإعدادات") to Icons.Default.Settings
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm) { vm.newDraft(); tab = 2 }
                1 -> ScenariosScreen(vm) { vm.loadDraft(it); tab = 2 }
                2 -> EditorScreen(vm) { tab = 1 }
                3 -> TemplatesScreen { vm.loadDraft(it); tab = 2 }
                else -> SettingsScreen(vm)
            }
        }
    }
}

// ───────────────────────── Composants communs ─────────────────────────

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun RowScope.StatCard(value: String, label: String) {
    Card(Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LogRow(l: LogEntry) {
    val lang = LocalLang.current
    val (icon, color) = when (l.status) {
        "SUCCESS" -> Icons.Default.CheckCircle to OkGreen
        "SKIPPED" -> Icons.Default.Info to MaterialTheme.colorScheme.outline
        else -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    val date = remember(l.ts) { SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(l.ts)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(l.scenarioName, fontWeight = FontWeight.Medium)
            Text(detailText(lang, l.detail) + " · " + date, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun rememberNotifRequester(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else ctx.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }
}

// ───────────────────────── Accueil ─────────────────────────

@Composable
fun HomeScreen(vm: MainVM, goCreate: () -> Unit) {
    val scenarios by vm.scenarios.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val requestNotif = rememberNotifRequester()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("NEXORA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(t("Ton téléphone travaille pour toi.", "هاتفك يعمل من أجلك."))
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vm.kill) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.kill) t("Arrêt général activé", "الإيقاف العام مفعّل") else t("Automatisations actives", "الأتمتة نشطة"),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (vm.kill) t("Aucun scénario ne s'exécute.", "لا يتم تنفيذ أي سيناريو.") else t("Tout fonctionne normalement.", "كل شيء يعمل بشكل طبيعي."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = { vm.updateKill(!vm.kill) }) {
                        Text(if (vm.kill) t("Réactiver", "إعادة التفعيل") else t("Tout arrêter", "إيقاف الكل"))
                    }
                }
            }
        }
        if (!notifOk) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Les notifications sont désactivées : les scénarios ne pourront pas t'alerter.", "الإشعارات معطلة: لن تتمكن السيناريوهات من تنبيهك."))
                        Button(onClick = requestNotif) { Text(t("Autoriser les notifications", "السماح بالإشعارات")) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(scenarios.count { it.enabled }.toString(), t("Scénarios actifs", "سيناريوهات نشطة"))
                StatCard(logs.size.toString(), t("Exécutions", "التنفيذات"))
                StatCard(logs.count { it.status == "ERROR" }.toString(), t("Erreurs", "أخطاء"))
            }
        }
        item {
            Button(onClick = goCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(t("Nouveau scénario", "سيناريو جديد"))
            }
        }
        item { Text(t("Dernières exécutions", "آخر التنفيذات"), style = MaterialTheme.typography.titleMedium) }
        if (logs.isEmpty()) {
            item { Text(t("Aucune exécution pour le moment.", "لا توجد تنفيذات حتى الآن.")) }
        } else {
            items(logs.take(5), key = { it.id }) { LogRow(it) }
        }
    }
}

// ───────────────────────── Scénarios ─────────────────────────

@Composable
fun ScenariosScreen(vm: MainVM, onEdit: (Scenario) -> Unit) {
    val scenarios by vm.scenarios.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lang = LocalLang.current
    var toDelete by remember { mutableStateOf<Scenario?>(null) }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(t("Supprimer ce scénario ?", "حذف هذا السيناريو؟")) },
            text = { Text(s.name) },
            confirmButton = { TextButton(onClick = { vm.delete(s); toDelete = null }) { Text(t("Supprimer", "حذف")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(t("Annuler", "إلغاء")) } }
        )
    }

    if (scenarios.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                t("Aucun scénario. Va dans « Créer » ou « Modèles » pour commencer.", "لا توجد سيناريوهات. انتقل إلى «إنشاء» أو «نماذج» للبدء."),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(scenarios, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(summary(lang, s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.toggle(s, it) })
                    }
                    Text(
                        (if (s.actionType == "REMINDER") t("Rappel", "تذكير") else t("Notification", "إشعار")) + " : " + s.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            vm.runNow(s) { code -> Toast.makeText(ctx, detailText(vm.lang, code), Toast.LENGTH_SHORT).show() }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(t("Exécuter", "تنفيذ"))
                        }
                        TextButton(onClick = { onEdit(s) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(t("Modifier", "تعديل"))
                        }
                        TextButton(onClick = { toDelete = s }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Éditeur (Créer / Modifier) ─────────────────────────

@Composable
fun TriChoice(label: String, value: Boolean?, yes: String, no: String, onChange: (Boolean?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text(t("Ignorer", "تجاهل")) })
            FilterChip(selected = value == true, onClick = { onChange(true) }, label = { Text(yes) })
            FilterChip(selected = value == false, onClick = { onChange(false) }, label = { Text(no) })
        }
    }
}

@Composable
fun EditorScreen(vm: MainVM, onDone: () -> Unit) {
    val lang = LocalLang.current
    key(vm.editorKey) {
        val d = vm.draft
        var name by remember { mutableStateOf(d.name) }
        var trigger by remember { mutableStateOf(d.triggerType) }
        val time = rememberTimePickerState(d.hour, d.minute, true)
        var days by remember { mutableIntStateOf(d.daysMask) }
        var logic by remember { mutableStateOf(d.logic) }
        var useBat by remember { mutableStateOf(d.condBatteryBelow != null) }
        var bat by remember { mutableFloatStateOf((d.condBatteryBelow ?: 30).toFloat()) }
        var charging by remember { mutableStateOf(d.condCharging) }
        var wifi by remember { mutableStateOf(d.condWifi) }
        var action by remember { mutableStateOf(d.actionType) }
        var title by remember { mutableStateOf(d.title) }
        var msg by remember { mutableStateOf(d.message) }
        var error by remember { mutableStateOf(false) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (d.id == 0L) t("Nouveau scénario", "سيناريو جديد") else t("Modifier le scénario", "تعديل السيناريو"),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(t("Nom du scénario", "اسم السيناريو")) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Section(t("1. Déclencheur — quand agir ?", "١. المُشغّل — متى؟")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRIGGERS.forEach { tt ->
                        FilterChip(selected = trigger == tt, onClick = { trigger = tt }, label = { Text(triggerName(lang, tt)) })
                    }
                }
                if (trigger == "TIME") {
                    TimeInput(state = time)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLabels(lang).forEachIndexed { i, label ->
                            FilterChip(
                                selected = (days and (1 shl i)) != 0,
                                onClick = { days = days xor (1 shl i) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (trigger != "MANUAL" && trigger != "TIME") {
                    Text(
                        t("Fonctionne via un service actif en arrière-plan (notification permanente).", "يعمل عبر خدمة نشطة في الخلفية (إشعار دائم)."),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Section(t("2. Conditions — faut-il agir ?", "٢. الشروط — هل ننفذ؟")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = logic == "AND", onClick = { logic = "AND" }, label = { Text(t("ET (toutes)", "و (الكل)")) })
                    FilterChip(selected = logic == "OR", onClick = { logic = "OR" }, label = { Text(t("OU (au moins une)", "أو (واحد على الأقل)")) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (useBat) t("Batterie sous ", "البطارية أقل من ") + bat.toInt() + " %" else t("Condition de batterie", "شرط البطارية"),
                        Modifier.weight(1f)
                    )
                    Switch(checked = useBat, onCheckedChange = { useBat = it })
                }
                if (useBat) Slider(value = bat, onValueChange = { bat = it }, valueRange = 5f..95f)
                TriChoice(t("Chargeur", "الشاحن"), charging, t("En charge", "قيد الشحن"), t("Pas en charge", "غير موصول")) { charging = it }
                TriChoice(t("Wi-Fi", "واي فاي"), wifi, t("Connecté", "متصل"), t("Non connecté", "غير متصل")) { wifi = it }
            }

            Section(t("3. Action — que faire ?", "٣. الإجراء — ماذا نفعل؟")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = action == "NOTIFICATION", onClick = { action = "NOTIFICATION" }, label = { Text(t("Notification", "إشعار")) })
                    FilterChip(selected = action == "REMINDER", onClick = { action = "REMINDER" }, label = { Text(t("Rappel", "تذكير")) })
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(t("Titre", "العنوان")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = msg, onValueChange = { msg = it },
                    label = { Text(t("Message", "الرسالة")) }, modifier = Modifier.fillMaxWidth()
                )
            }

            if (error) Text(t("Le nom et le titre sont obligatoires.", "الاسم والعنوان مطلوبان."), color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    if (name.isBlank() || title.isBlank()) {
                        error = true
                    } else {
                        vm.save(
                            d.copy(
                                name = name.trim(), triggerType = trigger,
                                hour = time.hour, minute = time.minute,
                                daysMask = if (days == 0) 127 else days,
                                logic = logic,
                                condBatteryBelow = if (useBat) bat.toInt() else null,
                                condCharging = charging, condWifi = wifi,
                                actionType = action, title = title.trim(), message = msg.trim()
                            )
                        )
                        vm.newDraft()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("Enregistrer", "حفظ")) }
        }
    }
}

// ───────────────────────── Modèles ─────────────────────────

@Composable
fun TemplatesScreen(onUse: (Scenario) -> Unit) {
    val lang = LocalLang.current
    val list = listOf(
        Scenario(
            name = tr(lang, "Boire de l'eau", "شرب الماء"), triggerType = "TIME", hour = 10, minute = 0,
            actionType = "REMINDER", title = tr(lang, "💧 Hydratation", "💧 الترطيب"),
            message = tr(lang, "Pense à boire un verre d'eau.", "تذكّر شرب كوب من الماء.")
        ),
        Scenario(
            name = tr(lang, "Alerte batterie faible", "تنبيه البطارية الضعيفة"), triggerType = "BATTERY_LOW",
            title = tr(lang, "🔋 Batterie faible", "🔋 بطارية ضعيفة"),
            message = tr(lang, "Branche ton chargeur bientôt.", "قم بتوصيل الشاحن قريباً.")
        ),
        Scenario(
            name = tr(lang, "Chargeur branché", "الشاحن موصول"), triggerType = "POWER_CONNECTED",
            title = tr(lang, "⚡ Charge en cours", "⚡ جارٍ الشحن"),
            message = tr(lang, "Le chargeur est bien connecté.", "الشاحن متصل بنجاح.")
        ),
        Scenario(
            name = tr(lang, "Bonne nuit", "ليلة سعيدة"), triggerType = "TIME", hour = 22, minute = 30,
            condCharging = false, actionType = "REMINDER",
            title = tr(lang, "🌙 Mets ton téléphone en charge", "🌙 ضع هاتفك على الشاحن"),
            message = tr(lang, "Il est temps de le brancher pour la nuit.", "حان وقت توصيله بالشاحن لليلة.")
        ),
        Scenario(
            name = tr(lang, "Bonjour", "صباح الخير"), triggerType = "TIME", hour = 7, minute = 30,
            title = tr(lang, "☀️ Bonne journée !", "☀️ يوماً سعيداً!"),
            message = tr(lang, "Une nouvelle journée commence.", "يوم جديد يبدأ.")
        )
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(t("Modèles prêts à configurer", "نماذج جاهزة للإعداد"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(list) { s ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(summary(lang, s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(s.message, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onUse(s) }) { Text(t("Utiliser", "استخدام")) }
                }
            }
        }
    }
}

// ───────────────────────── Réglages / Permissions / Historique ─────────────────────────

@Composable
fun PermRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null,
            tint = if (ok) OkGreen else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        if (!ok) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
fun SettingsScreen(vm: MainVM) {
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = sub != null) { sub = null }
    if (sub == "history") {
        HistoryScreen(vm) { sub = null }
        return
    }

    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(tick) {
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val requestNotif = rememberNotifRequester()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(t("Réglages", "الإعدادات"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section(t("Permissions", "الأذونات")) {
            PermRow(t("Notifications", "الإشعارات"), notifOk, t("Autoriser", "السماح"), requestNotif)
            PermRow(t("Alarmes exactes (heure précise)", "المنبهات الدقيقة (وقت محدد)"), exactOk, t("Autoriser", "السماح")) {
                if (Build.VERSION.SDK_INT >= 31) {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }
            }
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("Optimisation de la batterie", "تحسين البطارية")) }
            Text(
                t("Certains fabricants limitent l'arrière-plan : exclus NEXORA de l'optimisation de batterie pour plus de fiabilité.",
                    "بعض الشركات تقيّد الخلفية: استثنِ NEXORA من تحسين البطارية لموثوقية أكبر."),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section(t("Langue", "اللغة")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.lang == "fr", onClick = { vm.updateLang("fr") }, label = { Text("Français") })
                FilterChip(selected = vm.lang == "ar", onClick = { vm.updateLang("ar") }, label = { Text("العربية") })
            }
        }

        Section(t("Thème", "المظهر")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.theme == "system", onClick = { vm.updateTheme("system") }, label = { Text(t("Système", "النظام")) })
                FilterChip(selected = vm.theme == "light", onClick = { vm.updateTheme("light") }, label = { Text(t("Clair", "فاتح")) })
                FilterChip(selected = vm.theme == "dark", onClick = { vm.updateTheme("dark") }, label = { Text(t("Sombre", "داكن")) })
            }
        }

        Section(t("Historique", "السجل")) {
            Button(onClick = { sub = "history" }, modifier = Modifier.fillMaxWidth()) { Text(t("Voir l'historique des exécutions", "عرض سجل التنفيذات")) }
        }

        Text("NEXORA 0.1.0 · MVP", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun HistoryScreen(vm: MainVM, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(t("Historique", "السجل"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearLogs() }) { Text(t("Effacer", "مسح")) }
        }
        if (logs.isEmpty()) {
            Text(t("Aucune exécution enregistrée.", "لا توجد تنفيذات مسجلة."), modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn { items(logs, key = { it.id }) { LogRow(it) } }
        }
    }
}
