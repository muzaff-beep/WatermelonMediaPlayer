// database/FolderVisibility.kt
@Entity(tableName = "folder_visibility")
data class FolderVisibility(
    @PrimaryKey val folderPath: String,
    @ColumnInfo(name = "is_visible") val isVisible: Boolean = true
)