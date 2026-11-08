package com.joegec.joycon2android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.donationBannerDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "donation_banner_preferences")

class DonationBannerDataStore(context: Context) : DonationBannerRepository {

    private val dataStore = context.applicationContext.donationBannerDataStore

    override val showDonationBanner: Flow<Boolean> = dataStore.data.map { it[SHOW_DONATION_BANNER] ?: true }

    override suspend fun setShowDonationBanner(enabled: Boolean) {
        dataStore.edit { it[SHOW_DONATION_BANNER] = enabled }
    }

    private companion object {
        val SHOW_DONATION_BANNER = booleanPreferencesKey("show_donation_banner")
    }
}
