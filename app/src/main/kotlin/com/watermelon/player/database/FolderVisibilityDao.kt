// database/FolderVisibilityDao.kt
@Dao
interface FolderVisibilityDao {
    @Query("SELECT * FROM folder_visibility WHERE isVisible = 0")
    fun getExcludedFolders(): Flow<List<FolderVisibility>>

    @Query("SELECT isVisible FROM folder_visibility WHERE folderPath = :path")
    suspend fun isFolderVisible(path: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVisibility(folder: FolderVisibility)
}