// database/MediaDatabase.kt
@Database(
    entities = [VideoEntity::class, FolderVisibility::class],
    version = 1,
    exportSchema = true
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun folderVisibilityDao(): FolderVisibilityDao
}