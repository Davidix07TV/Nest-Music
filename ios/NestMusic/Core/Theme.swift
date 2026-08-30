//
//  Theme.swift
//  Nest Music
//
//  Material 3-style color scheme seeded from Nest Music's coral brand color
//  (#ED5564), with light / dark / pure-black variants to match the Android app.
//

import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}

/// Nest Music's signature coral seed color (Android: DefaultThemeColor = 0xFFED5564).
enum NestPalette {
    static let seed = Color(hex: 0xED5564)

    // Light scheme (Material 3 tonal palette generated from the coral seed).
    static let light = NestColors(
        primary: Color(hex: 0x8F4950),
        onPrimary: .white,
        primaryContainer: Color(hex: 0xFFDADC),
        onPrimaryContainer: Color(hex: 0x3B0710),
        secondary: Color(hex: 0x775656),
        onSecondary: .white,
        background: Color(hex: 0xFFF8F7),
        onBackground: Color(hex: 0x22191A),
        surface: Color(hex: 0xFFF8F7),
        onSurface: Color(hex: 0x22191A),
        surfaceContainer: Color(hex: 0xF5DEDF),
        surfaceContainerHigh: Color(hex: 0xEFD9DA),
        outline: Color(hex: 0x857374),
        isDark: false
    )

    // Dark scheme.
    static let dark = NestColors(
        primary: Color(hex: 0xFFB3B8),
        onPrimary: Color(hex: 0x561D25),
        primaryContainer: Color(hex: 0x723339),
        onPrimaryContainer: Color(hex: 0xFFDADC),
        secondary: Color(hex: 0xE6BDBD),
        onSecondary: Color(hex: 0x442929),
        background: Color(hex: 0x1A1112),
        onBackground: Color(hex: 0xF0DEDF),
        surface: Color(hex: 0x1A1112),
        onSurface: Color(hex: 0xF0DEDF),
        surfaceContainer: Color(hex: 0x251719),
        surfaceContainerHigh: Color(hex: 0x2A1B1D),
        outline: Color(hex: 0xA08C8D),
        isDark: true
    )

    // Pure-black dark scheme (matches Android's "pure black" option).
    static let pureBlack = NestColors(
        primary: Color(hex: 0xFFB3B8),
        onPrimary: Color(hex: 0x561D25),
        primaryContainer: Color(hex: 0x723339),
        onPrimaryContainer: Color(hex: 0xFFDADC),
        secondary: Color(hex: 0xE6BDBD),
        onSecondary: Color(hex: 0x442929),
        background: .black,
        onBackground: Color(hex: 0xF0DEDF),
        surface: .black,
        onSurface: Color(hex: 0xF0DEDF),
        surfaceContainer: Color(hex: 0x141414),
        surfaceContainerHigh: Color(hex: 0x1C1C1C),
        outline: Color(hex: 0x8E8E8E),
        isDark: true
    )
}

struct NestColors {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let secondary: Color
    let onSecondary: Color
    let background: Color
    let onBackground: Color
    let surface: Color
    let onSurface: Color
    let surfaceContainer: Color
    let surfaceContainerHigh: Color
    let outline: Color
    let isDark: Bool
}

private struct NestColorsKey: EnvironmentKey {
    static let defaultValue: NestColors = NestPalette.light
}

extension EnvironmentValues {
    var nestColors: NestColors {
        get { self[NestColorsKey.self] }
        set { self[NestColorsKey.self] = newValue }
    }
}

/// Resolve the palette for a given color scheme.
func nestColors(for colorScheme: ColorScheme, pureBlack: Bool = false) -> NestColors {
    if colorScheme == .dark { return pureBlack ? NestPalette.pureBlack : NestPalette.dark }
    return NestPalette.light
}

extension View {
    /// Applies the Nest Music palette. Driven by the ambient color scheme so the
    /// app follows light/dark automatically.
    func nestTheme(pureBlack: Bool = false) -> some View {
        modifier(NestThemeModifier(pureBlack: pureBlack))
    }
}

private struct NestThemeModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let pureBlack: Bool

    func body(content: Content) -> some View {
        content.environment(\.nestColors, nestColors(for: colorScheme, pureBlack: pureBlack))
    }
}
