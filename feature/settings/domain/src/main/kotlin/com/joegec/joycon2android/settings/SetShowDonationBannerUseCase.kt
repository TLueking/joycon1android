package com.joegec.joycon2android.settings

class SetShowDonationBannerUseCase(private val repository: DonationBannerRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setShowDonationBanner(enabled)
}
