package com.family.remote.support

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Central Firestore access point.
 *
 * The project's Firestore database was created through the current Firebase
 * console flow, which assigns the database id "default" — NOT the legacy
 * "(default)" id that plain FirebaseFirestore.getInstance() targets (that
 * path returns NOT_FOUND for this project). Every Firestore call in the app
 * MUST obtain its instance here, so the correct database id is controlled in
 * exactly one place (BuildConfig.FIRESTORE_DATABASE_ID, overridable in CI).
 */
object Fb {
    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID)
    }
}
