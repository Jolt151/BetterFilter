package com.betterfilter

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.db.*

class MyDatabaseOpenHelper private constructor(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "MyDatabase", null, 1) {
    init {
        instance = this
    }

    companion object {
        private var instance: MyDatabaseOpenHelper? = null

        @Synchronized
        fun getInstance(ctx: Context) = instance ?: MyDatabaseOpenHelper(ctx.applicationContext)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Here you create tables
        db.createTable("whitelisted_apps", true,
            "package_name" to TEXT + PRIMARY_KEY + UNIQUE,
            "visible_name" to TEXT)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Here you can upgrade tables, as usual
        db.dropTable("whitelisted_apps", true)
    }
}

// Access property for Context
val Context.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(this)


fun Cursor.cursorToString(): String {
    var cursorString = ""
    if (this.moveToFirst()) {
        val columnNames = this.getColumnNames()
        for (name in columnNames)
            cursorString += String.format("%s ][ ", name)
        cursorString += "\n"
        do {
            for (name in columnNames) {
                cursorString += String.format(
                    "%s ][ ",
                    this.getString(this.getColumnIndex(name))
                )
            }
            cursorString += "\n"
        } while (this.moveToNext())
    }
    return cursorString
}