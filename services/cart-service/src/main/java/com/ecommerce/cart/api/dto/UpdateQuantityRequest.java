package com.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Set an absolute quantity. Zero removes the line, which is why the floor is 0. */
public record UpdateQuantityRequest(@Min(0) @Max(99) int quantity) {
}
