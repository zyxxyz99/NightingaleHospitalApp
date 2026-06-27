package com.example.nightingalehospitalapp.repository.diagnostic

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.diagnostic.TestBooking

class DiagnosticRepository {

    fun bookTest(testBooking: TestBooking) {

        val id = FirebaseConfig.testBookingsRef.document().id
            ?: throw Exception("Failed to generate test booking ID")

        val updatedBooking = testBooking.copy(bookingId = id)

        FirebaseConfig.testBookingsRef
            .document(id)
            .set(updatedBooking)
    }
}