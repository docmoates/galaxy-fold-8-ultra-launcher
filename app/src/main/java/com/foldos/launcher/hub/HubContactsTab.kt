package com.foldos.launcher.hub

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.foldos.launcher.theme.FoldOsSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.HubCall
import com.foldos.launcher.core.repo.HubContact
import com.foldos.launcher.theme.LocalFoldOsTokens

import java.time.LocalDate

/**
 * The Hub's Contacts tab (1.6.1): the tab itself never scrolls. A Favorites row (up to 7 starred contacts; tap
 * opens the contact card, long-press opens the dialer) sits above a rounded box that holds ALL contacts,
 * alphabetical with A-Z section headers, a call button (ACTION_DIAL, never a direct call) and a message button
 * (`sms:`) per row, and a letter scrubber on the right edge; only that list scrolls. Search filters the list live.
 * The field stays inline when the height allows it and otherwise collapses to an icon. Inner screen: list on the
 * left 55%, favorites as a column on the right.
 */
@Composable
internal fun HubContactsTab(
    screen: ScreenKind,
    settings: LauncherSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val granted = rememberHubPermission(Manifest.permission.READ_CONTACTS)
    val callsGranted = rememberHubPermission(Manifest.permission.READ_CALL_LOG)
    val inner = screen != ScreenKind.COVER

    if (!granted) {
        // Permission missing: only the compact centred panel.
        HubPermissionPanel(
            icon = Icons.Rounded.People,
            title = "Contacts access",
            reason = "Allow access to show your contacts, favourites and photos.",
            permission = Manifest.permission.READ_CONTACTS,
            modifier = modifier.padding(horizontal = HubPagePadding).padding(contentPadding),
        )
        return
    }

    val contacts by remember(graph) { graph.hub.contacts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by remember(graph) { graph.hub.favorites() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val missed by remember(graph) { graph.hub.missedCalls() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val favs = remember(favorites) { favorites.take(MAX_FAVORITES) }
    val missedByKey = remember(missed, callsGranted) {
        if (!callsGranted) emptyMap<String, List<HubCall>>() else missed.groupBy { hubNumberKey(it.number) }.filterKeys { it.isNotEmpty() }
    }
    // Sorting, filtering and grouping ~1000 contacts runs off the main thread.
    val entries by produceState(initialValue = emptyList<ContactEntry>(), contacts, query) {
        value = withContext(Dispatchers.Default) { buildContactEntries(filterContacts(contacts, query)) }
    }
    val peopleCount = remember(entries) { entries.count { it is ContactEntry.Person } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = HubPagePadding),
    ) {
        val gap = HubFitGap
        val barH = HubSlimBarHeight.scaledBy(fontScale)
        val searchH = 50.dp
        val kickerH = 22.dp.scaledBy(fontScale)
        // The field stays inline only when the tab is tall enough to keep a useful list beside it.
        val inlineSearch = maxHeight >= 680.dp
        val showSearch = inlineSearch || searchOpen || query.isNotEmpty()

        val slotWidth = maxWidth / FAVORITE_SLOTS
        val avatar = minOf(56.dp, slotWidth - 10.dp).coerceAtLeast(32.dp)
        val favRowH = avatar + 8.dp + 18.dp.scaledBy(fontScale)

        var left = maxHeight - barH - gap
        if (showSearch) left -= searchH + gap
        // Cover: favorites row above the list, only while a useful list stays below it.
        val showFavsRow = !inner && query.isBlank() && favs.isNotEmpty() && left >= kickerH + favRowH + gap + 220.dp

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            HubSlimBar(title = "Contacts") {
                if (!inlineSearch) {
                    HubIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search contacts",
                        onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                        size = 40.dp,
                        tinted = false,
                    )
                }
                HubIconButton(
                    icon = Icons.Rounded.PersonAdd,
                    contentDescription = "New contact",
                    onClick = { HubIntents.newContact(context) },
                    size = 40.dp,
                )
            }
            if (showSearch) {
                HubSearchPill(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (contacts.isEmpty()) "Search contacts" else "Search ${contacts.size} contacts",
                )
            }
            if (showFavsRow) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    HubKicker("Favorites", Modifier.height(kickerH))
                    Row(Modifier.fillMaxWidth()) {
                        favs.forEach { contact ->
                            FavoriteSlot(contact = contact, width = slotWidth, avatar = avatar)
                        }
                    }
                }
            }
            if (inner) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ContactsListBox(
                        entries = entries,
                        peopleCount = peopleCount,
                        hasQuery = query.isNotBlank(),
                        missedByKey = missedByKey,
                        today = today,
                        modifier = Modifier.weight(if (favs.isEmpty()) 1f else 0.55f).fillMaxHeight(),
                    )
                    if (favs.isNotEmpty()) {
                        Column(Modifier.weight(0.45f).fillMaxHeight()) {
                            HubKicker("Favorites", Modifier.height(kickerH))
                            HubListCard(Modifier.fillMaxWidth()) {
                                favs.forEachIndexed { index, contact ->
                                    if (index > 0) HubRowDivider()
                                    FavoriteRow(contact)
                                }
                            }
                        }
                    }
                }
            } else {
                ContactsListBox(
                    entries = entries,
                    peopleCount = peopleCount,
                    hasQuery = query.isNotBlank(),
                    missedByKey = missedByKey,
                    today = today,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

/** One line of the all-contacts list: an alphabetic section header or a person. */
internal sealed interface ContactEntry {
    val key: String

    data class Header(val letter: Char) : ContactEntry {
        override val key: String get() = "h_$letter"
    }

    data class Person(val contact: HubContact) : ContactEntry {
        override val key: String get() = "c_${contact.id}_${contact.lookupKey}"
    }
}

/** Section letter of a display name: A-Z (diacritics folded to their base letter), everything else is '#'. */
internal fun contactLetter(name: String): Char {
    val first = name.trim().firstOrNull() ?: return '#'
    val base = java.text.Normalizer.normalize(first.toString(), java.text.Normalizer.Form.NFD).firstOrNull() ?: first
    val upper = base.uppercaseChar()
    return if (upper in 'A'..'Z') upper else '#'
}

/** Alphabetical (case-insensitive) list of [contacts] with a [ContactEntry.Header] before each letter; '#' goes last. */
internal fun buildContactEntries(contacts: List<HubContact>): List<ContactEntry> {
    val sorted = contacts.sortedWith(
        compareBy<HubContact> { contactLetter(it.name) == '#' }.thenBy { it.name.lowercase() },
    )
    val out = ArrayList<ContactEntry>(sorted.size + 28)
    var current: Char? = null
    for (contact in sorted) {
        val letter = contactLetter(contact.name)
        if (letter != current) {
            out += ContactEntry.Header(letter)
            current = letter
        }
        out += ContactEntry.Person(contact)
    }
    return out
}

/** The rounded list card: a LazyColumn of every contact with section headers, and the letter scrubber on the right. */
@Composable
private fun ContactsListBox(
    entries: List<ContactEntry>,
    peopleCount: Int,
    hasQuery: Boolean,
    missedByKey: Map<String, List<HubCall>>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val fontScale = LocalDensity.current.fontScale
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowH = ContactRowHeight.scaledBy(fontScale)
    val letters = remember(entries) { entries.mapNotNull { (it as? ContactEntry.Header)?.letter } }
    val letterIndex = remember(entries) {
        HashMap<Char, Int>().also { map -> entries.forEachIndexed { i, e -> if (e is ContactEntry.Header) map[e.letter] = i } }
    }
    // A new query starts at the top.
    LaunchedEffect(hasQuery, peopleCount) { listState.scrollToItem(0) }

    FoldOsSurface(modifier = modifier) {
        if (entries.isEmpty()) {
            HubEmptyRow(if (hasQuery) "No contacts match" else "No contacts yet")
        } else {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    items(entries, key = { it.key }, contentType = { if (it is ContactEntry.Header) 0 else 1 }) { entry ->
                        when (entry) {
                            is ContactEntry.Header -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(tokens.onSurface.copy(alpha = 0.05f))
                                    .padding(start = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = entry.letter.toString(),
                                    color = tokens.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            is ContactEntry.Person -> {
                                val calls = missedByKey[hubNumberKey(entry.contact.phone.orEmpty())]
                                ContactQuickRow(
                                    contact = entry.contact,
                                    missedCount = calls?.size ?: 0,
                                    height = rowH,
                                    today = today,
                                    lastMissed = calls?.firstOrNull()?.time,
                                )
                            }
                        }
                    }
                }
                if (letters.size > 1) {
                    LetterScrubber(
                        letters = letters,
                        onJump = { letter ->
                            val index = letterIndex[letter]
                            if (index != null) scope.launch { listState.scrollToItem(index) }
                        },
                    )
                }
            }
        }
    }
}

/** Slim right-edge letter strip: tap or drag along it to jump to a section. Thins out the letters when short. */
@Composable
private fun LetterScrubber(letters: List<Char>, onJump: (Char) -> Unit) {
    val tokens = LocalFoldOsTokens.current
    var heightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val perLetterPx = with(density) { 15.dp.toPx() }
    val step = if (heightPx <= 0f) 1 else ceil(letters.size * perLetterPx / heightPx).toInt().coerceAtLeast(1)
    val shown = remember(letters, step) { letters.filterIndexed { i, _ -> i % step == 0 } }
    val jump by rememberUpdatedState(onJump)
    Column(
        modifier = Modifier
            .width(26.dp)
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(shown) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(y: Float) {
                        if (shown.isEmpty() || size.height <= 0) return
                        val i = (y / size.height * shown.size).toInt().coerceIn(0, shown.size - 1)
                        jump(shown[i])
                    }
                    at(down.position.y)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            at(change.position.y)
                            change.consume()
                        }
                    } while (change.pressed)
                }
            }
            .semantics { contentDescription = "Jump to letter" },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        shown.forEach { letter ->
            Text(
                text = letter.toString(),
                color = tokens.accent,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Favorite as a compact row (inner screen): tap opens the contact card, long-press opens the dialer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(contact: HubContact) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val phone = contact.phone
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                if (phone != null) customActions = listOf(CustomAccessibilityAction("Call ${contact.name}") { HubIntents.dial(context, phone); true })
            }
            .combinedClickable(
                role = Role.Button,
                onClick = { HubIntents.openContact(context, contact) },
                onLongClick = { if (phone != null) HubIntents.dial(context, phone) },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubAvatar(name = contact.name, photoUri = contact.photoUri, size = 38.dp)
        Text(
            text = contact.name,
            color = tokens.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


private const val MAX_FAVORITES = 7
private const val FAVORITE_SLOTS = 7
private val ContactRowHeight: Dp = 60.dp

/** Last nine digits of a phone number, enough to match "+1 512 555 0100" with "(512) 555-0100". */
internal fun hubNumberKey(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 9) digits.takeLast(9) else digits
}

/**
 * Contacts with a phone number that are not already shown as favorites, most-called first (counted over
 * [callNumbers], the numbers of the recent call log), then everyone else alphabetically.
 */
internal fun frequentContacts(contacts: List<HubContact>, excludeIds: Set<Long>, callNumbers: List<String>): List<HubContact> {
    val counts = HashMap<String, Int>()
    for (number in callNumbers) {
        val key = hubNumberKey(number)
        if (key.isNotEmpty()) counts[key] = (counts[key] ?: 0) + 1
    }
    return contacts
        .filter { it.id !in excludeIds && !it.phone.isNullOrBlank() }
        .sortedWith(
            compareByDescending<HubContact> { counts[hubNumberKey(it.phone.orEmpty())] ?: 0 }
                .thenBy { it.name.lowercase() },
        )
}

/** Name, phone or e-mail substring match, case-insensitive; an empty query keeps everything. */
internal fun filterContacts(contacts: List<HubContact>, query: String): List<HubContact> {
    val q = query.trim()
    if (q.isEmpty()) return contacts
    val digits = q.filter { it.isDigit() }
    return contacts.filter { contact ->
        contact.name.contains(q, ignoreCase = true) ||
            (digits.length >= 3 && contact.phone?.filter { it.isDigit() }?.contains(digits) == true) ||
            contact.email?.contains(q, ignoreCase = true) == true
    }
}

/** Favorite avatar with first name: tap opens the contact card, long-press opens the dialer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSlot(contact: HubContact, width: Dp, avatar: Dp) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val phone = contact.phone
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .semantics {
                if (phone != null) customActions = listOf(CustomAccessibilityAction("Call ${contact.name}") { HubIntents.dial(context, phone); true })
            }
            .combinedClickable(
                role = Role.Button,
                onClick = { HubIntents.openContact(context, contact) },
                onLongClick = { if (phone != null) HubIntents.dial(context, phone) },
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HubAvatar(name = contact.name, photoUri = contact.photoUri, size = avatar)
        Text(
            text = hubFirstName(contact.name),
            color = tokens.onSurface,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** One "Frequent" row: avatar (with a missed-call badge), name, status line, call and message buttons. */
@Composable
private fun ContactQuickRow(
    contact: HubContact,
    missedCount: Int,
    height: Dp,
    today: LocalDate,
    lastMissed: Long?,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val phone = contact.phone
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(role = Role.Button) { HubIntents.openContact(context, contact) }
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            HubAvatar(name = contact.name, photoUri = contact.photoUri, size = 42.dp)
            if (missedCount > 0) {
                Text(
                    text = if (missedCount > 99) "99+" else missedCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-3).dp)
                        .defaultMinSize(minWidth = 16.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HubMissedRed)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.name,
                color = tokens.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = if (missedCount > 0 && lastMissed != null) {
                "Missed call ${HubFormat.callTime(lastMissed, today)}"
            } else {
                phone ?: contact.email
            }
            if (detail != null) {
                Text(
                    text = detail,
                    color = if (missedCount > 0) HubMissedRed else tokens.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (phone != null) {
            HubIconButton(Icons.Rounded.Call, "Call ${contact.name}", { HubIntents.dial(context, phone) }, size = 38.dp)
            HubIconButton(Icons.Rounded.Chat, "Message ${contact.name}", { HubIntents.message(context, phone) }, size = 38.dp)
        }
    }
}

private val HubMissedRed: Color = Color(0xFFE5384B)
