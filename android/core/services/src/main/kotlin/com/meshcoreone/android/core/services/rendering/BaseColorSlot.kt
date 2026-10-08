// PortedFrom: MC1Services/Sources/MC1Services/Models/Rendering/BaseColorSlot.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

/**
 * Direction-tagged colour slot for chat-message text. The view layer resolves [OUTGOING] to the
 * on-bubble text colour and [INCOMING] to the primary text colour at render time, so rendering
 * models stay free of UI colour types.
 */
enum class BaseColorSlot { OUTGOING, INCOMING }
