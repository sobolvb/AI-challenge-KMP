package com.aichallengekmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform