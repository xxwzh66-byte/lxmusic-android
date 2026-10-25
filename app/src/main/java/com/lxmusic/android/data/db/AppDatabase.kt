package com.lxmusic.android.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

@Entity(tableName = "song_list")
data class SongListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val coverUrl: String?,
    val description: String,
    val createdAt: Long,
)

/** A user-created playlist. */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A track stored inside a playlist (MusicInfo serialised to JSON). */
@Entity(tableName = "playlist_track", primaryKeys = ["playlistId", "musicId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val musicId: String,
    val musicJson: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A favourited track. */
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val musicId: String,
    val musicJson: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A recently-played entry (one row per track, updated on replay). */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val musicId: String,
    val musicJson: String,
    val playedAt: Long = System.currentTimeMillis(),
)

/**
 * V5.3.3: one row per playback, the raw material for the play-count statistics.
 *
 * [PlayHistoryEntity] cannot serve this: it keeps a single row per track
 * (upserted on replay) precisely so "recently played" stays a de-duplicated
 * list, which throws away the count. This table appends instead.
 */
@Entity(tableName = "play_event")
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val musicId: String,
    val musicJson: String,
    val playedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "list_item", primaryKeys = ["listId", "musicId"])
data class ListItemEntity(
    val listId: Long,
    val musicId: String,
    val musicJson: String,
    val position: Int,
)

@Entity(tableName = "user_api")
data class UserApiEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val homepage: String,
    val script: String,
    val allowShowUpdateAlert: Boolean = true,
    val createdAt: Long,
)

/** A cached local track discovered by the SAF / manual scanners (C2). */
@Entity(tableName = "local_track")
data class LocalTrackEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val documentId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val lastModified: Long,
    val coverPath: String?,
    /** "folder" (SAF tree scan) or "manual" (explicitly added file). */
    val origin: String,
    val dateAdded: Long,
)

@Dao
interface SongListDao {
    @Query("SELECT * FROM song_list ORDER BY createdAt ASC")
    suspend fun all(): List<SongListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: SongListEntity): Long

    @Query("DELETE FROM song_list WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT * FROM list_item WHERE listId = :listId ORDER BY position ASC")
    suspend fun items(listId: Long): List<ListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItemEntity)

    @Query("DELETE FROM list_item WHERE listId = :listId AND musicId = :musicId")
    suspend fun deleteItem(listId: Long, musicId: String)

    @Query("SELECT MAX(position) FROM list_item WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int?
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY updatedAt DESC")
    suspend fun all(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun rename(id: Long, name: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE playlist SET coverUrl = :cover, updatedAt = :ts WHERE id = :id")
    suspend fun setCover(id: Long, cover: String?, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_track WHERE playlistId = :id ORDER BY position ASC")
    suspend fun tracks(id: Long): List<PlaylistTrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_track WHERE playlistId = :id")
    suspend fun trackCount(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_track WHERE playlistId = :id AND musicId = :musicId")
    suspend fun removeTrack(id: Long, musicId: String)

    @Query("DELETE FROM playlist_track WHERE playlistId = :id")
    suspend fun clearTracks(id: Long)

    @Query("SELECT MAX(position) FROM playlist_track WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int?
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY addedAt DESC")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE musicId = :musicId)")
    suspend fun isFavorite(musicId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(fav: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE musicId = :musicId")
    suspend fun remove(musicId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<PlayHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun touch(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

@Dao
interface PlayEventDao {
    /** Every event at or after [from], oldest first (0 means "everything"). */
    @Query("SELECT * FROM play_event WHERE playedAt >= :from AND playedAt <= :now ORDER BY playedAt ASC")
    suspend fun since(from: Long, now: Long): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_event")
    suspend fun count(): Int

    @Insert
    suspend fun log(event: PlayEventEntity)

    @Query("DELETE FROM play_event")
    suspend fun clear()

    /** Housekeeping: keeps the table from growing without bound. */
    @Query("DELETE FROM play_event WHERE playedAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface UserApiDao {
    @Query("SELECT * FROM user_api ORDER BY createdAt ASC")
    suspend fun all(): List<UserApiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(api: UserApiEntity)

    @Query("DELETE FROM user_api WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LocalTrackDao {
    @Query("SELECT * FROM local_track ORDER BY title COLLATE NOCASE ASC")
    suspend fun all(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_track WHERE origin = :origin")
    suspend fun byOrigin(origin: String): List<LocalTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<LocalTrackEntity>)

    @Query("DELETE FROM local_track WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM local_track")
    suspend fun clear()

    @Query("DELETE FROM local_track WHERE origin = 'folder'")
    suspend fun clearFolders()

    @Query("DELETE FROM local_track WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Database(
    entities = [
        SongListEntity::class, ListItemEntity::class, UserApiEntity::class,
        PlaylistEntity::class, PlaylistTrackEntity::class,
        FavoriteEntity::class, PlayHistoryEntity::class,
        LocalTrackEntity::class,
        PlayEventEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songListDao(): SongListDao
    abstract fun userApiDao(): UserApiDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun playEventDao(): PlayEventDao

    companion object {

        /** v2 -> v3: adds the local_track cache table (C2). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_track` (" +
                        "`id` TEXT NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`documentId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`lastModified` INTEGER NOT NULL, " +
                        "`coverPath` TEXT, " +
                        "`origin` TEXT NOT NULL, " +
                        "`dateAdded` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_track_origin` ON `local_track` (`origin`)")
            }
        }

        /** v3 -> v4: adds the append-only play_event table (V5.3.3). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_event` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`musicId` TEXT NOT NULL, " +
                        "`musicJson` TEXT NOT NULL, " +
                        "`playedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_event_playedAt` " +
                        "ON `play_event` (`playedAt`)"
                )
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "lxmusic.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}