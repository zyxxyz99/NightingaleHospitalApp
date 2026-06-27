package com.example.nightingalehospitalapp.viewmodel.admin.surgery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nightingalehospitalapp.database.FirebaseConfig
import com.example.nightingalehospitalapp.models.enums.SurgeryStatus
import com.example.nightingalehospitalapp.models.surgery.SurgeryBooking
import com.example.nightingalehospitalapp.repository.surgery.SurgeryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SurgeryBookingItem(
    val surgeryId: String,
    val patientName: String,
    val doctorName: String,
    val otRoom: String,
    val surgeryType: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val status: SurgeryStatus,
    val otId: String
)

/**
 * Surfaces every surgery in the system (admin view) or just the surgeries
 * assigned to a specific doctor (doctor view).
 *
 * Pass [doctorId] in the constructor / via [bindDoctor] to restrict the list.
 * Both admins and doctors may transition a surgery's status; the FAB that
 * schedules new surgeries is hidden in doctor scope (see [isDoctorScope]).
 * Surgeries persist in the list across status changes — only a CANCELLED
 * transition is the "this surgery is no longer happening" signal in the
 * doctor's dashboard.
 */
class ManageSurgeriesViewModel(
    initialDoctorId: String? = null
) : ViewModel() {

    private val surgeryRepository = SurgeryRepository()

    private val _doctorId = MutableStateFlow(initialDoctorId?.takeIf { it.isNotBlank() })

    /** True when the VM is being driven by a doctor's dashboard view. */
    // Backing flow for [isDoctorScope]. Doctors arrive with no constructor
    // arg and bind later via [bindDoctor]; without this second flow, the
    // activity would never see the scope flip and would keep showing the
    // "+" FAB to doctors.
    private val _isDoctorScope = MutableStateFlow(_doctorId.value != null)
    val isDoctorScope: StateFlow<Boolean> = _isDoctorScope.asStateFlow()

    private val _surgeries = MutableStateFlow<List<SurgeryBookingItem>>(emptyList())
    val surgeries: StateFlow<List<SurgeryBookingItem>> = _surgeries

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchSurgeries()
    }

    /**
     * Bind the VM to a single doctor's view. Subsequent fetches will only
     * return surgeries where `doctorId == [doctorId]`. Status transitions
     * remain enabled — the doctor can mark their own surgeries as
     * COMPLETED or CANCELLED.
     */
    fun bindDoctor(doctorId: String) {
        if (doctorId.isBlank()) return
        _doctorId.value = doctorId
        // Flip scope so the screen recomposes with the FAB hidden / title
        // flipped. Without this, doctors bound from an intent extra would
        // still see the admin layout.
        _isDoctorScope.value = true
        fetchSurgeries()
    }

    /**
     * Loads surgeries from `surgery_bookings`. When a doctor scope is bound,
     * only that doctor's bookings are returned. Patient / doctor / OT display
     * strings are resolved in parallel — individual sub-lookup failures fall
     * back to a labelled "Unknown" placeholder rather than hanging the screen.
     */
    fun fetchSurgeries() {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val raw: List<SurgeryBooking> = _doctorId.value
                    ?.let { surgeryRepository.getAllSurgeriesForDoctorAsync(it) }
                    ?: surgeryRepository.getAllSurgeriesAsync()

                if (raw.isEmpty()) {
                    _surgeries.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                val items = coroutineScope {
                    raw.map { surgery ->
                        async {
                            val patientName = lookupUserName(
                                surgery.patientId,
                                fallback = "Unknown Patient"
                            )
                            val doctorName = lookupUserName(
                                surgery.doctorId,
                                fallback = "Unknown Doctor"
                            )
                            val otRoom = lookupOtRoom(
                                surgery.otId,
                                fallback = "Unassigned OT"
                            )
                            SurgeryBookingItem(
                                surgeryId = surgery.surgeryId,
                                patientName = patientName,
                                doctorName = doctorName,
                                otRoom = otRoom,
                                surgeryType = surgery.surgeryType,
                                date = surgery.date,
                                startTime = surgery.startTime,
                                endTime = surgery.endTime,
                                status = surgery.status,
                                otId = surgery.otId
                            )
                        }
                    }.awaitAll()
                }

                _surgeries.value = items
                _isLoading.value = false
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Failed to load surgeries"
                _isLoading.value = false
            }
        }
    }

    private suspend fun lookupUserName(userId: String, fallback: String): String {
        if (userId.isBlank()) return fallback
        return try {
            val snap = FirebaseConfig.usersRef.document(userId).get().await()
            snap.getString("name")?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    private suspend fun lookupOtRoom(otId: String, fallback: String): String {
        if (otId.isBlank()) return fallback
        return try {
            val snap = FirebaseConfig.operationTheatresRef.document(otId).get().await()
            snap.getString("roomNumber")?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    /**
     * Updates the status of a surgery. Both admins and doctors can call this —
     * `EXTRA_DOCTOR_ID` only restricts which rows are listed, not who can
     * transition their status.
     *
     * The row is updated in place in [_surgeries] (no Firestore re-read) so
     * the card reflects the new status instantly and never disappears from
     * the list — only the [SurgeryStatus] field on the underlying doc is
     * touched, never the doc itself. The associated operation theatre is
     * freed on both COMPLETED and CANCELLED so the room is reusable after
     * the scheduled day.
     */
    fun updateSurgeryStatus(
        surgeryId: String,
        newStatus: SurgeryStatus,
        otId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        surgeryRepository.updateSurgeryStatus(surgeryId, newStatus) { success, error ->
            if (!success) {
                onResult(false, error ?: "Failed to update surgery status")
                return@updateSurgeryStatus
            }

            // Reflect the new status in the in-memory list immediately so the
            // card does not flicker / vanish while the OT-release (and any
            // subsequent cross-device sync) is processed. A re-fetch would
            // route the screen through the loading spinner branch and the
            // user would see the row disappear until the snapshot landed;
            // instead we mutate the matching item in place.
            _surgeries.value = _surgeries.value.map { item ->
                if (item.surgeryId == surgeryId) item.copy(status = newStatus) else item
            }

            // Release the OT back to AVAILABLE when the surgery is finished
            // or cancelled. Runs in the background — its outcome does not
            // affect the surgery row, only the OT's availability flag.
            val shouldFreeOt = newStatus == SurgeryStatus.COMPLETED ||
                newStatus == SurgeryStatus.CANCELLED
            if (shouldFreeOt && otId.isNotBlank()) {
                surgeryRepository.updateOperationTheatreStatus(otId, "AVAILABLE") { otOk, otErr ->
                    if (otOk) {
                        onResult(true, null)
                    } else {
                        onResult(false, otErr ?: "Surgery updated, but failed to free OT")
                    }
                }
            } else {
                onResult(true, null)
            }
        }
    }
}
