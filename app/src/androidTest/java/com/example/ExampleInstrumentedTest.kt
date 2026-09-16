package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.database.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실 서비스 계측(Instrumented) 테스트: 패키지 정합성 및 Room DB 싱글톤 초기화 검증
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun verifyAppContextAndPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.Sunnylife.kyh", appContext.packageName)
    }

    @Test
    fun verifyDatabaseInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(appContext)
        assertNotNull(db)
        assertNotNull(db.userDao())
        assertNotNull(db.financialLogDao())
        assertNotNull(db.syncQueueDao())
    }
}
