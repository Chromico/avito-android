package com.avito.runner.finalizer.verdict

import com.avito.report.model.AndroidTest
import com.avito.report.model.SimpleRunTest
import com.avito.report.model.TestStaticData

internal interface HasNotReportedTestsDeterminer {

    fun determine(
        runResult: List<SimpleRunTest>,
        allTests: Collection<TestStaticData>
    ): Result

    sealed class Result {

        open val lostTests
            get() = emptyList<AndroidTest.Lost>()

        object AllTestsReported : Result()

        data class HasNotReportedTests(
            override val lostTests: List<AndroidTest.Lost>
        ) : Result()
    }
}