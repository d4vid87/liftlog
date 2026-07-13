package dev.dwm.liftlog.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClient(): HttpClient = HttpClient(OkHttp)
