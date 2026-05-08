package com.example.progettoappiot

data class Accesso(
    val username: String? = null,
    val tag_id:   String? = null,
    val orario:   String? = null,
    val data:     String? = null,
    val azione:   String? = null,
    val source:   String? = null
)

data class LoginResponse(
    val success:         Boolean,
    val username:        String?  = null,
    val is_admin:        Boolean? = null,
    val has_door_access: Boolean? = null,
    val message:         String?  = null,
    val profile_picture: String?  = null
)

data class RegisterResponse(
    val success:         Boolean,
    val message:         String  = "",
    val has_door_access: Boolean? = null
)

data class StatoPortaResponse(
    val stato: String? = null
)

/** Tag RFID — gestito dall'admin */
data class Tag(
    val tag_id:          String?  = null,
    val label:           String?  = null,
    val has_door_access: Boolean? = null
)

/** Utente — per gestione accessi admin */
data class UserItem(
    val username:        String,
    val has_door_access: Boolean = false
)

data class GenericResponse(
    val success: Boolean,
    val message: String? = null
)
