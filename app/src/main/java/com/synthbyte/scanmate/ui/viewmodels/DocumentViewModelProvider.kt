package com.synthbyte.scanmate.ui.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synthbyte.scanmate.data.AppDatabase

@Composable
fun rememberDocumentViewModel(): DocumentViewModel {
    val context = LocalContext.current.applicationContext
    val dao = remember { AppDatabase.getDatabase(context).docDao() }
    return viewModel(factory = DocumentViewModelFactory(dao, context))
}
