package com.example.questkampus

data class Member(
    val name: String = "",
    val level: Int = 1,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val avatar_url: String = ""
)