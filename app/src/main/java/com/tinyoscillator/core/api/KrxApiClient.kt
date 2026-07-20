package com.tinyoscillator.core.api

import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import com.krxkt.model.EtfInfo
import com.krxkt.model.EtfPortfolio
import com.krxkt.model.EtfPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class KrxApiClient {

    // @Volatile — [getKrxIndex]/[getKrxStock] read와 [close]/[closeInternal] write가 [mutex] 밖에서
    // 일어나므로 happens-before 보장을 위해 필수(Phase 3-5).
    @Volatile private var krxClient: KrxClient? = null
    @Volatile private var krxEtf: KrxEtf? = null
    @Volatile private var krxIndex: KrxIndex? = null
    @Volatile private var krxStock: KrxStock? = null
    private val mutex = Mutex()

    /**
     * login→작업→close 한 세션 전체를 직렬화하기 위한 client-level mutex (Phase 3-6).
     *
     * 이 클라이언트는 `@Singleton`이라 여러 리포지토리·워커가 공유한다. 한 호출자가 `login →
     * getKrxIndex/getKrxStock 사용 → close()` 하는 도중, 다른 호출자의 `close()`가 세션을
     * 무효화(사용 중 close)할 수 있다. 호출처는 login-use-close 시퀀스를 `sessionMutex.withLock { }`로
     * 감싸 서로 배타 실행해야 한다. 개별 호출 직렬화용 [mutex]와 분리한다 — 세션 블록이 sessionMutex를
     * 든 채 내부에서 [login]이 [mutex]를 다시 획득해도 교착되지 않도록.
     */
    val sessionMutex = Mutex()

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

    suspend fun getEtfPrice(date: String): Result<List<EtfPrice>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val etf = krxEtf
                ?: return@withContext Result.failure(IllegalStateException("KRX 로그인이 필요합니다"))
            try {
                Result.success(etf.getEtfPrice(date))
            } catch (e: Exception) {
                Timber.e(e, "ETF 시세 조회 실패: $date")
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

    /**
     * Close the KRX client and release resources.
     * Note: This is intentionally non-suspend and does not acquire the mutex.
     * It is called from finally blocks where suspension may not be appropriate.
     * Callers must ensure no concurrent API operations are in progress.
     */
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
