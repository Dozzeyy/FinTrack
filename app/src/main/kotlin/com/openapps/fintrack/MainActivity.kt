/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openapps.fintrack.ui.*
import com.openapps.fintrack.ui.theme.ExpTrackerTheme

class MainActivity : FragmentActivity() {
    private var lockHandler = Handler(Looper.getMainLooper())
    private lateinit var viewModel: ExpenseViewModel
    private val isLockedState = mutableStateOf(false)
    private var lastInteractionTime: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]
        
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val appLockEnabledOnStart = prefs.getBoolean("app_lock_enabled", false)
        
        // Always lock on app restart if lock is enabled
        isLockedState.value = appLockEnabledOnStart

        setContent {
            val isLocked by isLockedState
            val themeToUse by remember { mutableStateOf(prefs.getString("theme", "Dark") ?: "Dark") }

            ExpTrackerTheme(theme = themeToUse) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLocked) {
                        ExpTrackerApp(
                            viewModel = viewModel, 
                            extras = intent.extras,
                            onRequireAuth = { onAuthSuccess ->
                                authenticate(onAuthSuccess)
                            }
                        )
                    } else {
                        UnlockScreen(
                            onUnlock = { isLockedState.value = false }
                        )
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionTime = System.currentTimeMillis()
        resetLockTimer()
    }

    override fun onResume() {
        super.onResume()
        val elapsed = System.currentTimeMillis() - lastInteractionTime
        val timeoutStr = viewModel.inactivityTimeout
        val timeoutMillis = getTimeoutMillis(timeoutStr)
        
        if (viewModel.appLockEnabled && timeoutStr != "keep unlocked until app closure" && elapsed > timeoutMillis) {
            isLockedState.value = true
        }
        resetLockTimer()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            .putLong("last_exit_time", System.currentTimeMillis())
            .apply()
    }

    private fun getTimeoutMillis(timeoutStr: String): Long {
        return when (timeoutStr) {
            "5 seconds" -> 5_000L
            "15 seconds" -> 15_000L
            "30 seconds" -> 30_000L
            "1 minute" -> 60_000L
            "2 minute" -> 120_000L
            "5 minute" -> 300_000L
            "10 minute" -> 600_000L
            "30 minutes" -> 1_800_000L
            else -> 30_000L
        }
    }

    private fun resetLockTimer() {
        if (!::viewModel.isInitialized) return
        lockHandler.removeCallbacksAndMessages(null)
        val timeoutStr = viewModel.inactivityTimeout
        if (timeoutStr == "keep unlocked until app closure") return

        val millis = getTimeoutMillis(timeoutStr)

        lockHandler.postDelayed({
            val now = System.currentTimeMillis()
            if (viewModel.appLockEnabled && (now - lastInteractionTime >= millis)) {
                isLockedState.value = true
            }
        }, millis)
    }

    fun authenticate(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricManager = BiometricManager.from(this)
        
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                             BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        val canAuth = biometricManager.canAuthenticate(authenticators)
        
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                applicationContext, 
                "Secure unlock unavailable. Please check device security settings.", 
                Toast.LENGTH_LONG
            ).show()
            return 
        }

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FinTrack Secure")
            .setSubtitle("Confirm your identity")
            .setAllowedAuthenticators(authenticators)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(applicationContext, "Authentication error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun UnlockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FinTrack Locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Confirm identity to continue.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { 
            (context as MainActivity).authenticate { onUnlock() }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Unlock")
        }
        
        LaunchedEffect(Unit) {
            (context as MainActivity).authenticate { onUnlock() }
        }
    }
}

@Composable
fun ExpTrackerApp(
    viewModel: ExpenseViewModel, 
    startRoute: String = "home", 
    extras: Bundle? = null,
    onRequireAuth: (() -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isSetupComplete = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("setup_complete", false)
    }
    
    val initialRoute = if (isSetupComplete) startRoute else "setup"

    LaunchedEffect(extras) {
        val navigateTo = extras?.getString("navigate_to")
        if (navigateTo != null && isSetupComplete) {
            navController.navigate(navigateTo)
        }
    }

    NavHost(
        navController = navController, 
        startDestination = initialRoute,
        enterTransition = { fadeIn() + slideInHorizontally { it } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it } },
        popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
        popExitTransition = { fadeOut() + slideOutHorizontally { it } }
    ) {
        composable("setup") {
            SetupScreen(onComplete = {
                navController.navigate("home") {
                    popUpTo("setup") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigate = { route -> 
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("add_transaction") {
            AddTransactionScreen(
                viewModel = viewModel, 
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
                initialData = extras
            )
        }
        composable("add_transaction_template") {
            val bundle = Bundle().apply { putBoolean("template_mode", true) }
            AddTransactionScreen(
                viewModel = viewModel, 
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
                initialData = bundle
            )
        }
        composable("templates") {
            TemplatesScreen(
                viewModel = viewModel,
                onNavigate = { route, _ -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("add_category") {
            AddCategoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("manage_categories") {
            ManageCategoriesScreen(
                viewModel = viewModel,
                onEditCategory = { navController.navigate("add_category") },
                onEditAccount = { navController.navigate("add_category") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("tags_main") {
            TagsMainScreen(
                onNavigate = { navController.navigate(it) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("add_tag") {
            AddTagScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("manage_tags") {
            ManageTagsScreen(
                viewModel = viewModel,
                onEditTag = { navController.navigate("add_tag") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("summary_by_tags") {
            TagSummaryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("summary") {
            SummaryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onRequireAuth = onRequireAuth)
        }
        composable("permissions") {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable("database") {
            DatabaseScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("contact") {
            ContactScreen(onBack = { navController.popBackStack() })
        }
        composable("budgets_main") {
            BudgetsMainScreen(
                onNavigate = { navController.navigate(it) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("manage_budgets") {
            ManageBudgetsScreen(
                viewModel = viewModel,
                onEditBudget = { navController.navigate("add_budget") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("add_budget") {
            AddBudgetScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("budget_vs_actual") {
            BudgetComparisonScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
