package com.jdcr.kmpdatabase

import android.content.Context
import com.jdcr.kmpdatabase.util.JdcrDBLog

object JdcrDBTest {

    fun demo(context: Context, add: Boolean = false) {
        val db = AppDatabaseManager(context)
        if (add) {
            db.taskQueries.insertMedia("test1", "/sd/funny/abc", 6, "mp4", "video", "111")
            db.taskQueries.insertMedia("test2", "/sd/funny/abc", 7, "mp4", "video", "112")
            db.taskQueries.insertMedia("test3", "/sd/funny/abc", 8, "mp4", "video", "113")
        }
        val tasks = db.taskQueries.selectAll().executeAsList()
        JdcrDBLog.d("数据库数据量:" + tasks.size)
        db.taskQueries.selectByUuid("112").executeAsOne().apply {
            JdcrDBLog.d("查询结果:$file_name")
        }
    }

}