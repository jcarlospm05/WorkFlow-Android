package com.workflow.operaciones.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun upgrading_database_version_one_preserves_existing_catalogs()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="migration-${UUID.randomUUID()}.sqlite"
        val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
        val person=Operator(UUID.randomUUID().toString(),name="Persona de migración sintética")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            old.execSQL("CREATE TABLE masters (id TEXT NOT NULL,kind TEXT NOT NULL,code TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(id))")
            old.execSQL("CREATE UNIQUE INDEX index_masters_kind_code ON masters(kind,code)")
            old.execSQL("CREATE TABLE operations (id TEXT NOT NULL,day TEXT NOT NULL,module TEXT NOT NULL,naturalKey TEXT,payload TEXT NOT NULL,PRIMARY KEY(id))")
            old.execSQL("CREATE INDEX index_operations_day ON operations(day)")
            old.execSQL("CREATE UNIQUE INDEX index_operations_day_naturalKey ON operations(day,naturalKey)")
            old.execSQL("CREATE TABLE daily_documents (day TEXT NOT NULL,revision INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(day))")
            old.execSQL("CREATE TABLE received (originId TEXT NOT NULL,day TEXT NOT NULL,revision INTEGER NOT NULL,payload TEXT NOT NULL,receivedAt TEXT NOT NULL,PRIMARY KEY(originId,day))")
            old.execSQL("CREATE TABLE audit (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,at TEXT NOT NULL,action TEXT NOT NULL,recordId TEXT NOT NULL,`before` TEXT,`after` TEXT)")
            old.execSQL("INSERT INTO masters VALUES (?,?,?,?)",arrayOf(person.id,"operator","workflow:auto:${person.id}",DailyCodec.json.encodeToString(person)))
            old.version=1
        }
        val db=Room.databaseBuilder(context,WorkflowDatabase::class.java,name).addMigrations(WorkflowDatabase.MIGRATION_1_2).build()
        try {
            val repository=WorkflowRepository(context,db)
            assertEquals(person,repository.masters().first.single())
            assertEquals(person,repository.document("2026-11-06").operators.single())
            assertEquals(2,db.openHelper.writableDatabase.version)
            assertTrue(db.dao().openings().isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
}
