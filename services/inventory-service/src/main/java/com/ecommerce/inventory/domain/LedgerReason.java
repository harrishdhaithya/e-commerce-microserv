package com.ecommerce.inventory.domain;

/** Why a quantity moved. Every ledger row carries one. */
public enum LedgerReason {

    /** Stock arriving from a supplier, or an initial load. */
    RECEIPT,

    /** Held for an order. Negative: it leaves availability without leaving the shelf. */
    RESERVE,

    /** A held quantity shipped. Negative against on-hand. */
    COMMIT,

    /** A held quantity returned to availability. */
    RELEASE,

    /** The expiry sweep returning stock from an abandoned checkout. */
    EXPIRY,

    /** A manual correction by an administrator, signed either way. */
    ADJUSTMENT,

    /** A customer return. */
    RETURN
}
