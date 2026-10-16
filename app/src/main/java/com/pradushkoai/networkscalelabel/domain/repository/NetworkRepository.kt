package com.pradushkoai.networkscalelabel.domain.repository

import com.pradushkoai.networkscalelabel.domain.model.PhoneNetworks

/**
 * Сетевые интерфейсы телефона (IPv4): нужны для диагностики —
 * телефон и весы должны быть в одной подсети для прямого UDP-обмена.
 */
interface NetworkRepository {

    /** Перечитывает состояние сети и возвращает свежий снимок. */
    fun refresh(): PhoneNetworks

    /** Последний известный снимок без обращений к системе. */
    fun current(): PhoneNetworks
}
