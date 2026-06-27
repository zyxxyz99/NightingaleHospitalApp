package com.example.nightingalehospitalapp.repository.admission

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.admission.Admission

class AdmissionRepository {

    fun admitPatient(admission: Admission) {

        val id = FirebaseConfig.admissionsRef.document().id
            ?: throw Exception("Failed to generate admission ID")

        val updatedAdmission = admission.copy(id = id)

        FirebaseConfig.admissionsRef
            .document(id)
            .set(updatedAdmission)
    }
}