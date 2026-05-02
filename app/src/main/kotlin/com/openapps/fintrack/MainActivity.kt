package com.openapps.fintrack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openapps.fintrack.ui.*
import com.openapps.fintrack.ui.theme.ExpTrackerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val appLockEnabledOnStart = prefs.getBoolean("app_lock_enabled", false)

        setContent {
            var isUnlocked by remember { mutableStateOf(!appLockEnabledOnStart) }
            val themeToUse = remember { prefs.getString("theme", "Dark") ?: "Dark" }

            ExpTrackerTheme(theme = themeToUse) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isUnlocked) {
                        val viewModel: ExpenseViewModel = viewModel()
                        ExpTrackerApp(
                            viewModel = viewModel, 
                            extras = intent.extras,
                            onRequireAuth = { onAuthSuccess ->
                                authenticate(onAuthSuccess)
                            }
                        )
                    } else {
                        LockOverlay {
                            authenticate {
                                isUnlocked = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, errString, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FinTrack Secure")
            .setSubtitle("Confirm your identity")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            onSuccess()
        }
    }
}

@Composable
fun LockOverlay(onRetry: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Button(onClick = onRetry) {
            androidx.compose.material3.Text("Unlock App")
        }
    }
    LaunchedEffect(Unit) {
        onRetry()
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

    NavHost(navController = navController, startDestination = initialRoute) {
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
                initialData = extras
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
