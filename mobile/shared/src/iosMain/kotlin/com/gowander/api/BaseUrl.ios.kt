package com.gowander.api

// iOS simulator shares the Mac's network (ADR-029 §3). shortcut: simulator only, physical devices need HTTPS + a server (later milestone).
actual val apiBaseUrl: String = "http://127.0.0.1:8090"
