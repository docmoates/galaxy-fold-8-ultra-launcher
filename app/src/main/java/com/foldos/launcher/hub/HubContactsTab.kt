package com.foldos.launcher.hub

import android.Manifest
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.HubContact
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Hub's Contacts tab (mock-up `peoplePage`): favourites row, search, A-Z list with section
 * headers and an alphabet scrubber, call / message buttons per row. The inner screen keeps the list
 * on the left and shows the selected contact's card (call, message, video, e-mail, details and
 * recent calls) on the right.
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
    val granted = rememberHubPermission(Manifest.permission.READ_CONTACTS)
    val inner = screen != ScreenKind.COVER

    val contacts by remember(graph) { graph.hub.contacts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by remember(graph) { graph.hub.favorites() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf(-1L) }

    val filtered = remember(contacts, query) { filterContacts(contacts, query) }
    val selected: HubContact? = remember(contacts, favorites, selectedId) {
        contacts.firstOrNull { it.id == selectedId } ?: favorites.firstOrNull() ?: contacts.firstOrNull()
    }
    val onContactTap: (HubContact) -> Unit = { contact ->
        if (inner) selectedId = contact.id else HubIntents.openContact(context, contact)
    }

    val header: @Composable () -> Unit = {
        HubPageHeader(
            title = "Contacts",
            subtitle = when {
                !granted -> "Contacts access needed"
                contacts.isEmpty() -> "No contacts yet"
                else -> "${contacts.size} contacts"
            },
            subtitleIcon = Icons.Rounded.Person,
            trailingIcon = Icons.Rounded.PersonAdd,
            onTrailingClick = { HubIntents.newContact(context) },
        )
    }
    val search: @Composable () -> Unit = {
        HubSearchPill(
            value = query,
            onValueChange = { query = it },
            placeholder = if (contacts.isEmpty()) "Search contacts" else "Search ${contacts.size} contacts",
        )
    }
    val favoritesRow: @Composable () -> Unit = {
        if (granted && query.isEmpty() && favorites.isNotEmpty()) {
            if (!inner) HubKicker("Favourites")
            HubFavoritesRow(favorites = favorites, selectedId = if (inner) selected?.id else null, onTap = onContactTap)
        }
    }
    val list: @Composable (Modifier) -> Unit = { m ->
        if (!granted) {
            HubPermissionCard(
                title = "Contacts",
                body = "Your contacts, favourites and photos stay on this phone.",
                permission = Manifest.permission.READ_CONTACTS,
                modifier = m,
            )
        } else {
            HubListCard(modifier = m) {
                HubContactList(
                    contacts = filtered,
                    selectedId = if (inner) selected?.id else null,
                    onTap = onContactTap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (!granted) {
        // Permission missing: only the centred card, with no header, search pill, favourites or list.
        HubPermissionPanel(
            icon = Icons.Rounded.People,
            title = "Contacts access",
            reason = "Allow access to show your contacts, favourites and photos.",
            permission = Manifest.permission.READ_CONTACTS,
            modifier = modifier.padding(horizontal = HubPagePadding).padding(contentPadding),
        )
        return
    }

    if (!inner) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = HubPagePadding).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(HubCardGap),
        ) {
            header()
            search()
            favoritesRow()
            list(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        HubTwoPane(
            modifier = modifier.fillMaxSize().padding(horizontal = HubPagePadding).padding(contentPadding),
            left = {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(HubCardGap)) {
                    header()
                    search()
                    favoritesRow()
                    list(Modifier.fillMaxWidth().weight(1f))
                }
            },
            right = {
                Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                    HubListCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (!granted || selected == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (granted) "Pick a contact" else "Allow contacts access to see details",
                                    color = LocalFoldOsTokens.current.onSurface.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                )
                            }
                        } else {
                            HubContactCard(contact = selected, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            },
        )
    }
}

/** Name or phone substring match, case-insensitive; an empty query keeps everything. */
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

/** Section letter for the A-Z list: the first letter upper-cased, "#" for anything else. */
internal fun contactSection(contact: HubContact): String {
    val first = contact.name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

/* ------------------------------------------------------------------------------------------ */
/* Favourites                                                                                  */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.favs`: 60dp avatars with first names. */
@Composable
private fun HubFavoritesRow(favorites: List<HubContact>, selectedId: Long?, onTap: (HubContact) -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        favorites.forEach { contact ->
            val isSelected = contact.id == selectedId
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) tokens.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onTap(contact) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HubAvatar(name = contact.name, photoUri = contact.photoUri, size = 60.dp)
                Text(
                    text = hubFirstName(contact.name),
                    color = tokens.onSurface,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* A-Z list with scrubber                                                                      */
/* ------------------------------------------------------------------------------------------ */

private sealed class ContactListEntry {
    data class Section(val letter: String) : ContactListEntry()
    data class Person(val contact: HubContact) : ContactListEntry()
}

/** Section headers (`.sect`) and rows (`.crow`) in a LazyColumn, with the alphabet scrubber down the right. */
@Composable
private fun HubContactList(
    contacts: List<HubContact>,
    selectedId: Long?,
    onTap: (HubContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val entries = remember(contacts) {
        val list = ArrayList<ContactListEntry>(contacts.size + 27)
        var last: String? = null
        for (contact in contacts) {
            val section = contactSection(contact)
            if (section != last) {
                list += ContactListEntry.Section(section)
                last = section
            }
            list += ContactListEntry.Person(contact)
        }
        list
    }
    val sectionIndex = remember(entries) {
        val map = LinkedHashMap<String, Int>()
        entries.forEachIndexed { index, entry -> if (entry is ContactListEntry.Section) map[entry.letter] = index }
        map
    }
    val letters = remember(sectionIndex) { sectionIndex.keys.toList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    if (contacts.isEmpty()) {
        HubEmptyRow("No contacts match")
        return
    }

    Row(modifier) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    is ContactListEntry.Section -> item(key = "s-${entry.letter}-$index") {
                        Text(
                            text = entry.letter,
                            color = tokens.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    is ContactListEntry.Person -> item(key = "c-${entry.contact.id}") {
                        HubContactRow(
                            contact = entry.contact,
                            selected = entry.contact.id == selectedId,
                            onTap = { onTap(entry.contact) },
                            onCall = { entry.contact.phone?.let { HubIntents.dial(context, it) } },
                            onMessage = { entry.contact.phone?.let { HubIntents.message(context, it) } },
                        )
                    }
                }
            }
        }
        if (letters.size > 1) {
            HubAlphabetScrubber(
                letters = letters,
                active = activeLetter,
                modifier = Modifier.width(22.dp).fillMaxHeight().padding(vertical = 8.dp),
                onLetter = { letter ->
                    activeLetter = letter
                    val index = sectionIndex[letter] ?: return@HubAlphabetScrubber
                    scope.launch { listState.scrollToItem(index) }
                },
                onRelease = { activeLetter = null },
            )
        }
    }
}

@Composable
private fun HubContactRow(
    contact: HubContact,
    selected: Boolean,
    onTap: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) tokens.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubAvatar(name = contact.name, photoUri = contact.photoUri, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text(text = contact.name, color = tokens.onSurface, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = contact.phone ?: contact.email
            if (detail != null) {
                Text(text = detail, color = tokens.onSurface.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (contact.phone != null) {
            HubIconButton(Icons.Rounded.Call, "Call", onCall, size = 36.dp, tinted = false)
            HubIconButton(Icons.Rounded.Chat, "Message", onMessage, size = 36.dp, tinted = false)
        }
    }
}

/** The A-Z rail: tap or drag to jump to a section; the letter under the finger is highlighted. */
@Composable
private fun HubAlphabetScrubber(
    letters: List<String>,
    active: String?,
    onLetter: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    var heightPx by remember { mutableIntStateOf(1) }
    fun letterAt(y: Float): String? {
        if (letters.isEmpty()) return null
        val index = ((y / heightPx.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }
    Column(
        modifier = modifier
            .onSizeChanged { heightPx = it.height }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> letterAt(offset.y)?.let(onLetter) },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, _ -> letterAt(change.position.y)?.let(onLetter) }
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        letterAt(offset.y)?.let(onLetter)
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val isActive = letter == active
            Text(
                text = letter,
                color = if (isActive) tokens.accent else tokens.onSurface.copy(alpha = 0.6f),
                fontSize = if (isActive) 13.sp else 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Contact card (inner screen)                                                                 */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.cdet` / `.cact` / `.irow`: big avatar, four actions, details and recent calls. */
@Composable
private fun HubContactCard(contact: HubContact, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val repo = graph.hub as? FoldOsHubRepository
    val callsGranted = rememberHubPermission(Manifest.permission.READ_CALL_LOG)
    val recentCalls by remember(repo) { repo?.recentCalls() ?: flowOf(emptyList<HubCallLogEntry>()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val phone = contact.phone
    val calls = remember(recentCalls, phone) {
        if (phone == null) emptyList() else recentCalls.filter { PhoneNumberUtils.compare(it.number, phone) }.take(5)
    }
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HubAvatar(name = contact.name, photoUri = contact.photoUri, size = 96.dp)
            Text(
                text = contact.name,
                color = tokens.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (contact.starred) {
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.onSurface.copy(alpha = 0.65f))
                }
                Text(
                    text = if (contact.starred) "Favourite" else "Contact",
                    color = tokens.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.5.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            HubContactAction(Icons.Rounded.Call, "Call", enabled = phone != null) { phone?.let { HubIntents.dial(context, it) } }
            HubContactAction(Icons.Rounded.Chat, "Message", enabled = phone != null) { phone?.let { HubIntents.message(context, it) } }
            HubContactAction(Icons.Rounded.Videocam, "Video", enabled = true) {
                scope.launch {
                    val intent = repo?.videoCallIntent(contact)
                    if (intent != null) HubIntents.start(context, intent) else HubIntents.openContact(context, contact)
                }
            }
            HubContactAction(Icons.Rounded.Email, "Email", enabled = contact.email != null) {
                contact.email?.let { HubIntents.email(context, it) }
            }
        }
        if (phone != null) {
            HubInfoRow(Icons.Rounded.Call, phone, "Phone") { HubIntents.dial(context, phone) }
        }
        val email = contact.email
        if (email != null) {
            HubInfoRow(Icons.Rounded.Email, email, "Email") { HubIntents.email(context, email) }
        }
        HubInfoRow(Icons.Rounded.Person, "Open in Contacts", "Edit, share or see every detail") { HubIntents.openContact(context, contact) }
        if (callsGranted && calls.isNotEmpty()) {
            calls.forEach { call ->
                HubInfoRow(
                    icon = Icons.Rounded.History,
                    text = "${callTypeLabel(call.type)} · ${HubFormat.callTime(call.time, today)}",
                    caption = if (call.durationSeconds > 0) HubFormat.duration(0L, call.durationSeconds * 1000L) else "Recent call",
                    onClick = { HubIntents.dial(context, call.number) },
                )
            }
        }
    }
}

private fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    CallLog.Calls.MISSED_TYPE -> "Missed"
    CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
    CallLog.Calls.REJECTED_TYPE -> "Declined"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
    else -> "Call"
}

/** Mock-up `.cact div`: 50dp accent-tinted disc with a label under it. */
@Composable
private fun HubContactAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val alpha = if (enabled) 1f else 0.4f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(tokens.accent.copy(alpha = 0.18f * alpha))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tokens.accent.copy(alpha = alpha))
        }
        Text(text = label, color = tokens.onSurface.copy(alpha = alpha), fontSize = 12.sp)
    }
}

/** Mock-up `.irow`: icon, value and caption with a hairline above. */
@Composable
private fun HubInfoRow(icon: ImageVector, text: String, caption: String, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    HubRowDivider()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = tokens.onSurface.copy(alpha = 0.6f))
        Column(Modifier.weight(1f)) {
            Text(text = text, color = tokens.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = caption, color = tokens.onSurface.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
