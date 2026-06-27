package com.example.nightingalehospitalapp.repository.surgery

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.surgery.SurgeryBooking

class SurgeryRepository {

    fun bookSurgery(surgery: SurgeryBooking) {

        val id = FirebaseConfig.surgeryBookingsRef.document().id
            ?: throw Exception("Failed to generate surgery ID")

        val updatedSurgery = surgery.copy(surgeryId = id)

        FirebaseConfig.surgeryBookingsRef
            .document(id)
            .set(updatedSurgery)
    }
}