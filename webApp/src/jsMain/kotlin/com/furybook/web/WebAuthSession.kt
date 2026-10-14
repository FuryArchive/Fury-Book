package com.furybook.web

/** Display identity only, not a credential or proof of authentication. Server verification remains required. */
data class WebAuthSession(val userId: String, val email: String)
