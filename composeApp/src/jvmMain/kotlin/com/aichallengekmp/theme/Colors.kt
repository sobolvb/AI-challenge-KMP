package com.aichallengekmp.theme

import androidx.compose.ui.graphics.Color


// Ozon brand
val OzonBlue = Color(0xFF005BFF)      // Ozon blue, brand primary. :contentReference[oaicite:1]{index=1}
val OzonMagenta = Color(0xFFF91155)   // Ozon magenta (accent). :contentReference[oaicite:2]{index=2}

// Supporting / utility
val OzonMorningBlue = Color(0xFF00A2FF) // lighter accent (example from guidelines). :contentReference[oaicite:3]{index=3}
val OzonSurface = Color(0xFFF2F7FF)    // very light surface (brand-bg tint). :contentReference[oaicite:4]{index=4}
val OzonNight = Color(0xFF091E89)      // deep/dark variant for dark theme

// Chat-specific colours
val IncomingBubble = Color(0xFFFFFFFF)        // white for incoming (light theme)
val OutgoingBubble = OzonBlue                  // outgoing uses Ozon blue
val OutgoingBubbleText = Color(0xFFFFFFFF)
val IncomingBubbleText = Color(0xFF1F2937)     // dark text on incoming bubble

// Semantic neutrals
val Neutral10 = Color(0xFFF7F9FB)
val Neutral20 = Color(0xFFEDF2FA)
val Neutral60 = Color(0xFF6B7280)