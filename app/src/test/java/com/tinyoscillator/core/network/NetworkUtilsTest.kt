package com.tinyoscillator.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * NetworkUtils unit tests.
 *
 * Verifies connectivity check behavior in various network conditions.
 */
class NetworkUtilsTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var capabilities: NetworkCapabilities

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        network = mockk()
        capabilities = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    @Test
    fun `네트워크 사용 가능 시 true 반환`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        assertTrue(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `activeNetwork가 null이면 false 반환`() {
        every { connectivityManager.activeNetwork } returns null

        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `capabilities가 null이면 false 반환`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `INTERNET capability 없으면 false 반환`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `VALIDATED capability 없으면 false 반환`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `ConnectivityManager가 null이면 true 반환 (fail-open)`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null

        assertTrue(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun `두 capability 모두 없으면 false 반환`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }
}
