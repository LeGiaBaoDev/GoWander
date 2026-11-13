package com.gowander.api

// Android emulator reaches the Mac's 127.0.0.1 as 10.0.2.2 (ADR-029 §3). shortcut: emulator only, physical devices need HTTPS + a server (later milestone).
actual val apiBaseUrl: String = "http://10.0.2.2:8090"
