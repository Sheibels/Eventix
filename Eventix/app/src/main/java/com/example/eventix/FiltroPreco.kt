package com.example.eventix

enum class FiltroPreco(val nomeExibicao: String) {
    TODOS("Todos os preços"),
    MAIS_BARATO("Mais barato primeiro"),
    MAIS_CARO("Mais caro primeiro"),
    ATE_100("Até 100€"),
    ATE_300("Até 300€"),
    ATE_500("Até 500€"),
    ACIMA_500("Acima de 500€")
}