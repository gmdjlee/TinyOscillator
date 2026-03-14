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
                // Clear previous state before attempting login
                closeInternal()

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
                    client.close()
                }
                success
            } catch (e: Exception) {
                Timber.e(e, "KRX 로그인 에러")
                false
            }
        }
    }

    suspend fun getEtfTickerList(date: String): Result<List<EtfInfo>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val etf = krxEtf
                ?: return@withContext Result.failure(IllegalStateException("KRX 로그인이 필요합니다"))
            try {
                Result.success(etf.getEtfTickerList(date))
            } catch (e: Exception) {
                Timber.e(e, "ETF 목록 조회 실패: $date")
                Result.failure(e)
            }
        }
    }

    suspend fun getPortfolio(date: String, ticker: String): Result<List<EtfPortfolio>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val etf = krxEtf
                ?: return@withContext Result.failure(IllegalStateException("KRX 로그인이 필요합니다"))
            try {
                Result.success(etf.getPortfolio(date, ticker))
            } catch (e: Exception) {
                Timber.e(e, "포트폴리오 조회 실패: $ticker / $date")
                Result.failure(e)
            }
        }
    }

    fun getKrxIndex(): KrxIndex? = krxIndex

    fun getKrxStock(): KrxStock? = krxStock

    fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        try {
            krxClient?.close()
        } catch (e: Exception) {
            Timber.w(e, "KRX 클라이언트 close 실패")
        }
        krxClient = null
        krxEtf = null
        krxIndex = null
        krxStock = null
    }
}
