package com.example.blackjack.model

enum class Rank(val baseValue: Int) {
    ACE(11),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(10),
    QUEEN(10),
    KING(10)
}

enum class Suit {
    CLUBS,
    DIAMONDS,
    HEARTS,
    SPADES
}

data class Card(val rank: Rank, val suit: Suit) {
    val baseValue: Int
        get() = rank.baseValue
}
