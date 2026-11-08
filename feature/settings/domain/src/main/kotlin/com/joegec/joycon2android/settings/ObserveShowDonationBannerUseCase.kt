package com.joegec.joycon2android.settings

import kotlinx.coroutines.flow.Flow

class ObserveShowDonationBannerUseCase(private val repository: DonationBannerRepository) {
    operator fun invoke(): Flow<Boolean> = repository.showDonationBanner
}
