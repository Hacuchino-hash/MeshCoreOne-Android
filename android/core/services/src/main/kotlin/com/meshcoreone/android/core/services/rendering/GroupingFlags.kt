// PortedFrom: MC1Services/Sources/MC1Services/Models/Rendering/GroupingFlags.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

/** Grouping signal — first-in-cluster, show timestamp, show divider. */
data class GroupingFlags(
    val showTimestamp: Boolean,
    val showDirectionGap: Boolean,
    val showSenderName: Boolean,
    val showNewMessagesDivider: Boolean,
    /** True for the first message of a new calendar day; drives the day separator. */
    val showDayDivider: Boolean = false,
)
