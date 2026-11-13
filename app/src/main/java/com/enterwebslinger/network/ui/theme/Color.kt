// === FILE: app/src/main/java/com/enterwebslinger/network/ui/theme/Color.kt ===
package com.enterwebslinger.network.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Accents
val CyberCyan = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF0284C7)
val DeepNavy = Color(0xFF080D1A)
val CardBackground = Color(0xFF0F172A)
val CardBackgroundElevated = Color(0xFF1E293B)
val BorderStrokeColor = Color(0xFF334155)
val DividerColor = BorderStrokeColor

// Semantic Indicators
val StatusEmerald = Color(0xFF10B981) // Healthy, Open, Connected
val StatusAmber = Color(0xFFF59E0B)   // High Latency, Warning, Filtered
val StatusCrimson = Color(0xFFEF4444) // Offline, Closed, Packet Loss
val StatusPurple = Color(0xFFA855F7)  // UPnP, mDNS, Discovery

val StatusConnected = StatusEmerald
val StatusWarning = StatusAmber
val StatusError = StatusCrimson

// Text & Surfaces
val DarkBackground = Color(0xFF080C16)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkOutline = Color(0xFF334155)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Chart / Spectrum Colors
val Band24G = Color(0xFF38BDF8)
val Band5G = Color(0xFF10B981)
val Band6G = Color(0xFFA855F7)
