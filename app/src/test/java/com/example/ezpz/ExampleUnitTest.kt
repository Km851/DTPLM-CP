package com.example.ezpz

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun parseJsonResponse_keepsGeminiDueDate() {
        val tasks = TaskManager.parseJsonResponse(
            """{"tasks":[{"title":"Lab report","subtitle":"4:30 PM · 30 min","dueDate":"2026-09-04"}]}"""
        )

        assertEquals(1, tasks.size)
        assertEquals("Lab report", tasks[0].title)
        assertEquals("2026-09-04", tasks[0].dueDate)
    }

    @Test
    fun parseJsonResponse_defaultsMissingDueDateToToday() {
        val tasks = TaskManager.parseJsonResponse(
            """{"tasks":[{"title":"Read notes","subtitle":"20 min"}]}"""
        )

        assertEquals(1, tasks.size)
        assertEquals(TaskManager.todayDate(), tasks[0].dueDate)
    }

    @Test
    fun parseJsonResponse_acceptsDateAlias() {
        val tasks = TaskManager.parseJsonResponse(
            """{"tasks":[{"title":"Quiz prep","subtitle":"15 min","date":"2026-09-05"}]}"""
        )

        assertEquals("2026-09-05", tasks[0].dueDate)
    }

    @Test
    fun parseJsonResponse_stripsMarkdownFence() {
        val tasks = TaskManager.parseJsonResponse(
            "```json\n{\"tasks\":[{\"title\":\"Gym\",\"subtitle\":\"6 PM\",\"dueDate\":\"2026-09-01\"}]}\n```"
        )

        assertEquals(1, tasks.size)
        assertEquals("Gym", tasks[0].title)
        assertEquals("2026-09-01", tasks[0].dueDate)
    }
}
