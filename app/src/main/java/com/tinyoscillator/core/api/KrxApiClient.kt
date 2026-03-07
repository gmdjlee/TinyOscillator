package com.tinyoscillator.core.api

import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import com.krxkt.model.EtfInfo
import com.krxkt.model.EtfPortfolio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class KrxApiClient {

    private var krxClient: KrxClient? = null
    private var krxEtf: KrxEtf? = null
    private var krxIndex: KrxIndex? = null
    private var krxStock: KrxStock? = null
    private val mutex = Mutex()

    suspend fun login(id: String, pw: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val client = KrxClient()
                val success = client.login(id, pw)
                if (success) {
                    krxClient = client
                    krxEtf = KrxEtf(client)
                    krxIndex = KrxIndex(client)
                    krxStock = KrxStock(client)
                    Timber.d("KRX 로그인 성공")
                } else {
                    Timber.w("KRX 로그인 실패")
                }
                success
            } catch (e: Exception) {
                Timber.e(e, "KRX 로그인 에러")
                false
            }
        }
    }

    suspend fun getEtfTickerList(date: String): List<EtfInfo> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val etf = krxEtf ?: throw IllegalStateException("KRX 로그인이 필요합니다")
            etf.getEtfTickerList(date)
        }
    }

    suspend fun getPortfolio(date: String, ticker: String): List<EtfPortfolio> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val etf = krxEtf ?: throw IllegalStateException("KRX 로그인이 필요합니다")
            etf.getPortfolio(date, ticker)
        }
    }

    fun getKrxIndex(): KrxIndex? = krxIndex

    fun getKrxStock(): KrxStock? = krxStock

    fun close() {
        krxClient?.close()
        krxClient = null
        krxEtf = null
        krxIndex = null
        krxStock = null
    }
}
