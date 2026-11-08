package com.joegec.joycon2android.settings

import kotlinx.coroutines.flow.Flow

interface DonationBannerRepository {
    val showDonationBanner: Flow<Boolean>
    suspend fun setShowDonationBanner(enabled: Boolean)
}
