@Dao
interface VideoDao {
    @Query("""
        SELECT * FROM videos 
        WHERE folder_path NOT IN (
            SELECT folderPath FROM folder_visibility WHERE isVisible = 0
        )
    """)
    fun getAllVisibleVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)
}