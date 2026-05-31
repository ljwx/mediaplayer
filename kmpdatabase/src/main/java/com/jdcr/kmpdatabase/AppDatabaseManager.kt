package com.jdcr.kmpdatabase

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import tasksq.AppDatabase

class AppDatabaseManager(context: Context) {
    private val driver = AndroidSqliteDriver(
        schema = AppDatabase.Schema,
        context = context,
        name = "AppDatabase.db" // 数据库文件名
    )

    val db = AppDatabase(driver)
    val taskQueries = db.mediaListQueries
}