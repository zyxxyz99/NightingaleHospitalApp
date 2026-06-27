package com.example.nightingalehospitalapp.repository.prescription

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.prescription.Prescription

class PrescriptionRepository {

    fun addPrescription(prescription: Prescription) {

        val id = FirebaseConfig.prescriptionsRef.document().id
            ?: throw Exception("Failed to generate prescription ID")

        val updatedPrescription = prescription.copy(prescriptionId = id)

        FirebaseConfig.prescriptionsRef
            .document(id)
            .set(updatedPrescription)
    }
}