package com.example.decegestin

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
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

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var pendingWidgetIntent: android.content.Intent? = null
    
    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            tryConsumePendingWidgetIntent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
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
                R.id.menu_history -> {
                    navController.navigate(R.id.HistoryFragment)
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
                    com.example.decegestin.DashboardWidget.notifyUpdate(this)
                    true
                }
                R.id.menu_offline_mode -> {
                    showToast(getString(R.string.offline_active))
                    true
                }
                R.id.menu_update_app -> {
                    showToast(getString(R.string.searching_updates))
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
            if (destination.id == R.id.FirstFragment || destination.id == R.id.SecondFragment || destination.id == R.id.RegisterFragment) {
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