package com.example.decegestin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import android.view.Menu
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.example.decegestin.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var pendingWidgetIntent: android.content.Intent? = null
    
    private var notifListener: ChildEventListener? = null
    private var currentNotifRef: DatabaseReference? = null
    private val appStartTime = System.currentTimeMillis() - 10000

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            tryConsumePendingWidgetIntent()
            subscribeToInstitutionalTopic()
            setupInstitutionalNotificationListener()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Solicitar permisos de notificación en Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (!isGranted) {
                    Toast.makeText(this, "Permiso de notificaciones denegado. No recibirá recordatorios.", Toast.LENGTH_LONG).show()
                }
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Verificar permiso de alarmas exactas en Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // Suscribir a tópicos institucionales para notificaciones FCM
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            subscribeToInstitutionalTopic()
            setupInstitutionalNotificationListener()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                0, // No aplicar padding arriba para que el fragmento cubra la barra de estado
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Setup Drawer with NavigationUI
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.FirstFragment, R.id.SecondFragment),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // Manejar clic en el encabezado del drawer para volver al Dashboard
        val header = binding.navView.getHeaderView(0)
        header.findViewById<View>(R.id.nav_header_container)?.setOnClickListener {
            val currentId = navController.currentDestination?.id
            if (currentId != R.id.SecondFragment && 
                currentId != R.id.FirstFragment &&
                currentId != R.id.RegisterFragment) {
                
                // Intentar volver al Dashboard si está en el historial
                if (!navController.popBackStack(R.id.SecondFragment, false)) {
                    navController.navigate(R.id.SecondFragment)
                }
            }
            binding.drawerLayout.closeDrawers()
        }

        // Manejar clics específicos del menú que no son navegación directa
        binding.navView.setNavigationItemSelectedListener { item ->
            val result = when (item.itemId) {
                R.id.BitacoraFragment -> {
                    navController.navigate(R.id.BitacoraFragment)
                    true
                }
                R.id.EducandoFragment -> {
                    navController.navigate(R.id.EducandoFragment)
                    true
                }
                R.id.menu_history -> {
                    navController.navigate(R.id.HistoryFragment)
                    true
                }
                R.id.SexualViolenceDbFragment -> {
                    navController.navigate(R.id.SexualViolenceDbFragment)
                    true
                }
                R.id.menu_new_form -> {
                    navController.navigate(R.id.NewFormFragment)
                    true
                }
                R.id.DocsFragment -> {
                    navController.navigate(R.id.DocsFragment)
                    true
                }
                R.id.NotificationManagerFragment -> {
                    navController.navigate(R.id.NotificationManagerFragment)
                    true
                }
                R.id.menu_sync_now -> {
                    showToast(getString(R.string.sync_cloud))
                    DashboardWidget.notifyUpdate(this)
                    true
                }
                R.id.menu_offline_mode -> {
                    showToast(getString(R.string.offline_active))
                    true
                }
                R.id.menu_update_app -> {
                    checkForUpdates()
                    true
                }
                else -> {
                    // Para los "En desarrollo", mostrar un aviso
                    val title = item.title.toString()
                    if (title.contains("(Desarrollo)") || title.contains("Desarrollo")) {
                        showToast(getString(R.string.module_in_dev))
                    }
                    false
                }
            }
            if (result) binding.drawerLayout.closeDrawers()
            result
        }

        // Manejar navegación desde Widget con una pequeña demora para asegurar que el NavController esté listo
        binding.root.post {
            handleIntent(intent)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Bloquear el menú lateral en pantallas de inicio/registro
            if (destination.id == R.id.FirstFragment || destination.id == R.id.RegisterFragment) {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                // Actualizar info del encabezado
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val header = binding.navView.getHeaderView(0)
                header.findViewById<android.widget.TextView>(R.id.header_email)?.text = user?.email ?: getString(R.string.no_session)
            }

            // Ocultar siempre la Action Bar por defecto ya que usamos Top Bars personalizadas en los XML
            supportActionBar?.hide()
            
            // Ocultar el FAB en la mayoría de las pantallas principales
            if (destination.id == R.id.FirstFragment || destination.id == R.id.SecondFragment || 
                destination.id == R.id.RegisterFragment || destination.id == R.id.EducandoFragment ||
                destination.id == R.id.PdfViewerFragment || destination.id == R.id.SexualViolenceDbFragment ||
                destination.id == R.id.SexualViolenceDetailFragment) {
                binding.fab.visibility = android.view.View.GONE
            } else {
                binding.fab.visibility = android.view.View.VISIBLE
            }

            // Intentar consumir intent si llegamos al Dashboard y hay algo pendiente
            if (destination.id == R.id.SecondFragment) {
                tryConsumePendingWidgetIntent()
            }
        }

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.module_in_dev), Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
    }

    override fun onStart() {
        super.onStart()
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    override fun onStop() {
        super.onStop()
        com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        notifListener?.let { currentNotifRef?.removeEventListener(it) }
    }

    private fun setupInstitutionalNotificationListener() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance().reference

        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            val inst = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeInstKey = AppUtils.getSafeKey(inst)

            notifListener?.let { currentNotifRef?.removeEventListener(it) }
            currentNotifRef = database.child("institutions").child(safeInstKey).child("notifications")

            notifListener = currentNotifRef?.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val createdBy = snapshot.child("createdBy").value?.toString()
                    val timestamp = snapshot.child("timestamp").value as? Long ?: 0L
                    val message = snapshot.child("message").value?.toString() ?: return

                    if (createdBy != user.uid && timestamp > appStartTime) {
                        showInstitutionalNotification(message)
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun showInstitutionalNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "dece_institutional_activity"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Actividad Institucional DECE", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Nueva Entrada Registrada")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder)
    }

    private fun checkForUpdates() {
        showToast(getString(R.string.searching_updates))
        val database = FirebaseDatabase.getInstance().reference

        database.child("app_config").child("update").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                showToast("Tu aplicación está actualizada (v${BuildConfig.VERSION_NAME})")
                return@addOnSuccessListener
            }

            val latestVersionCode = snapshot.child("versionCode").value?.toString()?.toIntOrNull() ?: 1
            val latestVersionName = snapshot.child("versionName").value?.toString() ?: "1.0"
            val apkUrl = snapshot.child("apkUrl").value?.toString() ?: ""
            val changelog = snapshot.child("changelog").value?.toString() ?: "Hay una nueva versión disponible con mejoras."

            if (latestVersionCode > BuildConfig.VERSION_CODE) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Nueva versión disponible ($latestVersionName)")
                    .setMessage(changelog)
                    .setPositiveButton("Descargar") { _, _ ->
                        if (apkUrl.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                            startActivity(intent)
                        } else {
                            showToast("Enlace de descarga no disponible")
                        }
                    }
                    .setNegativeButton("Más tarde", null)
                    .show()
            } else {
                showToast("Tu aplicación está actualizada (v${BuildConfig.VERSION_NAME})")
            }
        }.addOnFailureListener {
            showToast("Error al buscar actualizaciones")
        }
    }

    private fun subscribeToInstitutionalTopic() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance().reference

        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            val inst = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeInstKey = AppUtils.getSafeKey(inst)
            FirebaseMessaging.getInstance().subscribeToTopic("inst_$safeInstKey")
        }
    }

    // Permitir abrir el drawer desde los fragmentos
    fun openDrawer() {
        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
    }


    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent) 
        pendingWidgetIntent = intent
        tryConsumePendingWidgetIntent()
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.hasExtra("navigate_to") == true) {
            pendingWidgetIntent = intent
            tryConsumePendingWidgetIntent()
        }
    }

    /**
     * Intenta consumir el intent del widget si el usuario está logueado y el NavController está listo.
     * @return true si se consumió y navegó, false en caso contrario.
     */
    fun tryConsumePendingWidgetIntent(): Boolean {
        val intent = pendingWidgetIntent ?: return false
        val navigateTo = intent.getStringExtra("navigate_to") ?: return false

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment ?: return false
        val navController = navHostFragment.navController

        // Si el usuario no está logueado, no navegamos aún
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return false

        // Limpiar para evitar doble ejecución
        pendingWidgetIntent = null

        if (navigateTo == "DetailFragment") {
            val title = intent.getStringExtra("title")
            val institution = intent.getStringExtra("institution")
            val bundle = Bundle().apply {
                putString("title", title)
                putString("institution", institution)
            }
            try {
                // Si no estamos en el Dashboard, vamos primero allá para asegurar el stack
                if (navController.currentDestination?.id != R.id.SecondFragment) {
                    navController.navigate(R.id.SecondFragment)
                    pendingWidgetIntent = intent // Guardar para el siguiente paso
                    return true
                }
                navController.navigate(R.id.DetailFragment, bundle)
                return true
            } catch (e: Exception) {
                pendingWidgetIntent = intent 
            }
        } else if (navigateTo == "BitacoraFragment") {
            try {
                if (navController.currentDestination?.id != R.id.SecondFragment &&
                    navController.currentDestination?.id != R.id.BitacoraFragment) {
                    navController.navigate(R.id.SecondFragment)
                    pendingWidgetIntent = intent
                    return true
                }
                navController.navigate(R.id.BitacoraFragment)
                return true
            } catch (e: Exception) {
                pendingWidgetIntent = intent
            }
        } else if (navigateTo == "DocsFragment") {
            try {
                if (navController.currentDestination?.id != R.id.SecondFragment &&
                    navController.currentDestination?.id != R.id.DocsFragment) {
                    navController.navigate(R.id.SecondFragment)
                    pendingWidgetIntent = intent
                    return true
                }
                navController.navigate(R.id.DocsFragment)
                return true
            } catch (e: Exception) {
                pendingWidgetIntent = intent
            }
        } else if (navigateTo == "NewFormFragment") {
            try {
                if (navController.currentDestination?.id != R.id.SecondFragment &&
                    navController.currentDestination?.id != R.id.NewFormFragment) {
                    navController.navigate(R.id.SecondFragment)
                    pendingWidgetIntent = intent
                    return true
                }
                navController.navigate(R.id.NewFormFragment)
                return true
            } catch (e: Exception) {
                pendingWidgetIntent = intent
            }
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}