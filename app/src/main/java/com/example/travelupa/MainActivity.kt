package com.example.travelupa

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil.compose.rememberAsyncImagePainter
import com.example.travelupa.ui.theme.TravelupaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")
    object Login : Screen("login")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "travelupa-database"
        ).build()

        val imageDao = db.imageDao()
        val currentUser = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
                    AppNavigation(currentUser = currentUser, imageDao = imageDao)
                }
            }
        }
    }
}

data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    val gambarResId: Int? = null
)

@Composable
fun AppNavigation(currentUser: FirebaseUser?, imageDao: ImageDao) {
    val navController = rememberNavController()
    val startDestination = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Greeting.route) {
            GreetingScreen(
                onStart = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Greeting.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                imageDao = imageDao,
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Greeting.route) {
                        popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun GreetingScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(
                text = "Selamat Datang di Travelupa!",
                style = MaterialTheme.typography.h4,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Solusi buat kamu yang lupa kemana-mana",
                style = MaterialTheme.typography.h6,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Button(
            onClick = onStart,
            modifier = Modifier.width(360.dp).align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Text(text = "Mulai")
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Login", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                        }
                        isLoading = false
                        onLoginSuccess()
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = "Login failed: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Login")
        }
        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun RekomendasiTempatScreen(onBackToLogin: () -> Unit, imageDao: ImageDao) {
    val firestore = FirebaseFirestore.getInstance()
    var daftarTempatWisata by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showTambahDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        firestore.collection("tempat_wisata")
            .get()
            .addOnSuccessListener { result ->
                val items = result.toObjects(TempatWisata::class.java)
                daftarTempatWisata = items
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Travelupa") },
                actions = {
                    IconButton(onClick = onBackToLogin) {
                        Icon(painter = painterResource(id = android.R.drawable.ic_lock_power_off), contentDescription = "Logout", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTambahDialog = true },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Tempat Wisata")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            LazyColumn {
                items(daftarTempatWisata) { tempat ->
                    TempatItemEditable(
                        tempat = tempat,
                        onDelete = {
                            firestore.collection("tempat_wisata").document(tempat.nama).delete()
                                .addOnSuccessListener {
                                    daftarTempatWisata = daftarTempatWisata.filter { it.nama != tempat.nama }
                                    Toast.makeText(context, "Data dihapus", Toast.LENGTH_SHORT).show()
                                }
                        }
                    )
                }
            }
        }

        if (showTambahDialog) {
            TambahTempatWisataDialog(
                firestore = firestore,
                imageDao = imageDao,
                onDismiss = { showTambahDialog = false },
                onTambah = { nama, deskripsi, localPath ->
                    val newPlace = TempatWisata(nama, deskripsi, localPath)
                    daftarTempatWisata = daftarTempatWisata + newPlace
                    showTambahDialog = false
                }
            )
        }
    }
}

@Composable
fun TempatItemEditable(tempat: TempatWisata, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val painter = if (tempat.gambarUriString != null) {
                rememberAsyncImagePainter(File(tempat.gambarUriString))
            } else if (tempat.gambarResId != null) {
                painterResource(id = tempat.gambarResId)
            } else {
                painterResource(id = android.R.drawable.ic_menu_gallery)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painter,
                    contentDescription = tempat.nama,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )

                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        offset = DpOffset((-40).dp, 0.dp)
                    ) {
                        DropdownMenuItem(onClick = { expanded = false; onDelete() }) {
                            Text("Delete")
                        }
                    }
                }
            }

            Text(text = tempat.nama, style = MaterialTheme.typography.h6, modifier = Modifier.padding(bottom = 8.dp, top = 12.dp))
            Text(text = tempat.deskripsi, style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun TambahTempatWisataDialog(
    firestore: FirebaseFirestore,
    imageDao: ImageDao,
    onDismiss: () -> Unit,
    onTambah: (String, String, String?) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val gambarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> gambarUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Tempat Wisata Baru") },
        text = {
            Column {
                TextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Tempat") }, modifier = Modifier.fillMaxWidth(), enabled = !isUploading)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = deskripsi, onValueChange = { deskripsi = it }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(), enabled = !isUploading)
                Spacer(modifier = Modifier.height(8.dp))

                gambarUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { gambarLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !isUploading) {
                    Text("Pilih Gambar")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isNotBlank() && deskripsi.isNotBlank() && gambarUri != null) {
                        isUploading = true

                        coroutineScope.launch {
                            try {
                                val localPath = saveImageLocally(context, gambarUri!!)
                                imageDao.insert(ImageEntity(localPath = localPath, tempatWisataId = nama))

                                val newPlace = TempatWisata(nama, deskripsi, localPath)

                                firestore.collection("tempat_wisata").document(nama)
                                    .set(newPlace)
                                    .await()

                                isUploading = false
                                onTambah(nama, deskripsi, localPath)
                                Toast.makeText(context, "Berhasil disimpan", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                isUploading = false
                                Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Tambah")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface), enabled = !isUploading) {
                Text("Batal")
            }
        }
    )
}

fun saveImageLocally(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "image_${System.currentTimeMillis()}.jpg")
    val outputStream = FileOutputStream(file)

    inputStream?.use { input ->
        outputStream.use { output ->
            input.copyTo(output)
        }
    }
    return file.absolutePath
}