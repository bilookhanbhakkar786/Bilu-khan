package com.example.ui.screens.principal

import android.net.Uri
import com.example.data.model.toDisplayDateTime
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AnnouncementPost
import com.example.data.model.ClassSection
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolProfileAndPostsScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    initialTab: Int = 0
) {
    val school = state.school ?: return
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val canEditSchool = isPrincipal || state.role == UserRole.TEACHER
    var tabIndex by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showEditSchoolDialog by remember { mutableStateOf(false) }
    var showCreatePostDialog by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.updateSchoolProfile(
                school.name, school.schoolType, school.address, school.city,
                school.contactNumber, school.email, school.description,
                school.establishedYear, school.website, null, null, uri
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 860.dp)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("School Feed & Posts") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("School Profile") })
            }
            Spacer(Modifier.height(14.dp))

            if (tabIndex == 0) {
                val canPublish = isPrincipal || state.currentTeacherPermissions.publishAnnouncements
                if (canPublish) {
                    Button(
                        onClick = { showCreatePostDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_announcement_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Publish Announcement / School Post")
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (state.announcements.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Campaign,
                        title = "No posts published yet",
                        description = "Announcements, exam schedules, admission notices, and school news will appear here."
                    )
                } else {
                    state.announcements.forEach { post ->
                        AnnouncementPostCard(
                            post = post,
                            comments = state.commentsByAnnouncement[post.id].orEmpty(),
                            canDelete = isPrincipal || post.publisherId == state.currentUser?.id,
                            onLike = { viewModel.toggleAnnouncementLike(post.id) },
                            onLoadComments = { viewModel.loadComments(post.id) },
                            onAddComment = { text -> viewModel.addComment(post.id, text) },
                            onDelete = { viewModel.deleteAnnouncement(post.id) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                // School Profile View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    if (!school.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = school.bannerUrl,
                            contentDescription = "School Cover Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_default_school_banner),
                            contentDescription = "School Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, NavyDeep.copy(alpha = 0.85f))))
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SchoolAvatar(school.logoUrl, school.name, size = 60.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(school.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text(
                                    text = "${school.schoolType} • ID: ${school.schoolCode}${if (!school.isPublic) " • HIDDEN" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            if (canEditSchool) {
                                IconButton(
                                    onClick = { showEditSchoolDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("edit_school_banner_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit School", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                if (isPrincipal) {
                    Spacer(Modifier.height(14.dp))
                    SectionCard(
                        title = "School Visibility",
                        trailing = {
                            StatusPill(
                                label = if (school.isPublic) "PUBLIC" else "HIDDEN",
                                textColor = if (school.isPublic) PresentGreen else AbsentRed,
                                backgroundColor = if (school.isPublic) PresentGreenBg else AbsentRedBg
                            )
                        }
                    ) {
                        Text(
                            text = if (school.isPublic) {
                                "Your school is currently visible to all Students without approval. Tap below if you want to hide it."
                            } else {
                                "Your school is HIDDEN. Students cannot find or view it — only you (Principal) and your Teachers have access."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hide school from students",
                                style = MaterialTheme.typography.titleSmall,
                                color = NavyPrimary
                            )
                            Switch(
                                checked = !school.isPublic,
                                onCheckedChange = { hide -> viewModel.toggleSchoolVisibility(!hide) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.toggleSchoolVisibility(!school.isPublic) },
                            modifier = Modifier.fillMaxWidth().testTag("hide_school_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (school.isPublic) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = if (school.isPublic) AbsentRed else PresentGreen
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (school.isPublic) "Hide School Now" else "Unhide School (Make Public)",
                                color = if (school.isPublic) AbsentRed else PresentGreen
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionCard(
                    title = "About ${school.name}",
                    trailing = {
                        if (canEditSchool) {
                            TextButton(
                                onClick = { showEditSchoolDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("edit_school_button")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit School")
                            }
                        }
                    }
                ) {
                    Text(school.description.ifBlank { "No description added yet." }, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("Type: ${school.schoolType}", style = MaterialTheme.typography.bodySmall)
                    Text("Address: ${school.address.ifBlank { "—" }}, ${school.city}", style = MaterialTheme.typography.bodySmall)
                    Text("Contact: ${school.contactNumber}", style = MaterialTheme.typography.bodySmall)
                    Text("Email: ${school.email}", style = MaterialTheme.typography.bodySmall)
                    school.establishedYear?.let { Text("Established: $it", style = MaterialTheme.typography.bodySmall) }
                    school.website?.let { Text("Website: $it", style = MaterialTheme.typography.bodySmall) }
                }

                Spacer(Modifier.height(14.dp))
                SectionCard(
                    title = "School Pictures (${school.galleryUrls.size}/8)",
                    trailing = {
                        if (canEditSchool && school.galleryUrls.size < 8) {
                            TextButton(
                                onClick = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_gallery_photo_button")
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add Photo")
                            }
                        }
                    }
                ) {
                    if (school.galleryUrls.isEmpty()) {
                        Text("No campus gallery pictures uploaded yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(school.galleryUrls) { url ->
                                Box(modifier = Modifier.size(130.dp)) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "School gallery photo",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (canEditSchool) {
                                        Surface(
                                            shape = CircleShape,
                                            color = NavyDeep.copy(alpha = 0.72f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.removeSchoolGalleryPhoto(url) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("remove_gallery_photo_button")
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "Remove photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditSchoolDialog) {
        EditSchoolDialog(
            state = state,
            onDismiss = { showEditSchoolDialog = false },
            onSave = { name, type, address, city, phone, email, desc, year, web, logoUri, bannerUri, clearLogo, clearBanner ->
                viewModel.updateSchoolProfile(
                    name = name,
                    schoolType = type,
                    address = address,
                    city = city,
                    contactNumber = phone,
                    email = email,
                    description = desc,
                    establishedYear = year,
                    website = web,
                    logoUri = logoUri,
                    bannerUri = bannerUri,
                    galleryUriToAdd = null,
                    clearLogo = clearLogo,
                    clearBanner = clearBanner
                )
                showEditSchoolDialog = false
            }
        )
    }

    if (showCreatePostDialog) {
        CreateAnnouncementDialog(
            sections = state.sessionSections,
            canChooseScope = isPrincipal || state.role == UserRole.TEACHER,
            onDismiss = { showCreatePostDialog = false },
            onPublish = { scope, section, category, title, body, imageUri, allowComments ->
                viewModel.publishAnnouncement(scope, section, category, title, body, imageUri, allowComments)
                showCreatePostDialog = false
            }
        )
    }
}

@Composable
fun AnnouncementPostCard(
    post: AnnouncementPost,
    comments: List<com.example.data.model.AnnouncementComment>,
    canDelete: Boolean,
    onLike: () -> Unit,
    onLoadComments: () -> Unit,
    onAddComment: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(post.category, RoyalBlue, SkySoft)
                if (post.scope == "class") {
                    StatusPill("Class Update", NavyPrimary, SkySoft)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete post", tint = AbsentRed)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(post.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
        Spacer(Modifier.height(4.dp))
        Text(post.body, style = MaterialTheme.typography.bodyMedium)
        if (!post.imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.title,
                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Published by ${post.publisherName} • ${post.createdAt.toDisplayDateTime()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onLike) {
                Icon(Icons.Rounded.Favorite, contentDescription = "Like", tint = AbsentRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${post.likesCount} Likes")
            }
            if (post.allowComments) {
                TextButton(onClick = {
                    showComments = !showComments
                    if (showComments) onLoadComments()
                }) {
                    Icon(Icons.Rounded.Comment, contentDescription = "Comments", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Comments")
                }
            }
        }

        if (showComments && post.allowComments) {
            Spacer(Modifier.height(8.dp))
            comments.forEach { c ->
                Text("${c.authorName}: ${c.body}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Write a comment") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (commentText.isNotBlank()) {
                        onAddComment(commentText)
                        commentText = ""
                    }
                }) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send comment", tint = RoyalBlue)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSchoolDialog(
    state: HubUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, Int?, String?, Uri?, Uri?, Boolean, Boolean) -> Unit
) {
    val school = state.school ?: return
    var name by remember { mutableStateOf(school.name) }
    var type by remember { mutableStateOf(school.schoolType.ifBlank { "Private" }) }
    var address by remember { mutableStateOf(school.address) }
    var city by remember { mutableStateOf(school.city) }
    var phone by remember { mutableStateOf(school.contactNumber) }
    var email by remember { mutableStateOf(school.email) }
    var desc by remember { mutableStateOf(school.description) }
    var year by remember { mutableStateOf(school.establishedYear?.toString().orEmpty()) }
    var website by remember { mutableStateOf(school.website.orEmpty()) }
    var logoUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }
    var clearLogo by remember { mutableStateOf(false) }
    var clearBanner by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) {
            logoUri = it
            clearLogo = false
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) {
            bannerUri = it
            clearBanner = false
        }
    }

    val schoolTypes = listOf("Private", "Government", "Other")
    val hasLogo = logoUri != null || (!clearLogo && !school.logoUrl.isNullOrBlank())
    val hasBanner = bannerUri != null || (!clearBanner && !school.bannerUrl.isNullOrBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit School Details") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Logo & Banner previews + actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (logoUri != null) {
                        AsyncImage(
                            model = logoUri,
                            contentDescription = "Selected School Logo",
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        SchoolAvatar(
                            logoUrl = if (clearLogo) null else school.logoUrl,
                            schoolName = name.ifBlank { school.name },
                            size = 48.dp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().testTag("edit_school_logo_button")
                        ) {
                            Text(if (logoUri != null) "Logo Selected" else "Change Logo")
                        }
                        if (hasLogo) {
                            TextButton(
                                onClick = {
                                    logoUri = null
                                    clearLogo = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Remove Logo", color = AbsentRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.width(84.dp).height(48.dp).clip(RoundedCornerShape(10.dp))) {
                        when {
                            bannerUri != null -> AsyncImage(
                                model = bannerUri,
                                contentDescription = "Selected School Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            !clearBanner && !school.bannerUrl.isNullOrBlank() -> AsyncImage(
                                model = school.bannerUrl,
                                contentDescription = "Current School Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            else -> Image(
                                painter = painterResource(id = R.drawable.img_default_school_banner),
                                contentDescription = "Default Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().testTag("edit_school_banner_picker_button")
                        ) {
                            Text(if (bannerUri != null) "Banner Selected" else "Change Banner")
                        }
                        if (hasBanner) {
                            TextButton(
                                onClick = {
                                    bannerUri = null
                                    clearBanner = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset Banner", color = AbsentRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("School Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    schoolTypes.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option) },
                            modifier = Modifier.testTag("edit_school_type_${option.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("School Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_name_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_address_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; validationError = null },
                    label = { Text("City *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_city_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; validationError = null },
                    label = { Text("Contact Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_phone_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    label = { Text("Email *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_email_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4); validationError = null },
                    label = { Text("Established Year (1800-2100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_year_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_website_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_desc_input")
                )

                if (validationError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val parsedYear = year.toIntOrNull()
                    when {
                        trimmedName.length < 2 -> validationError = "School Name must be at least 2 characters."
                        city.isBlank() -> validationError = "City is required."
                        phone.isBlank() -> validationError = "Contact Number is required."
                        email.isBlank() -> validationError = "Email is required."
                        year.isNotBlank() && (parsedYear == null || parsedYear !in 1800..2100) ->
                            validationError = "Established Year must be between 1800 and 2100."
                        else -> onSave(
                            trimmedName, type, address, city, phone, email, desc,
                            parsedYear, website, logoUri, bannerUri, clearLogo, clearBanner
                        )
                    }
                },
                modifier = Modifier.testTag("edit_school_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_school_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAnnouncementDialog(
    sections: List<ClassSection>,
    canChooseScope: Boolean,
    defaultSection: ClassSection? = null,
    onDismiss: () -> Unit,
    onPublish: (String, ClassSection?, String, String, String, Uri?, Boolean) -> Unit
) {
    var scope by remember { mutableStateOf(if (defaultSection != null || !canChooseScope) "class" else "school") }
    var selectedSection by remember { mutableStateOf(defaultSection ?: sections.firstOrNull()) }
    var category by remember { mutableStateOf("Announcement") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var allowComments by remember { mutableStateOf(true) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) imageUri = it }

    val categories = listOf("Announcement", "News", "Event", "Admission Notice", "Exam Schedule", "Holiday Notice", "Homework")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Post") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (canChooseScope) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = scope == "school", onClick = { scope = "school" }, label = { Text("School-wide") })
                        FilterChip(selected = scope == "class", onClick = { scope = "class" }, label = { Text("Specific Class") })
                    }
                }
                if (scope == "class" && sections.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Section", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sections.forEach { sec ->
                            FilterChip(
                                selected = selectedSection?.id == sec.id,
                                onClick = { selectedSection = sec },
                                label = { Text("Sec ${sec.name}") }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Post Title *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Details *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (imageUri == null) "Attach Photo (Optional)" else "Photo Attached")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allow Comments", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = allowComments, onCheckedChange = { allowComments = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onPublish(scope, if (scope == "class") selectedSection else null, category, title, body, imageUri, allowComments)
            }) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
