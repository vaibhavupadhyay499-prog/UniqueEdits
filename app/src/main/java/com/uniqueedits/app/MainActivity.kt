package com.uniqueedits.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.TimeUnit

data class ServiceItem(
    val id: String,
    val title: String,
    val basePrice: Double,
    val description: String
)

data class ChatMsg(
    val senderId: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

object AppConfig {
    val ADMIN_PHONE_NUMBERS = listOf("+919876543210", "+919123456780")
    const val UPI_ID = "uniqueedits@upi"

    val ALL_SERVICES = listOf(
        ServiceItem("1", "Wedding Reels", 15000.0, "Cinematic wedding highlights & 4K editing"),
        ServiceItem("2", "Pre Wedding Reels", 10000.0, "Couple shoot & color grading"),
        ServiceItem("3", "Baby Shower Reels", 6000.0, "Candid emotional event capture"),
        ServiceItem("4", "Birthday Reels", 5000.0, "Party & celebration dynamic edits"),
        ServiceItem("5", "Car Delivery Reels", 4000.0, "Cinematic car reveal transitions"),
        ServiceItem("6", "Bike Delivery Reels", 3000.0, "Superbike delivery hype reels"),
        ServiceItem("7", "Event Reels", 7000.0, "Concerts, fests & corporate shoots"),
        ServiceItem("8", "Product Reels", 5000.0, "E-commerce & ad shoots"),
        ServiceItem("9", "Thumbnail Design", 500.0, "High CTR YouTube thumbnails"),
        ServiceItem("10", "Logo Design", 2000.0, "Vector branding assets"),
        ServiceItem("11", "Photo Editing", 1500.0, "Retouching & color grading"),
        ServiceItem("12", "Video Editing", 3500.0, "Full long-form video edits"),
        ServiceItem("13", "Raw Clips Upload", 1000.0, "Cloud storage & backup service"),
        ServiceItem("14", "Custom Booking", 2500.0, "Custom editing & shoot package")
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UniqueEditsApp()
                }
            }
        }
    }
}

@Composable
fun UniqueEditsApp() {
    var currentUserPhone by remember { mutableStateOf<String?>(null) }
    var selectedService by remember { mutableStateOf<ServiceItem?>(null) }
    var activeChatRoomId by remember { mutableStateOf<String?>(null) }

    if (currentUserPhone == null) {
        LoginAuthScreen(onAuthComplete = { phone -> currentUserPhone = phone })
    } else {
        val isAdmin = AppConfig.ADMIN_PHONE_NUMBERS.contains(currentUserPhone)

        when {
            activeChatRoomId != null -> {
                LiveChatScreen(
                    roomId = activeChatRoomId!!,
                    senderPhone = currentUserPhone!!,
                    onBack = { activeChatRoomId = null }
                )
            }
            selectedService != null -> {
                ServiceBookingScreen(
                    service = selectedService!!,
                    customerPhone = currentUserPhone!!,
                    onBack = { selectedService = null }
                )
            }
            isAdmin -> {
                AdminDashboard(
                    adminPhone = currentUserPhone!!,
                    onLogout = { currentUserPhone = null }
                )
            }
            else -> {
                CustomerDashboard(
                    onServiceSelect = { service -> selectedService = service },
                    onOpenSupportChat = { activeChatRoomId = "support_${currentUserPhone}" },
                    onLogout = { currentUserPhone = null }
                )
            }
        }
    }
}

@Composable
fun LoginAuthScreen(onAuthComplete: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val auth = remember { FirebaseAuth.getInstance() }

    var phoneInput by remember { mutableStateOf("+91") }
    var otpInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isCodeSent by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Unique Edits", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Video & Media Editing Studio", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        if (!isCodeSent) {
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Phone Number (+91...)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (phoneInput.length >= 10) {
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phoneInput)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                        if (task.isSuccessful) onAuthComplete(phoneInput)
                                    }
                                }

                                override fun onVerificationFailed(e: FirebaseException) {
                                    errorMsg = e.message ?: "Verification failed"
                                }

                                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                    verificationId = id
                                    isCodeSent = true
                                }
                            }).build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get OTP")
            }
        } else {
            OutlinedTextField(
                value = otpInput,
                onValueChange = { otpInput = it },
                label = { Text("Enter 6-Digit OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    verificationId?.let { id ->
                        val cred = PhoneAuthProvider.getCredential(id, otpInput)
                        auth.signInWithCredential(cred).addOnCompleteListener { task ->
                            if (task.isSuccessful) onAuthComplete(phoneInput) else errorMsg = "Invalid OTP"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify & Continue")
            }
        }

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMsg, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDashboard(
    onServiceSelect: (ServiceItem) -> Unit,
    onOpenSupportChat: () -> Unit,
    onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        AppConfig.ALL_SERVICES.filter { it.title.contains(search, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unique Edits") },
                actions = {
                    IconButton(onClick = onOpenSupportChat) {
                        Icon(Icons.Default.Email, contentDescription = "Support")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search services (Reels, Design, Editing...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { service ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onServiceSelect(service) },
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(service.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(service.description, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("₹${service.basePrice.toInt()}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceBookingScreen(
    service: ServiceItem,
    customerPhone: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var distanceKmText by remember { mutableStateOf("0") }

    val distanceKm = distanceKmText.toDoubleOrNull() ?: 0.0
    val travelCharge = if (distanceKm > 10.0) (distanceKm - 10.0) * 25.0 else 0.0
    val totalAmount = service.basePrice + travelCharge

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(service.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Package: ${service.title}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(service.description, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Base Price: ₹${service.basePrice.toInt()}", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Shoot Location Distance (in KM):", fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = distanceKmText,
                onValueChange = { distanceKmText = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Travel Policy: First 10 KM is Free! (₹25/KM charged afterwards)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Order Summary", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Base Charge:")
                        Text("₹${service.basePrice.toInt()}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Travel Fee:")
                        Text("₹${travelCharge.toInt()} (${if (distanceKm <= 10) "Free" else "${(distanceKm - 10).toInt()} extra km"})")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Amount:", fontWeight = FontWeight.Bold)
                        Text("₹${totalAmount.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val uri = Uri.parse("upi://pay").buildUpon()
                        .appendQueryParameter("pa", AppConfig.UPI_ID)
                        .appendQueryParameter("pn", "Unique Edits")
                        .appendQueryParameter("tn", "Booking: ${service.title}")
                        .appendQueryParameter("am", totalAmount.toString())
                        .appendQueryParameter("cu", "INR")
                        .build()

                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(Intent.createChooser(intent, "Pay using UPI"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "No UPI app found on device", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pay ₹${totalAmount.toInt()} via UPI")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    roomId: String,
    senderPhone: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var messageText by remember { mutableStateOf("") }
    var messageList by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }

    DisposableEffect(roomId) {
        val listener = db.collection("chats")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messageList = snapshot.toObjects(ChatMsg::class.java)
                }
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                items(messageList) { msg ->
                    val isMe = msg.senderId == senderPhone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isMe) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = msg.text, color = if (isMe) Color.White else Color.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type message...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val newMsg = ChatMsg(senderId = senderPhone, text = messageText)
                            db.collection("chats").document(roomId).collection("messages").add(newMsg)
                            messageText = ""
                        }
                    }
        
