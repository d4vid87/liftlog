package dev.dwm.liftlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> Flow<List<T>>.collectAsStateList(): State<List<T>> = collectAsState(initial = emptyList())
