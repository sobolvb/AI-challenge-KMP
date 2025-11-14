package local.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import local.di.ServiceLocator
import local.ui.UpdatedMainScreen
import local.viewmodel.ReasoningViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: ReasoningViewModel by viewModels {
        ServiceLocator.getViewModel()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                UpdatedMainScreen(viewModel = viewModel)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ServiceLocator.cleanup()
    }
}