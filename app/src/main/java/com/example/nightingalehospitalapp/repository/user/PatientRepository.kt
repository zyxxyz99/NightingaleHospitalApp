package com.example.nightingalehospitalapp.repository.user

import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.user.Patient

class PatientRepository {

    fun registerPatient(patient: Patient) {
        val id = FirebaseConfig.patientsRef.document().id
        
        // Create a copy of the patient with the new ID
        val updatedPatient = patient.copy(patientId = id)

        FirebaseConfig.patientsRef.document(id).set(updatedPatient)
    }
}
