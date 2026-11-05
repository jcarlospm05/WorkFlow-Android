package com.workflow.operaciones.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "masters", indices = [Index(value = ["kind", "code"], unique = true)])
data class MasterRow(@PrimaryKey val id: String, val kind: String, val code: String, val payload: String)
@Entity(tableName = "operations", indices = [Index("day"), Index(value = ["day", "naturalKey"], unique = true)])
data class OperationRow(@PrimaryKey val id: String, val day: String, val module: String, val naturalKey: String?, val payload: String)
@Entity(tableName = "daily_documents")
data class DocumentRow(@PrimaryKey val day: String, val revision: Long, val payload: String)
@Entity(tableName = "received", primaryKeys = ["originId", "day"])
data class ReceivedRow(val originId: String, val day: String, val revision: Long, val payload: String, val receivedAt: String)
@Entity(tableName = "audit")
data class AuditRow(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val at: String, val action: String, val recordId: String, val before: String?, val after: String?)
@Dao interface WorkflowDao {
    @Query("SELECT * FROM masters ORDER BY kind, code") suspend fun masters(): List<MasterRow>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMaster(row: MasterRow)
    @Update suspend fun updateMaster(row: MasterRow)
    @Query("SELECT * FROM operations WHERE day = :day ORDER BY id") suspend fun operations(day: String): List<OperationRow>
    @Query("SELECT * FROM operations ORDER BY day, id") suspend fun allOperations(): List<OperationRow>
    @Query("SELECT * FROM operations WHERE id = :id") suspend fun operation(id: String): OperationRow?
    @Upsert suspend fun putOperation(row: OperationRow)
    @Query("SELECT * FROM daily_documents WHERE day = :day") suspend fun document(day: String): DocumentRow?
    @Query("SELECT * FROM daily_documents ORDER BY day DESC") suspend fun documents(): List<DocumentRow>
    @Upsert suspend fun putDocument(row: DocumentRow)
    @Query("SELECT * FROM received WHERE originId = :origin AND day = :day") suspend fun received(origin: String, day: String): ReceivedRow?
    @Query("SELECT * FROM received ORDER BY day DESC, receivedAt DESC") suspend fun allReceived(): List<ReceivedRow>
    @Upsert suspend fun putReceived(row: ReceivedRow)
    @Insert suspend fun audit(row: AuditRow)
    @Query("SELECT COUNT(*) FROM audit") fun changes(): Flow<Int>
}
@Database(entities = [MasterRow::class, OperationRow::class, DocumentRow::class, ReceivedRow::class, AuditRow::class], version = 1, exportSchema = true)
abstract class WorkflowDatabase : RoomDatabase() {
    abstract fun dao(): WorkflowDao
    companion object {
        fun open(context: android.content.Context) = Room.databaseBuilder(context, WorkflowDatabase::class.java, "workflow.sqlite").build()
        // No destructive migration fallback. Add explicit migrations when schema version increases.
    }
}
