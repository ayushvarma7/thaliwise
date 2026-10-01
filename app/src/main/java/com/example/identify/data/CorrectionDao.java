package com.example.identify.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CorrectionDao {
    @Insert long insert(CorrectionEntity e);
    @Query("SELECT * FROM corrections ORDER BY timestampMillis DESC") LiveData<List<CorrectionEntity>> observeAll();
    @Query("SELECT * FROM corrections ORDER BY timestampMillis DESC") List<CorrectionEntity> getAllSync();
    @Query("SELECT imagePath FROM corrections") List<String> getAllImagePathsSync();
    @Query("SELECT COUNT(*) FROM corrections") LiveData<Integer> observeCount();
    @Query("SELECT COUNT(*) FROM corrections WHERE accepted = 0") LiveData<Integer> observeCorrectionCount();
    @Query("SELECT COUNT(*) FROM corrections WHERE source = 'MEMORY'") LiveData<Integer> observeMemoryHitCount();
    @Query("SELECT COUNT(*) FROM corrections WHERE source = 'MEMORY' AND accepted = 0") LiveData<Integer> observeMemoryHitCorrectedCount();
    @Query("DELETE FROM corrections") void deleteAll();
}
