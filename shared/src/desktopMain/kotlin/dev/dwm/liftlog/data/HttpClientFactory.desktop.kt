package dev.dwm.liftlog.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun httpClient(): HttpClient = HttpClient(CIO)
