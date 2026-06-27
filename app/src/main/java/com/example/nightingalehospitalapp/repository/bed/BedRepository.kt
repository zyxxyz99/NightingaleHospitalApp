package com.example.nightingalehospitalapp.repository.bed

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.hospital.Bed

class BedRepository {

    fun addBed(bed: Bed) {

        val id = FirebaseConfig.bedsRef.document().id
            ?: throw Exception("Failed to generate bed ID")

        val updatedBed = bed.copy(bedId = id)

        FirebaseConfig.bedsRef
            .document(id)
            .set(updatedBed)
    }
}